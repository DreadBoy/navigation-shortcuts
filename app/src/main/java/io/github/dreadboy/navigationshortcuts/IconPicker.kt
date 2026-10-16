package io.github.dreadboy.navigationshortcuts

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconSettings
import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shows an [IconRef] in an ImageView as the Niagara bitmap in [style] (also for `builtin` refs, decisions 5.6), or the
 * built-in Material drawable when Niagara is missing or the glyph can't be drawn. Empty until then, so no Material
 * icon flashes up while Niagara is readable.
 */
object IconImages {
    fun bind(view: ImageView, scope: CoroutineScope, icon: IconRef, style: AnyconSettings, seedKey: String, sizePx: Int) {
        val tag = BindTag(icon, style, seedKey)
        view.tag = tag
        view.setImageDrawable(null)
        scope.launch {
            val bitmap = withContext(Dispatchers.Default) {
                try {
                    IconRendering.draw(IconRendering.session(view.context), icon, style, sizePx, seedKey).bitmap
                } catch (e: Exception) {
                    null
                }
            }
            if (view.tag != tag) return@launch
            if (bitmap != null) view.setImageBitmap(bitmap) else view.setImageResource(IconResolver.drawableRes(icon))
        }
    }

    private data class BindTag(val icon: IconRef, val style: AnyconSettings, val seedKey: String)
}

/**
 * Icon picker for the share sheet and the config screen (decisions 5.5/5.6): Niagara's category and action glyphs drawn
 * in the widget's [style] (searchable by name) while Niagara is readable; otherwise only the built-in Material set.
 */
object IconPicker {
    /** [search]: what the search field matches (label plus aliases, e.g. "pool" for the glyph mapped to it). */
    private data class Item(val ref: IconRef, val label: String, val description: String, val search: String = label)

    fun show(
        activity: Activity,
        scope: CoroutineScope,
        style: AnyconSettings,
        seedKey: String,
        current: IconRef?,
        onPick: (IconRef) -> Unit,
    ) {
        scope.launch {
            val (installed, glyphs) = withContext(Dispatchers.IO) {
                val session = IconRendering.session(activity)
                session.niagaraInstalled to IconRendering.pickableGlyphs(activity, session)
            }
            if (activity.isFinishing || activity.isDestroyed) return@launch
            val niagara = installed && glyphs.isNotEmpty()
            val hidden = if (style.style is AnyconStyle.Sticker) NiagaraGlyphs.brokenInSticker else emptySet()
            val items = if (niagara) {
                // The 14 concept names find their mapped glyph ("pool" → jellyfish, decisions 5.6).
                val aliases = NiagaraGlyphs.forConcept.entries.groupBy({ it.value }, { it.key })
                glyphs.filterNot { it in hidden }.map {
                    val name = NiagaraGlyphs.displayName(it)
                    Item(IconRef.niagara(it), name, name, (listOf(name) + aliases[it].orEmpty()).joinToString(" "))
                }
            } else {
                BuiltInIcons.all.map { (name, _) ->
                    Item(IconRef.builtin(name), name, activity.getString(R.string.builtin_icon, name))
                }
            }
            build(activity, scope, style, seedKey, highlighted(current, niagara), items, niagara, onPick)
        }
    }

    /** The picker entry shown for [current]: its Niagara equivalent while Niagara is readable, else its built-in icon. */
    private fun highlighted(current: IconRef?, niagara: Boolean): IconRef? = when {
        current == null -> null
        niagara -> NiagaraGlyphs.glyphsFor(current).firstOrNull()?.let(IconRef::niagara)
        current.source == IconRef.SOURCE_BUILTIN -> current
        else -> IconRef.builtin(NiagaraGlyphs.fallbackFor(current.name))
    }

    private fun build(
        activity: Activity,
        scope: CoroutineScope,
        style: AnyconSettings,
        seedKey: String,
        current: IconRef?,
        all: List<Item>,
        niagara: Boolean,
        onPick: (IconRef) -> Unit,
    ) {
        val density = activity.resources.displayMetrics.density
        val iconPx = (44 * density).toInt()
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
        val empty = TextView(activity).apply {
            id = R.id.icon_empty
            setText(R.string.no_icons_match)
            setPadding(0, pad, 0, pad)
            visibility = View.GONE
        }
        lateinit var dialog: AlertDialog
        val adapter = object : BaseAdapter() {
            override fun getCount() = shown.size
            override fun getItem(position: Int) = shown[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val cell = (convertView as? LinearLayout) ?: LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    setPadding(pad / 4, pad / 3, pad / 4, pad / 3)
                    addView(ImageView(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(iconPx, iconPx)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                    })
                    addView(TextView(activity).apply {
                        textSize = 11f
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                        gravity = Gravity.CENTER
                    })
                }
                val item = shown[position]
                IconImages.bind(cell.getChildAt(0) as ImageView, scope, item.ref, style, seedKey, iconPx)
                (cell.getChildAt(1) as TextView).text = item.label
                cell.contentDescription = item.description
                cell.setBackgroundColor(if (item.ref == current) SELECTED else Color.TRANSPARENT)
                return cell
            }
        }
        val grid = GridView(activity).apply {
            id = R.id.icon_grid
            numColumns = GridView.AUTO_FIT
            columnWidth = (72 * density).toInt()
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (380 * density).toInt())
            this.adapter = adapter
            setOnItemClickListener { _, _, position, _ ->
                onPick(shown[position].ref)
                dialog.dismiss()
            }
        }
        root.addView(empty)
        root.addView(grid)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.trim()?.lowercase().orEmpty()
                shown = if (q.isEmpty()) all else all.filter { q in it.search.lowercase() }
                adapter.notifyDataSetChanged()
                empty.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
            }
        })
        dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.pick_icon)
            .setView(root)
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private const val SELECTED = 0x332E9D57
}
