package com.zendolauncher.platform

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Schedules a one-shot local reminder notification when the user chooses a session
 * timebox in the Mindful Gate. Uses inexact [AlarmManager.setAndAllowWhileIdle] so no
 * exact-alarm permission or persistent background service is required.
 */
class SessionReminderScheduler(private val context: Context) {

    fun hasNotificationPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    fun schedule(
        targetKey: String,
        appLabel: String,
        minutes: Int,
        intention: String?,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        if (minutes <= 0) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, SessionReminderReceiver::class.java).apply {
            putExtra(SessionReminderReceiver.EXTRA_APP_LABEL, appLabel)
            putExtra(SessionReminderReceiver.EXTRA_MINUTES, minutes)
            putExtra(SessionReminderReceiver.EXTRA_INTENTION, intention.orEmpty())
        }
        val pending = PendingIntent.getBroadcast(
            context,
            targetKey.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val triggerAt = nowMillis + minutes * 60_000L
        runCatching {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }
}
