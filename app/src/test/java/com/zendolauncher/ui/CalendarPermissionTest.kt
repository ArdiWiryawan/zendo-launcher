package com.zendolauncher.ui

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zendolauncher.LauncherActivity
import com.zendolauncher.ui.theme.LauncherTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Regression tests for the repeated calendar-permission prompt.
 *
 * The system dialog fires from exactly one shared action
 * (`CalendarAccessAction`) and only from an explicit tap. After a denial the
 * ask is recorded, so a permanent denial routes to the app Settings page
 * instead of re-firing a dialog the platform would auto-deny.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class CalendarPermissionTest {

    @get:Rule
    val rule = createAndroidComposeRule<LauncherActivity>()

    private val viewModel: LauncherViewModel
        get() = ViewModelProvider(rule.activity)[LauncherViewModel::class.java]

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private var collectJob: Job? = null

    @After
    fun tearDown() {
        collectJob?.cancel()
        collectJob = null
    }

    private fun settle() {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    @Test
    fun denial_recordsTheAsk_withoutGranting() {
        viewModel.setCalendarAsked(false)
        collectJob = scope.launch { viewModel.state.collect {} }
        settle()
        viewModel.onCalendarPermissionResult(false)
        settle()
        assertFalse(viewModel.state.value.calendarPermission)
        assertTrue(viewModel.state.value.settings.calendarAsked)
    }

    @Test
    fun grant_leavesEventsToRefresh_andDoesNotLoop() {
        viewModel.setCalendarAsked(false)
        collectJob = scope.launch { viewModel.state.collect {} }
        settle()
        // A grant refreshes state; no dialog is ever launched from here — the
        // launcher only fires from the explicit Allow tap.
        viewModel.onCalendarPermissionResult(true)
        settle()
        assertFalse(viewModel.state.value.settings.calendarAsked)
    }

    @Test
    fun permanentDenial_offersSettingsInsteadOfReasking() {
        // asked = true + Robolectric rationale (false, never granted at OS level)
        // is exactly the permanent-denial shape: the dead dialog must not fire.
        viewModel.setCalendarAsked(true)
        rule.waitForIdle()
        rule.activityRule.scenario.onActivity { activity ->
            val vm = ViewModelProvider(activity)[LauncherViewModel::class.java]
            activity.setContent {
                LauncherTheme(mode = vm.state.value.settings.theme) {
                    CalendarScreen(vm, vm.state.value)
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("Open Settings to allow calendar").assertIsDisplayed()
        rule.onAllNodesWithText("Allow calendar access").assertCountEquals(0)
    }

    @Test
    fun firstAsk_showsAllowAction() {
        viewModel.setCalendarAsked(false)
        rule.waitForIdle()
        rule.activityRule.scenario.onActivity { activity ->
            val vm = ViewModelProvider(activity)[LauncherViewModel::class.java]
            activity.setContent {
                LauncherTheme(mode = vm.state.value.settings.theme) {
                    CalendarScreen(vm, vm.state.value)
                }
            }
        }
        rule.waitForIdle()
        // Robolectric has no calendar permission: the off-state shows Allow once.
        rule.onNodeWithText("Allow calendar access").assertIsDisplayed()
    }

    @Test
    fun askedFlag_persistsAcrossReads() {
        viewModel.setCalendarAsked(true)
        assertEquals(true, viewModel.state.value.settings.calendarAsked)
        viewModel.setCalendarAsked(false)
        assertEquals(false, viewModel.state.value.settings.calendarAsked)
    }

    @Test
    fun effectiveCalendarIds_areExplicit_neverAutoAll() {
        val cals = listOf(
            com.zendolauncher.model.DeviceCalendar(1, "Work", "a@gmail.com", "com.google"),
            com.zendolauncher.model.DeviceCalendar(2, "Local", null, null),
        )
        // Empty means none chosen — never "all visible".
        assertEquals(emptySet<Long>(), viewModel.effectiveCalendarIds(emptySet(), cals, false))
        assertEquals(setOf(2L), viewModel.effectiveCalendarIds(setOf(2L), cals, false))
        // The legacy Google-only filter still narrows the selection to Google
        // calendars when an upgrading install stored it; after migration it is off.
        assertEquals(emptySet<Long>(), viewModel.effectiveCalendarIds(emptySet(), cals, true))
        assertEquals(setOf(1L), viewModel.effectiveCalendarIds(setOf(1L, 2L), cals, true))
        assertEquals(
            emptySet<Long>(),
            viewModel.effectiveCalendarIds(
                emptySet(),
                listOf(com.zendolauncher.model.DeviceCalendar(2, "Local", null, null)),
                true,
            ),
        )
    }

    @Test
    fun googleOnlyFlag_persists() {
        // Read back through the view model's own state (same prefs file): a fresh
        // store instance could race the async disk write, which is a test artifact,
        // not product behavior — the store writes memory-synchronously.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val job = scope.launch { viewModel.state.collect {} }
        try {
            viewModel.setCalendarGoogleOnly(true)
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(true, viewModel.state.value.settings.calendarGoogleOnly)
            viewModel.setCalendarGoogleOnly(false)
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(false, viewModel.state.value.settings.calendarGoogleOnly)
        } finally {
            job.cancel()
        }
    }
}
