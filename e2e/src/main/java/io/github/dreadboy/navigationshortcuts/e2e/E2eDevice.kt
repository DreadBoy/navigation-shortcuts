package io.github.dreadboy.navigationshortcuts.e2e

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

/** Package names and user-visible texts the suite relies on. */
object E2e {
    const val APP = "io.github.dreadboy.navigationshortcuts"
    const val NIAGARA = "bitpit.launcher"
    const val MAPS = "com.google.android.apps.maps"

    /** Launcher label of the app (app_name). */
    const val APP_LABEL = "Navigation Shortcuts"

    /** Widget description shown under the preview in Niagara's picker (widget_description). */
    const val WIDGET_DESCRIPTION = "Tap a destination to start Google Maps driving navigation"

    /** Share-sheet label of our share target (share_target_label). */
    const val SHARE_TARGET = "Add to navigation widget"

    /** Content description of an empty widget cell (empty_cell_description). */
    const val EMPTY_CELL = "Empty shortcut slot"

    /** Empty-cell tip text (tip_message). */
    const val TIP_MESSAGE = "Share a place from Google Maps to add it here."

    /** Cells of a newly placed widget (default 1×4 grid). */
    const val DEFAULT_CELLS = 4

    const val SHORT = 5_000L
    const val LONG = 20_000L
}

/** Thin wrapper over [UiDevice] with waits that fail with a readable message instead of returning null. */
class E2eDevice {
    val ui: UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    init {
        // Maps navigation animates continuously, so the UI never goes "idle". The default 10 s idle wait
        // would make every query during navigation take 10 s; all synchronisation here is explicit waits.
        Configurator.getInstance().waitForIdleTimeout = 500
    }

    fun shell(command: String): String = ui.executeShellCommand(command)

    fun has(selector: BySelector): Boolean = ui.hasObject(selector)

    fun find(selector: BySelector): UiObject2? = ui.findObject(selector)

    fun waitFor(selector: BySelector, timeoutMs: Long = E2e.SHORT, what: String = selector.toString()): UiObject2 {
        ui.wait(Until.hasObject(selector), timeoutMs)
        return ui.findObject(selector)
            ?: throw AssertionError("Timed out after ${timeoutMs} ms waiting for $what (foreground: ${ui.currentPackageName})")
    }

    fun waitGone(selector: BySelector, timeoutMs: Long = E2e.SHORT, what: String = selector.toString()) {
        if (!ui.wait(Until.gone(selector), timeoutMs)) {
            throw AssertionError("Timed out after ${timeoutMs} ms waiting for $what to disappear")
        }
    }

    companion object {
        fun res(pkg: String, id: String): BySelector = By.res(pkg, id)
        fun resAny(pkg: String, vararg ids: String): BySelector =
            By.res(Pattern.compile(Pattern.quote(pkg) + ":id/(" + ids.joinToString("|") + ")"))
    }
}
