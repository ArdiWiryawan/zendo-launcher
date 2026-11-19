package com.zendolauncher.core

/**
 * Pure domain constants and helpers for the Mindful Gate.
 */
object MindfulGate {

    const val BORED_INTENTION = "Just checking / bored"

    val DEFAULT_INTENTIONS: List<String> = listOf(
        "Reply / messages",
        "Look something up",
        "Work / post",
        BORED_INTENTION,
    )

    val DELAY_OPTIONS_SECONDS: List<Int> = listOf(0, 3, 5, 8)
    const val DEFAULT_DELAY_SECONDS = 5

    val TIMEBOX_OPTIONS_MINUTES: List<Int> = listOf(5, 10, 15)

    fun isBoredIntention(intention: String?): Boolean =
        intention == BORED_INTENTION
}
