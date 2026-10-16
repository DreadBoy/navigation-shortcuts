package io.github.dreadboy.navigationshortcuts.anycons.source

import android.graphics.Bitmap
import io.github.dreadboy.navigationshortcuts.anycons.glyph.Glyph

/**
 * The five Niagara textures the packs use. Resource names carry a quality suffix that may change if Niagara
 * re-encodes them, so [knownName] is tried first and then [prefix] plus [FALLBACK_SUFFIXES].
 */
enum class Texture(val prefix: String, val knownName: String) {
    /** Sticker CORAL fill, 256×256. */
    CORAL_GRADIENT("anycons_tex_coral_gradient", "anycons_tex_coral_gradient_lossless"),

    /** Sticker OPAL fill, 256×256. */
    OPAL_GRADIENT("anycons_tex_opal_gradient", "anycons_tex_opal_gradient_lossless"),

    /** Marble greyscale overlay, 1536×1536 (4×4 tiles). */
    MARBLE_OVERLAY("anycons_tex_marble_overlay", "anycons_tex_marble_overlay_q92"),

    /** Watercolor greyscale overlay, 1024×512 (4×2 tiles). */
    WATERCOLOR_OVERLAY("anycons_tex_watercolor_overlay", "anycons_tex_watercolor_overlay_q95"),

    /** Watercolor alpha blobs, 1536×768 (4×2 tiles). */
    WATERCOLOR_TRANSPARENCY("anycons_tex_watercolor_transparency", "anycons_tex_watercolor_transparency_q100"),
    ;

    /** Resource entry names to try, most likely first. */
    val candidateNames: List<String>
        get() = (listOf(knownName) + FALLBACK_SUFFIXES.map { prefix + it }).distinct()

    companion object {
        val FALLBACK_SUFFIXES = listOf(
            "_lossless", "_q100", "_q95", "_q92", "_q90", "_q85", "_q80", "_q75", "_q70", "_q60", "_q50", "",
        )
    }
}

/** Where glyphs and textures come from. Implementations return null on any failure and never throw. */
interface GlyphSource {
    /** The glyph `ic_ip_<name>` (e.g. `cat_home`), or null if unavailable. */
    fun glyph(name: String): Glyph?

    /** The decoded texture, or null if unavailable. */
    fun texture(texture: Texture): Bitmap?
}
