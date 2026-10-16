package io.github.dreadboy.navigationshortcuts.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pre-MVP E2E (PLAN 1.8 + 2.2): real Niagara home screen, real widget placement, real taps, real Google Maps.
 * Device preparation (Niagara as home, onboarding, Maps first-run dialogs, mock location): e2e/setup-emulator.sh,
 * called by e2e/run-e2e.sh.
 */
@RunWith(AndroidJUnit4::class)
class PreMvpWidgetTest {

    private val d = E2eDevice()
    private val niagara = Niagara(d)
    private val maps = GoogleMaps(d)

    @Before
    fun setUp() {
        niagara.assertIsDefaultHome()
        maps.forceStop()
        niagara.removeCustomWidgetIfPresent()
    }

    @After
    fun tearDown() {
        maps.forceStop()
    }

    /** Harness smoke test: the place/remove helpers work and leave Niagara's slot empty. */
    @Test
    fun harness_placeFindAndRemoveWidget() {
        niagara.addOurWidget()
        niagara.cell(E2e.PRE_MVP_LABEL)

        niagara.removeCustomWidgetIfPresent()
        assertNull("custom widget still placed after Remove", niagara.placedWidget())
    }

    /** Spec scenario 2 (single cell) and scenario 5 (survives process death). */
    @Test
    fun tapStartsNavigation_againAfterMapsForceStop_andAfterAppProcessDeath() {
        niagara.addOurWidget()

        // 1. First tap on the freshly placed widget.
        niagara.cell(E2e.PRE_MVP_LABEL).click()
        maps.assertNavigationInForeground()

        // 2. HOME, end navigation, tap again.
        d.ui.pressHome()
        maps.forceStop()
        niagara.goHome()
        niagara.cell(E2e.PRE_MVP_LABEL).click()
        maps.assertNavigationInForeground()

        // 3. Process death of our app (what the system does to a cached process): the widget keeps
        //    rendering its cell and a tap still launches navigation.
        maps.forceStop()
        niagara.goHome()
        killAppProcess()
        niagara.cell(E2e.PRE_MVP_LABEL).click()
        maps.assertNavigationInForeground()
    }

    /**
     * Spec scenario 5 with `am force-stop` (the user's "Force stop" in App info). Since Android 15 the
     * system masks widgets of a force-stopped (stopped-state) app: the host shows the framework placeholder
     * (app icon, `android:id/work_widget_app_icon`) instead of our RemoteViews. The first tap only takes the
     * app out of the stopped state (onUpdate re-renders the cell); the next tap navigates. See decisions 1.3.
     */
    @Test
    fun afterAppForceStop_widgetIsMaskedUntilFirstTap_thenTapStartsNavigation() {
        niagara.addOurWidget()
        niagara.cell(E2e.PRE_MVP_LABEL)

        d.shell("am force-stop ${E2e.APP}")
        niagara.goHome()
        val mask = d.waitFor(
            By.pkg(E2e.NIAGARA).res("android", "work_widget_app_icon"),
            E2e.LONG,
            "system mask over the force-stopped app's widget",
        )
        mask.click()

        niagara.cell(E2e.PRE_MVP_LABEL).click()
        maps.assertNavigationInForeground()
    }

    /** Kills the app's process without putting it into the stopped state (unlike `am force-stop`). */
    private fun killAppProcess() {
        d.shell("am kill ${E2e.APP}")
        val pid = d.shell("pidof ${E2e.APP}").trim()
        assertEquals("app process still alive after am kill", "", pid)
    }
}
