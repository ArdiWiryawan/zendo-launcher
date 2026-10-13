package com.zendolauncher.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zendolauncher.data.ThemeMode

/**
 * Monochrome palette. Two tones of text and one hairline separator carry the whole
 * hierarchy, so there is nothing to make loud.
 */
@Immutable
data class LauncherPalette(
    val background: Color,
    val primary: Color,
    val secondary: Color,
    val separator: Color,
    val isDark: Boolean,
)

val DarkPalette = LauncherPalette(
    background = Color(0xFF0B0B0C),
    primary = Color(0xFFF2F2F0),
    secondary = Color(0xFF8A8A88),
    separator = Color(0xFF1F1F21),
    isDark = true,
)

val LightPalette = LauncherPalette(
    background = Color(0xFFF7F7F5),
    primary = Color(0xFF111112),
    secondary = Color(0xFF6E6E6C),
    separator = Color(0xFFE2E2DE),
    isDark = false,
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

object LauncherColors {
    val current: LauncherPalette
        @Composable get() = LocalPalette.current
}

/**
 * Spacing scale. Screens used to carry inline dp literals with no shared vocabulary;
 * these are the only values a screen needs. [gutter] is the screen margin and
 * [touchTarget] is the Android minimum interactive size.
 */
object LauncherSpace {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 40.dp
    val gutter = 24.dp
    val hairline = 1.dp
    val touchTarget = 48.dp
}

/**
 * Type scale. The clock is the only large element; everything else is quiet.
 * Nothing is uppercase and only section headings carry letter-spacing, so the
 * hierarchy comes from size and colour rather than from shouting.
 */
object LauncherType {
    val clock = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 60.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = (-1.5).sp,
    )
    val date = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal)

    /** Screen titles. Without this a title renders at list-row size and reads as a row. */
    val title = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Normal)
    val section = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp)

    /** Explicit line heights: text-heavy rows otherwise inherit the font default. */
    val appLabel = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Normal, lineHeight = 24.sp)
    val body = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp)
    val secondary = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal)
    val action = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp)
}

/**
 * The clock is the one large element, so it must never clip. At a raised system font
 * scale a fixed 60.sp renders wider than the screen. This shrinks the requested size
 * so the widest realistic clock string still fits the available width.
 *
 * Pure: depends only on its arguments. Ceiling is [base]; floor is 28sp. Upgrade path:
 * per-glyph autosize once `TextAutoSize` ships in the pinned Compose version.
 */
fun clockSizeSp(screenWidthDp: Int, fontScale: Float, base: Float = 60f): Float {
    if (fontScale <= 0f) return base
    val available = screenWidthDp - 2 * LauncherSpace.gutter.value
    // Widest realistic string is "12:59" plus the AM/PM run. The per-character advances
    // are deliberately pessimistic: Roboto's digits advance at roughly 0.60em, and
    // guessing low here is what would let the clock clip on a device whose font is
    // slightly wider than expected. Over-estimating only costs a little size at very
    // large font scales, which is the safe direction to be wrong in.
    val digitsEm = 4 * 0.60f
    val colonEm = 0.30f
    val suffixEm = 2 * 0.55f
    val gapDp = LauncherSpace.sm.value
    val suffixSp = suffixEm * LauncherType.secondary.fontSize.value
    val fitted = ((available - gapDp) / fontScale - suffixSp) / (digitsEm + colonEm)
    return fitted.coerceIn(28f, base)
}

/**
 * Material3 reads roles the launcher never sets — `DropdownMenu` takes its container
 * from `surfaceContainer`, its item colour from `onSurfaceVariant`, and any elevated
 * surface from `surfaceTint`. Unset roles fall back to Material's tonal palette, which
 * is how the one popup in the product ended up off-identity. Mapping every role onto
 * the two-tone palette keeps Material components looking like the launcher.
 */
private fun ColorScheme.inLauncherPalette(palette: LauncherPalette): ColorScheme = copy(
    background = palette.background,
    surface = palette.background,
    onBackground = palette.primary,
    onSurface = palette.primary,
    primary = palette.primary,
    onPrimary = palette.background,
    outline = palette.separator,
    // Page-level containers stay the page.
    surfaceContainerLowest = palette.background,
    surfaceContainerLow = palette.background,
    surfaceVariant = palette.background,
    secondaryContainer = palette.background,
    // Elevated containers need to separate from the page; the separator tone is the
    // palette's single step off the background, and it is the menu's container.
    surfaceContainer = palette.separator,
    surfaceContainerHigh = palette.separator,
    surfaceContainerHighest = palette.separator,
    onSurfaceVariant = palette.primary,
    onSecondaryContainer = palette.primary,
    outlineVariant = palette.separator,
    inverseSurface = palette.primary,
    inverseOnSurface = palette.background,
    // Transparent, or Material3 tints every elevated surface toward it.
    surfaceTint = Color.Transparent,
    scrim = palette.background,
    error = palette.primary,
    onError = palette.background,
)

@Composable
fun LauncherTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val palette = when (mode) {
        ThemeMode.LIGHT -> LightPalette
        ThemeMode.DARK -> DarkPalette
        ThemeMode.SYSTEM -> if (isSystemInDarkTheme()) DarkPalette else LightPalette
    }
    val scheme = if (palette.isDark) {
        darkColorScheme().inLauncherPalette(palette)
    } else {
        lightColorScheme().inLauncherPalette(palette)
    }
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
    }
}
