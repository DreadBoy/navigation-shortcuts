package io.github.dreadboy.navigationshortcuts.anycons.color

import io.github.dreadboy.navigationshortcuts.anycons.color.Oklch.Companion.ok
import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconStyle
import io.github.dreadboy.navigationshortcuts.anycons.settings.AppOrThemePalette
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** A pack's colour triple (ARGB ints). */
data class AnyconPalette(val stroke: Int, val fill: Int, val background: Int)

/**
 * Per-pack palette formulas from a seed colour (or, for APP_COLORS palettes, a per-destination colour), as
 * documented for Niagara Anycons 1.16.33. Notation in comments: `L, C, H` = OKLCH of the source colour;
 * `ach ? C : x` = keep the source chroma when it is achromatic, else use x.
 */
object Palettes {
    /** Niagara's `niagara_river`, the APP_COLORS fallback. */
    const val APP_FALLBACK: Int = 0xFF109DFF.toInt()

    /** `grey_600`, the Dots APP_COLORS fallback. */
    const val DOTS_APP_FALLBACK: Int = 0xFF757575.toInt()

    const val WHITE: Int = 0xFFFFFFFF.toInt()
    const val BLACK: Int = 0xFF000000.toInt()
    const val STICKER_CORAL_STROKE: Int = 0xFF482658.toInt()
    const val MARBLE_WHITE_STROKE: Int = 0xFF121212.toInt()

    /** Outline stroke for dark text (Niagara `neutral1_0900`, azure family). */
    const val OUTLINE_DARK_TEXT_STROKE: Int = 0xFF1A1C1F.toInt()

    private fun Oklch.chromaOr(x: Double) = if (isAchromatic) c else x
    private val Oklch.isYellow get() = h in 60.0..120.0

    fun material(shade: AnyconStyle.Material.Shade, seed: Int): AnyconPalette {
        val o = Oklch.fromArgb(seed)
        return when (shade) {
            AnyconStyle.Material.Shade.LIGHT -> AnyconPalette(
                stroke = ok(0.40, o.chromaOr(0.10), o.h),
                fill = ok(1.00, o.chromaOr(0.00), o.h),
                background = ok(0.95, o.chromaOr(0.05), o.h),
            )
            AnyconStyle.Material.Shade.VIVID -> AnyconPalette(
                stroke = ok(0.35, o.chromaOr(0.15), o.h),
                fill = ok(0.95, o.chromaOr(0.05), o.h),
                background = if (o.isYellow) ok(0.88, o.chromaOr(0.10), o.h) else ok(0.83, o.chromaOr(0.085), o.h),
            )
            AnyconStyle.Material.Shade.DARK -> {
                val fill = ok(0.31, o.chromaOr(0.04), o.h)
                AnyconPalette(stroke = ok(0.83, o.chromaOr(0.08), o.h), fill = fill, background = fill)
            }
        }
    }

    /** Sticker palettes. For CORAL/OPAL the fill is a texture; [AnyconPalette.fill] is unused there. */
    fun sticker(palette: AnyconStyle.Sticker.Palette, seed: Int, appColor: Int?): AnyconPalette = when (palette) {
        AnyconStyle.Sticker.Palette.CORAL -> AnyconPalette(STICKER_CORAL_STROKE, WHITE, WHITE)
        AnyconStyle.Sticker.Palette.OPAL -> AnyconPalette(BLACK, WHITE, WHITE)
        AnyconStyle.Sticker.Palette.THEME_COLORS -> {
            val o = Oklch.fromArgb(seed)
            AnyconPalette(
                stroke = ok(0.35, o.chromaOr(0.15), o.h),
                fill = if (o.isYellow) ok(0.88, o.chromaOr(0.10), o.h) else ok(0.83, o.chromaOr(0.085), o.h),
                background = ok(1.0, o.chromaOr(0.0), o.h),
            )
        }
        AnyconStyle.Sticker.Palette.APP_COLORS -> {
            val o = Oklch.fromArgb(appColor ?: APP_FALLBACK)
            AnyconPalette(
                stroke = ok(0.30, min(o.c, 0.15), o.h),
                fill = ok(max(0.75, o.l.pow(0.85)), min(o.c, 0.15), o.h),
                background = WHITE,
            )
        }
    }

    fun dots(palette: AppOrThemePalette, seed: Int, appColor: Int?): Int = when (palette) {
        AppOrThemePalette.THEME_COLORS -> Oklch.fromArgb(seed).toArgb()
        AppOrThemePalette.APP_COLORS -> compositeOver(0x54FFFFFF, appColor ?: DOTS_APP_FALLBACK)
    }

