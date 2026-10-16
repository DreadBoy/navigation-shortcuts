package io.github.dreadboy.navigationshortcuts.anycons.glyph

/**
 * Which themed colour a path's fill or stroke refers to. Niagara's glyphs never hard-code their colours; each
 * path points at one of the Anycons theme attributes, and each mask style (see [MaskStyle]) gives those
 * attributes an alpha. Fill and stroke are classified independently: a few glyphs *fill* with the stroke
 * colour, and that fill belongs to the stroke layer.
 */
enum class PaintRole {
    /** Attribute absent: nothing is painted. */
    NONE,

    /** `?AnyconsBgFillColor`: the silhouette. */
    BG,

    /** `?AnyconsFgFillColor`: the detail fill. */
    FG,

    /** `?AnyconsStrokeColor`: the outline (and stroke-coloured fills). */
    STROKE,

    /** A literal colour (e.g. `#00000000`) or an unknown reference (e.g. `?weatherCold`): treated as invisible. */
    OTHER,
}

enum class StrokeCap { BUTT, ROUND, SQUARE }

enum class StrokeJoin { MITER, ROUND, BEVEL }

enum class FillType { NON_ZERO, EVEN_ODD }

/**
 * A scalar attribute that is either a theme reference (resolved by the mask style at render time) or a literal.
 */
sealed interface ThemedFloat {
    data object Themed : ThemedFloat
    data class Literal(val value: Float) : ThemedFloat
}

/** One `<group>` transform as VectorDrawable applies it: translate(-pivot), scale, rotate, translate(pivot + t). */
data class GroupTransform(
    val pivotX: Float = 0f,
    val pivotY: Float = 0f,
    val scaleX: ThemedFloat = ThemedFloat.Literal(1f),
    val scaleY: ThemedFloat = ThemedFloat.Literal(1f),
    val rotation: Float = 0f,
    val translateX: Float = 0f,
    val translateY: Float = 0f,
)

data class GlyphPath(
    val pathData: String,
    val fillRole: PaintRole,
    val strokeRole: PaintRole,
    val strokeWidth: ThemedFloat,
    val strokeCap: StrokeCap = StrokeCap.BUTT,
    val strokeJoin: StrokeJoin = StrokeJoin.MITER,
    val strokeMiterLimit: Float = 4f,
    val fillType: FillType = FillType.NON_ZERO,
    /** Enclosing groups, outermost first. */
    val groups: List<GroupTransform> = emptyList(),
)

/** A parsed Anycon glyph (Niagara `ic_ip_*` VectorDrawable). */
data class Glyph(
    val name: String,
    val widthDp: Float,
    val heightDp: Float,
    val viewportWidth: Float,
    val viewportHeight: Float,
    val paths: List<GlyphPath>,
)
