package io.github.dreadboy.navigationshortcuts.anycons.render

/**
 * 2D OpenSimplex2 noise, "fast" variant (`noise2`).
 *
 * Kotlin port of the OpenSimplex2 reference implementation by K.jpg (KdotJPG,
 * https://github.com/KdotJPG/OpenSimplex2), which its author dedicated to the public domain (CC0 1.0).
 * Only the 2D standard-orientation function is ported.
 */
internal object OpenSimplex2 {
    private const val PRIME_X = 0x5205402B9270C86FL
    private const val PRIME_Y = 0x598CD327003817B5L
    private const val HASH_MULTIPLIER = 0x53A3F72DEEC546F5L

    private const val SKEW_2D = 0.366025403784439
    private const val UNSKEW_2D = -0.21132486540518713

    private const val N_GRADS_2D_EXPONENT = 7
    private const val N_GRADS_2D = 1 shl N_GRADS_2D_EXPONENT
    private const val NORMALIZER_2D = 0.01001634121365712
    private const val RSQUARED_2D = 0.5f

    private val GRADIENTS_2D: FloatArray = run {
        val base = doubleArrayOf(
            0.38268343236509, 0.923879532511287,
            0.923879532511287, 0.38268343236509,
            0.923879532511287, -0.38268343236509,
            0.38268343236509, -0.923879532511287,
            -0.38268343236509, -0.923879532511287,
            -0.923879532511287, -0.38268343236509,
            -0.923879532511287, 0.38268343236509,
            -0.38268343236509, 0.923879532511287,
            0.130526192220052, 0.99144486137381,
            0.608761429008721, 0.793353340291235,
            0.793353340291235, 0.608761429008721,
            0.99144486137381, 0.130526192220051,
            0.99144486137381, -0.130526192220051,
            0.793353340291235, -0.60876142900872,
            0.608761429008721, -0.793353340291235,
            0.130526192220052, -0.99144486137381,
            -0.130526192220052, -0.99144486137381,
            -0.608761429008721, -0.793353340291235,
            -0.793353340291235, -0.608761429008721,
            -0.99144486137381, -0.130526192220052,
            -0.99144486137381, 0.130526192220051,
            -0.793353340291235, 0.608761429008721,
            -0.608761429008721, 0.793353340291235,
            -0.130526192220052, 0.99144486137381,
        )
        // The reference normalises in float precision: (float)((float)g / NORMALIZER).
        val normalised = FloatArray(base.size) { (base[it].toFloat() / NORMALIZER_2D).toFloat() }
        FloatArray(N_GRADS_2D * 2) { normalised[it % normalised.size] }
    }

    /** Noise in roughly [-1, 1] at ([x], [y]) for [seed]. */
    fun noise2(seed: Long, x: Double, y: Double): Float {
        val s = SKEW_2D * (x + y)
        return noise2UnskewedBase(seed, x + s, y + s)
    }

    private fun noise2UnskewedBase(seed: Long, xs: Double, ys: Double): Float {
        val xsb = fastFloor(xs)
        val ysb = fastFloor(ys)
        val xi = (xs - xsb).toFloat()
        val yi = (ys - ysb).toFloat()

        val xsbp = xsb * PRIME_X
        val ysbp = ysb * PRIME_Y

        val t = (xi + yi) * UNSKEW_2D.toFloat()
        val dx0 = xi + t
        val dy0 = yi + t

        var value = 0f
        val a0 = RSQUARED_2D - dx0 * dx0 - dy0 * dy0
        if (a0 > 0) {
            value = (a0 * a0) * (a0 * a0) * grad(seed, xsbp, ysbp, dx0, dy0)
        }

        val a1 = (2 * (1 + 2 * UNSKEW_2D) * (1 / UNSKEW_2D + 2)).toFloat() * t +
            ((-2 * (1 + 2 * UNSKEW_2D) * (1 + 2 * UNSKEW_2D)).toFloat() + a0)
        if (a1 > 0) {
            val dx1 = dx0 - (1 + 2 * UNSKEW_2D).toFloat()
            val dy1 = dy0 - (1 + 2 * UNSKEW_2D).toFloat()
            value += (a1 * a1) * (a1 * a1) * grad(seed, xsbp + PRIME_X, ysbp + PRIME_Y, dx1, dy1)
        }

        if (dy0 > dx0) {
            val dx2 = dx0 - UNSKEW_2D.toFloat()
            val dy2 = dy0 - (UNSKEW_2D + 1).toFloat()
            val a2 = RSQUARED_2D - dx2 * dx2 - dy2 * dy2
            if (a2 > 0) {
                value += (a2 * a2) * (a2 * a2) * grad(seed, xsbp, ysbp + PRIME_Y, dx2, dy2)
            }
        } else {
            val dx2 = dx0 - (UNSKEW_2D + 1).toFloat()
            val dy2 = dy0 - UNSKEW_2D.toFloat()
            val a2 = RSQUARED_2D - dx2 * dx2 - dy2 * dy2
            if (a2 > 0) {
                value += (a2 * a2) * (a2 * a2) * grad(seed, xsbp + PRIME_X, ysbp, dx2, dy2)
            }
        }
        return value
    }

    private fun grad(seed: Long, xsvp: Long, ysvp: Long, dx: Float, dy: Float): Float {
        var hash = seed xor xsvp xor ysvp
        hash *= HASH_MULTIPLIER
        hash = hash xor (hash shr (64 - N_GRADS_2D_EXPONENT + 1))
        val gi = hash.toInt() and ((N_GRADS_2D - 1) shl 1)
        return GRADIENTS_2D[gi] * dx + GRADIENTS_2D[gi or 1] * dy
    }

    private fun fastFloor(x: Double): Int {
        val xi = x.toInt()
        return if (x < xi) xi - 1 else xi
    }
}
