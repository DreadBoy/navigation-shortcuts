package io.github.dreadboy.navigationshortcuts.e2e

import android.os.SystemClock
import android.util.Log
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import io.github.dreadboy.navigationshortcuts.e2e.E2eDevice.Companion.res

/**
 * Drives Niagara Launcher (free tier) the way a user does. Selectors and flows: docs/decisions.md 1.3.
 * Niagara has a single custom-widget slot under the clock; a second custom widget needs Pro (widget stack).
 */
class Niagara(private val d: E2eDevice) {

    // BySelector is mutable (.text() etc. modify it), so every use gets a fresh one.

    private val clock: BySelector get() = res(E2e.NIAGARA, "clock")
    private val infoContainer: BySelector get() = res(E2e.NIAGARA, "info_container")
    private val searchField: BySelector get() = res(E2e.NIAGARA, "toolbar_text") // app search and widget picker
    private val sheetScrim: BySelector get() = res(E2e.NIAGARA, "dismiss_view") // long-press menus / bottom sheets
    private val widgetSlot: BySelector get() = res(E2e.NIAGARA, "widget_stack_recycler")
    private val menuItem: BySelector get() = res(E2e.NIAGARA, "header_text_view")

    fun assertIsDefaultHome() {
        val resolved = d.shell(
            "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME",
        ).trim().lines().last()
        check(resolved.startsWith(E2e.NIAGARA + "/")) {
            "Default home is '$resolved', expected Niagara. Run e2e/setup-emulator.sh first."
        }
    }

    /** Brings Niagara's home screen to the front with nothing on top of it (no search, menu or picker). */
    fun goHome() {
        if (d.ui.currentPackageName != E2e.NIAGARA) d.ui.pressHome()
        val deadline = System.currentTimeMillis() + E2e.LONG
        while (System.currentTimeMillis() < deadline) {
            d.ui.wait(Until.hasObject(By.pkg(E2e.NIAGARA)), E2e.SHORT)
            when {
                d.ui.currentPackageName != E2e.NIAGARA -> d.ui.pressHome()
                // HOME on Niagara's home opens app search, and menus/pickers are overlays: BACK closes them.
                d.has(searchField) || d.has(sheetScrim) -> d.ui.pressBack()
                d.has(infoContainer) -> return
                else -> d.ui.pressHome()
            }
            d.ui.waitForIdle(500)
        }
        throw AssertionError("Niagara home screen not reached (foreground: ${d.ui.currentPackageName})")
    }

    /** The custom widget slot's host view, if a custom widget is placed. */
    fun placedWidget(): UiObject2? = d.find(widgetSlot)?.findObject(By.clazz("android.appwidget.AppWidgetHostView"))

    /**
     * Removes whatever custom widget occupies Niagara's single slot: long-press → "Remove" → "Okay".
     * Keeps runs repeatable (free Niagara refuses a second custom widget).
     */
    fun removeCustomWidgetIfPresent() {
        goHome()
        // The slot itself, so a "Tap to restore" placeholder (widget of an uninstalled app) is removed too.
        val slot = d.find(widgetSlot)?.findObject(res(E2e.NIAGARA, "container")) ?: return
        // In the slot under the clock Niagara intercepts the long-press even over our clickable cell.
        // (With "Replace clock with widget" enabled it does not and the press reaches the cell instead;
        // see decisions 1.3. The suite never enables that option.)
        val c = slot.visibleCenter
        longPress(c.x, c.y)
        d.waitFor(menuItem.text("Remove"), what = "widget long-press menu item 'Remove'").click()
        d.waitFor(By.text("Do you want to remove this widget?"), what = "remove confirmation")
        d.waitFor(res(E2e.NIAGARA, "button_positive").text("Okay"), what = "'Okay' button").click()
        d.waitGone(widgetSlot, what = "custom widget slot")
        goHome()
    }

    /**
     * Places our widget through the free-tier flow: long-press the clock → "Add Custom Widget" → scroll the
     * picker to our app → tap the app row → tap the widget preview. Returns once the widget is on home.
     */
    fun addOurWidget() {
        goHome()
        check(placedWidget() == null) { "A custom widget is already placed; remove it first" }
        openNiagaraWidgetSheet()
        d.waitFor(menuItem.text("Add Custom Widget"), what = "'Add Custom Widget'").click()
        pickOurWidget()
        if (d.ui.wait(Until.hasObject(By.text("Create Widget Stack?")), 1_000)) {
            throw AssertionError("Niagara offered a widget stack (Pro): another custom widget is still placed")
        }
        d.waitGone(By.pkg(E2e.NIAGARA).text("Select a widget"), what = "widget picker")
        d.waitFor(widgetSlot, E2e.LONG, "placed custom widget slot")
    }

