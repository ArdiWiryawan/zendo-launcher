package com.zendolauncher.data

import com.zendolauncher.core.BridgeAction
import com.zendolauncher.core.BridgeHighlight
import com.zendolauncher.core.BridgeParse
import com.zendolauncher.core.BridgeSnapshot
import com.zendolauncher.core.BridgeTask
import com.zendolauncher.core.DayRecord
import com.zendolauncher.core.Highlight
import com.zendolauncher.core.Origins
import com.zendolauncher.core.TaskItem
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Settings worth carrying in a backup: preferences about how the launcher
 * behaves, never identifiers. Package names, calendar ids, account names and
 * anything typed by other apps stay out — a backup that leaked those would be
 * exactly the kind of file this project exists to avoid.
 */
data class BackupPrefs(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val layout: LayoutMode = LayoutMode.TEXT,
    val clock24h: Boolean = true,
    val reduceMotion: Boolean = false,
    val swipeUpEnabled: Boolean = true,
    val pagingEnabled: Boolean = true,
    val monochromeIcons: Boolean = true,
    val pinLimit: Int = SettingsStore.DEFAULT_PIN_LIMIT,
    val screenTimeEnabled: Boolean = false,
    val screenTimeHidden: Boolean = false,
    val pauseEnabled: Boolean = false,
    val pauseMessage: String = SettingsStore.DEFAULT_PAUSE_MESSAGE,
    val windowEnabled: Boolean = false,
    val windowStartMinute: Int = SettingsStore.DEFAULT_WINDOW_START,
    val windowEndMinute: Int = SettingsStore.DEFAULT_WINDOW_END,
)

/** Everything a full local backup contains. No tokens, no event bodies, no logs. */
data class BackupContents(
    val actionTitle: String = "",
    val actionDone: Boolean = false,
    val actionOrigin: String = Origins.LOCAL,
    val actionSourceId: String? = null,
    val highlights: List<Highlight> = emptyList(),
    val tasks: List<TaskItem> = emptyList(),
    val days: List<DayRecord> = emptyList(),
    val prefs: BackupPrefs = BackupPrefs(),
)

sealed interface BackupParse {
    data class Ok(val contents: BackupContents) : BackupParse

    /** A file that is readable but not a valid backup. [message] is user-facing. */
    data class Rejected(val message: String) : BackupParse
}

/**
 * Strict, dependency-free JSON for the two files the launcher reads:
 *
 *  - the Zendo bridge snapshot (written by the PWA, imported here), and
 *  - the launcher's own backup (written and re-read here).
 *
 * Everything is validated by type and length before any of it is trusted; an
 * unknown or newer schema version is rejected with a sentence a person can act
 * on. Nothing in a file is ever executed — it is read as data or not at all.
 */
object BridgeJson {

    const val BRIDGE_FORMAT = "zendo-launcher-bridge"
    const val BACKUP_FORMAT = "zendo-launcher-backup"
    const val SCHEMA_VERSION = 1

    /** Refuses to even parse a file larger than this — a snapshot is kilobytes. */
    const val MAX_RAW_CHARS = 2_000_000

    private const val MAX_ID = 128
    private const val MAX_TEXT = 500
    private const val MAX_STAMP = 64
    private const val MAX_MESSAGE = 500
    private const val MAX_TASKS = 500

    private class Reject(message: String) : Exception(message)

    // ---- Zendo bridge snapshot ----------------------------------------------------

    fun parseSnapshot(raw: String?, today: LocalDate): BridgeParse {
        if (raw.isNullOrBlank()) return BridgeParse.Rejected("The file is empty.")
        if (raw.length > MAX_RAW_CHARS) {
            return BridgeParse.Rejected("The file is too large to be a Zendo snapshot.")
        }
        return try {
            BridgeParse.Ok(readSnapshot(root(raw), today))
        } catch (r: Reject) {
            BridgeParse.Rejected(r.message ?: "The snapshot could not be read.")
        } catch (_: Exception) {
            BridgeParse.Rejected("This is not a valid Zendo snapshot file.")
        }
    }

