package io.github.dreadboy.navigationshortcuts.anycons

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.graphics.createBitmap
import io.github.dreadboy.navigationshortcuts.anycons.glyph.PaintRole
import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconSettings
import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Niagara's 549 per-app glyphs (`ic_ip_app_*`, PLAN 5b.4) parse with only the three Anycons colour roles and render
 * visibly in every pack. Writes one contact sheet per pack into `anycons/build/app-glyphs/` for visual review (Niagara
 * art: build dir only, never committed).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppGlyphsTest {

    private fun appGlyphs(): List<String> =
        File(NiagaraFixtures.requireDecoded(), "res/drawable").list()!!
            .filter { it.startsWith("ic_ip_app_") && it.endsWith(".xml") }
            .map { it.removePrefix("ic_ip_").removeSuffix(".xml") }
            .sorted()

    @Test
    fun appGlyphsUseOnlyAnyconColourRoles() {
        val apps = appGlyphs()
        assertEquals(549, apps.size)
        val allowed = setOf(PaintRole.NONE, PaintRole.BG, PaintRole.FG, PaintRole.STROKE)
        for (name in apps) {
            val glyph = NiagaraFixtures.source.glyph(name)
            assertNotNull(name, glyph)
            assertEquals("$name viewport", 24f, glyph!!.viewportWidth)
            for (path in glyph.paths) {
                assertTrue("$name fill ${path.fillRole}", path.fillRole in allowed)
                assertTrue("$name stroke ${path.strokeRole}", path.strokeRole in allowed)
            }
            assertTrue("$name has an outline", glyph.paths.any { it.strokeRole == PaintRole.STROKE })
        }
    }

    @Test
    fun appGlyphsRenderInEveryPack() {
        val apps = appGlyphs()
        val renderer = AnyconRenderer(NiagaraFixtures.source, maxCacheBytes = 1)
        val cell = 48
        val cols = 30
        val outDir = File(System.getProperty("anycons.buildDir") ?: "build", "app-glyphs")
        outDir.mkdirs()
        for ((label, style) in PACKS) {
            val settings = AnyconSettings(style, seedColor = ContactSheetTest.SEED)
            val sheet = createBitmap(cols * cell, ((apps.size + cols - 1) / cols) * cell)
            val canvas = Canvas(sheet)
            canvas.drawColor(if (style is AnyconStyle.Outline) 0xFF2B2B2B.toInt() else 0xFFE4E4E4.toInt())
            val blank = ArrayList<String>()
            apps.forEachIndexed { i, name ->
                val icon = renderer.render(name, settings, cell, seedKey = name)
                assertNotNull("$label / $name", icon)
                if (visibleFraction(icon!!) < 0.05) blank += name
                canvas.drawBitmap(icon, ((i % cols) * cell).toFloat(), ((i / cols) * cell).toFloat(), null)
            }
            File(outDir, "app-glyphs-${label.replace(' ', '_')}.png").outputStream().use {
                sheet.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            assertTrue("$label: nearly blank app glyphs $blank", blank.isEmpty())
        }
    }

    private fun visibleFraction(bitmap: Bitmap): Double {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.count { Color.alpha(it) > 64 } / pixels.size.toDouble()
    }

    companion object {
        val PACKS: List<Pair<String, AnyconStyle>> = listOf(
            "Sticker Coral" to AnyconStyle.Sticker(AnyconStyle.Sticker.Palette.CORAL),
            "Sticker Theme" to AnyconStyle.Sticker(),
            "Material" to AnyconStyle.Material(),
            "Outline" to AnyconStyle.Outline(),
            "Outline Chroma" to AnyconStyle.OutlineChroma(),
            "Watercolor" to AnyconStyle.Watercolor(),
            "Marble" to AnyconStyle.Marble(),
            "Bright" to AnyconStyle.Bright(),
            "Stellar" to AnyconStyle.Stellar,
        )
    }
}
