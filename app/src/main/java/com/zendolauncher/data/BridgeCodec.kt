package com.zendolauncher.data

import com.zendolauncher.core.DayRecord
import com.zendolauncher.core.Highlight
import com.zendolauncher.core.MindfulGate
import com.zendolauncher.core.Origins
import com.zendolauncher.core.TaskItem
import com.zendolauncher.ui.theme.BackdropStyle
import com.zendolauncher.ui.theme.ScrimStrength
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * Settings worth carrying in a backup: preferences about how the launcher
 * behaves, never identifiers. Package names, calendar ids, account names and
 * anything typed by other apps stay out — a backup that leaked those would be
 * exactly the kind of file this project exists to avoid.
 *
 * The removed appearance keys (layout, icon style, pin limit, screen time) are no longer
 * written. A backup from an older build still parses: unknown keys are ignored, and a
 * restore simply leaves the controls that no longer exist out of the result.
 */
data class BackupPrefs(
    val theme: ThemeMode = ThemeMode.DARK,
    val reduceMotion: Boolean = false,
    val pauseEnabled: Boolean = false,
    val pauseMessage: String = SettingsStore.DEFAULT_PAUSE_MESSAGE,
    val windowEnabled: Boolean = false,
    val windowStartMinute: Int = SettingsStore.DEFAULT_WINDOW_START,
    val windowEndMinute: Int = SettingsStore.DEFAULT_WINDOW_END,
    val homeMode: HomeMode = HomeMode.QUIET,
    val homeAlignment: HomeAlignment = HomeAlignment.CENTER,
    val labelCase: LabelCase = LabelCase.LOWERCASE,
    val backdropStyle: BackdropStyle = BackdropStyle.MATTE,
    val scrimStrength: ScrimStrength = ScrimStrength.BALANCED,
    val backdropBlur: Boolean = false,
    val showCalendarGlance: Boolean = true,
    val showUsageOnHome: Boolean = true,
    val pauseDelaySeconds: Int = MindfulGate.DEFAULT_DELAY_SECONDS,
    val gateTimeboxEnabled: Boolean = true,
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
 * Strict, dependency-free JSON for the one file the launcher reads and writes: its own
 * backup.
 *
 * Everything is validated by type and length before any of it is trusted; an
 * unknown or newer schema version is rejected with a sentence a person can act
 * on. Nothing in a file is ever executed — it is read as data or not at all.
 */
object BridgeJson {

    const val BACKUP_FORMAT = "zendo-launcher-backup"
    const val SCHEMA_VERSION = 1

    /** Refuses to even parse a file larger than this — a backup is kilobytes. */
    const val MAX_RAW_CHARS = 2_000_000

    private const val MAX_ID = 128
    private const val MAX_TEXT = 500
    private const val MAX_STAMP = 64
    private const val MAX_MESSAGE = 500

    private class Reject(message: String) : Exception(message)

    // ---- Launcher backup ----------------------------------------------------------

    fun encodeBackup(contents: BackupContents, appVersion: String, now: Long): String {
        val action = JSONObject()
            .put("title", contents.actionTitle)
            .put("done", contents.actionDone)
            .put("origin", Origins.normalize(contents.actionOrigin))
        contents.actionSourceId?.let { action.put("sourceId", it) }
        val settings = JSONObject()
            .put("theme", contents.prefs.theme.name)
            .put("reduceMotion", contents.prefs.reduceMotion)
            .put("pauseEnabled", contents.prefs.pauseEnabled)
            .put("pauseMessage", contents.prefs.pauseMessage)
            .put("windowEnabled", contents.prefs.windowEnabled)
            .put("windowStartMinute", contents.prefs.windowStartMinute)
            .put("windowEndMinute", contents.prefs.windowEndMinute)
            .put("homeMode", contents.prefs.homeMode.name)
            .put("homeAlignment", contents.prefs.homeAlignment.name)
            .put("labelCase", contents.prefs.labelCase.name)
            .put("backdropStyle", contents.prefs.backdropStyle.name)
            .put("scrimStrength", contents.prefs.scrimStrength.name)
            .put("backdropBlur", contents.prefs.backdropBlur)
            .put("showCalendarGlance", contents.prefs.showCalendarGlance)
            .put("showUsageOnHome", contents.prefs.showUsageOnHome)
            .put("pauseDelaySeconds", contents.prefs.pauseDelaySeconds)
            .put("gateTimeboxEnabled", contents.prefs.gateTimeboxEnabled)
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
        theme = enumOr(o, "theme", ThemeMode.DARK),
        reduceMotion = optionalBool(o, "reduceMotion", false),
        pauseEnabled = optionalBool(o, "pauseEnabled", false),
        pauseMessage = optionalText(o, "pauseMessage", MAX_MESSAGE)
            ?: SettingsStore.DEFAULT_PAUSE_MESSAGE,
        windowEnabled = optionalBool(o, "windowEnabled", false),
        windowStartMinute = (optionalNumber(o, "windowStartMinute")?.toInt()
            ?: SettingsStore.DEFAULT_WINDOW_START).coerceIn(0, 24 * 60 - 1),
        windowEndMinute = (optionalNumber(o, "windowEndMinute")?.toInt()
            ?: SettingsStore.DEFAULT_WINDOW_END).coerceIn(0, 24 * 60 - 1),
        homeMode = enumOr(o, "homeMode", HomeMode.QUIET),
        homeAlignment = enumOr(o, "homeAlignment", HomeAlignment.CENTER),
        labelCase = enumOr(o, "labelCase", LabelCase.LOWERCASE),
        backdropStyle = enumOr(o, "backdropStyle", BackdropStyle.MATTE),
        scrimStrength = enumOr(o, "scrimStrength", ScrimStrength.BALANCED),
        backdropBlur = optionalBool(o, "backdropBlur", false),
        showCalendarGlance = optionalBool(o, "showCalendarGlance", true),
        showUsageOnHome = optionalBool(o, "showUsageOnHome", true),
        pauseDelaySeconds = (optionalNumber(o, "pauseDelaySeconds")?.toInt()
            ?: MindfulGate.DEFAULT_DELAY_SECONDS)
            .let { if (it in MindfulGate.DELAY_OPTIONS_SECONDS) it else MindfulGate.DEFAULT_DELAY_SECONDS },
        gateTimeboxEnabled = optionalBool(o, "gateTimeboxEnabled", true),
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
