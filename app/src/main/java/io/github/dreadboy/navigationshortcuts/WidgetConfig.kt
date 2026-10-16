package io.github.dreadboy.navigationshortcuts

import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconSettings
import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconStyle
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Icon reference, resolved only when the widget renders (spec: Icons → Storage).
 * - `builtin`: bundled Material-style icon by name ([BuiltInIcons]).
 * - `niagara`: Anycon glyph `ic_ip_<name>` read from the installed Niagara and drawn in the widget's style
 *   ([IconRendering]); falls back to a built-in icon ([NiagaraGlyphs.fallbackFor]) whenever it can't be drawn.
 */
@Serializable
data class IconRef(val source: String, val name: String) {
    companion object {
        const val SOURCE_BUILTIN = "builtin"
        const val SOURCE_NIAGARA = "niagara"
        fun builtin(name: String) = IconRef(SOURCE_BUILTIN, name)
        fun niagara(glyphName: String) = IconRef(SOURCE_NIAGARA, glyphName)
    }
}

/**
 * One destination shortcut. [destination] is what goes into `google.navigation:q=` ("lat,lng" or text).
 * [id] only exists to make "add this shared place" idempotent across the pending list and the
 * requestPinAppWidget success callback (decisions 3.3); it is never shown.
 */
@Serializable
data class Entry(
    val label: String,
    val destination: String,
    val icon: IconRef,
    val id: String = UUID.randomUUID().toString(),
)

/**
 * Per-widget config, stored as JSON under `widget_<appWidgetId>` (decisions 0.4). Entries fill left→right, top→bottom.
 * [style] is how Niagara glyphs are drawn on this widget (decisions 5.5); 0.2.0 configs have none and get the default.
 * [iconSize] is the icon size on the home screen (decisions 5b, U); configs before 0.4.0 have none and get [IconSize.DEFAULT].
 */
@Serializable
data class WidgetConfig(
    val rows: Int = DEFAULT_ROWS,
    val cols: Int = DEFAULT_COLS,
    val entries: List<Entry> = emptyList(),
    val style: AnyconSettings = DEFAULT_STYLE,
    val iconSize: IconSize = IconSize.DEFAULT,
) {
    val capacity: Int get() = rows * cols
    val isFull: Boolean get() = entries.size >= capacity

    fun canGrowColumns(): Boolean = cols < MAX_SIZE
    fun canGrowRows(): Boolean = rows < MAX_SIZE

    /** "Label · Label · Label" for the widget chooser, or null for an empty widget. */
    fun summary(): String? = entries.takeIf { it.isNotEmpty() }?.joinToString(" · ") { it.label }

    fun contains(entryId: String): Boolean = entries.any { it.id == entryId }

    /** Adds [entry] if there is room; returns null when the grid is full. */
    fun plus(entry: Entry): WidgetConfig? = if (isFull) null else copy(entries = entries + entry)

    /**
     * Adds [entries] to a config, growing it (columns first, then rows, up to [MAX_SIZE]×[MAX_SIZE]) so they fit.
     * Returns the new config and the entries that did not fit even at the maximum size.
     */
    fun plusGrowing(newEntries: List<Entry>): Pair<WidgetConfig, List<Entry>> {
        var r = rows
        var c = cols
        val wanted = entries.size + newEntries.size
        while (r * c < wanted && (c < MAX_SIZE || r < MAX_SIZE)) {
            if (c < MAX_SIZE) c++ else r++
        }
        val room = (r * c - entries.size).coerceAtLeast(0)
        val fits = newEntries.take(room)
        return copy(rows = r, cols = c, entries = entries + fits) to newEntries.drop(room)
    }

    companion object {
        const val DEFAULT_ROWS = 1
        const val DEFAULT_COLS = 4
        const val MAX_SIZE = 6
        const val MIN_SIZE = 1

        /** Sticker with the Coral palette: the pack the user runs in Niagara on the phone (decisions 5.5). */
        val DEFAULT_STYLE = AnyconSettings(style = AnyconStyle.Sticker(AnyconStyle.Sticker.Palette.CORAL))
    }
}

/**
 * Icon size of a widget's cells (decisions 5b, U). [DEFAULT] is Niagara's own home-screen app icon (44 dp, measured on
 * Niagara 1.16.33); the label size follows. Icons still shrink to fit when the cell is smaller.
 */
@Serializable
enum class IconSize(val dp: Int, val labelSp: Int, val label: String) {
    @SerialName("small")
    SMALL(36, 14, "Small"),

    @SerialName("default")
    DEFAULT(44, 16, "Default (like Niagara)"),

    @SerialName("large")
    LARGE(56, 16, "Large"),
    ;

    /**
     * The size actually drawn with [cols] columns: the chosen one, or the next smaller that fits a column of Niagara's
     * 315 dp slot (decisions 1.3) with the cell's 4 dp end padding, so an icon is never cut (Large with 6 columns → Default).
     */
    fun fitting(cols: Int): IconSize {
        val column = SLOT_WIDTH_DP / cols.coerceAtLeast(1) - CELL_PADDING_DP
        return entries.filter { it.ordinal <= ordinal && it.dp <= column }.maxByOrNull { it.dp } ?: SMALL
    }

    companion object {
        const val SLOT_WIDTH_DP = 315
        const val CELL_PADDING_DP = 4
    }
}

/** How the share sheet grows a full grid ("+1 column" / "+1 row"). */
enum class Growth { COLUMN, ROW }

fun WidgetConfig.grow(growth: Growth): WidgetConfig? = when (growth) {
    Growth.COLUMN -> if (canGrowColumns()) copy(cols = cols + 1) else null
    Growth.ROW -> if (canGrowRows()) copy(rows = rows + 1) else null
}
