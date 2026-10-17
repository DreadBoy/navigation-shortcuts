package io.github.dreadboy.navigationshortcuts

/**
 * Small pure helpers behind texts of the share sheet and the config screen (decisions 5b, settings and sheet UX).
 * Unit-tested without Android.
 */
object SheetTexts {
    /** How many of the target widget's other labels the "Added …" toast names before "…" (E23). */
    const val MAX_NAMED = 3

    /**
     * The other destinations of the widget a place was added to, for "Added Stožice Stadium to the widget with Doma,
     * Služba, …" (E23): up to [max] labels, then "…"; null when the widget had none (the toast then says "to the widget").
     */
    fun others(labels: List<String>, max: Int = MAX_NAMED): String? {
        if (labels.isEmpty()) return null
        val named = labels.take(max).joinToString(", ")
        return if (labels.size > max) "$named, …" else named
    }

    /**
     * What a widget cell shows of [label] (E14): all of it if it fits [widthPx], else the longest prefix that fits with
     * "…", as the cell's one-line, end-ellipsized label does. [measure] is the text width in px.
     */
    fun shownLabel(label: String, widthPx: Float, measure: (String) -> Float): String {
        val text = label.trim()
        if (measure(text) <= widthPx) return text
        var n = text.length
        while (n > 1) {
            n--
            val candidate = text.take(n).trimEnd() + ELLIPSIS
            if (measure(candidate) <= widthPx) return candidate
        }
        return text.take(1) + ELLIPSIS
    }

    /**
     * Width (dp) a cell label has in Niagara's default 315 dp slot with [cols] columns: the column minus the cell's end
     * padding and the label's own 1 + 2 dp shadow padding (decisions 5b U/E19).
     */
    fun labelWidthDp(cols: Int): Float =
        IconSize.SLOT_WIDTH_DP.toFloat() / cols.coerceIn(WidgetConfig.MIN_SIZE, WidgetConfig.MAX_SIZE) -
            IconSize.CELL_PADDING_DP - LABEL_PADDING_DP

    /** Config screen (E1): whether the edited state differs from what was loaded, i.e. Back must ask. */
    fun hasUnsavedChanges(original: WidgetConfig?, edited: WidgetConfig): Boolean = original != null && original != edited

    /**
     * Add/Edit dialog: whether renaming re-guesses the icon. A new entry: yes. An existing one: only while its icon is
     * still the guess for its current name — an icon picked by hand isn't tracked, but differs from the guess.
     */
    fun iconFollowsLabel(existing: Entry?): Boolean =
        existing == null || existing.icon == IconGuess.guess(existing.label, existing.destination)

    private const val LABEL_PADDING_DP = 3
    const val ELLIPSIS = "…"
}
