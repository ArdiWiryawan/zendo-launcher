package com.zendolauncher.platform

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Process
import android.widget.Toast
import com.zendolauncher.model.LaunchTarget

/**
 * Starts apps. Every failure path ends in a message rather than a crash: an app can
 * be uninstalled, disabled, or restricted between the moment the home screen renders
 * and the moment the user taps it.
 */
class AppLauncher(private val context: Context) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)

    fun launch(target: LaunchTarget) {
        val started = if (target.isShortcut) {
            launchShortcut(target)
        } else {
            launchActivity(target)
        }
        if (!started) {
            Toast.makeText(context, "Can't open ${target.label}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchActivity(target: LaunchTarget): Boolean {
        val component = ComponentName(target.packageName, target.activityName ?: return false)
        return try {
            launcherApps.startMainActivity(component, Process.myUserHandle(), null, null)
            true
        } catch (e: Exception) {
            fallbackIntent(target.packageName)
        }
    }

    private fun launchShortcut(target: LaunchTarget): Boolean {
        val id = target.shortcutId ?: return false
        return try {
            launcherApps.startShortcut(target.packageName, id, null, null, Process.myUserHandle())
            true
        } catch (e: Exception) {
            fallbackIntent(target.packageName)
        }
    }

    /**
     * Last resort for a package whose recorded entry no longer starts: ask PackageManager
     * for the package's launch intent and start that instead.
     */
    private fun fallbackIntent(packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            return try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } catch (e: Exception) {
                false
            }
        }
        return false
    }
}
