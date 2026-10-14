# Interaction Behavior

Scope: versionName 0.3.0. Single-activity Compose root (`LauncherActivity` +
`ui/LauncherViewModel.kt` `Screen`: ONBOARDING / HOME / APPS / SETTINGS /
WALLPAPERS / CALENDAR). No animation library; swaps are instant except pager
flings and one-shot `animateScrollToPage`.

## Back

`LauncherActivity.kt` `LauncherRoot` registers one `BackHandler` per layer,
most-specific last (last registered wins). Plain Home registers nothing, so
Back falls to the system: no meaningless slide, no exit, no pager move.

Order (first listed = lowest priority):

1. `screen == SETTINGS` → `backFromSettings()` (returns to drawer when
   entered from it, preserving query via `settingsReturn`; else HOME).
2. `screen == APPS` → `goHome()` (clears query, keeps pager page: only
   `activePageId` moves the pager, and nothing here touches it).
3. `screen == WALLPAPERS || CALENDAR` → `goHome()`.
4. `screen == HOME && pageEditing` → `leavePageEdit()`.
5. `showAllHighlights` → close. `showAllTasks` → close.
   `showHighlightEditor` → dismiss. (Overlays above whichever screen opened them.)
6. `pendingPause != null` → `cancelPause()` (highest priority: the dialog
   always wins over the screen beneath it).

`ONBOARDING` owns its handler in `OnboardingScreen` (dismiss). `viewModel.back()`
keeps the same order for tests (`back_leavesEditMode_andPlainHomeClaimsNothing`
asserts false on plain Home, true leaving edit mode).

Predictive back: manifest `enableOnBackInvokedCallback="true"`; no custom
transition, so nothing conflicts with edge-back.

## Swipe and scroll ownership

| Gesture | Region | Owner |
| --- | --- | --- |
| Horizontal drag, centre band | pager viewport | `HorizontalPager` |
| Horizontal drag from screen edge | system edge zone | System Back (pager makes no exclusion claim; inset gutter keeps it unambiguous) |
| Vertical drag in page content | scrolling column | page `verticalScroll` (Compose dispatches by axis; no custom NestedScrollConnection) |
| Tap app row | row bounds | `AppRow` combinedClickable |
| Long-press app row | row bounds | drawer menu; on Launch page disabled (`longPressMenu=false`) so long-press+drag reorders instead |
| Long-press then drag (Launch) | row | `detectDragGesturesAfterLongPress`, one slot per row height, written per slot via `moveItemBy` (key-based, index re-looked-up) |
| Swipe up | Home bottom bar only | drawer open (`openApps()`), threshold 120px past slop; taps still click. Disabled by setting |
| Swipe down | Drawer header only | `goHome()` to the same Home page, same threshold; list keeps its gesture |
| Swipe on scrollable widget/calendar list | list bounds | the list; chrome swipes never fire there |

Rules enforced in code:

- `userScrollEnabled = pages.size > 1 && settings.pagingEnabled`. Edit mode
  replaces the pager with `PageManager`, so reorder drags can never page.
- `settledPage` (not `currentPage`) persists the active page; programmatic
  moves use one-shot `pagerTargetPage` cleared by the settle effect.
- `reduceMotion`: programmatic moves use `scrollToPage` instead of
  `animateScrollToPage`. Flings still follow the finger; nothing else
  animates.
- Rapid swipes: each settle writes one id; out-of-range targets ignored.
- No overscroll/bounce/parallax added. `systemBarsPadding` (+ `imePadding` in
  the drawer, bars-before-IME order) and the clock `topGap`
  `coerceIn(lg, xl)` keep content reachable on short screens and behind
  cutouts without custom bar handling.

## Home paging

`HorizontalPager` + `rememberPagerState(initialPage = activeIndex)`,
`key = page.id` so reorder never leaks per-page state. Indicator dots are
8dp selected / 6dp unselected, primary vs separator, each with
"Name, page i of n" + "Showing/Not showing" semantics; tap calls `goToPage`.
Closing any overlay/dialog preserves the page (no navigation touches
`activePageId`).

## App drawer

Opens via All apps button or bottom-bar swipe-up. Search autofocuses;
`imePadding` keeps the bottom row above the keyboard. Scroll position is
saved on dispose (`drawerIndex/Offset`) and restored on re-entry, including
after a Settings detour. `goHome` clears the query; in-drawer Clear does not
leave the screen. Swipe-down on the header returns Home.

## Settings for gestures

Settings → Gestures: "Horizontal swipe pages Home" (`pagingEnabled`, default
on), "Swipe up on bottom bar opens drawer" (`swipeUpEnabled`, default on),
"Reduce launcher transitions" (`reduceMotion`, default off). Small fixed set;
no gesture-mapping system. All in `SettingsStore` (`launcher_settings`
prefs) alongside `shownCalendarIds`.

## Known device-specific differences (unverified without hardware)

- Edge-back vs pager under gesture navigation (OEM edge widths differ).
- `FLAG_LOCK` honored or ignored per OEM/Android version.
- Calendar provider columns on some OEM ROMs; query is guarded and falls
  back to empty rather than crashing.
- Font-scale extremes: clock fits via `clockSizeSp` arithmetic; week-strip
  44dp columns may tighten on very narrow screens but remain tappable via
  the 36dp circle + vertical padding.
