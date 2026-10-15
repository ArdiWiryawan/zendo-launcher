package com.zendolauncher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.zendolauncher.core.archivedList
import com.zendolauncher.core.homeSummary
import com.zendolauncher.core.primary
import com.zendolauncher.core.UpcomingItem
import com.zendolauncher.core.UpcomingMerge
import com.zendolauncher.core.visible
import com.zendolauncher.ui.theme.LauncherColors
import com.zendolauncher.ui.theme.LauncherSpace
import com.zendolauncher.ui.theme.LauncherType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The Today dashboard: action, highlights, tasks, upcoming. One coherent page
 * with separators, not three card stacks. All data is local and persistent.
 */
@Composable
fun TodayDashboard(viewModel: LauncherViewModel, state: UiState) {
    Column {
        TodayActionSection(viewModel, state)
        Spacer(Modifier.height(LauncherSpace.lg))
        SectionSeparator()
        Spacer(Modifier.height(LauncherSpace.md))
        HighlightsSection(viewModel, state)
        Spacer(Modifier.height(LauncherSpace.lg))
        SectionSeparator()
        Spacer(Modifier.height(LauncherSpace.md))
        TasksSection(viewModel, state)
        Spacer(Modifier.height(LauncherSpace.lg))
        SectionSeparator()
        Spacer(Modifier.height(LauncherSpace.md))
        UpcomingSection(viewModel, state)
    }
}

@Composable
private fun SectionSeparator() {
    Hairline()
}

/**
 * The day's headline action: dominant, completable, editable inline. The draft
 * lives in the view model so rotation keeps it; saving a blank refuses inline.
 */
@Composable
private fun TodayActionSection(viewModel: LauncherViewModel, state: UiState) {
    val palette = LauncherColors.current
    SectionHeading("Today's action")
    Spacer(Modifier.height(LauncherSpace.sm))
    if (state.editingTodayAction) {
        var draft by remember(state.todayActionDraft) { mutableStateOf(state.todayActionDraft) }
        BasicTextField(
            value = draft,
            onValueChange = {
                draft = it
                viewModel.updateTodayActionDraft(it)
            },
            singleLine = true,
            textStyle = LauncherType.body.copy(color = palette.primary),
            cursorBrush = SolidColor(palette.primary),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { viewModel.saveTodayAction() }),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Today's action" },
            decorationBox = { inner ->
                if (draft.isEmpty()) SecondaryText("What is the one thing for today?")
                inner()
            },
        )
        state.todayActionError?.let {
            Spacer(Modifier.height(LauncherSpace.xs))
            SecondaryText(it)
        }
        Spacer(Modifier.height(LauncherSpace.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(LauncherSpace.md)) {
            QuietAction("Save", onClick = { viewModel.saveTodayAction() })
            QuietAction("Cancel", onClick = { viewModel.cancelTodayActionEdit() })
        }
    } else if (state.todayActionTitle.isBlank()) {
        EmptyNote("Name the one thing that matters today.")
        QuietAction("Set today's action", onClick = { viewModel.editTodayAction() })
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = state.todayActionTitle,
                style = LauncherType.body,
                color = if (state.todayActionDone) palette.secondary else palette.primary,
                modifier = Modifier.weight(1f),
            )
            QuietAction(
                if (state.todayActionDone) "Reopen" else "Done",
                onClick = { viewModel.toggleTodayAction() },
                modifier = Modifier.semantics {
                    contentDescription = (if (state.todayActionDone) "Reopen " else "Complete ") + state.todayActionTitle
                },
            )
        }
        QuietAction("Edit", onClick = { viewModel.editTodayAction() })
    }
}

