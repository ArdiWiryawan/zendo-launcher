package com.zendolauncher.platform

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.zendolauncher.core.DailyUsageSnapshot
import com.zendolauncher.core.RawUsageEvent
import com.zendolauncher.core.UsageCalculator
import com.zendolauncher.core.UsageEventType
import java.time.LocalDate
import java.time.ZoneId

/**
 * Reads today's per-app foreground time and launch count from Android's [UsageStatsManager].
 *
 * Strictly opt-in: if the user has not granted Usage Access in system Settings, [hasAccess]
 * returns false and [readToday] returns [DailyUsageSnapshot.EMPTY] without prompting.
 */
class UsageRepository(private val context: Context) {

    @Suppress("DEPRECATION")
    fun hasAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun readToday(nowMillis: Long = System.currentTimeMillis()): DailyUsageSnapshot {
        if (!hasAccess()) return DailyUsageSnapshot.EMPTY
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return DailyUsageSnapshot.EMPTY

        val zone = ZoneId.systemDefault()
        val dayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        if (nowMillis <= dayStart) return DailyUsageSnapshot.EMPTY

        val rawEvents = ArrayList<RawUsageEvent>()
        val usageEvents = runCatching { manager.queryEvents(dayStart, nowMillis) }.getOrNull()
            ?: return DailyUsageSnapshot.EMPTY

        val event = UsageEvents.Event()
        while (usageEvents.hasNextEvent()) {
            if (!usageEvents.getNextEvent(event)) break
            val mappedType = mapEventType(event.eventType) ?: continue
            val pkg = event.packageName ?: continue
            rawEvents += RawUsageEvent(pkg, mappedType, event.timeStamp)
        }

        return UsageCalculator.aggregate(
            events = rawEvents,
            dayStartMillis = dayStart,
            nowMillis = nowMillis,
            excludedPackages = setOf(context.packageName),
        )
    }

    fun settingsIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    @Suppress("DEPRECATION")
    private fun mapEventType(type: Int): UsageEventType? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && type == UsageEvents.Event.ACTIVITY_RESUMED ->
            UsageEventType.FOREGROUND
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && type == UsageEvents.Event.ACTIVITY_PAUSED ->
            UsageEventType.BACKGROUND
        type == UsageEvents.Event.MOVE_TO_FOREGROUND -> UsageEventType.FOREGROUND
        type == UsageEvents.Event.MOVE_TO_BACKGROUND -> UsageEventType.BACKGROUND
        else -> null
    }
}
