package com.zendolauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.zendolauncher.core.CalendarGlance
import com.zendolauncher.core.UsageCalculator
import com.zendolauncher.data.LabelCase
import com.zendolauncher.platform.UsageRepository
import com.zendolauncher.ui.theme.LauncherColors
import com.zendolauncher.ui.theme.LauncherSpace
import com.zendolauncher.ui.theme.LauncherType

/**
 * Dedicated Screen Usage & Wellbeing screen reachable from Quiet Home or Settings.
 */
@Composable
fun UsageScreen(viewModel: LauncherViewModel, state: UiState) {
    val palette = LauncherColors.current
    LaunchedEffect(Unit) { viewModel.refreshUsage() }

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
            ScreenTitle("Screen usage") { QuietAction("Home", onClick = { viewModel.goHome() }) }
            Spacer(Modifier.height(LauncherSpace.md))
            Hairline()
            Spacer(Modifier.height(LauncherSpace.md))
            UsageOverviewBody(viewModel = viewModel, state = state, includeCalendarGlance = false)
            Spacer(Modifier.height(LauncherSpace.xl))
        }
    }
}

/**
 * Calm Canvas Usage & Glance page (`PageType.WIDGET`).
 */
@Composable
fun UsagePage(viewModel: LauncherViewModel, state: UiState) {
    LaunchedEffect(Unit) { viewModel.refreshUsage() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = LauncherSpace.gutter),
    ) {
        Spacer(Modifier.height(LauncherSpace.xl))
        Text("Usage & Glance", style = LauncherType.title, color = LauncherColors.current.primary)
        Spacer(Modifier.height(LauncherSpace.md))
        Hairline()
        Spacer(Modifier.height(LauncherSpace.md))
        UsageOverviewBody(viewModel = viewModel, state = state, includeCalendarGlance = true)
        Spacer(Modifier.height(LauncherSpace.xl))
    }
}

@Composable
private fun UsageOverviewBody(
    viewModel: LauncherViewModel,
    state: UiState,
    includeCalendarGlance: Boolean,
) {
    val palette = LauncherColors.current
    val context = LocalContext.current

    if (includeCalendarGlance) {
        SectionHeading("Next up")
        Spacer(Modifier.height(LauncherSpace.sm))
        if (!state.calendarPermission) {
            EmptyNote("Calendar access is off.")
            QuietAction("Open calendar", onClick = { viewModel.openCalendar() })
        } else {
            val summary = remember(state.calendarEvents) {
                CalendarGlance.summarize(state.calendarEvents, System.currentTimeMillis())
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { viewModel.openCalendar() }
                    .padding(vertical = LauncherSpace.xs),
            ) {
                Text(summary.headline, style = LauncherType.body, color = palette.primary)
                summary.subline?.let {
                    Spacer(Modifier.height(LauncherSpace.xs))
                    SecondaryText(it)
                }
            }
        }
        Spacer(Modifier.height(LauncherSpace.lg))
        Hairline()
        Spacer(Modifier.height(LauncherSpace.md))
    }

    SectionHeading("Today on screen")
    Spacer(Modifier.height(LauncherSpace.sm))

    if (!state.usageAccess) {
        EmptyNote("Usage access is optional and stays completely on this device.")
        Spacer(Modifier.height(LauncherSpace.xs))
        SecondaryText(
            "Allow Usage Access for Zendo Launcher to see today's screen time, app open counts, " +
                "and real-time context inside the Mindful Gate.",
        )
        Spacer(Modifier.height(LauncherSpace.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(LauncherSpace.lg)) {
            QuietAction("Grant usage access") {
                runCatching { context.startActivity(UsageRepository(context).settingsIntent()) }
            }
            QuietAction("Refresh") { viewModel.refreshUsage() }
        }
    } else {
        val snapshot = state.dailyUsage
        val totalText = UsageCalculator.formatDuration(snapshot.totalForegroundMillis)
        Text(
            text = totalText,
            style = LauncherType.title,
            color = palette.primary,
        )
        Spacer(Modifier.height(LauncherSpace.xs))
        SecondaryText("${snapshot.totalLaunches} app opens today · local only")
        Spacer(Modifier.height(LauncherSpace.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(LauncherSpace.lg)) {
            QuietAction("Refresh") { viewModel.refreshUsage() }
            QuietAction("Manage access") {
                runCatching { context.startActivity(UsageRepository(context).settingsIntent()) }
            }
        }

        Spacer(Modifier.height(LauncherSpace.lg))
        Hairline()
        Spacer(Modifier.height(LauncherSpace.md))
        SectionHeading("Most used today")
        Spacer(Modifier.height(LauncherSpace.sm))

        val topApps = remember(snapshot) { snapshot.topApps(limit = 8) }
        val targetByPackage = remember(state.allApps) {
            state.allApps.groupBy { it.packageName }.mapValues { it.value.first() }
        }

        if (topApps.isEmpty()) {
            EmptyNote("No recorded foreground usage yet today.")
        } else {
            val maxMillis = topApps.maxOf { it.foregroundMillis }.coerceAtLeast(1L)
            topApps.forEachIndexed { index, item ->
                if (index > 0) Spacer(Modifier.height(LauncherSpace.md))
                val target = targetByPackage[item.packageName]
                val rawLabel = target?.label ?: item.packageName.substringAfterLast('.')
                val label = if (state.settings.labelCase == LabelCase.LOWERCASE) {
                    rawLabel.lowercase()
                } else {
                    rawLabel
                }
                val fraction = (item.foregroundMillis.toFloat() / maxMillis.toFloat()).coerceIn(0.05f, 1f)
                val isGated = target != null && target.key in state.settings.distract

                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = label,
                            style = LauncherType.body,
                            color = palette.primary,
                            modifier = Modifier.weight(1f),
                        )
                        SecondaryText(
                            "${UsageCalculator.formatDuration(item.foregroundMillis)} · ${item.launchCount}×",
                        )
                        if (target != null) {
                            Spacer(Modifier.padding(start = LauncherSpace.sm))
                            QuietAction(
                                text = if (isGated) "Gated" else "Gate",
                                onClick = {
                                    if (!state.settings.pauseEnabled) viewModel.setPauseEnabled(true)
                                    viewModel.toggleDistract(target.key)
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(LauncherSpace.xs))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(palette.separator),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction)
                                .fillMaxHeight()
                                .background(palette.primary.copy(alpha = 0.72f)),
                        )
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(LauncherSpace.lg))
    Hairline()
    Spacer(Modifier.height(LauncherSpace.md))
    SectionHeading("Mindful Gate")
    Spacer(Modifier.height(LauncherSpace.xs))
    SecondaryText(
        if (state.settings.pauseEnabled) {
            "Active for ${state.settings.distract.size} apps · ${state.settings.pauseDelaySeconds}s breath delay."
        } else {
            "Currently off. Turn it on in Settings to pause before opening distracting apps."
        },
    )
    Spacer(Modifier.height(LauncherSpace.xs))
    QuietAction("Gate settings", onClick = { viewModel.openSettings() })
}
