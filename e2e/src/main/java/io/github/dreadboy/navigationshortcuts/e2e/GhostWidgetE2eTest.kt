package io.github.dreadboy.navigationshortcuts.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.junit.runner.RunWith

/**
 * Niagara "ghost" widget IDs (verifier's Repro B, decisions 3.9): "Replace widget" with a new Navigation Shortcuts widget
 * keeps the old widget ID bound to us although only the new widget is on screen.
 *
 * Leaves ghost IDs behind that only uninstalling the app clears, so e2e/run-e2e.sh runs this class last, after
 * WidgetE2eTest, and reinstalls the app before every run.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING) // the stranded-places test runs after the chooser test (labels)
class GhostWidgetE2eTest {

    private val d = E2eDevice()
    private val niagara = Niagara(d)
    private val maps = GoogleMaps(d)
    private val sheet = ShareSheet(d)

    @Before
    fun setUp() {
        niagara.assertIsDefaultHome()
        maps.forceStop()
        niagara.removeCustomWidgetIfPresent()
        AppStore(d).reset() // a removed widget's places stay pending (decisions 5b E5): start clean (pm clear)
    }

    @After
    fun tearDown() {
        maps.forceStop()
    }

    @Test
    fun replaceWidget_shareLandsInVisibleWidget_ghostIsFlagged_andCreateNewWidgetReachesTheNextWidget() {
        val ghostsBefore = niagara.boundWidgetIdsOfOurApp().size

        // 1. A widget with one place.
        niagara.addOurWidget()
        maps.openPlace(Places.STOZICE.query, Places.STOZICE.suggestion)
        sheet.shareFromMapsPlacePage(maps)
        sheet.tapAdd()
        sheet.waitGone()
        niagara.assertWidgetCells(listOf(Places.STOZICE.label), E2e.DEFAULT_CELLS - 1)

        // 2. Niagara: long-press it → Add Custom Widget → Navigation Shortcuts → Replace widget. A new, empty widget is
        //    shown; the old ID stays bound (the ghost).
        niagara.replaceCustomWidgetWithNewOurs()
        niagara.assertEmptyWidget()
        assertEquals("bound IDs after Replace (old one stays bound)", ghostsBefore + 2, niagara.boundWidgetIdsOfOurApp().size)

        // 3. Share: the chooser lists the visible (newest) widget first and preselects it; Add puts the place there.
        maps.openPlace(Places.CASTLE.query, Places.CASTLE.suggestion)
        sheet.shareFromMapsPlacePage(maps)
        var rows = sheet.chooser()
        assertTrue("chooser shown with both widgets: $rows", rows.size >= 2)
        assertEquals("newest widget first and preselected: $rows", "Empty widget" to true, rows.first())
        assertTrue("ghost listed with its old place: $rows", rows.any { it.first.startsWith(Places.STOZICE.label) })
        assertEquals("'+ Create new widget' last: $rows", ShareSheet.CREATE_NEW to false, rows.last())
        sheet.tapAdd()
        sheet.waitGone()
        niagara.assertWidgetCells(listOf(Places.CASTLE.label), E2e.DEFAULT_CELLS - 1)

        // 4. Next share: the visible widget displayed its renders since, the ghost did not → flagged and listed last.
        maps.openPlace(Places.STOZICE.query, Places.STOZICE.suggestion)
        sheet.shareFromMapsPlacePage(maps)
        rows = sheet.chooser()
        assertEquals("visible widget first and preselected: $rows", Places.CASTLE.label to true, rows.first())
        val ghostRow = rows.single { it.first.startsWith(Places.STOZICE.label) }
        assertTrue("ghost flagged: $rows", ghostRow.first.endsWith("not seen on home screen"))
        assertEquals("flagged ghost listed after the visible widget, before '+ Create new widget': $rows", ghostRow, rows[rows.size - 2])
        assertEquals("no warning for the preselected visible widget", "", sheet.widgetHint())

        // 5. Way out: "+ Create new widget" → pin request (Niagara's sheet; left with Back, as its "Okay" with a widget
        //    present gets stuck, decisions 3.2/5.7) → the place stays pending → the next widget placed contains it.
        sheet.selectCreateNew()
        sheet.tapPrimary("Add")
        d.waitFor(By.pkg(E2e.NIAGARA).text("Add to home screen"), E2e.LONG, "Niagara's 'Add to home screen' pin sheet")
        d.ui.pressBack()
        d.waitFor(By.res(E2e.APP, "message_text").textStartsWith("Saved."), E2e.LONG, "'Saved…' explanation")
        sheet.tapPrimary("OK")
        sheet.waitGone()
        niagara.replaceCustomWidgetWithNewOurs()
        niagara.assertWidgetCells(listOf(Places.STOZICE.label), E2e.DEFAULT_CELLS - 1)
    }

    /**
     * Decisions 5b E5 round 2: "Replace widget" strands the old widget's places on a ghost (no onDeleted). The app screen
     * flags it and moves them to the widget on the home screen, where they show and navigate. Other ghosts this class
     * left behind may hold places too; they move along, so only "contains" is asserted.
     */
    @Test
    fun strandedPlaces_afterReplaceWidget_appScreenMovesThemToTheVisibleWidget() {
        val config = ConfigScreen(d)
        niagara.addOurWidget()
        niagara.addCell().click()
        config.awaitShown()
        config.addManually(ZOO.first, ZOO.second)
        config.addManually(TIVOLI.first, TIVOLI.second)
        config.save()
        niagara.assertWidgetCells(listOf(ZOO.first, TIVOLI.first), E2e.DEFAULT_CELLS - 2)

        niagara.replaceCustomWidgetWithNewOurs()
        niagara.assertEmptyWidget()

        // The app screen offers the move once the home screen showed the new widget without the old one.
        val move = By.res(E2e.APP, "stranded_move")
        val deadline = System.currentTimeMillis() + E2e.LONG
        while (true) {
            niagara.openOurApp()
            if (d.ui.wait(androidx.test.uiautomator.Until.hasObject(move), E2e.SHORT)) break
            if (System.currentTimeMillis() > deadline) throw AssertionError("no 'Move … places' offer in the app screen")
            niagara.goHome()
        }
        val text = d.waitFor(By.res(E2e.APP, "stranded_text"), what = "stranded places text").text.orEmpty()
        assertTrue("names the stranded places: '$text'", ZOO.first in text && TIVOLI.first in text)
        d.waitFor(move, what = "'Move … places' button").click()
        d.waitGone(move, E2e.LONG, "'Move … places' offer after moving")

        niagara.goHome()
        val deadline2 = System.currentTimeMillis() + E2e.LONG
        while (!niagara.cellLabels().containsAll(listOf(ZOO.first, TIVOLI.first))) {
            if (System.currentTimeMillis() > deadline2) {
                throw AssertionError("moved places not on the visible widget: ${niagara.cellLabels()}")
            }
            Thread.sleep(500)
            niagara.goHome()
        }
        val labels = niagara.cellLabels()
        assertEquals("each place once: $labels", labels.size, labels.toSet().size)
        maps.forceStop()
        niagara.cell(ZOO.first).click()
        maps.assertNavigationInForeground()
        maps.assertNavigationIntentFor(ZOO.second)
    }

    private companion object {
        val ZOO = "Zoo" to "Ljubljana Zoo"
        val TIVOLI = "Tivoli" to "Tivoli Park, Ljubljana"
    }
}
