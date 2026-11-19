package com.zendolauncher.ui.theme

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

/**
 * Curated calm Home backgrounds, plus the user's own photo behind a legibility scrim.
 *
 * Every bundled style is drawn procedurally so it scales cleanly to any aspect ratio,
 * adapts to both Soft Matte Charcoal and Warm Sage Paper, and keeps visual frequency
 * low behind text.
 */
enum class BackdropStyle {
    MATTE,
    WINDOW_SHADOW,
    WASHI_LINEN,
    MORNING_MIST,
    CUSTOM_PHOTO,
}

enum class ScrimStrength {
    SUBTLE,
    BALANCED,
    DEEP,
}

fun BackdropStyle.label(): String = when (this) {
    BackdropStyle.MATTE -> "Matte"
    BackdropStyle.WINDOW_SHADOW -> "Window shadow"
    BackdropStyle.WASHI_LINEN -> "Washi linen"
    BackdropStyle.MORNING_MIST -> "Morning mist"
    BackdropStyle.CUSTOM_PHOTO -> "Custom photo"
}

fun ScrimStrength.label(): String = when (this) {
    ScrimStrength.SUBTLE -> "Subtle"
    ScrimStrength.BALANCED -> "Balanced"
    ScrimStrength.DEEP -> "Deep"
}

/**
 * Renders the Home backdrop and a theme-matched legibility scrim.
 */
@Composable
fun HomeBackdrop(
    style: BackdropStyle,
    scrimStrength: ScrimStrength,
    blurPhoto: Boolean,
    customBitmap: Bitmap?,
    modifier: Modifier = Modifier,
) {
    val palette = LauncherColors.current
    val resolvedStyle = if (style == BackdropStyle.CUSTOM_PHOTO && customBitmap == null) {
        BackdropStyle.MATTE
    } else {
        style
    }

    Box(modifier.background(palette.background)) {
        if (resolvedStyle == BackdropStyle.CUSTOM_PHOTO && customBitmap != null) {
            val imageBitmap = remember(customBitmap) { customBitmap.asImageBitmap() }
            val imageModifier = if (blurPhoto && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Modifier.fillMaxSize().blur(18.dp)
            } else {
                Modifier.fillMaxSize()
            }
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = imageModifier,
            )
            // Adaptive scrim keeps centered/left text readable over any personal photo.
            val baseAlpha = when (scrimStrength) {
                ScrimStrength.SUBTLE -> if (palette.isDark) 0.42f else 0.50f
                ScrimStrength.BALANCED -> if (palette.isDark) 0.62f else 0.68f
                ScrimStrength.DEEP -> if (palette.isDark) 0.78f else 0.82f
            }
            val blurFallbackBoost = if (blurPhoto && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) 0.10f else 0f
            Box(
                Modifier
                    .fillMaxSize()
                    .background(palette.background.copy(alpha = (baseAlpha + blurFallbackBoost).coerceAtMost(0.90f))),
            )
        } else {
            val scrimAlpha = when (scrimStrength) {
                ScrimStrength.SUBTLE -> 0.0f
                ScrimStrength.BALANCED -> 0.14f
                ScrimStrength.DEEP -> 0.34f
            }
            Canvas(Modifier.fillMaxSize()) {
                when (resolvedStyle) {
                    BackdropStyle.MATTE -> drawMatteSurface(palette)
                    BackdropStyle.WINDOW_SHADOW -> drawWindowShadow(palette)
                    BackdropStyle.WASHI_LINEN -> drawWashiLinen(palette)
                    BackdropStyle.MORNING_MIST -> drawMorningMist(palette)
                    BackdropStyle.CUSTOM_PHOTO -> drawMatteSurface(palette)
                }
                if (scrimAlpha > 0f) {
                    drawRect(palette.background.copy(alpha = scrimAlpha))
                }
            }
        }
    }
}

private fun DrawScope.drawMatteSurface(palette: LauncherPalette) {
    val topTone = if (palette.isDark) Color(0xFF1E1D1B) else Color(0xFFF2F3ED)
    val bottomTone = if (palette.isDark) Color(0xFF131314) else Color(0xFFE2E6DC)
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(topTone, palette.background, bottomTone),
        ),
    )
    drawSubtleGrain(palette, alpha = if (palette.isDark) 0.025f else 0.022f)
}

/**
 * Window Shadow / Dappled Light: warm diagonal light wash paired with soft blind shadows
 * so the screen reads like physical paper beside a sunlit window.
 */
