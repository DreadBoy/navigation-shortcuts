package io.github.dreadboy.navigationshortcuts

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.SystemClock
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import io.github.dreadboy.navigationshortcuts.IconCatalog.Entry
import io.github.dreadboy.navigationshortcuts.IconCatalog.Section
import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconSettings
import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shows an [IconRef] in an ImageView as the Niagara bitmap in [style] (also for `builtin` refs, decisions 5.6), or the
 * built-in Material drawable when Niagara is missing or the glyph can't be drawn. Empty until then, so no Material
 * icon flashes up while Niagara is readable. The Material drawables are white (made for the widget over a
 * wallpaper), so in our screens they are tinted with the theme's text colour to stay visible in light mode (E7).
 */
object IconImages {
    fun bind(view: ImageView, scope: CoroutineScope, icon: IconRef, style: AnyconSettings, seedKey: String, sizePx: Int) {
        val tag = BindTag(icon, style, seedKey)
        view.tag = tag
        view.setImageDrawable(null)
        view.imageTintList = null
        scope.launch {
            val bitmap = withContext(Dispatchers.Default) {
                try {
                    IconRendering.draw(IconRendering.session(view.context), icon, style, sizePx, seedKey).bitmap
                } catch (e: Exception) {
                    null
                }
            }
            if (view.tag != tag) return@launch
            if (bitmap != null) {
                view.setImageBitmap(bitmap)
            } else {
                view.setImageResource(IconResolver.drawableRes(icon))
                view.imageTintList = ColorStateList.valueOf(themeColor(view, android.R.attr.textColorPrimary))
            }
        }
    }

    /** A colour attribute of [view]'s theme (colour or colour-state-list resource), grey if it can't be resolved. */
    fun themeColor(view: View, attr: Int): Int {
        val value = TypedValue()
        val context = view.context
        if (!context.theme.resolveAttribute(attr, value, true)) return Color.GRAY
        return if (value.resourceId != 0) context.getColorStateList(value.resourceId).defaultColor else value.data
    }

    private data class BindTag(val icon: IconRef, val style: AnyconSettings, val seedKey: String)
}

/**
 * Icon picker for the share sheet and the config screen (decisions 5.5/5.6, 5b): while Niagara is readable its
 * glyphs drawn in the widget's [style] — the current icon on top, then places, other icons and apps ([IconCatalog]),
 * searchable by name and synonyms; otherwise only the built-in Material set. A lazy list of icon rows: only visible
 * cells are rendered, so ~880 entries open as fast as a few dozen.
 */
object IconPicker {
    private const val TAG = "NavShortPicker"

    fun show(
        activity: Activity,
        scope: CoroutineScope,
        style: AnyconSettings,
        seedKey: String,
        current: IconRef?,
        onPick: (IconRef) -> Unit,
    ) {
        val t0 = SystemClock.elapsedRealtime()
        scope.launch {
            val (niagara, items) = withContext(Dispatchers.Default) {
                val session = IconRendering.session(activity)
                val glyphs = IconRendering.pickableGlyphs(activity, session)
                val niagara = session.niagaraInstalled && glyphs.isNotEmpty()
                val hidden = if (style.style is AnyconStyle.Sticker) NiagaraGlyphs.brokenInSticker else emptySet()
                niagara to if (niagara) catalog(glyphs, hidden) else IconCatalog.builtin()
            }
            if (activity.isFinishing || activity.isDestroyed) return@launch
            val selected = highlighted(current, niagara)
            build(activity, scope, style, seedKey, selected, IconCatalog.withCurrent(items, selected), niagara, t0, onPick)
        }
    }

    private var cached: Triple<List<String>, Set<String>, List<Entry>>? = null

    /** The Niagara catalog, rebuilt only when the enumerated glyph list (a new list when Niagara changes) or style does. */
    @Synchronized
    private fun catalog(glyphs: List<String>, hidden: Set<String>): List<Entry> {
        cached?.let { (g, h, entries) -> if (g === glyphs && h == hidden) return entries }
        val t0 = SystemClock.elapsedRealtime()
        return IconCatalog.niagara(glyphs, hidden).also {
            Log.i(TAG, "catalog of ${it.size} entries built in ${SystemClock.elapsedRealtime() - t0} ms")
            cached = Triple(glyphs, hidden, it)
        }
    }

    /** The picker entry shown for [current]: its Niagara equivalent while Niagara is readable, else its built-in icon. */
    private fun highlighted(current: IconRef?, niagara: Boolean): IconRef? = when {
        current == null -> null
        niagara -> NiagaraGlyphs.glyphsFor(current).firstOrNull()?.let(IconRef::niagara)
        current.source == IconRef.SOURCE_BUILTIN -> current
        else -> IconRef.builtin(NiagaraGlyphs.fallbackFor(current.name))
    }

    /** A list row: a section title or a row of icon cells. */
    private sealed interface Row {
        data class Header(val section: Section) : Row
        data class Icons(val entries: List<Entry>) : Row
    }

