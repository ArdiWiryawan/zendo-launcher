package com.zendolauncher.core

/**
 * Whether the agenda cache is old enough to justify a re-query. A never-loaded
 * cache or one over a minute old refreshes; settling on the page repeatedly does
 * not hammer the provider. Pure so the rule is unit-tested rather than inferred.
 */
object CalendarCache {

    /** One minute: page swipes never re-query; returning from an external calendar does. */
    const val STALE_AFTER_MILLIS = 60_000L

    fun isStale(
        lastRefreshMillis: Long,
        nowMillis: Long,
        staleAfterMillis: Long = STALE_AFTER_MILLIS,
    ): Boolean = lastRefreshMillis <= 0L || nowMillis - lastRefreshMillis > staleAfterMillis
}
