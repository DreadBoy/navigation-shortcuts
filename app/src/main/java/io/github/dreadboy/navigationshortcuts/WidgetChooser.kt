package io.github.dreadboy.navigationshortcuts

/**
 * Widget chooser logic for the share sheet (decisions 3.9), pure so it is unit-tested.
 *
 * Niagara's "Replace widget" leaves the old widget ID bound ("ghost"): the system reports it like any displayed widget.
 * The render probe tells them apart softly: a displayed widget loads each render when the home screen shows, a ghost
 * never does. Because the signal is unproven for hidden pages of Niagara Pro widget stacks, it only orders, labels and
 * warns — it never hides a widget or deletes its data.
 */
object WidgetChooser {
    /**
     * Widgets to flag "not seen on your home screen": the latest render was not displayed although another of our
     * widgets was displayed (fetched a render) after that render was pushed — the home screen was demonstrably shown,
     * just without this widget. Only such a witness counts, never elapsed time: renders pushed while the user stays in
     * Maps are legitimately unconfirmed for as long as the home screen isn't shown (round 3, decisions 3.9).
     */
    fun notSeen(statuses: Map<Int, RenderProbe.Status>): Set<Int> = statuses.filter { (id, s) ->
        !s.latestShown && statuses.any { (other, o) -> other != id && o.shownAt > s.unconfirmedSince }
    }.keys

    /** Places left on widgets that aren't on the home screen, and the visible widget they can move to (decisions 5b E5). */
    data class Stranded(val ghosts: List<Int>, val target: Int, val count: Int)

    /**
     * After Niagara's "Replace widget" the old ID stays bound with its places but is never shown (3.7/3.9). [notSeen]
     * widgets with places are "stranded"; the target is the newest widget that is not flagged (the one on the home
     * screen). Null when nothing is stranded or no visible widget exists. [count] = places to move (distinct ids that
     * are not on the target already).
     */
    fun stranded(ids: Collection<Int>, notSeen: Set<Int>, configs: Map<Int, WidgetConfig>): Stranded? {
        val target = order(ids, notSeen).firstOrNull { it !in notSeen } ?: return null
        val ghosts = order(ids, notSeen).filter { it in notSeen && configs[it]?.entries.orEmpty().isNotEmpty() }
        if (ghosts.isEmpty()) return null
        val onTarget = configs[target]?.entries.orEmpty().map { it.id }.toSet()
        val count = ghosts.flatMap { configs.getValue(it).entries }.map { it.id }.filterNot { it in onTarget }.toSet().size
        return if (count == 0) null else Stranded(ghosts, target, count)
    }

    /** Chooser order: widgets not flagged first, then newest (highest ID = most recently placed) first. */
    fun order(ids: Collection<Int>, notSeen: Set<Int>): List<Int> =
        ids.sortedWith(compareBy<Int> { if (it in notSeen) 1 else 0 }.thenByDescending { it })

    /**
     * Chip labels: the widget's "Label · Label" summary or [emptyLabel]; identical labels get [newest] / [older]
     * (by widget ID) so the user can tell them apart; flagged widgets get [notSeenSuffix].
     */
    fun labels(
        ids: Collection<Int>,
        summaries: Map<Int, String?>,
        notSeen: Set<Int>,
        emptyLabel: String,
        newest: String,
        older: String,
        notSeenSuffix: String,
    ): Map<Int, String> {
        val base = ids.associateWith { summaries[it] ?: emptyLabel }
        return base.mapValues { (id, label) ->
            val twins = base.filterValues { it == label }.keys.sortedDescending()
            val disambiguated = when {
                twins.size < 2 -> label
                twins.first() == id -> "$label ($newest)"
                twins.size == 2 -> "$label ($older)"
                else -> "$label ($older ${twins.indexOf(id)})"
            }
            if (id in notSeen) "$disambiguated — $notSeenSuffix" else disambiguated
        }
    }
}
