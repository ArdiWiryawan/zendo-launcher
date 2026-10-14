package com.zendolauncher.ui

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
import androidx.compose.ui.window.Dialog
import com.zendolauncher.core.archivedList
import com.zendolauncher.core.primary
import com.zendolauncher.core.visible
import com.zendolauncher.ui.theme.LauncherColors
import com.zendolauncher.ui.theme.LauncherSpace
import com.zendolauncher.ui.theme.LauncherType

/**
 * Highlight editor + lists. Dialogs close via Back (viewModel.back handles).
 */
@Composable
fun HighlightOverlays(viewModel: LauncherViewModel, state: UiState) {
    if (state.showHighlightEditor) {
        val existing = state.highlights.firstOrNull { it.id == state.editingHighlightId }
        HighlightEditorDialog(
            initialTitle = existing?.title.orEmpty(),
            initialNote = existing?.note.orEmpty(),
            initiallyPinned = existing?.isPrimary ?: state.highlights.isEmpty(),
            isNew = existing == null,
            onSave = { title, note, pin -> viewModel.saveHighlight(title, note, pin) },
            onDismiss = { viewModel.dismissHighlightEditor() },
            onArchive = if (existing != null) ({ viewModel.archiveHighlight(existing.id, true); viewModel.dismissHighlightEditor() }) else null,
        )
    }
    if (state.showAllHighlights) {
        AllHighlightsDialog(viewModel, state)
    }
    if (state.showAllTasks) {
        AllTasksDialog(viewModel, state)
    }
}

@Composable
private fun HighlightEditorDialog(
    initialTitle: String,
    initialNote: String,
    initiallyPinned: Boolean,
    isNew: Boolean,
    onSave: (String, String, Boolean) -> Unit,
    onDismiss: () -> Unit,
    onArchive: (() -> Unit)?,
) {
    val palette = LauncherColors.current
    var title by remember { mutableStateOf(initialTitle) }
    var note by remember { mutableStateOf(initialNote) }
    var pin by remember { mutableStateOf(initiallyPinned) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(LauncherSpace.lg),
        ) {
            Text(
                if (isNew) "New highlight" else "Edit highlight",
                style = LauncherType.title, color = palette.primary,
            )
            Spacer(Modifier.height(LauncherSpace.md))
            SecondaryText("Title")
            BasicTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                textStyle = LauncherType.body.copy(color = palette.primary),
                cursorBrush = SolidColor(palette.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Highlight title" },
                decorationBox = { inner ->
                    if (title.isEmpty()) SecondaryText("Finish the important work.")
                    inner()
                },
            )
            Spacer(Modifier.height(LauncherSpace.md))
            SecondaryText("Supporting note")
            BasicTextField(
                value = note,
                onValueChange = { note = it },
                textStyle = LauncherType.body.copy(color = palette.primary),
                cursorBrush = SolidColor(palette.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Supporting note" },
                decorationBox = { inner ->
                    if (note.isEmpty()) SecondaryText("Prepare the prototype and review feedback.")
                    inner()
                },
            )
            Spacer(Modifier.height(LauncherSpace.md))
            ToggleRow(label = "Pin as primary highlight", checked = pin, onCheckedChange = { pin = it })
            Spacer(Modifier.height(LauncherSpace.md))
            Row(horizontalArrangement = Arrangement.spacedBy(LauncherSpace.lg)) {
                QuietAction("Save", onClick = { onSave(title, note, pin) })
                QuietAction("Cancel", onClick = onDismiss)
                if (onArchive != null) {
                    QuietAction("Archive", onClick = onArchive)
                }
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
                    Column(Modifier.fillMaxWidth().padding(vertical = LauncherSpace.sm)) {
                        Text(h.title, style = LauncherType.body, color = palette.primary)
                        if (h.note.isNotBlank()) SecondaryText(h.note)
                        Row(horizontalArrangement = Arrangement.spacedBy(LauncherSpace.md)) {
                            if (!h.isPrimary) QuietAction("Pin", onClick = { viewModel.pinHighlight(h.id) })
                            QuietAction("Edit", onClick = { viewModel.openHighlightEditor(h.id) })
                            QuietAction("Archive", onClick = { viewModel.archiveHighlight(h.id, true) })
                            QuietAction("Delete", onClick = { viewModel.deleteHighlight(h.id) })
                        }
                        if (h.isPrimary) SecondaryText("Primary")
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
                                h.title, style = LauncherType.body, color = palette.secondary,
                                modifier = Modifier.weight(1f),
                            )
                            QuietAction("Restore", onClick = { viewModel.archiveHighlight(h.id, false) })
                            QuietAction("Delete", onClick = { viewModel.deleteHighlight(h.id) })
                        }
                    }
                }
            }
            Spacer(Modifier.height(LauncherSpace.md))
            Row(horizontalArrangement = Arrangement.spacedBy(LauncherSpace.lg)) {
                QuietAction("New", onClick = { viewModel.openHighlightEditor(null) })
                QuietAction("Close", onClick = { viewModel.closeAllHighlights() })
            }
            // Silence unused import warning for primary() extension used by callers.
            @Suppress("UNUSED_EXPRESSION")
            state.highlights.primary()
        }
    }
}

