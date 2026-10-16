package io.github.dreadboy.navigationshortcuts.anycons

import io.github.dreadboy.navigationshortcuts.anycons.render.OpenSimplex2
import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconSettings
import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconStyle
import io.github.dreadboy.navigationshortcuts.anycons.settings.AppOrThemePalette
import io.github.dreadboy.navigationshortcuts.anycons.settings.IconShape
import io.github.dreadboy.navigationshortcuts.anycons.settings.Thickness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsAndNoiseTest {

    private val allStyles: List<AnyconStyle> = listOf(
        AnyconStyle.Material(AnyconStyle.Material.Shade.LIGHT, Thickness.THIN),
        AnyconStyle.Material(AnyconStyle.Material.Shade.DARK, Thickness.BOLD),
        AnyconStyle.Sticker(AnyconStyle.Sticker.Palette.CORAL),
        AnyconStyle.Dots(AnyconStyle.Dots.Shape.DIAMOND, AppOrThemePalette.APP_COLORS),
        AnyconStyle.Outline(AnyconStyle.Outline.FillType.FILL, Thickness.THIN, AnyconStyle.Outline.TextMode.DARK_TEXT),
        AnyconStyle.OutlineChroma(Thickness.THIN, AppOrThemePalette.APP_COLORS),
        AnyconStyle.Watercolor(AppOrThemePalette.APP_COLORS),
        AnyconStyle.Marble(AnyconStyle.Marble.Palette.BLACK),
        AnyconStyle.Bright(AppOrThemePalette.APP_COLORS),
        AnyconStyle.Stellar,
    )

    @Test
    fun `every style round-trips through JSON`() {
        for (style in allStyles) {
            val s = AnyconSettings(style, IconShape.ROUNDED_SQUARE, 0xFF123456.toInt(), launcherShadow = false)
            assertEquals(s, AnyconSettings.fromJson(s.toJson()))
        }
    }

    @Test
    fun `serial names are stable`() {
        val json = AnyconSettings(AnyconStyle.Material(AnyconStyle.Material.Shade.VIVID, Thickness.BOLD)).toJson()
        assertTrue(json, json.contains("\"type\":\"material\""))
        assertTrue(json, json.contains("\"shade\":\"vibrant\""))
        assertTrue(json, json.contains("\"thickness\":\"thick\""))
        assertTrue(json, json.contains("\"shape\":\"squircle\""))
        val chroma = AnyconSettings(AnyconStyle.OutlineChroma(palette = AppOrThemePalette.APP_COLORS)).toJson()
        assertTrue(chroma, chroma.contains("\"type\":\"outline_chroma\""))
        assertTrue(chroma, chroma.contains("\"palette\":\"app_colors\""))
        val stellar = AnyconSettings(AnyconStyle.Stellar).toJson()
        assertTrue(stellar, stellar.contains("\"type\":\"stellar\""))
        // Unknown keys from future versions are ignored.
        val parsed = AnyconSettings.fromJson(
            """{"style":{"type":"bright","palette":"theme_colors","extra":1},"shape":"circle","seedColor":-1,"x":true}""",
        )
        assertEquals(AnyconStyle.Bright(AppOrThemePalette.THEME_COLORS), parsed.style)
        assertEquals(IconShape.CIRCLE, parsed.shape)
    }

    @Test
    fun `defaults and launcher shadow table`() {
        val d = AnyconSettings()
        assertEquals(AnyconStyle.Material(AnyconStyle.Material.Shade.VIVID, Thickness.BOLD), d.style)
        assertEquals(IconShape.SQUIRCLE, d.shape)
        assertTrue(d.effectiveLauncherShadow)
        assertFalse(AnyconSettings(AnyconStyle.Watercolor()).effectiveLauncherShadow)
        assertTrue(AnyconSettings(AnyconStyle.Outline()).effectiveLauncherShadow) // light text
        assertFalse(
            AnyconSettings(AnyconStyle.Outline(textMode = AnyconStyle.Outline.TextMode.DARK_TEXT)).effectiveLauncherShadow,
        )
        assertFalse(AnyconSettings(AnyconStyle.Stellar, launcherShadow = false).effectiveLauncherShadow)
        assertTrue(AnyconSettings(AnyconStyle.Watercolor(), launcherShadow = true).effectiveLauncherShadow)
        assertFalse(AnyconStyle.Dots().usesGlyph)
    }

    @Test
    fun `stable seed is String hashCode`() {
        assertEquals("home".hashCode().toLong(), AnyconRenderer.stableSeed("home"))
        assertNotEquals(AnyconRenderer.stableSeed("home"), AnyconRenderer.stableSeed("work"))
    }

    @Test
    fun `opensimplex2 noise is deterministic, bounded and seed dependent`() {
        var min = Float.MAX_VALUE
        var max = -Float.MAX_VALUE
        var differs = 0
        for (i in 0 until 2000) {
            val x = i * 0.137
            val y = i * 0.071 - 3.0
            val v = OpenSimplex2.noise2(0L, x, y)
            assertEquals(v, OpenSimplex2.noise2(0L, x, y), 0f)
            if (v != OpenSimplex2.noise2(1L, x, y)) differs++
            min = minOf(min, v)
            max = maxOf(max, v)
        }
        assertTrue("min $min", min >= -1.01f && min < -0.5f)
        assertTrue("max $max", max <= 1.01f && max > 0.5f)
        assertTrue(differs > 1900)
        // Lattice points of the skewed grid are zero-valued in simplex noise.
        assertEquals(0f, OpenSimplex2.noise2(0L, 0.0, 0.0), 1e-6f)
    }
}
