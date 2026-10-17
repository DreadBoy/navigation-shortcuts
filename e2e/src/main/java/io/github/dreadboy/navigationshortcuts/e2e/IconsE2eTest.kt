package io.github.dreadboy.navigationshortcuts.e2e

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Decisions 5.6 (v0.3.1): with Niagara installed, Material icons never show. Real Niagara, real Maps share, real
 * taps. The only non-user step is the debug-build store hook that seeds a 0.2.0-style `builtin` icon (precondition).
 */
@RunWith(AndroidJUnit4::class)
class IconsE2eTest {

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
            throw AssertionError("Widget IDs $stray still bound to our provider (ghosts, decisions 3.9): run e2e/run-e2e.sh")
        }
    }

    @After
    fun tearDown() {
        maps.forceStop()
    }

    /**
     * Share Stožice Stadium: the guessed icon is Niagara's soccer glyph (stored as `niagara`), the picker lists only
     * Niagara glyphs (no "Built-in …" entry for any of the 15 Material names), and the cell is drawn as a Niagara glyph.
     * Then a stored `builtin` icon (0.2.0 data) renders as its Niagara equivalent: `builtin:stadium` gives exactly the
     * soccer glyph's pixels, `builtin:home` a different Niagara glyph — colourful, not the white Material vector.
     */
    @Test
    fun niagaraPresent_shareGuessPickerAndStoredBuiltInIcons_areAllNiagaraGlyphs() {
        niagara.addOurWidget()
        maps.openPlace(STOZICE.query, STOZICE.suggestion)
        sheet.shareFromMapsPlacePage(maps)
        assertEquals(STOZICE.label, sheet.label())
        assertTrue("guessed icon is Niagara's soccer glyph: ${sheet.iconDescription()}", sheet.iconDescription().endsWith(": Soccer"))

        // Picker: only Niagara glyphs.
        d.waitFor(By.res(E2e.APP, "icon_button"), what = "icon button").click()
        val search = d.waitFor(By.res(E2e.APP, "icon_search"), E2e.LONG, "picker search field")
        d.waitFor(By.res(E2e.APP, "icon_grid"), E2e.LONG, "picker grid")
        assertTrue("no 'Niagara missing' note", !d.has(By.pkg(E2e.APP).textStartsWith("Niagara isn't installed")))
        saveScreenshot("picker-niagara-only")
        for ((name, glyph) in BUILT_IN_NAMES) {
            search.text = name
            SystemClock.sleep(300)
            val descs = gridDescriptions()
            assertTrue("no built-in entry for '$name': $descs", descs.none { it.startsWith("Built-in") })
            // The concept name is a search alias of its mapped Niagara glyph.
            assertTrue("'$name' finds the '$glyph' glyph: $descs", glyph in descs)
        }
        search.text = "qqqzzz"
        d.waitFor(By.res(E2e.APP, "icon_empty").text("No icons match"), what = "'No icons match'")
        search.text = "soccer"
        d.waitFor(By.res(E2e.APP, "icon_grid").hasDescendant(By.desc("Soccer")), what = "soccer glyph in the picker")
        d.waitFor(By.pkg(E2e.APP).text("Cancel"), what = "picker Cancel").click()
        d.waitGone(By.res(E2e.APP, "icon_grid"), what = "picker")

        sheet.tapAdd()
        sheet.waitGone()
        niagara.assertWidgetCells(listOf(STOZICE.label), E2e.DEFAULT_CELLS - 1)
        assertEquals("stored icon", "niagara" to "cat_soccer_game", storedIcon(STOZICE.label))
        SystemClock.sleep(1_000)
        val shared = iconArea(STOZICE.label)
        // Default icon size = Niagara's home-screen app icon, 44 dp (decisions 5b U).
        val expectedPx = 44 * InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        assertEquals("icon width like Niagara's 44 dp app icons", expectedPx.toDouble(), shared.width.toDouble(), 2.0)
        saveScreenshot("widget-shared-stadium")
        assertTrue("shared cell is a colourful Niagara glyph (${colourful(shared)})", colourful(shared) > MIN_COLOURFUL)

        // 0.2.0 data: builtin:home → Niagara's home glyph (a re-render, different from the soccer glyph).
        seedIcon(STOZICE.label, "builtin", "home")
        val home = awaitIconChange(STOZICE.label, shared)
        saveScreenshot("widget-builtin-home")
        assertTrue("builtin:home drawn as a colourful Niagara glyph (${colourful(home)})", colourful(home) > MIN_COLOURFUL)

        // builtin:stadium → exactly the soccer glyph the share produced (same glyph, same seed).
        seedIcon(STOZICE.label, "builtin", "stadium")
        val stadium = awaitIconChange(STOZICE.label, home)
        saveScreenshot("widget-builtin-stadium")
        val diff = differingFraction(shared, stadium)
        assertTrue("builtin:stadium renders as cat_soccer_game (${"%.1f".format(diff * 100)} % pixels differ)", diff < MAX_SAME)
        assertEquals("stored data untouched by rendering", "builtin" to "stadium", storedIcon(STOZICE.label))

        maps.forceStop()
        niagara.cell(STOZICE.label).click()
        maps.assertNavigationInForeground()
    }

    private fun gridDescriptions(): List<String> =
        d.find(By.res(E2e.APP, "icon_grid"))?.findObjects(By.clickable(true))?.mapNotNull { it.contentDescription } ?: emptyList()

    /** (source, name) of the stored icon of the entry labelled [label], via the debug store dump. */
    private fun storedIcon(label: String): Pair<String, String> {
        val out = d.shell("am broadcast -n ${E2e.APP}/.DebugStoreReceiver -a ${E2e.APP}.debug.DUMP_STORE")
        val entry = Regex("""\{[^{}]*"label":"${Regex.escape(label)}"[^{}]*"icon":\{"source":"([^"]*)","name":"([^"]*)"\}""")
            .find(out.replace("\\\"", "\""))
            ?: throw AssertionError("entry '$label' not in the store dump: $out")
        return entry.groupValues[1] to entry.groupValues[2]
    }

    /** Precondition only: stores [source]:[name] as the icon of the first cell, [label] (debug build hook). */
    private fun seedIcon(label: String, source: String, name: String) {
        val out = d.shell(
            "am broadcast -n ${E2e.APP}/.DebugStoreReceiver -a ${E2e.APP}.debug.SET_ICON " +
                "--ei index 0 --es source $source --es name $name",
        )
        assertEquals("seeded icon (${out.trim()})", source to name, storedIcon(label))
    }

    private fun awaitIconChange(label: String, before: Bitmap): Bitmap {
        val deadline = System.currentTimeMillis() + E2e.LONG
        var now = iconArea(label)
        while (differingFraction(before, now) < MIN_CHANGED && System.currentTimeMillis() < deadline) {
            SystemClock.sleep(500)
            now = iconArea(label)
        }
        assertTrue("cell '$label' re-rendered", differingFraction(before, now) >= MIN_CHANGED)
        SystemClock.sleep(500)
        return iconArea(label)
    }

    /** Screen area of the cell's icon view. */
    private fun iconArea(label: String): Bitmap {
        niagara.goHome()
        d.ui.wait(Until.hasObject(By.pkg(E2e.APP).desc("Navigate to $label")), E2e.LONG)
        val labelView = niagara.cell(label)
        val cell = d.waitFor(By.pkg(E2e.APP).desc("Navigate to $label"), what = "cell '$label'")
        // The icon view itself: icons are 44 dp and left-aligned in a wider cell (decisions 5b U/E19).
        val icon = cell.findObject(By.res(E2e.APP, "cell_icon")) ?: throw AssertionError("cell '$label' has no icon view")
        val area = Rect(icon.visibleBounds)
        assertTrue("icon of '$label' above its label", area.bottom <= labelView.visibleBounds.top)
        val shot = screenshot()
        return Bitmap.createBitmap(shot, area.left, area.top, area.width(), area.height())
    }

    /**
     * Share of pixels with clearly visible colour (HSV-ish saturation): Niagara's Sticker Coral glyphs are coloured;
     * the Material fallback is a white glyph with a grey outline, so it scores ~0.
     */
    private fun colourful(b: Bitmap): Double {
        var n = 0
        var total = 0
        for (y in 0 until b.height step 2) for (x in 0 until b.width step 2) {
            val p = b.getPixel(x, y)
            val hi = max(Color.red(p), max(Color.green(p), Color.blue(p)))
            val lo = min(Color.red(p), min(Color.green(p), Color.blue(p)))
            if (hi - lo > 60) n++
            total++
        }
        return n.toDouble() / total
    }

    private fun screenshot(): Bitmap =
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            ?: throw AssertionError("screenshot failed")

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
        val file = File(dir, "icons-$name.png")
        file.outputStream().use { screenshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
        Log.i("NavShortE2e", "screenshot saved: ${file.absolutePath}")
    }

    private companion object {
        val STOZICE = Places.STOZICE

        /** The 15 built-in (Material) icon names → picker label of the mapped Niagara glyph (decisions 5.6, 5b). */
        val BUILT_IN_NAMES = listOf(
            "pin" to "Location", "home" to "Home", "work" to "Office", "gym" to "Fitness", "stadium" to "Soccer",
            "school" to "Education", "pool" to "Swimming", "shop" to "Shopping", "restaurant" to "Food",
            "cafe" to "Coffee", "hospital" to "Health", "airport" to "Air travel", "parking" to "Cars",
            "mountain" to "Travel", "castle" to "Castle",
        )

        const val MIN_CHANGED = 0.05
        const val MAX_SAME = 0.02
        const val MIN_COLOURFUL = 0.03
    }
}
