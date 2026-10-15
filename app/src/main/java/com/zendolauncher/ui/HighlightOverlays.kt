package com.zendolauncher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import com.zendolauncher.core.TaskView
import com.zendolauncher.core.archivedList
import com.zendolauncher.core.forView
import com.zendolauncher.core.overdueLabel
import com.zendolauncher.core.visible
import com.zendolauncher.ui.theme.LauncherColors
import com.zendolauncher.ui.theme.LauncherSpace
import com.zendolauncher.ui.theme.LauncherType
import java.time.LocalDate

/**
 * Saved-list dialogs for highlights and tasks. Editing itself lives in the
 * full-screen editors (`Editors.kt`); tapping a row opens it. Delete asks
 * first (both are destructive and unrecoverable); archive does not (Restore
 * reverses it in place).
 */
@Composable
fun HighlightOverlays(viewModel: LauncherViewModel, state: UiState) {
    if (state.showAllHighlights) {
        AllHighlightsDialog(viewModel, state)
    }
    if (state.showAllTasks) {
        AllTasksDialog(viewModel, state)
    }
    state.confirmDeleteHighlightId?.let { id ->
        ConfirmDeleteDialog(
            title = "Delete this highlight?",
            body = (state.highlights.firstOrNull { it.id == id }?.title ?: "This highlight") +
                " will be permanently removed.",
            onDelete = { viewModel.confirmDeleteHighlight() },
            onCancel = { viewModel.cancelDeleteHighlight() },
        )
    }
    state.confirmDeleteTaskId?.let { id ->
        ConfirmDeleteDialog(
            title = "Delete this task?",
            body = (state.tasks.firstOrNull { it.id == id }?.title ?: "This task") +
                " will be permanently removed.",
            onDelete = { viewModel.confirmDeleteTask() },
            onCancel = { viewModel.cancelDeleteTask() },
        )
    }
}

@Composable
private fun ConfirmDeleteDialog(title: String, body: String, onDelete: () -> Unit, onCancel: () -> Unit) {
    val palette = LauncherColors.current
    Dialog(onDismissRequest = onCancel) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(LauncherSpace.lg),
        ) {
            Text(title, style = LauncherType.body, color = palette.primary)
            Spacer(Modifier.height(LauncherSpace.xs))
            SecondaryText(body)
            Spacer(Modifier.height(LauncherSpace.md))
            Row(horizontalArrangement = Arrangement.spacedBy(LauncherSpace.lg)) {
                QuietAction("Delete", onClick = onDelete)
                QuietAction("Cancel", onClick = onCancel)
            }
        }
    }
}

@Composable
private fun AllHighlightsDialog(viewModel: LauncherViewModel, state: UiState) {
    val palette = LauncherColors.current
    val visible = state.highlights.visible()
    val archived = state.highlights.archivedList()
    Dialog(onDismissRequest = { viewModel.closeAllHighlights() }) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(LauncherSpace.lg),
        ) {
            Text("All highlights", style = LauncherType.title, color = palette.primary)
            Spacer(Modifier.height(LauncherSpace.sm))
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(visible, key = { it.id }) { h ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = LauncherSpace.sm)
                            .clickable(onClick = { viewModel.openHighlightEditor(h.id) })
                            .semantics { contentDescription = "Edit highlight: ${h.title}" },
                    ) {
                        Text(h.title, style = LauncherType.body, color = palette.primary)
                        if (h.note.isNotBlank()) {
                            SecondaryText(
                                h.note,
                                modifier = Modifier,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(LauncherSpace.md)) {
                            if (!h.isPrimary) QuietAction("Pin", onClick = { viewModel.pinHighlight(h.id) })
                            else SecondaryText("Primary")
                            QuietAction("Archive", onClick = { viewModel.archiveHighlight(h.id, true) })
                            QuietAction("Delete", onClick = { viewModel.requestDeleteHighlight(h.id) })
                        }
                    }
                    Hairline()
                }
                if (archived.isNotEmpty()) {
                    item(key = "archived-header") {
                        SectionHeading("Archived")
                    }
                    items(archived, key = { it.id }) { h ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = LauncherSpace.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                h.title,
                                style = LauncherType.body,
                                color = palette.secondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            QuietAction("Restore", onClick = { viewModel.archiveHighlight(h.id, false) })
                            QuietAction("Delete", onClick = { viewModel.requestDeleteHighlight(h.id) })
                        }
                    }
                }
            }
            Spacer(Modifier.height(LauncherSpace.md))
            Row(horizontalArrangement = Arrangement.spacedBy(LauncherSpace.lg)) {
                QuietAction("New", onClick = { viewModel.openHighlightEditor(null) })
                QuietAction("Close", onClick = { viewModel.closeAllHighlights() })
            }
        }
    }
}

