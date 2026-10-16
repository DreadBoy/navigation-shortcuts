package io.github.dreadboy.navigationshortcuts

import android.app.Activity
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Share target "Add to navigation widget" (spec Flow 2, PLAN 3.5–3.7): a bottom sheet over Google Maps.
 * Resolves the shared link once (background thread, short timeout), lets the user adjust label / destination /
 * icon, picks the widget (hidden when there is one), grows a full grid in one tap, or places a new widget.
 */
class AddDestinationActivity : Activity() {

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
    private lateinit var placeNewButton: Button
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
    private var notSeen: Map<Int, Long> = emptyMap() // appWidgetId → render pending since (flagged widgets only)
    private var selectedWidget: Int? = null
    private var mode = Mode.ADD
    private var busy = false

    /** Set when requestPinAppWidget showed the launcher's confirmation; checked when we come back. */
    private var pinRequestedFor: Entry? = null
    private var pausedSincePinRequest = false

    /** Widget IDs bound when the pin was requested; a new ID afterwards means the launcher placed the widget. */
    private var pinBaseline: Set<Int> = emptySet()

    private enum class Mode { ADD, GRID_FULL, NO_WIDGET, DONE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_destination)
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        window.setGravity(Gravity.BOTTOM)
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        store = WidgetStore(this)

        labelInput = findViewById(R.id.label_input)
        destinationInput = findViewById(R.id.destination_input)
        iconButton = findViewById(R.id.icon_button)
        statusText = findViewById(R.id.status_text)
        progress = findViewById(R.id.resolving_progress)
        widgetHeading = findViewById(R.id.widget_heading)
        widgetHint = findViewById(R.id.widget_hint)
        gridTip = findViewById(R.id.grid_tip)
        placeNewButton = findViewById(R.id.place_new_button)
        chips = findViewById(R.id.widget_chips)
        messageText = findViewById(R.id.message_text)
        growButtons = findViewById(R.id.grow_buttons)
        addColumnButton = findViewById(R.id.add_column_button)
        addRowButton = findViewById(R.id.add_row_button)
        addButton = findViewById(R.id.add_button)
        form = findViewById(R.id.form)

        findViewById<Button>(R.id.cancel_button).setOnClickListener { finish() }
        iconButton.setOnClickListener { showIconPicker() }
        addButton.setOnClickListener { onPrimary() }
        placeNewButton.setOnClickListener { saveForNewWidget() }
        addColumnButton.setOnClickListener { addToSelected(Growth.COLUMN) }
        addRowButton.setOnClickListener { addToSelected(Growth.ROW) }
        val refresh = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = updateButtons()
        }
        labelInput.addTextChangedListener(refresh)
        destinationInput.addTextChangedListener(refresh)
        chips.setOnCheckedChangeListener { _, checkedId ->
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
        loadWidgets()
        resolve(text)
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
            if (!iconChosenByUser) setIcon(IconGuess.guess(labelInput.text.toString()))
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
            val multiple = widgetIds.size > 1
            widgetHeading.visibility = if (multiple) View.VISIBLE else View.GONE
            chips.visibility = if (multiple) View.VISIBLE else View.GONE
            selectedWidget = widgetIds.firstOrNull()
            if (multiple) chips.check(firstChip)
            updateMode()
            setIcon(icon)
        }
    }

    private fun updateMode() {
        val id = selectedWidget
        val config = id?.let { state.configs[it] ?: WidgetConfig() }
        mode = when {
            mode == Mode.DONE -> Mode.DONE
            id == null -> Mode.NO_WIDGET
            config!!.isFull -> Mode.GRID_FULL
            else -> Mode.ADD
        }
        val since = id?.let { notSeen[it] }
        widgetHint.visibility = if (since != null && mode != Mode.DONE) View.VISIBLE else View.GONE
        if (since != null) {
            widgetHint.text = getString(
                R.string.not_seen_hint,
                DateUtils.formatDateTime(this, since, DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE),
            )
        }
        // Secondary way out when widgets exist but the chosen one may not be the one on screen.
        placeNewButton.visibility = if (id != null && mode != Mode.DONE) View.VISIBLE else View.GONE
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
            Mode.NO_WIDGET -> {
                messageText.visibility = View.VISIBLE
                messageText.setText(R.string.no_widget)
                growButtons.visibility = View.GONE
                addButton.visibility = View.VISIBLE
                addButton.setText(if (pinSupported()) R.string.place_new_widget else R.string.save_for_later)
            }
            Mode.DONE -> Unit
        }
        updateButtons()
    }

    private fun updateButtons() {
        if (mode == Mode.DONE) return
        val ready = resolved && !busy && labelInput.text.isNotBlank() && destinationInput.text.isNotBlank()
        addButton.isEnabled = ready
        placeNewButton.isEnabled = ready
        addColumnButton.isEnabled = ready
        addRowButton.isEnabled = ready
    }

    private fun currentEntry(): Entry =
        Entry(label = labelInput.text.toString().trim(), destination = destinationInput.text.toString().trim(), icon = icon, id = entryId)

    private fun onPrimary() {
        when (mode) {
            Mode.ADD -> addToSelected(null)
            Mode.NO_WIDGET -> placeNewWidget()
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

    /**
     * Secondary action while widgets exist (way out when the chosen one isn't on screen, decisions 3.9): save to the
     * pending list for the next widget the user places. No requestPinAppWidget here: with a widget already placed,
     * free Niagara answers it with an unusable "Create Widget Stack?" state (decisions 3.2).
     */
    private fun saveForNewWidget() {
        val entry = currentEntry()
        busy = true
        updateButtons()
        scope.launch {
            withContext(Dispatchers.IO) { store.update { it.addPending(entry) } }
            busy = false
            Log.i(TAG, "saved '${entry.label}' for a new widget")
            showDone(getString(R.string.saved_for_new_widget))
        }
    }

    private fun pinSupported(): Boolean = AppWidgetManager.getInstance(this).isRequestPinAppWidgetSupported

    /**
     * "Place new widget" (primary with no widget, secondary otherwise): the entry goes to the pending list first (a
     * newly placed widget takes it over), then the launcher is asked to pin a widget; the success callback carries the
     * entry too (decisions 3.3).
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
        findViewById<View>(R.id.cancel_button).visibility = View.GONE
        placeNewButton.visibility = View.GONE
        widgetHint.visibility = View.GONE
        gridTip.visibility = View.GONE
    }

    /** Icon style of the widget the place goes to (the default style when there is none yet). */
    private fun currentStyle() = selectedWidget?.let { state.configs[it]?.style } ?: WidgetConfig.DEFAULT_STYLE

    private fun setIcon(ref: IconRef) {
        icon = ref
        val sizePx = (40 * resources.displayMetrics.density).toInt()
        IconImages.bind(iconButton, scope, ref, currentStyle(), entryId, sizePx)
        iconButton.contentDescription = getString(R.string.change_icon) + ": " + ref.name
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
