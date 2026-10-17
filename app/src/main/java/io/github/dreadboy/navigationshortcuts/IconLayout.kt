package io.github.dreadboy.navigationshortcuts

import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconSettings
import kotlin.math.roundToInt

/**
 * Where an icon sits in its 44 dp (Default) view, like Niagara's home screen (decisions 5b U/E19, round 2). Pure.
 *
 * - Shape packs (Material, Marble, Bright, Stellar) draw a full-bleed shape; Niagara normalizes those like adaptive
 *   icons, whose visible shape is 98 of the 116 px view (measured: Maps' icon, left edge 9 px inside the view). So they
 *   get [SHAPE_SCALE] of the view, centred.
 * - Glyph packs (Sticker, Outline, Outline Chroma, Watercolor, Dots) are drawn over the whole view, as Niagara does
 *   (no normalization in its launcher code); their own composition leaves the margin (Sticker: 0.7 front + rim).
 * - The label under the icon starts where the icons visibly start (the median left edge of the widget's icons), so it
 *   doesn't stick out to the left of the column.
 */
object IconLayout {
    /** Niagara's visible adaptive icon / icon view: 98 / 116 px. */
    const val SHAPE_SCALE = 98.0 / 116.0

    /** Alpha from which a pixel counts as visible icon (ignores the soft launcher shadow). */
    const val VISIBLE_ALPHA = 128

    fun isShapePack(style: AnyconSettings): Boolean = StyleOptions.usesShape(StyleOptions.packOf(style.style))

    /** Padding on each side of the icon view, so a shape pack's visible size matches Niagara's adaptive icons. */
    fun paddingPx(style: AnyconSettings, viewPx: Int): Int =
        if (isShapePack(style)) (viewPx * (1 - SHAPE_SCALE) / 2).roundToInt() else 0

    /**
     * Leftmost column of [pixels] (ARGB, row-major, [width]×[height]) with a pixel at least [VISIBLE_ALPHA] opaque, or
     * null for an empty bitmap.
     */
    fun leftEdge(pixels: IntArray, width: Int, height: Int): Int? {
        for (x in 0 until width) {
            for (y in 0 until height) if ((pixels[y * width + x] ushr 24) >= VISIBLE_ALPHA) return x
        }
        return null
    }

    /** Largest label inset (dp): a small dot (Dots) or a narrow glyph must not push labels far right (round 3). */
    const val MAX_LABEL_INSET_DP = 8

    /**
     * Label start inside the cell (px): the view padding plus the median visible left edge of the icons, scaled from
     * bitmap px ([bitmapPx]) to the drawn size ([contentPx]), at most [maxPx]; just the padding when no edge is known.
     */
    fun labelStartPx(paddingPx: Int, edges: List<Int>, bitmapPx: Int, contentPx: Int, maxPx: Int = Int.MAX_VALUE): Int {
        if (edges.isEmpty() || bitmapPx <= 0) return paddingPx
        val median = edges.sorted()[edges.size / 2]
        return (paddingPx + (median.toDouble() * contentPx / bitmapPx).roundToInt()).coerceAtMost(maxOf(paddingPx, maxPx))
    }

    /**
     * One cell's label start: the inset, reduced only as far as needed so it never makes a label ellipsize that would
     * fit without it (room = [cellPx] minus [reservedPx] end padding, label [labelPx] wide); never below [paddingPx].
     * A label too long for the cell anyway keeps the inset (it ellipsizes either way).
     */
    fun fitLabelStartPx(startPx: Int, paddingPx: Int, cellPx: Int, reservedPx: Int, labelPx: Int): Int {
        val room = cellPx - reservedPx
        if (labelPx > room - paddingPx) return startPx
        return startPx.coerceAtMost(maxOf(paddingPx, room - labelPx))
    }

    /** Whether the label follows the icons' visible edge: not for Dots, whose dot sits in the middle of the view. */
    fun alignsLabel(style: AnyconSettings): Boolean = style.style.usesGlyph
}
