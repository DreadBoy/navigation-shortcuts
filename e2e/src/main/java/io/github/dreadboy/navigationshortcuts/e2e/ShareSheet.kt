package io.github.dreadboy.navigationshortcuts.e2e

import android.os.SystemClock
import android.util.Log
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiObject2
import io.github.dreadboy.navigationshortcuts.e2e.E2eDevice.Companion.res

/**
 * Our "Add to navigation widget" bottom sheet, driven through its visible controls. Two steps (decisions 5.7):
 * details (icon, label, destination: Cancel / Next), then the widget list (Back / Add). Helpers that need step 2 tap
 * Next themselves when the sheet is still on step 1.
 */
class ShareSheet(private val d: E2eDevice) {

    private fun id(name: String): BySelector = res(E2e.APP, name)

    /** Waits for the sheet and for the link to be resolved (label and destination filled, or the failure message). */
    fun awaitResolved(): ShareSheet {
        d.waitFor(id("sheet_title"), E2e.LONG, "our share sheet '${E2e.SHARE_TARGET}'")
        // The app keeps asking for up to ~12 s while a just-created short link is not live yet (decisions 3.5).
        val deadline = System.currentTimeMillis() + RESOLVE_TIMEOUT_MS
        while (d.has(id("resolving_progress"))) {
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("share sheet still resolving the link after $RESOLVE_TIMEOUT_MS ms (status: ${status()})")
            }
            SystemClock.sleep(200)
        }
        return this
    }

    fun label(): String = d.waitFor(id("label_input"), what = "label field").text.orEmpty()
    fun destination(): String = d.waitFor(id("destination_input"), what = "destination field").text.orEmpty()
    fun status(): String = d.find(id("status_text"))?.text.orEmpty()
    fun message(): String {
        ensureWidgetStep()
        // "No navigation widget yet" is shown above the list (widget_step_message), everything else below it.
        return (d.find(id("message_text")) ?: d.find(id("widget_step_message")))?.text.orEmpty()
    }
    fun iconDescription(): String = d.waitFor(id("icon_button"), what = "icon button").contentDescription.orEmpty()

    /** On step 1 (primary button says "Next"). */
    fun isOnDetailsStep(): Boolean = d.has(id("add_button").text("Next"))

    /** Step 1 → step 2. */
    fun next() {
        tapPrimary("Next")
        d.waitFor(id("widget_chips"), E2e.LONG, "widget list (step 2)")
    }

    /** Step 2 → step 1 with the Back button. */
    fun back() {
        d.waitFor(id("cancel_button").text("Back"), what = "Back button").click()
        d.waitFor(id("label_input"), E2e.SHORT, "details step after Back")
    }

    private fun ensureWidgetStep() {
        if (isOnDetailsStep()) next()
    }

    /** Selects the "+ Create new widget" row (last row of step 2). */
    fun selectCreateNew() {
        ensureWidgetStep()
        d.waitFor(id("widget_chips").hasChild(By.text(CREATE_NEW)), what = "'$CREATE_NEW' row")
        d.find(By.res(E2e.APP, "widget_chips"))!!.findObject(By.text(CREATE_NEW)).click()
        d.ui.wait(androidx.test.uiautomator.Until.hasObject(By.text(CREATE_NEW).checked(true)), E2e.SHORT)
    }

    fun setLabel(label: String) {
        d.waitFor(id("label_input"), what = "label field").text = label
        d.ui.wait(androidx.test.uiautomator.Until.hasObject(id("label_input").text(label)), E2e.SHORT)
    }

    /** The primary button ("Next", "Add", "OK"). */
    fun primary(): UiObject2 = d.waitFor(id("add_button"), what = "sheet primary button")

    /** Taps the primary button once it says [expectedText]; "Add" first moves on from step 1 with Next. */
    fun tapPrimary(expectedText: String) {
        if (expectedText == "Add") {
            d.ui.wait(androidx.test.uiautomator.Until.hasObject(id("add_button").enabled(true)), E2e.LONG)
            ensureWidgetStep()
        }
        val selector = id("add_button").text(expectedText).enabled(true)
        if (!d.ui.wait(androidx.test.uiautomator.Until.hasObject(selector), E2e.LONG)) {
            val button = d.find(id("add_button"))
            throw AssertionError(
                "Timed out waiting for enabled '$expectedText' in the share sheet (button: '${button?.text}' " +
                    "enabled=${button?.isEnabled}; label='${d.find(id("label_input"))?.text}', " +
                    "destination='${d.find(id("destination_input"))?.text}', status='${status()}', message='${message()}')",
            )
        }
        d.waitFor(selector, what = "enabled '$expectedText' button").click()
    }

    fun tapAdd() = tapPrimary("Add")

    fun tapGrow(column: Boolean) {
        ensureWidgetStep()
        val name = if (column) "add_column_button" else "add_row_button"
        d.waitFor(id(name).enabled(true), what = if (column) "'+1 column'" else "'+1 row'").click()
    }

    fun waitGone() = d.waitGone(id("sheet_title"), E2e.LONG, "our share sheet")

    /**
     * On a Maps place page: Share → our sheet, resolved. Maps now and then shares a `?cid=` link without the address
     * instead of a short link (decisions 3.5); the sheet then says "share the place again", and that is what a user
     * does: Cancel and Share once more. Any other resolution failure stays visible to the test.
     */
    fun shareFromMapsPlacePage(maps: GoogleMaps) {
        maps.shareCurrentPlaceToOurApp()
        awaitResolved()
        if (CID_FALLBACK in status()) {
            Log.w("NavShortE2e", "Maps shared a cid link without the address; sharing again (decisions 3.5)")
            d.waitFor(id("cancel_button"), what = "sheet Cancel").click()
            waitGone()
            maps.shareCurrentPlaceToOurApp()
            awaitResolved()
        }
    }

    /** Step 2 rows, top to bottom, as (label, checked); the last one is "+ Create new widget". */
    fun chooser(): List<Pair<String, Boolean>> {
        ensureWidgetStep()
        val group = d.find(id("widget_chips")) ?: return emptyList()
        return group.children.sortedBy { it.visibleBounds.top }.map { it.text.orEmpty() to it.isChecked }
    }

    /** The "not seen on your home screen" hint under the chooser, or "" when hidden. */
    fun widgetHint(): String {
        ensureWidgetStep()
        return d.find(id("widget_hint"))?.text.orEmpty()
    }

    companion object {
        /** Last row of step 2 (create_new_widget). */
        const val CREATE_NEW = "+ Create new widget"

        private const val RESOLVE_TIMEOUT_MS = 30_000L

        /** Start of failed_cid in the app's strings. */
        private const val CID_FALLBACK = "Google Maps shared a link without the address"
    }
}
