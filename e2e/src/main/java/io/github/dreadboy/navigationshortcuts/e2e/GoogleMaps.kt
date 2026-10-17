package io.github.dreadboy.navigationshortcuts.e2e

import android.util.Log
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.StaleObjectException
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

    /**
     * Google Play services' location-settings check that Maps requests now and then before navigating ("To continue,
     * your device will need to use Location Accuracy", No thanks / Turn on; `LocationSettingsCheckerActivity`, 2 of
     * ~40 navigation assertions in the 0.3.0 runs). It sits over Maps and holds navigation. A user taps "Turn on"; the
     * suite does the same (the setting then sticks). Nothing else is dismissed.
     */
    private val locationAccuracyDialog: BySelector get() = By.pkg(GMS).textContains("use Location Accuracy")
    private val locationAccuracyTurnOn: BySelector get() = By.pkg(GMS).text("Turn on")

    /** Asserts that Maps is the foreground app and shows active turn-by-turn navigation. */
    fun assertNavigationInForeground(timeoutMs: Long = E2e.LONG) {
        val deadline = System.currentTimeMillis() + timeoutMs
        // The dialog sits on top of the navigation screen, so check it on every round, including the last.
        while (dismissNavigationWelcomeIfShown() || acceptLocationAccuracyIfShown() ||
            !d.ui.wait(Until.hasObject(activeNavigation), POLL_MS)
        ) {
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError(
                    "Timed out after $timeoutMs ms waiting for Google Maps active navigation " +
                        "(foreground: ${d.ui.currentPackageName}; Maps intent: ${mapsLaunchIntent()}; " +
                        "on screen: ${visibleMapsTexts()}; foreground texts: ${foregroundTexts()})",
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

    /** Failure diagnostics: texts of whatever is in front (e.g. a Play services dialog over Maps). */
    private fun foregroundTexts(): String {
        val pkg = d.ui.currentPackageName ?: return ""
        if (pkg == E2e.MAPS) return "(Maps)"
        return d.ui.findObjects(By.pkg(pkg)).mapNotNull { it.text?.takeIf { t -> t.isNotBlank() } }
            .distinct().take(20).joinToString(", ") { "'$it'" }
    }

    /** Taps "Turn on" on Play services' Location Accuracy prompt if it is showing; true if it did. */
    private fun acceptLocationAccuracyIfShown(): Boolean {
        if (!d.has(locationAccuracyDialog)) return false
        Log.w("NavShortE2e", "Play services 'Location Accuracy' prompt over Maps: tapping Turn on")
        d.waitFor(locationAccuracyTurnOn, what = "'Turn on' in the Location Accuracy prompt").click()
        d.waitGone(locationAccuracyDialog, what = "Location Accuracy prompt")
        return true
    }

    /** Taps "Got it" on the navigation first-run dialog if it is showing; true if it did. */
    private fun dismissNavigationWelcomeIfShown(): Boolean {
        if (!d.has(navigationWelcomeDialog)) return false
        d.waitFor(dialogGotIt, what = "'Got it' on Maps' navigation welcome dialog").click()
        d.waitGone(navigationWelcomeDialog, what = "Maps' navigation welcome dialog")
        return true
    }

    /**
     * Asserts that the navigation Maps is showing was started for [expectedQuery] (the encoded `q=` value our
     * widget put into the intent, as `dumpsys` records it). Call after [assertNavigationInForeground].
     */
    fun assertNavigationIntentFor(expectedQuery: String) {
        val encoded = "q=" + uriEncode(expectedQuery)
        val intents = mapsLaunchIntent()
        if (encoded !in intents) {
            throw AssertionError("Maps navigation is not for '$expectedQuery' (expected '$encoded' in: $intents)")
        }
    }

    /**
     * Opens Google Maps like a user (cold start from its launcher activity), types [query] into "Search here" and
     * taps the suggestion titled [suggestion], which opens that place's page. Returns once its Share button shows.
     */
    fun openPlace(query: String, suggestion: String) {
        forceStop()
        d.shell("am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n $MAPS_ACTIVITY")
        // Maps re-lays out its home screen during a cold start, so the search box can vanish right after it
        // appears: tap it until the editable search field opens.
        val searchStart = System.currentTimeMillis()
        while (!d.has(res(E2e.MAPS, "search_omnibox_edit_text"))) {
            if (System.currentTimeMillis() - searchStart > E2e.LONG) {
                throw AssertionError(
                    "Maps search field did not open after a cold start (foreground: ${d.ui.currentPackageName}; " +
                        "on screen: ${visibleMapsTexts()})",
                )
            }
            try {
                val box = d.ui.wait(Until.findObject(res(E2e.MAPS, "search_omnibox_text_box")), E2e.SHORT)
                // Maps' home search box: "Search here", or a rotating hint ("Try gas stations, ATMs", seen 2026-10-17)
                // while the Explore tab is shown. On a place or route screen the box shows the place instead.
                val home = box != null && (box.contentDescription == "Search here" || d.has(res(E2e.MAPS, "explore_tab_strip_button")))
                if (box != null && home) {
                    box.click()
                } else if (d.ui.currentPackageName == E2e.MAPS) {
                    // A cold start can restore the last directions/route or place screen (e.g. after a navigation
                    // test; decisions 3.5). A user goes back to Maps' home ("Search here") first.
                    d.ui.pressBack()
                } else {
                    // Backed out of Maps entirely: open it again (warm now, so it shows its home).
                    d.shell("am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n $MAPS_ACTIVITY")
                }
            } catch (e: StaleObjectException) {
                // re-laid out between find and click; try again
            }
            d.ui.wait(Until.hasObject(res(E2e.MAPS, "search_omnibox_edit_text")), 2_000)
        }
        d.waitFor(res(E2e.MAPS, "search_omnibox_edit_text"), what = "Maps search field").text = query
        val deadline = System.currentTimeMillis() + E2e.LONG * 2
        var rounds = 0
        while (true) {
            // Seen once in ~30 runs: the tap only puts the suggestion into the search field and the place never
            // opens. A user then submits the search; an exact name opens the place or a result list, whose matching
            // title the next round taps.
            if (rounds++ > 0 && d.find(res(E2e.MAPS, "search_omnibox_edit_text").text(suggestion)) != null) {
                Log.w(TAG, "Maps kept '$suggestion' in the search field without opening it; submitting the search")
                d.ui.pressEnter()
                if (d.ui.wait(Until.hasObject(placeShareButton), E2e.SHORT)) break
            }
            // The search field can carry the same text as the suggestion; tap the suggestion row, not the field.
            // Suggestions refresh while Maps processes the typed text, so a found row can go stale: look again.
            val tapped = try {
                val row = d.ui.findObjects(By.pkg(E2e.MAPS).text(suggestion))
                    .firstOrNull { it.resourceName?.endsWith("search_omnibox_edit_text") != true }
                row?.click()
                row != null
            } catch (e: StaleObjectException) {
                false
            }
            // Opening the place needs the network; give it time before tapping again.
            if (tapped && d.ui.wait(Until.hasObject(placeShareButton), E2e.LONG / 2)) break
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("Maps did not open '$suggestion' for '$query' (Maps-side, decisions 3.5; on screen: ${visibleMapsTexts()})")
            }
            d.ui.waitForIdle(500)
        }
        d.waitFor(placeShareButton, E2e.LONG, "Share button on the Maps place page for '$suggestion'")
    }

    /** On a Maps place page: Share → our target "Add to widget" in Maps' share grid. */
    fun shareCurrentPlaceToOurApp() {
        d.waitFor(placeShareButton, E2e.LONG, "Share button on the Maps place page").click()
        d.waitFor(By.pkg(E2e.MAPS).text(E2e.SHARE_TARGET), E2e.LONG, "'${E2e.SHARE_TARGET}' in Maps' share sheet").click()
    }

    /** True when Maps is in front showing a place page (where a share returns to). */
    fun isOnPlacePage(): Boolean = d.ui.currentPackageName == E2e.MAPS && d.has(placeShareButton)

    private val placeShareButton: BySelector get() = res(E2e.MAPS, "card_stack_header_share_button")

    /** Ends navigation so the next tap starts fresh (avoids Maps' "Exit navigation?" dialog). */
    fun forceStop() {
        d.shell("am force-stop ${E2e.MAPS}")
    }

    private companion object {
        const val TAG = "NavShortE2e"
        const val POLL_MS = 1_000L
        const val GMS = "com.google.android.gms"
        const val MAPS_ACTIVITY = "com.google.android.apps.maps/com.google.android.maps.MapsActivity"

        /** Same rules as android.net.Uri.encode (what our widget uses for `q=`). */
        fun uriEncode(s: String): String = android.net.Uri.encode(s)
    }
}