@Composable
private fun AllTasksDialog(viewModel: LauncherViewModel, state: UiState) {
    val palette = LauncherColors.current
    var draft by remember { mutableStateOf("") }
    var editingTitle by remember(state.editingTaskId) {
        mutableStateOf(state.tasks.firstOrNull { it.id == state.editingTaskId }?.title.orEmpty())
    }
    Dialog(onDismissRequest = {
        viewModel.closeTaskEditor()
        viewModel.closeAllTasks()
    }) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(LauncherSpace.lg),
        ) {
            Text("All tasks", style = LauncherType.title, color = palette.primary)
            Spacer(Modifier.height(LauncherSpace.sm))
            SecondaryText("${state.tasks.count { it.done }}/${state.tasks.size} done")
            Spacer(Modifier.height(LauncherSpace.sm))
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(state.tasks, key = { it.id }) { task ->
                    Column(Modifier.fillMaxWidth().padding(vertical = LauncherSpace.xs)) {
                        if (state.editingTaskId == task.id) {
                            BasicTextField(
                                value = editingTitle,
                                onValueChange = { editingTitle = it },
                                singleLine = true,
                                textStyle = LauncherType.body.copy(color = palette.primary),
                                cursorBrush = SolidColor(palette.primary),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(LauncherSpace.md)) {
                                QuietAction("Save", onClick = {
                                    viewModel.renameTask(task.id, editingTitle)
                                    viewModel.closeTaskEditor()
                                })
                                QuietAction("Cancel", onClick = { viewModel.closeTaskEditor() })
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    (if (task.done) "● " else "○ ") + task.title,
                                    style = LauncherType.body,
                                    color = if (task.done) palette.secondary else palette.primary,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(LauncherSpace.md)) {
                                QuietAction(
                                    if (task.done) "Uncheck" else "Check",
                                    onClick = { viewModel.toggleTask(task.id) },
                                )
                                QuietAction("Edit", onClick = {
                                    editingTitle = task.title
                                    viewModel.openTaskEditor(task.id)
                                })
                                QuietAction(
                                    if (task.showOnHome) "Hide from Home" else "Show on Home",
                                    onClick = { viewModel.setTaskVisible(task.id, !task.showOnHome) },
                                )
                                QuietAction("Delete", onClick = { viewModel.deleteTask(task.id) })
                            }
                        }
                    }
                    Hairline()
                }
            }
            Spacer(Modifier.height(LauncherSpace.sm))
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                textStyle = LauncherType.body.copy(color = palette.primary),
                cursorBrush = SolidColor(palette.primary),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "New task title" },
                decorationBox = { inner ->
                    if (draft.isEmpty()) SecondaryText("+ Add task")
                    inner()
                },
            )
            Spacer(Modifier.height(LauncherSpace.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(LauncherSpace.lg)) {
                if (draft.isNotBlank()) {
                    QuietAction("Add", onClick = {
                        viewModel.addTask(draft)
                        draft = ""
                    })
                }
                QuietAction("Close", onClick = {
                    viewModel.closeTaskEditor()
                    viewModel.closeAllTasks()
                })
            }
        }
    }
}
