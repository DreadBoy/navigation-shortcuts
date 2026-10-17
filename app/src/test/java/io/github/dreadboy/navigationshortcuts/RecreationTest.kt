package io.github.dreadboy.navigationshortcuts

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import org.robolectric.RuntimeEnvironment
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
import org.robolectric.shadows.ShadowDialog

/**
 * Decisions 5b round 2: unsaved edits survive a recreation of the config screen and the share sheet — both a
 * configuration change (dark-mode switch → `recreate()`) and process death (a new activity from the saved state).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RecreationTest {

    // ------------------------------------------------------------------ config screen

    private fun configIntent(): Intent {
        val context = RuntimeEnvironment.getApplication()
        val id = shadowOf(AppWidgetManager.getInstance(context)).createWidget(NavigationWidgetProvider::class.java, R.layout.widget_grid)
        return WidgetConfigActivity.intent(context, id, fromWidget = false)
    }

    private fun <T : Activity> loaded(controller: ActivityController<T>): T {
        val activity = controller.get()
        waitUntil("config loaded") { activity.findViewById<Button>(R.id.config_save).isEnabled }
        return activity
    }

    private fun rows(activity: Activity) = activity.findViewById<TextView>(R.id.rows_value).text.toString()

    private fun backAsksToSave(activity: Activity) {
        @Suppress("DEPRECATION")
        activity.onBackPressed()
        idle()
        assertFalse("Back with restored edits must ask, not close", activity.isFinishing)
        val dialog = ShadowDialog.getLatestDialog()
        assertTrue("'Save changes?' shown", dialog != null && dialog.isShowing)
        dialog.dismiss()
    }

    @Test
    fun configScreen_keepsEdits_overAConfigurationChange() {
        val controller = Robolectric.buildActivity(WidgetConfigActivity::class.java, configIntent()).setup()
        val activity = loaded(controller)
        activity.findViewById<Button>(R.id.rows_plus).performClick()
        assertEquals("2", rows(activity))

        controller.recreate()
        val recreated = loaded(controller)
        assertEquals("edit kept after recreation", "2", rows(recreated))
        backAsksToSave(recreated)
    }

    @Test
    fun configScreen_keepsEdits_overProcessDeath() {
        val intent = configIntent()
        val first = Robolectric.buildActivity(WidgetConfigActivity::class.java, intent).setup()
        loaded(first).findViewById<Button>(R.id.rows_plus).performClick()
        val saved = Bundle()
        first.saveInstanceState(saved).pause().stop().destroy()

        val second = Robolectric.buildActivity(WidgetConfigActivity::class.java, intent).setup(saved)
        val restored = loaded(second)
        assertEquals("edit kept after process death", "2", rows(restored))
        backAsksToSave(restored)
    }

    @Test
    fun configScreen_keepsAnOpenAddDialogsText() {
        val controller = Robolectric.buildActivity(WidgetConfigActivity::class.java, configIntent()).setup()
        val activity = loaded(controller)
        activity.findViewById<Button>(R.id.add_manual_button).performClick()
        idle()
        ShadowDialog.getLatestDialog().findViewById<EditText>(R.id.dialog_label).setText("Half typed")
        controller.recreate()
        loaded(controller)
        val dialog = ShadowDialog.getLatestDialog()
        assertTrue(dialog.isShowing)
        assertEquals("Half typed", dialog.findViewById<EditText>(R.id.dialog_label).text.toString())
    }

    // ------------------------------------------------------------------ share sheet

    private fun sheetIntent() = Intent(Intent.ACTION_SEND)
        .setClassName("io.github.dreadboy.navigationshortcuts", AddDestinationActivity::class.java.name)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, "https://maps.google.com/?q=46.056900,14.505800")

    private fun editedSheetOnStep2(controller: ActivityController<AddDestinationActivity>): AddDestinationActivity {
        val activity = controller.get()
        val next = activity.findViewById<Button>(R.id.add_button)
        waitUntil("share resolved") { next.isEnabled }
        activity.findViewById<EditText>(R.id.label_input).setText("Stadion")
        next.performClick()
        idle()
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.widget_step).visibility)
        return activity
    }

    private fun assertRestoredOnStep2(activity: AddDestinationActivity) {
        waitUntil("widget list loaded") { activity.findViewById<Button>(R.id.add_button).isEnabled }
        assertEquals("still on step 2", View.VISIBLE, activity.findViewById<View>(R.id.widget_step).visibility)
        assertEquals("Stadion", activity.findViewById<EditText>(R.id.label_input).text.toString())
        @Suppress("DEPRECATION")
        activity.onBackPressed()
        idle()
        assertFalse(activity.isFinishing)
        assertEquals("Back → step 1 with the edit", View.VISIBLE, activity.findViewById<View>(R.id.form).visibility)
        assertEquals("Stadion", activity.findViewById<EditText>(R.id.label_input).text.toString())
        assertEquals("46.056900,14.505800", activity.findViewById<EditText>(R.id.destination_input).text.toString())
    }

    @Test
    fun shareSheet_keepsEditsAndStep_overAConfigurationChange() {
        val controller = Robolectric.buildActivity(AddDestinationActivity::class.java, sheetIntent()).setup()
        editedSheetOnStep2(controller)
        controller.recreate()
        assertRestoredOnStep2(controller.get())
    }

    @Test
    fun shareSheet_keepsEditsAndStep_overProcessDeath() {
        val first = Robolectric.buildActivity(AddDestinationActivity::class.java, sheetIntent()).setup()
        editedSheetOnStep2(first)
        val saved = Bundle()
        first.saveInstanceState(saved).pause().stop().destroy()
        val second = Robolectric.buildActivity(AddDestinationActivity::class.java, sheetIntent()).setup(saved)
        assertRestoredOnStep2(second.get())
    }

    // ------------------------------------------------------------------ helpers

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

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
