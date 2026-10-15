package com.zendolauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.zendolauncher.core.HomeLayout
import com.zendolauncher.core.HomePage
import com.zendolauncher.core.PageType
import com.zendolauncher.ui.theme.LauncherColors
import com.zendolauncher.ui.theme.LauncherSpace
import com.zendolauncher.ui.theme.LauncherType
import com.zendolauncher.ui.theme.clockSizeSp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The launcher's most important surface, and its job is to let the user act and leave.
 * It is now one to five pages sharing one gesture, so the clock is still the only large
 * element, empty space is still left empty, and nothing is added to fill a page.
 *
 * Every page renders something true. A page whose content type has no backing
 * implementation says so in words: it never invents data, and it never offers a control
 * that does nothing.
 */
@Composable
fun HomeScreen(viewModel: LauncherViewModel, state: UiState) {
    val palette = LauncherColors.current
    val pages = state.settings.home.pages
    // addPage() inserts after the active page, so the index the pager must already be
    // showing is the active one. It falls back to 0 only if the active id resolves to
    // nothing, which sanitize has already ruled out.
    val initialPage = pages.indexOfFirst { it.id == state.settings.home.activePageId }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage) { pages.size }

    // The stored layout is the source of truth; the pager is a view of it. This effect is
    // the only place the two are reconciled, and it is keyed on the settled page rather
    // than the current one: currentPage changes at the halfway mark of a fling and would
    // store a page the user then abandons.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { viewModel.onPagerSettled(it) }
    }
    // One-shot programmatic move. A scroll that comes to rest lands on the page it reached
    // and clears the target through the settled-page effect above, which is also what keeps
    // this from firing again: the state that drove it is gone by the next recomposition.
    // Reduce-motion jumps instead of animating.
    LaunchedEffect(state.pagerTargetPage, state.settings.reduceMotion) {
        val target = state.pagerTargetPage ?: return@LaunchedEffect
        if (target in 0 until pagerState.pageCount) {
            if (state.settings.reduceMotion) pagerState.scrollToPage(target)
            else pagerState.animateScrollToPage(target)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.background)
            .systemBarsPadding(),
    ) {
        if (state.pageEditing) {
            PageManager(
                viewModel = viewModel,
                state = state,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                userScrollEnabled = pages.size > 1 && state.settings.pagingEnabled,
            ) { index ->
                val page = pages.getOrNull(index) ?: return@HorizontalPager
                // Keyed on the page id so a page's own remembered state — the focus note's
                // draft in particular — does not leak into whatever page takes its index
                // after a reorder.
                key(page.id) {
                    PageBody(page, viewModel, state)
                }
            }
        }

        state.undoHidden?.let {
            // The gutter lives on the scrolling page above, not on this column, so the note
            // has to carry its own or its text starts flush against the screen edge.
            UndoNote(
                text = "Hidden ${it.label}.",
                actionLabel = "Undo",
                onAction = { viewModel.undoHide() },
                modifier = Modifier.padding(horizontal = LauncherSpace.gutter),
            )
            Spacer(Modifier.height(LauncherSpace.sm))
        }

        if (!state.pageEditing && pages.size > 1) {
            PageIndicator(pages, pagerState) { viewModel.goToPage(it) }
        }
        if (state.pageEditing) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = LauncherSpace.gutter, vertical = LauncherSpace.md),
                horizontalArrangement = Arrangement.spacedBy(LauncherSpace.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QuietAction("Done", onClick = { viewModel.leavePageEdit() })
                // Absent at the limit rather than present-and-inert: a control that cannot
                // do anything is worse than one that is not there.
                if (pages.size < HomeLayout.MAX_PAGES) {
                    QuietAction("Add page", onClick = { viewModel.addPage() })
                }
            }
        } else {
            // Swipe-up chrome: the bottom bar owns the vertical swipe so scrollable
            // page content keeps its gesture. Taps still reach the actions; a deliberate
            // upward drag opens the drawer instead.
            Row(
                Modifier
                    .fillMaxWidth()
                    .swipeUpToOpen(state.settings.swipeUpEnabled) { viewModel.openApps() }
                    .padding(horizontal = LauncherSpace.gutter, vertical = LauncherSpace.md),
                horizontalArrangement = Arrangement.spacedBy(LauncherSpace.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QuietAction("All apps", onClick = { viewModel.openApps() })
                QuietAction("Edit pages", onClick = { viewModel.enterPageEdit() })
                QuietAction("Settings", onClick = { viewModel.openSettings(Screen.HOME) })
            }
        }
    }

    // Persistent Today overlays: highlight editor + full lists. Dialogs, so Back
    // closes them via viewModel.back() before anything else on Home.
    HighlightOverlays(viewModel, state)
}

