package com.zendolauncher.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import com.zendolauncher.core.DayRecord
import com.zendolauncher.core.Origins
import com.zendolauncher.ui.theme.LauncherColors
import com.zendolauncher.ui.theme.LauncherSpace
import com.zendolauncher.ui.theme.LauncherType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Local day history: one record per date, newest first, exactly as the day stood.
 * No charts, no streaks, no scores. Export writes a full local backup; clear erases
 * the past and nothing else. Everything on this screen is a file on this device.
 */
@Composable
fun HistoryScreen(viewModel: LauncherViewModel, state: UiState) {
    val palette = LauncherColors.current
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let { viewModel.exportBackupTo(it) } }

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
            ScreenTitle("History") { QuietAction("Home", onClick = { viewModel.goHome() }) }
            Spacer(Modifier.height(LauncherSpace.sm))
            SecondaryText(
                "Past days kept on this device: one action, one highlight, and the tasks " +
                    "as they stood. Today's record keeps changing with the day; older days " +
                    "never do.",
            )
            Spacer(Modifier.height(LauncherSpace.md))
            Row(horizontalArrangement = Arrangement.spacedBy(LauncherSpace.lg)) {
                QuietAction(
                    "Export backup",
                    onClick = {
                        exportLauncher.launch("zendo-launcher-backup-${LocalDate.now()}.json")
                    },
                )
                QuietAction("Clear history", onClick = { viewModel.requestClearHistory() })
            }
            state.historyMessage?.let { message ->
                Spacer(Modifier.height(LauncherSpace.sm))
                SecondaryText(message)
                QuietAction("Dismiss", onClick = { viewModel.clearHistoryMessage() })
            }
            Spacer(Modifier.height(LauncherSpace.lg))

            when {
                state.historyLoading -> SecondaryText("Loading…")
                state.historyDays.isEmpty() -> EmptyNote(
                    "No days recorded yet. Today's record starts as soon as you use Today, " +
                        "and a day stops changing when the date moves on.",
                )
                else -> {
                    val days = state.historyDays.sortedByDescending { it.date }
                    days.forEachIndexed { index, day ->
                        if (index > 0) {
                            Spacer(Modifier.height(LauncherSpace.md))
                            Hairline()
                            Spacer(Modifier.height(LauncherSpace.md))
                        }
                        HistoryDay(day)
                    }
                }
            }
            Spacer(Modifier.height(LauncherSpace.xl))
        }
    }
}

/** One recorded day, read top to bottom the way the Today page read that day. */
@Composable
private fun HistoryDay(day: DayRecord) {
    val palette = LauncherColors.current
    SectionHeading(
        day.date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())),
    )
    Spacer(Modifier.height(LauncherSpace.sm))
    if (day.isEmpty) {
        SecondaryText("A quiet day; nothing was recorded.")
        return
    }
    if (day.actionTitle.isNotBlank()) {
        Text(
            text = day.actionTitle,
            style = LauncherType.body.copy(
                textDecoration = if (day.actionDone) TextDecoration.LineThrough else null,
            ),
            color = if (day.actionDone) palette.secondary else palette.primary,
        )
    }
    if (day.highlightText.isNotBlank()) {
        Spacer(Modifier.height(LauncherSpace.xs))
        SecondaryText("Highlight: ${day.highlightText}")
    }
    val tasks = day.tasks.sortedBy { it.order }
    if (tasks.isNotEmpty()) {
        Spacer(Modifier.height(LauncherSpace.xs))
        tasks.forEach { task ->
            Text(
                text = if (task.done) "●  ${task.title}" else "○  ${task.title}",
                style = LauncherType.body.copy(
                    textDecoration = if (task.done) TextDecoration.LineThrough else null,
                ),
                color = if (task.done) palette.secondary else palette.primary,
            )
        }
    }
    if (Origins.isImported(day.origin) || day.tasks.any { Origins.isImported(it.origin) }) {
        Spacer(Modifier.height(LauncherSpace.xs))
        SecondaryText("Includes a record that came from Zendo.")
    }
}
