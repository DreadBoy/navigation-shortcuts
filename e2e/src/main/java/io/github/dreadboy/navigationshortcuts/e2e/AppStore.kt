package io.github.dreadboy.navigationshortcuts.e2e

import android.util.Log

/**
 * Test isolation for the app's store (debug build's DebugStoreReceiver). Since decisions 5b E5 a removed widget's
 * destinations move to the pending list, so the widget a test removes in setUp would hand its places to the next test's
 * widget. Each setUp clears the pending list after removing the widget — a precondition reset, like the uninstall in
 * run-e2e.sh; never used on the path under test.
 */
class AppStore(private val d: E2eDevice) {
    fun clearPending() {
        val out = d.shell("am broadcast -n ${E2e.APP}/.DebugStoreReceiver -a ${E2e.APP}.debug.CLEAR_PENDING")
        check("dropped=" in out) { "CLEAR_PENDING failed: $out" }
        Log.i("NavShortE2e", "store: ${out.trim().lines().last()}")
    }
}
