package com.zendolauncher.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zendolauncher.LauncherActivity
import com.zendolauncher.model.LaunchTarget
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The access window as it is actually wired: through [LauncherViewModel.launch], against
 * the real settings store and a pinned clock. [AccessWindowTest] covers the arithmetic on
 * its own; this covers the gate, the store keys behind it, and the pause dialog it shares.
 *
 * The activity's own view model is used rather than a hand-built one because
 * [LauncherViewModel.state] is a `stateIn(WhileSubscribed)`: it only folds settings into
 * `UiState` while something collects it, and only the running UI does. A view model with
 * no collector would write the store correctly and still report a stale default.
 *
 * [LauncherViewModel.nowMinuteOfDay] exists as an internal seam precisely so this can pin
 * the time. Nothing in the app assigns it, so the gate under test is the shipped one.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class AccessWindowGateTest {

    @Suppress("DEPRECATION")
    @get:Rule
    val rule = createAndroidComposeRule<LauncherActivity>()

    private val vm: LauncherViewModel
        get() = ViewModelProvider(rule.activity)[LauncherViewModel::class.java]

    private fun target(pkg: String = "com.example.notes") =
        LaunchTarget(packageName = pkg, activityName = null, label = "Notes")

    /** Places the launcher at a fixed minute-of-day. */
    private fun atMinute(minute: Int) {
        vm.nowMinuteOfDay = { minute }
    }

    @Before
    fun setUp() {
        // Settings live in SharedPreferences, which Robolectric keeps for the whole JVM,
        // so each case puts the store back to the shipped defaults rather than assuming.
        vm.setPauseEnabled(false)
        vm.setWindowEnabled(false)
        vm.setWindowStart(22 * 60)
        vm.setWindowEnd(2 * 60)
        vm.state.value.settings.distract.toList().forEach { vm.toggleDistract(it) }
        vm.cancelPause()
        atMinute(12 * 60)
        rule.waitForIdle()
    }

    @After
    fun tearDown() {
        vm.setPauseEnabled(false)
        vm.setWindowEnabled(false)
        vm.cancelPause()
        rule.waitForIdle()
    }

    /** Distraction-lists the target and turns the pause on; the window is set per test. */
    private fun armPauseFor(target: LaunchTarget) {
        vm.toggleDistract(target.key)
        vm.setPauseEnabled(true)
        rule.waitForIdle()
    }

    // --- Inside the window: passes through -------------------------------------------

    @Test
    fun insideWindow_launchesWithoutPausing() {
        val t = target()
        armPauseFor(t)
        vm.setWindowEnabled(true)
        vm.setWindowStart(9 * 60)
        vm.setWindowEnd(17 * 60)
        atMinute(12 * 60)

        vm.launch(t)
        rule.waitForIdle()

        assertNull("an enabled window lets the app straight through", vm.state.value.pendingPause)
    }

    @Test
    fun wrapAroundWindow_lateNight_launchesWithoutPausing() {
        val t = target()
        armPauseFor(t)
        vm.setWindowEnabled(true)
        vm.setWindowStart(22 * 60)
        vm.setWindowEnd(2 * 60)
        atMinute(23 * 60 + 30)

        vm.launch(t)
        rule.waitForIdle()

        assertNull(vm.state.value.pendingPause)
    }

    @Test
    fun wrapAroundWindow_afterMidnight_launchesWithoutPausing() {
        val t = target()
        armPauseFor(t)
        vm.setWindowEnabled(true)
        vm.setWindowStart(22 * 60)
        vm.setWindowEnd(2 * 60)
        atMinute(1 * 60 + 15)

        vm.launch(t)
        rule.waitForIdle()

        assertNull(
            "the after-midnight half of the window counts as inside",
            vm.state.value.pendingPause,
        )
    }

    // --- Outside the window: pauses ---------------------------------------------------

    @Test
    fun outsideWindow_pauses() {
        val t = target()
        armPauseFor(t)
        vm.setWindowEnabled(true)
        vm.setWindowStart(22 * 60)
        vm.setWindowEnd(2 * 60)
        atMinute(9 * 60)

        vm.launch(t)
        rule.waitForIdle()

        assertEquals(t, vm.state.value.pendingPause)
    }

    @Test
    fun outsideWindow_wrapAroundBoundary_justPastEnd_pauses() {
        val t = target()
        armPauseFor(t)
        vm.setWindowEnabled(true)
        vm.setWindowStart(22 * 60)
        vm.setWindowEnd(2 * 60)
        // 02:00 is the exclusive end, so 02:01 is outside a window that has just closed.
        atMinute(2 * 60 + 1)

        vm.launch(t)
        rule.waitForIdle()

        assertEquals(t, vm.state.value.pendingPause)
    }

    // --- A disabled window never pauses -----------------------------------------------

    @Test
    fun disabledWindow_neverPauses_whateverTheTime() {
        val t = target()
        armPauseFor(t)
        vm.setWindowEnabled(false)
        vm.setWindowStart(9 * 60)
        vm.setWindowEnd(10 * 60)

        // Once from inside the configured window, once from far outside it: with the
        // toggle off, neither the stored times nor the clock decide anything.
        listOf(9 * 60 + 30, 3 * 60).forEach { minute ->
            atMinute(minute)
            vm.cancelPause()
            vm.launch(t)
            rule.waitForIdle()
            assertEquals(
                "with the window off the time is irrelevant ($minute)",
                t,
                vm.state.value.pendingPause,
            )
        }
    }

    @Test
    fun windowEnabled_butPauseOff_neverPauses() {
        val t = target()
        vm.toggleDistract(t.key)
        vm.setPauseEnabled(false)
        vm.setWindowEnabled(true)
        atMinute(3 * 60)
        rule.waitForIdle()

        vm.launch(t)
        rule.waitForIdle()

        assertNull(
            "the window narrows the pause; it does not replace it",
            vm.state.value.pendingPause,
        )
    }

    @Test
    fun windowEnabled_butAppNotListed_neverPauses() {
        val t = target()
        vm.setPauseEnabled(true)
        vm.setWindowEnabled(true)
        atMinute(3 * 60)
        rule.waitForIdle()

        vm.launch(t)
        rule.waitForIdle()

        assertNull("an unlisted app is never gated by the window", vm.state.value.pendingPause)
    }

    // --- The dialog it shares still works ---------------------------------------------

    @Test
    fun pausedByWindow_confirmLaunchesAndClears() {
        val t = target()
        armPauseFor(t)
        vm.setWindowEnabled(true)
        vm.setWindowStart(22 * 60)
        vm.setWindowEnd(2 * 60)
        atMinute(9 * 60)

        vm.launch(t)
        rule.waitForIdle()
        assertEquals(t, vm.state.value.pendingPause)

        vm.confirmPause()
        rule.waitForIdle()
        assertNull(vm.state.value.pendingPause)
    }

    // --- The store keys behind the UI -------------------------------------------------

    @Test
    fun windowSettings_roundTripThroughTheStore() {
        vm.setWindowEnabled(true)
        vm.setWindowStart(6 * 60 + 30)
        vm.setWindowEnd(23 * 60 + 45)
        rule.waitForIdle()

        val settings = vm.state.value.settings
        assertTrue(settings.windowEnabled)
        assertEquals(6 * 60 + 30, settings.windowStartMinute)
        assertEquals(23 * 60 + 45, settings.windowEndMinute)
    }

    @Test
    fun windowTimes_clampToTheDayRatherThanStoringAnImpossibleTime() {
        vm.setWindowStart(-30)
        rule.waitForIdle()
        assertEquals(0, vm.state.value.settings.windowStartMinute)

        vm.setWindowEnd(AccessWindow.MINUTES_PER_DAY + 60)
        rule.waitForIdle()
        assertEquals(
            AccessWindow.MINUTES_PER_DAY - 1,
            vm.state.value.settings.windowEndMinute,
        )
    }
}
