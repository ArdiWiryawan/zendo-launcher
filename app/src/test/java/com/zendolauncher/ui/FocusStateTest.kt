package com.zendolauncher.ui

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zendolauncher.core.FocusPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/** Focus timer through the real view model: phases move, history counts once. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class FocusStateTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private var collectJob: Job? = null

    @After
    fun stopCollecting() {
        collectJob?.cancel()
        collectJob = null
    }

    private fun viewModel(): LauncherViewModel {
        val vm = LauncherViewModel(ApplicationProvider.getApplicationContext())
        collectJob?.cancel()
        collectJob = scope.launch { vm.state.collect {} }
        settle()
        return vm
    }

    private fun settle() {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    @Test
    fun `start pause resume finish records exactly one session`() {
        val vm = viewModel()
        val historyBefore = vm.state.value.focusHistory.size
        vm.startFocus(25, 5)
        settle()
        assertEquals(FocusPhase.WORK, vm.state.value.focusSession.phase)
        vm.pauseFocus()
        settle()
        assertEquals(FocusPhase.PAUSED, vm.state.value.focusSession.phase)
        vm.resumeFocus()
        settle()
        assertEquals(FocusPhase.WORK, vm.state.value.focusSession.phase)
        vm.finishFocus()
        settle()
        assertEquals(FocusPhase.IDLE, vm.state.value.focusSession.phase)
        assertEquals(historyBefore + 1, vm.state.value.focusHistory.size)
        // Finishing idle records nothing more.
        vm.finishFocus()
        settle()
        assertEquals(historyBefore + 1, vm.state.value.focusHistory.size)
        vm.clearFocusHistory()
        settle()
    }

    @Test
    fun `today action saves toggles and refuses blanks`() {
        val vm = viewModel()
        vm.editTodayAction()
        vm.updateTodayActionDraft("   ")
        assertEquals(false, vm.saveTodayAction())
        settle()
        assertEquals(true, vm.state.value.editingTodayAction)
        vm.updateTodayActionDraft("Headline")
        assertEquals(true, vm.saveTodayAction())
        settle()
        assertEquals("Headline", vm.state.value.todayActionTitle)
        vm.toggleTodayAction()
        settle()
        assertEquals(true, vm.state.value.todayActionDone)
        vm.toggleTodayAction()
        settle()
        assertEquals(false, vm.state.value.todayActionDone)
    }

    @Test
    fun `setup finishes once and back from fresh setup skips`() {
        val vm = viewModel()
        vm.dismissOnboarding()
        settle()
        assertEquals(Screen.SETUP, vm.state.value.screen)
        vm.backFromSetup()
        settle()
        assertEquals(Screen.HOME, vm.state.value.screen)
        assertTrue(vm.state.value.settings.onboardingSeen)
    }
}
