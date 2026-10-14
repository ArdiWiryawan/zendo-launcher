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
    fun `malformed reads as empty`() {
        assertEquals(emptyList<TaskItem>(), TaskStore.decode(null))
        assertEquals(emptyList<TaskItem>(), TaskStore.decode("broken"))
    }
}
