package io.github.dreadboy.navigationshortcuts.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * MVP E2E (PLAN 3.8): real Niagara home screen (free tier, our widget is the only custom widget, decisions 2.4),
 * real widget placement, real Google Maps search + Share, real taps. Nothing is injected: destinations only get
 * into the widget through Maps' share sheet. Device preparation: e2e/setup-emulator.sh via e2e/run-e2e.sh.
 */
@RunWith(AndroidJUnit4::class)
class WidgetE2eTest {

    private val d = E2eDevice()
    private val niagara = Niagara(d)
    private val maps = GoogleMaps(d)
    private val sheet = ShareSheet(d)

    @Before
    fun setUp() {
        niagara.assertIsDefaultHome()
        maps.forceStop()
        niagara.removeCustomWidgetIfPresent()
        val stray = niagara.boundWidgetIdsOfOurApp()
        if (stray.isNotEmpty()) {
            throw AssertionError(
                "Widget IDs $stray are still bound to our provider with no widget on Niagara's home (ghosts left by " +
                    "Niagara's 'Replace widget', decisions 3.9). Only uninstalling the app clears them: run e2e/run-e2e.sh, " +
                    "which reinstalls the app, and keep GhostWidgetE2eTest last.",
            )
        }
    }

    @After
    fun tearDown() {
        maps.forceStop()
    }

    /** Harness smoke test: a new widget is an empty 1×4 grid; the place/remove helpers leave the slot empty. */
    @Test
    fun harness_newWidgetIsEmptyDefaultGrid_andCanBeRemoved() {
        niagara.addOurWidget()
        niagara.assertWidgetCells(emptyList(), E2e.DEFAULT_CELLS)

        niagara.removeCustomWidgetIfPresent()
        assertNull("custom widget still placed after Remove", niagara.placedWidget())
    }

    /**
     * Spec Flow 4: an empty cell opens the config screen for this widget, which keeps the "share a place from Google
     * Maps" tip as a hint; Cancel leaves the widget unchanged.
     */
    @Test
    fun emptyCellTap_opensConfigWithShareTip() {
        niagara.addOurWidget()
        niagara.assertWidgetCells(emptyList(), E2e.DEFAULT_CELLS)

        niagara.emptyCells()[1].click()
        val config = ConfigScreen(d).awaitShown()
        assertTrue("share tip in the config screen: '${config.hint()}'", config.hint().contains("share any place from Google Maps"))
        assertEquals(emptyList<String>(), config.entries())
        assertEquals(1 to 4, config.rows() to config.cols())
        config.cancel()

        niagara.assertWidgetCells(emptyList(), E2e.DEFAULT_CELLS)
    }

    /** Spec scenario 3b: Maps → place → Share → "Add to navigation widget" → Add → back in Maps → cell → navigation. */
    @Test
    fun shareFromMaps_addsCell_andTapStartsNavigation() {
        niagara.addOurWidget()

        maps.openPlace(STOZICE.query, STOZICE.suggestion)
        shareCurrentPlace()
        assertEquals("label prefilled from the shared place", STOZICE.label, sheet.label())
        val destination = sheet.destination()
        assertTrue("destination resolved from the link: '$destination'", destination.startsWith(STOZICE.label))
        val icon = sheet.iconDescription()
        assertTrue("icon guessed from the name: $icon", icon.endsWith("cat_soccer_game"))
        // Step 2 is always shown: the one widget (preselected), then "+ Create new widget".
        assertEquals(listOf("Empty widget" to true, ShareSheet.CREATE_NEW to false), sheet.chooser())
        // Back returns to step 1 with everything kept.
        sheet.back()
        assertEquals(STOZICE.label, sheet.label())
        assertEquals(destination, sheet.destination())
        assertEquals(icon, sheet.iconDescription())
        sheet.tapAdd()

        sheet.waitGone()
        d.ui.wait(androidx.test.uiautomator.Until.hasObject(By.pkg(E2e.MAPS)), E2e.SHORT)
        assertTrue("back on the Maps place page after Add (foreground ${d.ui.currentPackageName})", maps.isOnPlacePage())

        niagara.assertWidgetCells(listOf(STOZICE.label), E2e.DEFAULT_CELLS - 1)
        maps.forceStop()
        niagara.cell(STOZICE.label).click()
        maps.assertNavigationInForeground()
        maps.assertNavigationIntentFor(destination)
    }

