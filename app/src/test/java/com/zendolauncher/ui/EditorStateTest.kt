package com.zendolauncher.ui

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zendolauncher.data.HighlightStore
import com.zendolauncher.data.TaskStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * Editor state machine on the real view model: validation keeps the editor
 * open with the draft intact, Back protects unsaved work, and saves land in
 * the persistent stores. Compose and the activity are not involved.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class EditorStateTest {

    /**
     * `state` shares while subscribed, so a plain test with no collector would
     * only ever see the initial value. One eager collector per view model keeps
     * the assertions honest.
     */
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

    /** Runs tasks posted to the main looper so `state` catches up with the stores. */
    private fun settle() {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    private fun cleanHighlights(vararg ids: String) {
        val store = HighlightStore(ApplicationProvider.getApplicationContext())
        ids.forEach { store.delete(it) }
    }

    private fun cleanTasks(vararg ids: String) {
        val store = TaskStore(ApplicationProvider.getApplicationContext())
        ids.forEach { store.delete(it) }
    }

    @Test
    fun `blank highlight refuses to save and keeps the editor open`() {
        val vm = viewModel()
        vm.openHighlightEditor(null)
        vm.updateHighlightDraft("  ", "  ", false)
        assertFalse(vm.saveHighlight())
        settle()
        assertEquals(Screen.HIGHLIGHT_EDITOR, vm.state.value.screen)
        assertTrue(vm.state.value.highlightError != null)
    }

    @Test
    fun `note-only highlight derives its title and saves`() {
        val vm = viewModel()
        vm.openHighlightEditor(null)
        vm.updateHighlightDraft("", "Ship the calm launcher", false)
        assertTrue(vm.saveHighlight())
        settle()
        assertEquals(Screen.HOME, vm.state.value.screen)
        val saved = vm.state.value.highlights.firstOrNull { it.note == "Ship the calm launcher" }
        assertEquals("Ship the calm launcher", saved?.title)
        cleanHighlights(saved!!.id)
    }

    @Test
    fun `back with unsaved highlight asks before discarding`() {
        val vm = viewModel()
        vm.openHighlightEditor(null)
        vm.updateHighlightDraft("Draft", "", false)
        vm.backFromHighlightEditor()
        settle()
        assertEquals(Screen.HIGHLIGHT_EDITOR, vm.state.value.screen)
        assertTrue(vm.state.value.showDiscardHighlight)
        // Back again dismisses the confirm, not the editor: the draft survives.
        assertTrue(vm.back())
        settle()
        assertEquals(Screen.HIGHLIGHT_EDITOR, vm.state.value.screen)
        assertFalse(vm.state.value.showDiscardHighlight)
        assertEquals("Draft", vm.state.value.highlightTitle)
        vm.discardHighlight()
        settle()
        assertEquals(Screen.HOME, vm.state.value.screen)
        assertEquals("", vm.state.value.highlightTitle)
    }

    @Test
    fun `editing an existing highlight does not duplicate it`() {
        val vm = viewModel()
        settle()
        val before = vm.state.value.highlights.size
        vm.openHighlightEditor(null)
        vm.updateHighlightDraft("Original", "", false)
        assertTrue(vm.saveHighlight())
        settle()
        val id = vm.state.value.highlights.first { it.title == "Original" }.id
        vm.openHighlightEditor(id)
        vm.updateHighlightDraft("Original edited", "body", false)
        assertTrue(vm.saveHighlight())
        settle()
        assertEquals(before + 1, vm.state.value.highlights.size)
        assertEquals("body", vm.state.value.highlights.first { it.id == id }.note)
        cleanHighlights(id)
    }

    @Test
    fun `blank task refuses to save and quick add ignores blanks`() {
        val vm = viewModel()
        vm.openTaskEditor(null)
        vm.updateTaskDraft("   ", null, "")
        assertFalse(vm.saveTask())
        settle()
        assertEquals(Screen.TASK_EDITOR, vm.state.value.screen)
        assertTrue(vm.state.value.taskEditorError != null)
        assertNull(vm.addTask("   "))
        vm.discardTask()
        settle()
    }

    @Test
    fun `task with details saves and survives a toggle round trip`() {
        val vm = viewModel()
        vm.openTaskEditor(null)
        vm.updateTaskDraft("Prototype", 1_790_000_000_000L, "Bring numbers")
        assertTrue(vm.saveTask())
        settle()
        assertEquals(Screen.HOME, vm.state.value.screen)
        val saved = vm.state.value.tasks.first { it.title == "Prototype" }
        assertEquals(1_790_000_000_000L, saved.dueDateMillis)
        assertEquals("Bring numbers", saved.notes)
        vm.toggleTask(saved.id)
        settle()
        assertTrue(vm.state.value.tasks.first { it.id == saved.id }.done)
        vm.toggleTask(saved.id)
        settle()
        assertFalse(vm.state.value.tasks.first { it.id == saved.id }.done)
        cleanTasks(saved.id)
    }

    @Test
    fun `back from clean editors returns home and claims nothing on plain home`() {
        val vm = viewModel()
        vm.openHighlightEditor(null)
        settle()
        assertTrue(vm.back())
        settle()
        assertEquals(Screen.HOME, vm.state.value.screen)
        vm.openTaskEditor(null)
        settle()
        assertTrue(vm.back())
        settle()
        assertEquals(Screen.HOME, vm.state.value.screen)
        assertFalse(vm.back())
    }
}
