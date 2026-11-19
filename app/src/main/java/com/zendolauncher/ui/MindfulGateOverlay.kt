package com.zendolauncher.ui

import android.Manifest
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zendolauncher.core.AppUsageSummary
import com.zendolauncher.core.MindfulGate
import com.zendolauncher.core.UsageCalculator
import com.zendolauncher.data.LabelCase
import com.zendolauncher.data.LauncherSettings
import com.zendolauncher.ui.theme.HomeBackdrop
import com.zendolauncher.ui.theme.LauncherColors
import com.zendolauncher.ui.theme.LauncherSpace
import com.zendolauncher.ui.theme.LauncherType
import kotlinx.coroutines.delay

/**
 * Full-screen Mindful Gate overlay shown before opening a distraction-listed app.
 *
 * Combines:
 *  1. Real-time Screen Usage context ("Today: 38m · opened 9×") when Usage Access is granted.
 *  2. A breathing micro-delay that breaks autopilot muscle-memory before "Open" activates.
 *  3. An intention prompt with a gentle pivot when the user admits "Just checking / bored".
 *  4. An optional timebox reminder (5m / 10m / 15m) delivered via local notification.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MindfulGateOverlay(
    label: String,
    settings: LauncherSettings,
    usageSummary: AppUsageSummary?,
    usageAccess: Boolean,
    hasNotificationPermission: Boolean,
    customWallpaperBitmap: Bitmap?,
    onConfirm: (timeboxMinutes: Int?, intention: String?) -> Unit,
    onCancel: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenUsage: () -> Unit,
) {
    val palette = LauncherColors.current
    val displayLabel = if (settings.labelCase == LabelCase.LOWERCASE) label.lowercase() else label

    val delayTotal = settings.pauseDelaySeconds.coerceAtLeast(0)
    var remainingSeconds by remember(label, delayTotal) { mutableIntStateOf(delayTotal) }
    var selectedIntention by remember(label) { mutableStateOf<String?>(null) }
    var selectedTimebox by remember(label, settings.gateTimeboxEnabled) {
        mutableStateOf(if (settings.gateTimeboxEnabled) 10 else null)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* If denied, app still launches cleanly without nagging. */ }

    LaunchedEffect(label, delayTotal) {
        remainingSeconds = delayTotal
        while (remainingSeconds > 0) {
            delay(1_000L)
            remainingSeconds--
        }
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize()) {
            HomeBackdrop(
                style = settings.backdropStyle,
                scrimStrength = settings.scrimStrength,
                blurPhoto = settings.backdropBlur,
                customBitmap = customWallpaperBitmap,
                modifier = Modifier.fillMaxSize(),
            )
            // Additional soft veil so the gate reads distinctly as an intentional pause.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(palette.background.copy(alpha = 0.72f)),
            )

            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = LauncherSpace.gutter),
            ) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Spacer(Modifier.height(LauncherSpace.xl))
                    SectionHeading("Mindful pause")
                    Spacer(Modifier.height(LauncherSpace.sm))
                    Text(
                        text = displayLabel,
                        style = LauncherType.title,
                        color = palette.primary,
                    )
                    if (usageAccess) {
                        Spacer(Modifier.height(LauncherSpace.xs))
                        SecondaryText(UsageCalculator.formatCompactMirror(usageSummary))
                    }
                    Spacer(Modifier.height(LauncherSpace.sm))
                    Text(
                        text = settings.pauseMessage,
                        style = LauncherType.body,
                        color = palette.secondary,
                    )

                    Spacer(Modifier.height(LauncherSpace.lg))
                    Hairline()
                    Spacer(Modifier.height(LauncherSpace.md))

                    if (remainingSeconds > 0) {
                        val progress = if (delayTotal > 0) {
                            (delayTotal - remainingSeconds).toFloat() / delayTotal.toFloat()
                        } else {
                            1f
                        }
                        Text(
                            text = "Take a breath… ${remainingSeconds}s",
                            style = LauncherType.body,
                            color = palette.primary,
                        )
                        Spacer(Modifier.height(LauncherSpace.sm))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(palette.separator),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(progress.coerceIn(0.05f, 1f))
                                    .fillMaxHeight()
                                    .background(palette.primary),
                            )
                        }
                        Spacer(Modifier.height(LauncherSpace.sm))
                        SecondaryText("Notice the urge before opening.")
                    } else if (MindfulGate.isBoredIntention(selectedIntention)) {
                        Text(
                            text = "No pressure to open this right now.",
                            style = LauncherType.body,
                            color = palette.primary,
                        )
                        Spacer(Modifier.height(LauncherSpace.xs))
                        SecondaryText(
                            "Most quick checks turn into scrolling. Put the phone down, or redirect to something intentional.",
                        )
                        Spacer(Modifier.height(LauncherSpace.md))
                        Column(verticalArrangement = Arrangement.spacedBy(LauncherSpace.sm)) {
                            GateButton(
                                text = "Put it down",
                                emphasized = true,
                                onClick = onCancel,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(LauncherSpace.lg)) {
                                QuietAction("Check calendar", onClick = {
                                    onCancel()
                                    onOpenCalendar()
                                })
                                if (usageAccess) {
                                    QuietAction("View usage", onClick = {
                                        onCancel()
                                        onOpenUsage()
                                    })
                                }
                            }
                            Spacer(Modifier.height(LauncherSpace.sm))
                            QuietAction("Open anyway (5m check-in)") {
                                onConfirm(if (settings.gateTimeboxEnabled) 5 else null, selectedIntention)
                            }
                        }
                    } else {
                        SectionHeading("What brings you here?")
                        Spacer(Modifier.height(LauncherSpace.sm))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(LauncherSpace.sm),
                            verticalArrangement = Arrangement.spacedBy(LauncherSpace.sm),
                        ) {
                            MindfulGate.DEFAULT_INTENTIONS.forEach { intention ->
                                val selected = selectedIntention == intention
                                GateChip(
                                    label = intention,
                                    selected = selected,
                                    onClick = { selectedIntention = intention },
                                )
                            }
                        }

                        if (settings.gateTimeboxEnabled) {
                            Spacer(Modifier.height(LauncherSpace.lg))
                            SectionHeading("Remind me after")
                            Spacer(Modifier.height(LauncherSpace.sm))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(LauncherSpace.sm),
                                verticalArrangement = Arrangement.spacedBy(LauncherSpace.sm),
                            ) {
                                MindfulGate.TIMEBOX_OPTIONS_MINUTES.forEach { mins ->
                                    GateChip(
                                        label = "${mins}m",
                                        selected = selectedTimebox == mins,
                                        onClick = {
                                            selectedTimebox = mins
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                        },
                                    )
                                }
                                GateChip(
                                    label = "No timer",
                                    selected = selectedTimebox == null,
                                    onClick = { selectedTimebox = null },
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(LauncherSpace.xl))
                }

                // Bottom actions: Cancel is always reachable; Continue unlocks after breath delay.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = LauncherSpace.md),
                    horizontalArrangement = Arrangement.spacedBy(LauncherSpace.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (remainingSeconds == 0 && !MindfulGate.isBoredIntention(selectedIntention)) {
                        QuietAction(
                            text = "Continue",
                            onClick = { onConfirm(selectedTimebox, selectedIntention) },
                        )
                    }
                    QuietAction("Cancel", onClick = onCancel)
                }
            }
        }
    }
}

@Composable
private fun GateChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = LauncherColors.current
    val shape = RoundedCornerShape(999.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) palette.primary else palette.background.copy(alpha = 0.6f))
            .border(LauncherSpace.hairline, if (selected) palette.primary else palette.separator, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = LauncherType.action.copy(fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal),
            color = if (selected) palette.background else palette.primary,
        )
    }
}

@Composable
private fun GateButton(text: String, emphasized: Boolean, onClick: () -> Unit) {
    val palette = LauncherColors.current
    val shape = RoundedCornerShape(999.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(LauncherSpace.touchTarget)
            .clip(shape)
            .background(if (emphasized) palette.primary else palette.separator)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Text(
            text = text,
            style = LauncherType.action,
            color = if (emphasized) palette.background else palette.primary,
        )
    }
}