/**
 * Vertical swipe detector for chrome rows. Only fires on a deliberate upward drag
 * past the touch slop + threshold; taps pass through to the row's actions.
 */
private fun Modifier.swipeUpToOpen(enabled: Boolean, onSwipeUp: () -> Unit): Modifier {
    if (!enabled) return this
    return this.pointerInput(onSwipeUp) {
        var accumulated = 0f
        detectVerticalDragGestures(
            onDragStart = { accumulated = 0f },
            onVerticalDrag = { change, dragAmount ->
                accumulated += dragAmount
                // Consume only once the gesture is clearly a swipe, so taps still click.
                if (accumulated < -120f) {
                    change.consume()
                }
            },
            onDragEnd = {
                if (accumulated < -120f) onSwipeUp()
                accumulated = 0f
            },
            onDragCancel = { accumulated = 0f },
        )
    }
}

/** One page's content. Each type is its own composable, so a page's job is legible. */
@Composable
private fun PageBody(page: HomePage, viewModel: LauncherViewModel, state: UiState) {
    when (page.type) {
        PageType.TODAY -> TodayPage(viewModel, state)
        PageType.APPS -> LaunchPage(page, viewModel, state)
        PageType.NOTES -> FocusPage(viewModel, state)
        PageType.WIDGET -> WidgetPage(page)
    }
}

// ---- Today -------------------------------------------------------------------------

/**
 * Date first, then Highlights / Tasks / Upcoming from local stores + device calendar.
 * Persistent: highlights stay until edited/archived, tasks keep completion, upcoming
 * reads the device provider when permission is granted. Nothing is invented.
 */
@Composable
private fun TodayPage(viewModel: LauncherViewModel, state: UiState) {
    val palette = LauncherColors.current
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val heightDp = with(density) { windowInfo.containerSize.height.toDp() }
    // A fraction of the real window height rather than a fixed step, so the block below
    // sits at the same height on a short screen and a tall one. The spacing scale is the
    // floor and the ceiling, because a fraction of a very short or very tall window is
    // either cramped or a gap nobody asked for.
    val topGap = (heightDp * 0.06f).coerceIn(LauncherSpace.lg, LauncherSpace.xl)
    // Measured here and handed to the clock, which needs the same width to size itself.
    val widthDp = with(density) { windowInfo.containerSize.width.toDp().value }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = LauncherSpace.gutter),
    ) {
        // The status-bar inset is already consumed above, so this is the clock's breathing
        // room on top of it. The size is not a guess: see topGap above.
        Spacer(Modifier.height(topGap))
        Clock(state.settings.clock24h, widthDp)
        Spacer(Modifier.height(LauncherSpace.xs))
        Text(
            text = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", currentLocale())),
            style = LauncherType.date,
            color = palette.secondary,
        )
        Spacer(Modifier.height(LauncherSpace.xs))
        Text(text = "Today.", style = LauncherType.title, color = palette.primary)

        if (state.settings.todayEnabled) {
            Spacer(Modifier.height(LauncherSpace.md))
            Hairline()
            Spacer(Modifier.height(LauncherSpace.md))
            TodayDashboard(viewModel, state)
        } else {
            Spacer(Modifier.height(LauncherSpace.md))
            EmptyNote("Today is hidden. Turn on Show today in Settings to see highlights, tasks and upcoming.")
        }

        // The shortcut exists only when a Focus page does. It moves the pager and nothing
        // else, so it is a real action rather than a placeholder.
        val focusIndex = state.settings.home.pages.indexOfFirst { it.type == PageType.NOTES }
        if (focusIndex >= 0) {
            Spacer(Modifier.height(LauncherSpace.xl))
            QuietAction("Focus", onClick = { viewModel.goToPage(focusIndex) })
        }

        Spacer(Modifier.height(LauncherSpace.xl))
    }
}

// ---- Launch ------------------------------------------------------------------------

