package io.github.dreadboy.navigationshortcuts

import android.content.Context
import android.util.Log

/**
 * Places stranded on a replaced widget (decisions 5b E5 round 2): Niagara's "Replace widget" keeps the old widget ID
 * bound without showing it and sends no onDeleted, so its places would be invisible. The render probe flags such a
 * widget (decisions 3.9); the app screen and the config screen offer to move its places to the visible widget.
 */
object StrandedPlaces {
    private const val TAG = "NavShortStranded"

    /** Off the main thread: what is stranded now (null: nothing), from the probe statuses of earlier renders. */
    suspend fun find(context: Context): WidgetChooser.Stranded? {
        val store = WidgetStore(context)
        val bound = store.boundWidgetIds()
        val flagged = WidgetChooser.notSeen(bound.associateWith { RenderProbe.status(context, it) })
        val state = store.update { it.prune(bound) } ?: store.read()
        return WidgetChooser.stranded(bound, flagged, state.configs)
    }

    /** Off the main thread: moves the places of [stranded]'s ghosts to its target and re-renders both. */
    suspend fun move(context: Context, stranded: WidgetChooser.Stranded): StoreState? {
        val store = WidgetStore(context)
        val after = store.update { state ->
            stranded.ghosts.fold(state) { acc, ghost -> acc.movePlaces(ghost, stranded.target) ?: acc }
        }
        Log.i(TAG, "moved places of ${stranded.ghosts} to widget ${stranded.target}: ${after?.configs?.get(stranded.target)?.summary()}")
        WidgetUpdater.update(context, listOf(stranded.target) + stranded.ghosts)
        return after
    }
}
