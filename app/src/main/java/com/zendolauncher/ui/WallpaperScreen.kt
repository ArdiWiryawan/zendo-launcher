package com.zendolauncher.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.zendolauncher.core.WallpaperCategory
import com.zendolauncher.core.WallpaperPattern
import com.zendolauncher.core.WallpaperTemplate
import com.zendolauncher.core.WallpaperTemplates
import com.zendolauncher.data.WallpaperTarget
import com.zendolauncher.ui.theme.LauncherColors
import com.zendolauncher.ui.theme.LauncherSpace
import com.zendolauncher.ui.theme.LauncherType
import kotlin.random.Random

/**
 * Minimal wallpaper gallery: browse, preview, apply to Home / Lock / Both.
 * Preview only — nothing is written until Apply reports success.
 */
@Composable
fun WallpaperScreen(viewModel: LauncherViewModel, state: UiState) {
    val palette = LauncherColors.current
    val context = LocalContext.current
    var category by remember { mutableStateOf<WallpaperCategory?>(null) }
    val templates = remember(category) { WallpaperTemplates.byCategory(category) }
    val selected = remember(state.wallpaperSelectedId) {
        WallpaperTemplates.byId(state.wallpaperSelectedId)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.background)
            .systemBarsPadding(),
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LauncherSpace.gutter),
        ) {
            Spacer(Modifier.height(LauncherSpace.lg))
            ScreenTitle("Wallpapers") { QuietAction("Home", onClick = { viewModel.goHome() }) }
            Spacer(Modifier.height(LauncherSpace.xs))
            SecondaryText("Less visual noise. More room to think.")
            Spacer(Modifier.height(LauncherSpace.md))

            // Category filters: text choices, selected marked by weight + hairline.
            ChoiceRow(
                label = "",
                options = listOf(
                    null to "All",
                    WallpaperCategory.SOLID to "Solid",
                    WallpaperCategory.SUBTLE to "Subtle",
                ),
                selected = category,
                onSelect = { category = it },
            )
            Spacer(Modifier.height(LauncherSpace.md))

            // Simple previews, not product cards: color field + name.
            val rows = templates.chunked(3)
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { template ->
                        val isSelected = template.id == state.wallpaperSelectedId
                        Column(
                            Modifier
                                .weight(1f)
                                .clickable(onClick = { viewModel.selectWallpaper(template.id) })
                                .semantics {
                                    contentDescription = "${template.name} wallpaper"
                                    stateDescription = if (isSelected) "Selected" else "Not selected"
                                }
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) palette.primary else palette.separator,
                                )
                                .padding(4.dp),
                        ) {
                            WallpaperPreviewBox(template, Modifier.fillMaxWidth().aspectRatio(1f))
                            Spacer(Modifier.height(4.dp))
                            Text(
                                template.name,
                                style = LauncherType.secondary,
                                color = if (isSelected) palette.primary else palette.secondary,
                            )
                        }
                    }
                    // Fill incomplete rows so columns stay aligned.
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (selected != null) {
                Spacer(Modifier.height(LauncherSpace.md))
                Hairline()
                Spacer(Modifier.height(LauncherSpace.md))
                Text("Preview", style = LauncherType.title, color = palette.primary)
                Spacer(Modifier.height(LauncherSpace.xs))
                SecondaryText(
                    if (selected.darkBackground) "Light text reads well on this wallpaper."
                    else "Dark text reads well on this wallpaper.",
                )
                Spacer(Modifier.height(LauncherSpace.sm))
                // Home vs Lock distinction: two restrained minis, not a fake lock screen.
                Row(horizontalArrangement = Arrangement.spacedBy(LauncherSpace.md)) {
                    MiniHomePreview(selected, Modifier.weight(1f))
                    MiniLockPreview(selected, Modifier.weight(1f))
                }
                Spacer(Modifier.height(LauncherSpace.xs))
                SecondaryText("Miniature preview only. It does not replace your real lock screen.")
                Spacer(Modifier.height(LauncherSpace.md))

                Text("Apply wallpaper to", style = LauncherType.body, color = palette.primary)
                Spacer(Modifier.height(LauncherSpace.sm))
                ChoiceRow(
                    label = "",
                    options = listOf(
                        WallpaperTarget.HOME to "Home",
                        WallpaperTarget.LOCK to "Lock screen",
                        WallpaperTarget.BOTH to "Both",
                    ),
                    selected = state.wallpaperTarget,
                    onSelect = { viewModel.setWallpaperTarget(it) },
                )
                Spacer(Modifier.height(LauncherSpace.md))
                if (state.wallpaperApplying) {
                    SecondaryText("Applying…")
                } else {
                    QuietAction("Apply wallpaper", onClick = { viewModel.applyWallpaper() })
                }
                state.wallpaperMessage?.let {
                    Spacer(Modifier.height(LauncherSpace.xs))
                    SecondaryText(it)
                }
                if (state.wallpaperMessage?.contains("not supported", ignoreCase = true) == true ||
                    state.wallpaperMessage?.contains("needs Android", ignoreCase = true) == true ||
                    state.wallpaperMessage?.contains("did not confirm", ignoreCase = true) == true ||
                    state.wallpaperMessage?.contains("not allow", ignoreCase = true) == true
                ) {
                    QuietAction("Open system picker instead", onClick = {
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_SET_WALLPAPER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        } catch (_: ActivityNotFoundException) {
                        }
                    })
                }
                Spacer(Modifier.height(LauncherSpace.sm))
                SecondaryText(
                    "Lock wallpaper depends on the device (Android 7.0+). " +
                        "If direct apply fails here, the system picker can still set it. " +
                        "This never replaces your lock screen — only its background.",
                )
            } else {
                Spacer(Modifier.height(LauncherSpace.sm))
                EmptyNote("Choose a wallpaper above to preview it.")
            }
            Spacer(Modifier.height(LauncherSpace.xl))
        }
    }
}

