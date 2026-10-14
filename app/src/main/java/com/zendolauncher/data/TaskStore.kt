package com.zendolauncher.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.zendolauncher.core.TaskItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistent tasks. Separate file so toggling a task never rewrites settings.
 */
class TaskStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _tasks = MutableStateFlow(read())
    val tasks: StateFlow<List<TaskItem>> = _tasks.asStateFlow()

    fun read(): List<TaskItem> = TaskItem.sanitizeAll(decode(prefs.getString(KEY_ITEMS, null)))

    private fun persist(next: List<TaskItem>) {
        val sanitized = TaskItem.sanitizeAll(next)
        prefs.edit { putString(KEY_ITEMS, encode(sanitized)) }
        _tasks.value = sanitized
    }

    fun add(title: String): TaskItem? {
        val t = title.trim()
        if (t.isEmpty()) return null
        val now = System.currentTimeMillis()
        val item = TaskItem(id = TaskItem.newId(), title = t, createdAt = now, updatedAt = now)
        persist(listOf(item) + _tasks.value)
        return item
    }

    fun toggleDone(id: String) {
        persist(
            _tasks.value.map {
                if (it.id == id) it.copy(done = !it.done, updatedAt = System.currentTimeMillis()) else it
            },
        )
    }

    fun rename(id: String, title: String) {
        val t = title.trim()
        if (t.isEmpty()) return
        persist(
            _tasks.value.map {
                if (it.id == id) it.copy(title = t, updatedAt = System.currentTimeMillis()) else it
            },
        )
    }

    /**
     * Full detail save from the task editor. Blank title is refused (returns false)
     * so the editor stays open with the user's text instead of closing on nothing.
     */
    fun updateDetails(id: String?, title: String, dueDateMillis: Long?, notes: String): TaskItem? {
        val t = title.trim()
        if (t.isEmpty()) return null
        val now = System.currentTimeMillis()
        val current = _tasks.value.toMutableList()
        val result: TaskItem
        if (id != null) {
            val index = current.indexOfFirst { it.id == id }
            if (index < 0) return null
            result = current[index].copy(
                title = t,
                dueDateMillis = dueDateMillis,
                notes = notes.trim(),
                updatedAt = now,
            )
            current[index] = result
        } else {
            result = TaskItem(
                id = TaskItem.newId(),
                title = t,
                createdAt = now,
                updatedAt = now,
                dueDateMillis = dueDateMillis,
                notes = notes.trim(),
            )
            current.add(0, result)
        }
        persist(current)
        return result
    }

    fun delete(id: String) {
        persist(_tasks.value.filterNot { it.id == id })
    }

    fun setShowOnHome(id: String, show: Boolean) {
        persist(_tasks.value.map { if (it.id == id) it.copy(showOnHome = show) else it })
    }

    companion object {
        private const val PREFS = "launcher_tasks"
        private const val KEY_ITEMS = "tasks_json"

        fun encode(items: List<TaskItem>): String {
            val array = JSONArray()
            items.forEach { t ->
                val o = JSONObject()
                    .put("id", t.id)
                    .put("title", t.title)
                    .put("done", t.done)
                    .put("createdAt", t.createdAt)
                    .put("updatedAt", t.updatedAt)
                    .put("showOnHome", t.showOnHome)
                t.dueDateMillis?.let { o.put("dueDateMillis", it) }
                if (t.notes.isNotBlank()) o.put("notes", t.notes)
                array.put(o)
            }
            return array.toString()
        }

        fun decode(raw: String?): List<TaskItem> {
            if (raw.isNullOrBlank()) return emptyList()
            return try {
                val array = JSONArray(raw)
                val out = ArrayList<TaskItem>(array.length())
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    val id = o.optString("id", "")
                    val title = o.optString("title", "")
                    if (id.isBlank() || title.isBlank()) continue
                    out += TaskItem(
                        id = id,
                        title = title,
                        done = o.optBoolean("done", false),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
                        dueDateMillis = if (o.has("dueDateMillis")) o.optLong("dueDateMillis") else null,
                        showOnHome = o.optBoolean("showOnHome", true),
                        notes = o.optString("notes", ""),
                    )
                }
                out
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
