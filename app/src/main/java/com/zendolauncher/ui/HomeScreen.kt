package com.zendolauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.zendolauncher.core.CalendarGlance
import com.zendolauncher.core.HomeLayout
import com.zendolauncher.core.HomePage
import com.zendolauncher.core.PageType
import com.zendolauncher.core.UsageCalculator
import com.zendolauncher.data.HomeAlignment
import com.zendolauncher.data.HomeMode
import com.zendolauncher.data.LabelCase
import com.zendolauncher.ui.theme.HomeBackdrop
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
 *
 * A fresh install is one page holding the apps that were pinned. Everything else is added
 * on purpose from Edit pages, so the first screen is never a dashboard the user did not
 * ask for. Empty space is left empty; nothing is added to fill a page.
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
        snapshotFlow { pagerState.settledPage }.collect {
            viewModel.onPagerSettled(it)
            // Agenda freshness follows visibility, not a timer: settling on Plan &
            // Focus re-queries only when the cache is stale.
            if (pages.getOrNull(it)?.type == PageType.PLAN) viewModel.onPlanPageVisible()
        }
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

    Box(Modifier.fillMaxSize()) {
        HomeBackdrop(
            style = state.settings.backdropStyle,
            scrimStrength = state.settings.scrimStrength,
            blurPhoto = state.settings.backdropBlur,
            customBitmap = state.customWallpaperBitmap,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding(),
        ) {
            if (state.settings.homeMode == HomeMode.QUIET) {
                QuietHomePage(
                    viewModel = viewModel,
                    state = state,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            } else if (state.pageEditing) {
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
                    userScrollEnabled = pages.size > 1,
                ) { index ->
                    val page = pages.getOrNull(index) ?: return@HorizontalPager
                    key(page.id) {
                        PageBody(page, viewModel, state)
                    }
                }
            }

            state.undoHidden?.let {
                UndoNote(
                    text = "Hidden ${it.label}.",
                    actionLabel = "Undo",
                    onAction = { viewModel.undoHide() },
                    modifier = Modifier.padding(horizontal = LauncherSpace.gutter),
                )
                Spacer(Modifier.height(LauncherSpace.sm))
            }

            state.pinNotice?.let {
                SecondaryText(it, modifier = Modifier.padding(horizontal = LauncherSpace.gutter))
                Spacer(Modifier.height(LauncherSpace.sm))
            }

            if (state.settings.homeMode == HomeMode.CALM_CANVAS && !state.pageEditing && pages.size > 1) {
                PageIndicator(pages, pagerState) { viewModel.goToPage(it) }
            }
            if (state.settings.homeMode == HomeMode.CALM_CANVAS && state.pageEditing) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = LauncherSpace.gutter, vertical = LauncherSpace.md),
                    horizontalArrangement = Arrangement.spacedBy(LauncherSpace.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    QuietAction("Done", onClick = { viewModel.leavePageEdit() })
                    if (pages.size < HomeLayout.MAX_PAGES) {
                        QuietAction("Add page", onClick = { viewModel.openAddPage() })
                    }
                }
            } else {
                val pillShape = RoundedCornerShape(999.dp)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = LauncherSpace.gutter)
                        .height(LauncherSpace.touchTarget)
                        .clip(pillShape)
                        .border(LauncherSpace.hairline, palette.separator, pillShape)
                        .clickable(role = Role.Button) { viewModel.openApps(autoFocusSearch = true) },
                ) {
                    Text("Search apps", style = LauncherType.action, color = palette.secondary)
                }
                Spacer(Modifier.height(LauncherSpace.sm))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .swipeUpToOpen { viewModel.openApps() }
                        .padding(horizontal = LauncherSpace.gutter, vertical = LauncherSpace.md),
                    horizontalArrangement = Arrangement.spacedBy(LauncherSpace.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    QuietAction("All apps", onClick = { viewModel.openApps() })
                    if (state.settings.homeMode == HomeMode.QUIET) {
                        QuietAction("Calendar", onClick = { viewModel.openCalendar() })
                        QuietAction("Usage", onClick = { viewModel.openUsage() })
                    } else {
                        QuietAction("Edit pages", onClick = { viewModel.enterPageEdit() })
                    }
                    QuietAction("Settings", onClick = { viewModel.openSettings(Screen.HOME) })
                }
            }
        }
    }

    // Persistent Today overlays: highlight editor + full lists. Dialogs, so Back
    // closes them via viewModel.back() before anything else on Home.
    HighlightOverlays(viewModel, state)

    if (state.addingPage) {
        AddPageDialog(
            onPick = { type -> viewModel.addPage(type) },
            onDismiss = { viewModel.closeAddPage() },
        )
    }
}

/**
 * Vertical swipe detector for chrome rows. Only fires on a deliberate upward drag
 * past the touch slop + threshold; taps pass through to the row's actions.
 */
