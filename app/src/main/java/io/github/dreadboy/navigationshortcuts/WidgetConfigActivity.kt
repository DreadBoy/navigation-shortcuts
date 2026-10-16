package io.github.dreadboy.navigationshortcuts

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconSettings
import io.github.dreadboy.navigationshortcuts.anycons.settings.IconShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Per-widget config screen (PLAN 4.1, decisions 4.1): grid size, icon style with a live preview, and the destination
 * list (add manually, edit, change icon, delete, drag to reorder). Entry points: the widget's empty cells, Niagara's
 * long-press "Change widget settings" (APPWIDGET_CONFIGURE, `reconfigurable|configuration_optional`) and the app's
 * widget list. Changes apply on Save; the widget re-renders at once.
 */
class WidgetConfigActivity : Activity() {

    private val scope = MainScope()
    private lateinit var store: WidgetStore

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var original: WidgetConfig? = null
    private var rows = WidgetConfig.DEFAULT_ROWS
    private var cols = WidgetConfig.DEFAULT_COLS
    private val entries = mutableListOf<Entry>()
    private var style: AnyconSettings = WidgetConfig.DEFAULT_STYLE
    private var iconSize: IconSize = IconSize.DEFAULT
    private var saving = false

    private lateinit var scroll: ScrollView
    private lateinit var rowsValue: TextView
    private lateinit var colsValue: TextView
    private lateinit var rowsMinus: Button
    private lateinit var rowsPlus: Button
    private lateinit var colsMinus: Button
    private lateinit var colsPlus: Button
    private lateinit var gridNote: TextView
    private lateinit var gridTallNote: TextView
    private lateinit var iconSizeSpinner: Spinner
    private lateinit var colorLabel: View
    private lateinit var preview: LinearLayout
    private lateinit var styleNote: TextView
    private lateinit var packSpinner: Spinner
    private lateinit var dimensionsBox: LinearLayout
    private lateinit var shapeRow: View
    private lateinit var shapeSpinner: Spinner
    private lateinit var colorRow: LinearLayout
    private lateinit var entryList: LinearLayout
    private lateinit var emptyListText: TextView
    private lateinit var addButton: Button
    private lateinit var addNote: TextView
    private lateinit var saveButton: Button

