package com.zendolauncher.model

/**
 * Where the user's daily planning block comes from. Null fields render nothing.
 *
 * [tasks] and [upcoming] are additional to what [com.zendolauncher.core.TodaySelector]
 * can currently fill: the shipped source is [com.zendolauncher.platform.UnavailableZendoSource],
 * which returns null, and Zendo's stored day plan has no task list and no multi-day
 * agenda. The fields exist so the Today page's task and upcoming sections have a shape to
 * render *when* something fills them, and both default to empty so a source that only
 * ever supplies a highlight carries no invented data. The Today page renders an explicit
 * empty state for each of them rather than a blank gap.
 */
data class ZendoToday(
    val highlight: String? = null,
    val mainAction: String? = null,
    val nextAgenda: AgendaItem? = null,
    val practices: List<String> = emptyList(),
    val tasks: List<TodayTask> = emptyList(),
    val upcoming: List<AgendaItem> = emptyList(),
) {
    val isEmpty: Boolean
        get() = highlight.isNullOrBlank() &&
            mainAction.isNullOrBlank() &&
            nextAgenda == null &&
            practices.isEmpty() &&
            tasks.isEmpty() &&
            upcoming.isEmpty()
}

data class AgendaItem(val title: String, val time: String? = null)

/** One thing to do today. [done] is carried so a future source can tick it off. */
data class TodayTask(val title: String, val done: Boolean = false)