    /**
     * Verifier's Repro B (decisions 3.9): with our widget placed, long-press it → "Add Custom Widget" → our widget →
     * "Create Widget Stack?" → "Replace widget". Niagara shows a new widget of ours and keeps the old ID bound (ghost).
     */
    fun replaceCustomWidgetWithNewOurs() {
        goHome()
        val slot = d.find(widgetSlot)?.findObject(res(E2e.NIAGARA, "container"))
            ?: throw AssertionError("no custom widget to replace")
        val c = slot.visibleCenter
        longPress(c.x, c.y)
        d.waitFor(menuItem.text("Add Custom Widget"), what = "widget menu 'Add Custom Widget'").click()
        pickOurWidget()
        d.waitFor(By.text("Create Widget Stack?"), E2e.LONG, "'Create Widget Stack?' (free tier)")
        d.waitFor(By.pkg(E2e.NIAGARA).text("Replace widget"), what = "'Replace widget'").click()
        d.waitGone(By.text("Create Widget Stack?"), E2e.LONG, "'Create Widget Stack?'")
        d.waitFor(widgetSlot, E2e.LONG, "placed custom widget slot")
    }

    /**
     * Widget IDs the system has bound to our provider, from `dumpsys appwidget`. Includes Niagara "ghosts" left by
     * "Replace widget", which only uninstalling the app clears (decisions 3.9).
     */
    fun boundWidgetIdsOfOurApp(): List<Int> {
        val widgets = d.shell("dumpsys appwidget").substringAfter("Widgets:", "").substringBefore("Hosts:")
        return widgets.split(Regex("""\n\s+\[\d+] """)).mapNotNull { block ->
            val id = Regex("""^id=(\d+)""").find(block.trim())?.groupValues?.get(1)?.toInt()
            id?.takeIf { block.contains("cmp:ComponentInfo{${E2e.APP}/") }
        }
    }

    /** In Niagara's widget picker: scroll to our app, expand it and tap our widget's preview card. */
    private fun pickOurWidget() {
        // Title is a large header first and moves into the toolbar once the list scrolls.
        d.waitFor(By.pkg(E2e.NIAGARA).text("Select a widget"), what = "widget picker")

        val picker = d.waitFor(res(E2e.NIAGARA, "recycler_view"), what = "picker list")
        var appRow = scrollTo(picker, menuItem.text(E2e.APP_LABEL), "our app in the widget picker")
        // The row can be found while only a sliver of it shows at the bottom edge. Niagara does not scroll
        // the expanded card into view, so bring the row into the upper half first: the card opens below it.
        var scrolls = 0
        while (appRow.visibleBounds.top > d.ui.displayHeight / 2 && scrolls++ < MAX_SCROLLS) {
            if (!picker.scroll(Direction.DOWN, 0.25f)) break
            appRow = d.waitFor(menuItem.text(E2e.APP_LABEL), what = "our app in the widget picker")
        }
        appRow.click()

        // Expanding the app row shows the live preview (our previewLayout) with the widget description
        // below it; the whole card is the click target that places the widget. If the card still ends
        // in the lower quarter (gesture area) or below the screen, scroll forward until it is in the safe area.
        scrolls = 0
        var description = d.ui.wait(Until.findObject(By.text(E2e.WIDGET_DESCRIPTION)), E2e.SHORT)
        while ((description == null || description.visibleBounds.bottom > d.ui.displayHeight * 3 / 4) &&
            scrolls++ < MAX_SCROLLS
        ) {
            if (!picker.scroll(Direction.DOWN, 0.25f)) break
            description = d.ui.wait(Until.findObject(By.text(E2e.WIDGET_DESCRIPTION)), E2e.SHORT)
        }
        (description ?: d.waitFor(By.text(E2e.WIDGET_DESCRIPTION), what = "our widget's preview card")).click()
    }

    /** Finds our widget's cell by its visible label on the home screen. */
    fun cell(label: String): UiObject2 =
        d.waitFor(
            By.pkg(E2e.APP).res(E2e.APP, "cell_label").text(label),
            E2e.LONG,
            "widget cell labelled '$label' on Niagara home",
        )

