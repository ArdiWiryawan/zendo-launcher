package com.zendolauncher.core

import java.util.UUID

/**
 * What a Home page is for. The type decides which body renders — it is not what the user
 * sees, which is [HomePage.name].
 *
 * The tree ships three of these and they all render. [WIDGET] is a fourth value the
 * serializer understands so a page of that type in a stored layout round-trips instead of
 * being dropped, and because naming the unbuilt page is more honest than pretending
 * nothing is missing. Nothing ever creates one: [HomeLayout.addPage] defaults to [APPS],
 * and the edit-mode "Add" control only offers [APPS]. A WIDGET page therefore renders an
 * empty state that says a widget page is not built yet, which is a true statement about
 * the app rather than a control that does nothing.
 *
 * Pure Kotlin on purpose: the model is JVM-unit-testable and free of Android types, and
 * so is [HomeLayoutCodec], which stays in this package rather than in `data`. Only
 * `data/SettingsStore.kt` touches `android.content`.
 */
enum class PageType {
    /** Date, the day's highlight, agenda and a route into Focus. */
    TODAY,

    /** The one page that holds an ordered list of [com.zendolauncher.model.LaunchTarget.key] values. */
    APPS,

    /** Calendar agenda merged with due tasks, plus the focus timer. */
    PLAN,

    /** Focus session state and a quick note. */
    NOTES,

    /** Recognised and stored, not implemented. See the class comment. */
    WIDGET,
}

/** The label a page falls back to when the user has not renamed it. */
fun PageType.defaultName(): String = when (this) {
    PageType.TODAY -> "Today"
    PageType.APPS -> "Apps"
    PageType.PLAN -> "Plan & Focus"
    PageType.NOTES -> "Focus"
    PageType.WIDGET -> "Widget"
}

/**
 * A page's own settings, flattened rather than sealed: a page's type can change without
 * its identity changing, and a sealed hierarchy would force the store to understand every
 * variant in order to write a partial change.
 *
 * [itemKeys] holds `LaunchTarget.key` strings in the user's order. An APPS page renders
 * them; every other type keeps them untouched, so switching a page's type away and back
 * finds its apps where they were.
 *
 * [columns] is reserved. Only `1` is honoured; nothing writes anything else and no layout
 * reads it. [showClock] only has an effect on a [PageType.TODAY] page — the clock is that
 * page's own and is not drawn anywhere else.
 */
data class PageConfig(
    val heading: String? = null,
    val itemKeys: List<String> = emptyList(),
    val columns: Int = 1,
    val showClock: Boolean = false,
)

/**
 * One Home page. [id] is stable, opaque and never derived from [name]: two pages may
 * legitimately be called "Work", and renaming must not orphan [HomeLayout.activePageId].
 * Ids are never renumbered on reorder either — order is list position and nothing else.
 */
data class HomePage(
    val id: String,
    val type: PageType,
    val name: String,
    val config: PageConfig = PageConfig(),
)

/** One entry in a reorder or a move: which page, and where it goes. */
data class PageMove(val id: String, val toIndex: Int)

/**
 * The whole Home layout. [pages] *is* the order.
 *
 * This carries the pure page operations so the persistence layer writes them and the view
 * model calls them, with one implementation in the middle. Clamping, id repair and the
 * active-page fallback live in [sanitize] and run on every read and after every mutation,
 * so nothing downstream re-derives or repairs anything.
 */
