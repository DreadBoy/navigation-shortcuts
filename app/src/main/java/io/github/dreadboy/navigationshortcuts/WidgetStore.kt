package io.github.dreadboy.navigationshortcuts

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.widgetDataStore: DataStore<Preferences> by preferencesDataStore(name = "widgets")

/**
 * Preferences DataStore with one JSON config per widget (`widget_<id>`) plus `pending` (decisions 0.4, 3.1).
 * Every mutation is one atomic [DataStore.edit] over a [StoreState] snapshot.
 */
class WidgetStore(context: Context) {
    private val appContext = context.applicationContext
    private val dataStore = appContext.widgetDataStore

    suspend fun read(): StoreState = decode(dataStore.data.first())

    /**
     * Applies [transform] atomically and returns the new state. A null result means "no change"; the
     * returned value is then null too, so callers can tell e.g. "grid full" apart.
     */
    suspend fun update(transform: (StoreState) -> StoreState?): StoreState? {
        var result: StoreState? = null
        dataStore.edit { prefs ->
            val before = decode(prefs)
            val after = transform(before) ?: return@edit
            encode(prefs, before, after)
            result = after
        }
        return result
    }

    /** Widget IDs the system currently has bound to our provider. */
    fun boundWidgetIds(): List<Int> =
        AppWidgetManager.getInstance(appContext)
            .getAppWidgetIds(ComponentName(appContext, NavigationWidgetProvider::class.java)).toList()

    private fun decode(prefs: Preferences): StoreState {
        val configs = mutableMapOf<Int, WidgetConfig>()
        var pending = emptyList<Entry>()
        for ((key, value) in prefs.asMap()) {
            val name = key.name
            val raw = value as? String ?: continue
            when {
                name == PENDING_KEY.name -> pending = runCatching { json.decodeFromString(entryList, raw) }
                    .onFailure { Log.w(TAG, "unreadable pending list, ignoring", it) }.getOrDefault(emptyList())
                name.startsWith(WIDGET_PREFIX) -> {
                    val id = name.removePrefix(WIDGET_PREFIX).toIntOrNull() ?: continue
                    runCatching { json.decodeFromString(WidgetConfig.serializer(), raw) }
                        .onSuccess { configs[id] = it }
                        .onFailure { Log.w(TAG, "unreadable config for widget $id, ignoring", it) }
                }
            }
        }
        return StoreState(configs, pending)
    }

    private fun encode(prefs: MutablePreferences, before: StoreState, after: StoreState) {
        for (id in before.configs.keys - after.configs.keys) prefs.remove(widgetKey(id))
        for ((id, config) in after.configs) {
            if (before.configs[id] != config) prefs[widgetKey(id)] = json.encodeToString(WidgetConfig.serializer(), config)
        }
        if (after.pending != before.pending) {
            if (after.pending.isEmpty()) prefs.remove(PENDING_KEY)
            else prefs[PENDING_KEY] = json.encodeToString(entryList, after.pending)
        }
    }

    companion object {
        private const val TAG = "NavShortStore"
        private const val WIDGET_PREFIX = "widget_"
        private val PENDING_KEY = stringPreferencesKey("pending")
        private val entryList = ListSerializer(Entry.serializer())

        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun widgetKey(id: Int) = stringPreferencesKey(WIDGET_PREFIX + id)
    }
}
