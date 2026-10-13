package com.zendolauncher.platform

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.UserManager
import androidx.core.graphics.drawable.toBitmap
import com.zendolauncher.model.LaunchTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Installed launcher activities, discovered through the LauncherApps system service.
 *
 * LauncherApps is used instead of queryIntentActivities so the launcher never needs
 * QUERY_ALL_PACKAGES, and so multi-profile users are handled by the platform.
 * Icons are read lazily and cached, because the default text-first home screen
 * never needs them.
 */
class AppRepository(private val context: Context) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val iconCache = mutableMapOf<String, Drawable>()
    private val activityCache = mutableMapOf<String, android.content.pm.LauncherActivityInfo>()

    private val _apps = MutableStateFlow<List<LaunchTarget>>(emptyList())
    val apps: StateFlow<List<LaunchTarget>> = _apps.asStateFlow()

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String?, user: android.os.UserHandle?) = refresh()
        override fun onPackageRemoved(packageName: String?, user: android.os.UserHandle?) = refresh()
        override fun onPackageChanged(packageName: String?, user: android.os.UserHandle?) = refresh()
        override fun onPackagesAvailable(packageNames: Array<out String>?, user: android.os.UserHandle?, replacing: Boolean) = refresh()
        override fun onPackagesUnavailable(packageNames: Array<out String>?, user: android.os.UserHandle?, replacing: Boolean) = refresh()
    }

    /** Registers for package changes. The first enumeration is the caller's to schedule. */
    fun start() {
        launcherApps.registerCallback(callback)
    }

    fun stop() {
        launcherApps.unregisterCallback(callback)
    }

    fun refresh() {
        val targets = ArrayList<LaunchTarget>()
        iconCache.clear()
        activityCache.clear()
        for (user in userManager.userProfiles) {
            val activities = try {
                launcherApps.getActivityList(null, user)
            } catch (e: SecurityException) {
                emptyList()
            }
            for (activity in activities) {
                if (activity.applicationInfo.packageName == context.packageName) continue
                val target = LaunchTarget(
                    packageName = activity.applicationInfo.packageName,
                    activityName = activity.componentName.className,
                    label = activity.label?.toString()?.takeIf { it.isNotBlank() }
                        ?: activity.applicationInfo.loadLabel(context.packageManager).toString(),
                )
                targets.add(target)
                activityCache[target.key] = activity
            }
        }
        _apps.value = targets.distinctBy { it.key }
    }

    /**
     * Icon for a launch target, or null when the app is gone. Call from a background
     * dispatcher; the drawable is cached so the common case is a map lookup.
     */
    fun iconFor(target: LaunchTarget, sizePx: Int): Drawable? {
        iconCache[target.key]?.let { return it }
        val drawable = activityCache[target.key]?.let { activity ->
            try {
                activity.getIcon(sizePx)
            } catch (e: Exception) {
                null
            }
        }
        if (drawable != null) iconCache[target.key] = drawable
        return drawable
    }

    fun bitmapFor(target: LaunchTarget, sizePx: Int): android.graphics.Bitmap? =
        iconFor(target, sizePx)?.toBitmap(sizePx, sizePx)

    fun activitiesFor(packageName: String): List<android.content.pm.LauncherActivityInfo> =
        try {
            launcherApps.getActivityList(packageName, android.os.Process.myUserHandle())
        } catch (e: SecurityException) {
            emptyList()
        }
}
