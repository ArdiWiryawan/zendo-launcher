package com.zendolauncher.core

import java.time.LocalDate

/** One task from a validated Zendo snapshot file. Pure data; never executed. */
data class BridgeTask(
    val id: String,
    val title: String,
    val done: Boolean = false,
    val dueDate: LocalDate? = null,
    val sortOrder: Int = 0,
    val updatedAt: String? = null,
)

data class BridgeAction(val id: String? = null, val title: String = "")

data class BridgeHighlight(val id: String? = null, val text: String = "")

/**
 * A manual, one-way snapshot exported by the Zendo PWA and picked by the user
 * through the system file picker. Nothing here is a login, a token or a live
 * connection: it is a file the user chose, once.
 */
data class BridgeSnapshot(
    val source: String,
    val exportedAt: String,
    val localDate: LocalDate,
    val timeZone: String? = null,
    val action: BridgeAction? = null,
    val highlight: BridgeHighlight? = null,
    val tasks: List<BridgeTask> = emptyList(),
) {
    val isEmpty: Boolean
        get() = action == null && highlight == null && tasks.isEmpty()
}

sealed interface BridgeParse {
    data class Ok(val snapshot: BridgeSnapshot) : BridgeParse

    /** A file that is readable but not a valid snapshot. [message] is user-facing. */
    data class Rejected(val message: String) : BridgeParse
}
