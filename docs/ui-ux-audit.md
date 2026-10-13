# UI/UX audit

This audit covers the shipped Compose UI of Zendo Launcher: three screens (Home, app list
plus search, Settings), the shared components they are built from, the type and colour
tokens, and the root shell that hosts them. Everything below was read out of the source in
this repository and cross-checked against the 28 existing JVM tests, 13 of which execute the
real Compose UI through Robolectric. The UI has never been rendered on a physical device or
an emulator: there is no screenshot of real hardware behind any finding here, and no
observation of a real touch, a real keyboard, a real font scale, or a real screen reader. The
findings are source inspection plus JVM Compose execution, and where a claim depends on
device behaviour it is labelled as reasoning from platform documentation rather than
observation.

## How this was audited

- Direct source inspection of the 12 production files named in the brief, plus
  `platform/AppLauncher.kt` (the launch path referenced by the persistence and PWA
  findings), `app/build.gradle` (SDK levels), and the three test files.
- Read in full: `ui/HomeScreen.kt` (179 lines), `ui/AppListScreen.kt` (96),
  `ui/SettingsScreen.kt` (252), `ui/Components.kt` (177), `ui/theme/Theme.kt` (106),
  `LauncherActivity.kt` (75), `ui/LauncherViewModel.kt` (196), `data/SettingsStore.kt` (104),
  `platform/ShortcutRepository.kt` (89), `platform/AppRepository.kt` (99), `core/AppQuery.kt`
  (66), `AndroidManifest.xml`.
- The 28 existing JVM tests: 9 in `core/AppQueryTest.kt`, 6 in `core/TodaySelectorTest.kt`,
  13 in `ui/LauncherUiTest.kt`. The last of these is not a mock: it drives the shipped
  `LauncherActivity` and the view model the activity creates, through
  `createAndroidComposeRule<LauncherActivity>()` under Robolectric at `sdk = [36]`
  (`ui/LauncherUiTest.kt:40-45`). It is the only mechanical evidence available that these
  screens compose and that text is present in the semantics tree.
- Measured values (line heights, dp arithmetic, glyph widths) are computed from default font
  metrics and stated as estimates. They were not measured on a device.
- What could not be verified: real-device rendering, real font-scale behaviour, real IME
  behaviour, the real `LauncherApps` app list, real PWA discovery, and TalkBack. See the
  closing section.

## Screen inventory

| Screen | File | Lines | What it does | What it is missing |
| --- | --- | --- | --- | --- |
| Home | `ui/HomeScreen.kt` | 179 | Clock, date, optional Today block, Essentials list, bottom action row | Onboarding; a visible way to add an app to Essentials; a stable top offset; any inset handling beyond system bars |
| App list and search | `ui/AppListScreen.kt` | 96 | Search field, result count, alphabetical app list, Settings and Refresh actions | IME insets; field focus; keyboard action; clear-query affordance; alphabet structure; a loading state distinct from "no apps" |
| Settings | `ui/SettingsScreen.kt` | 252 | Theme, layout, 24-hour clock, Today toggle, Zendo toggle, default-Home status, pinned order, hidden apps, privacy, about | Row-level hit targets; a text marker for the selected option; app labels in the hidden list; a scroll affordance |
| Shared components | `ui/Components.kt` | 177 | `Hairline`, `SectionHeading`, `QuietAction`, `AppRow`, `AppIcon`, `EmptyNote`, `SecondaryText` | Any component carrying a 48dp target; an icon placeholder; a semantics role for favourite state |
| Tokens | `ui/theme/Theme.kt` | 106 | Two palettes, seven text styles, the Material scheme bridge | A spacing scale; a complete Material scheme (only 7 of ~30 roles are set) |
| Root shell | `LauncherActivity.kt` | 75 | Edge-to-edge host, theme binding, Back handling, screen switch | System-bar style bound to the in-app theme |

There is no onboarding screen, no alphabetical index, no first-run state, and no per-screen
scroll affordance. `Screen` is a three-value enum (`HOME, APPS, SETTINGS`) at
`ui/LauncherViewModel.kt:36`.

## P0 — essential

### P0-1. The only popup in the app is off-palette (Material default surface)

- **Screen / component:** `AppRow` long-press menu, shared by Home and the app list.
- **Observed problem:** `DropdownMenu` reads `MenuDefaults.containerColor`, which resolves to
  `colorScheme.surfaceContainer`, and `DropdownMenuItem` reads `colorScheme.onSurfaceVariant`
  for its default content colour. Neither role is set by the theme, so the menu renders in
  Material3's default tonal container (a grey, slightly violet-tinted surface) with
  default-variant text, on a launcher whose entire identity is two monochrome tones.
- **Evidence:** `ui/Components.kt:123` renders `DropdownMenu(expanded = menuOpen, ...)`.
  `ui/theme/Theme.kt:82-102` sets exactly seven roles on each branch:
  `background, surface, onBackground, onSurface, primary, onPrimary, outline`. There is no
  `surfaceContainer`, `surfaceContainerHigh`, `surfaceContainerHighest`, `onSurfaceVariant`,
  `surfaceVariant`, `scrim`, `inverseSurface`, `tertiary`, or `error` anywhere in the file.
- **Why it creates friction:** the one menu a user opens while customising the launcher is
  the one element that does not look like the product. It is also the only place the app
  shows a tonal surface, so the exception is conspicuous rather than subtle.
- **Recommended improvement:** set the missing roles in both `darkColorScheme` and
  `lightColorScheme` from the existing palette values (`surfaceContainer` and
  `surfaceContainerHigh` to the background or separator tone, `onSurfaceVariant` to
  `secondary`), or pass explicit `containerColor` and `textColor` to `DropdownMenu` and
  `DropdownMenuItem`. No new colour is needed.
- **Expected user benefit:** the customisation menu reads as part of the launcher instead of
  as a stock Android widget.
- **Regression risk:** low. The change is additive to the scheme. It does not touch
  `LauncherColors`, and the 13 UI tests address menu items only through the two pinned
  screens, not through the menu, so no test depends on the current container colour.
- **Correction to the brief:** the brief also states this "affects `Switch` internals". It
  does not, in the shipped code: `ToggleRow` passes an explicit `SwitchDefaults.colors(...)`
  for checked and unchecked thumb, track, and border
  (`ui/SettingsScreen.kt:216-223`). Only the disabled-state colours and the switch icon are
  left to the scheme. The real exposure is the `DropdownMenu` and any Material component
  added later.

### P0-2. Missing IME inset handling: the keyboard covers the drawer

- **Screen / component:** app list and search.
- **Observed problem:** the activity is edge-to-edge and the manifest asks the window to
  resize, but at `targetSdk 36` the window does not resize for the IME, so the inset has to
  be consumed in Compose. It is not. The search results list and the bottom action row are
  laid out behind the keyboard whenever the field is focused.