    private fun rows(entries: List<Entry>, columns: Int): List<Row> {
        val bySection = entries.groupBy { it.section }
        // Section titles only when there is more than one section (the Material set is a single list).
        val headers = bySection.size > 1
        val rows = ArrayList<Row>()
        for ((section, inSection) in bySection) {
            if (headers) rows += Row.Header(section)
            inSection.chunked(columns).forEach { rows += Row.Icons(it) }
        }
        return rows
    }

    private fun sectionTitle(section: Section): Int = when (section) {
        Section.CURRENT -> R.string.icons_current
        Section.PLACES -> R.string.icons_places
        Section.MORE -> R.string.icons_more
        Section.APPS -> R.string.icons_apps
    }

    private fun build(
        activity: Activity,
        scope: CoroutineScope,
        style: AnyconSettings,
        seedKey: String,
        current: IconRef?,
        all: List<Entry>,
        niagara: Boolean,
        startedAt: Long,
        onPick: (IconRef) -> Unit,
    ) {
        val density = activity.resources.displayMetrics.density
        val iconPx = (44 * density).toInt()
        val cellMinPx = (72 * density).toInt()
        val pad = (12 * density).toInt()
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }
        val search = EditText(activity).apply {
            id = R.id.icon_search
            hint = activity.getString(R.string.search_icons)
            setSingleLine()
        }
        root.addView(search)
        if (!niagara) {
            root.addView(TextView(activity).apply {
                setText(R.string.niagara_missing_icons)
                setPadding(0, pad / 2, 0, pad / 2)
            })
        }
        var shown = all
        var columns = 4
        var rows = rows(shown, columns)
        lateinit var dialog: AlertDialog
        val empty = TextView(activity).apply {
            id = R.id.icon_empty
            setText(R.string.no_icons_match)
            setPadding(0, pad, 0, pad)
            visibility = View.GONE
        }

        fun cell(): LinearLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isClickable = true
            isFocusable = true
            setPadding(pad / 4, pad / 3, pad / 4, pad / 3)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(ImageView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(iconPx, iconPx)
                scaleType = ImageView.ScaleType.FIT_CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            })
            addView(TextView(activity).apply {
                textSize = 11f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            })
        }

        fun bindCell(cell: LinearLayout, entry: Entry?) {
            if (entry == null) {
                cell.visibility = View.INVISIBLE
                cell.tag = null
                cell.contentDescription = null
                cell.setOnClickListener(null)
                return
            }
            cell.visibility = View.VISIBLE
            if (cell.tag != entry) {
                cell.tag = entry
                IconImages.bind(cell.getChildAt(0) as ImageView, scope, entry.ref, style, seedKey, iconPx)
                (cell.getChildAt(1) as TextView).text = entry.label
                cell.contentDescription = entry.label
            }
            val selected = entry.ref == current
            cell.isSelected = selected
            cell.setBackgroundColor(if (selected) SELECTED else Color.TRANSPARENT)
            cell.setOnClickListener {
                onPick(entry.ref)
                dialog.dismiss()
            }
        }

        val adapter = object : BaseAdapter() {
            override fun getCount() = rows.size
            override fun getItem(position: Int) = rows[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getViewTypeCount() = 2
            override fun getItemViewType(position: Int) = if (rows[position] is Row.Header) 0 else 1
            override fun areAllItemsEnabled() = false
            override fun isEnabled(position: Int) = false

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                when (val row = rows[position]) {
                    is Row.Header -> ((convertView as? TextView) ?: TextView(activity).apply {
                        setPadding(pad / 4, pad, pad / 4, pad / 3)
                        setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Small)
                        setTextColor(IconImages.themeColor(parent, android.R.attr.colorAccent))
                        isAccessibilityHeading = true
                    }).apply { setText(sectionTitle(row.section)) }

                    is Row.Icons -> {
                        val line = (convertView as? LinearLayout)?.takeIf { it.childCount == columns }
                            ?: LinearLayout(activity).apply {
                                orientation = LinearLayout.HORIZONTAL
                                layoutParams = AbsListView.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                                )
                                repeat(columns) { addView(cell()) }
                            }
                        for (i in 0 until columns) bindCell(line.getChildAt(i) as LinearLayout, row.entries.getOrNull(i))
                        line
                    }
                }
        }
        val list = ListView(activity).apply {
            id = R.id.icon_grid
            divider = null
            dividerHeight = 0
            setSelector(android.R.color.transparent)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (380 * density).toInt())
            this.adapter = adapter
            addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                val fit = ((v.width - v.paddingLeft - v.paddingRight) / cellMinPx).coerceAtLeast(1)
                if (v.width > 0 && fit != columns) {
                    columns = fit
                    rows = rows(shown, columns)
                    v.post { adapter.notifyDataSetChanged() }
                }
            }
        }
        root.addView(empty)
        root.addView(list)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                shown = IconCatalog.filter(all, s?.toString().orEmpty())
                rows = rows(shown, columns)
                adapter.notifyDataSetChanged()
                list.setSelection(0)
                empty.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
            }
        })
        dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.pick_icon)
            .setView(root)
            .setNegativeButton(R.string.cancel, null)
            .show()
        list.post {
            Log.i(TAG, "picker open in ${SystemClock.elapsedRealtime() - startedAt} ms: ${all.size} entries, $columns columns")
        }
    }

    private const val SELECTED = 0x332E9D57
}
