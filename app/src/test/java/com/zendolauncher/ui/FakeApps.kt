package com.zendolauncher.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.PackageInfo
import android.os.Process
import org.robolectric.Shadows.shadowOf

/**
 * Installs fake launcher apps into Robolectric's package manager, so screens that draw
 * real rows — the dock first of all — have something honest to draw. Robolectric's
 * LauncherApps starts empty and stays empty; these fixtures stand in for the installed
 * apps the platform would hand the launcher on a device.
 *
 * Both LauncherActivityInfo and its LauncherActivityInfoInternal argument are hidden
 * framework types whose constructors are @hide, so they are assembled reflectively the
 * same way the shadow layer itself would. Shadow state resets between test methods, so
 * every test that needs apps installs its own.
 */
internal object FakeApps {

    /**
     * Installs one launchable app and returns the key the launcher will store for it,
     * in the same `package/activity` form [com.zendolauncher.model.LaunchTarget] uses.
     */
    fun install(
        context: Context,
        packageName: String,
        label: String,
        iconRes: Int = android.R.drawable.ic_menu_edit,
    ): String {
        val pm = context.packageManager
        val appInfo = ApplicationInfo().apply {
            this.packageName = packageName
            name = label
            icon = iconRes
            nonLocalizedLabel = label
            flags = ApplicationInfo.FLAG_HAS_CODE
        }
        val activity = ActivityInfo().apply {
            this.packageName = packageName
            name = "$packageName.Main"
            applicationInfo = appInfo
            icon = iconRes
            nonLocalizedLabel = label
        }
        shadowOf(pm).installPackage(PackageInfo().apply {
            this.packageName = packageName
            applicationInfo = appInfo
        })
        val component = ComponentName(packageName, "$packageName.Main")
        shadowOf(pm).addActivityIfNotPresent(component)

        val user = Process.myUserHandle()
        val internalClass = Class.forName("android.content.pm.LauncherActivityInfoInternal")
        val incrementalClass = Class.forName("android.content.pm.IncrementalStatesInfo")
        val internal = internalClass
            .getConstructor(
                ActivityInfo::class.java,
                incrementalClass,
                android.os.UserHandle::class.java,
                Boolean::class.javaPrimitiveType,
            )
            .newInstance(activity, null, user, false)
        val constructor = LauncherActivityInfo::class.java
            .getDeclaredConstructor(Context::class.java, internalClass)
        constructor.isAccessible = true
        val launcherActivity = constructor.newInstance(context, internal) as LauncherActivityInfo

        val launcherApps = context.getSystemService(android.content.pm.LauncherApps::class.java)
        shadowOf(launcherApps).addActivity(user, launcherActivity)
        return "$packageName/$packageName.Main"
    }
}
