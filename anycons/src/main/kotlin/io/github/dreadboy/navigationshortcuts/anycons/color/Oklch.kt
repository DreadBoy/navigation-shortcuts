package io.github.dreadboy.navigationshortcuts.anycons.color

import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A colour in OKLCH (Björn Ottosson's OKLab in polar form). [l] is 0..1, [c] is 0..0.37, [h] is degrees.
 *
 * [toArgb] maps out-of-gamut colours into sRGB by keeping L and H and bisecting chroma (step < 1e-4), then rounds
 * each channel ×255 and clamps it. Inputs are normalised first (hue to [0, 360), L to [0, 1], C to [0, 0.37]).
 */
data class Oklch(val l: Double, val c: Double, val h: Double) {

    /** Niagara's achromatic rule: darker colours tolerate a bit more chroma before counting as coloured. */
    val isAchromatic: Boolean get() = if (l < 0.15) c < 0.04 else c < 0.028

    fun toArgb(): Int {
        val lc = l.coerceIn(0.0, 1.0)
        val maxC = c.coerceIn(0.0, MAX_CHROMA)
        val hue = normalizeHue(h)
        val hRad = Math.toRadians(hue)
        val cosH = cos(hRad)
        val sinH = sin(hRad)

        linearRgbOrNull(lc, maxC, cosH, sinH)?.let { return pack(it) }

        // Bisect chroma: lo is always in gamut (C = 0 is grey, which always is), hi is out of gamut.
        var lo = 0.0
        var hi = maxC
        var best = linearRgbOrNull(lc, 0.0, cosH, sinH) ?: clampedLinear(lc, 0.0, cosH, sinH)
        var iterations = 0
        while (hi - lo >= CHROMA_EPSILON && iterations < 64) {
            val mid = (lo + hi) / 2.0
            val rgb = linearRgbOrNull(lc, mid, cosH, sinH)
            if (rgb != null) {
                lo = mid
                best = rgb
            } else {
                hi = mid
            }
            iterations++
        }
        return pack(best)
    }

    override fun toString(): String = "Oklch(l=%.4f, c=%.4f, h=%.2f)".format(l, c, h)

    companion object {
        const val MAX_CHROMA = 0.37
        private const val CHROMA_EPSILON = 1e-4

        /** Tolerance for "inside sRGB": white comes out at 1.0000000000000002 in double maths. */
        private const val GAMUT_EPSILON = 1e-9

        fun fromArgb(argb: Int): Oklch {
            val r = srgbToLinear(((argb shr 16) and 0xFF) / 255.0)
            val g = srgbToLinear(((argb shr 8) and 0xFF) / 255.0)
            val b = srgbToLinear((argb and 0xFF) / 255.0)

            val lms0 = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
            val lms1 = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
            val lms2 = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)

            val labL = 0.2104542553 * lms0 + 0.7936177850 * lms1 - 0.0040720468 * lms2
            val labA = 1.9779984951 * lms0 - 2.4285922050 * lms1 + 0.4505937099 * lms2
            val labB = 0.0259040371 * lms0 + 0.7827717662 * lms1 - 0.8086757660 * lms2

            val chroma = sqrt(labA * labA + labB * labB)
            val hue = normalizeHue(Math.toDegrees(atan2(labB, labA)))
            return Oklch(labL, chroma, hue)
        }

        fun normalizeHue(h: Double): Double {
            if (h.isNaN()) return 0.0
            val n = ((h % 360.0) + 360.0) % 360.0
            return if (n >= 360.0) 0.0 else n
        }

        /** Gamut-mapped OKLCH → opaque ARGB. */
        fun ok(l: Double, c: Double, h: Double): Int = Oklch(l, c, h).toArgb()

        private fun srgbToLinear(v: Double): Double =
            if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)

        private fun linearToSrgb(v: Double): Double =
            if (v <= 0.0031308) 12.92 * v else 1.055 * v.pow(1.0 / 2.4) - 0.055

        private fun linear(l: Double, c: Double, cosH: Double, sinH: Double): DoubleArray {
            val a = c * cosH
            val b = c * sinH
            val l1 = l + 0.3963377774 * a + 0.2158037573 * b
            val m1 = l - 0.1055613458 * a - 0.0638541728 * b
            val s1 = l - 0.0894841775 * a - 1.2914855480 * b
            val lc = l1 * l1 * l1
            val mc = m1 * m1 * m1
            val sc = s1 * s1 * s1
            return doubleArrayOf(
                4.0767416621 * lc - 3.3077115913 * mc + 0.2309699292 * sc,
                -1.2684380046 * lc + 2.6097574011 * mc - 0.3413193965 * sc,
                -0.0041960863 * lc - 0.7034186147 * mc + 1.7076147010 * sc,
            )
        }

        private fun linearRgbOrNull(l: Double, c: Double, cosH: Double, sinH: Double): DoubleArray? {
            val rgb = linear(l, c, cosH, sinH)
            return if (rgb.all { it >= -GAMUT_EPSILON && it <= 1.0 + GAMUT_EPSILON }) rgb else null
        }

        private fun clampedLinear(l: Double, c: Double, cosH: Double, sinH: Double): DoubleArray =
            linear(l, c, cosH, sinH).map { it.coerceIn(0.0, 1.0) }.toDoubleArray()

        private fun channel(v: Double): Int = (linearToSrgb(v.coerceIn(0.0, 1.0)) * 255.0).roundToInt().coerceIn(0, 255)

        private fun pack(rgb: DoubleArray): Int =
            (0xFF shl 24) or (channel(rgb[0]) shl 16) or (channel(rgb[1]) shl 8) or channel(rgb[2])
    }
}