    /**
     * Spec scenario 2 over shared entries (Maps force-stopped between taps, decisions 1.2) and scenario 5 with
     * ordinary process death (`am kill`): cells keep rendering and the first tap navigates.
     */
    @Test
    fun twoSharedEntries_eachTapStartsNavigation_alsoAfterAppProcessDeath() {
        niagara.addOurWidget()
        val stozice = shareAndAdd(STOZICE)
        val castle = shareAndAdd(CASTLE)
        niagara.assertWidgetCells(listOf(STOZICE.label, CASTLE.label), E2e.DEFAULT_CELLS - 2)

        for ((label, destination) in listOf(STOZICE.label to stozice, CASTLE.label to castle)) {
            maps.forceStop()
            niagara.goHome()
            niagara.cell(label).click()
            maps.assertNavigationInForeground()
            maps.assertNavigationIntentFor(destination)
        }

        maps.forceStop()
        niagara.goHome()
        killAppProcess()
        niagara.assertWidgetCells(listOf(STOZICE.label, CASTLE.label), E2e.DEFAULT_CELLS - 2)
        niagara.cell(CASTLE.label).click()
        maps.assertNavigationInForeground()
        maps.assertNavigationIntentFor(castle)
    }

    /**
     * Spec scenario 5 with `am force-stop`: Android 15+ masks a stopped app's widget until the first tap
     * (decisions 1.3, 2.4); the next tap on the shared cell navigates.
     */
    @Test
    fun afterAppForceStop_widgetIsMaskedUntilFirstTap_thenTapStartsNavigation() {
        niagara.addOurWidget()
        val destination = shareAndAdd(STOZICE)
        niagara.assertWidgetCells(listOf(STOZICE.label), E2e.DEFAULT_CELLS - 1)

        maps.forceStop()
        d.shell("am force-stop ${E2e.APP}")
        niagara.goHome()
        val mask = d.waitFor(
            By.pkg(E2e.NIAGARA).res("android", "work_widget_app_icon"),
            E2e.LONG,
            "system mask over the force-stopped app's widget",
        )
        mask.click()

        niagara.cell(STOZICE.label).click()
        maps.assertNavigationInForeground()
        maps.assertNavigationIntentFor(destination)
    }

    /** PLAN 3.7: a full grid offers "+1 column" / "+1 row"; one tap grows the grid and adds the place. */
    @Test
    fun gridFull_plusOneColumn_thenPlusOneRow() {
        niagara.addOurWidget()
        maps.openPlace(STOZICE.query, STOZICE.suggestion)
        val labels = (1..6).map { "S$it" }
        for ((i, label) in labels.withIndex()) {
            shareCurrentPlace()
            sheet.setLabel(label)
            when (i) {
                in 0..3 -> sheet.tapAdd()
                4 -> {
                    assertTrue("grid-full message: '${sheet.message()}'", sheet.message().contains("full (1×4)"))
                    sheet.tapGrow(column = true)
                }
                5 -> {
                    assertTrue("grid-full message: '${sheet.message()}'", sheet.message().contains("full (1×5)"))
                    sheet.tapGrow(column = false)
                }
            }
            sheet.waitGone()
            d.waitFor(By.res(E2e.MAPS, "card_stack_header_share_button"), E2e.LONG, "Maps place page after adding $label")
        }
        // 2 rows × 5 columns: six shortcuts, four empty cells.
        niagara.assertWidgetCells(labels, 4)
        val rows = niagara.cellLabels().size
        assertEquals(6, rows)
        maps.forceStop()
        niagara.cell("S6").click()
        maps.assertNavigationInForeground()
    }

    /**
     * Share with no widget placed → "Place new widget" → Niagara's pin confirmation → the new widget already contains
     * the place (requestPinAppWidget; decisions 3.2). Exactly one cell: pending list + callback don't duplicate it.
     */
    @Test
    fun noWidget_share_placeNewWidget_pinsWidgetWithThePlace() {
        assertNull(niagara.placedWidget())
        maps.openPlace(STOZICE.query, STOZICE.suggestion)
        shareCurrentPlace()
        assertTrue("no-widget message: '${sheet.message()}'", sheet.message().startsWith("No navigation widget"))
        assertEquals("only '+ Create new widget', preselected", listOf(ShareSheet.CREATE_NEW to true), sheet.chooser())
        val message = d.waitFor(By.res(E2e.APP, "widget_step_message"), what = "no-widget message")
        val list = d.waitFor(By.res(E2e.APP, "widget_chips"), what = "widget list")
        assertTrue("no-widget message above the list", message.visibleBounds.bottom <= list.visibleBounds.top)
        sheet.tapPrimary("Add")

        niagara.answerPinRequest(confirm = true)
        niagara.assertWidgetCells(listOf(STOZICE.label), E2e.DEFAULT_CELLS - 1)
        assertFalse("share sheet left behind", d.has(By.res(E2e.APP, "sheet_title")))

        maps.forceStop()
        niagara.cell(STOZICE.label).click()
        maps.assertNavigationInForeground()
    }