private fun DrawScope.drawWindowShadow(palette: LauncherPalette) {
    val warmLight = if (palette.isDark) Color(0xFF2B2823) else Color(0xFFF6F5EE)
    val deepCorner = if (palette.isDark) Color(0xFF121213) else Color(0xFFDCE1D5)
    val shadowBand = if (palette.isDark) Color(0xFF0E0E0F) else Color(0xFFC8CFC0)

    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(deepCorner, palette.background, warmLight),
            start = Offset(0f, size.height),
            end = Offset(size.width, 0f),
        ),
    )

    // Soft radial daylight bloom from top-right
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                warmLight.copy(alpha = if (palette.isDark) 0.55f else 0.72f),
                Color.Transparent,
            ),
            center = Offset(size.width * 0.88f, size.height * 0.14f),
            radius = size.maxDimension * 0.78f,
        ),
        center = Offset(size.width * 0.88f, size.height * 0.14f),
        radius = size.maxDimension * 0.78f,
    )

    // Three gentle diagonal window-slat shadows across upper/middle space
    val bandAlpha = if (palette.isDark) 0.24f else 0.18f
    for (i in 0..2) {
        val yOffset = size.height * (0.06f + i * 0.22f)
        val bandHeight = size.height * 0.09f
        val path = Path().apply {
            moveTo(-size.width * 0.1f, yOffset + size.height * 0.16f)
            lineTo(size.width * 1.1f, yOffset - size.height * 0.08f)
            lineTo(size.width * 1.1f, yOffset - size.height * 0.08f + bandHeight)
            lineTo(-size.width * 0.1f, yOffset + size.height * 0.16f + bandHeight)
            close()
        }
        drawPath(path, color = shadowBand.copy(alpha = bandAlpha))
    }

    drawSubtleGrain(palette, alpha = 0.025f)
}

/**
 * Tactile Linen / Washi Paper: subtle woven horizontal/vertical fibers and a gentle
 * central halo that frames a centered app list without adding clutter.
 */
private fun DrawScope.drawWashiLinen(palette: LauncherPalette) {
    val outer = if (palette.isDark) Color(0xFF131314) else Color(0xFFE1E5DB)
    val halo = if (palette.isDark) Color(0xFF21201E) else Color(0xFFF3F4EE)
    val threadColor = if (palette.isDark) Color(0xFFECEAE4) else Color(0xFF3E443A)

    drawRect(color = outer)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(halo, palette.background, outer),
            center = Offset(size.width * 0.5f, size.height * 0.46f),
            radius = size.maxDimension * 0.62f,
        ),
        center = Offset(size.width * 0.5f, size.height * 0.46f),
        radius = size.maxDimension * 0.62f,
    )

    // Subtle linen weave lines
    val stepPx = 10.dp.toPx().coerceAtLeast(8f)
    val threadAlpha = if (palette.isDark) 0.022f else 0.024f
    var y = 0f
    while (y < size.height) {
        drawLine(
            color = threadColor.copy(alpha = threadAlpha),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f,
        )
        y += stepPx
    }
    var x = 0f
    while (x < size.width) {
        drawLine(
            color = threadColor.copy(alpha = threadAlpha * 0.75f),
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = 1f,
        )
        x += stepPx * 1.4f
    }

    drawSubtleGrain(palette, alpha = 0.022f)
}

/**
 * Morning Mist / Soft Horizon: layered atmospheric mist and soft distant hill silhouettes
 * in the lower third, keeping the center clean for reading.
 */
private fun DrawScope.drawMorningMist(palette: LauncherPalette) {
    val skyTop = if (palette.isDark) Color(0xFF1B1C1E) else Color(0xFFF0F2EB)
    val mistMid = if (palette.isDark) Color(0xFF232524) else Color(0xFFE3E8DF)
    val ridgeFar = if (palette.isDark) Color(0xFF1E201F) else Color(0xFFD5DDD0)
    val ridgeNear = if (palette.isDark) Color(0xFF151617) else Color(0xFFC9D2C4)

    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(skyTop, palette.background, mistMid),
        ),
    )

    // Soft mist glow near horizon
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                mistMid.copy(alpha = 0.85f),
                Color.Transparent,
            ),
            center = Offset(size.width * 0.42f, size.height * 0.64f),
            radius = size.width * 0.95f,
        ),
        center = Offset(size.width * 0.42f, size.height * 0.64f),
        radius = size.width * 0.95f,
    )

    // Far organic horizon silhouette
    val farPath = Path().apply {
        moveTo(0f, size.height * 0.72f)
        cubicTo(
            size.width * 0.28f, size.height * 0.65f,
            size.width * 0.65f, size.height * 0.77f,
            size.width, size.height * 0.69f,
        )
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }
    drawPath(farPath, color = ridgeFar.copy(alpha = if (palette.isDark) 0.55f else 0.48f))

    // Near softer ridge
    val nearPath = Path().apply {
        moveTo(0f, size.height * 0.82f)
        cubicTo(
            size.width * 0.36f, size.height * 0.78f,
            size.width * 0.72f, size.height * 0.87f,
            size.width, size.height * 0.80f,
        )
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }
    drawPath(nearPath, color = ridgeNear.copy(alpha = if (palette.isDark) 0.70f else 0.55f))

    drawSubtleGrain(palette, alpha = 0.02f)
}

/** Deterministic, zero-allocation micro-grain so matte surfaces feel tactile rather than flat. */
private fun DrawScope.drawSubtleGrain(palette: LauncherPalette, alpha: Float) {
    val dotColor = if (palette.isDark) Color(0xFFECEAE4) else Color(0xFF1D1F1C)
    val step = 18.dp.toPx().coerceAtLeast(12f)
    var row = 0
    var y = step * 0.5f
    while (y < size.height) {
        var col = 0
        var x = step * 0.5f + if (row % 2 == 1) step * 0.4f else 0f
        while (x < size.width) {
            val hash = ((row * 73856093) xor (col * 19349663)) and 0xFF
            if (hash < 96) {
                drawCircle(
                    color = dotColor.copy(alpha = alpha * (0.5f + (hash / 192f))),
                    radius = 0.9f,
                    center = Offset(x, y),
                )
            }
            x += step
            col++
        }
        y += step
        row++
    }
}
