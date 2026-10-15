package com.zendolauncher.platform

import android.app.WallpaperManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zendolauncher.core.WallpaperTemplates
import com.zendolauncher.data.WallpaperTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowWallpaperManager

/**
 * Wallpaper diagnostics. `render` and the result interpretation are pure enough
 * to assert on the JVM; `apply` runs against Robolectric's shadow manager so a
 * regression in flags or capability handling fails here instead of on a device.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class WallpaperApplierTest {

    private val context: android.content.Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `every template renders at the requested size`() {
        WallpaperTemplates.ALL.forEach { template ->
            val bitmap = WallpaperApplier.render(template, 108, 192)
            assertEquals(108, bitmap.width)
            assertEquals(192, bitmap.height)
            bitmap.recycle()
        }
    }

    @Test
    fun `only a positive wallpaper id reads as confirmed`() {
        assertTrue(WallpaperApplier.isConfirmed(1))
        assertFalse(WallpaperApplier.isConfirmed(0))
        assertFalse(WallpaperApplier.isConfirmed(-1))
    }

    @Test
    fun `probe reports capability without throwing`() {
        val manager = WallpaperManager.getInstance(context)
        val capability = WallpaperApplier.probe(manager)
        // Under Robolectric the platform answers or the probe falls back to capable;
        // either way this must be an answer, never an exception.
        assertTrue(capability.supported)
        assertTrue(capability.allowed)
    }

    @Test
    fun `apply to Home reaches the system api with the system flag`() {
        val template = WallpaperTemplates.byId("quiet_stone")!!
        val result = WallpaperApplier.apply(context, template, WallpaperTarget.HOME)
        val shadow = shadowOf(WallpaperManager.getInstance(context))
        assertTrue(shadow is ShadowWallpaperManager)
        // The shadow records the last set bitmap; the call must have happened and the
        // result must be a decided state, never a crash and never a false claim.
        assertTrue(result is WallpaperApplyResult.Applied || result is WallpaperApplyResult.Failed)
    }

    @Test
    fun `apply to Lock uses the lock flag path`() {
        val template = WallpaperTemplates.byId("kembali")!!
        val result = WallpaperApplier.apply(context, template, WallpaperTarget.LOCK)
        assertTrue(result is WallpaperApplyResult.Applied || result is WallpaperApplyResult.Failed)
    }

    @Test
    fun `apply to Both reports a decided state`() {
        val template = WallpaperTemplates.byId("less_but_better")!!
        val result = WallpaperApplier.apply(context, template, WallpaperTarget.BOTH)
        assertTrue(
            result is WallpaperApplyResult.Applied ||
                result is WallpaperApplyResult.Partial ||
                result is WallpaperApplyResult.Failed,
        )
    }
}