    /** Labels of our widget's filled cells, in reading order (left→right, top→bottom). */
    fun cellLabels(): List<String> =
        d.ui.findObjects(By.pkg(E2e.APP).res(E2e.APP, "cell_label"))
            .sortedWith(compareBy({ it.visibleBounds.top }, { it.visibleBounds.left }))
            .map { it.text }

    /** Our widget's empty cells (transparent, tappable), in reading order. */
    fun emptyCells(): List<UiObject2> =
        d.ui.findObjects(By.pkg(E2e.APP).res(E2e.APP, "cell_empty"))
            .sortedWith(compareBy({ it.visibleBounds.top }, { it.visibleBounds.left }))

    /**
     * Waits until our widget shows exactly [labels] as filled cells and [empty] empty cells (the widget re-renders
     * asynchronously after an update).
     */
    fun assertWidgetCells(labels: List<String>, empty: Int) {
        val deadline = System.currentTimeMillis() + E2e.LONG
        while (true) {
            goHome()
            val shown = cellLabels()
            val emptyShown = emptyCells().size
            if (shown == labels && emptyShown == empty) return
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("Widget shows cells $shown + $emptyShown empty, expected $labels + $empty empty")
            }
            SystemClock.sleep(500)
        }
    }

    /**
     * Niagara's requestPinAppWidget confirmation ("Add to home screen", our app name, Okay / Cancel; decisions 3.2).
     * Taps [confirm] ? "Okay" : "Cancel".
     */
    fun answerPinRequest(confirm: Boolean) {
        d.waitFor(By.pkg(E2e.NIAGARA).text("Add to home screen"), E2e.LONG, "Niagara's 'Add to home screen' pin sheet")
        d.waitFor(By.pkg(E2e.NIAGARA).text(E2e.APP_LABEL), what = "our widget in the pin sheet")
        val button = if (confirm) res(E2e.NIAGARA, "button_positive").text("Okay") else res(E2e.NIAGARA, "button_negative").text("Cancel")
        d.waitFor(button, what = "pin sheet button").click()
        d.waitGone(By.pkg(E2e.NIAGARA).text("Add to home screen"), what = "Niagara's pin sheet")
    }

    private fun openNiagaraWidgetSheet() {
        // The clock is the documented entry; the date line opens the same "Niagara widget" sheet and is
        // still there when "Replace clock with widget" is enabled.
        val anchor = d.find(clock) ?: d.waitFor(infoContainer, what = "Niagara clock/date")
        val c = anchor.visibleCenter
        longPress(c.x, c.y)
        d.waitFor(By.text("Niagara widget"), what = "'Niagara widget' sheet")
    }

    private fun scrollTo(list: UiObject2, target: BySelector, what: String): UiObject2 {
        repeat(MAX_SCROLLS) {
            d.find(target)?.let { return it }
            if (!list.scroll(Direction.DOWN, 0.6f)) return d.waitFor(target, what = what)
        }
        return d.waitFor(target, what = what)
    }

    /**
     * Press-and-hold on Niagara's home (clock, date line, custom widget slot). When the clock ticks to the
     * next minute during the press, Niagara re-lays out the header and cancels the long-press (no sheet or
     * menu opens). So if the press could overlap a minute boundary, first wait until the minute has rolled.
     */
    private fun longPress(x: Int, y: Int) {
        val now = System.currentTimeMillis() // the test runs on the device: this is the clock Niagara shows
        val msIntoMinute = now % 60_000
        if (msIntoMinute > 60_000 - LONG_PRESS_GUARD_MS) {
            val waitMs = 60_000 - msIntoMinute + ROLLOVER_SETTLE_MS
            Log.i(TAG, "long-press would cross the minute boundary; waiting $waitMs ms for the clock to roll")
            SystemClock.sleep(waitMs)
        }
        d.ui.swipe(x, y, x, y, LONG_PRESS_STEPS)
    }

    private companion object {
        const val TAG = "NavShortE2e"

        /** Longest measured press (60 steps, up to ~2.5 s) plus margin. */
        const val LONG_PRESS_GUARD_MS = 4_000L

        /** Niagara cancels presses from ~:59.9 to ~:00.3 (TIME_TICK + relayout); 1.5 s clears it on a loaded host. */
        const val ROLLOVER_SETTLE_MS = 1_500L

        /**
         * Zero-length swipe = press-and-hold. Nominally 5 ms per step, measured ~33 ms per step on the
         * emulator, so 60 steps hold for ~1-2 s, safely above the 400 ms system long-press timeout.
         */
        const val LONG_PRESS_STEPS = 60
        const val MAX_SCROLLS = 30
    }
}
