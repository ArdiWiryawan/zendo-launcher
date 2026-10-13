# UI/UX implementation and before-and-after review

This document records the redesign that followed `docs/ui-ux-audit.md`. It has two parts.
Section 1 is the implementation summary: what changed, in which file, which audit finding
it closes, and what the user sees as a result. Section 2 is the before-and-after review.

Scope of section 1 is exactly the set of changes in commit `22273c1` (the diff against its
parent `2ac913a`). Nothing is described that the diff does not show. Findings are cited by
their id in `docs/ui-ux-audit.md`.

The register of section 2 is deliberately narrower than the spec asked for. The spec asked
for before-and-after screenshots under `docs/ui-ux-review/`. That deliverable was not
achievable and section 2 explains why, then substitutes a textual review.

## 1. Implementation summary

### Design tokens: `ui/theme/Theme.kt`

- **`LauncherSpace` (lines 62-71).** A named spacing scale now exists: `xs` 4dp, `sm` 8dp,
  `md` 16dp, `lg` 24dp, `xl` 40dp, plus `gutter` 24dp, `hairline` 1dp and `touchTarget`
  48dp. Every screen and component reads from it instead of inline literals. Closes P2-2
  across all four screens. Effect: one shared vertical rhythm, and no new gap
  invented per screen.
- **`LauncherType` extended (lines 78-96).** `title` (20sp) is new, for screen headers.
  `section` moved from 11sp to 12sp (line 89). `appLabel` and `body` gained explicit
  `lineHeight` (24sp and 22sp). Closes P2-1. Effect: section labels are legible, and
  text-heavy rows no longer inherit the font default line height.
- **`clockSizeSp(screenWidthDp, fontScale, base)` (lines 106-121).** A pure function that
  shrinks the requested clock size so the widest realistic clock string still fits the
  width available after the gutters, clamped to 28sp at the floor and `base` at the
  ceiling. Closes P0-7 together with the `maxLines = 1` at `ui/HomeScreen.kt:137`. Effect:
  the clock stays one line at a raised system font scale. This is arithmetic from default
  font metrics, not a device measurement.
- **`ColorScheme.inLauncherPalette` (lines 130-158).** Every role Material3 reads for a
  menu is now mapped onto the two-tone palette: `surfaceContainer`, `surfaceContainerHigh`,
  `surfaceContainerHighest`, `onSurfaceVariant`, `surfaceVariant`, `scrim`, `inverseSurface`,
  `surfaceTint` (transparent, or elevated surfaces get tinted) and others. Closes P0-1.
  Effect: the long-press menu renders in the launcher's own tones instead of Material3's
  default violet-tinted container.

### Shared components: `ui/Components.kt`

- **`QuietAction` (lines 93-103).** The clickable is now the outermost node and the box
  carries `defaultMinSize(minHeight = LauncherSpace.touchTarget)`, so the hit area is 48dp
  while the label keeps its quiet size. The `clip(CircleShape)` is gone, which also removes
  the ripple that was clipped to a circle inside a wide row. Closes P0-5 and P2-4. Effect:
  every action meets the platform minimum, and press feedback spans the whole target.
- **`AppRow` (lines 118-197).** `onLongClickLabel` states the gesture for assistive
  technology (line 141). `stateDescription` reports "In Essentials" or "Not in Essentials"
  (lines 144-146). A `defaultMinSize` of 48dp applies to the row (line 147). The long-press
  menu is anchored to the row's trailing edge through `menuAlignment` (line 190), and the
  hide item is renamed "Hide and unpin" so the unpin it also performs is stated (line 205).
  Closes P1-4, P2-5, and the announcement half of P0-4. A trailing `Add` / `Remove`
  affordance was written behind a `showFavouriteToggle` flag and then **deleted**: no screen
  ever passed it, and shipping an unrendered parameter is the dead flexibility the audit
  raised as P2-8. See "Findings still open" for why the visible affordance is not shipped.
  Effect: pinning state is reportable, the menu opens under the finger, and hiding no
  longer silently drops a pinned entry.
