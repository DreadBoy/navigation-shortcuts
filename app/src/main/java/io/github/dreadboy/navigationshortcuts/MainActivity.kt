package io.github.dreadboy.navigationshortcuts

import android.app.Activity
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
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
        scope.launch {
            val (ids, state) = withContext(Dispatchers.IO) {
                val store = WidgetStore(applicationContext)
                val bound = store.boundWidgetIds()
                bound to (store.update { it.prune(bound) } ?: store.read())
            }
            val ordered = WidgetChooser.order(ids, emptySet())
            val labels = WidgetChooser.labels(
                ids = ordered,
                summaries = ordered.associateWith { state.configs[it]?.summary() },
                notSeen = emptySet(),
                emptyLabel = getString(R.string.empty_widget),
                newest = getString(R.string.chip_newest),
                older = getString(R.string.chip_older),
                notSeenSuffix = getString(R.string.chip_not_seen),
            )
            val list = findViewById<LinearLayout>(R.id.widget_list)
            list.removeAllViews()
            findViewById<View>(R.id.no_widgets_text).visibility = if (ordered.isEmpty()) View.VISIBLE else View.GONE
            val pad = (12 * resources.displayMetrics.density).toInt()
            val ripple = TypedValue().also { theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true) }
            for (id in ordered) {
                list.addView(TextView(this@MainActivity).apply {
                    text = labels.getValue(id)
                    contentDescription = getString(R.string.main_open_widget, labels.getValue(id))
                    setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Medium)
                    setPadding(0, pad, 0, pad)
                    setBackgroundResource(ripple.resourceId)
                    setOnClickListener { startActivity(WidgetConfigActivity.intent(this@MainActivity, id, fromWidget = false)) }
                })
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
