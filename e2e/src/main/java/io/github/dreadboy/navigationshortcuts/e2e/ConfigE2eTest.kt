package io.github.dreadboy.navigationshortcuts.e2e

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * Phase 4 E2E (spec scenarios 3, 4, 7; PLAN 4.1–4.4): the per-widget config screen through all three entry points
 * (empty cell, Niagara's long-press "Change widget settings", the app's widget list), manual add, grid resize, delete,
 * drag reorder and icon style. Real Niagara, real taps, real Google Maps; nothing injected.
 */
@RunWith(AndroidJUnit4::class)
class ConfigE2eTest {

    private val d = E2eDevice()
    private val niagara = Niagara(d)
    private val maps = GoogleMaps(d)
    private val config = ConfigScreen(d)

    @Before
    fun setUp() {
        niagara.assertIsDefaultHome()
        maps.forceStop()
        niagara.removeCustomWidgetIfPresent()
        val stray = niagara.boundWidgetIdsOfOurApp()
        if (stray.isNotEmpty()) {
            throw AssertionError("Widget IDs $stray still bound to our provider (ghosts, decisions 3.9): run e2e/run-e2e.sh")
        }
    }

    @After
    fun tearDown() {
        maps.forceStop()
    }

    /**
     * Spec scenarios 3 + 4: empty cell → config; add three destinations manually; empty cell again shows them; grid
     * 1×4 → 2×2 (shrinking below the entry count is blocked); delete one; drag the last to the top; Save → the widget
     * re-renders as 2×2 in the new order and a filled cell still navigates.
     */
    @Test
    fun emptyCell_config_addThree_resizeTo2x2_deleteAndReorder_widgetReRendersAndNavigates() {
        niagara.addOurWidget()
        niagara.emptyCells()[0].click()
        config.awaitShown()
        config.addManually(BTC.first, BTC.second)
        config.addManually(ZOO.first, ZOO.second)
        config.addManually(TIVOLI.first, TIVOLI.second)
        assertEquals(listOf(BTC.first, ZOO.first, TIVOLI.first), config.entries())
        config.save()
        niagara.assertWidgetCells(listOf(BTC.first, ZOO.first, TIVOLI.first), 1)

        // Scenario 3: the empty cell opens this widget's config with the three destinations.
        niagara.emptyCells().single().click()
        config.awaitShown()
        assertEquals(listOf(BTC.first, ZOO.first, TIVOLI.first), config.entries())

        // Scenario 4: 1×4 → 2×4 → 2×3 → 2×2; 2×1 would hold fewer than three: blocked with a note.
        config.tapStepper("More rows")
        config.tapStepper("Fewer columns")
        config.tapStepper("Fewer columns")
        assertEquals(2 to 2, config.rows() to config.cols())
        assertFalse("shrinking below 3 destinations must be blocked", config.isStepperEnabled("Fewer columns"))
        assertTrue("grid note: '${config.gridNote()}'", config.gridNote().contains("can't be smaller"))

        config.delete(ZOO.first)
        config.drag(TIVOLI.first, BTC.first)
        assertEquals(listOf(TIVOLI.first, BTC.first), config.entries())
        config.save()

        niagara.assertWidgetCells(listOf(TIVOLI.first, BTC.first), 2)
        val labelTop = niagara.cell(TIVOLI.first).visibleBounds.top
        assertTrue("2×2: the empty cells are in the second row", niagara.emptyCells().all { it.visibleBounds.top > labelTop })

        maps.forceStop()
        niagara.cell(BTC.first).click()
        maps.assertNavigationInForeground()
        maps.assertNavigationIntentFor(BTC.second)
    }

    /** Spec scenario 7: Niagara long-press → "Change widget settings" opens our config for that widget. */
    @Test
    fun niagaraLongPress_changeWidgetSettings_opensConfigForThatWidget() {
        niagara.addOurWidget()
        niagara.emptyCells()[0].click()
        config.awaitShown()
        config.addManually(ZOO.first, ZOO.second)
        config.save()
        niagara.assertWidgetCells(listOf(ZOO.first), 3)

        niagara.changeWidgetSettings()
        config.awaitShown()
        assertEquals("config of the long-pressed widget", listOf(ZOO.first), config.entries())
        config.rename(ZOO.first, "Ljubljana Zoo")
        config.save()

        niagara.assertWidgetCells(listOf("Ljubljana Zoo"), 3)
        maps.forceStop()
        niagara.cell("Ljubljana Zoo").click()
        maps.assertNavigationInForeground()
        maps.assertNavigationIntentFor(ZOO.second)
    }

