package com.zendolauncher.core

import java.time.LocalDate
import java.time.ZoneId

/**
 * A small practical task. Stored locally, never auto-created from calendar
 * events. [showOnHome] lets the user choose which tasks appear in the Home summary.
 * [notes] is optional free text; [dueDateMillis] an optional wall-clock due date.
 *
 * [origin] is text ("local" or "zendo-pwa") and [sourceId] is the id the Zendo
 * PWA gave a task, kept so re-importing a snapshot updates instead of duplicating.
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
    val origin: String = Origins.LOCAL,
    val sourceId: String? = null,
) {
    companion object {
        fun newId(): String = "t_" + java.util.UUID.randomUUID().toString().replace("-", "")

        fun sanitizeAll(items: List<TaskItem>): List<TaskItem> {
            val seen = HashSet<String>()
            return items.mapNotNull { t ->
                val id = t.id.takeIf { it.isNotBlank() && seen.add(it) } ?: return@mapNotNull null
                val title = t.title.trim()
                if (title.isEmpty()) return@mapNotNull null
                t.copy(
                    id = id,
                    title = title,
                    origin = Origins.normalize(t.origin),
                    sourceId = t.sourceId?.trim()?.takeIf { it.isNotEmpty() },
                )
            }.sortedWith(compareBy<TaskItem> { it.done }.thenBy { it.createdAt })
        }
    }
}

fun List<TaskItem>.homeSummary(limit: Int = 3): List<TaskItem> =
    filter { it.showOnHome }.sortedWith(compareBy<TaskItem> { it.done }.thenBy { it.createdAt }).take(limit)

fun List<TaskItem>.doneCount(): Int = count { it.done }

fun List<TaskItem>.visibleCount(): Int = count { it.showOnHome }

/** The four task views. Mapped onto existing states — no new model. */
enum class TaskView { TODAY, UPCOMING, ALL, DONE }

/** True when the due date passed and the task is still open. Quiet word, no alarm. */
fun TaskItem.isOverdue(today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Boolean {
    val due = dueDateMillis ?: return false
    if (done) return false
    return due < today.atStartOfDay(zone).toInstant().toEpochMilli()
}

/** "Overdue · 3d" style readout, or null when not overdue. Pure for tests. */
fun TaskItem.overdueLabel(today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): String? {
    val due = dueDateMillis ?: return null
    if (done) return null
    val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
    if (due >= start) return null
    val days = ((start - due) / 86_400_000L).toInt() + 1
    return if (days <= 1) "Overdue" else "Overdue · ${days}d"
}

/** Splits tasks into the four views. Order inside a view is undone-first. */
fun List<TaskItem>.forView(
    view: TaskView,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
): List<TaskItem> {
    val endOfToday = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val ordered = sortedWith(compareBy<TaskItem> { it.done }.thenBy { it.createdAt })
    return when (view) {
        TaskView.TODAY -> ordered.filter { !it.done && it.dueDateMillis != null && it.dueDateMillis < endOfToday }
        TaskView.UPCOMING -> ordered.filter { !it.done && it.dueDateMillis != null && it.dueDateMillis >= endOfToday }
        TaskView.ALL -> ordered
        TaskView.DONE -> ordered.filter { it.done }
    }
}
