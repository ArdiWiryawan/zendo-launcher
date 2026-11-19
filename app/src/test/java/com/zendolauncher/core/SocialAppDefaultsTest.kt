package com.zendolauncher.core

import com.zendolauncher.model.LaunchTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialAppDefaultsTest {

    @Test
    fun `recognises common social media packages and skips utility apps`() {
        assertTrue(SocialAppDefaults.isSocialPackage("com.instagram.android"))
        assertTrue(SocialAppDefaults.isSocialPackage("com.zhiliaoapp.musically"))
        assertTrue(SocialAppDefaults.isSocialPackage("com.twitter.android"))
        assertTrue(SocialAppDefaults.isSocialPackage("com.google.android.youtube"))
        assertFalse(SocialAppDefaults.isSocialPackage("com.google.android.calendar"))
        assertFalse(SocialAppDefaults.isSocialPackage("com.example.notes"))
    }

    @Test
    fun `findSocialKeys returns launch keys for installed social apps only`() {
        val installed = listOf(
            LaunchTarget("com.instagram.android", ".Main", "Instagram"),
            LaunchTarget("com.google.android.calendar", ".AllInOne", "Calendar"),
            LaunchTarget("com.zhiliaoapp.musically", ".Splash", "TikTok"),
            LaunchTarget("com.instagram.android", null, "IG Shortcut", shortcutId = "dm"),
        )
        assertEquals(
            setOf(
                "com.instagram.android/.Main",
                "com.zhiliaoapp.musically/.Splash",
            ),
            SocialAppDefaults.findSocialKeys(installed),
        )
    }
}
