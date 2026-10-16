package io.github.dreadboy.navigationshortcuts.anycons

import android.graphics.Bitmap
import io.github.dreadboy.navigationshortcuts.anycons.BitmapAsserts.distinctColours
import io.github.dreadboy.navigationshortcuts.anycons.BitmapAsserts.opaqueCount
import io.github.dreadboy.navigationshortcuts.anycons.BitmapAsserts.pixels
import io.github.dreadboy.navigationshortcuts.anycons.glyph.Glyph
import io.github.dreadboy.navigationshortcuts.anycons.render.AnyconComposer
import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconSettings
import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconStyle
import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconStyle.Dots
import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconStyle.Marble
import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconStyle.Material
import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconStyle.Outline
import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconStyle.Sticker
import io.github.dreadboy.navigationshortcuts.anycons.settings.AppOrThemePalette
import io.github.dreadboy.navigationshortcuts.anycons.settings.IconShape
import io.github.dreadboy.navigationshortcuts.anycons.settings.Thickness
import io.github.dreadboy.navigationshortcuts.anycons.source.GlyphSource
import io.github.dreadboy.navigationshortcuts.anycons.source.Texture
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposerTest {

    private fun render(glyph: Glyph?, settings: AnyconSettings, source: GlyphSource, size: Int = 96, seed: Long = 42): Bitmap? =
        AnyconComposer.render(glyph, settings, source::texture, size, Random(seed))

    private fun assertRenders(glyph: Glyph?, source: GlyphSource, label: String) {
        for (style in ALL_VARIANTS) {
            for (shadow in listOf(false, true)) {
                val b = render(glyph, AnyconSettings(style, launcherShadow = shadow), source)
                assertNotNull("$label $style", b)
                assertEquals(96, b!!.width)
                assertEquals(96, b.height)
                assertTrue("$label $style is empty", opaqueCount(b) > 100)
            }
        }
    }

    @Test
    fun `every pack and variant renders with a synthetic glyph and textures`() {
        assertRenders(TestGlyphs.house(), TestGlyphs.syntheticSource(), "synthetic")
    }

    @Test
    fun `every pack and variant renders with Niagara glyphs and textures`() {
        val source = NiagaraFixtures.source
        for (t in Texture.entries) assertNotNull("texture $t", source.texture(t))
        assertRenders(NiagaraFixtures.parseGlyph("cat_home"), source, "cat_home")
        assertRenders(NiagaraFixtures.parseGlyph("act_flash_small_and_narrow"), source, "flash")
    }

    @Test
    fun `all icon shapes render and differ at the corners`() {
        val g = TestGlyphs.house()
        val src = TestGlyphs.syntheticSource()
        val corners = IconShape.entries.map { shape ->
            val b = render(g, AnyconSettings(Material(), shape, launcherShadow = false), src)!!
            pixels(b)[2 * 96 + 8] ushr 24
        }
        // Rounded square reaches further into the corner than the squircle, which reaches further than the circle.
        assertEquals(3, corners.size)
        assertTrue("$corners", corners[0] <= corners[1] && corners[1] <= corners[2])
    }

    @Test
    fun `missing glyph or texture gives null`() {
        val noTextures = object : GlyphSource {
            override fun glyph(name: String): Glyph? = null
            override fun texture(texture: Texture): Bitmap? = null
        }
        val g = TestGlyphs.house()
        assertNull(render(null, AnyconSettings(Material()), noTextures))
        assertNull(render(g, AnyconSettings(Sticker(Sticker.Palette.CORAL)), noTextures))
        assertNull(render(g, AnyconSettings(AnyconStyle.Watercolor()), noTextures))
        assertNull(render(g, AnyconSettings(Marble()), noTextures))
        // Packs without textures still work; Dots needs no glyph.
        assertNotNull(render(g, AnyconSettings(Sticker(Sticker.Palette.THEME_COLORS)), noTextures))
        assertNotNull(render(g, AnyconSettings(AnyconStyle.Stellar), noTextures))
        assertNotNull(render(null, AnyconSettings(Dots()), noTextures))
    }

    @Test
    fun `same seed gives identical pixels, different seed differs for textured packs`() {
        val g = TestGlyphs.house()
        val src = TestGlyphs.syntheticSource()
        for (style in listOf(AnyconStyle.Watercolor(), Marble(), AnyconStyle.Stellar)) {
            val s = AnyconSettings(style)
            val a = pixels(render(g, s, src, seed = 1)!!)
            assertArrayEquals(a, pixels(render(g, s, src, seed = 1)!!))
            assertFalse("$style should vary with seed", a.contentEquals(pixels(render(g, s, src, seed = 2)!!)))
        }
    }

    @Test
    fun `stellar has stars`() {
        val b = render(TestGlyphs.house(), AnyconSettings(AnyconStyle.Stellar, launcherShadow = false), TestGlyphs.syntheticSource(), 192)!!
        assertTrue(distinctColours(b) > 20)
    }

    @Test
    fun `renderer caches and falls back to null`() {
        val renderer = AnyconRenderer(TestGlyphs.syntheticSource())
        val s = AnyconSettings(Material())
        val a = renderer.render("test_house", s, 64, "home")
        assertNotNull(a)
        assertSame(a, renderer.render("test_house", s, 64, "home"))
        assertNull(renderer.render("does_not_exist", s, 64, "home"))
        assertNotNull(renderer.render("does_not_exist", AnyconSettings(Dots()), 64, "home"))
        assertNull(renderer.render("test_house", s, 0, "home"))
        renderer.clearCache()
        assertFalse(a === renderer.render("test_house", s, 64, "home"))
    }

    companion object {
        val ALL_VARIANTS: List<AnyconStyle> = buildList {
            for (shade in Material.Shade.entries) for (t in Thickness.entries) add(Material(shade, t))
            for (p in Sticker.Palette.entries) add(Sticker(p))
            for (shape in Dots.Shape.entries) for (p in AppOrThemePalette.entries) add(Dots(shape, p))
            for (f in Outline.FillType.entries) for (t in Thickness.entries) for (m in Outline.TextMode.entries) add(Outline(f, t, m))
            for (t in Thickness.entries) for (p in AppOrThemePalette.entries) add(AnyconStyle.OutlineChroma(t, p))
            for (p in AppOrThemePalette.entries) add(AnyconStyle.Watercolor(p))
            for (p in Marble.Palette.entries) add(Marble(p))
            for (p in AppOrThemePalette.entries) add(AnyconStyle.Bright(p))
            add(AnyconStyle.Stellar)
        }
    }
}
