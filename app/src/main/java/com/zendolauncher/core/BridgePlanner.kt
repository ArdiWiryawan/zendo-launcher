package com.zendolauncher.core

import java.time.LocalDate
import java.time.ZoneId

enum class ImportMode { MERGE, REPLACE_IMPORTED }

/** Counts shown in the import preview — computed from the same plan that applies. */
data class ImportSummary(
    val addedTasks: Int = 0,
    val updatedTasks: Int = 0,
    val removedImportedTasks: Int = 0,
    val keptLocalTasks: Int = 0,
    val actionSet: Boolean = false,
    val highlightAdded: Boolean = false,
    val highlightUpdated: Boolean = false,
) {
    val hasTaskChange: Boolean
        get() = addedTasks > 0 || updatedTasks > 0 || removedImportedTasks > 0

    val hasAnyChange: Boolean
        get() = hasTaskChange || actionSet || highlightAdded || highlightUpdated
}

/**
 * What one snapshot would change, already decided.
 *
 * The preview dialog shows [summary]; the apply step writes exactly the same
 * lists. [actionTitle], [highlights] and [tasks] are null when that part is
 * untouched, so "nothing to do" never rewrites anything.
 */
data class BridgePlan(
    val targetDate: LocalDate,
    val isToday: Boolean,
    val mode: ImportMode,
    val actionTitle: String? = null,
    val highlights: List<Highlight>? = null,
    val tasks: List<TaskItem>? = null,
    val dayRecord: DayRecord? = null,
    val summary: ImportSummary = ImportSummary(),
)

/**
 * Pure merge planner for the manual Zendo bridge.
 *
 * Rules, in one place so the preview and the write cannot disagree:
 *  - Fill what is empty; update what a previous import wrote; never overwrite
 *    text the user typed locally.
 *  - Tasks are matched by their stable Zendo id, so re-importing the same file
 *    updates instead of duplicating.
 *  - REPLACE_IMPORTED first removes everything a previous import wrote (for the
 *    target date) and then merges — it never removes local records.
 *  - A snapshot for a past date lands only in that day's history record; today's
 *    live data is not touched. A snapshot for today lands in the live stores and
 *    is therefore captured into today's history as the day goes on.
 */
object BridgePlanner {

    fun plan(
        snapshot: BridgeSnapshot,
        today: LocalDate,
        mode: ImportMode,
        zone: ZoneId = ZoneId.systemDefault(),
        now: Long = System.currentTimeMillis(),
        currentActionTitle: String = "",
        currentActionOrigin: String = Origins.LOCAL,
        currentHighlights: List<Highlight> = emptyList(),
        currentTasks: List<TaskItem> = emptyList(),
        currentDay: DayRecord? = null,
    ): BridgePlan {
        return if (snapshot.localDate == today) {
            planToday(
                snapshot, mode, zone, now,
                currentActionTitle, currentActionOrigin, currentHighlights, currentTasks,
            )
        } else {
            planPast(snapshot, mode, zone, now, currentDay)
        }
    }

