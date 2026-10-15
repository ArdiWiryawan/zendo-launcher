package com.zendolauncher.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zendolauncher.core.WallpaperTemplate
import com.zendolauncher.core.WallpaperTemplates
import com.zendolauncher.data.WallpaperTarget
import com.zendolauncher.platform.WallpaperApplier
import com.zendolauncher.ui.theme.LauncherColors
import com.zendolauncher.ui.theme.LauncherSpace
import com.zendolauncher.ui.theme.LauncherType

/**
 * The wallpaper gallery: three designs, one preview, and an honest apply.
 * Nothing is written until the system confirms it; a device without a lock
 * target is never offered one; a failure keeps a real way out (the system
 * picker) instead of a dead end.
 */
@Composable
fun WallpaperScreen(viewModel: LauncherViewModel, state: UiState) {
    val palette = LauncherColors.current
    val context = LocalContext.current
    val lockSupported = remember { WallpaperApplier.lockTargetSupported(context) }
    val selectedId = state.wallpaperSelectedId ?: WallpaperTemplates.DEFAULT_ID
    val selected = remember(selectedId) {
        WallpaperTemplates.byId(selectedId) ?: WallpaperTemplates.default()
    }

    // A device without a separate lock target must not keep one selected: the
    // choice snaps Home so the row and the apply always agree.
    LaunchedEffect(lockSupported) {
        if (!lockSupported && state.wallpaperTarget != WallpaperTarget.HOME) {
            viewModel.setWallpaperTarget(WallpaperTarget.HOME)
        }
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
            SecondaryText(
                "Three quiet designs, drawn for a 9:20 screen. The top stays clear " +
                    "for the lock clock; anything set sits below it.",
            )
            Spacer(Modifier.height(LauncherSpace.md))

            // The three designs as tall thumbnails, not product cards.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WallpaperTemplates.ALL.forEach { template ->
                    val isSelected = template.id == selectedId
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
                        WallpaperPreviewBox(
                            template,
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(9f / 20f),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            template.name,
                            style = LauncherType.secondary,
                            color = if (isSelected) palette.primary else palette.secondary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(LauncherSpace.md))
            Hairline()
            Spacer(Modifier.height(LauncherSpace.md))
            Text("Preview", style = LauncherType.title, color = palette.primary)
            Spacer(Modifier.height(LauncherSpace.sm))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                WallpaperPreviewBox(
                    selected,
                    Modifier
                        .width(220.dp)
                        .aspectRatio(9f / 20f)
                        .border(1.dp, palette.separator),
                )
            }
            Spacer(Modifier.height(LauncherSpace.sm))
            SecondaryText(selected.description)

            Spacer(Modifier.height(LauncherSpace.md))
            Text("Apply wallpaper to", style = LauncherType.body, color = palette.primary)
            Spacer(Modifier.height(LauncherSpace.sm))
            ChoiceRow(
                label = "",
                options = if (lockSupported) {
                    listOf(
                        WallpaperTarget.HOME to "Home",
                        WallpaperTarget.LOCK to "Lock screen",
                        WallpaperTarget.BOTH to "Both",
                    )
                } else {
                    listOf(WallpaperTarget.HOME to "Home")
                },
                selected = if (lockSupported) state.wallpaperTarget else WallpaperTarget.HOME,
                onSelect = { viewModel.setWallpaperTarget(it) },
            )
            if (!lockSupported) {
                Spacer(Modifier.height(LauncherSpace.xs))
                SecondaryText(
                    "This device does not report a separate lock target, so only Home " +
                        "is offered here. The system picker below can still set one.",
                )
            }
            Spacer(Modifier.height(LauncherSpace.md))
            if (state.wallpaperApplying) {
                SecondaryText("Applying…")
            } else {
                QuietAction("Apply wallpaper", onClick = { viewModel.applyWallpaper() })
            }
            state.wallpaperMessage?.let { message ->
                Spacer(Modifier.height(LauncherSpace.xs))
                SecondaryText(message)
                // A partial or failed apply keeps a real way out. Only a confirmed
                // success hides the picker route, because only then is there nothing
                // left to fall back for.
                if (!message.startsWith("Applied")) {
                    QuietAction("Open system picker instead", onClick = {
                        openSystemWallpaperPicker(context)
                    })
                }
            }
            Spacer(Modifier.height(LauncherSpace.sm))
            SecondaryText(
                "Direct apply uses the system WallpaperManager for Home and, where the " +
                    "device supports it, Lock — and reports success only after the system " +
                    "confirms it. It sets a background, never the clock, notifications, " +
                    "or unlock method.",
            )
            Spacer(Modifier.height(LauncherSpace.xl))
        }
    }
}

/**
 * One design, drawn the way the applier draws it: base field, then the caption
 * and wordmark below the top third. The geometry is proportional to the box so
 * thumbnails and the large preview stay true to the rendered bitmap.
 */
@Composable
fun WallpaperPreviewBox(template: WallpaperTemplate, modifier: Modifier = Modifier) {
    val base = Color(template.baseColor.toULong())
    val ink = if (template.darkBackground) Color.White else Color.Black
    BoxWithConstraints(modifier.background(base)) {
        // Captured before any nested scope: BoxWithConstraintsScope properties are not
        // reachable from inside the Column/Text lambdas below.
        val boxWidth = maxWidth
        val boxHeight = maxHeight
        if (template.caption == null && template.wordmark == null) return@BoxWithConstraints
        Column(
            Modifier.padding(start = boxWidth * 0.11f, top = boxHeight * 0.40f),
        ) {
            template.caption?.let { caption ->
                Text(
                    text = caption,
                    color = ink.copy(alpha = 0.92f),
                    fontSize = (boxWidth.value * 0.065f).sp,
                    fontWeight = FontWeight.Light,
                )
            }
            if (template.wordmark != null) {
                Spacer(Modifier.height(boxHeight * 0.03f))
                Text(
                    text = template.wordmark,
                    color = ink.copy(alpha = 0.6f),
                    fontSize = (boxWidth.value * 0.030f).sp,
                    letterSpacing = (boxWidth.value * 0.013f).sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** The honest fallback: leave the apply to the system's own picker. */
private fun openSystemWallpaperPicker(context: Context) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_SET_WALLPAPER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "This device has no wallpaper picker.", Toast.LENGTH_SHORT).show()
    }
}
