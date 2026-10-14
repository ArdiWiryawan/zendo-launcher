package com.zendolauncher.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zendolauncher.core.TaskItem
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class TaskStoreCodecTest {

    @Test
    fun `tasks survive a round trip preserving completion`() {
        val items = listOf(
            TaskItem(id = "t1", title = "Review meeting notes", done = true),
            TaskItem(id = "t2", title = "Finish dashboard prototype"),
        )
        val decoded = TaskStore.decode(TaskStore.encode(items))
        assertEquals(listOf("t1", "t2"), decoded.map { it.id })
        assertEquals(true, decoded.first { it.id == "t1" }.done)
    }

    @Test
    fun `notes and due date survive a round trip and stay absent when unset`() {
        val items = listOf(
            TaskItem(id = "t1", title = "With details", dueDateMillis = 1_790_000_000_000L, notes = "Bring the numbers"),
            TaskItem(id = "t2", title = "Plain"),
        )
        val decoded = TaskStore.decode(TaskStore.encode(items))
        val detailed = decoded.first { it.id == "t1" }
        assertEquals(1_790_000_000_000L, detailed.dueDateMillis)
        assertEquals("Bring the numbers", detailed.notes)
        val plain = decoded.first { it.id == "t2" }
        assertEquals(null, plain.dueDateMillis)
        assertEquals("", plain.notes)
    }

    @Test
    fun `old blobs without notes still read`() {
        val raw = """[{"id":"t1","title":"Legacy"}]"""
        val decoded = TaskStore.decode(raw)
        assertEquals("", decoded.single().notes)
        assertEquals(null, decoded.single().dueDateMillis)
    }

    @Test
    fun `malformed reads as empty`() {
        assertEquals(emptyList<TaskItem>(), TaskStore.decode(null))
        assertEquals(emptyList<TaskItem>(), TaskStore.decode("broken"))
    }
}
