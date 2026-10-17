package io.github.dreadboy.navigationshortcuts

import android.app.AlertDialog
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateUtils
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Share target "Add to navigation widget" (spec Flow 2, PLAN 3.5–3.7, decisions 5.7): a two-step bottom sheet over
 * Google Maps. Step 1 resolves the shared link once (background thread, short timeout) and lets the user agree on
 * icon, label and destination (Cancel / Next). Step 2 picks the widget — always shown, newest first, "not seen"
 * widgets last, "+ Create new widget" as the last row (Back / Add); a full grid grows in one tap.
 */
class AddDestinationActivity : ComponentActivity() {

    private val scope = MainScope()
    private lateinit var store: WidgetStore

    private lateinit var labelInput: EditText
    private lateinit var destinationInput: EditText
    private lateinit var iconButton: ImageButton
    private lateinit var statusText: TextView
    private lateinit var progress: ProgressBar
    private lateinit var widgetHeading: TextView
    private lateinit var widgetHint: TextView
    private lateinit var gridTip: View
    private lateinit var cancelButton: Button
    private lateinit var widgetStep: View
    private lateinit var widgetStepMessage: View
    private lateinit var chips: RadioGroup
    private lateinit var messageText: TextView
    private lateinit var growButtons: View
    private lateinit var addColumnButton: Button
    private lateinit var addRowButton: Button
    private lateinit var addButton: Button
    private lateinit var form: View

    private var icon: IconRef = IconGuess.refFor(BuiltInIcons.PIN)

    /** One id per share: seeds the icon's look (same as on the widget) and keeps pending/pin idempotent (decisions 3.3). */
    private val entryId = java.util.UUID.randomUUID().toString()
    private var iconChosenByUser = false
    private var resolved = false
    private var state = StoreState()
    private var widgetIds: List<Int> = emptyList()
    private val chipWidgets = mutableMapOf<Int, Int>() // chip view id → appWidgetId
    private var createNewChip = View.NO_ID
    private var notSeen: Map<Int, Long> = emptyMap() // appWidgetId → render pending since (flagged widgets only)

