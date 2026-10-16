package io.github.dreadboy.navigationshortcuts.anycons.glyph

/** Niagara's stroke-width presets (`AnyconsStroke10` … `AnyconsStroke17`), in viewport units. */
enum class StrokeWeight(val width: Float) {
    W10(1.0f), W11(1.1f), W12(1.2f), W13(1.3f), W15(1.5f), W16(1.6f), W17(1.7f),
}

/**
 * How a glyph is rasterised as a single-colour layer: the alpha each colour role gets, the themed stroke width and
 * the themed group scale. These mirror Niagara's Anycons mask styles (only their numeric values are used).
 */
data class MaskStyle(
    val bgAlpha: Int,
    val fgAlpha: Int,
    val strokeAlpha: Int,
    val strokeWidth: Float,
    val groupScale: Float = 1f,
) {
    fun alphaOf(role: PaintRole): Int = when (role) {
        PaintRole.BG -> bgAlpha
        PaintRole.FG -> fgAlpha
        PaintRole.STROKE -> strokeAlpha
        PaintRole.NONE, PaintRole.OTHER -> 0
    }

    companion object {
        val COMPLETE_FILL = MaskStyle(0xFF, 0xFF, 0x00, 0f)
        val MIXED_FILL = MaskStyle(0xA0, 0xFF, 0x00, 0f)
        val SUBTLE_MIXED_FILL = MaskStyle(0x60, 0xFF, 0x00, 0f)
        val STAMP_STROKE = MaskStyle(0xFF, 0x00, 0xFF, 6.0f, groupScale = 0.7f)

        fun stroke(weight: StrokeWeight) = MaskStyle(0x00, 0x00, 0xFF, weight.width)
    }
}
