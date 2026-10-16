package io.github.dreadboy.navigationshortcuts.anycons.render

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import kotlin.math.max
import kotlin.math.pow

/**
 * Stellar's procedural starfield: a T×T (T = 4.5·N) white texture whose alpha comes from two OpenSimplex2 octaves,
 * a fine one (60 cycles across T, seed 0) that places the stars and a coarse one (3.9 cycles, seed 1) that dims
 * regions. The row index is the first noise coordinate.
 *
 * Cached per icon size N (Niagara reuses a larger texture for smaller icons, which makes the look depend on render
 * history; keying by N gives the same look at every size).
 */
internal object StarTexture {
    private const val MAX_CACHED = 2
    private val cache = LinkedHashMap<Int, Bitmap>(4, 0.75f, true)

    fun forSize(n: Int): Bitmap = synchronized(cache) {
        cache[n]?.takeUnless { it.isRecycled }?.let { return it }
        val bitmap = generate(n)
        cache[n] = bitmap
        while (cache.size > MAX_CACHED) {
            val eldest = cache.keys.first()
            cache.remove(eldest)
        }
        bitmap
    }

    fun generate(n: Int): Bitmap {
        val t = (n * 3 * 1.5f).toInt().coerceAtLeast(3)
        val step = 1.0 / t
        val fine = step * 60.0
        val coarse = step * 3.9
        val pixels = IntArray(t * t)
        var i = 0
        for (row in 0 until t) {
            for (col in 0 until t) {
                val n1 = OpenSimplex2.noise2(0L, row * fine, col * fine)
                val n2 = OpenSimplex2.noise2(1L, row * coarse, col * coarse)
                val alpha = if (n1 > 0.8f && n2 > 0f) {
                    ((n1 - 0.8f) * 4f).toDouble().pow(0.2).toFloat() * n2.toDouble().pow(0.5).toFloat()
                } else {
                    0f
                }
                val a = (max(alpha, 0f) * 255f).toInt().coerceAtMost(255)
                pixels[i++] = (a shl 24) or 0xFFFFFF
            }
        }
        val bitmap = createBitmap(t, t)
        bitmap.setPixels(pixels, 0, t, 0, 0, t, t)
        return bitmap
    }
}