@Composable
fun WallpaperPreviewBox(template: WallpaperTemplate, modifier: Modifier = Modifier) {
    val base = Color(template.baseColor.toULong())
    val secondary = Color((template.secondaryColor ?: template.baseColor).toULong())
    Box(modifier.background(base)) {
        when (template.pattern) {
            WallpaperPattern.NONE -> Unit
            WallpaperPattern.GRADIENT -> Box(
                Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(base, secondary))),
            )
            WallpaperPattern.TONAL -> Box(
                Modifier.fillMaxSize().background(Brush.linearGradient(listOf(secondary, base))),
            )
            WallpaperPattern.TWO_TONE -> Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(0.72f).fillMaxWidth().background(base))
                Box(Modifier.weight(0.28f).fillMaxWidth().background(secondary))
            }
            WallpaperPattern.LINES -> Canvas(Modifier.fillMaxSize()) {
                val y = size.height * 0.7f
                drawLine(
                    color = secondary,
                    start = androidx.compose.ui.geometry.Offset(size.width * 0.13f, y),
                    end = androidx.compose.ui.geometry.Offset(size.width * 0.87f, y),
                    strokeWidth = 2f,
                )
            }
            WallpaperPattern.GRAIN -> {
                val seed = remember(template.id) { Random(template.id.hashCode()) }
                Canvas(Modifier.fillMaxSize()) {
                    repeat(160) {
                        drawCircle(
                            color = if (seed.nextBoolean()) secondary else base,
                            radius = 1.5f,
                            center = androidx.compose.ui.geometry.Offset(
                                seed.nextFloat() * size.width,
                                seed.nextFloat() * size.height,
                            ),
                            alpha = 0.25f,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniHomePreview(template: WallpaperTemplate, modifier: Modifier = Modifier) {
    val base = Color(template.baseColor.toULong())
    val onWallpaper = if (template.darkBackground) Color.White else Color.Black
    Column(modifier) {
        SecondaryText("Home")
        Spacer(Modifier.height(4.dp))
        Box {
            WallpaperPreviewBox(template, Modifier.fillMaxWidth().aspectRatio(9f / 14f))
            Column(Modifier.fillMaxWidth().padding(8.dp)) {
                Text("09:41", style = LauncherType.body, color = onWallpaper)
                Text("Essentials", style = LauncherType.secondary, color = onWallpaper.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
private fun MiniLockPreview(template: WallpaperTemplate, modifier: Modifier = Modifier) {
    val onWallpaper = if (template.darkBackground) Color.White else Color.Black
    Column(modifier) {
        SecondaryText("Lock")
        Spacer(Modifier.height(4.dp))
        Box {
            WallpaperPreviewBox(template, Modifier.fillMaxWidth().aspectRatio(9f / 14f))
            Column(
                Modifier.fillMaxWidth().padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(12.dp))
                Text("09:41", style = LauncherType.title, color = onWallpaper)
                Text(
                    "Wednesday, 14 October",
                    style = LauncherType.secondary,
                    color = onWallpaper.copy(alpha = 0.8f),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "2 notifications hidden",
                    style = LauncherType.secondary,
                    color = onWallpaper.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
fun LockWallpaperHint() {
    // Shared guidance line used wherever lock apply is offered.
    SecondaryText(
        "Lock visuals stay with Android: this sets only the background, " +
            "never the clock position, notifications, or unlock method.",
    )
    Spacer(Modifier.height(4.dp))
    Spacer(Modifier.width(0.dp))
}