- **`ScreenTitle` (lines 76-85).** New. A title level above the rows with a trailing slot
  for the screen's own action, no container. Effect: "All apps" and "Settings" read as
  titles rather than as another row.
- **`ToggleRow` (lines 229-256).** The Material3 `Switch` is gone. The whole row is
  `toggleable` with `role = Role.Switch`, and the state is the word "On" or "Off" whose
  weight and tone both change. Closes P1-9 and P1-12. Effect: the settings screen is text
  throughout, and tapping the label changes the setting. The word is used instead of a
  tick glyph because the default font family does not cover U+2713 and a fallback could
  render tofu.
- **`ChoiceRow` (lines 264-307).** The selected option now differs by a weight change and
  a 20dp hairline marker under the label, as well as by colour, and each option is
  `selectable` with `role = Role.RadioButton` and a 48dp minimum height. Closes P0-5 and
  P0-6. Effect: the active theme and layout are legible without relying on colour, and a
  screen reader can name them.
- **`AppIcon` (lines 204-219).** The icon box is emitted unconditionally at `Modifier.size`
  (line 208) and only the `Image` inside it is conditional on the bitmap. Closes P1-7.
  Effect: a resolving icon can no longer shift the label sideways as the list scrolls.
- **`SecondaryText` (lines 320-327).** The dead `color` parameter is removed; the
  composable reads the palette itself. Closes P2-8. Effect: one way to render secondary
  text.
- **`EmptyNote`.** Now used only for true empty states. The explanatory paragraphs in
  Settings use `SecondaryText`. Closes P2-7. Effect: empty states and help text are
  distinguishable.

### Home: `ui/HomeScreen.kt`

- The Essentials empty state is one sentence, "No pinned apps yet." (line 78). Closes
  P1-8.
- The `Refresh` action is removed from the bottom row; the row is now "All apps" and
  "Settings" (lines 98-107). Closes P1-1 and, with it, P2-6, because the weighted spacer
  that produced the second spacing mechanism is gone and `Arrangement.spacedBy` is the only
  one left.
- The top offset is `LauncherSpace.xl` (40dp, line 60) with a comment stating it is
  deliberate breathing room rather than a measured offset. P2-3 is only partly addressed;
  see "Findings still open".
- The clock takes `clockSizeSp` and `maxLines = 1` (lines 130-137). Closes P0-7 with the
  token change above.
- The Today block returns early when it has neither an action nor an agenda entry
  (line 170), so a payload carrying only `practices` does not produce a heading over
  nothing. Effect: no empty "Today" heading.

### App drawer and search: `ui/AppListScreen.kt`

- `.imePadding()` is added after `.systemBarsPadding()` (line 69), in that order, so the
  keyboard height is added above the bars rather than stacked with them. Closes P0-2.
  Effect: the results and the bottom action row stay reachable with the keyboard open.
  This is the one change in the set that depends on real IME behaviour and could not be
  exercised here.
- The field requests focus on arrival through a `FocusRequester` and `LaunchedEffect`
  (lines 59, 104). Closes the first half of P0-3.
- `KeyboardOptions` sets `capitalization = None`, `autoCorrectEnabled = false` and
  `ImeAction.Search` (lines 87-90), and `KeyboardActions(onSearch)` hides the keyboard
  (line 95). Closes the second half of P0-3. Effect: app names are not rewritten, and the
  IME offers Search.
- A `Clear` action appears only while the query is non-empty and calls
  `viewModel.clearQuery()` (lines 110-112, `ui/LauncherViewModel.kt:228`). Closes the third
  half of P0-3. The field is also given the accessible name "Search apps" (line 108), since
  the "Search" placeholder is a sibling `Text` and carries no semantics of its own. Effect: the query can be reset without leaving the screen.