/**
 * The one page that holds an ordered app list. Reordering is a long press and then a drag,
 * which is why these rows do not open the pin/hide menu on a long press: one gesture cannot
 * both lift a row and open a popup. Unpin stays reachable on the row itself and the menu is
 * untouched everywhere else — see [AppRow].
 *
 * A row dragged past its neighbour is written to the store immediately rather than on drop,
 * so the order on screen is always the order that is stored.
 */
@Composable
private fun LaunchPage(page: HomePage, viewModel: LauncherViewModel, state: UiState) {
    val targets = if (state.settings.home.activePageId == page.id) {
        state.favourites
    } else {
        // A page that is not active has no list in UiState; it resolves its own from
        // everything visible, through the same AppQuery the active page uses.
        val byKey = state.visibleApps.associateBy { it.key }
        page.config.itemKeys.mapNotNull { byKey[it] }
    }
    var rowHeightPx by remember { mutableFloatStateOf(0f) }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var accumulated by remember { mutableFloatStateOf(0f) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = LauncherSpace.gutter),
    ) {
        Spacer(Modifier.height(LauncherSpace.xl))
        SectionHeading(page.config.heading ?: "Apps")
        Spacer(Modifier.height(LauncherSpace.sm))

        if (targets.isEmpty()) {
            EmptyNote("No pinned apps here yet. Open All apps and pin the ones you want.")
        } else {
            targets.forEach { target ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .zIndex(if (draggingKey == target.key) 1f else 0f)
                        .onSizeChanged { if (rowHeightPx == 0f) rowHeightPx = it.height.toFloat() }
                        .pointerInput(page.id, targets.size) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingKey = target.key
                                    accumulated = 0f
                                },
                                onDragEnd = {
                                    draggingKey = null
                                    accumulated = 0f
                                },
                                onDragCancel = {
                                    draggingKey = null
                                    accumulated = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val step = rowHeightPx
                                    if (step <= 0f) return@detectDragGesturesAfterLongPress
                                    accumulated += dragAmount.y
                                    // One slot per row height travelled. The index is looked
                                    // up by key each time, because the row's position has
                                    // moved by then.
                                    while (accumulated >= step) {
                                        viewModel.moveItemBy(page.id, target.key, 1)
                                        accumulated -= step
                                    }
                                    while (accumulated <= -step) {
                                        viewModel.moveItemBy(page.id, target.key, -1)
                                        accumulated += step
                                    }
                                },
                            )
                        },
                ) {
                    AppRow(
                        viewModel = viewModel,
                        target = target,
                        layout = state.settings.layout,
                        isFavourite = true,
                        onClick = { viewModel.launch(target) },
                        onToggleFavourite = { viewModel.toggleFavourite(target.key) },
                        onToggleHidden = { viewModel.toggleHidden(target.key) },
                        longPressMenu = false,
                        showPinAction = true,
                    )
                }
            }
        }
        Spacer(Modifier.height(LauncherSpace.xl))
    }
}

// ---- Focus -------------------------------------------------------------------------

/**
 * The Focus page. Its note is the only thing here with storage behind it; everything else
 * states plainly that it is not built, rather than offering a control that would do
 * nothing or a number that would be made up.
 */
