package io.github.dreadboy.navigationshortcuts.anycons

import io.github.dreadboy.navigationshortcuts.anycons.color.Oklch
import io.github.dreadboy.navigationshortcuts.anycons.color.Oklch.Companion.ok
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OklchTest {
    private fun assertOklch(argb: Int, l: Double, c: Double, h: Double?) {
        val o = Oklch.fromArgb(argb)
        assertEquals("L of ${Integer.toHexString(argb)}", l, o.l, 1e-3)
        assertEquals("C of ${Integer.toHexString(argb)}", c, o.c, 1e-3)
        if (h != null) assertEquals("H of ${Integer.toHexString(argb)}", h, o.h, 0.05)
    }

    @Test
    fun `reference values from Ottosson's OKLab`() {
        assertOklch(0xFFFFFFFF.toInt(), 1.0, 0.0, null)
        assertOklch(0xFF000000.toInt(), 0.0, 0.0, null)
        assertOklch(0xFFFF0000.toInt(), 0.62796, 0.25768, 29.234)
        assertOklch(0xFF00FF00.toInt(), 0.86644, 0.29483, 142.495)
        assertOklch(0xFF0000FF.toInt(), 0.45201, 0.31321, 264.052)
    }

    @Test
    fun `hue from atan2 is normalised to 0 until 360`() {
        // Blue-ish purple has a negative atan2 hue before normalisation.
        val o = Oklch.fromArgb(0xFFFF00FF.toInt())
        assertTrue(o.h in 0.0..360.0)
        assertEquals(328.36, o.h, 0.05)
    }

    @Test
    fun `sRGB round trips exactly`() {
        val samples = listOf(0xFF109DFF, 0xFFFFFFFF, 0xFF000000, 0xFF757575, 0xFF482658, 0xFFFF8800, 0xFF00FF00, 0xFF123456)
        for (s in samples) {
            val argb = s.toInt()
            assertEquals(Integer.toHexString(argb), argb, Oklch.fromArgb(argb).toArgb())
        }
    }

    @Test
    fun `gamut mapping keeps lightness and hue and reduces chroma`() {
        // L=1 with any chroma maps to white; very dark maps near black.
        assertEquals(0xFFFFFFFF.toInt(), ok(1.0, 0.17, 0.0))
        assertEquals(0xFFFFFFFF.toInt(), ok(1.0, 0.3, 200.0))
        val nearBlack = ok(0.05, 0.17, 0.0)
        assertTrue(channelMax(nearBlack) < 20)

        // Out-of-gamut vivid green: result is in gamut and keeps L and H closely.
        val mapped = ok(0.6, 0.37, 140.0)
        val back = Oklch.fromArgb(mapped)
        assertEquals(0.6, back.l, 0.01)
        assertEquals(140.0, back.h, 1.0)
        assertTrue(back.c < 0.37)

        // In-gamut colours are not altered by mapping.
        assertEquals(Oklch.fromArgb(0xFF109DFF.toInt()).toArgb(), 0xFF109DFF.toInt())
    }

    @Test
    fun `inputs are clamped and hues normalised before conversion`() {
        assertEquals(ok(0.5, 0.1, 330.0), ok(0.5, 0.1, -30.0))
        assertEquals(ok(0.5, 0.1, 10.0), ok(0.5, 0.1, 370.0))
        assertEquals(ok(1.0, 0.0, 0.0), ok(1.5, 0.0, 0.0))
        assertEquals(ok(0.0, 0.0, 0.0), ok(-0.2, 0.0, 0.0))
        assertEquals(ok(0.7, 0.37, 30.0), ok(0.7, 0.9, 30.0))
    }

    @Test
    fun `achromatic rule depends on lightness`() {
        assertTrue(Oklch(0.1, 0.039, 0.0).isAchromatic)
        assertFalse(Oklch(0.1, 0.041, 0.0).isAchromatic)
        assertTrue(Oklch(0.5, 0.027, 0.0).isAchromatic)
        assertFalse(Oklch(0.5, 0.029, 0.0).isAchromatic)
        assertTrue(Oklch.fromArgb(0xFF757575.toInt()).isAchromatic)
        assertFalse(Oklch.fromArgb(0xFF109DFF.toInt()).isAchromatic)
    }

    private fun channelMax(argb: Int) = maxOf((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF)
}
