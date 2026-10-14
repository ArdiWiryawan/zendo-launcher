package com.zendolauncher.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.zendolauncher.core.Highlight
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistent highlights. Separate prefs file so editing a highlight never
 * rewrites the settings blob. JSON array via org.json (platform, no new dep).
 */
class HighlightStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _highlights = MutableStateFlow(read())
    val highlights: StateFlow<List<Highlight>> = _highlights.asStateFlow()

    fun read(): List<Highlight> =
        Highlight.sanitizeAll(decode(prefs.getString(KEY_ITEMS, null)))

    private fun persist(next: List<Highlight>) {
        val sanitized = Highlight.sanitizeAll(next)
        prefs.edit { putString(KEY_ITEMS, encode(sanitized)) }
        _highlights.value = sanitized
    }

    fun upsert(title: String, note: String, id: String? = null, pinAsPrimary: Boolean = false): Highlight? {
        val t = title.trim()
        if (t.isEmpty()) return null
        val now = System.currentTimeMillis()
        val current = _highlights.value.toMutableList()
        val result: Highlight
        if (id != null) {
            val index = current.indexOfFirst { it.id == id }
            if (index < 0) return null
            val old = current[index]
            result = old.copy(title = t, note = note.trim(), updatedAt = now)
            current[index] = result
        } else {
            result = Highlight(
                id = Highlight.newId(),
                title = t,
                note = note.trim(),
                createdAt = now,
                updatedAt = now,
                isPrimary = current.none { !it.archived },
            )
            current.add(0, result)
        }
        val withPin = if (pinAsPrimary) {
            current.map { it.copy(isPrimary = it.id == result.id && !it.archived) }
        } else {
            current
        }
        persist(withPin)
        return result
    }

    fun pinAsPrimary(id: String) {
        persist(_highlights.value.map { it.copy(isPrimary = it.id == id && !it.archived) })
    }

    fun archive(id: String, archived: Boolean = true) {
        val next = _highlights.value.map {
            if (it.id == id) it.copy(archived = archived, updatedAt = System.currentTimeMillis()) else it
        }
        persist(next)
    }

    fun delete(id: String) {
        persist(_highlights.value.filterNot { it.id == id })
    }

    companion object {
        private const val PREFS = "launcher_highlights"
        private const val KEY_ITEMS = "highlights_json"

        fun encode(items: List<Highlight>): String {
            val array = JSONArray()
            items.forEach { h ->
                array.put(
                    JSONObject()
                        .put("id", h.id)
                        .put("title", h.title)
                        .put("note", h.note)
                        .put("createdAt", h.createdAt)
                        .put("updatedAt", h.updatedAt)
                        .put("isPrimary", h.isPrimary)
                        .put("archived", h.archived),
                )
            }
            return array.toString()
        }

        fun decode(raw: String?): List<Highlight> {
            if (raw.isNullOrBlank()) return emptyList()
            return try {
                val array = JSONArray(raw)
                val out = ArrayList<Highlight>(array.length())
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    val id = o.optString("id", "")
                    val title = o.optString("title", "")
                    if (id.isBlank() || title.isBlank()) continue
                    out += Highlight(
                        id = id,
                        title = title,
                        note = o.optString("note", ""),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
                        isPrimary = o.optBoolean("isPrimary", false),
                        archived = o.optBoolean("archived", false),
                    )
                }
                out
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