- **Evidence:** `AndroidManifest.xml:38` sets `android:windowSoftInputMode="adjustResize"`.
  `LauncherActivity.kt:33` calls `enableEdgeToEdge()`. `app/build.gradle:21` sets
  `targetSdk 36` and `:16` sets `compileSdk 36`. `ui/AppListScreen.kt:29-35` applies
  `.fillMaxSize().background(...).systemBarsPadding().padding(horizontal = 24.dp)` and no
  `.imePadding()`. The bottom row at `ui/AppListScreen.kt:87-94` is the last child of that
  column, and the results `LazyColumn` at `:70` takes `Modifier.weight(1f)` of the same
  column, so both extend to the window bottom.
- **Why it creates friction:** the two controls a user needs while searching (clear or
  leave, and refresh) and the lower half of the results are unreachable without dismissing
  the keyboard first. On a short screen with a tall keyboard this is most of the list.
- **Recommended improvement:** add `.imePadding()` to the app list column, or set
  `Modifier.consumeWindowInsets` plus `imePadding()` on the column that holds the results
  and the bottom row. Do not change the manifest.
- **Expected user benefit:** results stay visible and the bottom row stays tappable with the
  keyboard open, which is the normal state while searching.
- **Regression risk:** low, but this is the finding least verifiable here. It depends on
  IME behaviour that Robolectric does not simulate. The 13 UI tests type by setting the
  query on the view model, not through the IME, so they will not catch a wrong inset
  choice. Verify on a device with a tall keyboard and a small screen.

### P0-3. Search requires a manual tap every time and cannot be submitted

- **Screen / component:** the search field on the app list.
- **Observed problem:** the `BasicTextField` is missing four things at once. It is never
  focused when the drawer opens, so the user taps before typing. It has no
  `KeyboardOptions`, so autocorrect and auto-capitalisation stay on and can mangle app
  names. It has no `keyboardActions`, so the IME shows a generic action rather than Search.
  It has no clear affordance, so the only way to reset the query is to leave the screen. It
  also has no accessible name: the placeholder "Search" is a separate `Text` node, so the
  field itself is announced without a label.
- **Evidence:** `ui/AppListScreen.kt:38-51` is the whole field. There is no
  `focusRequester`, no `LaunchedEffect { requestFocus() }`, no `keyboardOptions`, no
  `keyboardActions`, and no trailing clear control. The placeholder is the
  `decorationBox`'s `Text("Search", ...)` at `:46`, a sibling of the field's own semantics
  node. `ui/LauncherViewModel.kt:169-172` shows `goHome()` is the only place `_query` is
  cleared.
- **Why it creates friction:** search is the fastest path to an app in a text-first
  launcher and it is currently the slowest: tap the field, fight autocorrect, then leave the
  screen to clear it.
- **Recommended improvement:** request focus on first composition of the screen, set
  `KeyboardOptions(capitalization = None, autoCorrectEnabled = false)`,
  `KeyboardActions(onSearch = { /* dismiss the IME */ })` with `ImeAction.Search`, add a
  clear action that calls `viewModel.setQuery("")`, and give the field a label through
  `Modifier.semantics { contentDescription = "Search apps" }` or a visible label. All of
  this is Compose foundation and Material3 already on the classpath.
- **Expected user benefit:** open the drawer and type. Fewer taps, no corrupted queries, a
  one-tap reset, and a field a screen reader can name.
- **Regression risk:** medium. Autofocus changes what the UI tests see on entry to the
  screen, and `ui/LauncherUiTest.kt:96-110` asserts the placeholder "Search" is displayed on
  arrival; a visible label would change that string. Update those assertions in the same
  change.

### P0-4. The primary customisation action is hidden behind an undocumented long-press

- **Screen / component:** `AppRow`, shared by Home and the app list.
- **Observed problem:** adding an app to Essentials is reachable only by long-pressing its
  row. There is no visible affordance on the row, and no `onLongClickLabel`, so assistive
  technology announces nothing about the gesture. A user who never long-presses can never
  build a Home screen.
- **Evidence:** `ui/Components.kt:96-100` attaches
  `combinedClickable(onClick = onClick, onLongClick = { menuOpen = true }, role = Role.Button)`
  with no `onLongClickLabel`. The row renders only the label text and, when already pinned, a
  6dp dot (`:107-120`). The app's own empty state admits the problem:
  `ui/HomeScreen.kt:73` reads "Nothing pinned yet. Open All apps and long-press an app to add
  it here."
- **Why it creates friction:** the single action that makes the launcher personal is
  invisible, and the empty state has to teach a gesture instead of offering it.
- **Recommended improvement:** add a visible, small, text-only affordance to each row in the
  app list (for example a trailing "Pin" action that becomes "Unpin" when pinned), keep the
  long-press as a shortcut for people who know it, and set `onLongClickLabel` so the gesture
  is announced. The `AppRow` signature already receives `isFavourite` and
  `onToggleFavourite`, so this needs no new state.
- **Expected user benefit:** Essentials becomes discoverable without instruction, and the
  Home empty state can shrink to one sentence.
- **Regression risk:** medium. Adding a trailing control changes the row's semantics tree
  and its measured width; `ui/LauncherUiTest.kt:222-227` counts "Refresh" nodes and
  `:88-94` asserts bottom-row labels, neither of which touches the row body, but a new
  "Pin" text node could collide with `onNodeWithText` lookups in future tests. Keep the
  affordance text distinct from any existing label.

### P0-5. Touch targets below the 48dp minimum

- **Screen / component:** `QuietAction` (every navigation and management action) and the
  `ChoiceRow` options (Theme and Layout).
- **Observed problem:** two of the three interactive control types in the app are shorter
  than the 48dp minimum touch target. `QuietAction` is the app's only navigation control,
  so this affects every screen change and every pinned-order action. The Theme and Layout
  options are the only way to change either setting.
- **Evidence:** `ui/Components.kt:61-71` renders `QuietAction` as a `Text` styled
  `LauncherType.action` with `.padding(vertical = 10.dp, horizontal = 2.dp)`. That style is
  14.sp (`ui/theme/Theme.kt:72`) with no explicit `lineHeight`, so the text line is roughly
  17dp by default metrics and the total is roughly 37dp. `ui/SettingsScreen.kt:241-248`
  renders each option as a `Text` in the same 14.sp style with `.clickable { }.padding(vertical = 6.dp)`,
  roughly 29dp tall.
- **Why it creates friction:** 37dp and 29dp are below the 48dp minimum, so these are
  mis-tap targets, and the mis-taps land on destructive actions: "Unpin" and "Unhide" sit
  in the same row pattern as their neighbours.
- **Recommended improvement:** give `QuietAction` a `Modifier.defaultMinSize(minHeight = 48.dp)`
  with `wrapContentHeight(Alignment.CenterVertically)` so the visual density is unchanged
  and only the hit area grows, and give the `ChoiceRow` option the same treatment. Do not
  increase the visible padding: the brief's design direction keeps the text quiet.
- **Expected user benefit:** every action in the app meets the platform minimum without any
  change in visual weight.