- The results list draws sticky first-letter headers while the query is empty, derived from
  the same normalisation the sort uses (`letterOf`, lines 138-141 and 169-175), with a
  single `#` bucket for digits and symbols. Closes P1-6. Effect: a user can jump to a
  letter instead of scrolling.
- The per-row hairline is gone (`AppListRow`, lines 176-187). Closes P1-5. Effect: the drawer reads as
  one product with Home.
- The empty state distinguishes loading from empty: `state.isLoading` shows "Looking for
  apps…", a blank query shows "No launchable apps found.", and a query shows "Nothing
  matches …" (lines 123-132). Closes P1-13. Effect: the drawer does not claim there are no
  apps while the first scan is still running.

### Settings: `ui/SettingsScreen.kt`

- Hidden apps are named from a key-to-label map built from `state.allApps` (line 47),
  falling back to `key.substringBefore('/')` only for a key that no longer resolves
  (line 176). Closes P1-3. Effect: the hidden list reads as app names.
- The Pinned order row keeps Up and Down together as a reorder pair and pushes Unpin away
  by `LauncherSpace.xl` to the trailing edge (lines 218-253), and each action names the app
  it acts on through `contentDescription`. With the 48dp `QuietAction`, this closes P1-10.
  Effect: fewer accidental unpins and a row a screen reader can navigate.
- The three toggles use `ToggleRow` and the two selectors use `ChoiceRow`, as above.
- The Today copy changed. The note under "Show today" now reads "With this off, Home shows
  the clock, the date and your pinned apps only." (line 109), and the note under "Connect
  Zendo" states that Zendo exposes no planning API, so connecting it fetches nothing and
  the block stays empty (lines 117-120). This is copy only. P2-10 is not closed; see
  "Findings still open".
- A "Reopen onboarding" action is added under About (line 202). Closes the reachability half
  of P1-2. Effect: the first-run explanation is not a one-time, unrecoverable page.

### Onboarding

- **`ui/OnboardingScreen.kt` (new, 129 lines).** A single scrollable page, not a wizard,
  built from primitives. It states what the launcher replaces, that essentials are chosen
  from the app list, that Android owns the Home app setting, how to restore the previous
  Home screen, and that Zendo is optional. It has its own `BackHandler` (line 39), a "Set as
  Home app" action that opens `Settings.ACTION_HOME_SETTINGS` when the launcher is not
  default, and a "Got it" action that dismisses it (lines 86-103). Closes P1-2.
- **`data/SettingsStore.kt`.** `onboardingSeen` is added to `LauncherSettings` (line 107),
  read with a default of `false` (line 37) and written with the rest of the settings
  (line 50). Closes the persistence half of P1-2.
- **`ui/LauncherViewModel.kt`.** `Screen.ONBOARDING` is a new enum value (line 36), the
  initial screen is `ONBOARDING` unless `onboardingSeen` is set (line 84), and
  `dismissOnboarding()` writes the flag and moves to Home (lines 214-217) while
  `showOnboarding()` reopens it without clearing the flag (line 220). The `stateIn` initial
  value mirrors `_screen` so the first frame does not flash Home before onboarding
  (lines 151-155). Effect: a first-run user lands on the explanation. The existing UI tests
  are unaffected because `resetSettings()` calls `goHome()`, which moves the screen to Home
  regardless of the first-run flag.
- `isLoading` is added to `UiState` (line 55) and to the combined state (line 144), starts
  true (line 94), and is cleared in a `finally` in `enumerate()` (lines 188-197) so a
  platform query that throws cannot leave the flag set. Closes P1-13 with the drawer
  change above.
- `allApps` is added to `UiState` (line 53) as the unfiltered list, which is what the
  Settings hidden-apps labels are built from. Closes P1-3 with the Settings change above.
- `clearQuery()` is added (line 228). Closes the clear half of P0-3.

### Root shell and theming

