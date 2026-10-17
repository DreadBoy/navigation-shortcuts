package io.github.dreadboy.navigationshortcuts

import android.content.Context
import androidx.core.content.edit

/**
 * Unsaved config-screen edits per widget (decisions 5b round 3): written when the user leaves the screen without
 * deciding (Home, another app), read when the screen opens again for that widget through any entry point, dropped when
 * the user leaves deliberately (Save, Discard, Cancel). Plain SharedPreferences, one JSON [ConfigScreenState] per widget;
 * a draft is only ever a proposal — nothing reaches the widget until Save, which merges against the store (4.1).
 */
object ConfigDrafts {
    private const val PREFS = "config_drafts"

    private fun key(widgetId: Int) = "draft_$widgetId"

    fun save(context: Context, widgetId: Int, state: ConfigScreenState) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(key(widgetId), state.toJson()) }

    fun load(context: Context, widgetId: Int): ConfigScreenState? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key(widgetId), null)?.let { ConfigScreenState.fromJson(it) }

    fun clear(context: Context, widgetId: Int) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { remove(key(widgetId)) }
}
