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
import com.zendolauncher.ui.theme.LauncherColors
import com.zendolauncher.ui.theme.LauncherSpace
import com.zendolauncher.ui.theme.LauncherType

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
