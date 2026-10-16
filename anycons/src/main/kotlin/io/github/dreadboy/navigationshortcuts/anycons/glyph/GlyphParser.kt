package io.github.dreadboy.navigationshortcuts.anycons.glyph

import org.xmlpull.v1.XmlPullParser

/**
 * Maps a numeric theme-attribute id (as found in compiled XML, e.g. `?2130968576`) to the colour role it stands
 * for. At runtime the ids are Niagara's private `0x7f04…` ids, looked up by name with `getIdentifier`.
 */
fun interface AttrRoleResolver {
    fun roleOf(attrId: Int): PaintRole?

    companion object {
        val NONE = AttrRoleResolver { null }
    }
}

/**
 * Parses an Anycon glyph VectorDrawable from an [XmlPullParser].
 *
 * Two value forms are accepted, so the same parser serves the device and the tests:
 * - compiled binary XML (`Resources.getXml`): theme refs come back as `?<decimal attr id>` and enums as integers
 *   (`strokeLineCap="1"`);
 * - text XML (apktool-decoded): theme refs as `?AnyconsBgFillColor` / `?attr/AnyconsBgFillColor` and enums as names
 *   (`round`).
 *
 * Roles are taken from each path's own attributes, never from the path's position in the file.
 */
class GlyphParser(private val resolver: AttrRoleResolver = AttrRoleResolver.NONE) {

    class GlyphParseException(message: String) : Exception(message)

    fun parse(name: String, parser: XmlPullParser): Glyph {
        var widthDp = Float.NaN
        var heightDp = Float.NaN
        var viewportWidth = Float.NaN
        var viewportHeight = Float.NaN
        var sawVector = false
        val groupStack = ArrayDeque<GroupTransform?>() // null entries for non-group elements (keep depth aligned)
        val paths = mutableListOf<GlyphPath>()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val attrs = attributes(parser)
                    when (parser.name) {
                        "vector" -> {
                            sawVector = true
                            widthDp = parseDimension(attrs["width"])
                            heightDp = parseDimension(attrs["height"])
                            viewportWidth = attrs["viewportWidth"]?.toFloatOrNull() ?: Float.NaN
                            viewportHeight = attrs["viewportHeight"]?.toFloatOrNull() ?: Float.NaN
                            groupStack.addLast(null)
                        }
                        "group" -> groupStack.addLast(parseGroup(attrs))
                        "path" -> {
                            parsePath(attrs, groupStack.filterNotNull())?.let(paths::add)
                            groupStack.addLast(null)
                        }
                        else -> groupStack.addLast(null)
                    }
                }
                XmlPullParser.END_TAG -> groupStack.removeLastOrNull()
            }
            event = parser.next()
        }

        if (!sawVector) throw GlyphParseException("$name: no <vector> root")
        if (viewportWidth.isNaN() || viewportHeight.isNaN() || viewportWidth <= 0f || viewportHeight <= 0f) {
            throw GlyphParseException("$name: missing or invalid viewport")
        }
        if (widthDp.isNaN()) widthDp = viewportWidth
        if (heightDp.isNaN()) heightDp = viewportHeight
        return Glyph(name, widthDp, heightDp, viewportWidth, viewportHeight, paths)
    }

    private fun attributes(parser: XmlPullParser): Map<String, String> {
        val map = HashMap<String, String>(parser.attributeCount * 2)
        for (i in 0 until parser.attributeCount) {
            val raw = parser.getAttributeName(i) ?: continue
            val local = raw.substringAfterLast(':')
            val value = parser.getAttributeValue(i) ?: continue
            map[local] = value
        }
        return map
    }

    private fun parseGroup(attrs: Map<String, String>) = GroupTransform(
        pivotX = attrs["pivotX"]?.toFloatOrNull() ?: 0f,
        pivotY = attrs["pivotY"]?.toFloatOrNull() ?: 0f,
        scaleX = parseThemedFloat(attrs["scaleX"], 1f),
        scaleY = parseThemedFloat(attrs["scaleY"], 1f),
        rotation = attrs["rotation"]?.toFloatOrNull() ?: 0f,
        translateX = attrs["translateX"]?.toFloatOrNull() ?: 0f,
        translateY = attrs["translateY"]?.toFloatOrNull() ?: 0f,
    )

    private fun parsePath(attrs: Map<String, String>, groups: List<GroupTransform>): GlyphPath? {
        val data = attrs["pathData"]?.takeIf { it.isNotBlank() } ?: return null
        return GlyphPath(
            pathData = data,
            fillRole = classify(attrs["fillColor"]),
            strokeRole = classify(attrs["strokeColor"]),
            strokeWidth = parseThemedFloat(attrs["strokeWidth"], 0f),
            strokeCap = parseCap(attrs["strokeLineCap"]),
            strokeJoin = parseJoin(attrs["strokeLineJoin"]),
            strokeMiterLimit = attrs["strokeMiterLimit"]?.toFloatOrNull() ?: 4f,
            fillType = parseFillType(attrs["fillType"]),
            groups = groups,
        )
    }

    /** Classifies a colour attribute value into a [PaintRole]. */
    fun classify(value: String?): PaintRole {
        if (value.isNullOrBlank()) return PaintRole.NONE
        val v = value.trim()
        if (!v.startsWith("?")) return PaintRole.OTHER // literal #colour or @color/… reference
        val ref = v.substring(1)
        ref.toLongOrNull()?.let { return resolver.roleOf(it.toInt()) ?: PaintRole.OTHER }
        return when (ref.substringAfterLast('/').substringAfterLast(':')) {
            ATTR_BG -> PaintRole.BG
            ATTR_FG -> PaintRole.FG
            ATTR_STROKE -> PaintRole.STROKE
            else -> PaintRole.OTHER
        }
    }

    companion object {
        const val ATTR_BG = "AnyconsBgFillColor"
        const val ATTR_FG = "AnyconsFgFillColor"
        const val ATTR_STROKE = "AnyconsStrokeColor"

        internal fun parseThemedFloat(value: String?, default: Float): ThemedFloat {
            if (value.isNullOrBlank()) return ThemedFloat.Literal(default)
            if (value.startsWith("?")) return ThemedFloat.Themed
            return ThemedFloat.Literal(value.toFloatOrNull() ?: default)
        }

        internal fun parseDimension(value: String?): Float {
            if (value.isNullOrBlank()) return Float.NaN
            return value.trim().trimEnd { it.isLetter() }.toFloatOrNull() ?: Float.NaN
        }

        internal fun parseCap(value: String?): StrokeCap = when (value?.trim()) {
            "1", "round" -> StrokeCap.ROUND
            "2", "square" -> StrokeCap.SQUARE
            else -> StrokeCap.BUTT
        }

        internal fun parseJoin(value: String?): StrokeJoin = when (value?.trim()) {
            "1", "round" -> StrokeJoin.ROUND
            "2", "bevel" -> StrokeJoin.BEVEL
            else -> StrokeJoin.MITER
        }

        internal fun parseFillType(value: String?): FillType = when (value?.trim()) {
            "1", "evenOdd" -> FillType.EVEN_ODD
            else -> FillType.NON_ZERO
        }
    }
}