    /**
     * Icon style: Niagara glyphs (Home → cat_home, Gym → cat_fitness) in the default Sticker Coral, then Watercolor:
     * the cell icons change on screen (pixel comparison of the icon area) and the cell still navigates. Screenshots of
     * both are saved as evidence (test app's external files dir).
     */
    @Test
    fun styleChange_reRendersCells_andStillNavigates() {
        niagara.addOurWidget()
        niagara.emptyCells()[0].click()
        config.awaitShown()
        assertTrue("default style: ${config.previewDescription()}", config.previewDescription().endsWith("Sticker · Coral"))
        config.addManually("Home", BTC.second)
        config.addManually("Gym", ZOO.second)
        config.save()
        niagara.assertWidgetCells(listOf("Home", "Gym"), 2)
        SystemClock.sleep(1_000) // let the launcher draw the new bitmaps before the reference shot
        val before = iconArea("Home")
        saveScreenshot("style-sticker-coral")

        niagara.emptyCells()[0].click()
        config.awaitShown()
        config.select("style_pack", "Watercolor")
        assertTrue("preview: ${config.previewDescription()}", config.previewDescription().contains("Watercolor"))
        config.save()

        val deadline = System.currentTimeMillis() + E2e.LONG
        var changed = 0.0
        while (System.currentTimeMillis() < deadline) {
            niagara.goHome()
            changed = differingFraction(before, iconArea("Home"))
            if (changed > MIN_CHANGED) break
            SystemClock.sleep(500)
        }
        saveScreenshot("style-watercolor")
        assertTrue("Home icon area changed after the style change: ${"%.1f".format(changed * 100)} % pixels", changed > MIN_CHANGED)
        niagara.assertWidgetCells(listOf("Home", "Gym"), 2)

        maps.forceStop()
        niagara.cell("Home").click()
        maps.assertNavigationInForeground()
        maps.assertNavigationIntentFor(BTC.second)
    }

    /**
     * PLAN 4.2: the app's launcher activity lists the placed widget ("Empty widget"), tapping it opens that widget's
     * config; a manually added place (label + address) appears in the list and on the widget, and its cell navigates.
     */
    @Test
    fun launcherActivityList_opensConfig_manualAdd_cellNavigates() {
        niagara.addOurWidget()
        niagara.openOurApp()
        d.waitFor(By.pkg(E2e.APP).text("Empty widget"), E2e.LONG, "our widget in the app's widget list").click()
        config.awaitShown()
        config.addManually(TIVOLI.first, TIVOLI.second)
        config.save()
        d.waitFor(By.pkg(E2e.APP).text(TIVOLI.first), E2e.LONG, "the app's widget list showing the new label")

        niagara.goHome()
        niagara.assertWidgetCells(listOf(TIVOLI.first), 3)
        maps.forceStop()
        niagara.cell(TIVOLI.first).click()
        maps.assertNavigationInForeground()
        maps.assertNavigationIntentFor(TIVOLI.second)
    }

    /** Screen area of the cell's icon (cell bounds above its label). */
    private fun iconArea(label: String): Bitmap {
        val labelView = niagara.cell(label)
        val cell = d.waitFor(By.pkg(E2e.APP).desc("Navigate to $label"), what = "cell '$label'")
        val area = Rect(cell.visibleBounds.left, cell.visibleBounds.top, cell.visibleBounds.right, labelView.visibleBounds.top)
        val shot = screenshot()
        return Bitmap.createBitmap(shot, area.left, area.top, area.width(), area.height())
    }

    private fun screenshot(): Bitmap =
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            ?: throw AssertionError("screenshot failed")

    /** Fraction of pixels whose colour differs noticeably (any channel > 40). */
    private fun differingFraction(a: Bitmap, b: Bitmap): Double {
        if (a.width != b.width || a.height != b.height) return 1.0
        var diff = 0
        for (y in 0 until a.height step 2) for (x in 0 until a.width step 2) {
            val p = a.getPixel(x, y)
            val q = b.getPixel(x, y)
            if (abs(Color.red(p) - Color.red(q)) > 40 || abs(Color.green(p) - Color.green(q)) > 40 ||
                abs(Color.blue(p) - Color.blue(q)) > 40
            ) diff++
        }
        return diff.toDouble() / (((a.width + 1) / 2) * ((a.height + 1) / 2))
    }

    private fun saveScreenshot(name: String) {
        val dir = InstrumentationRegistry.getInstrumentation().context.getExternalFilesDir("screenshots") ?: return
        val file = File(dir, "$name.png")
        file.outputStream().use { screenshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
        Log.i("NavShortE2e", "screenshot saved: ${file.absolutePath}")
    }

    private companion object {
        val BTC = "BTC" to "BTC City, Ljubljana"
        val ZOO = "Zoo" to "Ljubljana Zoo"
        val TIVOLI = "Tivoli" to "Tivoli Park, Ljubljana"

        /** Share of icon-area pixels that must change for "re-rendered in the new style". */
        const val MIN_CHANGED = 0.10
    }
}