    /**
     * Pin cancelled → the place stays in the pending list and the sheet explains how to add the widget; a widget
     * placed later from Niagara's picker takes it over.
     */
    @Test
    fun noWidget_share_pinCancelled_placeIsPending_andNextWidgetGetsIt() {
        maps.openPlace(STOZICE.query, STOZICE.suggestion)
        shareCurrentPlace()
        sheet.tapPrimary("Add")

        niagara.answerPinRequest(confirm = false)
        d.waitFor(By.res(E2e.APP, "message_text").textStartsWith("Saved."), E2e.LONG, "'Saved…' explanation in the sheet")
        sheet.tapPrimary("OK")
        sheet.waitGone()

        niagara.goHome()
        assertNull("no widget placed after cancelling", niagara.placedWidget())
        niagara.addOurWidget()
        niagara.assertWidgetCells(listOf(STOZICE.label), E2e.DEFAULT_CELLS - 1)
    }

    /**
     * Decisions 5.7: "+ Create new widget" requests pinning also while a widget exists. Niagara's stuck overlay after
     * "Okay" in that case is a known Niagara bug (decisions 3.2), so the test goes only as far as Niagara's
     * "Add to home screen" sheet, leaves it with Back, and checks the pending fallback: the sheet says the place is
     * saved, the existing widget is unchanged, and the next widget placed takes the place over.
     */
    @Test
    fun widgetExists_createNewWidget_requestsPin_backKeepsPlacePending() {
        niagara.addOurWidget()
        shareAndAdd(STOZICE)
        niagara.assertWidgetCells(listOf(STOZICE.label), E2e.DEFAULT_CELLS - 1)

        maps.openPlace(CASTLE.query, CASTLE.suggestion)
        shareCurrentPlace()
        sheet.selectCreateNew()
        assertTrue("create-new hint: '${sheet.message()}'", sheet.message().contains("new navigation widget"))
        sheet.tapPrimary("Add")
        d.waitFor(By.pkg(E2e.NIAGARA).text("Add to home screen"), E2e.LONG, "Niagara's 'Add to home screen' pin sheet")
        d.ui.pressBack()
        d.waitFor(By.res(E2e.APP, "message_text").textStartsWith("Saved."), E2e.LONG, "'Saved…' confirmation in the sheet")
        sheet.tapPrimary("OK")
        sheet.waitGone()

        niagara.goHome()
        niagara.assertWidgetCells(listOf(STOZICE.label), E2e.DEFAULT_CELLS - 1)
        // The pending place goes into the next widget placed (here: after removing the current one).
        niagara.removeCustomWidgetIfPresent()
        niagara.addOurWidget()
        niagara.assertWidgetCells(listOf(CASTLE.label), E2e.DEFAULT_CELLS - 1)
    }

    /** On a Maps place page: Share → our sheet, resolved (re-shares once on Maps' cid fallback, decisions 3.5). */
    private fun shareCurrentPlace() = sheet.shareFromMapsPlacePage(maps)

    /** Opens [place] in Maps, shares it to us and taps Add; returns the resolved destination. */
    private fun shareAndAdd(place: Place): String {
        maps.openPlace(place.query, place.suggestion)
        shareCurrentPlace()
        assertEquals(place.label, sheet.label())
        val destination = sheet.destination()
        sheet.tapAdd()
        sheet.waitGone()
        return destination
    }

    /** Kills the app's process without putting it into the stopped state (unlike `am force-stop`). */
    private fun killAppProcess() {
        d.shell("am kill ${E2e.APP}")
        val pid = d.shell("pidof ${E2e.APP}").trim()
        assertEquals("app process still alive after am kill", "", pid)
    }

    private companion object {
        val STOZICE = Places.STOZICE
        val CASTLE = Places.CASTLE
    }
}
