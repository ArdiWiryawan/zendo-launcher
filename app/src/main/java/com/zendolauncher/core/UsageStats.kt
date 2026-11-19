package com.zendolauncher.core

enum class UsageEventType {
    FOREGROUND,
    BACKGROUND,
}

data class RawUsageEvent(
    val packageName: String,
    val type: UsageEventType,
    val timestampMillis: Long,
)

data class AppUsageSummary(
    val packageName: String,
    val foregroundMillis: Long,
    val launchCount: Int,
)

data class DailyUsageSnapshot(
    val totalForegroundMillis: Long,
    val totalLaunches: Int,
    val byPackage: Map<String, AppUsageSummary>,
) {
    fun topApps(limit: Int = 6, excludedPackages: Set<String> = emptySet()): List<AppUsageSummary> =
        byPackage.values
            .filter { it.packageName !in excludedPackages && it.foregroundMillis > 0L }
            .sortedByDescending { it.foregroundMillis }
            .take(limit)

    companion object {
        val EMPTY = DailyUsageSnapshot(0L, 0, emptyMap())
    }
}

/**
 * Pure Kotlin aggregator over Android usage events from local midnight to [nowMillis].
 *
 * Counts exact foreground time inside `[dayStartMillis, nowMillis]` and launch count
 * per package so the launcher can show honest, calm screen-time context without
 * background polling or yesterday's bucket bleed.
 */
object UsageCalculator {

    fun aggregate(
        events: List<RawUsageEvent>,
        dayStartMillis: Long,
        nowMillis: Long,
        excludedPackages: Set<String> = emptySet(),
    ): DailyUsageSnapshot {
        if (nowMillis <= dayStartMillis || events.isEmpty()) return DailyUsageSnapshot.EMPTY

        val sorted = events
            .asSequence()
            .filter { it.packageName.isNotBlank() && it.packageName !in excludedPackages }
            .filter { it.timestampMillis in dayStartMillis..nowMillis }
            .sortedBy { it.timestampMillis }
            .toList()

        val activeStart = HashMap<String, Long>()
        val durations = HashMap<String, Long>()
        val launches = HashMap<String, Int>()

        for (event in sorted) {
            when (event.type) {
                UsageEventType.FOREGROUND -> {
                    if (event.packageName !in activeStart) {
                        activeStart[event.packageName] = event.timestampMillis
                        launches[event.packageName] = (launches[event.packageName] ?: 0) + 1
                    }
                }
                UsageEventType.BACKGROUND -> {
                    val started = activeStart.remove(event.packageName) ?: continue
                    val delta = (event.timestampMillis - started).coerceAtLeast(0L)
                    durations[event.packageName] = (durations[event.packageName] ?: 0L) + delta
                }
            }
        }

        // Any session still open at nowMillis counts up to nowMillis.
        for ((pkg, started) in activeStart) {
            val delta = (nowMillis - started).coerceAtLeast(0L)
            durations[pkg] = (durations[pkg] ?: 0L) + delta
        }

        val summaries = HashMap<String, AppUsageSummary>()
        val allPackages = durations.keys + launches.keys
        var totalMillis = 0L
        var totalLaunches = 0
        for (pkg in allPackages) {
            val ms = durations[pkg] ?: 0L
            val count = launches[pkg] ?: 0
            if (ms > 0L || count > 0) {
                summaries[pkg] = AppUsageSummary(pkg, ms, count)
                totalMillis += ms
                totalLaunches += count
            }
        }
        return DailyUsageSnapshot(
            totalForegroundMillis = totalMillis,
            totalLaunches = totalLaunches,
            byPackage = summaries,
        )
    }

    fun formatDuration(millis: Long): String {
        if (millis <= 0L) return "0m"
        val totalMinutes = millis / 60_000L
        if (totalMinutes < 1L) return "<1m"
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return when {
            hours == 0L -> "${minutes}m"
            minutes == 0L -> "${hours}h"
            else -> "${hours}h ${minutes}m"
        }
    }

    fun formatCompactMirror(summary: AppUsageSummary?): String {
        if (summary == null || (summary.foregroundMillis <= 0L && summary.launchCount <= 0)) {
            return "Today: not opened yet"
        }
        val dur = formatDuration(summary.foregroundMillis)
        val opens = summary.launchCount.coerceAtLeast(1)
        return "Today: $dur · opened ${opens}×"
    }
}