- **`LauncherActivity.kt`.** `setTheme` selects `Theme_ZendoLauncher_Light` or
  `Theme_ZendoLauncher_Dark` before `super.onCreate()` when the user has chosen an explicit
  theme, and does nothing for `SYSTEM` (lines 47-53). Closes P0-9. `SystemBarIcons()`
  (lines 90-101) derives the status-bar and navigation-bar icon appearance from the
  resolved palette through `WindowCompat.getInsetsController`, keyed on `isDark`. Closes
  P0-8. The `BackHandler` now excludes `ONBOARDING` as well as `HOME` (line 107), so Back
  on onboarding dismisses it rather than dropping into Home unseen.
- **`res/values/themes.xml`.** One style becomes four: `Theme.ZendoLauncher.Base` (4-8),
  `.Light` (11-15) and `.Dark` (18-22), with bare `Theme.ZendoLauncher` inheriting Light
  (line 26). `windowLightStatusBar` and `windowLightNavigationBar` are now set per variant
  instead of hardcoded. Closes P0-9 and the XML half of P0-8.
- **`res/values-night/themes.xml` (new).** Bare `Theme.ZendoLauncher` inherits the Dark
  variant under system night mode, so the manifest default still follows the system.
  Closes the default half of P0-9.
- **`res/values/colors.xml`.** `launcher_window_background` becomes
  `launcher_window_background_light` and `launcher_window_background_dark`, both defined in
  the unqualified file (lines 3-4). **`res/values-night/colors.xml`.** The night-qualified
  window background is deleted; only `ic_launcher_background` remains. Closes P0-9.

### Tests: `app/src/test/java/com/zendolauncher/ui/LauncherUiTest.kt`

- `awaitEnumeration()` waits for `isLoading` to clear before asserting the drawer's
  definitive empty state, and the drawer test now asserts the header "All apps" as well.
- `homeScreen_rendersRefreshOnlyInIconTextLayout` is replaced by
  `homeScreen_hasNoRefreshActionInAnyLayout`, which asserts zero `Refresh` nodes in both
  layouts. This is the P1-1 regression guard the audit said had to be inverted.
- `search_clearAction_emptiesQuery_andStaysOnAppList` and
  `search_clearAction_absentWhileQueryIsEmpty` cover the clear action.
- `appList_letterBuckets_followTheSortsNormalisation` covers `letterOf`, including an
  accented name and the `#` bucket.
- `appList_whileEnumerationInFlight_showsProgressNotTheEmptyState` renders the loading
  state directly, because the real window is too short to race.
- `appList_loadingFlag_clearsAfterEnumeration` guards the stuck-flag failure.

### Docs

- `README.md` is updated: the Today block is described as present but empty, the search and
  header behaviour is documented, the hidden-apps and theme behaviour is corrected, and a
  "First run" section is added. `docs/ui-ux-audit.md` is added to the document index.
- `docs/design-reference-audit.md` is rewritten for accuracy, with an explicit statement
  that no source's rendered UI was retrievable and that the file records principles only.
- `docs/ui-ux-audit.md` gains P0-9, the cold-start window background finding.

### Findings still open, and one correction

- **P2-10.** The "Show today" toggle still has no observable effect. The wiring is correct
  and the block still requires a Zendo payload that no shipped source produces
  (`ui/LauncherViewModel.kt:82`, `UnavailableZendoSource`). The implementation changed only
  the copy (lines 104 and 116-120), which is what the audit's recommendation asked for, but
  the toggle itself remains without a visible result until a source ships.
- **P0-4.** The announcement half is done (`onLongClickLabel` and `stateDescription` on the
  row, so TalkBack states the favourite and names the gesture), and the onboarding page now
  teaches the gesture in words. The visible per-row affordance the audit asked for was
  **deliberately not shipped**. An "Add"/"Remove" action on every row of a list that can run
  to a hundred entries is the visual noise the design direction rules out, and the audit's
  own P1-5 recommendation was to take texture *out* of this list. The parameter that would
  have rendered it (`showFavouriteToggle`) was therefore deleted rather than left as dead
  flexibility, which is the same defect the audit raised as P2-8 against `SecondaryText`.
  The gesture remains the only way to build Essentials; this is an accepted, recorded gap.
