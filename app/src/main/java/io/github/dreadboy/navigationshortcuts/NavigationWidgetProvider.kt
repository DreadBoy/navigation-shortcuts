package io.github.dreadboy.navigationshortcuts

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The navigation widget. Everything is rebuilt from the stored config on every update, so nothing depends on
 * in-memory state surviving process death. A widget without a stored config (newly placed, or a 0.1.0 widget
 * after the update) gets the default empty 1×4 grid and takes over pending destinations (decisions 3.3).
 */
class NavigationWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        if (BuildConfig.DEBUG) {
            for (id in appWidgetIds) logOptions("onUpdate", id, appWidgetManager.getAppWidgetOptions(id))
        }
        goAsync { WidgetUpdater.update(context, appWidgetIds.toList()) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        // The removed widget's destinations move to the pending list (decisions 5b E5): the next widget gets them back.
        goAsync {
            val after = WidgetStore(context).update { it.delete(appWidgetIds.toList()) }
            Log.i(LOG_TAG, "onDeleted ${appWidgetIds.toList()}: pending now ${after?.pending?.size}, widgets ${after?.configs?.keys}")
            RenderProbe.forget(context, appWidgetIds.toList())
            // A newer, still empty widget may have taken them over (Replace reported in that order): show them.
            val adopted = after?.configs?.filterKeys { id -> appWidgetIds.all { id > it } }?.keys.orEmpty()
            if (adopted.isNotEmpty()) WidgetUpdater.update(context, adopted.toList())
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

    private companion object {
        const val LOG_TAG = "NavShortWidget"
    }
}

/**
 * Receives the `requestPinAppWidget` success callback (the launcher fills in EXTRA_APPWIDGET_ID). The intent carries
 * the shared entry itself, so the destination reaches the new widget even if the pending list was lost; adding is
 * idempotent on the entry id, so it never duplicates what the widget's first update took from the pending list.
 */
class PinWidgetCallbackReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val entry = intent.getStringExtra(EXTRA_ENTRY_JSON)
            ?.let { runCatching { WidgetStore.json.decodeFromString(Entry.serializer(), it) }.getOrNull() }
        Log.i(TAG, "pin callback: widget=$widgetId entry=${entry?.label}")
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID || entry == null) return
        goAsync {
            WidgetStore(context).update { it.completePin(widgetId, entry) }
            WidgetUpdater.update(context, listOf(widgetId))
        }
    }

    companion object {
        const val EXTRA_ENTRY_JSON = "io.github.dreadboy.navigationshortcuts.ENTRY_JSON"
        private const val TAG = "NavShortPin"
    }
}

/** Renders widgets from the store; also prunes configs of widgets that no longer exist. */
object WidgetUpdater {
    suspend fun update(context: Context, appWidgetIds: List<Int>? = null) {
        val store = WidgetStore(context)
        val bound = store.boundWidgetIds()
        val before = store.read()
        val requested = (appWidgetIds ?: bound).filter { it in bound }
        // A newly placed widget (no config yet) may have replaced one of ours (Niagara's "Replace widget" keeps the old
        // ID bound, decisions 3.9): render every bound widget too, so the old one gets a render it never displays while
        // the new one does — the probe can then flag it and offer to move its places (decisions 5b E5 round 2).
        // The others first: the new widget's render must be displayed after the old one's was pushed (witness rule).
        val ids = if (requested.any { it !in before.configs }) bound.filterNot { it in requested } + requested else requested
        // Niagara's "Replace widget" may not call onDeleted (decisions 1.4): prune against the bound IDs.
        val state = store.update { it.prune(bound).let { s -> ids.fold(s) { acc, id -> acc.ensureConfig(id) } } }
            ?: store.read()
        RenderProbe.forget(context, before.configs.keys - bound.toSet())
        val manager = AppWidgetManager.getInstance(context)
        for (id in ids) {
            val config = state.configs[id] ?: WidgetConfig()
            manager.updateAppWidget(id, WidgetRenderer.render(context, id, config))
        }
    }
}

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** Runs [block] off the main thread while keeping the broadcast (and the process) alive until it is done. */
fun BroadcastReceiver.goAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    receiverScope.launch {
        try {
            block()
        } catch (e: Exception) {
            Log.e("NavShortWidget", "widget update failed", e)
        } finally {
            pending.finish()
        }
    }
}
