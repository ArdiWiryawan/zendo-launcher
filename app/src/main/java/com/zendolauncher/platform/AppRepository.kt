package com.zendolauncher.platform

import android.content.Context
import android.content.pm.LauncherApps
import android.os.UserManager
import com.zendolauncher.model.LaunchTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Installed launcher activities, discovered through the LauncherApps system service.
 *
 * LauncherApps is used instead of queryIntentActivities so the launcher never needs
 * QUERY_ALL_PACKAGES, and so multi-profile users are handled by the platform.
 *
 * No icon is ever read. Home, the drawer and every list are text; loading and tinting
 * bitmaps was work that existed only to draw something the product no longer shows.
 */
class AppRepository(private val context: Context) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

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
            }
        }
        _apps.value = targets.distinctBy { it.key }
    }
}
