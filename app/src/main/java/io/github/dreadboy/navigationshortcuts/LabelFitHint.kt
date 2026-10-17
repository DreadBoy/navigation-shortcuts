package io.github.dreadboy.navigationshortcuts

import android.content.Context
import android.graphics.Typeface
import android.text.TextPaint
import android.util.TypedValue
import android.view.View
import android.widget.TextView

/**
 * Long Maps names (decisions 5b E14): under a label field, "On the widget: "Stožice Sta…". A shorter label fits." when
 * the label would be cut in a cell of a widget with [cols] columns (Niagara's default 315 dp slot, the cell label's
 * font and size). Hidden while the label fits.
 */
object LabelFitHint {
    fun update(hint: TextView, label: String, cols: Int, iconSize: IconSize) {
        val shown = shown(hint.context, label, cols, iconSize)
        if (shown == null) {
            hint.visibility = View.GONE
        } else {
            hint.text = hint.context.getString(R.string.label_shown_as, shown)
            hint.visibility = View.VISIBLE
        }
    }

    /** The cut label as the cell shows it, or null when [label] is blank or fits. */
    fun shown(context: Context, label: String, cols: Int, iconSize: IconSize): String? {
        if (label.isBlank()) return null
        val metrics = context.resources.displayMetrics
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.SANS_SERIF
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, iconSize.fitting(cols).labelSp.toFloat(), metrics)
        }
        val width = SheetTexts.labelWidthDp(cols) * metrics.density
        val shown = SheetTexts.shownLabel(label, width) { paint.measureText(it) }
        return shown.takeIf { it != label.trim() }
    }
}
