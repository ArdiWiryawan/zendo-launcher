package com.zendolauncher.core

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * The stored form of a [HomeLayout]: one JSON array under a single preferences key.
 *
 * JSON rather than the newline-separated text the flat favourites list used, because a
 * page needs four fields and an ordered sublist per record. Encoding that as delimited
 * text means a delimiter collision the moment a user renames a page to a string
 * containing the delimiter, and escaping would have to be invented by hand.
 *
 * `org.json` ships in the Android platform, so this costs no new dependency. Because
 * `org.json` is not on the JVM classpath by itself, the round-trip tests are Robolectric
 * tests rather than plain JVM ones — see `HomeLayoutCodecTest`.
 *
 * One blob rather than one key per page: `SharedPreferences` has no atomic multi-key
 * commit relative to a reader, so a reorder or a delete split across keys could be
 * observed half-applied. One `putString` is one atomic write.
 *
 * Every field is read with an `opt` accessor and a sane default, so a layout written by a
 * newer build does not make an older one reject the file — unknown keys are simply not
 * looked at. See [decode] for the guarantees.
 */
object HomeLayoutCodec {

    const val VERSION = 1

    private const val KEY_ID = "id"
    private const val KEY_TYPE = "type"
    private const val KEY_NAME = "name"
    private const val KEY_HEADING = "heading"
    private const val KEY_ITEMS = "items"
    private const val KEY_SHOW_CLOCK = "showClock"
    private const val KEY_COLUMNS = "columns"

    fun encode(layout: HomeLayout): String {
        val array = JSONArray()
        layout.pages.forEach { page ->
            val json = JSONObject()
                .put(KEY_ID, page.id)
                .put(KEY_TYPE, page.type.name)
                .put(KEY_NAME, page.name)
                .put(KEY_SHOW_CLOCK, page.config.showClock)
            page.config.heading?.let { json.put(KEY_HEADING, it) }
            if (page.config.itemKeys.isNotEmpty()) {
                json.put(KEY_ITEMS, JSONArray(page.config.itemKeys))
            }
            array.put(json)
        }
        return array.toString()
    }

    /**
     * The stored layout, or null when the string is absent or cannot be read as one.
     *
     * Null means "there is nothing stored here", which is what the caller needs in order
     * to tell a fresh install (fall back to [HomeLayout.DEFAULT]) from a stored layout it
     * must repair. An empty array, a truncated blob and a bare `null` all come back as
     * null rather than as an exception: a launcher that cannot start because one
     * preferences value is malformed is worse than one that starts on the default Home.
     *
     * The result is *not* sanitized here. Repairing is the store's job on every read, and
     * doing it in two places would be two definitions of valid.
     */
    fun decode(raw: String?): HomeLayout? {
        if (raw.isNullOrBlank()) return null
        val array = try {
            JSONArray(raw)
        } catch (_: JSONException) {
            return null
        }
        if (array.length() == 0) return null

        val pages = ArrayList<HomePage>(array.length())
        for (index in 0 until array.length()) {
            val json = array.optJSONObject(index) ?: continue
            val id = json.optString(KEY_ID, "")
            if (id.isBlank()) continue
            pages += HomePage(
                id = id,
                type = pageType(json.optString(KEY_TYPE, "")),
                name = json.optString(KEY_NAME, ""),
                config = PageConfig(
                    heading = json.optString(KEY_HEADING, "").takeIf { it.isNotBlank() },
                    itemKeys = itemKeys(json.optJSONArray(KEY_ITEMS)),
                    // Reserved and only ever 1; read so a hand-edited file does not carry a
                    // value that would silently change how a page lays out.
                    columns = json.optInt(KEY_COLUMNS, 1),
                    showClock = json.optBoolean(KEY_SHOW_CLOCK, false),
                ),
            )
        }
        if (pages.isEmpty()) return null
        return HomeLayout(pages, activePageId = null)
    }

    /** An unrecognised stored type becomes an APPS page, which is the type that lost the least. */
    private fun pageType(raw: String): PageType =
        PageType.entries.firstOrNull { it.name == raw } ?: PageType.APPS

    private fun itemKeys(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val keys = ArrayList<String>(array.length())
        for (index in 0 until array.length()) {
            val key = array.optString(index, "")
            if (key.isNotBlank()) keys += key
        }
        return keys
    }
}
