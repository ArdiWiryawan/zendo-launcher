package com.zendolauncher.platform

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import com.zendolauncher.core.WallpaperTemplate
import com.zendolauncher.data.WallpaperTarget

/**
 * Renders a bundled template to a Bitmap and asks the system to use it.
 * Uses official WallpaperManager flags; never touches the lock directly.
 *
 * Returns a sealed result so the UI only says "applied" after the API reports it.
 */
sealed interface WallpaperApplyResult {
    data object Applied : WallpaperApplyResult
    data class Partial(val message: String) : WallpaperApplyResult
    data class Failed(val message: String) : WallpaperApplyResult
}

/** What the device reports about wallpaper before any write is attempted. */
internal data class WallpaperCapability(
    val supported: Boolean = true,
    val allowed: Boolean = true,
)

object WallpaperApplier {

    /**
     * A returned wallpaper ID of 0 or less means the system did not confirm the
     * write (per the setBitmap/setResource contract), so it must not read as success.
     */
    internal fun isConfirmed(wallpaperId: Int): Boolean = wallpaperId > 0

    /** Pre-write capability probe. Unknown (exception) reads as capable, never as a block. */
    internal fun probe(manager: WallpaperManager): WallpaperCapability {
        val supported = runCatching { manager.isWallpaperSupported }.getOrDefault(true)
        val allowed = runCatching { manager.isSetWallpaperAllowed }.getOrDefault(true)
        return WallpaperCapability(supported, allowed)
    }

    /**
     * True when a separate lock target can exist on this device at all. Android 7.0
     * is the platform floor for FLAG_LOCK; the capability probe catches the rest.
     * The wallpaper screen must not offer Lock or Both when this is false.
     */
    fun lockTargetSupported(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 24) return false
        val manager = runCatching { WallpaperManager.getInstance(context) }.getOrNull()
            ?: return false
        val capability = probe(manager)
        return capability.supported && capability.allowed
    }

    /**
     * The 9:20 design drawn for this device's wallpaper size. The top third stays
     * clear for the lock clock; typography starts at 46% height and is sized from
     * the width, so the layout holds at any screen size.
     */
    fun render(template: WallpaperTemplate, width: Int = 1080, height: Int = 1920): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(template.baseColor.toInt())
        val ink = if (template.darkBackground) Color.WHITE else Color.BLACK
        val left = width * 0.11f
        var baseline = height * 0.46f
        template.caption?.let { caption ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ink
                alpha = 235
                textSize = width * 0.065f
                typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            }
            canvas.drawText(caption, left, baseline, paint)
            baseline += paint.textSize * 2.1f
        }
        template.wordmark?.let { mark ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ink
                alpha = 150
                textSize = width * 0.030f
                letterSpacing = 0.42f
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
            }
            canvas.drawText(mark, left, baseline, paint)
        }
        return bitmap
    }

    fun apply(context: Context, template: WallpaperTemplate, target: WallpaperTarget): WallpaperApplyResult {
        val manager = try {
            WallpaperManager.getInstance(context)
        } catch (e: Exception) {
            return WallpaperApplyResult.Failed("Wallpaper service unavailable.")
        }
        // Capability first: a TV without wallpaper support or a managed profile that
        // forbids setting it must read as a clear message, not a silent no-op.
        val capability = probe(manager)
        if (!capability.supported) {
            return WallpaperApplyResult.Failed("Wallpapers are not supported on this device.")
        }
        if (!capability.allowed) {
            return WallpaperApplyResult.Failed("Your device policy does not allow changing the wallpaper.")
        }
        // Render at the device's wallpaper size, not a hardcoded bitmap: oversized
        // bitmaps waste memory (and can kill a low-RAM process mid-apply), while
        // undersized ones upscale blurry. Falls back to 1080x1920 when unset.
        val width = manager.desiredMinimumWidth.takeIf { it > 0 } ?: 1080
        val height = manager.desiredMinimumHeight.takeIf { it > 0 } ?: 1920
        val bitmap = try {
            render(template, width, height)
        } catch (e: Exception) {
            return WallpaperApplyResult.Failed("Could not render wallpaper.")
        }
        return try {
            when (target) {
                WallpaperTarget.HOME -> {
                    val id = manager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                    if (isConfirmed(id)) WallpaperApplyResult.Applied
                    else WallpaperApplyResult.Failed("The system did not confirm the wallpaper.")
                }
                WallpaperTarget.LOCK -> {
                    if (Build.VERSION.SDK_INT < 24) {
                        return WallpaperApplyResult.Failed("Lock wallpaper needs Android 7.0+.")
                    }
                    try {
                        val id = manager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
                        if (isConfirmed(id)) WallpaperApplyResult.Applied
                        else WallpaperApplyResult.Failed("The system did not confirm the lock wallpaper.")
                    } catch (e: Exception) {
                        WallpaperApplyResult.Failed("Lock wallpaper not supported on this device.")
                    }
                }
                WallpaperTarget.BOTH -> {
                    var homeOk = true
                    var lockOk = true
                    var lockMessage: String? = null
                    try {
                        homeOk = isConfirmed(manager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM))
                        if (!homeOk) {
                            return WallpaperApplyResult.Failed("The system did not confirm the wallpaper.")
                        }
                    } catch (e: Exception) {
                        homeOk = false
                    }
                    if (Build.VERSION.SDK_INT >= 24) {
                        try {
                            lockOk = isConfirmed(manager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK))
                            if (!lockOk) lockMessage = "Lock target not confirmed here; Home was applied."
                        } catch (e: Exception) {
                            lockOk = false
                            lockMessage = "Lock target not supported here; Home was applied."
                        }
                    } else {
                        lockOk = false
                        lockMessage = "Lock target needs Android 7.0+; Home was applied."
                    }
                    when {
                        homeOk && lockOk -> WallpaperApplyResult.Applied
                        homeOk && !lockOk -> WallpaperApplyResult.Partial(lockMessage ?: "Lock not applied.")
                        !homeOk -> WallpaperApplyResult.Failed("System rejected the wallpaper.")
                        else -> WallpaperApplyResult.Failed("Could not apply wallpaper.")
                    }
                }
            }
        } catch (e: SecurityException) {
            WallpaperApplyResult.Failed("Missing wallpaper permission.")
        } catch (e: Exception) {
            WallpaperApplyResult.Failed("System rejected the wallpaper.")
        } finally {
            // Recycled after hand-off; setBitmap persists synchronously.
            runCatching { bitmap.recycle() }
        }
    }
}
