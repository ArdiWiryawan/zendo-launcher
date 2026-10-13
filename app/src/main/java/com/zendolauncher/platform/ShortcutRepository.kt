package com.zendolauncher.platform

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.UserManager
import com.zendolauncher.model.LaunchTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pinned shortcuts published by other apps.
 *
 * This is how installed web apps reach a launcher: a browser that installs a PWA
 * usually publishes a pinned shortcut, and Android's own WebAPK flow registers a
 * real launcher activity (which [AppRepository] picks up directly). Only pinned
 * shortcuts are queried — dynamic and manifest shortcuts need permissions a launcher
 * should not ask for. The platform only hands pinned shortcuts to the current
 * default home app, so [available] stays false until the user selects this launcher.
 */
class ShortcutRepository(private val context: Context) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    private val _shortcuts = MutableStateFlow<List<LaunchTarget>>(emptyList())
    val shortcuts: StateFlow<List<LaunchTarget>> = _shortcuts.asStateFlow()

    private val _available = MutableStateFlow(false)
    val available: StateFlow<Boolean> = _available.asStateFlow()

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String?, user: android.os.UserHandle?) = refresh()
        override fun onPackageRemoved(packageName: String?, user: android.os.UserHandle?) = refresh()
        override fun onPackageChanged(packageName: String?, user: android.os.UserHandle?) = refresh()
        override fun onPackagesAvailable(packageNames: Array<out String>?, user: android.os.UserHandle?, replacing: Boolean) = refresh()
        override fun onPackagesUnavailable(packageNames: Array<out String>?, user: android.os.UserHandle?, replacing: Boolean) = refresh()
    }

    /** Registers for package changes. The first query is the caller's to schedule. */
    fun start() {
        launcherApps.registerCallback(callback)
    }

    fun stop() {
        launcherApps.unregisterCallback(callback)
    }

    fun refresh() {
        val query = LauncherApps.ShortcutQuery().apply {
            setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
        }
        val found = ArrayList<LaunchTarget>()
        var allowed = false
        for (user in userManager.userProfiles) {
            val shortcuts: List<ShortcutInfo> = try {
                allowed = true
                launcherApps.getShortcuts(query, user) ?: emptyList()
            } catch (e: RuntimeException) {
                // A home screen must never crash because the platform refused or
                // failed a shortcut query; show no shortcuts instead.
                allowed = false
                emptyList()
            }
            for (shortcut in shortcuts) {
                val label = shortcut.shortLabel?.toString()?.takeIf { it.isNotBlank() } ?: continue
                found.add(
                    LaunchTarget(
                        packageName = shortcut.`package`,
                        activityName = null,
                        label = label,
                        shortcutId = shortcut.id,
                    ),
                )
            }
        }
        _available.value = allowed
        _shortcuts.value = found.distinctBy { it.key }
    }

    /** True when this launcher is the active home app and may read pinned shortcuts. */
    fun isDefaultHome(): Boolean {
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == context.packageName
    }
}
