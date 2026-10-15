package com.zendolauncher.platform

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import com.zendolauncher.model.AppUsage
import java.util.Calendar

/**
 * Optional local usage summary, numbers only. Read-only against
 * `UsageStatsManager`; never enforcement, never upload, never background work.
 *
 * Access is special (granted in system Settings → Usage Access, revocable
 * there). The repository only reads: without access every query is empty,
 * and every platform call is guarded so an OEM without the service answers
 * empty instead of crashing.
 */
class UsageRepository(private val context: Context) {

    fun hasAccess(): Boolean {
        if (Build.VERSION.SDK_INT < 23) return false
        return try {
            val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
            ops.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            ) == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    /** Today's per-app foreground minutes, highest first. Empty without access. */
    fun todaySummary(): List<AppUsage> {
        if (!hasAccess()) return emptyList()
        return try {
            val manager = context.getSystemService(UsageStatsManager::class.java)
                ?: return emptyList()
            val start = Calendar.getInstance().let {
                it.set(Calendar.HOUR_OF_DAY, 0)
                it.set(Calendar.MINUTE, 0)
                it.set(Calendar.SECOND, 0)
                it.set(Calendar.MILLISECOND, 0)
                it.timeInMillis
            }
            val now = System.currentTimeMillis()
            manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, now)
                .orEmpty()
                .filter { it.totalTimeInForeground > 0 }
                .map { AppUsage(it.packageName, (it.totalTimeInForeground / 60_000).toInt()) }
                .sortedByDescending { it.minutes }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
