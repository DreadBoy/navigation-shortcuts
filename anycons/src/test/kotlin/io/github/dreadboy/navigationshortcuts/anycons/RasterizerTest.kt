package io.github.dreadboy.navigationshortcuts.anycons

import io.github.dreadboy.navigationshortcuts.anycons.BitmapAsserts.alphaBounds
import io.github.dreadboy.navigationshortcuts.anycons.BitmapAsserts.alphaHistogram
import io.github.dreadboy.navigationshortcuts.anycons.BitmapAsserts.opaqueCount
import io.github.dreadboy.navigationshortcuts.anycons.BitmapAsserts.pixels
import io.github.dreadboy.navigationshortcuts.anycons.glyph.GlyphRasterizer
import io.github.dreadboy.navigationshortcuts.anycons.glyph.MaskStyle
import io.github.dreadboy.navigationshortcuts.anycons.glyph.StrokeWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RasterizerTest {

    @Test
    fun `mask modes give the documented role alphas`() {
        val house = TestGlyphs.house()
        val n = 96
        val mixed = alphaHistogram(GlyphRasterizer.renderMask(house, MaskStyle.MIXED_FILL, n))
        assertTrue("MixedFill bg alpha 0xA0 present", (mixed[0xA0] ?: 0) > 500)
        assertTrue("MixedFill fg opaque present", (mixed[0xFF] ?: 0) > 100)
        val subtle = alphaHistogram(GlyphRasterizer.renderMask(house, MaskStyle.SUBTLE_MIXED_FILL, n))
        assertTrue((subtle[0x60] ?: 0) > 500)
        val complete = alphaHistogram(GlyphRasterizer.renderMask(house, MaskStyle.COMPLETE_FILL, n))
        assertTrue((complete[0xFF] ?: 0) > 2000)
        assertEquals(0, complete[0xA0] ?: 0)
        // Fill styles have stroke alpha 0, stroke styles have fill alpha 0.
        val stroke = GlyphRasterizer.renderMask(house, MaskStyle.stroke(StrokeWeight.W15), n)
        assertTrue(opaqueCount(stroke) in 300 until opaqueCount(GlyphRasterizer.renderMask(house, MaskStyle.COMPLETE_FILL, n)))
    }

    @Test
    fun `thicker stroke covers more pixels`() {
        val house = TestGlyphs.house()
        val thin = opaqueCount(GlyphRasterizer.renderMask(house, MaskStyle.stroke(StrokeWeight.W11), 128), 128)
        val bold = opaqueCount(GlyphRasterizer.renderMask(house, MaskStyle.stroke(StrokeWeight.W17), 128), 128)
        assertTrue("$thin < $bold", thin < bold)
    }

    @Test
    fun `stamp stroke scales about the pivot by 0_7 and adds a rim`() {
        val house = TestGlyphs.house()
        val full = alphaBounds(GlyphRasterizer.renderMask(house, MaskStyle.COMPLETE_FILL, 240))!!
        val stamp = alphaBounds(GlyphRasterizer.renderMask(house, MaskStyle.STAMP_STROKE, 240))!!
        // Silhouette spans 16 units at scale 1 (x 4..20); at 0.7 plus 2×2.1 rim: 11.2 + 4.2 = 15.4 units.
        assertEquals(160.0, full.width().toDouble(), 3.0)
        assertEquals(154.0, stamp.width().toDouble(), 4.0)
        assertEquals(full.exactCenterX().toDouble(), stamp.exactCenterX().toDouble(), 2.0)
    }

    @Test
    fun `tint keeps per-path alpha`() {
        val house = TestGlyphs.house()
        val tinted = GlyphRasterizer.renderTinted(house, MaskStyle.MIXED_FILL, 0xFF3366CC.toInt(), 64)
        val px = pixels(tinted).filter { (it ushr 24) == 0xA0 }
        assertTrue(px.isNotEmpty())
        // Non-premultiplied read-back: colour channels are the tint.
        assertTrue(px.all { abs(((it shr 16) and 0xFF) - 0x33) <= 2 && abs((it and 0xFF) - 0xCC) <= 2 })
        val halfTint = GlyphRasterizer.renderTinted(house, MaskStyle.COMPLETE_FILL, 0x80FF0000.toInt(), 64)
        val maxAlpha = pixels(halfTint).maxOf { it ushr 24 }
        assertEquals(0x80.toDouble(), maxAlpha.toDouble(), 1.0)
    }

    @Test
    fun `narrow 16x24 glyph is fitted and centred in the square`() {
        val flash = NiagaraFixtures.parseGlyph("act_flash_small_and_narrow")
        val b = GlyphRasterizer.renderMask(flash, MaskStyle.stroke(StrokeWeight.W15), 96)
        val box = alphaBounds(b)
        assertNotNull(box)
        box!!
        assertEquals(48.0, box.exactCenterX().toDouble(), 3.0)
        assertTrue("fits vertically: $box", box.top >= 0 && box.bottom <= 96)
        assertTrue("narrower than tall: $box", box.width() < box.height())
    }

    @Test
    fun `48 viewport glyph renders inside the square`() {
        val pause = NiagaraFixtures.parseGlyph("act_pause_circled_48")
        val b = GlyphRasterizer.renderMask(pause, MaskStyle.stroke(StrokeWeight.W15), 96)
        val box = alphaBounds(b)!!
        assertTrue("$box", box.width() > 60 && box.left >= 0 && box.right <= 96)
    }

    @Test
    fun `stroke-coloured fills belong to the stroke layer`() {
        val dots = NiagaraFixtures.parseGlyph("act_dots_three_vertical")
        assertEquals(0, opaqueCount(GlyphRasterizer.renderMask(dots, MaskStyle.MIXED_FILL, 96)))
        val stroked = GlyphRasterizer.renderMask(dots, MaskStyle.stroke(StrokeWeight.W15), 96)
        // Centre of the middle dot is filled (the dot radius is 1 unit, wider than the stroke alone would cover).
        assertEquals(0xFF, pixels(stroked)[48 * 96 + 48] ushr 24)
    }

    @Test
    fun `every destination glyph renders in every mask mode`() {
        val styles = listOf(
            MaskStyle.MIXED_FILL, MaskStyle.SUBTLE_MIXED_FILL, MaskStyle.COMPLETE_FILL, MaskStyle.STAMP_STROKE,
        ) + StrokeWeight.entries.map { MaskStyle.stroke(it) }
        for (name in ContactSheetTest.GLYPHS) {
            val g = NiagaraFixtures.parseGlyph(name)
            for (s in styles) assertTrue("$name $s", opaqueCount(GlyphRasterizer.renderMask(g, s, 48)) > 20)
        }
    }
}
