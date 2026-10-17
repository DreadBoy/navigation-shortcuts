package io.github.dreadboy.navigationshortcuts

import kotlinx.serialization.Serializable

/**
 * What the share sheet keeps across a recreation (dark-mode switch, process death while Maps is in the background;
 * decisions 5b round 2): the place as resolved and edited, the icon, the step and the selected widget, a finished
 * "Saved…" message, and a pending pin request. The link is not resolved again after a restore.
 */
@Serializable
data class SheetState(
    val entryId: String,
    val label: String,
    val destination: String,
    val destinationEditable: Boolean,
    val status: String,
    val icon: IconRef,
    val iconChosenByUser: Boolean,
    val resolved: Boolean,
    val resolvedLabel: String? = null,
    val resolvedDestination: String? = null,
    val widgetStep: Boolean = false,
    val selectedWidget: Int? = null,
    val createNewSelected: Boolean = false,
    val doneMessage: String? = null,
    val pinRequestedFor: Entry? = null,
    val pinBaseline: List<Int> = emptyList(),
    val pausedSincePinRequest: Boolean = false,
) {
    fun toJson(): String = WidgetStore.json.encodeToString(serializer(), this)

    companion object {
        fun fromJson(json: String): SheetState? = runCatching { WidgetStore.json.decodeFromString(serializer(), json) }.getOrNull()
    }
}
