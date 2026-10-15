package com.zendolauncher.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.zendolauncher.core.DayRecord
import com.zendolauncher.core.DayTaskRecord
import com.zendolauncher.core.Origins
import com.zendolauncher.core.TaskItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Local day history. One record per local date, keyed by the date's own id
 * ("d_2026-10-15").
 *
 * The record for today is rewritten continuously by [capture] while the date is
 * current; records for past dates are never touched again by everyday editing.
 * There is no midnight job: a day stops being the capture target the moment the
 * local date moves on, and whatever was last written stays.
 *
 * Nothing here leaves the device. JSON via org.json (platform, no new dep),
 * versioned, tolerant on read, capped at [DayRecord.MAX_DAYS].
 */
class HistoryStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _days = MutableStateFlow(read())
    val days: StateFlow<List<DayRecord>> = _days.asStateFlow()

    fun read(): List<DayRecord> = DayRecord.sanitizeAll(decode(prefs.getString(KEY_DAYS, null)))

    fun recordFor(date: LocalDate): DayRecord? = _days.value.firstOrNull { it.date == date }

    /** Snapshot the live data into [date]; see the class comment for what that means. */
    fun capture(
        date: LocalDate,
        actionTitle: String,
        actionDone: Boolean,
        highlightText: String,
        tasks: List<TaskItem>,
        origin: String = Origins.LOCAL,
    ): Boolean = upsert(
        DayRecord.capture(date, actionTitle, actionDone, highlightText, tasks, origin),
    )

    /** Writes [record], replacing any record for the same date. Identical content is a no-op. */
    fun upsert(record: DayRecord): Boolean {
        val existing = recordFor(record.date)
        if (existing != null && existing.sameContentAs(record)) return false
        persist(_days.value.filterNot { it.date == record.date } + record)
        return true
    }

    /** Writes many records at once (backup restore). One persist, no partial state. */
    fun upsertAll(records: List<DayRecord>) {
        if (records.isEmpty()) return
        val byDate = _days.value.associateBy { it.date }.toMutableMap()
        records.forEach { byDate[it.date] = it }
        persist(byDate.values.toList())
    }

    fun clear() {
        prefs.edit { remove(KEY_DAYS) }
        _days.value = emptyList()
    }

    private fun persist(next: List<DayRecord>) {
        val sanitized = DayRecord.sanitizeAll(next)
        prefs.edit { putString(KEY_DAYS, encode(sanitized)) }
        _days.value = sanitized
    }

    companion object {
        private const val PREFS = "launcher_history"
        private const val KEY_DAYS = "history_json"

        /** Written beside the blob; read tolerantly so an older file still loads. */
        const val VERSION = 1

        fun encode(days: List<DayRecord>): String = JSONObject()
            .put("version", VERSION)
            .put("days", encodeArray(days))
            .toString()

        /** The bare array, for embedding in a larger document (backup). */
        fun encodeArray(days: List<DayRecord>): JSONArray {
            val array = JSONArray()
            days.forEach { day ->
                val tasks = JSONArray()
                day.tasks.forEach { task ->
                    val t = JSONObject()
                        .put("id", task.id)
                        .put("title", task.title)
                        .put("done", task.done)
                        .put("createdAt", task.createdAt)
                        .put("order", task.order)
                    task.dueDateMillis?.let { t.put("dueDateMillis", it) }
                    if (task.origin != Origins.LOCAL) t.put("origin", task.origin)
                    task.sourceId?.let { t.put("sourceId", it) }
                    tasks.put(t)
                }
                val o = JSONObject()
                    .put("id", day.id)
                    .put("date", day.date.toString())
                    .put("actionTitle", day.actionTitle)
                    .put("actionDone", day.actionDone)
                    .put("highlightText", day.highlightText)
                    .put("origin", day.origin)
                    .put("updatedAt", day.updatedAt)
                    .put("tasks", tasks)
                array.put(o)
            }
            return array
        }

        fun decode(raw: String?): List<DayRecord> {
            if (raw.isNullOrBlank()) return emptyList()
            return try {
                decodeArray(rawArray(raw))
            } catch (_: Exception) {
                emptyList()
            }
        }

        /** Decodes one array of day objects, skipping anything unreadable. */
        fun decodeArray(array: JSONArray): List<DayRecord> {
            val out = ArrayList<DayRecord>(array.length())
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val date = try {
                    LocalDate.parse(o.optString("date", ""))
                } catch (_: DateTimeParseException) {
                    continue
                }
                val tasks = ArrayList<DayTaskRecord>()
                val taskArray = o.optJSONArray("tasks")
                if (taskArray != null) {
                    for (j in 0 until taskArray.length()) {
                        val t = taskArray.optJSONObject(j) ?: continue
                        val title = t.optString("title", "").trim()
                        val id = t.optString("id", "").trim()
                        if (title.isEmpty() || id.isEmpty()) continue
                        tasks += DayTaskRecord(
                            id = id,
                            title = title,
                            done = t.optBoolean("done", false),
                            createdAt = t.optLong("createdAt", 0L),
                            order = t.optInt("order", j),
                            dueDateMillis = if (t.has("dueDateMillis")) t.optLong("dueDateMillis") else null,
                            origin = Origins.normalize(t.optString("origin", "")),
                            sourceId = t.optString("sourceId", "").takeIf { it.isNotBlank() },
                        )
                    }
                }
                out += DayRecord(
                    id = DayRecord.idFor(date),
                    date = date,
                    actionTitle = o.optString("actionTitle", ""),
                    actionDone = o.optBoolean("actionDone", false),
                    highlightText = o.optString("highlightText", ""),
                    tasks = tasks,
                    origin = Origins.normalize(o.optString("origin", "")),
                    updatedAt = o.optLong("updatedAt", 0L),
                )
            }
            return out
        }

        /** Accepts the wrapped {"version":1,"days":[…]} shape and a bare array alike. */
        private fun rawArray(raw: String): JSONArray {
            val trimmed = raw.trim()
            if (trimmed.startsWith("[")) return JSONArray(trimmed)
            val root = JSONObject(trimmed)
            return root.optJSONArray("days") ?: JSONArray()
        }
    }
}
