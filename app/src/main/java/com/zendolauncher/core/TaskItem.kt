package com.zendolauncher.core

/**
 * A small practical task. Stored locally, never auto-created from calendar
 * events. [showOnHome] lets the user choose which tasks appear in the Home summary.
 * [notes] is optional free text; [dueDateMillis] an optional wall-clock due date.
 */
data class TaskItem(
    val id: String,
    val title: String,
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val dueDateMillis: Long? = null,
    val showOnHome: Boolean = true,
    val notes: String = "",
) {
    companion object {
        fun newId(): String = "t_" + java.util.UUID.randomUUID().toString().replace("-", "")

        fun sanitizeAll(items: List<TaskItem>): List<TaskItem> {
            val seen = HashSet<String>()
            return items.mapNotNull { t ->
                val id = t.id.takeIf { it.isNotBlank() && seen.add(it) } ?: return@mapNotNull null
                val title = t.title.trim()
                if (title.isEmpty()) return@mapNotNull null
                t.copy(id = id, title = title)
            }.sortedWith(compareBy<TaskItem> { it.done }.thenBy { it.createdAt })
        }
    }
}

fun List<TaskItem>.homeSummary(limit: Int = 3): List<TaskItem> =
    filter { it.showOnHome }.sortedWith(compareBy<TaskItem> { it.done }.thenBy { it.createdAt }).take(limit)

fun List<TaskItem>.doneCount(): Int = count { it.done }

fun List<TaskItem>.visibleCount(): Int = count { it.showOnHome }
