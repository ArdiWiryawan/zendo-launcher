package com.zendolauncher.core

import com.zendolauncher.model.LaunchTarget
import java.text.Normalizer
import java.util.Locale

/**
 * Pure list logic for the launcher. No Android dependencies, so it is covered by
 * JVM unit tests.
 */
object AppQuery {

    private val COMBINING_MARKS = Regex("\\p{Mn}+")
    private val WORD_BREAKS = charArrayOf(' ', '-', '_', '.')

    /** Case- and accent-insensitive form used for matching and sorting. */
    fun normalize(input: String): String =
        Normalizer.normalize(input, Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .lowercase(Locale.ROOT)
            .trim()

    /** Apps the user chose to hide are removed from every list. */
    fun visible(apps: List<LaunchTarget>, hidden: Set<String>): List<LaunchTarget> =
        apps.filterNot { it.key in hidden }

    fun alphabetical(apps: List<LaunchTarget>): List<LaunchTarget> =
        apps.sortedWith(compareBy({ normalize(it.label) }, { it.key }))

    /**
     * Favourites in the user's own order. Keys that no longer resolve are dropped,
     * which is how an uninstalled app disappears without disturbing the order.
     */
    fun favourites(order: List<String>, apps: List<LaunchTarget>): List<LaunchTarget> {
        val byKey = apps.associateBy { it.key }
        return order.mapNotNull { byKey[it] }
    }

    fun search(apps: List<LaunchTarget>, query: String): List<LaunchTarget> {
        val q = normalize(query)
        if (q.isEmpty()) return alphabetical(apps)
        return apps
            .mapNotNull { app -> rank(app, q)?.let { rank -> app to rank } }
            .sortedWith(compareBy({ it.second }, { normalize(it.first.label) }))
            .map { it.first }
    }

    /** Lower is better; null means no match. */
    fun rank(app: LaunchTarget, normalizedQuery: String): Int? {
        val label = normalize(app.label)
        return when {
            label == normalizedQuery -> 0
            label.startsWith(normalizedQuery) -> 1
            label.split(*WORD_BREAKS).any { it.isNotEmpty() && it.startsWith(normalizedQuery) } -> 2
            label.contains(normalizedQuery) -> 3
            initials(label).startsWith(normalizedQuery) -> 4
            else -> null
        }
    }

    fun initials(normalizedLabel: String): String =
        normalizedLabel.split(*WORD_BREAKS)
            .filter { it.isNotEmpty() }
            .map { it.first() }
            .joinToString("")
}
