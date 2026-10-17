package io.github.dreadboy.navigationshortcuts

import android.os.Bundle
import android.util.Log
import kotlinx.serialization.Serializable

/**
 * What the config screen keeps across a recreation (dark-mode switch, process death in the background; decisions 5b E1
 * round 2): the config as loaded (so "Save changes?" still compares against it), the edited config, the pending Undo
 * and an open Add/Edit dialog. Stored as one JSON string in the saved instance state.
 */
@Serializable
data class ConfigScreenState(
    val original: WidgetConfig,
    val edited: WidgetConfig,
    val deletedIndex: Int? = null,
    val deleted: Entry? = null,
    val dialog: Dialog? = null,
) {
    /** The Add/Edit dialog: [entryId] of the edited entry, or the id a new entry will get. */
    @Serializable
    data class Dialog(val entryId: String, val label: String, val destination: String, val icon: IconRef, val iconPicked: Boolean)

    fun toJson(): String = WidgetStore.json.encodeToString(serializer(), this)

    fun into(bundle: Bundle) = bundle.putString(KEY, toJson())

    companion object {
        private const val KEY = "navshort.configScreen"

        fun fromJson(json: String): ConfigScreenState? =
            runCatching { WidgetStore.json.decodeFromString(serializer(), json) }.getOrNull()

        fun from(bundle: Bundle): ConfigScreenState? = bundle.getString(KEY)?.let { json ->
            fromJson(json) ?: null.also { Log.w("NavShortConfig", "unreadable saved screen state, ignored") }
        }
    }
}
