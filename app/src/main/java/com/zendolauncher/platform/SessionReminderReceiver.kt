package com.zendolauncher.platform

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.zendolauncher.LauncherActivity
import com.zendolauncher.R

/**
 * Posts a calm, local notification when a Mindful Gate timebox ends.
 */
class SessionReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val label = intent.getStringExtra(EXTRA_APP_LABEL).orEmpty().ifBlank { "this app" }
        val minutes = intent.getIntExtra(EXTRA_MINUTES, 10)
        val intention = intent.getStringExtra(EXTRA_INTENTION).orEmpty()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        ensureChannel(nm)

        val tapIntent = Intent(context, LauncherActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPending = PendingIntent.getActivity(
            context,
            REQUEST_HOME,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val body = if (intention.isNotBlank()) {
            "Still aligned with \"$intention\"? Tap to return home."
        } else {
            "Your ${minutes}-minute check-in for $label is up."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("$minutes min in $label")
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(contentPending)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        nm.notify(label.hashCode(), notification)
    }

    companion object {
        const val CHANNEL_ID = "zendo_mindful_reminders"
        const val EXTRA_APP_LABEL = "extra_app_label"
        const val EXTRA_MINUTES = "extra_minutes"
        const val EXTRA_INTENTION = "extra_intention"
        private const val REQUEST_HOME = 4102

        fun ensureChannel(nm: NotificationManager) {
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Mindful session reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Calm reminders when a timed social-app check-in ends."
            }
            nm.createNotificationChannel(channel)
        }
    }
}