    private val density get() = resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_config)
        store = WidgetStore(this)
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        // configuration_optional: when a launcher opens this on placement, backing out must not undo the placement.
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        Log.i(TAG, "config opened for widget $widgetId (action=${intent.action})")
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Toast.makeText(this, R.string.no_widget_id, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        scroll = findViewById(R.id.config_scroll)
        rowsValue = findViewById(R.id.rows_value)
        colsValue = findViewById(R.id.cols_value)
        rowsMinus = findViewById(R.id.rows_minus)
        rowsPlus = findViewById(R.id.rows_plus)
        colsMinus = findViewById(R.id.cols_minus)
        colsPlus = findViewById(R.id.cols_plus)
        gridNote = findViewById(R.id.grid_note)
        gridTallNote = findViewById(R.id.grid_tall_note)
        iconSizeSpinner = findViewById(R.id.icon_size)
        colorLabel = findViewById(R.id.color_label)
        preview = findViewById(R.id.style_preview)
        styleNote = findViewById(R.id.style_note)
        packSpinner = findViewById(R.id.style_pack)
        dimensionsBox = findViewById(R.id.style_dimensions)
        shapeRow = findViewById(R.id.shape_row)
        shapeSpinner = findViewById(R.id.style_shape)
        colorRow = findViewById(R.id.color_row)
        entryList = findViewById(R.id.entry_list)
        emptyListText = findViewById(R.id.empty_list_text)
        addButton = findViewById(R.id.add_manual_button)
        addNote = findViewById(R.id.add_note)
        saveButton = findViewById(R.id.config_save)

        rowsMinus.setOnClickListener { setGrid(rows - 1, cols) }
        rowsPlus.setOnClickListener { setGrid(rows + 1, cols) }
        colsMinus.setOnClickListener { setGrid(rows, cols - 1) }
        colsPlus.setOnClickListener { setGrid(rows, cols + 1) }
        addButton.setOnClickListener { showEntryDialog(null) }
        findViewById<Button>(R.id.config_cancel).setOnClickListener { finish() }
        saveButton.setOnClickListener { save() }
        saveButton.isEnabled = false
        load()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun load() {
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                if (widgetId !in store.boundWidgetIds()) return@withContext null
                // A widget without a config (just placed) gets the default grid and takes over pending places, exactly
                // as its first update does; idempotent, so it doesn't matter which runs first.
                val state = store.update { it.ensureConfig(widgetId) } ?: store.read()
                val config = state.configs[widgetId] ?: WidgetConfig()
                config to IconRendering.session(applicationContext).niagaraInstalled
            }
            if (loaded == null) {
                Log.w(TAG, "widget $widgetId is not bound to us")
                Toast.makeText(this@WidgetConfigActivity, R.string.no_widget_id, Toast.LENGTH_LONG).show()
                finish()
                return@launch
            }
            val (config, niagaraInstalled) = loaded
            original = config
            rows = config.rows
            cols = config.cols
            entries.clear()
            entries += config.entries
            style = config.style
            iconSize = config.iconSize
            styleNote.visibility = if (niagaraInstalled) View.GONE else View.VISIBLE
            setUpStyleControls()
            refreshGrid()
            rebuildList()
            saveButton.isEnabled = true
        }
    }

    // ------------------------------------------------------------------ grid

    /** Grid sizes the screen allows: 1–6 each, and never fewer cells than destinations (decisions 4.1). */
    private fun allowed(r: Int, c: Int): Boolean =
        r in WidgetConfig.MIN_SIZE..WidgetConfig.MAX_SIZE && c in WidgetConfig.MIN_SIZE..WidgetConfig.MAX_SIZE && r * c >= entries.size

    private fun setGrid(r: Int, c: Int) {
        if (!allowed(r, c)) return
        rows = r
        cols = c
        refreshGrid()
    }

    private fun refreshGrid() {
        rowsValue.text = rows.toString()
        colsValue.text = cols.toString()
        rowsMinus.isEnabled = allowed(rows - 1, cols)
        colsMinus.isEnabled = allowed(rows, cols - 1)
        rowsPlus.isEnabled = allowed(rows + 1, cols)
        colsPlus.isEnabled = allowed(rows, cols + 1)
        val blocked = (rows > 1 && rows * cols - cols < entries.size) || (cols > 1 && rows * cols - rows < entries.size)
        gridNote.visibility = if (blocked) View.VISIBLE else View.GONE
        gridNote.text = getString(R.string.grid_shrink_blocked, entries.size)
        // Growing rows: Niagara's slot doesn't grow with them (decisions 5b E3), as the share sheet says too.
        gridTallNote.visibility = if (rows >= TALL_HINT_ROWS && rows > (original?.rows ?: rows)) View.VISIBLE else View.GONE
        val atMax = entries.size >= WidgetConfig.MAX_SIZE * WidgetConfig.MAX_SIZE
        addButton.isEnabled = !atMax
        addNote.visibility = if (atMax) View.VISIBLE else View.GONE
    }

    /** Makes room for one more entry: columns first, then rows (as the share sheet's pending takeover does). */
    private fun growForOneMore() {
        if (rows * cols > entries.size) return
        if (cols < WidgetConfig.MAX_SIZE) cols++ else if (rows < WidgetConfig.MAX_SIZE) rows++
    }

    // ------------------------------------------------------------------ style

    private fun setUpStyleControls() {
        val packs = StyleOptions.packs
        packSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, packs.map { it.label })
        packSpinner.setSelection(packs.indexOfFirst { it.id == StyleOptions.packOf(style.style) }.coerceAtLeast(0), false)
        packSpinner.onItemSelectedListener = onSelected { position ->
            val pack = packs[position].id
            if (pack == StyleOptions.packOf(style.style)) return@onSelected
            style = style.copy(style = StyleOptions.build(pack, emptyMap()))
            buildDimensionSpinners()
            styleChanged()
        }

        val sizes = StyleOptions.iconSizes
        iconSizeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sizes.map { it.label })
        iconSizeSpinner.setSelection(sizes.indexOf(iconSize).coerceAtLeast(0), false)
        iconSizeSpinner.onItemSelectedListener = onSelected { position ->
            if (sizes[position] == iconSize) return@onSelected
            iconSize = sizes[position]
            updatePreview()
        }

        val shapes = StyleOptions.shapes
        shapeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, shapes.map { it.label })
        shapeSpinner.setSelection(shapes.indexOfFirst { it.id == style.shape.name }.coerceAtLeast(0), false)
        shapeSpinner.onItemSelectedListener = onSelected { position ->
            val shape = IconShape.valueOf(shapes[position].id)
            if (shape == style.shape) return@onSelected
            style = style.copy(shape = shape)
            styleChanged()
        }

        colorRow.removeAllViews()
        val swatchPx = (36 * density).toInt()
        val margin = (6 * density).toInt()
        for (color in StyleOptions.seedColors) {
            colorRow.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(swatchPx, swatchPx).apply { marginEnd = margin }
                contentDescription = getString(R.string.color_swatch, "#%06X".format(color and 0xFFFFFF))
                tag = color
                isClickable = true
                setOnClickListener {
                    style = style.copy(seedColor = color)
                    styleChanged()
                }
            })
        }
        buildDimensionSpinners()
        styleChanged()
    }

    private fun buildDimensionSpinners() {
        dimensionsBox.removeAllViews()
        val pack = StyleOptions.packOf(style.style)
        val selections = StyleOptions.selections(style.style).toMutableMap()
        for (dimension in StyleOptions.dimensions(pack)) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            row.addView(TextView(this).apply {
                text = dimension.label
                setTextColor(textColorSecondary())
                layoutParams = LinearLayout.LayoutParams((96 * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            val spinner = Spinner(this).apply {
                adapter = ArrayAdapter(
                    this@WidgetConfigActivity,
                    android.R.layout.simple_spinner_dropdown_item,
                    dimension.choices.map { it.label },
                )
                contentDescription = dimension.label
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setSelection(dimension.choices.indexOfFirst { it.id == selections[dimension.key] }.coerceAtLeast(0), false)
                onItemSelectedListener = onSelected { position ->
                    val id = dimension.choices[position].id
                    if (selections[dimension.key] == id) return@onSelected
                    selections[dimension.key] = id
                    style = style.copy(style = StyleOptions.build(pack, selections))
                    styleChanged()
                }
            }
            row.addView(spinner)
            dimensionsBox.addView(row)
        }
        shapeRow.visibility = if (StyleOptions.usesShape(pack)) View.VISIBLE else View.GONE
    }

    private fun styleChanged() {
        for (i in 0 until colorRow.childCount) {
            val swatch = colorRow.getChildAt(i)
            val color = swatch.tag as Int
            swatch.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                if (color == style.seedColor) setStroke((3 * density).toInt(), textColorPrimary())
            }
            swatch.isSelected = color == style.seedColor
        }
        // Swatches only where the colour is used (decisions 5b U/E8).
        val colorVisibility = if (StyleOptions.usesSeedColor(style.style)) View.VISIBLE else View.GONE
        colorLabel.visibility = colorVisibility
        colorRow.visibility = colorVisibility
        updatePreview()
        rebuildList()
    }

    private fun updatePreview() {
        preview.removeAllViews()
        val samples = StyleOptions.previewIcons(entries, PREVIEW_ICONS)
        // Shown at the widget's icon size, so the size setting is visible here (decisions 5b U).
        val sizePx = (iconSize.dp * density).toInt()
        val margin = (8 * density).toInt()
        for ((icon, seed) in samples) {
            val view = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply { setMargins(margin, 0, margin, 0) }
                scaleType = ImageView.ScaleType.FIT_CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            preview.addView(view)
            IconImages.bind(view, scope, icon, style, seed, sizePx)
        }
        preview.contentDescription = getString(R.string.style_preview) + ": " + StyleOptions.describe(style.style)
    }

    // ------------------------------------------------------------------ destinations

    private fun rebuildList() {
        entryList.removeAllViews()
        emptyListText.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        val iconPx = (44 * density).toInt()
        for ((index, entry) in entries.withIndex()) {
            val row = LayoutInflater.from(this).inflate(R.layout.config_entry_row, entryList, false)
            val icon = row.findViewById<ImageButton>(R.id.entry_icon)
            IconImages.bind(icon, scope, entry.icon, style, entry.id, iconPx)
            icon.contentDescription = getString(R.string.icon_for_entry, entry.label)
            icon.setOnClickListener {
                IconPicker.show(this, scope, style, entry.id, entry.icon) { picked ->
                    val at = entries.indexOfFirst { it.id == entry.id }
                    if (at >= 0) entries[at] = entries[at].copy(icon = picked)
                    rebuildList()
                    updatePreview()
                }
            }
            row.findViewById<TextView>(R.id.entry_label).text = entry.label
            row.findViewById<TextView>(R.id.entry_destination).text = entry.destination
            row.findViewById<View>(R.id.entry_text).setOnClickListener { showEntryDialog(entry) }
            row.findViewById<ImageButton>(R.id.entry_edit).apply {
                contentDescription = getString(R.string.edit_entry, entry.label)
                setOnClickListener { showEntryDialog(entry) }
            }
            row.findViewById<ImageButton>(R.id.entry_delete).apply {
                contentDescription = getString(R.string.delete_entry, entry.label)
                setOnClickListener {
                    entries.removeAll { it.id == entry.id }
                    Log.i(TAG, "deleted '${entry.label}' (not saved yet)")
                    rebuildList()
                    refreshGrid()
                    updatePreview()
                }
            }
            row.findViewById<View>(R.id.entry_drag).apply {
                contentDescription = getString(R.string.reorder_entry, entry.label)
                setOnTouchListener(DragToReorder(row, index))
            }
            entryList.addView(row)
        }
    }

    /**
     * Drag handle: the row follows the finger; on release it moves to the row under its centre. Starts on touch down
     * (no long-press), so a plain drag gesture reorders.
     */
    private inner class DragToReorder(private val row: View, private val from: Int) : View.OnTouchListener {
        private var startY = 0f

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    scroll.requestDisallowInterceptTouchEvent(true)
                    row.elevation = 8 * density
                    row.alpha = 0.9f
                }
                MotionEvent.ACTION_MOVE -> row.translationY = event.rawY - startY
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val centre = row.top + row.height / 2f + row.translationY
                    val to = (0 until entryList.childCount).firstOrNull { centre < entryList.getChildAt(it).bottom }
                        ?: (entryList.childCount - 1)
                    row.translationY = 0f
                    row.elevation = 0f
                    row.alpha = 1f
                    scroll.requestDisallowInterceptTouchEvent(false)
                    if (event.actionMasked == MotionEvent.ACTION_UP && to != from && from in entries.indices) {
                        val moved = entries.removeAt(from)
                        entries.add(to.coerceIn(0, entries.size), moved)
                        Log.i(TAG, "moved '${moved.label}' $from → $to (not saved yet)")
                        rebuildList()
                        updatePreview()
                    }
                }
            }
            return true
        }
    }

    /** Add (existing == null) or edit a destination: label + destination text. New entries get a guessed icon. */
    private fun showEntryDialog(existing: Entry?) {
        val pad = (20 * density).toInt()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }
        val labelInput = EditText(this).apply {
            id = R.id.dialog_label
            hint = getString(R.string.field_label)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setSingleLine()
            setText(existing?.label.orEmpty())
        }
        val destinationInput = EditText(this).apply {
            id = R.id.dialog_destination
            hint = getString(R.string.destination_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS
            setSingleLine()
            setText(existing?.destination.orEmpty())
        }
        box.addView(labelInput)
        box.addView(destinationInput)
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) R.string.add_destination else R.string.edit_destination)
            .setView(box)
            .setPositiveButton(R.string.ok) { _, _ ->
                val label = labelInput.text.toString().trim()
                val destination = destinationInput.text.toString().trim()
                if (label.isEmpty() || destination.isEmpty()) return@setPositiveButton
                if (existing == null) {
                    growForOneMore()
                    if (rows * cols <= entries.size) return@setPositiveButton
                    entries += Entry(label = label, destination = destination, icon = IconGuess.guess(label, destination))
                    Log.i(TAG, "added '$label' manually (not saved yet)")
                } else {
                    val at = entries.indexOfFirst { it.id == existing.id }
                    if (at >= 0) entries[at] = entries[at].copy(label = label, destination = destination)
                }
                refreshGrid()
                rebuildList()
                updatePreview()
                // Show the new destination (and "Add manually" for the next one).
                if (existing == null) scroll.post { scroll.smoothScrollTo(0, addButton.bottom) }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
        val ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        val validate = {
            ok.isEnabled = labelInput.text.isNotBlank() && destinationInput.text.isNotBlank()
        }
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = validate()
        }
        labelInput.addTextChangedListener(watcher)
        destinationInput.addTextChangedListener(watcher)
        validate()
    }

    // ------------------------------------------------------------------ save

    private fun save() {
        val before = original ?: return
        if (saving) return
        saving = true
        saveButton.isEnabled = false
        val edited = WidgetConfig(rows, cols, entries.toList(), style, iconSize)
        scope.launch {
            withContext(Dispatchers.IO) {
                store.update { it.saveEdited(widgetId, before, edited) }
                WidgetUpdater.update(applicationContext, listOf(widgetId))
            }
            Log.i(TAG, "saved widget $widgetId: ${rows}×$cols, ${entries.size} entries, style ${StyleOptions.describe(style.style)}")
            finish()
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun onSelected(block: (Int) -> Unit) = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = block(position)
        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
    }

    private fun themeColor(attr: Int): Int {
        val a = obtainStyledAttributes(intArrayOf(attr))
        return try {
            a.getColor(0, Color.GRAY)
        } finally {
            a.recycle()
        }
    }

    private fun textColorPrimary() = themeColor(android.R.attr.textColorPrimary)
    private fun textColorSecondary() = themeColor(android.R.attr.textColorSecondary)

    companion object {
        private const val TAG = "NavShortConfig"
        private const val PREVIEW_ICONS = 4

        /** From this many rows the default Niagara slot (315×80 dp) is too short for icons with labels. */
        const val TALL_HINT_ROWS = 2

        /** Opens the config for [appWidgetId]; from the widget it starts its own task (no back stack into the app). */
        fun intent(context: Context, appWidgetId: Int, fromWidget: Boolean): Intent =
            Intent(context, WidgetConfigActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                .apply { if (fromWidget) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) }
    }
}