private fun Modifier.swipeUpToOpen(onSwipeUp: () -> Unit): Modifier =
    this.pointerInput(onSwipeUp) {
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

/** One page's content. Each type is its own composable, so a page's job is legible. */
@Composable
private fun PageBody(page: HomePage, viewModel: LauncherViewModel, state: UiState) {
    when (page.type) {
        PageType.TODAY -> TodayPage(viewModel, state)
        PageType.APPS -> LaunchPage(page, viewModel, state)
        PageType.PLAN -> PlanPage(viewModel, state)
        PageType.NOTES -> NotePage(viewModel, state)
        PageType.WIDGET -> UsagePage(viewModel, state)
    }
}

// ---- Quiet Home (Option 1 default) -------------------------------------------------

/**
 * The default ultra-clean Home experience: clock, date, calm calendar glance, optional
 * screen-time whisper, and clean pinned apps with no trailing Unpin noise.
 */
@Composable
private fun QuietHomePage(
    viewModel: LauncherViewModel,
    state: UiState,
    modifier: Modifier = Modifier,
) {
    val palette = LauncherColors.current
    val centered = state.settings.homeAlignment == HomeAlignment.CENTER
    val lowercase = state.settings.labelCase == LabelCase.LOWERCASE
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val heightDp = with(density) { windowInfo.containerSize.height.toDp() }
    val widthDp = with(density) { windowInfo.containerSize.width.toDp().value }
    val topGap = (heightDp * 0.05f).coerceIn(LauncherSpace.md, LauncherSpace.xl)

    val appsPage = state.settings.home.pages.firstOrNull { it.type == PageType.APPS }
    val byKey = remember(state.visibleApps) { state.visibleApps.associateBy { it.key } }
    val targets = remember(appsPage, byKey) {
        appsPage?.config?.itemKeys?.mapNotNull { byKey[it] }.orEmpty()
    }

    Column(
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = LauncherSpace.gutter),
    ) {
        Spacer(Modifier.height(topGap))
        Clock(widthDp)
        Spacer(Modifier.height(LauncherSpace.xs))
        Text(
            text = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", currentLocale())),
            style = LauncherType.date,
            color = palette.secondary,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        )

        if (state.settings.showCalendarGlance) {
            Spacer(Modifier.height(LauncherSpace.md))
            CalendarGlanceBlock(
                state = state,
                centered = centered,
                onClick = { viewModel.openCalendar() },
            )
        }

        if (state.settings.showUsageOnHome && state.usageAccess && state.dailyUsage.totalForegroundMillis > 0L) {
            Spacer(Modifier.height(LauncherSpace.xs))
            val totalText = UsageCalculator.formatDuration(state.dailyUsage.totalForegroundMillis)
            val whisper = if (lowercase) "screen time · $totalText" else "Screen time · $totalText"
            Text(
                text = whisper,
                style = LauncherType.secondary,
                color = palette.secondary,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.clickable(role = Role.Button) { viewModel.openUsage() },
            )
        }

        Spacer(Modifier.height(LauncherSpace.lg))

        if (targets.isEmpty()) {
            EmptyNote(
                text = "No apps on Home yet. Open All apps and pin the essentials.",
                modifier = Modifier.fillMaxWidth(),
            )
            QuietAction("Open All apps", onClick = { viewModel.openApps() })
        } else {
            targets.forEachIndexed { index, target ->
                val usageWhisper = if (state.settings.showUsageOnHome && state.usageAccess) {
                    state.dailyUsage.byPackage[target.packageName]
                        ?.takeIf { it.foregroundMillis >= 60_000L }
                        ?.let { UsageCalculator.formatDuration(it.foregroundMillis) }
                } else {
                    null
                }
                AppRow(
                    viewModel = viewModel,
                    target = target,
                    isFavourite = true,
                    onClick = { viewModel.launch(target) },
                    onToggleFavourite = { viewModel.toggleFavourite(target.key) },
                    onToggleHidden = { viewModel.toggleHidden(target.key) },
                    showPinAction = false,
                    showFavouriteDot = false,
                    lowercaseLabel = lowercase,
                    centered = centered,
                    usageWhisper = usageWhisper,
                    isGated = target.key in state.settings.distract,
                    onToggleGate = {
                        if (!state.settings.pauseEnabled) viewModel.setPauseEnabled(true)
                        viewModel.toggleDistract(target.key)
                    },
                    onMoveUp = if (index > 0) ({ viewModel.reorderPinnedOnHome(target.key, -1) }) else null,
                    onMoveDown = if (index < targets.lastIndex) ({ viewModel.reorderPinnedOnHome(target.key, 1) }) else null,
                    longPressMenu = true,
                )
            }
        }
        Spacer(Modifier.height(LauncherSpace.xl))
    }
}

