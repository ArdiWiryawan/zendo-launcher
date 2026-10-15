package com.zendolauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import com.zendolauncher.core.ImportMode
import com.zendolauncher.ui.theme.LauncherColors
import com.zendolauncher.ui.theme.LauncherSpace
import com.zendolauncher.ui.theme.LauncherType

/**
 * The snapshot import preview: what the file says, what applying would change, and the
 * one choice that matters (merge or replace-imported). Same calm dialog shape as the
 * pause dialog; nothing is written until Apply.
 */
@Composable
fun BridgeImportDialog(
    preview: BridgePreviewUi,
    onMode: (ImportMode) -> Unit,
    onApply: () -> Unit,
    onCancel: () -> Unit,
) {
    val summary = preview.plan.summary
    Dialog(onDismissRequest = onCancel) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(LauncherColors.current.background)
                .padding(LauncherSpace.lg),
        ) {
            Text(
                text = "Import from Zendo",
                style = LauncherType.body,
                color = LauncherColors.current.primary,
            )
            Spacer(Modifier.height(LauncherSpace.xs))
            SecondaryText(preview.fileName)
            SecondaryText("Snapshot for ${preview.snapshot.localDate} · exported ${preview.snapshot.exportedAt}")
            Spacer(Modifier.height(LauncherSpace.md))
            ChoiceRow(
                label = "How to apply",
                options = listOf(
                    ImportMode.MERGE to "Merge",
                    ImportMode.REPLACE_IMPORTED to "Replace imported",
                ),
                selected = preview.mode,
                onSelect = onMode,
            )
            Spacer(Modifier.height(LauncherSpace.xs))
            SecondaryText(
                if (preview.mode == ImportMode.MERGE) {
                    "Fills blanks and refreshes what Zendo sent before. Your own text is never overwritten."
                } else {
                    "Removes what Zendo sent before for this date, then applies the file. Your own text stays."
                },
            )
            Spacer(Modifier.height(LauncherSpace.md))
            if (!summary.hasAnyChange) {
                SecondaryText("Already applied — this file would change nothing.")
            } else {
                SecondaryText(
                    if (preview.plan.isToday) {
                        "Today's action, highlight and tasks will be updated."
                    } else {
                        "Only the record for ${preview.plan.targetDate} is written; today is untouched."
                    },
                )
                Spacer(Modifier.height(LauncherSpace.xs))
                if (summary.addedTasks > 0) SecondaryText("Tasks added: ${summary.addedTasks}")
                if (summary.updatedTasks > 0) SecondaryText("Tasks updated: ${summary.updatedTasks}")
                if (summary.removedImportedTasks > 0) {
                    SecondaryText("Earlier Zendo tasks replaced: ${summary.removedImportedTasks}")
                }
                if (summary.actionSet) SecondaryText("Today's action set from the file.")
                if (summary.highlightAdded) SecondaryText("Highlight added from the file.")
                if (summary.highlightUpdated) SecondaryText("Highlight refreshed from the file.")
            }
            Spacer(Modifier.height(LauncherSpace.md))
            Row(horizontalArrangement = Arrangement.spacedBy(LauncherSpace.lg)) {
                if (summary.hasAnyChange) {
                    QuietAction("Apply", onClick = onApply)
                }
                QuietAction("Close", onClick = onCancel)
            }
        }
    }
}

/**
 * Restore preview. The sentence that matters most: day records the file does not
 * contain are kept, so a restore can never quietly erase dates it never knew about.
 */
@Composable
fun BackupRestoreDialog(
    preview: BackupPreviewUi,
    onRestore: () -> Unit,
    onCancel: () -> Unit,
) {
    val contents = preview.contents
    Dialog(onDismissRequest = onCancel) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(LauncherColors.current.background)
                .padding(LauncherSpace.lg),
        ) {
            Text(
                text = "Restore from backup",
                style = LauncherType.body,
                color = LauncherColors.current.primary,
            )
            Spacer(Modifier.height(LauncherSpace.xs))
            SecondaryText(preview.fileName)
            Spacer(Modifier.height(LauncherSpace.sm))
            SecondaryText(
                "The current action, highlights and tasks are replaced with the file's. " +
                    "Past-day records are updated by date; dates the file does not " +
                    "contain stay as they are.",
            )
            Spacer(Modifier.height(LauncherSpace.sm))
            SecondaryText(
                "In the file: ${contents.tasks.size} tasks · " +
                    "${contents.highlights.size} highlights · ${contents.days.size} days",
            )
            Spacer(Modifier.height(LauncherSpace.md))
            Row(horizontalArrangement = Arrangement.spacedBy(LauncherSpace.lg)) {
                QuietAction("Restore", onClick = onRestore)
                QuietAction("Cancel", onClick = onCancel)
            }
        }
    }
}

/** The one destructive confirmation: clearing history. Current day data is untouched. */
@Composable
fun ClearHistoryDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    Dialog(onDismissRequest = onCancel) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(LauncherColors.current.background)
                .padding(LauncherSpace.lg),
        ) {
            Text(
                text = "Clear history?",
                style = LauncherType.body,
                color = LauncherColors.current.primary,
            )
            Spacer(Modifier.height(LauncherSpace.xs))
            SecondaryText(
                "Every past-day record is erased. Your current action, highlights and " +
                    "tasks stay exactly as they are.",
            )
            Spacer(Modifier.height(LauncherSpace.md))
            Row(horizontalArrangement = Arrangement.spacedBy(LauncherSpace.lg)) {
                QuietAction("Clear", onClick = onConfirm)
                QuietAction("Cancel", onClick = onCancel)
            }
        }
    }
}
