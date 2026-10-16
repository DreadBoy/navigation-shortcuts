package io.github.dreadboy.navigationshortcuts.anycons

import android.graphics.Bitmap
import android.graphics.Rect

object BitmapAsserts {
    fun pixels(b: Bitmap): IntArray = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }

    fun opaqueCount(b: Bitmap, minAlpha: Int = 1): Int = pixels(b).count { (it ushr 24) >= minAlpha }

    /** Bounding box of pixels with alpha ≥ [minAlpha], or null if none. */
    fun alphaBounds(b: Bitmap, minAlpha: Int = 1): Rect? {
        val px = pixels(b)
        var l = Int.MAX_VALUE
        var t = Int.MAX_VALUE
        var r = -1
        var bt = -1
        for (y in 0 until b.height) for (x in 0 until b.width) {
            if ((px[y * b.width + x] ushr 24) >= minAlpha) {
                l = minOf(l, x); t = minOf(t, y); r = maxOf(r, x); bt = maxOf(bt, y)
            }
        }
        return if (r < 0) null else Rect(l, t, r + 1, bt + 1)
    }

    fun alphaHistogram(b: Bitmap): Map<Int, Int> = pixels(b).asList().groupingBy { it ushr 24 }.eachCount()

    fun distinctColours(b: Bitmap): Int = pixels(b).toSet().size
}
