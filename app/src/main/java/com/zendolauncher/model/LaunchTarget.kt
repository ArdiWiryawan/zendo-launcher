package com.zendolauncher.model

/**
 * A launchable entry shown by the launcher: an installed launcher activity, or a
 * pinned shortcut published by another app (how some browsers surface web apps).
 *
 * Deliberately free of Android types so ordering and matching stay unit-testable
 * on the JVM.
 */
data class LaunchTarget(
    val packageName: String,
    val activityName: String?,
    val label: String,
    val shortcutId: String? = null,
) {
    /** Stable identity used for favourites, hiding and ordering. */
    val key: String =
        if (shortcutId != null) "$packageName/$shortcutId" else "$packageName/$activityName"

    val isShortcut: Boolean get() = shortcutId != null
}
