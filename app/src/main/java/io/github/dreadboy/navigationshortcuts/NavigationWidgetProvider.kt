package io.github.dreadboy.navigationshortcuts

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import android.widget.RemoteViews

/**
 * Pre-MVP widget: one cell with a hardcoded destination. Everything is rebuilt from constants
 * in [onUpdate], so nothing depends on in-memory state surviving process death.
 */
class NavigationWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            if (BuildConfig.DEBUG) logOptions("onUpdate", appWidgetId, appWidgetManager.getAppWidgetOptions(appWidgetId))
            appWidgetManager.updateAppWidget(appWidgetId, buildViews(context, appWidgetId))
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        if (BuildConfig.DEBUG) logOptions("onAppWidgetOptionsChanged", appWidgetId, newOptions)
    }

    /** Debug builds only: records which sizes the host (Niagara) reports, for decisions 1.3/1.4. */
    private fun logOptions(event: String, appWidgetId: Int, options: Bundle) {
        @Suppress("DEPRECATION") // the typed overload needs API 33; minSdk is 31
        val sizes: List<SizeF>? = options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES)
        Log.d(
            LOG_TAG,
            "$event id=$appWidgetId " +
                "minW=${options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)} " +
                "minH=${options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)} " +
                "maxW=${options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)} " +
                "maxH=${options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)} " +
                "sizes=$sizes",
        )
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

    private companion object {
        const val LOG_TAG = "NavShortWidget"
    }
}
