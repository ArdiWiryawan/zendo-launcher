package com.zendolauncher.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The day's headline persists, toggles, and refuses blanks. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class TodayActionStoreTest {

    private fun store() = TodayActionStore(ApplicationProvider.getApplicationContext())

    @Test
    fun `save persists across instances and blank is refused`() {
        val first = store()
        assertFalse(first.save("   "))
        assertTrue(first.save("Ship the calm launcher"))
        val second = store()
        assertEquals("Ship the calm launcher", second.readTitleForTest())
        second.setDone(true)
        assertTrue(store().readDoneForTest())
        // Re-saving the same title keeps its done state; a new one resets it.
        second.save("Ship the calm launcher")
        assertTrue(store().readDoneForTest())
        second.save("Something else")
        assertFalse(store().readDoneForTest())
        assertEquals("Something else", store().readTitleForTest())
        second.save("starter")
        second.setDone(false)
    }

    private fun TodayActionStore.readTitleForTest(): String = title.value

    private fun TodayActionStore.readDoneForTest(): Boolean = done.value
}
