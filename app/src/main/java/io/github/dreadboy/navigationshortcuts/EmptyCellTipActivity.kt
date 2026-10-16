package io.github.dreadboy.navigationshortcuts

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle

/**
 * Shown when an empty widget cell is tapped (MVP; the full config screen comes in Phase 4): how to fill it.
 * A transparent activity hosting one dialog, finished when the dialog goes away.
 */
class EmptyCellTipActivity : Activity() {
    private var dialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val builder = AlertDialog.Builder(this)
            .setTitle(R.string.tip_title)
            .setMessage(R.string.tip_message)
            .setPositiveButton(R.string.ok, null)
            .setOnDismissListener { finish() }
        packageManager.getLaunchIntentForPackage(NavigationUris.MAPS_PACKAGE)?.let { maps ->
            builder.setNeutralButton(R.string.tip_open_maps) { _, _ -> startActivity(maps) }
        }
        dialog = builder.show()
    }

    override fun onDestroy() {
        dialog?.setOnDismissListener(null)
        dialog?.dismiss()
        super.onDestroy()
    }
}