- **Regression risk:** low. Taller rows shift the Settings scroll positions, and
  `ui/LauncherUiTest.kt:230-236` uses `performScrollTo()` before asserting, so it tolerates
  the shift.

### P0-6. Selected state is communicated by colour alone

- **Screen / component:** `ChoiceRow`, used for the Theme and Layout selectors.
- **Observed problem:** the selected option differs from the others only in text colour.
  There is no marker, no weight change, and no shape change. A user who cannot distinguish
  the two tones cannot tell which theme or layout is active.
- **Evidence:** `ui/SettingsScreen.kt:244` sets
  `color = if (value == selected) palette.primary else palette.secondary`. The two tones are
  `#F2F2F0` against `#8A8A88` in dark mode and `#111112` against `#6E6E6C` in light
  (`ui/theme/Theme.kt:32-46`). No other property varies between the options, and
  `ui/SettingsScreen.kt:239-249` shows the whole option rendering path.
- **Why it creates friction:** it fails the "do not rely on colour alone" requirement, and
  in light mode the two greys are close enough that the state is weak even for a user with
  full colour vision.
- **Recommended improvement:** keep the colour change and add one non-colour signal. The
  cheapest text-first option is a weight change on the selected option plus a leading
  marker, for example a "·" prefix or a `FontWeight.Medium` change, and expose the selection
  to assistive technology with `Modifier.selectable(selected = ..., role = Role.RadioButton)`
  instead of `clickable`.
- **Expected user benefit:** the active theme and layout are unambiguous at a glance and
  reportable by a screen reader.
- **Regression risk:** low for the visual change, medium for the semantics change.
  `ui/LauncherUiTest.kt:184-208` clicks the option by its text ("Dark", "Light",
  "Icon + text"); `selectable` keeps the text node, so those lookups survive. A "·" prefix
  would not, so prefer the weight change or a separate marker node.

### P0-7. The clock overflows at large system font scale

- **Screen / component:** `Clock` on Home.
- **Observed problem:** the clock is fixed at 60.sp with no autosizing and no `maxLines`,
  and it sits in a row with the AM/PM suffix. At a large system font scale the string is
  wider than the text column and the row clips instead of shrinking.
- **Evidence:** `ui/theme/Theme.kt:61-66` defines `clock` as `fontSize = 60.sp`,
  `fontWeight = FontWeight.Light`, `letterSpacing = (-1.5).sp`, with no `lineHeight` and no
  autosize configuration. `ui/HomeScreen.kt:124-125` renders it as
  `Row(verticalAlignment = Alignment.Bottom) { Text(text = text, style = LauncherType.clock, ...) }`
  with no `maxLines` and no `overflow` argument, and the AM/PM suffix follows at `:126-133`.
  The text column is the full width minus 24dp gutters (`ui/HomeScreen.kt:50`), so about
  312dp on a 360dp-wide screen.
- **Why it creates friction:** the clock is the anchor of the Home screen. When it clips, the
  first thing a user sees is a broken number.
- **Recommended improvement:** bound the clock. Either wrap it in `Text` with
  `autoSize = TextAutoSize.StepBased(...)` where the Compose version allows it, or apply
  `Modifier.fillMaxWidth()` with `softWrap = false` and an explicit `TextOverflow.Visible`
  guarded by a `maxLines = 1`, and reduce the base size from a measured value rather than a
  fixed one. A simpler fallback that needs no new API: derive the size from
  `LocalDensity.current.fontScale` and cap the effective size.
- **Expected user benefit:** the clock stays one line and legible at every system font
  setting.
- **Regression risk:** medium. `ui/LauncherUiTest.kt:83` and `:180` assert exactly one node
  matching a `\d{1,2}:\d{2}` pattern; splitting the clock into more than one text node, or
  changing the format, breaks them. Keep it a single text node.
- **Estimate, not measurement:** at `fontScale 2.0` the clock line is roughly 144dp tall by
  default metrics, and the 12-hour string with its suffix exceeds the 312dp available, so it
  clips. This arithmetic comes from default font metrics. It was not measured on a device,
  and it is listed again under "Not verifiable in this environment".

### P0-8. Status-bar icon contrast ignores the in-app theme

- **Screen / component:** the root shell.
- **Observed problem:** edge-to-edge is enabled with no arguments, so the system-bar icon
  colour is chosen from the system dark-mode setting. The launcher's own theme comes from the
  user's stored preference. When the two disagree, the status-bar icons are drawn in the
  wrong contrast against the launcher's background.
- **Evidence:** `LauncherActivity.kt:33` calls `enableEdgeToEdge()` with no
  `statusBarStyle` or `navigationBarStyle`. The in-app theme is applied from stored state at
  `LauncherActivity.kt:37`: `LauncherTheme(mode = state.settings.theme)`. The stored value is
  a three-way choice, `ThemeMode.SYSTEM, LIGHT, DARK` (`data/SettingsStore.kt:10`), and it
  can be set to a value that disagrees with the system, through `SettingsScreen.kt:62-71`.
- **Why it creates friction:** a user on a dark system who selects the Light theme gets light
  status-bar icons on a near-white background: an invisible clock, battery indicator, and
  notification icons. This is the failure mode most likely to be reported as "the launcher is
  broken", and it is reachable from the Settings screen in two taps.
- **Recommended improvement:** pass explicit styles to `enableEdgeToEdge`, or re-apply them
  from a `SideEffect` keyed on `state.settings.theme`. `SystemBarStyle.auto(lightScrim, darkScrim, detectDarkMode = { ... })`
  accepts a detection lambda, so the palette's own `isDark` flag
  (`ui/theme/Theme.kt:29`) is the correct input rather than `isSystemInDarkTheme()`.
- **Expected user benefit:** status-bar icons are always legible, whichever theme is chosen.
- **Regression risk:** low. `LauncherTheme` already computes the resolved palette, so the
  resolved `isDark` value is available at the call site; no new state is needed.
- **Reasoned from API contract:** the default detection behaviour of
  `SystemBarStyle.auto` is taken from the androidx.activity documentation, not observed on a
  device. See "Not verifiable in this environment".

### P0-9. The cold-start window background follows the system night mode, not the app theme

- **Screen / component:** the root shell, before the first Compose frame.
- **Observed problem:** the window background is painted by the window manager from the
  manifest theme, and that theme was resolved through the `values-night` resource
  qualifier. That qualifier follows the **system** night mode, while the launcher's theme is
  app-controlled: the user picks Light, Dark, or System in Settings, independently of the
  system setting. When the two disagree the first frame of every cold start is the wrong
  colour.