    /** Chosen widget; null = "+ Create new widget". Before the user picks: the first row (newest visible widget). */
    private var selectedWidget: Int? = null
    private var mode = Mode.ADD
    private var step = Step.DETAILS
    private var busy = false
    /** Enabled in step 2 only; disabled = default Back (closes the sheet). */
    private val backToDetails = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (mode != Mode.DONE) showStep(Step.DETAILS) else finish()
        }
    }
    private var widgetsLoaded = false

    /** Set when requestPinAppWidget showed the launcher's confirmation; checked when we come back. */
    private var pinRequestedFor: Entry? = null
    private var pausedSincePinRequest = false

    /** Widget IDs bound when the pin was requested; a new ID afterwards means the launcher placed the widget. */
    private var pinBaseline: Set<Int> = emptySet()

    /** Step 2 state for the selected row; CREATE_NEW = "+ Create new widget". */
    private enum class Mode { ADD, GRID_FULL, CREATE_NEW, DONE }

    private enum class Step { DETAILS, WIDGET }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_destination)
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        window.setGravity(Gravity.BOTTOM)
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        store = WidgetStore(this)
        onBackPressedDispatcher.addCallback(this, backToDetails)

        labelInput = findViewById(R.id.label_input)
        destinationInput = findViewById(R.id.destination_input)
        iconButton = findViewById(R.id.icon_button)
        statusText = findViewById(R.id.status_text)
        progress = findViewById(R.id.resolving_progress)
        widgetHeading = findViewById(R.id.widget_heading)
        widgetHint = findViewById(R.id.widget_hint)
        gridTip = findViewById(R.id.grid_tip)
        cancelButton = findViewById(R.id.cancel_button)
        widgetStep = findViewById(R.id.widget_step)
        widgetStepMessage = findViewById(R.id.widget_step_message)
        chips = findViewById(R.id.widget_chips)
        messageText = findViewById(R.id.message_text)
        growButtons = findViewById(R.id.grow_buttons)
        addColumnButton = findViewById(R.id.add_column_button)
        addRowButton = findViewById(R.id.add_row_button)
        addButton = findViewById(R.id.add_button)
        form = findViewById(R.id.form)

        cancelButton.setOnClickListener { if (step == Step.WIDGET && mode != Mode.DONE) showStep(Step.DETAILS) else finish() }
        iconButton.setOnClickListener { showIconPicker() }
        addButton.setOnClickListener { onPrimary() }
        addColumnButton.setOnClickListener { addToSelected(Growth.COLUMN) }
        addRowButton.setOnClickListener { addToSelected(Growth.ROW) }
        val refresh = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = updateButtons()
        }
        labelInput.addTextChangedListener(refresh)
        // Renaming the place ("Doma") re-guesses the icon until the user picks one (E12).
        labelInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (!resolved || iconChosenByUser) return
                val guess = IconGuess.guess(s?.toString().orEmpty(), destinationInput.text.toString())
                if (guess != icon) setIcon(guess)
            }
        })
        destinationInput.addTextChangedListener(refresh)
        chips.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == View.NO_ID) return@setOnCheckedChangeListener
            selectedWidget = chipWidgets[checkedId]
            updateMode()
            setIcon(icon) // drawn in the chosen widget's style
        }

        val text = sharedText(intent)
        if (text.isNullOrBlank()) {
            Toast.makeText(this, R.string.nothing_shared, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        setIcon(icon)
        showStep(Step.DETAILS)
        loadWidgets()
        resolve(text)
    }

    /** Step 1 (icon, label, destination: Cancel / Next) or step 2 (widget list: Back / Add). Edits are kept. */
    private fun showStep(next: Step) {
        step = next
        val details = next == Step.DETAILS
        form.visibility = if (details) View.VISIBLE else View.GONE
        widgetStep.visibility = if (details) View.GONE else View.VISIBLE
        cancelButton.setText(if (details) R.string.cancel else R.string.back)
        setBackToDetails(!details)
        updateMode()
    }

    /**
     * System Back in step 2 returns to step 1. AndroidX's OnBackPressedDispatcher delivers it on every API level:
     * through an OnBackInvokedCallback where predictive back is active (API 36 with targetSdk 36), else through
     * onBackPressed (API 31–35; OnBackInvokedDispatcher itself is API 33+ and not used there without a manifest opt-in).
     */
    private fun setBackToDetails(enabled: Boolean) {
        backToDetails.isEnabled = enabled
    }

    override fun onPause() {
        super.onPause()
        if (pinRequestedFor != null) pausedSincePinRequest = true
    }

    override fun onStop() {
        super.onStop()
        // Niagara confirms the pin by bringing its home screen (with the new widget) to the front, which stops
        // this sheet in the background. The share is done then; don't leave the sheet behind in Maps' task.
        if (pinRequestedFor == null) return
        scope.launch {
            val ids = withContext(Dispatchers.IO) { store.boundWidgetIds() }
            if ((ids - pinBaseline).isNotEmpty() && pinRequestedFor != null) {
                Log.i(TAG, "pin confirmed (sheet stopped): widgets now $ids")
                pinRequestedFor = null
                Toast.makeText(applicationContext, R.string.added_to_new_widget, Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Back from the launcher's pin confirmation: done if a widget now exists, else explain the pending list.
        val entry = pinRequestedFor ?: return
        if (!pausedSincePinRequest) return
        pinRequestedFor = null
        scope.launch {
            val ids = withContext(Dispatchers.IO) { store.boundWidgetIds() }
            if ((ids - pinBaseline).isNotEmpty()) {
                Log.i(TAG, "pin confirmed: widgets now $ids")
                Toast.makeText(applicationContext, R.string.added_to_new_widget, Toast.LENGTH_LONG).show()
                finish()
            } else {
                Log.i(TAG, "pin not confirmed; '${entry.label}' stays pending")
                showDone(getString(R.string.saved_pending))
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun sharedText(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        return intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
    }

    private fun resolve(text: String) {
        progress.visibility = View.VISIBLE
        statusText.setText(R.string.resolving)
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                MapsShareParser.resolve(text) { url ->
                    ShortLinkFetcher.firstLocationWhenLive(url, log = { Log.i(TAG, "resolve $url: $it") })
                }
            }
            Log.i(TAG, "resolved share '${text.replace('\n', '|')}': $result")
            progress.visibility = View.GONE
            if (labelInput.text.isNullOrBlank()) labelInput.setText(result.label)
            when (result) {
                is Resolution.Resolved -> {
                    destinationInput.setText(result.destination)
                    destinationInput.isEnabled = false
                    statusText.setText(if (result.hasCoordinates) R.string.resolved_coordinates else R.string.resolved_text)
                }
                is Resolution.Failed -> {
                    destinationInput.setText(result.destination.orEmpty())
                    destinationInput.isEnabled = true
                    val message = getString(failureMessage(result.kind))
                    statusText.text = if (result.destination != null) getString(R.string.failed_name_used, message) else message
                    if (result.destination == null) destinationInput.requestFocus()
                }
            }
            if (!iconChosenByUser) setIcon(IconGuess.guess(labelInput.text.toString(), destinationInput.text.toString()))
            resolved = true
            updateButtons()
        }
    }

    private fun failureMessage(kind: FailureKind): Int = when (kind) {
        FailureKind.NO_INTERNET -> R.string.failed_no_internet
        FailureKind.NETWORK -> R.string.failed_network
        FailureKind.LINK_NOT_LIVE -> R.string.failed_not_live
        FailureKind.CID_ONLY -> R.string.failed_cid
        FailureKind.NOT_MAPS -> R.string.failed_not_maps
        FailureKind.UNEXPECTED -> R.string.failed_unexpected
    }

    private fun loadWidgets() {
        scope.launch {
            val (ids, current, statuses) = withContext(Dispatchers.IO) {
                val bound = store.boundWidgetIds()
                // Probe status from earlier renders, read before this sheet pushes fresh ones (decisions 3.9).
                val probe = bound.associateWith { RenderProbe.status(applicationContext, it) }
                for ((id, status) in probe) Log.i(TAG, "widget $id render probe: $status")
                val s = store.update { it.prune(bound) } ?: store.read()
                // Fresh render (and probe) for every bound widget; the launcher shows it when its home screen is next
                // visible, so the next share knows which widgets are displayed.
                WidgetUpdater.update(applicationContext)
                Triple(bound, s, probe)
            }
            val flagged = WidgetChooser.notSeen(statuses)
            notSeen = flagged.associateWith { statuses.getValue(it).unconfirmedSince }
            widgetIds = WidgetChooser.order(ids, flagged)
            state = current
            val labels = WidgetChooser.labels(
                ids = widgetIds,
                summaries = widgetIds.associateWith { (current.configs[it] ?: WidgetConfig()).summary() },
                notSeen = flagged,
                emptyLabel = getString(R.string.empty_widget),
                newest = getString(R.string.chip_newest),
                older = getString(R.string.chip_older),
                notSeenSuffix = getString(R.string.chip_not_seen),
            )
            chips.setOnCheckedChangeListener(null)
            chips.removeAllViews()
            chipWidgets.clear()
            var firstChip = View.NO_ID
            widgetIds.forEach { id ->
                chips.addView(RadioButton(this@AddDestinationActivity).apply {
                    this.id = View.generateViewId()
                    chipWidgets[this.id] = id
                    if (firstChip == View.NO_ID) firstChip = this.id
                    text = labels.getValue(id)
                })
            }
            // Last row: always a way to a new widget (also the way out of a ghost widget, decisions 3.9/5.7).
            createNewChip = View.generateViewId()
            chips.addView(RadioButton(this@AddDestinationActivity).apply {
                this.id = createNewChip
                setText(R.string.create_new_widget)
            })
            chips.check(if (firstChip != View.NO_ID) firstChip else createNewChip)
            chips.setOnCheckedChangeListener { _, checkedId ->
                if (checkedId == View.NO_ID) return@setOnCheckedChangeListener
                selectedWidget = chipWidgets[checkedId]
                updateMode()
                setIcon(icon)
            }
            selectedWidget = widgetIds.firstOrNull()
            widgetsLoaded = true
            updateMode()
            setIcon(icon)
        }
    }

    private fun updateMode() {
        val id = selectedWidget
        val config = id?.let { state.configs[it] ?: WidgetConfig() }
        mode = when {
            mode == Mode.DONE -> Mode.DONE
            id == null -> Mode.CREATE_NEW
            config!!.isFull -> Mode.GRID_FULL
            else -> Mode.ADD
        }
        if (mode == Mode.DONE) return
        if (step == Step.DETAILS) {
            messageText.visibility = View.GONE
            growButtons.visibility = View.GONE
            gridTip.visibility = View.GONE
            addButton.visibility = View.VISIBLE
            addButton.setText(R.string.next)
            updateButtons()
            return
        }
        val noWidget = mode == Mode.CREATE_NEW && widgetsLoaded && widgetIds.isEmpty()
        widgetStepMessage.visibility = if (noWidget) View.VISIBLE else View.GONE
        val since = id?.let { notSeen[it] }
        widgetHint.visibility = if (since != null) View.VISIBLE else View.GONE
        if (since != null) {
            widgetHint.text = getString(
                R.string.not_seen_hint,
                DateUtils.formatDateTime(this, since, DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE),
            )
        }
        gridTip.visibility =
            if (mode == Mode.GRID_FULL && config!!.canGrowRows() && config.rows + 1 >= TALL_GRID_ROWS) View.VISIBLE else View.GONE
        when (mode) {
            Mode.ADD -> {
                messageText.visibility = View.GONE
                growButtons.visibility = View.GONE
                addButton.visibility = View.VISIBLE
                addButton.setText(R.string.add)
            }
            Mode.GRID_FULL -> {
                val canGrow = config!!.canGrowColumns() || config.canGrowRows()
                messageText.visibility = View.VISIBLE
                messageText.text = if (canGrow) getString(R.string.grid_full, config.rows, config.cols) else getString(R.string.grid_full_max)
                growButtons.visibility = View.VISIBLE
                addColumnButton.visibility = if (config.canGrowColumns()) View.VISIBLE else View.GONE
                addRowButton.visibility = if (config.canGrowRows()) View.VISIBLE else View.GONE
                addButton.visibility = View.GONE
            }
            Mode.CREATE_NEW -> {
                // No widget yet: the message is above the list (widget_step_message) instead.
                messageText.visibility = if (noWidget) View.GONE else View.VISIBLE
                messageText.setText(R.string.create_new_hint)
                growButtons.visibility = View.GONE
                addButton.visibility = View.VISIBLE
                addButton.setText(R.string.add)
            }
            Mode.DONE -> Unit
        }
        updateButtons()
    }

    private fun updateButtons() {
        if (mode == Mode.DONE) return
        val ready = resolved && !busy && labelInput.text.isNotBlank() && destinationInput.text.isNotBlank()
        addButton.isEnabled = ready && (step == Step.DETAILS || widgetsLoaded)
        addColumnButton.isEnabled = ready
        addRowButton.isEnabled = ready
    }

    private fun currentEntry(): Entry =
        Entry(label = labelInput.text.toString().trim(), destination = destinationInput.text.toString().trim(), icon = icon, id = entryId)

    private fun onPrimary() {
        if (step == Step.DETAILS && mode != Mode.DONE) {
            showStep(Step.WIDGET)
            return
        }
        when (mode) {
            Mode.ADD -> addToSelected(null)
            Mode.CREATE_NEW -> placeNewWidget()
            Mode.DONE -> finish()
            Mode.GRID_FULL -> Unit
        }
    }

    private fun addToSelected(growth: Growth?) {
        val id = selectedWidget ?: return
        val entry = currentEntry()
        busy = true
        updateButtons()
        scope.launch {
            val after = withContext(Dispatchers.IO) {
                store.update { it.addEntry(id, entry, growth) }?.also { WidgetUpdater.update(applicationContext, listOf(id)) }
            }
            busy = false
            if (after == null) {
                // Became full meanwhile (or cannot grow): show the current state again.
                state = withContext(Dispatchers.IO) { store.read() }
                updateMode()
                return@launch
            }
            val target = after.configs[id]?.summary().orEmpty()
            Log.i(TAG, "added '${entry.label}' to widget $id: $target")
            Toast.makeText(applicationContext, getString(R.string.added_to, target), Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun pinSupported(): Boolean = AppWidgetManager.getInstance(this).isRequestPinAppWidgetSupported

    /**
     * "+ Create new widget" (decisions 5.7, supersedes the 3.2 "only when none is bound" rule): the entry goes to the
     * pending list first (a newly placed widget takes it over), then the launcher is asked to pin a widget — also when
     * widgets already exist; the success callback carries the entry too (decisions 3.3). Pin unsupported, refused or
     * cancelled → the place stays pending and the sheet says so.
     */
    private fun placeNewWidget() {
        val entry = currentEntry()
        busy = true
        updateButtons()
        scope.launch {
            val baseline = withContext(Dispatchers.IO) {
                store.update { it.addPending(entry) }
                store.boundWidgetIds().toSet()
            }
            busy = false
            val requested = pinSupported() && runCatching { requestPin(entry) }
                .onFailure { Log.w(TAG, "requestPinAppWidget failed", it) }.getOrDefault(false)
            Log.i(TAG, "requestPinAppWidget for '${entry.label}' (bound before: $baseline): $requested")
            if (requested) {
                pinBaseline = baseline
                pinRequestedFor = entry
                pausedSincePinRequest = false
                showDone(getString(R.string.pin_requested))
            } else {
                showDone(getString(R.string.saved_pending))
            }
        }
    }

    private fun requestPin(entry: Entry): Boolean {
        val callback = Intent(this, PinWidgetCallbackReceiver::class.java)
            .setData(Uri.parse("navshort://pin/${entry.id}"))
            .putExtra(PinWidgetCallbackReceiver.EXTRA_ENTRY_JSON, WidgetStore.json.encodeToString(Entry.serializer(), entry))
        // Mutable: the launcher fills in EXTRA_APPWIDGET_ID. Explicit component, so this is allowed on API 34+.
        val pending = PendingIntent.getBroadcast(
            this,
            0,
            callback,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return AppWidgetManager.getInstance(this)
            .requestPinAppWidget(ComponentName(this, NavigationWidgetProvider::class.java), null, pending)
    }

    /** Replaces the form with a message and a single "OK" that closes the sheet. */
    private fun showDone(message: String) {
        mode = Mode.DONE
        form.visibility = View.GONE
        growButtons.visibility = View.GONE
        messageText.visibility = View.VISIBLE
        messageText.text = message
        addButton.visibility = View.VISIBLE
        addButton.isEnabled = true
        addButton.setText(R.string.ok)
        cancelButton.visibility = View.GONE
        widgetStep.visibility = View.GONE
        setBackToDetails(false)
        widgetHint.visibility = View.GONE
        gridTip.visibility = View.GONE
    }

    /** Icon style of the widget the place goes to (the default style when there is none yet). */
    private fun currentStyle() = selectedWidget?.let { state.configs[it]?.style } ?: WidgetConfig.DEFAULT_STYLE

    private fun setIcon(ref: IconRef) {
        icon = ref
        val sizePx = (40 * resources.displayMetrics.density).toInt()
        IconImages.bind(iconButton, scope, ref, currentStyle(), entryId, sizePx)
        iconButton.contentDescription = getString(R.string.change_icon) + ": " + IconNames.readable(ref)
    }

    private fun showIconPicker() {
        IconPicker.show(this, scope, currentStyle(), entryId, icon) { picked ->
            iconChosenByUser = true
            setIcon(picked)
        }
    }

    private companion object {
        const val TAG = "NavShortShare"

        /** From this many rows the default Niagara slot (315×80 dp) gets too short; suggest Move and resize. */
        const val TALL_GRID_ROWS = 3
    }
}
