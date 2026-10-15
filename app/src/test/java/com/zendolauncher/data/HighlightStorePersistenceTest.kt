package com.zendolauncher.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Store-level persistence: writes through one instance must be readable from a
 * fresh instance (the application-restart path), and archiving or deleting one
 * entry must leave the others intact.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class HighlightStorePersistenceTest {

    private fun store() = HighlightStore(ApplicationProvider.getApplicationContext())

    @Test
    fun `created highlight survives a fresh instance`() {
        val first = store()
        val saved = first.upsert("Persist me", "Long body text", null, true)!!
        val second = store()
        val reread = second.read().firstOrNull { it.id == saved.id }
        assertEquals("Persist me", reread?.title)
        assertEquals("Long body text", reread?.note)
        second.delete(saved.id)
    }

    @Test
    fun `editing keeps one record and archiving one keeps the others`() {
        val s = store()
        val a = s.upsert("Alpha", "", null, true)!!
        val b = s.upsert("Beta note here", "body", null, false)!!
        s.upsert("Alpha edited", "new body", a.id, false)
        assertEquals(2, s.read().size)
        assertEquals("Alpha edited", s.read().first { it.id == a.id }.title)
        s.archive(a.id, true)
        assertEquals(1, s.read().count { !it.archived })
        assertEquals("Beta note here", s.read().first { !it.archived }.title)
        s.archive(a.id, false)
        assertEquals(2, s.read().count { !it.archived })
        s.delete(a.id)
        s.delete(b.id)
        assertTrue(s.read().none { it.id == a.id || it.id == b.id })
    }

    @Test
    fun `blank upsert stores nothing`() {
        val s = store()
        assertNull(s.upsert("  ", "  ", null, false))
    }

    @Test
    fun `a rapid double create stores one record not twins`() {
        val s = store()
        val first = s.upsert("Twice", "", null, false)!!
        val second = s.upsert("Twice", "", null, false)!!
        assertEquals(first.id, second.id)
        assertEquals(1, s.read().count { it.title == "Twice" })
        s.delete(first.id)
    }
}
