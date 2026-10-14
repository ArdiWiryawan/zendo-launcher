package com.zendolauncher.platform

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import com.zendolauncher.core.WallpaperPattern
import com.zendolauncher.core.WallpaperTemplate
import com.zendolauncher.data.WallpaperTarget
import kotlin.random.Random

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

    fun render(template: WallpaperTemplate, width: Int = 1080, height: Int = 1920): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val base = template.baseColor.toInt()
        val secondary = (template.secondaryColor ?: template.baseColor).toInt()
        canvas.drawColor(base)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        when (template.pattern) {
            WallpaperPattern.NONE -> Unit
            WallpaperPattern.GRADIENT -> {
                paint.shader = LinearGradient(
                    0f, 0f, 0f, height.toFloat(), base, secondary, Shader.TileMode.CLAMP,
                )
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                paint.shader = null
            }
            WallpaperPattern.GRAIN -> {
                val rnd = Random(template.id.hashCode())
                paint.alpha = 14
                repeat(4200) {
                    paint.color = if (rnd.nextBoolean()) -0x1 else secondary
                    val x = rnd.nextFloat() * width
                    val y = rnd.nextFloat() * height
                    canvas.drawCircle(x, y, 1.2f, paint)
                }
                paint.alpha = 255
            }
            WallpaperPattern.LINES -> {
                paint.color = secondary
                paint.strokeWidth = 2f
                val y = height * 0.7f
                canvas.drawLine(width * 0.13f, y, width * 0.87f, y, paint)
            }
            WallpaperPattern.TWO_TONE -> {
                paint.color = secondary
                canvas.drawRect(0f, height * 0.72f, width.toFloat(), height.toFloat(), paint)
                paint.color = base
                paint.alpha = 40
                canvas.drawRect(0f, height * 0.72f, width.toFloat(), height * 0.72f + 3f, paint)
                paint.alpha = 255
            }
            WallpaperPattern.TONAL -> {
                paint.shader = LinearGradient(
                    0f, 0f, width.toFloat(), height.toFloat(), secondary, base, Shader.TileMode.CLAMP,
                )
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                paint.shader = null
            }
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
        val bitmap = try {
            render(template)
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
            // Bitmap is small (1080x1920 ARGB ~8MB); recycle after hand-off.
            runCatching { bitmap.recycle() }
        }
    }
}
