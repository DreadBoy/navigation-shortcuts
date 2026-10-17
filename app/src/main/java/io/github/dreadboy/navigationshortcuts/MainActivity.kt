package io.github.dreadboy.navigationshortcuts

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Launcher entry (PLAN 4.2): lists the placed widgets, identified like the share sheet's chooser ("Home · Gym · …",
 * newest first); tapping one opens its config. Below: how to add the widget and destinations.
 */
class MainActivity : Activity() {
    private val scope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        scope.launch {
            val (ids, state, flagged) = withContext(Dispatchers.IO) {
                val store = WidgetStore(applicationContext)
                val bound = store.boundWidgetIds()
                // Widgets the launcher no longer shows (a replaced widget, decisions 3.9), from earlier renders.
                val flagged = WidgetChooser.notSeen(bound.associateWith { RenderProbe.status(applicationContext, it) })
                Triple(bound, store.update { it.prune(bound) } ?: store.read(), flagged)
            }
            val stranded = WidgetChooser.stranded(ids, flagged, state.configs)
            showStranded(stranded, state)
            val ordered = WidgetChooser.order(ids, flagged)
            val labels = WidgetChooser.labels(
                ids = ordered,
                summaries = ordered.associateWith { state.configs[it]?.summary() },
                notSeen = flagged,
                emptyLabel = getString(R.string.empty_widget),
                newest = getString(R.string.chip_newest),
                older = getString(R.string.chip_older),
                notSeenSuffix = getString(R.string.chip_not_seen),
            )
            val list = findViewById<LinearLayout>(R.id.widget_list)
            list.removeAllViews()
            findViewById<View>(R.id.no_widgets_text).visibility = if (ordered.isEmpty()) View.VISIBLE else View.GONE
            // Cards with a chevron and "Tap to edit …", so they read as tappable (decisions 5b E15).
            for (id in ordered) {
                val row = LayoutInflater.from(this@MainActivity).inflate(R.layout.main_widget_row, list, false)
                row.findViewById<TextView>(R.id.widget_row_title).text = labels.getValue(id)
                if (id in flagged) row.findViewById<TextView>(R.id.widget_row_hint).setText(R.string.main_widget_row_not_seen)
                row.contentDescription = getString(R.string.main_open_widget, labels.getValue(id))
                row.setOnClickListener { startActivity(WidgetConfigActivity.intent(this@MainActivity, id, fromWidget = false)) }
                list.addView(row)
            }
        }
    }

    /** "A widget that is no longer on your home screen still holds 4 places: …" + "Move 4 places …" (decisions 5b E5). */
    private fun showStranded(stranded: WidgetChooser.Stranded?, state: StoreState) {
        val box = findViewById<View>(R.id.stranded_box)
        if (stranded == null) {
            box.visibility = View.GONE
            return
        }
        val names = stranded.ghosts.flatMap { state.configs[it]?.entries.orEmpty() }.map { it.label }.distinct()
        findViewById<TextView>(R.id.stranded_text).text =
            resources.getQuantityString(R.plurals.stranded_text, stranded.count, stranded.count, SheetTexts.others(names) ?: "")
        findViewById<Button>(R.id.stranded_move).apply {
            text = resources.getQuantityString(R.plurals.stranded_move, stranded.count, stranded.count)
            isEnabled = true
            setOnClickListener {
                isEnabled = false
                scope.launch {
                    withContext(Dispatchers.IO) { StrandedPlaces.move(applicationContext, stranded) }
                    Toast.makeText(
                        applicationContext,
                        resources.getQuantityString(R.plurals.stranded_moved, stranded.count, stranded.count),
                        Toast.LENGTH_LONG,
                    ).show()
                    refresh()
                }
            }
        }
        box.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
