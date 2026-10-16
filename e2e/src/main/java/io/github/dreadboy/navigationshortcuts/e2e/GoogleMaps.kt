package io.github.dreadboy.navigationshortcuts.e2e

import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Until
import io.github.dreadboy.navigationshortcuts.e2e.E2eDevice.Companion.res
import io.github.dreadboy.navigationshortcuts.e2e.E2eDevice.Companion.resAny

/** Google Maps assertions. "Navigation is showing" IDs: docs/decisions.md 1.2. */
class GoogleMaps(private val d: E2eDevice) {

    /** Present only in active turn-by-turn navigation (absent on Maps home and on the route preview). */
    private val activeNavigation: BySelector get() =
        resAny(E2e.MAPS, "navigation_time_remaining_label", "step_instruction_content", "nav_container")

    /**
     * Maps' navigation first-run dialog ("Use Google Assistant while navigating"), which can come back after
     * a normal Maps launch. A user taps "Got it"; the suite does the same. Nothing else is dismissed.
     */
    private val navigationWelcomeDialog: BySelector get() = res(E2e.MAPS, "navigation_welcome_title_text")
    private val dialogGotIt: BySelector get() = By.pkg(E2e.MAPS).res("android", "button1")

    /** Asserts that Maps is the foreground app and shows active turn-by-turn navigation. */
    fun assertNavigationInForeground(timeoutMs: Long = E2e.LONG) {
        val deadline = System.currentTimeMillis() + timeoutMs
        // The dialog sits on top of the navigation screen, so check it on every round, including the last.
        while (dismissNavigationWelcomeIfShown() || !d.ui.wait(Until.hasObject(activeNavigation), POLL_MS)) {
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError(
                    "Timed out after $timeoutMs ms waiting for Google Maps active navigation " +
                        "(foreground: ${d.ui.currentPackageName}; Maps intent: ${mapsLaunchIntent()}; " +
                        "on screen: ${visibleMapsTexts()})",
                )
            }
        }
        val fg = d.ui.currentPackageName
        if (fg != E2e.MAPS) throw AssertionError("Navigation UI found but foreground package is $fg")
    }

    /** Failure diagnostics: the intent(s) the system recorded for Maps' activity (as `dumpsys` shows them). */
    private fun mapsLaunchIntent(): String =
        d.shell("dumpsys activity activities").lines()
            .filter { "intent=" in it && E2e.MAPS in it || "google.navigation" in it }
            .map { it.trim() }.distinct().take(4).joinToString(" | ")

    /** Failure diagnostics: texts, descriptions and ids currently shown by Maps. */
    private fun visibleMapsTexts(): String =
        d.ui.findObjects(By.pkg(E2e.MAPS)).mapNotNull { o ->
            val label = o.text?.takeIf { it.isNotBlank() } ?: o.contentDescription?.takeIf { it.isNotBlank() }
            label?.let { "'$it'" + (o.resourceName?.substringAfter(":id/")?.let { id -> "#$id" } ?: "") }
        }.distinct().take(40).joinToString(", ")

    /** Taps "Got it" on the navigation first-run dialog if it is showing; true if it did. */
    private fun dismissNavigationWelcomeIfShown(): Boolean {
        if (!d.has(navigationWelcomeDialog)) return false
        d.waitFor(dialogGotIt, what = "'Got it' on Maps' navigation welcome dialog").click()
        d.waitGone(navigationWelcomeDialog, what = "Maps' navigation welcome dialog")
        return true
    }

    /** Ends navigation so the next tap starts fresh (avoids Maps' "Exit navigation?" dialog). */
    fun forceStop() {
        d.shell("am force-stop ${E2e.MAPS}")
    }

    private companion object {
        const val POLL_MS = 1_000L
    }
}
