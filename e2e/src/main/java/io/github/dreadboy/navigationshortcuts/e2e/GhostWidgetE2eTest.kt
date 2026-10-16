package io.github.dreadboy.navigationshortcuts.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Niagara "ghost" widget IDs (verifier's Repro B, decisions 3.9): "Replace widget" with a new Navigation Shortcuts widget
 * keeps the old widget ID bound to us although only the new widget is on screen.
 *
 * Leaves ghost IDs behind that only uninstalling the app clears, so e2e/run-e2e.sh runs this class last, after
 * WidgetE2eTest, and reinstalls the app before every run.
 */
@RunWith(AndroidJUnit4::class)
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
        niagara.assertWidgetCells(emptyList(), E2e.DEFAULT_CELLS)
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
}