@Composable
private fun FocusPage(viewModel: LauncherViewModel, state: UiState) {
    val palette = LauncherColors.current
    // Seeded from the store and written on every keystroke, so there is nothing to press to
    // save and nothing to lose if the process dies mid-sentence.
    var draft by remember { mutableStateOf(state.focusNote) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = LauncherSpace.gutter),
    ) {
        Spacer(Modifier.height(LauncherSpace.xl))
        SectionHeading("Focus session")
        Spacer(Modifier.height(LauncherSpace.sm))
        EmptyNote("Sessions are not built yet. Nothing here runs a timer or blocks apps.")

        Spacer(Modifier.height(LauncherSpace.xl))
        SectionHeading("Quick note")
        Spacer(Modifier.height(LauncherSpace.sm))
        BasicTextField(
            value = draft,
            onValueChange = {
                draft = it
                viewModel.setFocusNote(it)
            },
            textStyle = LauncherType.body.copy(color = palette.primary),
            cursorBrush = SolidColor(palette.primary),
            // Proper nouns and fragments: auto-capitalisation would rewrite the start of
            // every line into something the user did not type.
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Default),
            keyboardActions = KeyboardActions(),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Quick note" },
            decorationBox = { inner ->
                if (draft.isEmpty()) SecondaryText("One line for what this session is for.")
                inner()
            },
        )

        Spacer(Modifier.height(LauncherSpace.xl))
        SectionHeading("Wellbeing")
        Spacer(Modifier.height(LauncherSpace.sm))
        // The pause is the one wellbeing feature this launcher has. It is not re-implemented
        // here as a second switch; this is the route to where its controls live.
        QuietAction("Wellbeing settings", onClick = { viewModel.openSettings(Screen.HOME) })

        Spacer(Modifier.height(LauncherSpace.xl))
        SectionHeading("Usage")
        Spacer(Modifier.height(LauncherSpace.sm))
        // Usage access is optional and off by default; the summary and its switch
        // live in Settings, so this stays a route rather than a second switch.
        EmptyNote("Usage summary is optional and off unless you allow it in Settings.")
        Spacer(Modifier.height(LauncherSpace.xl))
    }
}

// ---- Widget ------------------------------------------------------------------------

/**
 * Recognised and stored, not built. The type exists so the serializer round-trips it and a
 * page can be re-typed without losing its config; nothing creates one. The page says what
 * it is rather than drawing an empty rectangle.
 */
@Composable
private fun WidgetPage(page: HomePage) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = LauncherSpace.gutter),
    ) {
        Spacer(Modifier.height(LauncherSpace.xl))
        SectionHeading(page.config.heading ?: "Widget")
        Spacer(Modifier.height(LauncherSpace.sm))
        EmptyNote("A widget page is not built yet. No widget is hosted here.")
        Spacer(Modifier.height(LauncherSpace.xl))
    }
}

// ---- Edit mode ---------------------------------------------------------------------

/**
 * The page manager: every page as a row that can be renamed in place, moved, or removed.
 * Removing is absent at the last page rather than present and inert, and the reason is the
 * count on screen. Renaming is an inline field, matching the drawer's search field, because
 * a dialog for one label would be the loudest surface this app owns.
 */
@Composable
private fun PageManager(viewModel: LauncherViewModel, state: UiState, modifier: Modifier = Modifier) {
    val pages = state.settings.home.pages

    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = LauncherSpace.gutter),
    ) {
        Spacer(Modifier.height(LauncherSpace.lg))
        SectionHeading("Home pages")
        Spacer(Modifier.height(LauncherSpace.xs))
        // The reason is stated where the missing action would have been.
        SecondaryText(
            if (pages.size >= HomeLayout.MAX_PAGES) {
                "Five pages is the limit."
            } else {
                "Long press a page name to rename it. Use Up and Down to reorder."
            },
        )
        Spacer(Modifier.height(LauncherSpace.sm))

        pages.forEachIndexed { index, page ->
            if (index > 0) Hairline()
            PageManagerRow(
                page = page,
                index = index,
                pageCount = pages.size,
                viewModel = viewModel,
            )
        }
        Spacer(Modifier.height(LauncherSpace.xl))
    }
}

@Composable
private fun PageManagerRow(page: HomePage, index: Int, pageCount: Int, viewModel: LauncherViewModel) {
    val palette = LauncherColors.current
    var renaming by remember(page.id) { mutableStateOf(false) }
    var draft by remember(page.id) { mutableStateOf(page.name) }
    val focusRequester = remember { FocusRequester() }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = LauncherSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (renaming) {
            LaunchedEffect(page.id) { focusRequester.requestFocus() }
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                textStyle = LauncherType.body.copy(color = palette.primary),
                cursorBrush = SolidColor(palette.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                // Committing here and on focus loss below means a rename cannot be lost by
                // pressing the key the user expects to save it.
                keyboardActions = KeyboardActions(
                    onDone = {
                        viewModel.renamePage(page.id, draft)
                        renaming = false
                    },
                ),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .semantics { contentDescription = "Page name" },
            )
        } else {
            Text(
                text = page.name,
                style = LauncherType.body,
                color = palette.primary,
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        draft = page.name
                        renaming = true
                    }
                    .semantics {
                        contentDescription = "Page ${index + 1}, ${page.name}. Rename"
                    },
            )
        }

        // The type is stated rather than editable: nothing in this build creates a page of
        // any type but apps, and a control that could produce an unbuildable page would be
        // the one dead control on the screen.
        Spacer(Modifier.width(LauncherSpace.md))
        SecondaryText(page.type.label())

        Spacer(Modifier.width(LauncherSpace.md))
        QuietAction(
            text = "Up",
            onClick = { viewModel.movePage(page.id, -1) },
            modifier = Modifier.semantics { contentDescription = "Move ${page.name} up" },
        )
        QuietAction(
            text = "Down",
            onClick = { viewModel.movePage(page.id, 1) },
            modifier = Modifier.semantics { contentDescription = "Move ${page.name} down" },
        )
        if (pageCount > HomeLayout.MIN_PAGES) {
            QuietAction(
                text = "Remove",
                onClick = { viewModel.removePage(page.id) },
                modifier = Modifier.semantics { contentDescription = "Remove ${page.name}" },
            )
        }
    }
}