- **P0-3, closed.** Focus on arrival, keyboard options, the Search action, the Clear action
  and the accessible name all landed. The field's accessible name is set through
  `semantics { contentDescription = "Search apps" }` (`ui/AppListScreen.kt:100-105`), because
  the "Search" placeholder is a sibling `Text` in the `decorationBox` and is not part of the
  field's own semantics.
- **P1-11, partly.** The menu item is renamed "Hide and unpin" so the unpin is stated, but
  there is still no confirmation and no undo. A mis-tap still costs a trip to Settings.
- **P2-3, not closed.** The Home top offset is now a named constant (`LauncherSpace.xl`,
  40dp) rather than a bare 56dp literal, but it is still a fixed value, not one derived
  from the available height or the inset. The audit's recommendation is unaddressed.
- **Correction: P2-2 is closed, not open.** All four screens now use `LauncherSpace`.
  `ui/OnboardingScreen.kt` was the last holdout: it carried inline `24.dp`, `56.dp`, `28.dp`,
  `20.dp`, `36.dp`, `40.dp` and `18.dp` literals and its own `TextAction` justified by a
  comment claiming `QuietAction` was under the minimum touch target, which the P0-5 change
  had made false. Both were corrected: the spacing is tokenised and the helper's comment now
  states its real reason (it keeps the label on the row's leading edge, where `QuietAction`'s
  centring `Box` would float it into the middle of the weight it is given).

## 2. Before-and-after review

### Deliverable status: screenshots not produced

The spec asked for `docs/ui-ux-review/` containing before-and-after screenshots. That
deliverable was not achievable and the directory was not created.

A Robolectric-based screenshot harness was written and run. It captured 720x1600 PNGs.
Decoding the captures showed they contained zero non-background pixels: Robolectric's
native graphics mode rasterised the Compose layout (backgrounds and hairlines) but drew no
text glyphs at all. Three of the captures were byte-identical, with the same SHA-256. For a
text-first launcher these images carry no information about the interface, and the
harness's own non-blank guard could not detect the failure, because a solid background
satisfies a check for non-background pixels. The harness and every image were deleted
rather than shipped as misleading evidence. `app/src/test/java/com/zendolauncher/ui/ScreenshotTest.kt`
no longer exists.

What follows is the textual substitute. It states, per screen, the behaviour recorded in
the audit and the behaviour the source now implements. It does not describe visual
appearance that was not seen.

### Home

Before: the clock was fixed at 60sp in a `Row` with no `maxLines`, so it clipped at a
raised font scale (P0-7). The Essentials empty state was two sentences and taught a
long-press (P1-8). The bottom row was "All apps", "Settings" and, only in the icon layout, a
"Refresh" action, with a weighted spacer mixed into `spacedBy(28.dp)` (P1-1, P2-6). The top
offset was a bare 56dp spacer (P2-3).

After: the clock is sized by `clockSizeSp(configuration.screenWidthDp, configuration.fontScale)`
and drawn with `maxLines = 1` (`ui/HomeScreen.kt:130-137`). The empty state is "No pinned
apps yet." (line 78). The bottom row is "All apps" and "Settings", spaced only by
`Arrangement.spacedBy(LauncherSpace.lg)` (lines 98-107). The top offset is
`LauncherSpace.xl` (line 60). The Today block, when enabled and given a payload, still
renders under its own heading and now returns early when it has nothing to show (line 170).

### App drawer and search

Before: no IME inset, so the keyboard covered the results and the bottom row (P0-2). The
field was never focused, had no `KeyboardOptions`, no Search action and no clear affordance
(P0-3). The list was one flat `LazyColumn` with a hairline under every row (P1-5, P1-6). An
empty list always read "No launchable apps found." (P1-13).

