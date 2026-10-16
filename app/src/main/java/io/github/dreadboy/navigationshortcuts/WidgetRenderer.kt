package io.github.dreadboy.navigationshortcuts

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Parcel
import android.util.Log
import android.widget.RemoteViews

/**
 * Builds the widget's RemoteViews from its stored config (decisions 0.3, 3.4): a vertical stack of rows, each a
 * horizontal row of equally weighted cells, so the grid fills the launcher's slot whatever its size.
 * Icons are Niagara bitmaps in the widget's style (decisions 5.5/5.6), also for `builtin` refs; the Material vector
 * only when Niagara is missing or a glyph can't be drawn.
 */
object WidgetRenderer {
    private const val TAG = "NavShortIcons"

    fun render(context: Context, appWidgetId: Int, config: WidgetConfig): RemoteViews {
        val icons = drawIcons(context, appWidgetId, config)
        val root = RemoteViews(context.packageName, R.layout.widget_grid)
        root.setImageViewUri(R.id.render_probe, RenderProbe.uri(context, appWidgetId, RenderProbe.nextGeneration(context, appWidgetId)))
        root.removeAllViews(R.id.grid_root)
        for (row in 0 until config.rows) {
            val rowViews = RemoteViews(context.packageName, R.layout.widget_row)
            for (col in 0 until config.cols) {
                val index = row * config.cols + col
                val entry = config.entries.getOrNull(index)
                rowViews.addView(
                    R.id.grid_row,
                    if (entry != null) {
                        filledCell(context, appWidgetId, index, entry, icons[index])
                    } else {
                        emptyCell(context, appWidgetId, index)
                    },
                )
            }
            root.addView(R.id.grid_root, rowViews)
        }
        logParcelSize(appWidgetId, root)
        return root
    }

    /**
     * Niagara bitmaps for the visible icons (index → bitmap), `builtin` refs included: while Niagara is readable every
     * icon is a Niagara glyph (decisions 5.6). Missing index = built-in Material fallback.
     */
    private fun drawIcons(context: Context, appWidgetId: Int, config: WidgetConfig): Map<Int, Bitmap> {
        val visible = config.entries.take(config.capacity).withIndex().toList()
        if (visible.isEmpty()) return emptyMap()
        val session = try {
            IconRendering.session(context)
        } catch (e: Exception) {
            Log.w(TAG, "icon session failed, using built-in icons", e)
            return emptyMap()
        }
        if (!session.niagaraInstalled) return emptyMap()
        val sizePx = IconRendering.cellSizePx(visible.size)
        val result = HashMap<Int, Bitmap>()
        val glyphMs = ArrayList<Double>()
        val renderMs = ArrayList<Double>()
        for ((index, entry) in visible) {
            val drawn = IconRendering.draw(session, entry.icon, config.style, sizePx, entry.id)
            glyphMs += drawn.glyphMs
            renderMs += drawn.renderMs
            if (drawn.bitmap != null) result[index] = drawn.bitmap else {
                Log.i(TAG, "widget $appWidgetId cell $index: '${entry.icon.name}' not drawable (Niagara installed=${session.niagaraInstalled}) → built-in")
            }
        }
        val bytes = result.values.sumOf { it.allocationByteCount.toLong() }
        Log.i(
            TAG,
            "widget $appWidgetId: ${result.size}/${visible.size} Niagara icons @${sizePx}px style=${config.style.style} " +
                "glyph load ms total=${"%.1f".format(glyphMs.sum())} median=${"%.2f".format(median(glyphMs))}, " +
                "render ms total=${"%.1f".format(renderMs.sum())} median=${"%.2f".format(median(renderMs))}, " +
                "bitmap bytes=$bytes",
        )
        return result
    }

    private fun median(values: List<Double>): Double =
        values.sorted().let { if (it.isEmpty()) 0.0 else it[it.size / 2] }

    /** Logs the RemoteViews' parcelled size (what goes through binder; decisions 5.5 budget). */
    private fun logParcelSize(appWidgetId: Int, views: RemoteViews) {
        val parcel = Parcel.obtain()
        try {
            views.writeToParcel(parcel, 0)
            Log.i(TAG, "widget $appWidgetId: RemoteViews parcel ${parcel.dataSize()} bytes")
        } catch (e: Exception) {
            Log.w(TAG, "parcel size measurement failed", e)
        } finally {
            parcel.recycle()
        }
    }

    private fun filledCell(context: Context, appWidgetId: Int, index: Int, entry: Entry, bitmap: Bitmap?): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_cell).apply {
            if (bitmap != null) setImageViewBitmap(R.id.cell_icon, bitmap)
            else setImageViewResource(R.id.cell_icon, IconResolver.drawableRes(entry.icon))
            setTextViewText(R.id.cell_label, entry.label)
            setContentDescription(R.id.cell_root, context.getString(R.string.navigate_to, entry.label))
            val navigate = Intent(Intent.ACTION_VIEW, Uri.parse(NavigationUris.drivingNavigation(entry.destination)))
                .setPackage(NavigationUris.MAPS_PACKAGE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            setOnClickPendingIntent(
                R.id.cell_root,
                PendingIntent.getActivity(context, requestCode(appWidgetId, index), navigate, FLAGS),
            )
        }

    private fun emptyCell(context: Context, appWidgetId: Int, index: Int): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_cell_empty).apply {
            setOnClickPendingIntent(
                R.id.cell_empty,
                PendingIntent.getActivity(
                    context,
                    requestCode(appWidgetId, index),
                    WidgetConfigActivity.intent(context, appWidgetId, fromWidget = true)
                        // Distinct data per widget+cell, so each cell has its own PendingIntent.
                        .setData(Uri.parse("navshort://widget/$appWidgetId/cell/$index")),
                    FLAGS,
                ),
            )
        }

    /** Unique per widget and cell (cells ≤ 36 < 64). */
    fun requestCode(appWidgetId: Int, index: Int): Int = (appWidgetId shl 6) or index

    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
}