    private fun planToday(
        snapshot: BridgeSnapshot,
        mode: ImportMode,
        zone: ZoneId,
        now: Long,
        currentActionTitle: String,
        currentActionOrigin: String,
        currentHighlights: List<Highlight>,
        currentTasks: List<TaskItem>,
    ): BridgePlan {
        // ---- Action: fill when blank, refresh what a previous import wrote, keep local text.
        val incomingAction = snapshot.action?.title?.trim()?.takeIf { it.isNotBlank() }
        val keepLocalAction = currentActionTitle.isNotBlank() && !Origins.isImported(currentActionOrigin)
        val actionTitle = incomingAction?.takeIf { !keepLocalAction && it != currentActionTitle }

        // ---- Highlights: matched by source id; added only when the day has none visible.
        var highlights = currentHighlights
        var removedImportedHighlight = false
        if (mode == ImportMode.REPLACE_IMPORTED) {
            val kept = highlights.filterNot { Origins.isImported(it.origin) }
            removedImportedHighlight = kept.size != highlights.size
            highlights = kept
        }
        var highlightAdded = false
        var highlightUpdated = false
        val incomingHighlight = snapshot.highlight?.text?.trim()?.takeIf { it.isNotBlank() }
        if (incomingHighlight != null) {
            val sourceId = snapshot.highlight.id?.trim()?.takeIf { it.isNotEmpty() }
            val bySource = sourceId?.let { sid -> highlights.firstOrNull { it.sourceId == sid } }
            when {
                bySource != null -> if (bySource.title != incomingHighlight) {
                    highlights = highlights.map {
                        if (it.id == bySource.id) {
                            it.copy(title = incomingHighlight, updatedAt = now)
                        } else {
                            it
                        }
                    }
                    highlightUpdated = true
                }

                highlights.none { !it.archived } -> {
                    highlights = highlights + Highlight(
                        id = Highlight.newId(),
                        title = incomingHighlight,
                        createdAt = now,
                        updatedAt = now,
                        isPrimary = true,
                        origin = Origins.ZENDO_PWA,
                        sourceId = sourceId,
                    )
                    highlightAdded = true
                }
                // A local highlight already holds the day. Keep it.
            }
        }
        val highlightsChanged = removedImportedHighlight || highlightAdded || highlightUpdated

        // ---- Tasks: upsert by stable source id; local tasks are never touched.
        var tasks = currentTasks
        var removedImportedTasks = 0
        if (mode == ImportMode.REPLACE_IMPORTED) {
            val kept = tasks.filterNot { Origins.isImported(it.origin) }
            removedImportedTasks = tasks.size - kept.size
            tasks = kept
        }
        var added = 0
        var updated = 0
        snapshot.tasks.forEachIndexed { index, incoming ->
            val dueMillis = dueMillis(incoming, zone)
            val existing = tasks.firstOrNull { !it.sourceId.isNullOrBlank() && it.sourceId == incoming.id }
            if (existing != null) {
                if (existing.title != incoming.title ||
                    existing.done != incoming.done ||
                    existing.dueDateMillis != dueMillis
                ) {
                    tasks = tasks.map {
                        if (it.id == existing.id) {
                            it.copy(
                                title = incoming.title,
                                done = incoming.done,
                                dueDateMillis = dueMillis,
                                updatedAt = now,
                            )
                        } else {
                            it
                        }
                    }
                    updated++
                }
            } else {
                tasks = tasks + TaskItem(
                    id = TaskItem.newId(),
                    title = incoming.title,
                    done = incoming.done,
                    // Monotonic stamp keeps the snapshot's own order among new arrivals.
                    createdAt = now + index,
                    updatedAt = now,
                    dueDateMillis = dueMillis,
                    showOnHome = true,
                    origin = Origins.ZENDO_PWA,
                    sourceId = incoming.id,
                )
                added++
            }
        }
        val tasksChanged = (mode == ImportMode.REPLACE_IMPORTED && removedImportedTasks > 0) ||
            added > 0 || updated > 0

        return BridgePlan(
            targetDate = snapshot.localDate,
            isToday = true,
            mode = mode,
            actionTitle = actionTitle,
            highlights = highlights.takeIf { highlightsChanged },
            tasks = tasks.takeIf { tasksChanged },
            summary = ImportSummary(
                addedTasks = added,
                updatedTasks = updated,
                removedImportedTasks = removedImportedTasks,
                keptLocalTasks = tasks.count { !Origins.isImported(it.origin) },
                actionSet = actionTitle != null,
                highlightAdded = highlightAdded,
                highlightUpdated = highlightUpdated,
            ),
        )
    }