@Composable
private fun AllTasksDialog(viewModel: LauncherViewModel, state: UiState) {
    val palette = LauncherColors.current
    // Quick Add draft is dialog-local on purpose: it is submitted or cleared
    // immediately, so there is no unsaved work to protect on dismiss.
    var draft by remember { mutableStateOf("") }
    fun submitDraft() {
        if (draft.isNotBlank()) {
            viewModel.addTask(draft)
            draft = ""
        }
    }
    Dialog(onDismissRequest = { viewModel.closeAllTasks() }) {
        val today = remember { LocalDate.now() }
        var view by remember { mutableStateOf(TaskView.ALL) }
        val shown = remember(state.tasks, view) { state.tasks.forView(view, today) }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(LauncherSpace.lg),
        ) {
            Text("All tasks", style = LauncherType.title, color = palette.primary)
            Spacer(Modifier.height(LauncherSpace.xs))
            // Four views over the same states — no second model, no new storage.
            ChoiceRow(
                label = "",
                options = listOf(
                    TaskView.TODAY to "Today",
                    TaskView.UPCOMING to "Upcoming",
                    TaskView.ALL to "All",
                    TaskView.DONE to "Done",
                ),
                selected = view,
                onSelect = { view = it },
            )
            Spacer(Modifier.height(LauncherSpace.sm))
            SecondaryText("${state.tasks.count { it.done }}/${state.tasks.size} done")
            Spacer(Modifier.height(LauncherSpace.sm))
            if (shown.isEmpty()) {
                EmptyNote(
                    when (view) {
                        TaskView.TODAY -> "No tasks due today. Add one below or check Upcoming."
                        TaskView.UPCOMING -> "No upcoming tasks."
                        TaskView.DONE -> "Completed tasks will appear here."
                        TaskView.ALL -> "No tasks yet."
                    },
                )
            }
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(shown, key = { it.id }) { task ->
                    Column(Modifier.fillMaxWidth().padding(vertical = LauncherSpace.xs)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = { viewModel.openTaskEditor(task.id) })
                                .semantics { contentDescription = "Edit task: ${task.title}" },
                        ) {
                            Text(
                                (if (task.done) "● " else "○ ") + task.title,
                                style = LauncherType.body,
                                color = if (task.done) palette.secondary else palette.primary,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        val meta = buildList {
                            task.dueDateMillis?.let { add("Due ${formatDue(it)}") }
                            task.overdueLabel(today)?.let { add(it) }
                            if (task.notes.isNotBlank()) add(task.notes.lines().first().take(60))
                            if (!task.showOnHome) add("Hidden from Home")
                        }
                        if (meta.isNotEmpty()) {
                            SecondaryText(
                                meta.joinToString(" · "),
                                modifier = Modifier,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(LauncherSpace.md)) {
                            QuietAction(
                                if (task.done) "Uncheck" else "Check",
                                onClick = { viewModel.toggleTask(task.id) },
                            )
                            QuietAction("Focus", onClick = { viewModel.focusFromTask() })
                            QuietAction(
                                if (task.showOnHome) "Hide from Home" else "Show on Home",
                                onClick = { viewModel.setTaskVisible(task.id, !task.showOnHome) },
                            )
                            QuietAction("Delete", onClick = { viewModel.requestDeleteTask(task.id) })
                        }
                    }
                    Hairline()
                }
            }
            Spacer(Modifier.height(LauncherSpace.sm))
            // Quick Add: single line, Done submits, input clears for the next one.
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                textStyle = LauncherType.body.copy(color = palette.primary),
                cursorBrush = SolidColor(palette.primary),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submitDraft() }),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Quick add task" },
                decorationBox = { inner ->
                    if (draft.isEmpty()) SecondaryText("What needs to get done?")
                    inner()
                },
            )
            Spacer(Modifier.height(LauncherSpace.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(LauncherSpace.lg)) {
                if (draft.isNotBlank()) {
                    QuietAction("Add task", onClick = { submitDraft() })
                }
                QuietAction("Details", onClick = { viewModel.openTaskEditor(null) })
                QuietAction("Close", onClick = { viewModel.closeAllTasks() })
            }
            Spacer(Modifier.height(LauncherSpace.xs))
            SecondaryText("Details opens the full editor: due date and notes.")
        }
    }
}
