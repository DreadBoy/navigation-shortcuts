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

/** Shows an [IconRef] in an ImageView: the built-in drawable at once, the Niagara bitmap in [style] when drawn. */
object IconImages {
    fun bind(view: ImageView, scope: CoroutineScope, icon: IconRef, style: AnyconSettings, seedKey: String, sizePx: Int) {
        view.tag = BindTag(icon, style, seedKey)
        view.setImageResource(IconResolver.drawableRes(icon))
        if (icon.source != IconRef.SOURCE_NIAGARA) return
        val tag = view.tag
        scope.launch {
            val bitmap = withContext(Dispatchers.Default) {
                IconRendering.draw(IconRendering.session(view.context), icon, style, sizePx, seedKey).bitmap
            }
            if (bitmap != null && view.tag == tag) view.setImageBitmap(bitmap)
        }
    }

    private data class BindTag(val icon: IconRef, val style: AnyconSettings, val seedKey: String)
}

/**
 * Icon picker for the share sheet and the config screen (decisions 5.5): Niagara's category and action glyphs drawn in
 * the widget's [style] (searchable by name), then the built-in set, which is the only set when Niagara is missing.
 */
object IconPicker {
    private data class Item(val ref: IconRef, val label: String, val description: String)

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
            val hidden = if (style.style is AnyconStyle.Sticker) NiagaraGlyphs.brokenInSticker else emptySet()
            val items = glyphs.filterNot { it in hidden }.map {
                val name = NiagaraGlyphs.displayName(it)
                Item(IconRef.niagara(it), name, name)
            } + BuiltInIcons.all.map { (name, _) ->
                Item(IconRef.builtin(name), name, activity.getString(R.string.builtin_icon, name))
            }
            build(activity, scope, style, seedKey, current, items, installed && glyphs.isNotEmpty(), onPick)
        }
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
        root.addView(grid)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.trim()?.lowercase().orEmpty()
                shown = if (q.isEmpty()) all else all.filter { q in it.label.lowercase() }
                adapter.notifyDataSetChanged()
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
