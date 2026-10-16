package io.github.dreadboy.navigationshortcuts.anycons

import io.github.dreadboy.navigationshortcuts.anycons.color.Oklch
import io.github.dreadboy.navigationshortcuts.anycons.color.Oklch.Companion.ok
import io.github.dreadboy.navigationshortcuts.anycons.color.Palettes
import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconStyle.Marble
import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconStyle.Material.Shade
import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconStyle.Outline.TextMode
import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconStyle.Sticker
import io.github.dreadboy.navigationshortcuts.anycons.settings.AppOrThemePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PalettesTest {
    private val blue = 0xFF109DFF.toInt()
    private val grey = 0xFF808080.toInt()
    private val yellow = 0xFFFFD000.toInt()
    private val white = 0xFFFFFFFF.toInt()

    @Test
    fun `material shades follow the formulas`() {
        val h = Oklch.fromArgb(blue).h
        val vivid = Palettes.material(Shade.VIVID, blue)
        assertEquals(ok(0.35, 0.15, h), vivid.stroke)
        assertEquals(ok(0.95, 0.05, h), vivid.fill)
        assertEquals(ok(0.83, 0.085, h), vivid.background)

        val light = Palettes.material(Shade.LIGHT, blue)
        assertEquals(white, light.fill)
        assertEquals(ok(0.95, 0.05, h), light.background)

        val dark = Palettes.material(Shade.DARK, blue)
        assertEquals(dark.fill, dark.background) // no fill layer
        assertEquals(ok(0.83, 0.08, h), dark.stroke)
    }

    @Test
    fun `yellow seeds use the lighter vivid background`() {
        val o = Oklch.fromArgb(yellow)
        assertTrue(o.h in 60.0..120.0)
        assertEquals(ok(0.88, 0.10, o.h), Palettes.material(Shade.VIVID, yellow).background)
        assertEquals(ok(0.88, 0.10, o.h), Palettes.sticker(Sticker.Palette.THEME_COLORS, yellow, null).fill)
    }

    @Test
    fun `achromatic seeds keep their own chroma`() {
        val o = Oklch.fromArgb(grey)
        val vivid = Palettes.material(Shade.VIVID, grey)
        assertEquals(ok(0.35, o.c, o.h), vivid.stroke)
        val s = vivid.stroke
        val spread = maxOf((s shr 16) and 0xFF, (s shr 8) and 0xFF, s and 0xFF) -
            minOf((s shr 16) and 0xFF, (s shr 8) and 0xFF, s and 0xFF)
        assertTrue("grey seed should give a grey stroke", spread <= 2)
    }

    @Test
    fun `app colour palettes fall back to niagara river`() {
        assertEquals(Palettes.sticker(Sticker.Palette.APP_COLORS, grey, blue), Palettes.sticker(Sticker.Palette.APP_COLORS, grey, null))
        assertEquals(white, Palettes.sticker(Sticker.Palette.APP_COLORS, grey, null).background)
        assertEquals(Palettes.bright(AppOrThemePalette.APP_COLORS, grey, blue), Palettes.bright(AppOrThemePalette.APP_COLORS, grey, null))
        assertEquals(
            Palettes.watercolor(AppOrThemePalette.APP_COLORS, grey, blue),
            Palettes.watercolor(AppOrThemePalette.APP_COLORS, grey, null),
        )
        assertEquals(
            Palettes.outlineChroma(AppOrThemePalette.APP_COLORS, grey, blue),
            Palettes.outlineChroma(AppOrThemePalette.APP_COLORS, grey, null),
        )
        // A destination colour overrides the fallback.
        assertNotEquals(
            Palettes.sticker(Sticker.Palette.APP_COLORS, grey, 0xFFFF8800.toInt()),
            Palettes.sticker(Sticker.Palette.APP_COLORS, grey, null),
        )
    }

    @Test
    fun `dots colours`() {
        // Fallback grey_600 lightened by 0x54 white: (84*255 + 117*171) / 255 = 162.
        assertEquals(0xFFA2A2A2.toInt(), Palettes.dots(AppOrThemePalette.APP_COLORS, blue, null))
        assertEquals(blue, Palettes.dots(AppOrThemePalette.THEME_COLORS, blue, null))
    }

    @Test
    fun `sticker texture palettes`() {
        assertEquals(0xFF482658.toInt(), Palettes.sticker(Sticker.Palette.CORAL, blue, null).stroke)
        assertEquals(0xFF000000.toInt(), Palettes.sticker(Sticker.Palette.OPAL, blue, null).stroke)
        assertEquals(white, Palettes.sticker(Sticker.Palette.OPAL, blue, null).background)
    }

    @Test
    fun `outline fill alpha depends on text mode`() {
        val light = Palettes.outline(TextMode.LIGHT_TEXT, blue)
        val dark = Palettes.outline(TextMode.DARK_TEXT, blue)
        assertEquals(white, light.stroke)
        assertEquals(0x33, light.fill ushr 24)
        assertEquals(0xFF1A1C1F.toInt(), dark.stroke)
        assertEquals(0x0C, dark.fill ushr 24)
    }

    @Test
    fun `outline chroma is a light pastel`() {
        val o = Oklch.fromArgb(Palettes.outlineChroma(AppOrThemePalette.THEME_COLORS, blue, null))
        assertTrue(o.l >= 0.86)
        assertTrue(o.c <= 0.161)
    }

    @Test
    fun `watercolor`() {
        val p = Palettes.watercolor(AppOrThemePalette.THEME_COLORS, blue, null)
        assertEquals(white, p.stroke)
        val bg = Oklch.fromArgb(p.background)
        val seed = Oklch.fromArgb(blue)
        assertEquals(minOf(0.8 * seed.l + 0.2, 0.9), bg.l, 0.01)
        assertTrue(Oklch.fromArgb(p.fill).l <= 0.31)
    }

    @Test
    fun `watercolor nudges olive fills`() {
        // A seed with hue ~100 and decent chroma: fill hue is pushed to 80 or 111.5.
        val olive = ok(0.6, 0.12, 100.0)
        val fill = Oklch.fromArgb(Palettes.watercolor(AppOrThemePalette.THEME_COLORS, olive, null).fill)
        assertTrue("fill hue ${fill.h}", kotlin.math.abs(fill.h - 111.5) < 3.0 || kotlin.math.abs(fill.h - 80.0) < 3.0)
    }

    @Test
    fun `marble palettes`() {
        assertEquals(white, Palettes.marble(Marble.Palette.WHITE, blue).fill)
        assertEquals(0xFF121212.toInt(), Palettes.marble(Marble.Palette.WHITE, blue).stroke)
        val black = Palettes.marble(Marble.Palette.BLACK, blue)
        assertEquals(white, black.stroke)
        assertTrue(((black.fill shr 16) and 0xFF) < 30)
        val theme = Palettes.marble(Marble.Palette.THEME_COLORS, blue)
        assertEquals(ok(0.6, 0.17, Oklch.fromArgb(blue).h), theme.fill)
        assertEquals(theme.fill, theme.background)
    }

    @Test
    fun `bright and stellar`() {
        val o = Oklch.fromArgb(blue)
        val bright = Palettes.bright(AppOrThemePalette.THEME_COLORS, blue, null)
        assertEquals(ok(0.2, 0.06, o.h), bright.stroke)
        assertEquals(ok(maxOf(o.l, 0.65), (0.95 * o.c).coerceIn(0.09, 0.17), o.h), bright.background)
        val stellar = Palettes.stellar(blue)
        assertEquals(ok(0.2, 0.04, o.h), stellar.background)
        assertEquals(ok(0.9, 0.06, o.h), stellar.stroke)
    }

    @Test
    fun `composite over matches AndroidX ColorUtils maths`() {
        assertEquals(0xFFFFFFFF.toInt(), Palettes.compositeOver(0xFFFFFFFF.toInt(), 0xFF000000.toInt()))
        assertEquals(0xFF000000.toInt(), Palettes.compositeOver(0x00FFFFFF, 0xFF000000.toInt()))
        assertEquals(0xFF808080.toInt(), Palettes.compositeOver(0x80FFFFFF.toInt(), 0xFF000000.toInt()))
    }
}
