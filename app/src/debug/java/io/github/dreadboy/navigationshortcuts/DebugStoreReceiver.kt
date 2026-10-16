package io.github.dreadboy.navigationshortcuts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Debug builds only (E2E, decisions 5.6): `am broadcast` hooks for test preconditions and store inspection.
 * - `DUMP_STORE`: result data = JSON `{"<widgetId>": [entries…]}` of the stored configs.
 * - `SET_ICON` (`--ei index I --es source S --es name N`): sets the stored icon of the entry at index I of every
 *   widget (e.g. a 0.2.0-style `builtin` ref) and re-renders the widgets. Only for seeding preconditions, never the user path.
 */
class DebugStoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val store = WidgetStore(context)
                if (intent.action == ACTION_SET_ICON) {
                    val index = intent.getIntExtra("index", 0)
                    val icon = IconRef(intent.getStringExtra("source").orEmpty(), intent.getStringExtra("name").orEmpty())
                    store.update { state ->
                        state.copy(configs = state.configs.mapValues { (_, config) ->
                            config.copy(entries = config.entries.mapIndexed { i, e -> if (i == index) e.copy(icon = icon) else e })
                        })
                    }
                    WidgetUpdater.update(context)
                    Log.i(TAG, "set icon of entry $index to $icon")
                }
                val configs = store.read().configs.mapKeys { it.key.toString() }.mapValues { it.value.entries }
                pending.resultData = WidgetStore.json.encodeToString(
                    MapSerializer(String.serializer(), ListSerializer(Entry.serializer())),
                    configs,
                )
            } catch (e: Exception) {
                Log.e(TAG, "debug store action failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "NavShortDebug"
        const val ACTION_SET_ICON = "io.github.dreadboy.navigationshortcuts.debug.SET_ICON"
    }
}
