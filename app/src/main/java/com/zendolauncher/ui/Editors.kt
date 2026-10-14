package com.zendolauncher.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.zendolauncher.ui.theme.LauncherColors
import com.zendolauncher.ui.theme.LauncherSpace
import com.zendolauncher.ui.theme.LauncherType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Full-screen editors. Highlights need room for thought, tasks need speed, so
 * they share the monochrome language but not the layout: the highlight editor
 * leads with a large multiline body, the task editor with a quick title plus
 * optional details.
 *
 * Both keep drafts in the view model (rotation-safe), validate inline without
 * closing on failure, and ask before discarding unsaved work. The header with
 * Back and Save is pinned; only the content scrolls, with `imePadding` so the
 * keyboard never covers the active input. There is no horizontal pager on
 * these screens, so scrolling a long form can never trigger a page swipe.
 */

// ---- Highlight editor ------------------------------------------------------------

@Composable
fun HighlightEditorScreen(viewModel: LauncherViewModel, state: UiState) {
    val palette = LauncherColors.current
    val isNew = state.editingHighlightId == null
    val titleFocus = remember { FocusRequester() }
    val bodyFocus = remember { FocusRequester() }
    LaunchedEffect(state.editingHighlightId) { titleFocus.requestFocus() }

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.background)
            .systemBarsPadding()
            .imePadding(),
    ) {
        // Pinned header: Back and Save stay reachable whatever the keyboard does.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = LauncherSpace.gutter, vertical = LauncherSpace.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuietAction("Back", onClick = { viewModel.backFromHighlightEditor() })
            Spacer(Modifier.weight(1f))
            QuietAction("Save", onClick = { viewModel.saveHighlight() })
        }
        Hairline()
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LauncherSpace.gutter),
        ) {
            Spacer(Modifier.height(LauncherSpace.lg))
            Text(
                if (isNew) "New highlight" else "Edit highlight",
                style = LauncherType.title, color = palette.primary,
            )
            Spacer(Modifier.height(LauncherSpace.md))
            SecondaryText("Title (optional)")
            Spacer(Modifier.height(LauncherSpace.xs))
            BasicTextField(
                value = state.highlightTitle,
                onValueChange = { viewModel.updateHighlightDraft(it, state.highlightNote, state.highlightPin) },
                singleLine = true,
                textStyle = LauncherType.body.copy(color = palette.primary),
                cursorBrush = SolidColor(palette.primary),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { bodyFocus.requestFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(titleFocus)
                    .semantics { contentDescription = "Highlight title" },
                decorationBox = { inner ->
                    if (state.highlightTitle.isEmpty()) SecondaryText("Finish the important work.")
                    inner()
                },
            )
            Spacer(Modifier.height(LauncherSpace.lg))
            SecondaryText("Your thoughts")
            Spacer(Modifier.height(LauncherSpace.xs))
            BasicTextField(
                value = state.highlightNote,
                onValueChange = { viewModel.updateHighlightDraft(state.highlightTitle, it, state.highlightPin) },
                textStyle = LauncherType.body.copy(color = palette.primary),
                cursorBrush = SolidColor(palette.primary),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp)
                    .focusRequester(bodyFocus)
                    .semantics { contentDescription = "Highlight thoughts, multiline" },
                decorationBox = { inner ->
                    if (state.highlightNote.isEmpty()) {
                        SecondaryText("Write freely. Add context, ideas, or the outcome you want to achieve.")
                    }
                    inner()
                },
            )
            Spacer(Modifier.height(LauncherSpace.md))
            ToggleRow(
                label = "Show as primary highlight on Today",
                checked = state.highlightPin,
                onCheckedChange = { viewModel.updateHighlightDraft(state.highlightTitle, state.highlightNote, it) },
            )
            state.highlightError?.let {
                Spacer(Modifier.height(LauncherSpace.sm))
                Text(it, style = LauncherType.body, color = palette.primary)
            }
            if (!isNew) {
                Spacer(Modifier.height(LauncherSpace.xl))
                SectionHeading("Manage")
                Spacer(Modifier.height(LauncherSpace.xs))
                // Archive is reversible (Restore in the list); delete asks first.
                QuietAction("Archive highlight") {
                    viewModel.archiveHighlight(state.editingHighlightId!!, true)
                    viewModel.backFromHighlightEditor()
                }
                QuietAction("Delete highlight") {
                    viewModel.requestDeleteHighlight(state.editingHighlightId!!)
                }
            }
            Spacer(Modifier.height(LauncherSpace.xl))
        }
    }

    if (state.showDiscardHighlight) {
        DiscardDialog(
            title = "Discard this highlight?",
            body = "Your unsaved changes will be lost.",
            onDiscard = { viewModel.discardHighlight() },
            onKeep = { viewModel.cancelDiscardHighlight() },
        )
    }
    state.confirmDeleteHighlightId?.let {
        DeleteDialog(
            title = "Delete this highlight?",
            body = "This cannot be undone.",
            onDelete = { viewModel.confirmDeleteHighlight() },
            onCancel = { viewModel.cancelDeleteHighlight() },
        )
    }
}

// ---- Task editor -----------------------------------------------------------------

