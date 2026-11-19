package com.zendolauncher.core

import com.zendolauncher.model.LaunchTarget

/**
 * Curated package matcher for common social-media and short-video apps so
 * Mindful Gate works out of the box on a fresh install without locking utility apps.
 */
object SocialAppDefaults {

    private val SOCIAL_PACKAGES: Set<String> = setOf(
        "com.instagram.android",
        "com.instagram.barcelona", // Threads
        "com.zhiliaoapp.musically", // TikTok global
        "com.ss.android.ugc.trill", // TikTok Asia
        "com.ss.android.ugc.aweme",
        "com.twitter.android", // X / Twitter
        "com.facebook.katana",
        "com.facebook.lite",
        "com.reddit.frontpage",
        "com.google.android.youtube",
        "com.snapchat.android",
        "com.pinterest",
        "com.kwai.bulldog", // SnackVideo
        "video.like", // Likee
        "tv.twitch.android.app",
    )

    fun isSocialPackage(packageName: String): Boolean =
        packageName.lowercase() in SOCIAL_PACKAGES

    fun findSocialKeys(targets: List<LaunchTarget>): Set<String> =
        targets.asSequence()
            .filter { !it.isShortcut && isSocialPackage(it.packageName) }
            .map { it.key }
            .toSet()
}