data class HomeLayout(
    val pages: List<HomePage> = emptyList(),
    val activePageId: String? = null,
) {

    /** Where the active page sits, or 0 when the id does not resolve. */
    val activeIndex: Int
        get() = pages.indexOfFirst { it.id == activePageId }.coerceAtLeast(0)

    fun page(id: String): HomePage? = pages.firstOrNull { it.id == id }

    /**
     * The page a pin from the drawer should land on: the active page when that page shows
     * apps, otherwise the first page that does, otherwise null — the caller creates one.
     */
    fun pinTargetPageId(): String? =
        page(activePageId ?: "")?.takeIf { it.type == PageType.APPS }?.id
            ?: pages.firstOrNull { it.type == PageType.APPS }?.id

    /** Adds a page immediately after the active one and returns its new id, or null at MAX. */
    fun addPage(type: PageType = PageType.APPS, name: String? = null, id: String? = null): Pair<HomeLayout, String>? {
        if (pages.size >= MAX_PAGES) return null
        val newId = id?.takeIf { candidate -> pages.none { it.id == candidate } } ?: newId()
        val page = HomePage(newId, type, name.orDefault(type))
        val at = (activeIndex + 1).coerceIn(0, pages.size)
        val next = pages.toMutableList().apply { add(at, page) }
        return copy(pages = next) to newId
    }

    /** Removes a page, unless it is the only one left. The active page falls back to a neighbour. */
    fun removePage(id: String): HomeLayout {
        if (pages.size <= MIN_PAGES) return this
        val index = pages.indexOfFirst { it.id == id }
        if (index < 0) return this
        val next = pages.toMutableList().apply { removeAt(index) }
        // Only a removed active page moves the active id; the rest of the time it stays.
        val active = if (activePageId == id) next[index.coerceAtMost(next.lastIndex)].id else activePageId
        return copy(pages = next, activePageId = active)
    }

    /** Renames. A blank name is ignored rather than stored, so a page can never render nameless. */
    fun renamePage(id: String, name: String): HomeLayout {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return this
        return copy(pages = pages.map { if (it.id == id) it.copy(name = trimmed) else it })
    }

    /** Moves a page to an absolute index, clamped. List position is the order; ids never change. */
    fun movePage(id: String, toIndex: Int): HomeLayout {
        val from = pages.indexOfFirst { it.id == id }
        if (from < 0) return this
        val to = toIndex.coerceIn(0, pages.lastIndex)
        if (to == from) return this
        val next = pages.toMutableList().apply { add(to, removeAt(from)) }
        return copy(pages = next)
    }

    /** Records which page is showing. No-op when the id is not in the layout. */
    fun setActivePage(id: String): HomeLayout =
        if (pages.any { it.id == id }) copy(activePageId = id) else this

    /** Replaces one page's config wholesale. */
    fun updatePageConfig(id: String, config: PageConfig): HomeLayout =
        copy(pages = pages.map { if (it.id == id) it.copy(config = config) else it })

    /** Changes a page's type, keeping its config so a re-type round-trips without data loss. */
    fun setType(id: String, type: PageType): HomeLayout =
        copy(pages = pages.map { if (it.id == id) it.copy(type = type) else it })

    /** Appends a key to a page's item list if it is not already there. */
    fun addItem(pageId: String, key: String): HomeLayout {
        if (key.isBlank()) return this
        return copy(
            pages = pages.map { page ->
                if (page.id != pageId || key in page.config.itemKeys) {
                    page
                } else {
                    page.copy(config = page.config.copy(itemKeys = page.config.itemKeys + key))
                }
            },
        )
    }

    /** Appends keys to a page, skipping any already present and skipping blanks, keeping their order. */
    fun addItems(pageId: String, keys: List<String>): HomeLayout {
        val page = page(pageId) ?: return this
        val merged = page.config.itemKeys.toMutableList()
        keys.forEach { key -> if (key.isNotBlank() && key !in merged) merged += key }
        if (merged.size == page.config.itemKeys.size) return this
        return updatePageConfig(pageId, page.config.copy(itemKeys = merged))
    }

    /** Removes a key from a page's item list. */
    fun removeItem(pageId: String, key: String): HomeLayout = copy(
        pages = pages.map { page ->
            if (page.id != pageId) page else page.copy(config = page.config.copy(itemKeys = page.config.itemKeys - key))
        },
    )

    /**
     * Removes a key from every page. Unhiding an app must not leave it pinned to a page the
     * user never touched.
     */
    fun removeItemEverywhere(key: String): HomeLayout = copy(
        pages = pages.map { page ->
            if (key in page.config.itemKeys) {
                page.copy(config = page.config.copy(itemKeys = page.config.itemKeys - key))
            } else {
                page
            }
        },
    )

    /**
     * Moves an item within its page.
     *
     * An item never crosses a page or a gap: an out-of-range *source* is a no-op, and an
     * out-of-range destination is refused rather than clamped, because clamping
     * `[a, b, c]` moving 0 "to 99" would silently send `a` to the end. A caller that
     * means "one step" asks for `from + 1` and gets a no-op at the last slot, which is
     * what a Step Up control at the end of a list should do.
     */
    fun moveItem(pageId: String, from: Int, to: Int): HomeLayout {
        val page = page(pageId) ?: return this
        val keys = page.config.itemKeys
        if (from !in keys.indices) return this
        if (to !in keys.indices) return this
        if (to == from) return this
        val next = keys.toMutableList().apply { add(to, removeAt(from)) }
        return updatePageConfig(pageId, page.config.copy(itemKeys = next))
    }

    /**
     * The first APPS page, created at the end when there is none. A pin from the drawer
     * always has somewhere real to land, so the pin can never be silently swallowed.
     */
    fun withAppsPage(): HomeLayout =
        if (pages.any { it.type == PageType.APPS }) this else copy(pages = pages + HomePage(newId(), PageType.APPS, PageType.APPS.defaultName()))

    /** Where the apps of the active page live: the active page, or the first APPS page. */
    fun activeAppsPageId(): String? = pinTargetPageId()

    companion object {
        const val MIN_PAGES = 1
        const val MAX_PAGES = 5

        /**
         * The layout a fresh install gets. The three ids are fixed literals rather than
         * generated, so a stored layout can name them and migration can build on them.
         */
        val DEFAULT: HomeLayout = HomeLayout(
            pages = listOf(
                HomePage("p_today", PageType.TODAY, "Today", PageConfig(showClock = true)),
                HomePage("p_plan", PageType.PLAN, "Plan & Focus"),
                HomePage("p_launch", PageType.APPS, "Essentials", PageConfig(heading = "Essentials")),
            ),
            activePageId = "p_today",
        )

        /** 1 + 32 characters, no separator, so an id is safe inside every encoding. */
        fun newId(): String = "p_" + UUID.randomUUID().toString().replace("-", "")

        /**
         * Clamps and repairs a layout so every consumer can trust it. Enforces, in order:
         * the page-count bounds, non-blank unique ids, non-blank names, de-duplicated item
         * keys, and an active id that resolves.
         */
        fun sanitize(layout: HomeLayout): HomeLayout {
            if (layout.pages.isEmpty()) return DEFAULT

            val seen = HashSet<String>(layout.pages.size)
            val repaired = layout.pages.map { page ->
                val id = page.id.takeIf { it.isNotBlank() && seen.add(it) } ?: newId().also { seen.add(it) }
                page.copy(
                    id = id,
                    name = page.name.trim().ifBlank { page.type.defaultName() },
                    config = page.config.copy(
                        itemKeys = page.config.itemKeys.filter { it.isNotBlank() }.distinct(),
                        columns = 1,
                    ),
                )
            }

            val pages = when {
                repaired.size > MAX_PAGES -> repaired.take(MAX_PAGES)
                else -> repaired
            }
            val active = layout.activePageId?.takeIf { id -> pages.any { it.id == id } } ?: pages.first().id
            return HomeLayout(pages, active)
        }
    }
}

private fun String?.orDefault(type: PageType): String =
    this?.trim()?.takeIf { it.isNotEmpty() } ?: type.defaultName()
