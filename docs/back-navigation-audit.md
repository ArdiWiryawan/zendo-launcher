# Back Navigation Audit

Scope: versionName 0.3.2. Method: grep over `app/src/main` for `BackHandler`
and every navigation-animation API + unit tests. No device run claimed.

## Symptom

Back could appear to trigger a horizontal slide without a meaningful screen
change.

## Evidence: there is no screen-transition animation in the tree

Grep for `AnimatedContent`, `Crossfade`, `NavHost` returns zero hits.
Screen swaps in `LauncherRoot` are instant `when` branches. The only animated
horizontal motion in the product is `HorizontalPager` itself:

- Swipe flings follow the finger (genuine user gesture, not a Back effect).
- Programmatic moves (`pagerTargetPage`) use `animateScrollToPage` only for
  genuine actions: tapping a page dot, "add a page then go to it", and the
  Focus shortcut. `reduceMotion` switches these to `scrollToPage`.
- `onPagerSettled` persists via `settledPage`, never mid-fling `currentPage`,
  so an abandoned fling cannot strand state on the wrong page.

Conclusion: no code path animates on Back. Any slide seen on Back is the
pager settling from a finger already in motion, or a platform edge-back
effect — not a launcher transition. The fix is therefore ownership (Back
must claim nothing when nothing is open), not animation removal.

## Every Back path (priority order, last registered wins)

`LauncherActivity.kt` `LauncherRoot` (13 handlers) + `OnboardingScreen.kt`
(1, always-on but only reachable on ONBOARDING):

| # | Enabled when | Effect |
| --- | --- | --- |
| 1 | `screen == SETTINGS` | `backFromSettings()` (drawer return keeps query) |
| 2 | `screen == APPS` | `goHome()` (clears query, keeps pager page) |
| 3 | `screen == WALLPAPERS \|\| CALENDAR` | `goHome()` |
| 4 | HOME `&& pageEditing` | `leavePageEdit()` |
| 5–6 | HOME `&& showAllHighlights/showAllTasks` | close the list dialog |
| 7–8 | HIGHLIGHT_EDITOR / TASK_EDITOR | `backFrom…Editor()` (dirty → discard confirm) |
| 9–10 | discard confirms | close confirm, draft kept |
| 11–12 | delete confirms | close confirm, record kept |
| 13 | `pendingPause != null` | `cancelPause()` (highest priority) |
| — | ONBOARDING screen | `dismissOnboarding()` (screen-owned) |

Plain Home enables none of them: Back falls through to the system — no slide
(there is nothing to animate), no exit, no duplicate Home entry, no pager
move. `viewModel.back()` mirrors the same order for tests.

Dialogs (`Dialog(onDismissRequest=…)`) all dismiss outward (pause, lists,
confirms, discard); the platform `DatePickerDialog` is system-owned.
External returns (system calendar, app Settings, wallpaper picker) push no
launcher nav entry: `onResume` only refreshes state (`refreshHomeState`),
so returning lands on the same screen with the same page, query, scroll
(drawer index/offset saved on dispose), and editor drafts (view model).

## State preserved across Back

Pager page (`activePageId` persisted per settle), drawer query (kept except
`goHome` which intentionally clears), drawer scroll, editor drafts,
wallpaper selection/message. No form input lives in composable-only state
except dialog-local Quick Add drafts, which are submitted-or-cleared
immediately by design.

## Tests

- `back_leavesEditMode_andPlainHomeClaimsNothing` (`LauncherUiTest`):
  plain-Home `back()` returns false and the activity is not finishing.
- `backFromSettings_returnsToDrawerAndPreservesQuery` (`PauseGateTest`).
- `EditorStateTest.back_*`: clean editors return Home; dirty editors raise
  the discard confirm; a second Back closes the confirm, never the draft.
- Gesture/button Back parity: every `BackHandler` body calls the same VM
  function as its on-screen counterpart (Done/Close/Cancel/Home), so button
  and gesture Back cannot diverge.

## Residual risks (unverified without hardware)

Platform edge-back sensitivity vs the pager under gesture navigation, and
OEM-specific predictive-back animations — both system-owned; the launcher
adds no custom transition that could fight them.