    private fun readSnapshot(root: JSONObject, today: LocalDate): BridgeSnapshot {
        if (requiredText(root, "format", MAX_STAMP) != BRIDGE_FORMAT) {
            throw Reject("This file is not a Zendo launcher snapshot.")
        }
        val version = requiredNumber(root, "schemaVersion").toInt()
        if (version < 1) throw Reject("The snapshot has an invalid schema version.")
        if (version > SCHEMA_VERSION) {
            throw Reject(
                "This snapshot was exported by a newer Zendo (format v$version). " +
                    "Update the launcher to import it.",
            )
        }
        val source = requiredText(root, "source", MAX_STAMP)
        val exportedAt = requiredText(root, "exportedAt", MAX_STAMP)
        val date = requiredDate(root, "localDate")
        if (date.isAfter(today)) {
            throw Reject("The snapshot is dated $date, which is in the future. Import it on that day instead.")
        }
        val action = objectOrNull(root, "todayAction")?.let { o ->
            BridgeAction(
                id = optionalText(o, "id", MAX_ID),
                title = requiredText(o, "title", MAX_TEXT),
            )
        }
        val highlight = objectOrNull(root, "highlight")?.let { o ->
            BridgeHighlight(
                id = optionalText(o, "id", MAX_ID),
                text = requiredText(o, "text", MAX_TEXT),
            )
        }
        val tasks = ArrayList<BridgeTask>()
        val seenIds = HashSet<String>()
        arrayOrNull(root, "tasks")?.let { array ->
            if (array.length() > MAX_TASKS) {
                throw Reject("The snapshot lists more than $MAX_TASKS tasks.")
            }
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: throw Reject("Task ${i + 1} is not an object.")
                val id = requiredText(o, "id", MAX_ID)
                // The same stable id twice is the same task, not two.
                if (!seenIds.add(id)) continue
                tasks += BridgeTask(
                    id = id,
                    title = requiredText(o, "title", MAX_TEXT),
                    done = optionalBool(o, "isCompleted", false),
                    dueDate = optionalDate(o, "dueDate"),
                    sortOrder = optionalNumber(o, "sortOrder")?.toInt() ?: i,
                    updatedAt = optionalText(o, "updatedAt", MAX_STAMP),
                )
            }
        }
        return BridgeSnapshot(
            source = source,
            exportedAt = exportedAt,
            localDate = date,
            timeZone = optionalText(root, "timeZone", MAX_STAMP),
            action = action,
            highlight = highlight,
            tasks = tasks,
        )
    }

    // ---- Launcher backup ----------------------------------------------------------

    fun encodeBackup(contents: BackupContents, appVersion: String, now: Long): String {
        val action = JSONObject()
            .put("title", contents.actionTitle)
            .put("done", contents.actionDone)
            .put("origin", Origins.normalize(contents.actionOrigin))
        contents.actionSourceId?.let { action.put("sourceId", it) }
        val settings = JSONObject()
            .put("theme", contents.prefs.theme.name)
            .put("layout", contents.prefs.layout.name)
            .put("clock24h", contents.prefs.clock24h)
            .put("reduceMotion", contents.prefs.reduceMotion)
            .put("swipeUpEnabled", contents.prefs.swipeUpEnabled)
            .put("pagingEnabled", contents.prefs.pagingEnabled)
            .put("monochromeIcons", contents.prefs.monochromeIcons)
            .put("pinLimit", contents.prefs.pinLimit)
            .put("screenTimeEnabled", contents.prefs.screenTimeEnabled)
            .put("screenTimeHidden", contents.prefs.screenTimeHidden)
            .put("pauseEnabled", contents.prefs.pauseEnabled)
            .put("pauseMessage", contents.prefs.pauseMessage)
            .put("windowEnabled", contents.prefs.windowEnabled)
            .put("windowStartMinute", contents.prefs.windowStartMinute)
            .put("windowEndMinute", contents.prefs.windowEndMinute)
        return JSONObject()
            .put("format", BACKUP_FORMAT)
            .put("schemaVersion", SCHEMA_VERSION)
            .put("exportedAt", Instant.ofEpochMilli(now).toString())
            .put("app", appVersion)
            .put("action", action)
            .put("highlights", JSONArray(HighlightStore.encode(contents.highlights)))
            .put("tasks", JSONArray(TaskStore.encode(contents.tasks)))
            .put("days", HistoryStore.encodeArray(contents.days))
            .put("settings", settings)
            .toString()
    }

    fun parseBackup(raw: String?): BackupParse {
        if (raw.isNullOrBlank()) return BackupParse.Rejected("The file is empty.")
        if (raw.length > MAX_RAW_CHARS) {
            return BackupParse.Rejected("The file is too large to be a launcher backup.")
        }
        return try {
            BackupParse.Ok(readBackup(root(raw)))
        } catch (r: Reject) {
            BackupParse.Rejected(r.message ?: "The backup could not be read.")
        } catch (_: Exception) {
            BackupParse.Rejected("This is not a valid Zendo Launcher backup file.")
        }
    }

    private fun readBackup(root: JSONObject): BackupContents {
        if (requiredText(root, "format", MAX_STAMP) != BACKUP_FORMAT) {
            throw Reject("This file is not a Zendo Launcher backup.")
        }
        val version = requiredNumber(root, "schemaVersion").toInt()
        if (version < 1) throw Reject("The backup has an invalid schema version.")
        if (version > SCHEMA_VERSION) {
            throw Reject(
                "This backup was written by a newer launcher (format v$version). " +
                    "Update the app to restore it.",
            )
        }
        val action = objectOrNull(root, "action")
        return BackupContents(
            actionTitle = action?.let { optionalText(it, "title", MAX_TEXT) } ?: "",
            actionDone = action?.let { optionalBool(it, "done", false) } ?: false,
            actionOrigin = action?.let { optionalText(it, "origin", MAX_STAMP) } ?: Origins.LOCAL,
            actionSourceId = action?.let { optionalText(it, "sourceId", MAX_ID) },
            // Sections are decoded with the same tolerant readers the stores use;
            // a damaged record is dropped rather than poisoning the whole file.
            highlights = arrayOrNull(root, "highlights")?.let {
                Highlight.sanitizeAll(HighlightStore.decode(it.toString()))
            } ?: emptyList(),
            tasks = arrayOrNull(root, "tasks")?.let {
                TaskItem.sanitizeAll(TaskStore.decode(it.toString()))
            } ?: emptyList(),
            days = arrayOrNull(root, "days")?.let {
                DayRecord.sanitizeAll(HistoryStore.decodeArray(it))
            } ?: emptyList(),
            prefs = objectOrNull(root, "settings")?.let { readPrefs(it) } ?: BackupPrefs(),
        )
    }

    private fun readPrefs(o: JSONObject): BackupPrefs = BackupPrefs(
        theme = enumOr(o, "theme", ThemeMode.SYSTEM),
        layout = enumOr(o, "layout", LayoutMode.TEXT),
        clock24h = optionalBool(o, "clock24h", true),
        reduceMotion = optionalBool(o, "reduceMotion", false),
        swipeUpEnabled = optionalBool(o, "swipeUpEnabled", true),
        pagingEnabled = optionalBool(o, "pagingEnabled", true),
        monochromeIcons = optionalBool(o, "monochromeIcons", true),
        pinLimit = (optionalNumber(o, "pinLimit")?.toInt() ?: SettingsStore.DEFAULT_PIN_LIMIT)
            .coerceIn(SettingsStore.MIN_PIN_LIMIT, SettingsStore.MAX_PIN_LIMIT),
        screenTimeEnabled = optionalBool(o, "screenTimeEnabled", false),
        screenTimeHidden = optionalBool(o, "screenTimeHidden", false),
        pauseEnabled = optionalBool(o, "pauseEnabled", false),
        pauseMessage = optionalText(o, "pauseMessage", MAX_MESSAGE)
            ?: SettingsStore.DEFAULT_PAUSE_MESSAGE,
        windowEnabled = optionalBool(o, "windowEnabled", false),
        windowStartMinute = (optionalNumber(o, "windowStartMinute")?.toInt()
            ?: SettingsStore.DEFAULT_WINDOW_START).coerceIn(0, 24 * 60 - 1),
        windowEndMinute = (optionalNumber(o, "windowEndMinute")?.toInt()
            ?: SettingsStore.DEFAULT_WINDOW_END).coerceIn(0, 24 * 60 - 1),
    )

    // ---- Strict field readers -----------------------------------------------------
    //
    // Required fields reject; optional fields reject only when present with the
    // wrong type. Messages name the field so a malformed file is fixable.

    private fun root(raw: String): JSONObject = try {
        JSONObject(raw)
    } catch (_: Exception) {
        throw Reject("This is not a valid JSON file.")
    }

    private fun value(obj: JSONObject, key: String): Any? =
        obj.opt(key)?.takeIf { it != JSONObject.NULL }

    private fun requiredText(obj: JSONObject, key: String, max: Int): String {
        val raw = value(obj, key)
        if (raw !is String) throw Reject("The file is missing \"$key\".")
        val text = raw.trim()
        if (text.isEmpty()) throw Reject("\"$key\" is empty.")
        if (text.length > max) throw Reject("\"$key\" is longer than $max characters.")
        return text
    }

    private fun optionalText(obj: JSONObject, key: String, max: Int): String? {
        val raw = value(obj, key) ?: return null
        if (raw !is String) throw Reject("\"$key\" is not text.")
        val text = raw.trim()
        if (text.isEmpty()) return null
        if (text.length > max) throw Reject("\"$key\" is longer than $max characters.")
        return text
    }

    private fun requiredNumber(obj: JSONObject, key: String): Number {
        val raw = value(obj, key) ?: throw Reject("The file is missing \"$key\".")
        if (raw !is Number) throw Reject("\"$key\" is not a number.")
        return raw
    }

    private fun optionalNumber(obj: JSONObject, key: String): Number? {
        val raw = value(obj, key) ?: return null
        if (raw !is Number) throw Reject("\"$key\" is not a number.")
        return raw
    }

    private fun optionalBool(obj: JSONObject, key: String, fallback: Boolean): Boolean {
        val raw = value(obj, key) ?: return fallback
        if (raw !is Boolean) throw Reject("\"$key\" is not true or false.")
        return raw
    }

    private fun requiredDate(obj: JSONObject, key: String): LocalDate {
        val raw = requiredText(obj, key, MAX_STAMP)
        return parseDate(raw) ?: throw Reject("\"$key\" is not a date (expected YYYY-MM-DD).")
    }

    private fun optionalDate(obj: JSONObject, key: String): LocalDate? {
        val raw = optionalText(obj, key, MAX_STAMP) ?: return null
        return parseDate(raw) ?: throw Reject("\"$key\" is not a date (expected YYYY-MM-DD).")
    }

    private fun parseDate(raw: String): LocalDate? = try {
        LocalDate.parse(raw)
    } catch (_: DateTimeParseException) {
        null
    }

    private fun objectOrNull(obj: JSONObject, key: String): JSONObject? {
        val raw = value(obj, key) ?: return null
        if (raw !is JSONObject) throw Reject("\"$key\" is not an object.")
        return raw
    }

    private fun arrayOrNull(obj: JSONObject, key: String): JSONArray? {
        val raw = value(obj, key) ?: return null
        if (raw !is JSONArray) throw Reject("\"$key\" is not a list.")
        return raw
    }

    private inline fun <reified T : Enum<T>> enumOr(obj: JSONObject, key: String, fallback: T): T {
        val raw = optionalText(obj, key, MAX_STAMP) ?: return fallback
        return enumValues<T>().firstOrNull { it.name == raw } ?: fallback
    }
}