- **Evidence:** before this change, `app/src/main/res/values/themes.xml` declared a single
  `Theme.ZendoLauncher` whose `android:windowBackground` was
  `@color/launcher_window_background`, and the only override of that colour was in
  `app/src/main/res/values-night/colors.xml` (`launcher_window_background` to `#FF0B0B0C`),
  so the window colour followed the system. The style is applied from the manifest at
  `AndroidManifest.xml:26` and `:37`. `AndroidManifest.xml:31` sets
  `android:clearTaskOnLaunch="true"`, which destroys the task on every return to Home, so
  the window is recreated constantly and the flash is the common path rather than a rare
  one. The theme the user picks is a three-way choice at `data/SettingsStore.kt:10`
  (`ThemeMode.SYSTEM, LIGHT, DARK`), and the Compose theme reads it at
  `ui/theme/Theme.kt:161-166`, which is far too late to affect a window that has already
  been painted.
- **Why it creates friction:** a full-screen flash of the wrong colour on every cold start
  is the first thing the user sees, and it is the most visible possible contradiction of a
  launcher whose premise is calm. It is reachable in two taps from Settings and there is no
  setting that turns it off once chosen.
- **Recommended improvement, now in the tree:** split the window theme per variant and
  select it before the window exists. `values/themes.xml` defines `Theme.ZendoLauncher.Base`
  (transparent system bars, edge-to-edge, `:4-8`), `Theme.ZendoLauncher.Light`
  (`launcher_window_background_light`, `windowLightStatusBar` and
  `windowLightNavigationBar` true, `:11-15`) and `Theme.ZendoLauncher.Dark` (the dark
  background, both flags false, `:18-22`). `values/colors.xml:3-4` defines both colours, so
  neither is night-qualified any more. The new `app/src/main/res/values-night/themes.xml:3`
  makes bare `Theme.ZendoLauncher` inherit the Dark variant, which is what keeps the
  manifest default following the system. `LauncherActivity.kt:47-51` calls `setTheme` with
  the explicit variant **before** `super.onCreate()` at `:53` when the user has chosen one,
  and leaves `SYSTEM` on the night-qualified default. A second, post-Compose correction
  derives the icon appearance from the resolved palette through
  `WindowCompat.getInsetsController` (`LauncherActivity.kt:90-101`).
- **Expected user benefit:** the window is already the colour the launcher is about to be,
  so no cold start flashes, whichever theme the user chose and whatever the system is set
  to.
- **Regression risk:** low. `SYSTEM` keeps the previous behaviour exactly, and the two
  explicit variants differ only in the window background and the two icon-appearance flags.
- **Related:** the status-bar half of the same disagreement is P0-8, which covers the
  post-Compose icon colour. This finding covers the window colour and the navigation-bar
  flag that the same edit now sets per variant rather than hardcoding.

## P1 — high-impact UX

### P1-1. "Refresh" visibility is coupled to the icon layout

- **Screen / component:** the Home bottom action row.
- **Observed problem:** whether a refresh control exists is decided by whether icons are
  shown in the app list. The two are unrelated. It is also redundant: both repositories
  already refresh themselves when packages change, so the manual control is a fallback that
  exists in two places with two different visibility rules.
- **Evidence:** `ui/HomeScreen.kt:101-103` reads
  `if (state.settings.layout == LayoutMode.ICON_TEXT) { QuietAction("Refresh", ...) }`.
  `platform/ShortcutRepository.kt:33-39` and `platform/AppRepository.kt:31-37` both register a
  `LauncherApps.Callback` that calls `refresh()` on package added, removed, changed,
  available, and unavailable. `ui/AppListScreen.kt:92` shows a second, unconditional
  `QuietAction("Refresh", ...)`.
- **Why it creates friction:** a control appears and disappears for a reason the user cannot
  see, and the same control behaves differently on two screens.
- **Recommended improvement:** remove `Refresh` from Home, keep one instance on the app list
  where a manual rescan is a plausible last resort, and leave the automatic callbacks as the
  primary path.
- **Expected user benefit:** one predictable place for one rarely needed action, and one
  fewer item competing for attention on the Home screen.
- **Regression risk:** medium. `ui/LauncherUiTest.kt:222-227` is a test named
  `homeScreen_rendersRefreshOnlyInIconTextLayout` that asserts exactly this coupling. That
  test must be deleted or inverted in the same change; leaving it will fail the build.

### P1-2. No onboarding

- **Screen / component:** first run, whole app.
- **Observed problem:** nothing explains what the launcher changes, how to make it the
  default Home app, how to reach the app list, how to return to the previous launcher, or
  that the launcher is optional. A first-run user lands on an empty Home screen.
- **Evidence:** `Screen` has three values and none of them is a first-run state
  (`ui/LauncherViewModel.kt:36`). `UiState` carries no first-run flag
  (`ui/LauncherViewModel.kt:38-48`). The only place the information exists is the README and
  a paragraph inside Settings (`ui/SettingsScreen.kt:132-139`), which a first-run user has no
  reason to open.
- **Why it creates friction:** the app's first impression is an empty screen with two words
  at the bottom and an instruction to long-press something that is on another screen.
- **Recommended improvement:** a single first-run surface that states the three facts a user
  needs (what this replaces, how to set it as Home, how to get back) and then never appears
  again. It should be skippable and text-only, and it should not become a permanent screen.
- **Expected user benefit:** a user can complete setup without reading the repository.
- **Regression risk:** low if the first-run state is a new `Screen` value. `ui/LauncherUiTest.kt:44-45`
  launches the real activity, so a first-run screen shown by default would break every test
  that expects to land on Home. Gate it behind a stored flag that the test setup resets, the
  same way `resetSettings()` at `:54-62` already resets the other preferences.

### P1-3. Hidden apps are listed by package name

- **Screen / component:** the Hidden apps section of Settings.
- **Observed problem:** the hidden list shows package identifiers rather than app names. The
  labels exist and are already loaded; they are dropped before the UI sees them.
- **Evidence:** `ui/SettingsScreen.kt:174` renders `key.substringBefore('/')`, so the list
  shows `com.whatsapp`, `com.android.chrome`. The filter that causes this is
  `ui/LauncherViewModel.kt:84-87`, `AppQuery.visible(apps, settings.hidden)`, applied before
  `results` and `favourites` are derived (`:89-95`). `UiState` exposes `visibleApps`
  (`:44`, assigned at `:122`) but no screen reads it: a grep for `visibleApps` across
  `app/src/main` returns only the view model's own lines. The plumbing is half-built.
- **Why it creates friction:** this is the one screen where a user decides whether to restore
  an app, and it is the one screen that does not say which app it is. Package names are not
  recognisable.
- **Recommended improvement:** expose the unfiltered list, or a key-to-label map, on
  `UiState` and render `target.label` in the hidden list, falling back to
  `key.substringBefore('/')` only when the app is no longer installed. `AppQuery` already has
  the ordering helpers to do this without new logic.
- **Expected user benefit:** a user can identify and restore a hidden app without decoding a
  package name.
- **Regression risk:** low. `ui/LauncherUiTest.kt:230-236` asserts the "No hidden apps."
  empty state, which is unaffected, and nothing asserts a package-name string.

### P1-4. Pinning gives almost no feedback

