package io.github.dreadboy.navigationshortcuts.e2e

import android.os.SystemClock
import android.util.Log

/**
 * Test isolation for the app's own data. Since decisions 5b E5 a removed widget's destinations move to the pending
 * list, so the widget a test removes in setUp would hand its places to the next test's widget. Each setUp therefore
 * clears the app's data with `pm clear` (shell, via UiAutomation) after removing the widget: works the same on debug
 * and release builds (no debug-only hook, decisions 6.2). A precondition reset, like the uninstall in run-e2e.sh;
 * never on the path under test. It also kills the app process and resets settings and the render-probe records.
 * Widget IDs stay bound (ghosts survive it, only an uninstall clears them) and Niagara is untouched.
 */
class AppStore(private val d: E2eDevice) {
    fun reset() {
        // onDeleted of the widget just removed may still be on its way; let it land before wiping the data.
        SystemClock.sleep(500)
        val out = d.shell("pm clear ${E2e.APP}").trim()
        check(out == "Success") { "pm clear ${E2e.APP} failed: $out" }
        Log.i("NavShortE2e", "app data cleared (pm clear)")
    }

    /** True when the app under test is a debug build with its store hooks (DebugStoreReceiver). */
    fun hasDebugHooks(): Boolean =
        "DebugStoreReceiver" in d.shell("cmd package query-receivers --brief -a ${E2e.APP}.debug.DUMP_STORE")
}