    private fun planPast(
        snapshot: BridgeSnapshot,
        mode: ImportMode,
        zone: ZoneId,
        now: Long,
        currentDay: DayRecord?,
    ): BridgePlan {
        val base = currentDay ?: DayRecord(
            id = DayRecord.idFor(snapshot.localDate),
            date = snapshot.localDate,
            updatedAt = now,
        )
        val recordIsImported = Origins.isImported(base.origin)

        // ---- Action text of the recorded day.
        var actionTitle = base.actionTitle
        var actionDone = base.actionDone
        var actionSet = false
        val incomingAction = snapshot.action?.title?.trim()?.takeIf { it.isNotBlank() }
        if (incomingAction != null && (base.actionTitle.isBlank() || recordIsImported)) {
            if (actionTitle != incomingAction) {
                actionTitle = incomingAction
                actionDone = false
                actionSet = true
            }
        }

        // ---- Highlight text of the recorded day.
        var highlightText = base.highlightText
        var highlightSet = false
        val incomingHighlight = snapshot.highlight?.text?.trim()?.takeIf { it.isNotBlank() }
        if (incomingHighlight != null && (base.highlightText.isBlank() || recordIsImported)) {
            if (highlightText != incomingHighlight) {
                highlightText = incomingHighlight
                highlightSet = true
            }
        }

        // ---- Tasks of the recorded day.
        var tasks = base.tasks
        var removedImported = 0
        if (mode == ImportMode.REPLACE_IMPORTED) {
            val kept = tasks.filterNot { Origins.isImported(it.origin) }
            removedImported = tasks.size - kept.size
            tasks = kept
        }
        var added = 0
        var updated = 0
        snapshot.tasks.forEachIndexed { index, incoming ->
            val dueMillis = dueMillis(incoming, zone)
            val existing = tasks.firstOrNull { !it.sourceId.isNullOrBlank() && it.sourceId == incoming.id }
            if (existing != null) {
                if (existing.title != incoming.title ||
                    existing.done != incoming.done ||
                    existing.dueDateMillis != dueMillis
                ) {
                    tasks = tasks.map {
                        if (it.id == existing.id) {
                            it.copy(title = incoming.title, done = incoming.done, dueDateMillis = dueMillis)
                        } else {
                            it
                        }
                    }
                    updated++
                }
            } else {
                tasks = tasks + DayTaskRecord(
                    id = importedTaskId(incoming.id, index),
                    title = incoming.title,
                    done = incoming.done,
                    createdAt = now + index,
                    order = tasks.size,
                    dueDateMillis = dueMillis,
                    origin = Origins.ZENDO_PWA,
                    sourceId = incoming.id,
                )
                added++
            }
        }

        val changed = actionSet || highlightSet || added > 0 || updated > 0 || removedImported > 0
        val record = if (changed) {
            DayRecord(
                id = DayRecord.idFor(snapshot.localDate),
                date = snapshot.localDate,
                actionTitle = actionTitle,
                actionDone = actionDone,
                highlightText = highlightText,
                tasks = tasks.mapIndexed { index, task -> task.copy(order = index) },
                origin = Origins.ZENDO_PWA,
                updatedAt = now,
            )
        } else {
            null
        }

        return BridgePlan(
            targetDate = snapshot.localDate,
            isToday = false,
            mode = mode,
            dayRecord = record,
            summary = ImportSummary(
                addedTasks = added,
                updatedTasks = updated,
                removedImportedTasks = removedImported,
                keptLocalTasks = tasks.count { !Origins.isImported(it.origin) },
                actionSet = actionSet,
                highlightAdded = highlightSet,
            ),
        )
    }

    private fun dueMillis(task: BridgeTask, zone: ZoneId): Long? =
        task.dueDate?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()

    /** Deterministic, filesystem-safe id for a task that only exists in a record. */
    private fun importedTaskId(sourceId: String, index: Int): String {
        val safe = sourceId.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(64)
        return if (safe.isEmpty()) "dt_imported_$index" else "dt_$safe"
    }
}
