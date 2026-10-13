package com.zendolauncher.ui

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zendolauncher.data.LayoutMode
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
 * One app line. Tap launches; long press opens the pin/hide menu. Rows are tall
 * enough to hit comfortably without looking like buttons.
 *
 * Favourite state is reported through `stateDescription` so TalkBack can announce it —
 * the dot is the only visual cue and it is invisible to a screen reader.
 *
 * The hide item names the unpin it also performs. Hiding a pinned app drops it from
 * Essentials in the same tap, and a menu that said only "Hide app" would let the user
 * lose an ordered entry without being told.
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
    showFavouriteToggle: Boolean = false,
    menuAlignment: Alignment.Horizontal = Alignment.End,
) {
    val palette = LauncherColors.current
    var menuOpen by remember { mutableStateOf(false) }

    Box(modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuOpen = true },
                    onLongClickLabel = if (isFavourite) "Remove from Essentials" else "Add to Essentials",
                    role = Role.Button,
                )
                .semantics {
                    stateDescription = if (isFavourite) "In Essentials" else "Not in Essentials"
                }
                .defaultMinSize(minHeight = LauncherSpace.touchTarget)
                .padding(vertical = LauncherSpace.sm),
        ) {
            if (layout == LayoutMode.ICON_TEXT) {
                AppIcon(viewModel, target, sizeDp = 28)
                Spacer(Modifier.width(14.dp))
            }
            Text(
                text = target.label,
                style = LauncherType.appLabel,
                color = palette.primary,
                modifier = Modifier.weight(1f),
            )
            if (isFavourite) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(palette.secondary),
                )
            }
            // The label keeps weight(1f), so the affordance can never push it off-screen.
            if (showFavouriteToggle) {
                Spacer(Modifier.width(LauncherSpace.md))
                QuietAction(
                    text = if (isFavourite) "Remove" else "Add",
                    onClick = onToggleFavourite,
                    modifier = Modifier.semantics {
                        contentDescription = if (isFavourite) {
                            "Remove ${target.label} from Essentials"
                        } else {
                            "Add ${target.label} to Essentials"
                        }
                    },
                )
            }
        }

        // Anchored to the row's trailing edge by default, so the menu opens under the
        // finger that summoned it rather than at the row's top-left corner.
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            modifier = Modifier.align(menuAlignment + Alignment.Top),
        ) {
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
                text = { Text(if (isFavourite) "Hide and unpin" else "Hide app", style = LauncherType.body) },
                onClick = {
                    menuOpen = false
                    onToggleHidden()
                },
            )
        }
    }
}

/**
 * Icons are loaded off the main thread and only when a layout asks for them. The slot is
 * emitted even while the bitmap is null, so a resolving icon cannot shift the label.
 */
@Composable
private fun AppIcon(viewModel: LauncherViewModel, target: LaunchTarget, sizeDp: Int) {
    val px = with(LocalDensity.current) { sizeDp.dp.roundToPx() }
    val bitmap by produceState<Bitmap?>(initialValue = null, target.key) {
        value = viewModel.bitmapFor(target, px)
    }
    Box(Modifier.size(sizeDp.dp)) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * A quiet boolean. The whole row is the target and the state is legible without colour:
 * the trailing word, its weight and its tone all change together. The word is used
 * rather than a tick glyph because the default font family does not cover U+2713 and
 * would depend on a system fallback that some builds drop, rendering a tofu box.
 * A Material pill switch would be the loudest control in a text-first launcher.
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
 * One line of mutually exclusive options. The selected option differs by a hairline
 * marker and a weight change as well as by colour, so selection survives low vision and
 * colour blindness. No container: a row of text is the default unit.
 */
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
        Row(horizontalArrangement = Arrangement.spacedBy(LauncherSpace.lg)) {
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
                    Spacer(Modifier.weight(1f))
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
                    Spacer(Modifier.weight(1f))
                }
            }
        }
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
