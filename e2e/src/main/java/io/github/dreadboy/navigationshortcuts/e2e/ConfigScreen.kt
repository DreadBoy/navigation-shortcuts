package io.github.dreadboy.navigationshortcuts.e2e

import android.os.SystemClock
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import io.github.dreadboy.navigationshortcuts.e2e.E2eDevice.Companion.res
import org.junit.Assert.assertEquals

/** Our per-widget config screen ("Widget settings"), driven through its visible controls. */
class ConfigScreen(private val d: E2eDevice) {

    private fun id(name: String): BySelector = res(E2e.APP, name)

    /** Waits for the screen with its destination list loaded (Save is enabled once the config is read). */
    fun awaitShown(): ConfigScreen {
        d.waitFor(id("config_title"), E2e.LONG, "our config screen 'Widget settings'")
        d.waitFor(id("config_save").enabled(true), E2e.LONG, "config loaded (Save enabled)")
        return this
    }

    fun isShown(): Boolean = d.has(id("config_title"))

    fun hint(): String = d.waitFor(id("config_hint"), what = "share tip in the config screen").text.orEmpty()

    /** Destination labels in list order. */
    fun entries(): List<String> =
        d.ui.findObjects(id("entry_label")).sortedBy { it.visibleBounds.top }.map { it.text }

    /** Accessibility label of the icon button of the entry [label]: "Change icon for <label>: <icon name>" (E22). */
    fun entryIconDescription(label: String): String =
        scrollTo(By.res(E2e.APP, "entry_icon").descStartsWith("Change icon for $label: ")).contentDescription.orEmpty()

    fun rows(): Int = d.waitFor(id("rows_value"), what = "rows value").text.toInt()
    fun cols(): Int = d.waitFor(id("cols_value"), what = "columns value").text.toInt()

    fun gridNote(): String = d.find(id("grid_note"))?.text.orEmpty()

    fun tapStepper(description: String) {
        scrollTo(By.pkg(E2e.APP).desc(description)).click()
        SystemClock.sleep(200)
    }

    fun isStepperEnabled(description: String): Boolean = scrollTo(By.pkg(E2e.APP).desc(description)).isEnabled

    /** "Add manually" → label + address → OK. */
    fun addManually(label: String, destination: String) {
        scrollTo(id("add_manual_button")).click()
        fillDialog(label, destination)
        scrollTo(id("entry_label").text(label))
    }

    /** Tap the destination's edit button and replace its label (destination unchanged). */
    fun rename(label: String, newLabel: String) {
        scrollTo(By.pkg(E2e.APP).desc("Edit $label")).click()
        d.waitFor(id("dialog_label"), what = "label field in the edit dialog").text = newLabel
        d.ui.wait(Until.hasObject(id("dialog_label").text(newLabel)), E2e.SHORT)
        d.waitFor(By.res("android", "button1").enabled(true), what = "dialog OK").click()
        scrollTo(id("entry_label").text(newLabel))
    }

    fun delete(label: String) {
        scrollTo(By.pkg(E2e.APP).desc("Delete $label")).click()
        d.waitGone(id("entry_label").text(label), what = "deleted destination '$label'")
    }

    /** Drags [label]'s handle onto [ontoLabel]'s row (a plain drag gesture, as a finger would). */
    fun drag(label: String, ontoLabel: String) {
        val from = scrollTo(By.pkg(E2e.APP).desc("Drag to reorder $label")).visibleCenter
        val to = scrollTo(By.pkg(E2e.APP).desc("Drag to reorder $ontoLabel")).visibleCenter
        d.ui.drag(from.x, from.y, to.x, to.y, DRAG_STEPS)
        SystemClock.sleep(300)
    }

    /** Selects [option] in the spinner with resource id [spinnerId] (e.g. pack "Watercolor"). */
    fun select(spinnerId: String, option: String) {
        scrollTo(id(spinnerId)).click()
        d.waitFor(By.pkg(E2e.APP).text(option), what = "'$option' in the dropdown").click()
        d.ui.wait(Until.hasObject(id(spinnerId).hasDescendant(By.text(option))), E2e.SHORT)
    }

    fun previewDescription(): String = scrollTo(id("style_preview")).contentDescription.orEmpty()

    fun save() {
        d.waitFor(id("config_save").enabled(true), what = "Save").click()
        d.waitGone(id("config_title"), E2e.LONG, "config screen after Save")
    }

    fun cancel() {
        d.waitFor(id("config_cancel"), what = "Cancel").click()
        d.waitGone(id("config_title"), E2e.LONG, "config screen after Cancel")
    }

    /** System Back with unsaved changes → "Save changes?" (decisions 5b E1); taps [button] ("Save", "Discard", "Keep editing"). */
    fun backAndAnswer(button: String) {
        d.ui.pressBack()
        d.waitFor(By.text("Save changes?"), what = "'Save changes?' dialog after Back")
        val res = when (button) {
            "Save" -> "button1"
            "Discard" -> "button2"
            "Keep editing" -> "button3"
            else -> throw IllegalArgumentException(button)
        }
        val b = d.waitFor(By.res("android", res), what = "'$button' in the dialog")
        assertEquals("dialog button", button.lowercase(), b.text.orEmpty().lowercase())
        b.click()
        d.waitGone(By.text("Save changes?"), what = "'Save changes?' dialog")
        if (button != "Keep editing") d.waitGone(id("config_title"), E2e.LONG, "config screen after '$button'")
    }

    /** Taps Undo in the bar shown after a delete (decisions 5b E18). */
    fun undo(label: String) {
        d.waitFor(id("undo_text").text("Deleted $label"), what = "'Deleted $label' undo bar")
        d.waitFor(id("undo_button"), what = "Undo").click()
        scrollTo(id("entry_label").text(label))
    }

    private fun fillDialog(label: String, destination: String) {
        d.waitFor(id("dialog_label"), what = "label field in the add dialog").text = label
        d.waitFor(id("dialog_destination"), what = "destination field in the add dialog").text = destination
        d.ui.wait(Until.hasObject(id("dialog_destination").text(destination)), E2e.SHORT)
        d.waitFor(By.res("android", "button1").enabled(true), what = "dialog OK").click()
        d.waitGone(id("dialog_label"), what = "add dialog")
    }

    /** Finds [selector] in the scrolling config screen, scrolling down (then up) as a user would. */
    private fun scrollTo(selector: BySelector): UiObject2 {
        d.ui.waitForIdle(300)
        val scroll = d.waitFor(id("config_scroll"), what = "config scroll view")
        // Only a control well inside the scroll view counts (not a sliver at its edge).
        val inner = scroll.visibleBounds
        fun visible(): UiObject2? = d.find(selector)?.takeIf {
            val b = it.visibleBounds
            b.height() >= MIN_VISIBLE_PX && b.bottom < inner.bottom - EDGE_PX && b.top > inner.top + EDGE_PX ||
                // controls outside the scroll view (e.g. dropdowns, dialogs)
                !inner.contains(b.centerX(), b.centerY())
        }
        visible()?.let { return it }
        for (direction in listOf(Direction.DOWN, Direction.UP)) {
            for (i in 0 until 8) {
                val more = scroll.scroll(direction, 0.4f) // false = reached the end (after scrolling there)
                d.ui.waitForIdle(300)
                visible()?.let { return it }
                if (!more) break
            }
        }
        return visible() ?: throw AssertionError("'$selector' not fully visible in the config screen after scrolling")
    }

    private companion object {
        const val DRAG_STEPS = 60
        const val MIN_VISIBLE_PX = 40
        const val EDGE_PX = 8
    }
}
