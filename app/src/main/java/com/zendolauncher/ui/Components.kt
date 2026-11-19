package com.zendolauncher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.zendolauncher.core.PageType
import com.zendolauncher.model.LaunchTarget
import com.zendolauncher.ui.theme.LauncherColors
import com.zendolauncher.ui.theme.LauncherSpace
import com.zendolauncher.ui.theme.LauncherType

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(LauncherSpace.hairline)
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

/**
 * Screen header: a title level above the list rows, with a trailing slot for the
 * screen's own action. No container — the title sits directly on the page.
 */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, style = LauncherType.title, color = LauncherColors.current.primary)
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

/**
 * A text-only action, and the product's only button. The clickable is the outermost
 * node so both the touch target and the ripple span the full minimum height; the box
 * centres the label inside it.
 */
@Composable
fun QuietAction(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .combinedClickable(onClick = onClick, role = Role.Button)
            .defaultMinSize(minHeight = LauncherSpace.touchTarget)
            .padding(horizontal = 2.dp),
    ) {
        Text(text = text, style = LauncherType.action, color = LauncherColors.current.primary)
    }
}

/**
 * One app line. Tap launches; long press opens the action menu (pin/hide/gate/reorder).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppRow(
    viewModel: LauncherViewModel,
    target: LaunchTarget,
    isFavourite: Boolean,
    onClick: () -> Unit,
    onToggleFavourite: () -> Unit,
    onToggleHidden: () -> Unit,
    modifier: Modifier = Modifier,
    showPinAction: Boolean = false,
    showFavouriteDot: Boolean = true,
    lowercaseLabel: Boolean = false,
    centered: Boolean = false,
    usageWhisper: String? = null,
    isGated: Boolean = false,
    onToggleGate: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    menuAlignment: Alignment.Horizontal = if (centered) Alignment.CenterHorizontally else Alignment.End,
    longPressMenu: Boolean = true,
) {
    val palette = LauncherColors.current
    var menuOpen by remember { mutableStateOf(false) }
    val displayLabel = if (lowercaseLabel) target.label.lowercase() else target.label

    Box(modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (centered) Arrangement.Center else Arrangement.Start,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = if (longPressMenu) ({ menuOpen = true }) else null,
                    onLongClickLabel = if (longPressMenu) {
                        if (isFavourite) "App options" else "Add to Home"
                    } else {
                        null
                    },
                    role = Role.Button,
                )
                .semantics {
                    stateDescription = if (isFavourite) "On Home" else "Not on Home"
                }
                .defaultMinSize(minHeight = LauncherSpace.touchTarget)
                .padding(vertical = LauncherSpace.sm),
        ) {
            if (centered) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(LauncherSpace.sm),
                ) {
                    Text(
                        text = displayLabel,
                        style = LauncherType.appLabel,
                        color = palette.primary,
                    )
                    usageWhisper?.let {
                        Text(
                            text = it,
                            style = LauncherType.secondary,
                            color = palette.secondary,
                        )
                    }
                }
            } else {
                Text(
                    text = displayLabel,
                    style = LauncherType.appLabel,
                    color = palette.primary,
                    modifier = Modifier.weight(1f),
                )
                usageWhisper?.let {
                    Spacer(Modifier.width(LauncherSpace.sm))
                    Text(
                        text = it,
                        style = LauncherType.secondary,
                        color = palette.secondary,
                    )
                }
                if (isFavourite && showFavouriteDot) {
                    Spacer(Modifier.width(LauncherSpace.sm))
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(palette.secondary),
                    )
                }
                if (showPinAction) {
                    Spacer(Modifier.width(LauncherSpace.md))
                    QuietAction(
                        text = if (isFavourite) "Unpin" else "Pin",
                        onClick = onToggleFavourite,
                        modifier = Modifier.semantics {
                            contentDescription = if (isFavourite) "Unpin ${target.label}" else "Pin ${target.label}"
                        },
                    )
                }
            }
        }

        if (longPressMenu) {
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                modifier = Modifier.align(menuAlignment + Alignment.Top),
            ) {
                onMoveUp?.let { moveUp ->
                    DropdownMenuItem(
                        text = { Text("Move up", style = LauncherType.body) },
                        onClick = {
                            menuOpen = false
                            moveUp()
                        },
                    )
                }
                onMoveDown?.let { moveDown ->
                    DropdownMenuItem(
                        text = { Text("Move down", style = LauncherType.body) },
                        onClick = {
                            menuOpen = false
                            moveDown()
                        },
                    )
                }
                onToggleGate?.let { toggleGate ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (isGated) "Remove Mindful Gate" else "Gate with Mindful Pause",
                                style = LauncherType.body,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            toggleGate()
                        },
                    )
                }
                DropdownMenuItem(
                    text = {
                        Text(
                            if (isFavourite) "Remove from Home" else "Add to Home",
                            style = LauncherType.body,
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onToggleFavourite()
                    },
                )
                DropdownMenuItem(
                    text = { Text(if (isFavourite) "Hide and unpin" else "Hide app", style = LauncherType.body) },
                    onClick = {
                        menuOpen = false
                        onToggleHidden()
                    },
                )
            }
        }
    }
}

/**
 * A quiet boolean. The whole row is the target and the state is legible without colour.
 */
