package com.zendolauncher.model

/** Where the user's daily planning block comes from. Null fields render nothing. */
data class ZendoToday(
    val highlight: String? = null,
    val mainAction: String? = null,
    val nextAgenda: AgendaItem? = null,
    val practices: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = highlight.isNullOrBlank() &&
            mainAction.isNullOrBlank() &&
            nextAgenda == null &&
            practices.isEmpty()
}

data class AgendaItem(val title: String, val time: String? = null)