- **Screen / component:** `AppRow` favourite marker.
- **Observed problem:** the only confirmation that an app was pinned is a 6dp dot. The row
  carries no `stateDescription` or `contentDescription` for it, so assistive technology
  cannot report favourite status at all.
- **Evidence:** `ui/Components.kt:113-120` renders the marker as
  `Box(Modifier.size(6.dp).clip(CircleShape).background(LauncherColors.current.secondary))`.
  The enclosing row's `combinedClickable` at `:96-100` sets only `role = Role.Button`. There
  is no `Modifier.semantics` block in `AppRow`.
- **Why it creates friction:** a 6dp grey dot on a monochrome background is easy to miss, and
  the menu item that produced it closes immediately, so the user gets no confirmation that
  anything happened.
- **Recommended improvement:** keep the dot and add `Modifier.semantics { stateDescription = if (isFavourite) "Pinned" else "Not pinned" }`
  to the row, and change the menu item text so the resulting state is stated in the row
  rather than only implied by the dot.
- **Expected user benefit:** a screen-reader user can tell which apps are pinned, and a
  sighted user gets an unambiguous state.
- **Regression risk:** low. Adding a `stateDescription` does not change the text nodes the
  existing tests query.

### P1-5. Per-row hairline in a long list

- **Screen / component:** the app list, versus the same component on Home.
- **Observed problem:** the drawer draws a separator under every row of a list that can hold
  100 or more apps, while the identical row on Home draws none. One component has two
  treatments, and a hundred separators is decoration rather than structure.
- **Evidence:** `ui/AppListScreen.kt:81` calls `Hairline()` inside the `items` block, after
  every `AppRow`. `ui/HomeScreen.kt:76-85` calls the same `AppRow` with no separator. The
  separator token is 1dp (`ui/Components.kt:41-48`).
- **Why it creates friction:** at this density the rule stops separating anything and starts
  being texture, and it makes the drawer look like a different product from Home.
- **Recommended improvement:** drop the per-row hairline and separate rows with whitespace
  only, matching Home. If structure is needed in a long list, use a single hairline under the
  section heading, which is where the information actually is.
- **Expected user benefit:** the two app lists read as one product, and the drawer is calmer
  at scroll speed.
- **Regression risk:** low. No test asserts a separator; the existing UI tests address rows by
  text.

### P1-6. No alphabetical structure in a 100+ item list

- **Screen / component:** the app list.
- **Observed problem:** the drawer is one flat list. Nothing helps a user find an app without
  typing, even though the list is already ordered alphabetically.
- **Evidence:** `ui/AppListScreen.kt:70-83` is a single `LazyColumn` with `items(list, key = ...)`
  and no grouping, no sticky header, and no index rail. The ordering that a header would
  expose already exists: `core/AppQuery.kt:27-28`, `alphabetical`, sorts by
  `normalize(it.label)` then `key`, and `AppQuery.search` falls back to it for an empty query
  (`:41`).
- **Why it creates friction:** scrolling a long list is the fallback when a user does not know
  what to type, and there is currently no way to jump.
- **Recommended improvement:** add sticky first-letter headers derived from the already-sorted
  list. It is text-only, needs no new dependency, and matches the existing ordering.
- **Expected user benefit:** a user can jump to a letter instead of scrolling through 100
  rows.
- **Regression risk:** medium. Grouping changes the item count and index of the `LazyColumn`;
  `ui/LauncherUiTest.kt:104` asserts an empty-state string rather than list contents, so the
  exposure is limited, but any test that scrolls the list will need its indices revisited.

### P1-7. Icon load shifts the label

- **Screen / component:** `AppIcon` inside `AppRow`, in the icon-and-text layout.
- **Observed problem:** the icon composable renders nothing until the bitmap resolves, and
  the icon box is the only part of the row that is conditional on the bitmap, so the label
  moves sideways as icons resolve one by one while the list scrolls.
- **Evidence:** `ui/Components.kt:149-162` renders `bitmap?.let { Image(...) }` with no
  placeholder and no fixed-size container; the bitmap arrives from
  `produceState<Bitmap?>(initialValue = null, target.key)` at `:151`, and a null bitmap
  produces no layout node at all. The row reserves the 28dp slot only through the icon's own
  `Modifier.size(sizeDp.dp)` at `:159`.
- **Correction to the brief:** the brief states that "the 28dp slot and its 14dp spacer do not
  exist until the bitmap resolves". The spacer is not conditional. `ui/Components.kt:103-106`
  places `AppIcon(...)` and `Spacer(Modifier.width(14.dp))` inside the same
  `if (layout == LayoutMode.ICON_TEXT)` block, so the 14dp spacer is always present whenever
  the layout is icon-and-text. The shift is 28dp, from the missing icon box alone, not 42dp.
- **Why it creates friction:** the label is the only content in the row, so a horizontal jump
  of 28dp is the whole row moving. At scroll speed this reads as the list jittering.
- **Recommended improvement:** wrap the icon in a `Box(Modifier.size(28.dp))` that is always
  emitted, and draw the image inside it when the bitmap is ready. The reserved box costs
  nothing when empty.
- **Expected user benefit:** rows are stable from the first frame, and the list does not shift
  as the user scrolls.
- **Regression risk:** low. The change adds an empty box in the text-only layout only if the
  box is placed outside the layout check; keep it inside, as the spacer already is.

### P1-8. The Essentials empty state is two sentences and teaches a hidden gesture

- **Screen / component:** Home, Essentials section.
- **Observed problem:** the empty state is two sentences, the second of which exists only to
  teach the long-press that P0-4 says should not be the only path. Once P0-4 is fixed, the
  sentence is wrong as well as long.
- **Evidence:** `ui/HomeScreen.kt:73` renders
  `EmptyNote("Nothing pinned yet. Open All apps and long-press an app to add it here.")`.
  The brief's design direction allows one sentence or less for an empty state.
- **Why it creates friction:** the first-run screen is dominated by an instruction for a
  gesture the user cannot see, in a paragraph that also carries the only explanation of what
  the section is for.
