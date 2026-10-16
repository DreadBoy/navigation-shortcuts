package io.github.dreadboy.navigationshortcuts.anycons

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.createBitmap
import io.github.dreadboy.navigationshortcuts.anycons.glyph.Glyph
import io.github.dreadboy.navigationshortcuts.anycons.glyph.GlyphParser
import io.github.dreadboy.navigationshortcuts.anycons.source.GlyphSource
import io.github.dreadboy.navigationshortcuts.anycons.source.Texture

/** Self-made glyph and textures so renderer tests also run without Niagara (CI). Not Niagara art. */
object TestGlyphs {
    /** A simple house-like shape in the Anycon structure: silhouette, detail fill, outline. */
    const val HOUSE_XML = """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24.0dp" android:height="24.0dp" android:viewportWidth="24.0" android:viewportHeight="24.0">
    <group android:pivotX="12.0" android:pivotY="12.0" android:scaleX="?AnyconsScale" android:scaleY="?AnyconsScale">
        <path android:fillColor="?AnyconsBgFillColor" android:pathData="M4,11L12,4L20,11L20,20L4,20Z" />
        <path android:fillColor="?AnyconsFgFillColor" android:pathData="M10,14L14,14L14,20L10,20Z" />
        <path android:pathData="M4,11L12,4L20,11L20,20L4,20Z M10,20L10,14L14,14L14,20"
            android:strokeColor="?AnyconsStrokeColor" android:strokeWidth="?AnyconsStrokeWidth"
            android:strokeLineCap="round" android:strokeLineJoin="round" />
    </group>
</vector>"""

    fun house(): Glyph = GlyphParser().parse("test_house", NiagaraFixtures.xmlParser(HOUSE_XML))

    /** Opaque grey gradient (stands in for overlay textures). */
    fun greyTexture(w: Int, h: Int): Bitmap {
        val b = createBitmap(w, h)
        val px = IntArray(w * h) { i ->
            val v = ((i % w) * 255 / w + (i / w) * 255 / h) / 2
            Color.rgb(v, v, v)
        }
        b.setPixels(px, 0, w, 0, 0, w, h)
        return b
    }

    /** Radial alpha blob tiles (stands in for the watercolor transparency atlas). */
    fun alphaTexture(w: Int, h: Int, cols: Int, rows: Int): Bitmap {
        val b = createBitmap(w, h)
        val tw = w / cols
        val th = h / rows
        val px = IntArray(w * h) { i ->
            val x = (i % w) % tw - tw / 2f
            val y = (i / w) % th - th / 2f
            val d = kotlin.math.sqrt(x * x + y * y) / (tw / 2f)
            val a = ((1f - d) * 255f).toInt().coerceIn(0, 255)
            Color.argb(a, 0, 0, 0)
        }
        b.setPixels(px, 0, w, 0, 0, w, h)
        return b
    }

    fun syntheticSource(): GlyphSource = object : GlyphSource {
        private val textures = HashMap<Texture, Bitmap>()
        override fun glyph(name: String): Glyph? = if (name == "test_house") house() else null
        override fun texture(texture: Texture): Bitmap = textures.getOrPut(texture) {
            when (texture) {
                Texture.CORAL_GRADIENT, Texture.OPAL_GRADIENT -> greyTexture(256, 256)
                Texture.MARBLE_OVERLAY -> greyTexture(384, 384)
                Texture.WATERCOLOR_OVERLAY -> greyTexture(256, 128)
                Texture.WATERCOLOR_TRANSPARENCY -> alphaTexture(384, 192, 4, 2)
            }
        }
    }
}
