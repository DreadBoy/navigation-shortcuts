package io.github.dreadboy.navigationshortcuts

/**
 * Everything the app stores: per-widget configs plus the pending list (places shared while no widget
 * was placed). All mutations are pure functions here; [WidgetStore] runs each one inside a single
 * atomic DataStore edit, so concurrent paths (onUpdate of a new widget, the pin success callback,
 * the share sheet) cannot interleave or duplicate an entry (decisions 3.3).
 */
data class StoreState(
    val configs: Map<Int, WidgetConfig> = emptyMap(),
    val pending: List<Entry> = emptyList(),
) {
    /** True when [entryId] is already on any widget. */
    fun isPlaced(entryId: String): Boolean = configs.values.any { it.contains(entryId) }

    /**
     * Makes sure [widgetId] has a config. A widget without one is new (or a 0.1.0 widget after the
     * update): it gets the default 1×4 grid and takes over the pending list, growing to fit it.
     */
    fun ensureConfig(widgetId: Int): StoreState {
        if (widgetId in configs) return this
        val (config, leftover) = WidgetConfig().plusGrowing(pending)
        return copy(configs = configs + (widgetId to config), pending = leftover)
    }

    /** Drops configs of widgets that no longer exist ([validIds] = AppWidgetManager.getAppWidgetIds). */
    fun prune(validIds: Collection<Int>): StoreState =
        copy(configs = configs.filterKeys { it in validIds })

    fun delete(widgetIds: Collection<Int>): StoreState = copy(configs = configs - widgetIds.toSet())

    fun addPending(entry: Entry): StoreState =
        if (isPlaced(entry.id) || pending.any { it.id == entry.id }) this else copy(pending = pending + entry)

    /**
     * Adds [entry] to [widgetId], first growing the grid by [growth] if given. Returns null if the grid is
     * full (and cannot or was not asked to grow). Idempotent on [Entry.id].
     */
    fun addEntry(widgetId: Int, entry: Entry, growth: Growth? = null): StoreState? {
        val base = ensureConfig(widgetId)
        if (base.isPlaced(entry.id)) return base
        var config = base.configs.getValue(widgetId)
        if (growth != null) config = config.grow(growth) ?: return null
        val added = config.plus(entry) ?: return null
        return base.copy(configs = base.configs + (widgetId to added), pending = base.pending.filterNot { it.id == entry.id })
    }

    /**
     * Config screen "Save" (decisions 4.1): [edited] (grid, style, entries in their new order) replaces the widget's
     * config. The screen edited a snapshot ([original]); entries that reached the widget meanwhile (a share while the
     * screen was open: not in [original], not in [edited]) are kept and appended. Nothing is ever dropped: if the
     * entries don't fit the edited grid it grows (columns first, then rows); beyond 6×6 the rest goes to pending.
     */
    fun saveEdited(widgetId: Int, original: WidgetConfig, edited: WidgetConfig): StoreState {
        val known = (original.entries + edited.entries).map { it.id }.toSet()
        val addedMeanwhile = configs[widgetId]?.entries.orEmpty().filter { it.id !in known }
        val (config, leftover) = edited.copy(entries = emptyList()).plusGrowing(edited.entries + addedMeanwhile)
        val placedIds = config.entries.map { it.id }.toSet()
        return copy(
            configs = configs + (widgetId to config),
            pending = pending.filterNot { it.id in placedIds } + leftover.filterNot { e -> pending.any { it.id == e.id } },
        )
    }

    /**
     * requestPinAppWidget success callback: the launcher placed [widgetId] for [entry]. If the widget's first
     * update already took the entry over from the pending list (or it is on any other widget), nothing is
     * added again; otherwise it is added, growing the grid if needed.
     */
    fun completePin(widgetId: Int, entry: Entry): StoreState {
        val base = ensureConfig(widgetId)
        if (base.isPlaced(entry.id)) return base.copy(pending = base.pending.filterNot { it.id == entry.id })
        val (config, leftover) = base.configs.getValue(widgetId).plusGrowing(listOf(entry))
        val pendingWithout = base.pending.filterNot { it.id == entry.id }
        return base.copy(configs = base.configs + (widgetId to config), pending = pendingWithout + leftover)
    }
}