// ---- Chrome ------------------------------------------------------------------------

/**
 * The page indicator: one dot per page, the current one larger and brighter, each named for
 * a reader. Dots are the thinnest thing that can say "there are more pages this way" without
 * taking a line of text from the page itself.
 */
@Composable
private fun PageIndicator(pages: List<HomePage>, pagerState: PagerState, onGoTo: (Int) -> Unit) {
    val palette = LauncherColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = LauncherSpace.gutter, vertical = LauncherSpace.sm),
        horizontalArrangement = Arrangement.spacedBy(LauncherSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        pages.forEachIndexed { index, page ->
            val selected = pagerState.currentPage == index
            Box(
                Modifier
                    .size(if (selected) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(if (selected) palette.primary else palette.separator)
                    .clickable { onGoTo(index) }
                    .semantics {
                        contentDescription = "${page.name}, page ${index + 1} of ${pages.size}"
                        // Colour and size are not the only signal: a reader is told which
                        // page is showing.
                        stateDescription = if (selected) "Showing" else "Not showing"
                    },
            )
        }
    }
}

// ---- Shared -------------------------------------------------------------------------

/**
 * Ticks on the minute boundary rather than every second: the display only ever shows
 * minutes, and a home screen that wakes the CPU 86,400 times a day is not calm.
 *
 * The size is fitted to the screen instead of fixed, because at a raised system font
 * scale a 60.sp clock is wider than the text column and clips. [widthDp] is the window's
 * measured width, which is what the text column actually has to fit into.
 */
@Composable
private fun Clock(use24Hour: Boolean, widthDp: Float) {
    val now by produceState(initialValue = LocalTime.now()) {
        while (true) {
            val current = LocalTime.now()
            value = current
            delay(1_000L * (60 - current.second))
        }
    }
    val locale = currentLocale()
    // LocalConfiguration is snapshot-backed, so a font-scale change re-runs this. The
    // width is not read from it: LocalWindowInfo carries the real container size.
    val configuration = LocalConfiguration.current
    val clockStyle = LauncherType.clock.copy(
        fontSize = clockSizeSp(widthDp.toInt(), configuration.fontScale).sp,
    )
    val palette = LauncherColors.current
    val pattern = if (use24Hour) "HH:mm" else "h:mm"
    val text = now.format(DateTimeFormatter.ofPattern(pattern, locale))
    Row(verticalAlignment = Alignment.Bottom) {
        Text(text = text, style = clockStyle, color = palette.primary, maxLines = 1)
        if (!use24Hour) {
            Text(
                text = now.format(DateTimeFormatter.ofPattern("a", locale)),
                style = LauncherType.secondary,
                color = palette.secondary,
                // Lifts the smaller suffix onto the clock's baseline.
                modifier = Modifier.padding(start = LauncherSpace.sm, bottom = LauncherSpace.md),
            )
        }
    }
}

/**
 * The current locale as observable state. Reading Locale.getDefault() inside a
 * composable would not re-run when the user changes locale, leaving a stale date
 * format on screen until the next process start.
 */
@Composable
private fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

/** A page's type as a word, for the manager row. */
private fun PageType.label(): String = when (this) {
    PageType.TODAY -> "Today"
    PageType.APPS -> "Apps"
    PageType.NOTES -> "Focus"
    PageType.WIDGET -> "Widget"
}