    /** Outline: stroke is the wallpaper text colour; fill (FILL variant only) is a faint tint of the seed. */
    fun outline(textMode: AnyconStyle.Outline.TextMode, seed: Int): AnyconPalette {
        val o = Oklch.fromArgb(seed)
        val c = min(o.c, 0.05)
        return when (textMode) {
            AnyconStyle.Outline.TextMode.LIGHT_TEXT -> AnyconPalette(
                stroke = WHITE,
                fill = (ok(0.9, c, o.h) and 0xFFFFFF) or 0x33000000,
                background = 0,
            )
            AnyconStyle.Outline.TextMode.DARK_TEXT -> AnyconPalette(
                stroke = OUTLINE_DARK_TEXT_STROKE,
                fill = (ok(0.3, c, o.h) and 0xFFFFFF) or 0x0C000000,
                background = 0,
            )
        }
    }

    fun outlineChroma(palette: AppOrThemePalette, seed: Int, appColor: Int?): Int = when (palette) {
        AppOrThemePalette.THEME_COLORS -> {
            val o = Oklch.fromArgb(seed)
            val l0 = min(1.0, 0.7 * o.l + 0.3)
            val l = max(l0, 0.87)
            val c0 = 0.7 * o.c
            val chromatic = if (l0 >= 0.15) c0 >= 0.028 else c0 >= 0.04
            ok(l, if (chromatic) min(c0, 0.16) else o.c, o.h)
        }
        AppOrThemePalette.APP_COLORS -> {
            val o = Oklch.fromArgb(appColor ?: APP_FALLBACK)
            ok(o.l.coerceIn(0.82, 0.95), min(0.8 * o.c, 0.12), o.h)
        }
    }

    fun watercolor(palette: AppOrThemePalette, seed: Int, appColor: Int?): AnyconPalette {
        val o = Oklch.fromArgb(if (palette == AppOrThemePalette.APP_COLORS) appColor ?: APP_FALLBACK else seed)
        val bg = Oklch(min(0.8 * o.l + 0.2, 0.9), 0.8 * o.c, o.h)
        var fill = bg.copy(l = min(bg.l, 0.3))
        if (fill.h in 80.0..111.5 && fill.c >= 0.0592 && fill.l <= 0.65) {
            fill = fill.copy(h = if (o.h < 95.75) 80.0 else 111.5) // avoid muddy olive/yellow
        }
        return AnyconPalette(stroke = WHITE, fill = fill.toArgb(), background = bg.toArgb())
    }

    /** Marble: fill and background are the same colour. */
    fun marble(palette: AnyconStyle.Marble.Palette, seed: Int): AnyconPalette = when (palette) {
        AnyconStyle.Marble.Palette.WHITE -> ok(1.0, 0.17, 0.0).let { AnyconPalette(MARBLE_WHITE_STROKE, it, it) }
        AnyconStyle.Marble.Palette.BLACK -> ok(0.05, 0.17, 0.0).let { AnyconPalette(WHITE, it, it) }
        AnyconStyle.Marble.Palette.THEME_COLORS -> {
            val o = Oklch.fromArgb(seed)
            val fill = when {
                o.isAchromatic -> ok(0.6, o.c, o.h)
                o.isYellow -> ok(0.75, 0.17, o.h)
                else -> ok(0.6, 0.17, o.h)
            }
            AnyconPalette(WHITE, fill, fill)
        }
    }

    fun bright(palette: AppOrThemePalette, seed: Int, appColor: Int?): AnyconPalette {
        val o = Oklch.fromArgb(if (palette == AppOrThemePalette.APP_COLORS) appColor ?: APP_FALLBACK else seed)
        val stroke = ok(0.2, 0.06, o.h)
        val fill = ok(min(1.0, 0.9 + 0.1 * o.l), 0.1 * o.c, o.h)
        val background = when (palette) {
            AppOrThemePalette.THEME_COLORS ->
                ok(max(o.l, 0.65), if (o.isAchromatic) 0.0 else (0.95 * o.c).coerceIn(0.09, 0.17), o.h)
            AppOrThemePalette.APP_COLORS -> ok(max(0.6, o.l.pow(0.85)), min(0.8 * o.c, 0.17), o.h)
        }
        return AnyconPalette(stroke, fill, background)
    }

    fun stellar(seed: Int): AnyconPalette {
        val o = Oklch.fromArgb(seed)
        return AnyconPalette(
            stroke = ok(0.9, o.chromaOr(0.06), o.h),
            fill = ok(0.9, o.chromaOr(0.08), o.h),
            background = ok(0.2, o.chromaOr(0.04), o.h),
        )
    }

    /** Source-over composite of [foreground] on [background] (integer maths as in AndroidX `ColorUtils`). */
    fun compositeOver(foreground: Int, background: Int): Int {
        val fa = (foreground ushr 24) and 0xFF
        val ba = (background ushr 24) and 0xFF
        val a = 0xFF - ((0xFF - ba) * (0xFF - fa) / 0xFF)
        fun comp(shift: Int): Int {
            if (a == 0) return 0
            val fc = (foreground shr shift) and 0xFF
            val bc = (background shr shift) and 0xFF
            return (0xFF * fc * fa + bc * ba * (0xFF - fa)) / (a * 0xFF)
        }
        return (a shl 24) or (comp(16) shl 16) or (comp(8) shl 8) or comp(0)
    }
}