- **Recommended improvement:** reduce to one sentence naming the outcome ("No pinned apps
  yet."), and let the visible affordance from P0-4 carry the instruction.
- **Expected user benefit:** a quieter first-run Home screen and an empty state that does not
  have to be rewritten when the affordance changes.
- **Regression risk:** medium. `ui/LauncherUiTest.kt:88-94` asserts this exact string with
  `onNodeWithText`. That assertion must be updated in the same change.

### P1-9. Settings uses a Material `Switch`

- **Screen / component:** `ToggleRow`, used by three settings.
- **Observed problem:** the loudest control in the product is a full Material3 pill switch,
  restyled to monochrome. It is the only control with a filled track and a moving thumb, and
  it is inconsistent with a text-first launcher whose other controls are text.
- **Evidence:** `ui/SettingsScreen.kt:213-224` renders a Material3 `Switch` with an explicit
  `SwitchDefaults.colors(...)`. The rest of the settings UI is text and hairlines
  (`:60-100`). The switch is used at `:83-87`, `:92-96`, and `:105-109`.
- **Why it creates friction:** the three switches draw the eye away from the settings that
  matter and break the visual language of the screen.
- **Recommended improvement:** replace the switch with a text state that the row toggles, for
  example a trailing "On" or "Off" in the secondary tone with the row itself selectable. This
  removes a Material component rather than restyling one, and it also resolves the
  non-overridden scheme roles noted in P0-1.
- **Expected user benefit:** the settings screen reads as text throughout, and the state of
  each setting is legible as words rather than as a position.
- **Regression risk:** medium. `ui/LauncherUiTest.kt:73-74` locates the three toggles by
  `isToggleable()` and index, so the tests depend on the toggle semantics. A text-based
  control must keep `Modifier.toggleable(...)`, or those helpers must be rewritten. The three
  tests using them are at `:167-182`, `:210-219`, and the helper at `:73-74`.

### P1-10. Pinned-order controls are cramped

- **Screen / component:** the Pinned order section of Settings.
- **Observed problem:** "Up", "Down", and "Unpin" are three text actions separated by 16dp,
  each roughly 37dp tall, with only 2dp of horizontal padding inside each. The targets are
  close together, and one of them, "Unpin", is destructive.
- **Evidence:** `ui/SettingsScreen.kt:150-154` lays the three out with
  `Arrangement.spacedBy(16.dp)`. `QuietAction` adds `padding(vertical = 10.dp, horizontal = 2.dp)`
  (`ui/Components.kt:69`), so the visual gap between adjacent targets is 16dp and each target
  is roughly 37dp tall. Unpinning removes the app from Essentials; the same action is not
  reversible from this row because the row disappears.
- **Why it creates friction:** three small adjacent targets, one of which is destructive and
  irreversible in place, is a mis-tap waiting to happen.
- **Recommended improvement:** this section duplicates what a reorder affordance on Home could
  do, and the cheapest fix is to reduce it. If it stays, raise each action to the 48dp
  minimum from P0-5 and increase the separation between "Unpin" and the two move actions so
  the destructive control is not adjacent to the common one.
- **Expected user benefit:** fewer accidental unpins and a reorder control that is usable one
  handed.
- **Regression risk:** low. No test addresses these three actions.

### P1-11. Hiding an app has no undo and silently unpins it

- **Screen / component:** the `AppRow` long-press menu.
- **Observed problem:** "Hide app" is a single tap with no confirmation and no undo. It also
  removes the app from Essentials, so one mis-tap on a menu that opens from a long-press
  removes the app from both lists. Reversing it requires finding the app by package name in
  Settings (see P1-3).
- **Evidence:** `ui/Components.kt:136-142` renders the "Hide app" `DropdownMenuItem` with
  `onClick = { menuOpen = false; onToggleHidden() }`, no confirmation. `data/SettingsStore.kt:62-67`
  shows `toggleHidden` also removing the key from `favourites`:
  `val favourites = if (key in hidden) current.favourites - key else current.favourites`. The
  favourite marker in the row is a 6dp dot (P1-4), so the user may not even have known the app
  was pinned.
- **Why it creates friction:** the most destructive action in the app is also the easiest to
  trigger by accident, and the recovery path runs through the screen that shows package
  names.
- **Recommended improvement:** either add a confirmation to "Hide app", or show a brief undo
  affordance after hiding. The undo is the better fit for the product's tone, and it needs
  only the existing `toggleHidden` call.
- **Expected user benefit:** a mis-tap costs one tap to fix instead of a trip to Settings.
- **Regression risk:** low. Nothing in the existing tests opens the long-press menu.

### P1-12. The toggle row's label is not tappable

- **Screen / component:** `ToggleRow`, used by all three settings toggles.
- **Observed problem:** the row is not clickable. Only the switch at the end of the row
  responds to a tap, so a user who taps the label "24-hour clock" gets nothing.
- **Evidence:** `ui/SettingsScreen.kt:204-212` renders the row as
  `Row(Modifier.fillMaxWidth().padding(vertical = 2.dp))` with no `clickable` and no
  `toggleable`. The label is a plain `Text` with `Modifier.weight(1f)` at `:212`, and the only
  interactive child is the `Switch` at `:213`.
- **Why it creates friction:** the label is the larger target and the one a user naturally
  aims at. The whole row looks like one control and behaves like two.
- **Recommended improvement:** move the interaction to the row with
  `Modifier.toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch)`
  and keep the `Switch` as a visual indicator only. This also fixes the touch target for the
  label area, which is currently a dead zone.
- **Expected user benefit:** tapping anywhere on the setting changes it, which is what the
  layout already implies.
- **Regression risk:** medium. `ui/LauncherUiTest.kt:73-74` selects toggles with
  `isToggleable()` by index. Making the row toggleable adds a second toggleable node per row
  if the `Switch` is left as-is, so the `Switch` must be made non-interactive in the same
  change or the index-based lookup will select the wrong node.

### P1-13. A false "no apps" state while the app list is still loading

- **Screen / component:** the app list, during the first seconds after launch.
- **Observed problem:** the list is empty until the background enumeration finishes, and the
  screen cannot tell "still loading" from "nothing installed". It shows the definitive message
  "No launchable apps found." for both.
- **Evidence:** `ui/AppListScreen.kt:65-68` renders the empty note whenever `list.isEmpty()`.
  The list is `state.results`, derived from `visibleApps`
  (`ui/LauncherViewModel.kt:84-87`, `:94-95`), which is built from the repository flows that
  start empty (`platform/AppRepository.kt:28`, `platform/ShortcutRepository.kt:27`). The
  enumeration runs after the first frame on a background dispatcher,
  `ui/LauncherViewModel.kt:140-143`. `UiState` has no loading flag to distinguish the states
  (`ui/LauncherViewModel.kt:38-48`).
- **Why it creates friction:** opening the drawer immediately after a cold start tells the
  user that no apps were found. On a slow device or with many packages this is a false and
  alarming message about a list that is about to appear.
- **Recommended improvement:** add a loading flag to `UiState`, set while the first
  enumeration is in flight, and render nothing, or a single quiet word, instead of the
  "no apps" note while it is set.
- **Expected user benefit:** the empty state only appears when it is true.
- **Regression risk:** medium. `ui/LauncherUiTest.kt:104` asserts "No launchable apps found."
  is displayed on arrival at the screen. That test runs under Robolectric where the
  enumeration does not complete, so the assertion encodes exactly the false state described
  here and will need to be rewritten to assert the loading state instead.

## P2 — visual refinement

### P2-1. The section heading is below the comfortable minimum size

- **Screen / component:** `SectionHeading`, used for Essentials, Today, and every Settings
  section.
- **Observed problem:** the section style is 11sp with 1.2sp tracking. It is the smallest text
  in the app and it scales badly at large font settings, where its tracking grows with it.
- **Evidence:** `ui/theme/Theme.kt:68` defines `section` as `fontSize = 11.sp`,
  `fontWeight = FontWeight.Medium`, `letterSpacing = 1.2.sp`. `ui/Components.kt:51-58`
  renders it in the secondary tone.
- **Recommended improvement:** raise it to 12sp and keep the tracking, or drop the tracking and
  rely on the tone. 12sp reads better without becoming loud, and it stays closer to the
  readable minimum.
- **Expected user benefit:** section labels are legible without competing with content.
- **Regression risk:** low. The heading strings are asserted in
  `ui/LauncherUiTest.kt:82`, `:109`, `:151`, `:235`, by text, not by size.

### P2-2. No spacing scale exists

- **Screen / component:** all three screens.
- **Observed problem:** spacing is inline literals with no named scale. Fourteen distinct
  values are in use: 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 24, 28, 40, 44, and 56 dp. The one
  consistent value is the 24dp gutter, used on all three screens.
- **Evidence:** examples across the codebase: `ui/HomeScreen.kt:52` (56), `:54` (6), `:63`
  and `:68` (44), `:88` (40), `:94` (24 and 18), `:95` (28); `ui/AppListScreen.kt:36` (28),
  `:53` (12), `:55` (20), `:86` (16); `ui/SettingsScreen.kt:49` (28), `:56` (24), `:72` (14),
  `:97` (6). The 24dp gutter appears at `ui/HomeScreen.kt:50`, `ui/AppListScreen.kt:34`, and
  `ui/SettingsScreen.kt:47`.
- **Recommended improvement:** define one small set of named values and use it. The brief's
  design direction asks for this explicitly, and the values already cluster: 4, 8, 12, 16,
  24, 32, 48 would cover every current use with a handful of literals retired.
- **Expected user benefit:** vertical rhythm becomes consistent and future screens do not
  invent new gaps.
- **Regression risk:** low. No test asserts a dp value.

### P2-3. The Home top offset is a guessed constant

- **Screen / component:** Home.
- **Observed problem:** the first element is a fixed 56dp spacer inside the system-bar
  padding, so the clock's position depends on a number that was chosen by eye rather than
  measured from the inset.
- **Evidence:** `ui/HomeScreen.kt:52` is `Spacer(Modifier.height(56.dp))`, the first child of
  the column that already has `.systemBarsPadding()` at `:43`.
- **Recommended improvement:** replace the constant with a fraction of the available height, or
  with a value derived from the inset plus a token from the spacing scale, so the block sits
  consistently on short and tall screens.
- **Expected user benefit:** the clock is placed deliberately on every screen size instead of
  landing differently depending on the status bar height.
- **Regression risk:** low. No test asserts the offset.

### P2-4. The action ripple is a circle inscribed in a wide row

- **Screen / component:** `QuietAction`.
- **Observed problem:** the modifier order clips to a circle before the padding and before the
  clickable, so the press feedback is a circle sized to the node's shorter dimension, centred
  in a target that may be much wider. On a long label the ends of the target have no feedback.
- **Evidence:** `ui/Components.kt:66-69` orders the modifiers
  `.clip(CircleShape).combinedClickable(...).padding(vertical = 10.dp, horizontal = 2.dp)`.
  The clip applies to the node's full bounds, which are as wide as the label plus 4dp, so a
  label such as "Open Home app settings" (`ui/SettingsScreen.kt:127`) produces a circle much
  narrower than the tappable row.
- **Recommended improvement:** use a shape that matches the row, or drop the clip and let the
  ripple fill the node. A hairline-bounded rectangle would fit the product better than a
  circle.
- **Expected user benefit:** press feedback covers the area that actually responds.
- **Regression risk:** low.

### P2-5. The long-press menu opens at the row's corner

- **Screen / component:** the `AppRow` menu.
- **Observed problem:** the menu is anchored to a full-width box, so it opens at the top-left
  of the row rather than near the finger or the trailing edge where the user long-pressed.
- **Evidence:** `ui/Components.kt:91` is `Box(modifier.fillMaxWidth())`, and the
  `DropdownMenu` at `:123` is a child of that box with no offset and no anchor of its own.
- **Recommended improvement:** anchor the menu to the row's trailing edge, or offset it toward
  the touch point, so it opens where the user's attention is.
- **Expected user benefit:** the menu appears adjacent to the finger, which is the standard
  expectation for a long-press menu.
- **Regression risk:** low.

### P2-6. Two layout mechanisms in one row

- **Screen / component:** the Home bottom action row.
- **Observed problem:** the row uses both uniform spacing and a weighted spacer, so the gap
  before the pushed element is the sum of both and differs from every other gap in the row.
- **Evidence:** `ui/HomeScreen.kt:91-96` sets `horizontalArrangement = Arrangement.spacedBy(28.dp)`
  and the row contains `Spacer(Modifier.weight(1f))` at `:100`, between "Settings" and the
  conditional "Refresh".
- **Recommended improvement:** pick one. Either let `spacedBy` do the work and drop the
  spacer, or drop `spacedBy` and place explicit spacers. Removing "Refresh" per P1-1 also
  removes the need for the weighted spacer.
- **Expected user benefit:** the bottom row has one rhythm instead of two.
- **Regression risk:** low.

### P2-7. One component serves two different jobs

- **Screen / component:** `EmptyNote`.
- **Observed problem:** it is used both for true empty states and for long explanatory
  paragraphs, at the same 13sp secondary tone. A paragraph of about 280 characters reads as
  a hint line.
- **Evidence:** `ui/Components.kt:165-172` is a single 13sp `Text` with 4dp vertical padding.
  True empty states use it at `ui/HomeScreen.kt:73`, `ui/AppListScreen.kt:66`, and
  `ui/SettingsScreen.kt:145`, `:164`. Long explanations use it at
  `ui/SettingsScreen.kt:98-100`, `:111-114`, and `:132-139`.
- **Recommended improvement:** keep `EmptyNote` for empty states and give explanatory copy its
  own treatment, or shorten the explanations to a single line each.
- **Expected user benefit:** empty states and help text become distinguishable, and the
  Settings screen stops reading as a wall of small grey text.
- **Regression risk:** low for the visual change. `ui/LauncherUiTest.kt:233-234` asserts the
  two empty-state strings by text, which is unaffected.

### P2-8. Dead flexibility on `SecondaryText`

- **Screen / component:** `SecondaryText`.
- **Observed problem:** the composable exposes a `color` parameter whose default reads a
  `CompositionLocal`, and no caller ever overrides it. The parameter is a second way to do
  what the surrounding `LauncherColors.current` already does.
- **Evidence:** `ui/Components.kt:175` declares
  `fun SecondaryText(text: String, modifier: Modifier = Modifier, color: Color = LauncherColors.current.secondary)`.
  Every call site passes only text: `ui/HomeScreen.kt:157`, `:164`, `:171`;
  `ui/SettingsScreen.kt:119`, `:188`, `:197`.
- **Recommended improvement:** remove the parameter and read the palette inside the composable,
  matching `SectionHeading` at `ui/Components.kt:51-58`, which does exactly that.
- **Expected user benefit:** one way to render secondary text, and no parameter that invites a
  caller to break the two-tone rule.
- **Regression risk:** low. The removal is mechanical; the six call sites pass no colour.

### P2-9. A trailing hairline closes every list

- **Screen / component:** the app list and the two Settings lists.
- **Observed problem:** the separator is emitted after each row rather than between rows, so a
  rule is drawn under the last item, closing a list that is still open.
- **Evidence:** `ui/AppListScreen.kt:81` calls `Hairline()` inside the `items` block.
  `ui/SettingsScreen.kt:156` and `:181` call it after each row inside the two `forEach`
  loops.
- **Recommended improvement:** draw the separator above each row except the first, or accept
  the whitespace-only treatment recommended in P1-5 and remove these rules entirely.
- **Expected user benefit:** lists do not appear to end mid-screen.
- **Regression risk:** low.

### P2-10. The "Show today" toggle has no observable effect

- **Screen / component:** the Today section toggle in Settings.
- **Observed problem:** the toggle is wired to real state and the Home screen does gate the
  Today block on it, but the block also requires a Zendo payload, and the only shipped source
  always returns null. So the section never renders and the toggle changes nothing on screen,
  in either position.
- **Evidence:** `ui/SettingsScreen.kt:92-96` renders the toggle bound to
  `state.settings.todayEnabled`. `ui/HomeScreen.kt:61-66` gates the block on
  `if (state.settings.todayEnabled) { state.zendo?.let { ... } }`. `state.zendo` comes from
  `ui/LauncherViewModel.kt:97-102`, which reads `zendoSource`, and the shipped source is
  `UnavailableZendoSource` (`ui/LauncherViewModel.kt:73`). The Settings copy already states
  this at `ui/SettingsScreen.kt:111-114`, and `docs/zendo-integration.md` confirms no planning
  API exists.
- **Recommended improvement:** leave the code as it is, since the wiring is correct and will
  work when a source ships, but adjust the copy so the toggle does not imply a visible result
  today. The adjacent note at `ui/SettingsScreen.kt:98-100` currently says turning it off
  gives a minimal home screen, which is true but not the reason nothing appears.
- **Expected user benefit:** the settings screen stops offering a control that appears broken.
- **Regression risk:** low. `ui/LauncherUiTest.kt:210-219` asserts the toggle updates state,
  which remains true, and `:146-165` already documents that no Today block renders.

## Verified as working, do not regress

- **PWA discovery.** `platform/ShortcutRepository.kt:50-80` queries
  `FLAG_MATCH_PINNED`, iterates `userManager.userProfiles` so it is profile-aware, skips
  shortcuts whose `shortLabel` is null or blank (`:67`), dedupes by `key` (`:79`), and
  swallows the platform's `RuntimeException` when the launcher is not the default home app
  (`:60-65`) while reporting availability through `_available` (`:78`). Web apps installed as
  WebAPKs register real launcher activities and are picked up separately by
  `platform/AppRepository.kt:48-71`. Launching goes through `platform/AppLauncher.kt`:
  `startMainActivity` for activities (`:31-39`), `startShortcut` for shortcuts (`:41-49`),
  and a `getLaunchIntentForPackage` fallback for both (`:52-64`), with a toast rather than a
  crash on total failure (`:26-28`). Do not restructure any of this.
- **Search ranking.** `core/AppQuery.kt:39-59` implements exact, prefix, word-start, contains,
  then initials ranking, with NFD accent stripping at `:17-21` and an alphabetical fallback
  for an empty query at `:41`. It is pure, has no Android dependency, and is covered by 9
  tests in `core/AppQueryTest.kt`. Reuse it as is.
- **Cold start.** `ui/LauncherViewModel.kt:131-146` registers the package callbacks
  synchronously and enumerates on `Dispatchers.IO` after the first frame, guarded by an
  `enumerated` flag so a resume does not re-enumerate. Preserve that ordering; the loading
  state added for P1-13 must not move enumeration onto the main thread.
- **Persistence.** `data/SettingsStore.kt:39-51` writes through `prefs.edit { }` and updates
  the `StateFlow` in the same call, so the UI never observes a value that was not written.
  `toggleHidden` correctly drops a hidden app from favourites (`:62-67`) and `moveFavourite`
  clamps to the list bounds (`:69-78`). The storage choice is documented at `:14-18`.
- **Monochrome palette.** `DarkPalette` and `LightPalette` (`ui/theme/Theme.kt:32-46`) are the
  identity: two text tones and one separator tone, no accent hue. Keep every value; only add
  roles to the scheme, as P0-1 asks.
- **Clock tick.** `ui/HomeScreen.kt:112-120` ticks on the minute boundary, computing
  `delay(1_000L * (60 - current.second))`, rather than waking once a second. Correct and
  deliberate, with the reason recorded at `:108-111`. Keep it.
- **Back handling.** `LauncherActivity.kt:62` enables a `BackHandler` only when the screen is
  not Home, so Back from a secondary screen returns home and Back on Home reaches the system.
  Correct.

## Not verifiable in this environment

The following are reasoned from platform documentation and API contracts, or from the
Compose and Robolectric behaviour the tests exercise. None of them was observed.

- **Real-device rendering.** No screen has been rendered on hardware or an emulator. Nothing
  here describes how the UI looks; the findings describe what the code does.
- **Real font-scale behaviour.** The overflow estimate in P0-7 comes from default font
  metrics, not from a device at a real font scale. Robolectric composes at the default scale,
  so no test covers it.
- **Real IME behaviour.** P0-2 depends on how a real keyboard and the window inset interact.
  Robolectric does not simulate an IME, and the UI tests set the query on the view model
  rather than typing into the field.
- **The real `LauncherApps` app list.** Every test runs against an empty app list; the
  Robolectric assertions for the drawer expect "No launchable apps found."
  (`ui/LauncherUiTest.kt:104`). List density, scroll performance, and the behaviour of the
  per-row hairline at 100 or more rows (P1-5) are therefore unverified.
- **Real PWA discovery.** `ShortcutRepository` is exercised only against the Robolectric
  platform, which returns no shortcuts. The dedupe, the blank-label skip, and the
  `RuntimeException` path are read from the source and have no runtime evidence behind them.
- **TalkBack.** No screen-reader session was run. The accessibility findings (P0-4, P0-6,
  P1-4, P1-12) are derived from the semantics each composable declares in source, not from
  what a screen reader actually announces.
- **Status-bar contrast.** P0-8 is reasoned from the documented default of
  `SystemBarStyle.auto` in androidx.activity. The failure was not reproduced on a device.
- **Estimated measurements.** The dp arithmetic in P0-5 (roughly 37dp and 29dp) and P0-7
  (roughly 144dp tall, wider than 312dp at `fontScale 2.0`) is computed from default font
  metrics. The conclusions are robust to a few dp of error, but the numbers themselves are
  estimates.
