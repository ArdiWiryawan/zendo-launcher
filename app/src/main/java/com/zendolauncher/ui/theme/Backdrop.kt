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
 * Curated calm atmosphere effects. Can render on their own matte surface OR
 * layer delicately on top of the user's custom photo + legibility scrim.
 */
enum class BackdropStyle {
    MATTE,
    WINDOW_SHADOW,
    WASHI_LINEN,
    MORNING_MIST,
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
}

fun ScrimStrength.label(): String = when (this) {
    ScrimStrength.SUBTLE -> "Subtle"
    ScrimStrength.BALANCED -> "Balanced"
    ScrimStrength.DEEP -> "Deep"
}

/**
 * Renders the Home backdrop: optional custom photo + legibility scrim + procedural
 * atmosphere layer (Matte, Window shadow, Washi linen, or Morning mist).
 */
@Composable
fun HomeBackdrop(
    style: BackdropStyle,
    scrimStrength: ScrimStrength,
    useCustomPhoto: Boolean,
    blurPhoto: Boolean,
    customBitmap: Bitmap?,
    modifier: Modifier = Modifier,
) {
    val palette = LauncherColors.current
    val hasPhoto = useCustomPhoto && customBitmap != null

    Box(modifier.background(palette.background)) {
        if (hasPhoto) {
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
            val baseAlpha = when (scrimStrength) {
                ScrimStrength.SUBTLE -> if (palette.isDark) 0.40f else 0.48f
                ScrimStrength.BALANCED -> if (palette.isDark) 0.60f else 0.66f
                ScrimStrength.DEEP -> if (palette.isDark) 0.78f else 0.82f
            }
            val blurFallbackBoost = if (blurPhoto && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) 0.10f else 0f
            Box(
                Modifier
                    .fillMaxSize()
                    .background(palette.background.copy(alpha = (baseAlpha + blurFallbackBoost).coerceAtMost(0.90f))),
            )
            if (style != BackdropStyle.MATTE) {
                Canvas(Modifier.fillMaxSize()) {
                    when (style) {
                        BackdropStyle.WINDOW_SHADOW -> drawWindowShadowOverlay(palette, intensity = 0.85f)
                        BackdropStyle.WASHI_LINEN -> drawWashiLinenOverlay(palette, intensity = 0.90f)
                        BackdropStyle.MORNING_MIST -> drawMorningMistOverlay(palette, intensity = 0.65f)
                        BackdropStyle.MATTE -> Unit
                    }
                }
            }
        } else {
            val scrimAlpha = when (scrimStrength) {
                ScrimStrength.SUBTLE -> 0.0f
                ScrimStrength.BALANCED -> 0.14f
                ScrimStrength.DEEP -> 0.34f
            }
            Canvas(Modifier.fillMaxSize()) {
                when (style) {
                    BackdropStyle.MATTE -> drawMatteSurface(palette)
                    BackdropStyle.WINDOW_SHADOW -> {
                        drawMatteSurface(palette)
                        drawWindowShadowOverlay(palette, intensity = 1f)
                    }
                    BackdropStyle.WASHI_LINEN -> {
                        drawMatteSurface(palette)
                        drawWashiLinenOverlay(palette, intensity = 1f)
                    }
                    BackdropStyle.MORNING_MIST -> {
                        drawMatteSurface(palette)
                        drawMorningMistOverlay(palette, intensity = 1f)
                    }
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

private fun DrawScope.drawWindowShadowOverlay(palette: LauncherPalette, intensity: Float) {
    val warmLight = if (palette.isDark) Color(0xFF332F28) else Color(0xFFF9F8F2)
    val shadowBand = if (palette.isDark) Color(0xFF0A0A0B) else Color(0xFFBDC5B4)

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                warmLight.copy(alpha = (if (palette.isDark) 0.45f else 0.62f) * intensity),
                Color.Transparent,
            ),
            center = Offset(size.width * 0.88f, size.height * 0.14f),
            radius = size.maxDimension * 0.78f,
        ),
        center = Offset(size.width * 0.88f, size.height * 0.14f),
        radius = size.maxDimension * 0.78f,
    )

    val bandAlpha = (if (palette.isDark) 0.30f else 0.24f) * intensity
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

    drawSubtleGrain(palette, alpha = 0.022f * intensity)
}

private fun DrawScope.drawWashiLinenOverlay(palette: LauncherPalette, intensity: Float) {
    val halo = if (palette.isDark) Color(0xFF282724) else Color(0xFFF6F7F1)
    val threadColor = if (palette.isDark) Color(0xFFECEAE4) else Color(0xFF3E443A)

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                halo.copy(alpha = 0.50f * intensity),
                Color.Transparent,
            ),
            center = Offset(size.width * 0.5f, size.height * 0.46f),
            radius = size.maxDimension * 0.58f,
        ),
        center = Offset(size.width * 0.5f, size.height * 0.46f),
        radius = size.maxDimension * 0.58f,
    )

    val stepPx = 10.dp.toPx().coerceAtLeast(8f)
    val threadAlpha = (if (palette.isDark) 0.032f else 0.034f) * intensity
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

    drawSubtleGrain(palette, alpha = 0.024f * intensity)
}

private fun DrawScope.drawMorningMistOverlay(palette: LauncherPalette, intensity: Float) {
    val mistMid = if (palette.isDark) Color(0xFF2B2E2D) else Color(0xFFE3E8DF)
    val ridgeFar = if (palette.isDark) Color(0xFF222524) else Color(0xFFD0D9CA)
    val ridgeNear = if (palette.isDark) Color(0xFF141516) else Color(0xFFC2CCBC)

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                mistMid.copy(alpha = 0.68f * intensity),
                Color.Transparent,
            ),
            center = Offset(size.width * 0.42f, size.height * 0.64f),
            radius = size.width * 0.95f,
        ),
        center = Offset(size.width * 0.42f, size.height * 0.64f),
        radius = size.width * 0.95f,
    )

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
    drawPath(farPath, color = ridgeFar.copy(alpha = (if (palette.isDark) 0.58f else 0.52f) * intensity))

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
    drawPath(nearPath, color = ridgeNear.copy(alpha = (if (palette.isDark) 0.72f else 0.58f) * intensity))

    drawSubtleGrain(palette, alpha = 0.02f * intensity)
}

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
