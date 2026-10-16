package io.github.dreadboy.navigationshortcuts

import android.content.Intent
import android.os.Looper
import android.view.View
import android.widget.Button
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * System Back in the share sheet: step 2 (widget list) goes back to step 1, step 1 closes the sheet.
 * API 32 is the minSdk-range path without OnBackInvokedDispatcher (the API 33-only calls used before crashed
 * there); API 36 is the targetSdk path. Back is delivered the way the framework does on API 31–35
 * (Activity.onBackPressed), which ComponentActivity routes through the OnBackPressedDispatcher.
 */
@RunWith(RobolectricTestRunner::class)
class AddDestinationBackTest {

    @Test @Config(sdk = [32])
    fun backInWidgetStepReturnsToDetails_api32() = backInWidgetStepReturnsToDetails()

    @Test @Config(sdk = [36])
    fun backInWidgetStepReturnsToDetails_api36() = backInWidgetStepReturnsToDetails()

    @Test @Config(sdk = [32])
    fun backInDetailsStepClosesSheet_api32() = backInDetailsStepClosesSheet()

    @Test @Config(sdk = [36])
    fun backInDetailsStepClosesSheet_api36() = backInDetailsStepClosesSheet()

    private fun backInWidgetStepReturnsToDetails() {
        val activity = startSheet().get()
        val next = activity.findViewById<Button>(R.id.add_button)
        waitUntil("Next enabled (share resolved)") { next.isEnabled }
        next.performClick()
        idle()
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.widget_step).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.form).visibility)

        pressBack(activity)

        assertFalse("Back in step 2 must not close the sheet", activity.isFinishing)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.form).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.widget_step).visibility)

        pressBack(activity) // now in step 1: the default Back closes the sheet
        assertTrue(activity.isFinishing)
    }

    private fun backInDetailsStepClosesSheet() {
        val activity = startSheet().get()
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.form).visibility)
        pressBack(activity)
        assertTrue(activity.isFinishing)
    }

    private fun startSheet(): ActivityController<AddDestinationActivity> {
        // A coordinate link resolves without network (MapsShareParser.coordinateQuery).
        val intent = Intent(Intent.ACTION_SEND)
            .setClassName("io.github.dreadboy.navigationshortcuts", AddDestinationActivity::class.java.name)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "https://maps.google.com/?q=46.056900,14.505800")
        return Robolectric.buildActivity(AddDestinationActivity::class.java, intent).setup().also { idle() }
    }

    @Suppress("DEPRECATION")
    private fun pressBack(activity: AddDestinationActivity) {
        activity.onBackPressed()
        idle()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** The sheet resolves the share and loads widgets on Dispatchers.IO, then continues on the main looper. */
    private fun waitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            idle()
            if (condition()) return
            Thread.sleep(20)
        }
        throw AssertionError("timed out waiting for: $what")
    }
}