@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LauncherColors.current
    Row(
        modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .defaultMinSize(minHeight = LauncherSpace.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = LauncherType.body,
            color = palette.primary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (checked) "On" else "Off",
            style = LauncherType.action,
            fontWeight = if (checked) FontWeight.Medium else FontWeight.Normal,
            color = if (checked) palette.primary else palette.secondary,
        )
    }
}

/**
 * One line of mutually exclusive options, wrapping cleanly when more than two options exist.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceRow(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LauncherColors.current
    Column(modifier) {
        Text(label, style = LauncherType.body, color = palette.primary)
        Spacer(Modifier.height(LauncherSpace.xs))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(LauncherSpace.lg),
            verticalArrangement = Arrangement.spacedBy(LauncherSpace.xs),
        ) {
            options.forEach { (value, text) ->
                val isSelected = value == selected
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = { onSelect(value) },
                        )
                        .defaultMinSize(minHeight = LauncherSpace.touchTarget),
                ) {
                    Spacer(Modifier.height(LauncherSpace.sm))
                    Text(
                        text = text,
                        style = LauncherType.action,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                        color = if (isSelected) palette.primary else palette.secondary,
                    )
                    Spacer(Modifier.height(LauncherSpace.xs))
                    Box(
                        Modifier
                            .width(20.dp)
                            .height(LauncherSpace.hairline)
                            .background(if (isSelected) palette.primary else Color.Transparent),
                    )
                    Spacer(Modifier.height(LauncherSpace.sm))
                }
            }
        }
    }
}

@Composable
fun StepperRow(
    label: String,
    valueText: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    decreaseLabel: String,
    increaseLabel: String,
    modifier: Modifier = Modifier,
) {
    val palette = LauncherColors.current
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = LauncherSpace.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = LauncherType.body,
            color = palette.primary,
            modifier = Modifier.weight(1f),
        )
        QuietAction(
            text = decreaseLabel,
            onClick = onDecrease,
            modifier = Modifier.semantics { contentDescription = "$decreaseLabel $label" },
        )
        Text(
            text = valueText,
            style = LauncherType.action,
            color = palette.primary,
            modifier = Modifier
                .padding(horizontal = LauncherSpace.md)
                .semantics { contentDescription = "$label $valueText" },
        )
        QuietAction(
            text = increaseLabel,
            onClick = onIncrease,
            modifier = Modifier.semantics { contentDescription = "$increaseLabel $label" },
        )
    }
}

@Composable
fun EmptyNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = LauncherType.secondary,
        color = LauncherColors.current.secondary,
        modifier = modifier.padding(vertical = LauncherSpace.xs),
    )
}

@Composable
fun SecondaryText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = LauncherType.secondary,
        color = LauncherColors.current.secondary,
        modifier = modifier,
    )
}

@Composable
fun UndoNote(text: String, actionLabel: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SecondaryText(text)
        Spacer(Modifier.weight(1f))
        QuietAction(text = actionLabel, onClick = onAction)
    }
}

@Composable
fun AddPageDialog(onPick: (PageType) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(LauncherColors.current.background)
                .padding(LauncherSpace.lg),
        ) {
            Text(
                text = "Add a page",
                style = LauncherType.body,
                color = LauncherColors.current.primary,
            )
            Spacer(Modifier.height(LauncherSpace.sm))
            SecondaryText("A page holds one thing. Apps is the list you already have.")
            Spacer(Modifier.height(LauncherSpace.md))
            ADD_PAGE_CHOICES.forEach { (type, label, note) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onPick(type) }
                        .defaultMinSize(minHeight = LauncherSpace.touchTarget),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(label, style = LauncherType.body, color = LauncherColors.current.primary)
                        SecondaryText(note)
                    }
                }
            }
            Spacer(Modifier.height(LauncherSpace.sm))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                QuietAction("Cancel", onClick = onDismiss)
            }
        }
    }
}

private val ADD_PAGE_CHOICES = listOf(
    Triple(PageType.APPS, "Apps", "Another list of pinned apps."),
    Triple(PageType.TODAY, "Today", "Clock, today's action, highlights and tasks."),
    Triple(PageType.PLAN, "Plan", "Your calendar agenda, day by day."),
    Triple(PageType.WIDGET, "Usage", "Screen usage summary and next calendar event."),
    Triple(PageType.NOTES, "Note", "One line for what this session is for."),
)
