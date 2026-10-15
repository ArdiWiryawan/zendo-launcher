package com.zendolauncher.core

import java.time.LocalDate

/** Shared text cap for user-authored titles written into history and snapshots. */
const val MAX_STORED_TITLE = 500

/**
 * One task as it stood on a recorded day. [order] is the position in the list at
 * capture time, so a day reads back exactly as it was shown even when timestamps
 * are odd (imported records carry their own creation times).
 */
data class DayTaskRecord(
    val id: String,
    val title: String,
    val done: Boolean,
    val createdAt: Long,
    val order: Int,
    val dueDateMillis: Long? = null,
    val origin: String = Origins.LOCAL,
    val sourceId: String? = null,
) {
    companion object {
        fun from(task: TaskItem, order: Int): DayTaskRecord = DayTaskRecord(
            id = task.id,
            title = task.title,
            done = task.done,
            createdAt = task.createdAt,
            order = order,
            dueDateMillis = task.dueDateMillis,
            origin = Origins.normalize(task.origin),
            sourceId = task.sourceId?.trim()?.takeIf { it.isNotEmpty() },
        )
    }
}

/**
 * A day in the launcher's local history, keyed by local date and a stable id
 * derived from it.
 *
 * The record for *today* keeps being rewritten as the day goes on — it is the
 * live day. Once the local date moves on, nothing rewrites it again: that is
 * what keeps yesterday intact while today is edited. There is no midnight job;
 * a past day simply stops being the capture target.
 */
data class DayRecord(
    val id: String,
    val date: LocalDate,
    val actionTitle: String = "",
    val actionDone: Boolean = false,
    val highlightText: String = "",
    val tasks: List<DayTaskRecord> = emptyList(),
    val origin: String = Origins.LOCAL,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val isEmpty: Boolean
        get() = actionTitle.isBlank() && highlightText.isBlank() && tasks.isEmpty()

    val doneCount: Int get() = tasks.count { it.done }

    /** True when every field except [updatedAt] matches — used to skip no-op writes. */
    fun sameContentAs(other: DayRecord): Boolean = copy(updatedAt = other.updatedAt) == other

    companion object {
        /** Oldest records beyond this are dropped so the file cannot grow forever. */
        const val MAX_DAYS = 400

        private const val MAX_TITLE = MAX_STORED_TITLE

        fun idFor(date: LocalDate): String = "d_$date"

        /** Snapshot of the live shapes as they stand right now. */
        fun capture(
            date: LocalDate,
            actionTitle: String,
            actionDone: Boolean,
            highlightText: String,
            tasks: List<TaskItem>,
            origin: String = Origins.LOCAL,
            now: Long = System.currentTimeMillis(),
        ): DayRecord = DayRecord(
            id = idFor(date),
            date = date,
            actionTitle = actionTitle.trim().take(MAX_TITLE),
            actionDone = actionDone,
            highlightText = highlightText.trim().take(MAX_TITLE),
            tasks = tasks.mapIndexed { index, task -> DayTaskRecord.from(task, index) },
            origin = Origins.normalize(origin),
            updatedAt = now,
        )

        /** Repairs a list: one record per date (newest write wins), id re-derived, capped. */
        fun sanitizeAll(records: List<DayRecord>): List<DayRecord> {
            val byDate = LinkedHashMap<LocalDate, DayRecord>()
            records.forEach { raw ->
                // Tolerant floor/ceiling: a garbled timestamp cannot pin a record to year 0.
                if (raw.date.year < 2000 || raw.date.year > 2200) return@forEach
                val record = raw.repaired()
                val existing = byDate[record.date]
                if (existing == null || record.updatedAt >= existing.updatedAt) {
                    byDate[record.date] = record
                }
            }
            return byDate.values.sortedByDescending { it.date }.take(MAX_DAYS)
        }

        private fun DayRecord.repaired(): DayRecord {
            val seen = HashSet<String>()
            val tasks = tasks
                .filter { it.id.isNotBlank() && it.title.isNotBlank() && seen.add(it.id) }
                .mapIndexed { index, task ->
                    task.copy(
                        title = task.title.trim().take(MAX_TITLE),
                        order = index,
                        origin = Origins.normalize(task.origin),
                        sourceId = task.sourceId?.trim()?.takeIf { it.isNotEmpty() },
                    )
                }
            return copy(
                id = idFor(date),
                actionTitle = actionTitle.trim().take(MAX_TITLE),
                highlightText = highlightText.trim().take(MAX_TITLE),
                tasks = tasks,
                origin = Origins.normalize(origin),
            )
        }
    }
}
