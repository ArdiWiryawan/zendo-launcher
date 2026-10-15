package com.zendolauncher.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zendolauncher.core.FocusPhase
import com.zendolauncher.core.FocusSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Focus persistence: active session visible to a fresh instance, history recorded once. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class FocusStorePersistenceTest {

    private fun store() = FocusStore(ApplicationProvider.getApplicationContext())

    @Test
    fun `started session survives a fresh instance and finish records once`() {
        val now = System.currentTimeMillis()
        val first = store()
        first.startWork(25, 5, nowMillis = now)
        val second = store()
        assertEquals(FocusPhase.WORK, second.session.value.phase)
        assertEquals(25, second.session.value.workMinutes)
        second.finish(nowMillis = now + 1_000L)
        assertEquals(FocusPhase.IDLE, second.session.value.phase)
        assertEquals(1, second.history.value.size)
        // Finishing idle records nothing: no double count.
        second.finish(nowMillis = now + 2_000L)
        assertEquals(1, store().history.value.size)
        // Malformed blobs fall back to idle rather than crashing.
        assertEquals(FocusSession(), FocusStore.decodeSession("broken"))
        assertEquals(emptyList<com.zendolauncher.core.FocusRecord>(), FocusStore.decodeHistory("broken"))
        assertTrue(store().history.value.size <= FocusStore.MAX_HISTORY)
        second.clearHistory()
        assertTrue(store().history.value.isEmpty())
    }
}
