package io.github.dreadboy.navigationshortcuts.anycons

import android.graphics.Bitmap
import android.util.LruCache
import io.github.dreadboy.navigationshortcuts.anycons.render.AnyconComposer
import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconSettings
import io.github.dreadboy.navigationshortcuts.anycons.source.GlyphSource
import java.util.Random

/**
 * Entry point: renders a destination's Anycon from a [GlyphSource] (normally Niagara's installed resources) with
 * a small bitmap LRU cache.
 *
 * Per-destination randomness (texture tile, flip, rotation) comes from `java.util.Random(seedKey.hashCode())`, so the
 * same destination always gets the same look. `String.hashCode` is specified by the JLS and therefore stable across
 * runs and devices.
 *
 * [render] returns null whenever the icon cannot be drawn (Niagara or the glyph or a texture missing, any error);
 * the caller then shows its own fallback icon. Returned bitmaps may be shared with the cache: do not recycle or
 * mutate them. Rendering is CPU work (Stellar generates a noise texture), so call it off the main thread.
 */
class AnyconRenderer(
    private val source: GlyphSource,
    maxCacheBytes: Int = DEFAULT_CACHE_BYTES,
) {
    private val cache = object : LruCache<String, Bitmap>(maxCacheBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    /**
     * @param glyphName glyph name without the `ic_ip_` prefix, e.g. `cat_home`.
     * @param seedKey stable per-destination key (e.g. the destination id).
     * @param destinationColor stand-in for the app colour of APP_COLORS palettes; null uses Niagara's fallback.
     */
    fun render(
        glyphName: String,
        settings: AnyconSettings,
        sizePx: Int,
        seedKey: String,
        destinationColor: Int? = null,
    ): Bitmap? {
        if (sizePx <= 0) return null
        val key = cacheKey(glyphName, settings, sizePx, seedKey, destinationColor)
        cache.get(key)?.takeUnless { it.isRecycled }?.let { return it }
        val bitmap = try {
            val glyph = if (settings.style.usesGlyph) source.glyph(glyphName) ?: return null else null
            AnyconComposer.render(glyph, settings, source::texture, sizePx, Random(stableSeed(seedKey)), destinationColor)
        } catch (_: Exception) {
            null
        } catch (_: StackOverflowError) {
            null
        } catch (_: OutOfMemoryError) {
            null
        } ?: return null
        cache.put(key, bitmap)
        return bitmap
    }

    fun clearCache() = cache.evictAll()

    companion object {
        const val DEFAULT_CACHE_BYTES = 8 * 1024 * 1024

        /** Deterministic per-destination seed: `seedKey.hashCode()` widened to a long (as Niagara seeds per icon). */
        fun stableSeed(seedKey: String): Long = seedKey.hashCode().toLong()

        internal fun cacheKey(
            glyphName: String,
            settings: AnyconSettings,
            sizePx: Int,
            seedKey: String,
            destinationColor: Int?,
        ): String = buildString {
            append(if (settings.style.usesGlyph) glyphName else "-")
            append('|').append(settings.toJson())
            append('|').append(sizePx)
            append('|').append(seedKey)
            append('|').append(destinationColor?.let { Integer.toHexString(it) } ?: "-")
        }
    }
}
