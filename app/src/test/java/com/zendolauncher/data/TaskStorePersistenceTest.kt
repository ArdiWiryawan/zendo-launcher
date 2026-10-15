package com.zendolauncher.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Task persistence across instances plus the detail-save validation. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class TaskStorePersistenceTest {

    private fun store() = TaskStore(ApplicationProvider.getApplicationContext())

    @Test
    fun `added task survives a fresh instance with completion intact`() {
        val first = store()
        val saved = first.add("Persist this task")!!
        first.toggleDone(saved.id)
        val second = store()
        val reread = second.read().firstOrNull { it.id == saved.id }
        assertEquals("Persist this task", reread?.title)
        assertEquals(true, reread?.done)
        second.delete(saved.id)
    }

    @Test
    fun `detail save persists due date and notes and refuses a blank title`() {
        val s = store()
        assertNull(s.updateDetails(null, "   ", null, ""))
        val saved = s.updateDetails(null, "Prototype", 1_790_000_000_000L, "Bring numbers")!!
        val reread = store().read().first { it.id == saved.id }
        assertEquals(1_790_000_000_000L, reread.dueDateMillis)
        assertEquals("Bring numbers", reread.notes)
        assertNull(s.updateDetails(saved.id, "  ", null, ""))
        assertEquals("Prototype", store().read().first { it.id == saved.id }.title)
        s.delete(saved.id)
    }

    @Test
    fun `a rapid double quick-add stores one task not twins`() {
        val s = store()
        val first = s.add("Buy milk")!!
        val second = s.add("Buy milk")!!
        assertEquals(first.id, second.id)
        assertEquals(1, s.read().count { it.title == "Buy milk" })
        assertTrue(s.add("Buy bread")!!.id != first.id)
        s.delete(first.id)
        s.read().firstOrNull { it.title == "Buy bread" }?.let { s.delete(it.id) }
    }
}