@Composable
private fun CalendarGlanceBlock(
    state: UiState,
    centered: Boolean,
    onClick: () -> Unit,
) {
    val palette = LauncherColors.current
    val align = if (centered) TextAlign.Center else TextAlign.Start
    Column(
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        modifier = Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = LauncherSpace.xs),
    ) {
        when {
            !state.calendarPermission -> {
                Text(
                    text = "Tap to connect Google Calendar",
                    style = LauncherType.secondary,
                    color = palette.secondary,
                    textAlign = align,
                )
            }
            state.settings.shownCalendarIds.isEmpty() -> {
                Text(
                    text = "Tap to select Google Calendar",
                    style = LauncherType.secondary,
                    color = palette.secondary,
                    textAlign = align,
                )
            }
            else -> {
                val summary = remember(state.calendarEvents) {
                    CalendarGlance.summarize(state.calendarEvents, System.currentTimeMillis())
                }
                Text(
                    text = summary.headline,
                    style = LauncherType.body,
                    color = palette.primary,
                    textAlign = align,
                )
                summary.subline?.let {
                    Text(
                        text = it,
                        style = LauncherType.secondary,
                        color = palette.secondary,
                        textAlign = align,
                    )
                }
            }
        }
    }
}

// ---- Today -------------------------------------------------------------------------

/**
 * Date first, then Today's action, highlights and tasks from local stores. Persistent:
 * highlights stay until edited/archived, tasks keep completion. The calendar lives on
 * the Plan page and is not duplicated here. Nothing is invented.
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
        Clock(widthDp)
        Spacer(Modifier.height(LauncherSpace.xs))
        Text(
            text = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", currentLocale())),
            style = LauncherType.date,
            color = palette.secondary,
        )
        Spacer(Modifier.height(LauncherSpace.xs))
        Text(text = "Today.", style = LauncherType.title, color = palette.primary)

        Spacer(Modifier.height(LauncherSpace.md))
        Hairline()
        Spacer(Modifier.height(LauncherSpace.md))
        TodayDashboard(viewModel, state)

        Spacer(Modifier.height(LauncherSpace.xl))
    }
}

// ---- Launch ------------------------------------------------------------------------

/**
 * The apps page: the ordered list this page holds, text only, starting at the top of the
 * page like every other screen.
 *
 * Reordering is a long press and then a drag, which is why these rows do not open the
 * pin/hide menu on a long press: one gesture cannot both lift a row and open a popup.
 * Unpin stays reachable on the row itself and the menu is untouched in the drawer.
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

    // Top-anchored, like every other page: the list starts directly under the status bar
    // and grows downward. Anchoring it to the bottom pushed the first row into the middle
    // of an otherwise empty page, which read as clipped rather than calm.
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = LauncherSpace.gutter),
    ) {
        Spacer(Modifier.height(LauncherSpace.xl))
        if (state.settings.showCalendarGlance) {
            CalendarGlanceBlock(
                state = state,
                centered = false,
                onClick = { viewModel.openCalendar() },
            )
            Spacer(Modifier.height(LauncherSpace.md))
            Hairline()
            Spacer(Modifier.height(LauncherSpace.md))
        }
        // The page's own name, as every page had before: one quiet label, then the list.
        SectionHeading(page.config.heading ?: page.name)
        Spacer(Modifier.height(LauncherSpace.sm))
        if (targets.isEmpty()) {
            EmptyNote("No apps on this page yet. Open All apps and pin the ones you want.")
            QuietAction("Open All apps", onClick = { viewModel.openApps() })
        } else {
            val lowercase = state.settings.labelCase == LabelCase.LOWERCASE
            targets.forEachIndexed { index, target ->
                if (index > 0) Hairline()
                val usageWhisper = if (state.settings.showUsageOnHome && state.usageAccess) {
                    state.dailyUsage.byPackage[target.packageName]
                        ?.takeIf { it.foregroundMillis >= 60_000L }
                        ?.let { UsageCalculator.formatDuration(it.foregroundMillis) }
                } else {
                    null
                }
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
                        isFavourite = true,
                        onClick = { viewModel.launch(target) },
                        onToggleFavourite = { viewModel.toggleFavourite(target.key) },
                        onToggleHidden = { viewModel.toggleHidden(target.key) },
                        longPressMenu = false,
                        showPinAction = false,
                        showFavouriteDot = false,
                        lowercaseLabel = lowercase,
                        usageWhisper = usageWhisper,
                    )
                }
            }
            Spacer(Modifier.height(LauncherSpace.md))
        }
    }
}

// ---- Note --------------------------------------------------------------------------

/**
 * The note page: one persistent quick note, plus a route to the wellbeing controls, which
 * live in Settings.
 */
@Composable
private fun NotePage(viewModel: LauncherViewModel, state: UiState) {
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
        SectionHeading("Note")
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
 * taking a line of text from the page itself. Not composed at all on a single-page Home,
 * where there is nothing to indicate.
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
 *
 * 24-hour only. The format used to be a setting; it was one more row for a value that
 * never changes after the first week, and a clock that reads the same way everywhere is
 * easier to scan than one that matches a preference.
 */
@Composable
private fun Clock(widthDp: Float) {
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
    Text(
        text = now.format(DateTimeFormatter.ofPattern("HH:mm", locale)),
        style = clockStyle,
        color = palette.primary,
        maxLines = 1,
    )
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
    PageType.PLAN -> "Plan"
    PageType.NOTES -> "Note"
    PageType.WIDGET -> "Usage"
}
