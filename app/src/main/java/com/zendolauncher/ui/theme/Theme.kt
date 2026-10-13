package com.zendolauncher.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
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
    val section = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp)
    val appLabel = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Normal)
    val body = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal)
    val secondary = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal)
    val action = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp)
}

@Composable
fun LauncherTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val palette = when (mode) {
        ThemeMode.LIGHT -> LightPalette
        ThemeMode.DARK -> DarkPalette
        ThemeMode.SYSTEM -> if (isSystemInDarkTheme()) DarkPalette else LightPalette
    }
    val scheme = if (palette.isDark) {
        darkColorScheme(
            background = palette.background,
            surface = palette.background,
            onBackground = palette.primary,
            onSurface = palette.primary,
            primary = palette.primary,
            onPrimary = palette.background,
            outline = palette.separator,
        )
    } else {
        lightColorScheme(
            background = palette.background,
            surface = palette.background,
            onBackground = palette.primary,
            onSurface = palette.primary,
            primary = palette.primary,
            onPrimary = palette.background,
            outline = palette.separator,
        )
    }
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
    }
}
