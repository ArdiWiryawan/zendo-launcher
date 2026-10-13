package com.zendolauncher.ui

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.zendolauncher.data.LayoutMode
import com.zendolauncher.model.LaunchTarget
import com.zendolauncher.ui.theme.LauncherColors
import com.zendolauncher.ui.theme.LauncherType

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(LauncherColors.current.separator),
    )
}

@Composable
fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = LauncherType.section,
        color = LauncherColors.current.secondary,
        modifier = modifier,
    )
}

@Composable
fun QuietAction(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text = text,
        style = LauncherType.action,
        color = LauncherColors.current.primary,
        modifier = modifier
            .clip(CircleShape)
            .combinedClickable(onClick = onClick, role = Role.Button)
            .padding(vertical = 10.dp, horizontal = 2.dp),
    )
}

/**
 * One app line. Tap launches; long press opens the pin/hide menu. Rows are tall
 * enough to hit comfortably without looking like buttons.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppRow(
    viewModel: LauncherViewModel,
    target: LaunchTarget,
    layout: LayoutMode,
    isFavourite: Boolean,
    onClick: () -> Unit,
    onToggleFavourite: () -> Unit,
    onToggleHidden: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Box(modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuOpen = true },
                    role = Role.Button,
                )
                .padding(vertical = 14.dp),
        ) {
            if (layout == LayoutMode.ICON_TEXT) {
                AppIcon(viewModel, target, sizeDp = 28)
                Spacer(Modifier.width(14.dp))
            }
            Text(
                text = target.label,
                style = LauncherType.appLabel,
                color = LauncherColors.current.primary,
                modifier = Modifier.weight(1f),
            )
            if (isFavourite) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(LauncherColors.current.secondary),
                )
            }
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = {
                    Text(
                        if (isFavourite) "Remove from Essentials" else "Add to Essentials",
                        style = LauncherType.body,
                    )
                },
                onClick = {
                    menuOpen = false
                    onToggleFavourite()
                },
            )
            DropdownMenuItem(
                text = { Text("Hide app", style = LauncherType.body) },
                onClick = {
                    menuOpen = false
                    onToggleHidden()
                },
            )
        }
    }
}

/** Icons are loaded off the main thread and only when a layout asks for them. */
@Composable
private fun AppIcon(viewModel: LauncherViewModel, target: LaunchTarget, sizeDp: Int) {
    val px = with(LocalDensity.current) { sizeDp.dp.roundToPx() }
    val bitmap by produceState<Bitmap?>(initialValue = null, target.key) {
        value = viewModel.bitmapFor(target, px)
    }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(sizeDp.dp),
        )
    }
}

@Composable
fun EmptyNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = LauncherType.secondary,
        color = LauncherColors.current.secondary,
        modifier = modifier.padding(vertical = 4.dp),
    )
}

@Composable
fun SecondaryText(text: String, modifier: Modifier = Modifier, color: Color = LauncherColors.current.secondary) {
    Text(text = text, style = LauncherType.secondary, color = color, modifier = modifier)
}