@Composable
private fun HighlightsSection(viewModel: LauncherViewModel, state: UiState) {
    val palette = LauncherColors.current
    val primary = state.highlights.primary()
    SectionHeading("Highlights")
    Spacer(Modifier.height(LauncherSpace.sm))
    if (primary == null) {
        EmptyNote("Write your priority here. It stays until you edit or archive it.")
        Spacer(Modifier.height(LauncherSpace.xs))
        QuietAction("Add highlight", onClick = { viewModel.openHighlightEditor(null) })
        if (state.highlights.visible().isNotEmpty() || state.highlights.archivedList().isNotEmpty()) {
            QuietAction("All highlights", onClick = { viewModel.openAllHighlights() })
        }
    } else {
        Text(text = primary.title, style = LauncherType.body, color = palette.primary)
        if (primary.note.isNotBlank()) {
            Spacer(Modifier.height(LauncherSpace.xs))
            SecondaryText(primary.note)
        }
        Spacer(Modifier.height(LauncherSpace.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(LauncherSpace.md)) {
            QuietAction("Edit", onClick = { viewModel.openHighlightEditor(primary.id) })
            QuietAction("All highlights", onClick = { viewModel.openAllHighlights() })
        }
    }
}

@Composable
private fun TasksSection(viewModel: LauncherViewModel, state: UiState) {
    val palette = LauncherColors.current
    val visible = state.tasks.filter { it.showOnHome }
    val done = visible.count { it.done }
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionHeading("Tasks", modifier = Modifier.weight(1f))
        SecondaryText("$done/${visible.size}")
    }
    Spacer(Modifier.height(LauncherSpace.sm))
    val summary = state.tasks.homeSummary(3)
    if (visible.isEmpty()) {
        EmptyNote("No tasks yet. Add the first one below.")
    } else {
        summary.forEach { task ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Checkbox, onClick = { viewModel.toggleTask(task.id) })
                    .semantics {
                        contentDescription = (if (task.done) "Uncheck " else "Check ") + task.title
                    },
            ) {
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(CircleShape),
                ) {
                    Text(
                        text = if (task.done) "●" else "○",
                        style = LauncherType.body,
                        color = palette.secondary,
                    )
                }
                Spacer(Modifier.width(LauncherSpace.sm))
                Text(
                    text = task.title,
                    style = LauncherType.body.copy(
                        textDecoration = if (task.done) TextDecoration.LineThrough else null,
                    ),
                    color = if (task.done) palette.secondary else palette.primary,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(LauncherSpace.xs))
        }
        if (visible.size > summary.size) {
            SecondaryText("+ ${visible.size - summary.size} more")
        }
    }
    var draft by remember { mutableStateOf("") }
    Spacer(Modifier.height(LauncherSpace.xs))
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value = draft,
            onValueChange = { draft = it },
            singleLine = true,
            textStyle = LauncherType.body.copy(color = palette.primary),
            cursorBrush = SolidColor(palette.primary),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                if (draft.isNotBlank()) {
                    viewModel.addTask(draft)
                    draft = ""
                }
            }),
            decorationBox = { inner ->
                if (draft.isEmpty()) SecondaryText("+ Add task")
                inner()
            },
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = "Add task" },
        )
        if (draft.isNotBlank()) {
            QuietAction("Add", onClick = {
                viewModel.addTask(draft)
                draft = ""
            })
        } else {
            QuietAction("All tasks", onClick = { viewModel.openAllTasks() })
        }
    }
}

@Composable
private fun UpcomingSection(viewModel: LauncherViewModel, state: UiState) {
    SectionHeading("Upcoming")
    Spacer(Modifier.height(LauncherSpace.sm))
    val items = remember(state.calendarEvents, state.tasks) {
        UpcomingMerge.next(state.calendarEvents, state.tasks, System.currentTimeMillis(), 3)
    }
    if (items.isEmpty()) {
        if (!state.calendarPermission) {
            EmptyNote("Calendar access is off. Upcoming stays empty until you allow it.")
            Spacer(Modifier.height(LauncherSpace.xs))
            SecondaryText("The launcher reads your device calendar only, offline. Nothing is uploaded.")
            CalendarAccessAction(viewModel, state.settings.calendarAsked)
        } else {
            EmptyNote("Nothing upcoming in the next days.")
        }
    } else {
        items.forEach { item ->
            when (item) {
                is UpcomingItem.Event -> {
                    val e = item.event
                    UpcomingRow(e.startMillis, e.endMillis, e.title, e.allDay)
                }
                is UpcomingItem.Task -> {
                    val due = Instant.ofEpochMilli(item.dueMillis).atZone(ZoneId.systemDefault())
                    Row {
                        Column(horizontalAlignment = Alignment.Start, modifier = Modifier.width(52.dp)) {
                            Text(
                                text = due.format(DateTimeFormatter.ofPattern("d MMM")),
                                style = LauncherType.body,
                                color = LauncherColors.current.primary,
                            )
                            SecondaryText("Due")
                        }
                        Spacer(Modifier.width(LauncherSpace.md))
                        Column(Modifier.weight(1f)) {
                            Text(text = item.task.title, style = LauncherType.body, color = LauncherColors.current.primary)
                            SecondaryText("Task")
                        }
                    }
                }
            }
            Spacer(Modifier.height(LauncherSpace.sm))
        }
        if (!state.calendarPermission) {
            SecondaryText("Calendar access is off — showing due tasks only.")
            CalendarAccessAction(viewModel, state.settings.calendarAsked)
        }
    }
    Spacer(Modifier.height(LauncherSpace.xs))
    QuietAction("Open calendar", onClick = { viewModel.openCalendar() })
}

@Composable
fun UpcomingRow(startMillis: Long, endMillis: Long, title: String, allDay: Boolean = false) {
    val palette = LauncherColors.current
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalTime()
    val end = Instant.ofEpochMilli(endMillis).atZone(zone).toLocalTime()
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    Row {
        Column(horizontalAlignment = Alignment.Start, modifier = Modifier.width(52.dp)) {
            Text(
                text = if (allDay) "All-day" else start.format(timeFmt),
                style = LauncherType.body,
                color = palette.primary,
            )
            if (!allDay) {
                SecondaryText(end.format(timeFmt))
            }
        }
        Spacer(Modifier.width(LauncherSpace.md))
        Column(Modifier.weight(1f)) {
            Text(text = title, style = LauncherType.body, color = palette.primary)
            if (!allDay) {
                val mins = ((endMillis - startMillis) / 60000).coerceAtLeast(1)
                SecondaryText("$mins minutes")
            } else {
                SecondaryText(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")))
            }
        }
    }
}
