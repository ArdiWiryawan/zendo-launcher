package com.zendolauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.zendolauncher.core.AppQuery
import com.zendolauncher.model.LaunchTarget
import com.zendolauncher.ui.theme.LauncherColors
import com.zendolauncher.ui.theme.LauncherSpace
import com.zendolauncher.ui.theme.LauncherType

/** The bucket for labels that do not normalise to a letter: digits and symbols. */
private const val NON_LETTER_BUCKET = "#"

/**
 * The app drawer. Its job is to put a name into the field, so search leads the screen
 * and the alphabetical list is the fallback for a user who does not know what to type.
 *
 * The screen consumes the IME inset itself rather than the window: the activity is
 * edge-to-edge and, at targetSdk 36, the window no longer resizes for the keyboard, so
 * without it the bottom action row and the last results sit behind the IME.
 */
@Composable
fun AppListScreen(viewModel: LauncherViewModel, state: UiState) {
    val palette = LauncherColors.current
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // The drawer exists to be typed into, so the field takes focus on arrival instead of
    // costing a tap every time.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // Retained across re-entry via the view model (no animation library): leaving for
    // Settings and coming back lands where the list was, not at the top.
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = viewModel.drawerIndex,
        initialFirstVisibleItemScrollOffset = viewModel.drawerOffset,
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.background)
            // Bars before the IME, and the order is load-bearing: each inset modifier
            // consumes the insets it applies, and the IME inset is reported net of what
            // was consumed, so the second call only adds the keyboard's height above the
            // bars. Reversed, the two would stack and leave a bar-height gap above the
            // keyboard for as long as it is open.
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = LauncherSpace.gutter),
    ) {
        Spacer(Modifier.height(LauncherSpace.lg))
        // Swipe-down chrome: the header owns the downward swipe so the scrolling list
        // keeps its gesture. A deliberate pull returns to the same Home page.
        Column(
            Modifier
                .fillMaxWidth()
                .pointerInput(viewModel) {
                    var accumulated = 0f
                    detectVerticalDragGestures(
                        onDragStart = { accumulated = 0f },
                        onVerticalDrag = { change, dragAmount ->
                            accumulated += dragAmount
                            if (accumulated > 120f) change.consume()
                        },
                        onDragEnd = {
                            if (accumulated > 120f) viewModel.goHome()
                            accumulated = 0f
                        },
                        onDragCancel = { accumulated = 0f },
                    )
                },
        ) {
            ScreenTitle("All apps") { QuietAction("Home", onClick = { viewModel.goHome() }) }
            Spacer(Modifier.height(LauncherSpace.md))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = state.query,
                onValueChange = { viewModel.setQuery(it) },
                singleLine = true,
                textStyle = LauncherType.appLabel.copy(color = palette.primary),
                cursorBrush = SolidColor(palette.primary),
                // App names are proper nouns: autocorrect and auto-capitalisation only
                // ever rewrite them into something that matches nothing.
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Search,
                ),
                // The list is already ranked as the query is typed, so submitting has
                // nothing left to resolve. The only friction left is the keyboard over
                // the results, and Search is the gesture that removes it.
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                decorationBox = { inner ->
                    if (state.query.isEmpty()) {
                        Text("Search", style = LauncherType.appLabel, color = palette.secondary)
                    }
                    inner()
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    // The "Search" placeholder is a sibling Text in the decoration box, so
                    // it is not part of this field's semantics and a screen reader would
                    // announce an unnamed text box. Name it here instead.
                    .semantics { contentDescription = "Search apps" },
            )
            if (state.query.isNotEmpty()) {
                QuietAction("Clear", onClick = { viewModel.clearQuery() })
            }
        }

        Spacer(Modifier.height(LauncherSpace.sm))
        Hairline()
        Spacer(Modifier.height(LauncherSpace.md))

        val list = state.results
        SectionHeading(if (state.query.isBlank()) "${list.size} apps" else "Results")
        Spacer(Modifier.height(LauncherSpace.sm))

        if (list.isEmpty()) {
            EmptyNote(
                when {
                    // "No apps" is only true once the first enumeration has finished;
                    // before that the honest state is that nothing is known yet.
                    state.isLoading -> "Looking for apps…"
                    state.query.isBlank() -> "No launchable apps found."
                    else -> "Nothing matches \"${state.query}\"."
                },
            )
        } else {
            // Instant swap, no animation library: state is saved on dispose so
            // re-entry restores the position instead of jumping to the top.
            DisposableEffect(Unit) {
                onDispose {
                    viewModel.drawerIndex = listState.firstVisibleItemIndex
                    viewModel.drawerOffset = listState.firstVisibleItemScrollOffset
                }
            }
            LazyColumn(Modifier.weight(1f), state = listState) {
                if (state.query.isBlank()) {
                    // Headers only without a query: search results are ranked, not
                    // alphabetical, and a letter over them would be a lie.
                    list.groupBy { letterOf(it.label) }.forEach { (letter, group) ->
                        stickyHeader(key = "letter-$letter") { LetterHeader(letter) }
                        items(group, key = { it.key }) { target ->
                            AppListRow(viewModel, state, target)
                        }
                    }
                } else {
                    items(list, key = { it.key }) { target ->
                        AppListRow(viewModel, state, target)
                    }
                }
            }
        }

        state.undoHidden?.let {
            UndoNote(
                text = "Hidden ${it.label}.",
                actionLabel = "Undo",
                onAction = { viewModel.undoHide() },
            )
            Spacer(Modifier.height(LauncherSpace.sm))
        }

        state.pinNotice?.let {
            SecondaryText(it)
            Spacer(Modifier.height(LauncherSpace.sm))
        }

        Spacer(Modifier.height(LauncherSpace.md))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(LauncherSpace.lg),
        ) {
            QuietAction("Settings", onClick = { viewModel.openSettings(Screen.APPS) })
            QuietAction("Refresh", onClick = { viewModel.refreshApps() })
        }
        Spacer(Modifier.height(LauncherSpace.sm))
    }
}

/**
 * The bucket a label files under, derived from the same normalisation the sort uses so
 * an accented name lands under its base letter exactly where it was ordered. Digits and
 * symbols share one bucket, which sorts first because they precede letters.
 */
internal fun letterOf(label: String): String {
    val first = AppQuery.normalize(label).firstOrNull() ?: return NON_LETTER_BUCKET
    return if (first.isLetter()) first.uppercaseChar().toString() else NON_LETTER_BUCKET
}

/** One row of the drawer. No separator: at this length a rule per row is texture. */
@Composable
private fun AppListRow(viewModel: LauncherViewModel, state: UiState, target: LaunchTarget) {
    // Pin state is whether the key lives on any Home page — the legacy flat favourites
    // list is migration-only and is never updated after migration.
    val pinnedKeys = remember(state.settings.home) {
        state.settings.home.pages.flatMap { it.config.itemKeys }.toSet()
    }
    AppRow(
        viewModel = viewModel,
        target = target,
        layout = state.settings.layout,
        isFavourite = target.key in pinnedKeys,
        onClick = { viewModel.launch(target) },
        onToggleFavourite = { viewModel.toggleFavourite(target.key) },
        onToggleHidden = { viewModel.toggleHidden(target.key) },
        // The long press stays for anyone who knows it, but pinning must not depend on
        // discovering it, so the drawer shows the action outright.
        showPinAction = true,
    )
}

/** Opaque, because a sticky header floats over the rows scrolling beneath it. */
@Composable
private fun LetterHeader(letter: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(LauncherColors.current.background)
            .padding(vertical = LauncherSpace.sm),
    ) {
        SectionHeading(letter)
    }
}
