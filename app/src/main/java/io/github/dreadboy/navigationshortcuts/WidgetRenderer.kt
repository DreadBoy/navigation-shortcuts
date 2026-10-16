package io.github.dreadboy.navigationshortcuts

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews

/**
 * Builds the widget's RemoteViews from its stored config (decisions 0.3, 3.4): a vertical stack of rows, each a
 * horizontal row of equally weighted cells, so the grid fills the launcher's slot whatever its size.
 */
object WidgetRenderer {

    fun render(context: Context, appWidgetId: Int, config: WidgetConfig): RemoteViews {
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
                    if (entry != null) filledCell(context, appWidgetId, index, entry) else emptyCell(context, appWidgetId, index),
                )
            }
            root.addView(R.id.grid_root, rowViews)
        }
        return root
    }

    private fun filledCell(context: Context, appWidgetId: Int, index: Int, entry: Entry): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_cell).apply {
            setImageViewResource(R.id.cell_icon, IconResolver.drawableRes(entry.icon))
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
            val tip = Intent(context, EmptyCellTipActivity::class.java)
                // Distinct data per widget+cell, so each cell has its own PendingIntent.
                .setData(Uri.parse("navshort://widget/$appWidgetId/cell/$index"))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            setOnClickPendingIntent(
                R.id.cell_empty,
                PendingIntent.getActivity(context, requestCode(appWidgetId, index), tip, FLAGS),
            )
        }

    /** Unique per widget and cell (cells ≤ 36 < 64). */
    fun requestCode(appWidgetId: Int, index: Int): Int = (appWidgetId shl 6) or index

    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
}