After: the column consumes `.systemBarsPadding()` then `.imePadding()`
(`ui/AppListScreen.kt:70-71`). The field takes focus on arrival (lines 59, 104), disables
autocorrect and auto-capitalisation, requests `ImeAction.Search` and hides the keyboard on
submit (lines 87-95). A "Clear" action appears while the query is non-empty (lines 110-112).
The list is grouped under sticky letter headers when the query is blank, with a `#` bucket
for digits and symbols (lines 138-141, 169-175), and draws no per-row hairline.
The empty state is "Looking for apps…" while the first scan runs, "No launchable apps
found." when it has finished, and "Nothing matches …" for a query (lines 123-132).

### Settings

Before: hidden apps were listed by package name (P1-3). The toggles were Material3 pill
switches whose labels were not tappable (P1-9, P1-12). The Theme and Layout options differed
by colour alone (P0-6). Up, Down and Unpin sat 16dp apart at roughly 37dp tall (P1-10).
There was no way back to the first-run explanation (P1-2).

After: the hidden list is named from a key-to-label map with a package-name fallback for an
uninstalled app (`ui/SettingsScreen.kt:47`, 176). Toggles are text rows reading "On" or
"Off", toggled by the whole row (`ui/Components.kt:245-272`). The selected Theme or Layout
option carries a weight change and a hairline marker, and is `selectable` as a radio button
(`ui/Components.kt:280-323`). The pinned-order row keeps Up and Down together and pushes
Unpin to the trailing edge behind a `LauncherSpace.xl` gap, each action naming its app
(`ui/SettingsScreen.kt:218-253`). About carries "Reopen onboarding" (line 202).

### Onboarding

Before: there was no onboarding screen and no first-run state at all. `Screen` had three
values, none of them a first-run state (P1-2). The only statement of what the launcher
replaces and how to restore the previous Home screen lived in the README and in a paragraph
inside Settings that a first-run user had no reason to open.

After: a first run lands on `OnboardingScreen` because `_screen` starts at
`Screen.ONBOARDING` unless `onboardingSeen` is set (`ui/LauncherViewModel.kt:84`). The page
states what the launcher replaces, that essentials come from the app list, that Android
owns the Home app setting, how to restore the previous Home screen, and that Zendo is
optional. It offers "Set as Home app" when the launcher is not default and "Got it" to
dismiss, and Back dismisses it (`ui/OnboardingScreen.kt:39`, 83-101). It is reachable again
from Settings.

### Cold-start frame

Before: the window background came from `@color/launcher_window_background`, overridden only
under `values-night`, so it followed the system night mode while the launcher's own theme
followed the user's stored choice. A user on a light system who selected Dark, or the
reverse, saw a full-screen flash of the wrong colour before the first Compose frame. Because
`android:clearTaskOnLaunch="true"` destroys the task on every return to Home
(`AndroidManifest.xml:31`), the window was recreated constantly (P0-9).

After: `LauncherActivity` calls `setTheme` with `Theme_ZendoLauncher_Light` or
`Theme_ZendoLauncher_Dark` before `super.onCreate()` when the user has chosen one
(`LauncherActivity.kt:47-53`), and leaves `SYSTEM` on the night-qualified bare theme
(`res/values-night/themes.xml:3`). The window background and the two icon-appearance flags
now come from the same choice, and a post-Compose `SideEffect` keeps the icon appearance in
step with the resolved palette (`LauncherActivity.kt:90-101`).

### Limitations

This review was not produced on a device. There was no device and no emulator. There is no
real font scale, no real IME, no TalkBack session, and no real `LauncherApps` app list
behind any statement above: every test runs against Robolectric, which returns no installed
apps and no shortcuts. There is no visual confirmation of the result. The before-and-after
comparison is a comparison of source and of recorded findings, not of rendered screens, and
the screenshot harness described above produced nothing that could change that.