@Composable
fun TaskEditorScreen(viewModel: LauncherViewModel, state: UiState) {
    val palette = LauncherColors.current
    val context = LocalContext.current
    val isNew = state.editingTaskId == null
    val titleFocus = remember { FocusRequester() }
    LaunchedEffect(state.editingTaskId) { titleFocus.requestFocus() }

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.background)
            .systemBarsPadding()
            .imePadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = LauncherSpace.gutter, vertical = LauncherSpace.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuietAction("Back", onClick = { viewModel.backFromTaskEditor() })
            Spacer(Modifier.weight(1f))
            QuietAction(if (isNew) "Add task" else "Save", onClick = { viewModel.saveTask() })
        }
        Hairline()
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LauncherSpace.gutter),
        ) {
            Spacer(Modifier.height(LauncherSpace.lg))
            Text(
                if (isNew) "New task" else "Edit task",
                style = LauncherType.title, color = palette.primary,
            )
            Spacer(Modifier.height(LauncherSpace.md))
            SecondaryText("Task")
            Spacer(Modifier.height(LauncherSpace.xs))
            BasicTextField(
                value = state.taskTitle,
                onValueChange = { viewModel.updateTaskDraft(it, state.taskDueMillis, state.taskNotes) },
                singleLine = true,
                textStyle = LauncherType.body.copy(color = palette.primary),
                cursorBrush = SolidColor(palette.primary),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.saveTask() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(titleFocus)
                    .semantics { contentDescription = "Task title" },
                decorationBox = { inner ->
                    if (state.taskTitle.isEmpty()) SecondaryText("What needs to get done?")
                    inner()
                },
            )
            state.taskEditorError?.let {
                Spacer(Modifier.height(LauncherSpace.sm))
                Text(it, style = LauncherType.body, color = palette.primary)
            }
            Spacer(Modifier.height(LauncherSpace.lg))
            SecondaryText("Due date (optional)")
            Spacer(Modifier.height(LauncherSpace.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = state.taskDueMillis?.let { formatDue(it) } ?: "No due date",
                    style = LauncherType.body,
                    color = if (state.taskDueMillis == null) palette.secondary else palette.primary,
                    modifier = Modifier.weight(1f),
                )
                QuietAction("Choose date", onClick = {
                    val base = state.taskDueMillis?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                    } ?: LocalDate.now()
                    DatePickerDialog(
                        context,
                        { _, y, m, d ->
                            val millis = LocalDate.of(y, m + 1, d)
                                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            viewModel.updateTaskDraft(state.taskTitle, millis, state.taskNotes)
                        },
                        base.year, base.monthValue - 1, base.dayOfMonth,
                    ).show()
                })
                if (state.taskDueMillis != null) {
                    QuietAction("Clear", onClick = {
                        viewModel.updateTaskDraft(state.taskTitle, null, state.taskNotes)
                    })
                }
            }
            Spacer(Modifier.height(LauncherSpace.lg))
            SecondaryText("Notes (optional)")
            Spacer(Modifier.height(LauncherSpace.xs))
            BasicTextField(
                value = state.taskNotes,
                onValueChange = { viewModel.updateTaskDraft(state.taskTitle, state.taskDueMillis, it) },
                textStyle = LauncherType.body.copy(color = palette.primary),
                cursorBrush = SolidColor(palette.primary),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
                    .semantics { contentDescription = "Task notes, multiline, optional" },
                decorationBox = { inner ->
                    if (state.taskNotes.isEmpty()) SecondaryText("Additional details.")
                    inner()
                },
            )
            if (!isNew) {
                Spacer(Modifier.height(LauncherSpace.xl))
                SectionHeading("Manage")
                Spacer(Modifier.height(LauncherSpace.xs))
                QuietAction("Delete task") {
                    viewModel.requestDeleteTask(state.editingTaskId!!)
                }
            }
            Spacer(Modifier.height(LauncherSpace.xl))
        }
    }

    if (state.showDiscardTask) {
        DiscardDialog(
            title = "Discard this task?",
            body = "Your unsaved changes will be lost.",
            onDiscard = { viewModel.discardTask() },
            onKeep = { viewModel.cancelDiscardTask() },
        )
    }
    state.confirmDeleteTaskId?.let {
        DeleteDialog(
            title = "Delete this task?",
            body = "This cannot be undone.",
            onDelete = { viewModel.confirmDeleteTask() },
            onCancel = { viewModel.cancelDeleteTask() },
        )
    }
}

internal fun formatDue(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
        .format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))

// ---- Shared confirms ---------------------------------------------------------------

@Composable
private fun DiscardDialog(title: String, body: String, onDiscard: () -> Unit, onKeep: () -> Unit) {
    val palette = LauncherColors.current
    Dialog(onDismissRequest = onKeep) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(palette.background)
                .padding(LauncherSpace.lg),
        ) {
            Text(title, style = LauncherType.body, color = palette.primary)
            Spacer(Modifier.height(LauncherSpace.xs))
            SecondaryText(body)
            Spacer(Modifier.height(LauncherSpace.md))
            Row(horizontalArrangement = Arrangement.spacedBy(LauncherSpace.lg)) {
                QuietAction("Discard", onClick = onDiscard)
                QuietAction("Keep editing", onClick = onKeep)
            }
        }
    }
}

@Composable
private fun DeleteDialog(title: String, body: String, onDelete: () -> Unit, onCancel: () -> Unit) {
    val palette = LauncherColors.current
    Dialog(onDismissRequest = onCancel) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(palette.background)
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
