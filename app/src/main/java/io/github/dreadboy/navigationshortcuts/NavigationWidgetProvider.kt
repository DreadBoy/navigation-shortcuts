package io.github.dreadboy.navigationshortcuts

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews

/**
 * Pre-MVP widget: one cell with a hardcoded destination. Everything is rebuilt from constants
 * in [onUpdate], so nothing depends on in-memory state surviving process death.
 */
class NavigationWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            appWidgetManager.updateAppWidget(appWidgetId, buildViews(context, appWidgetId))
        }
    }

    private fun buildViews(context: Context, appWidgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_destination)
        views.setImageViewResource(R.id.cell_icon, R.drawable.ic_place_pin)
        views.setTextViewText(R.id.cell_label, HardcodedDestination.LABEL)
        views.setContentDescription(R.id.cell_root, context.getString(R.string.navigate_to, HardcodedDestination.LABEL))

        val navigate = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(NavigationUris.drivingNavigation(HardcodedDestination.QUERY)),
        ).apply {
            setPackage(NavigationUris.MAPS_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            appWidgetId,
            navigate,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(R.id.cell_root, pendingIntent)
        return views
    }
}
