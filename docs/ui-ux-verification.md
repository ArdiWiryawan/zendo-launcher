# UI/UX redesign verification report

Deliverable D for the UI/UX redesign. It records what was mechanically verified after the
redesign landed, the regression checklist, what could not be verified, and the findings that
remain open. Finding ids refer to `docs/ui-ux-audit.md`; the changes referred to are the ones
described in `docs/ui-ux-implementation.md` (commits `22273c1` through the second pass).

Every claim below is a Gradle task result, a file read, or a CI run. Nothing was observed on
a device.

## 1. What was verified, and how

Local build, Windows, `./gradlew.bat --no-daemon`, after deleting `app/build/kotlin` and
`build/kotlin` and stopping stale Gradle daemons:

- `testDebugUnitTest`: BUILD SUCCESSFUL. 37 tests, 0 failures, 0 errors, 0 skipped. Counted
  from `app/build/test-results/testDebugUnitTest/*.xml`: `core/AppQueryTest` 9,
  `core/TodaySelectorTest` 6, `ui/LauncherUiTest` 22. The UI suite grew from 18 to 22 in the
  second pass; the four new tests are named in `docs/ui-ux-implementation.md`.
- `lintDebug`: BUILD SUCCESSFUL. Report at `app/build/reports/lint-results-debug.xml`.
  4 issues, all `severity="Warning"`, 0 errors: 4 x `GradleDependency`. The
  `ConfigurationScreenWidthHeight` warning was cleared in the second pass, because
  `HomeScreen` now reads the measured container size from `LocalWindowInfo`.
  An earlier lint run reported two `NewApi` errors: `android:windowLightNavigationBar`
  requires API 27 and the minimum is 26. They were fixed by adding `tools:targetApi="27"` to
  the two style items in `app/src/main/res/values/themes.xml` (lines 17 and 24). That is why
  the current report has no errors.
- `assembleDebug`: BUILD SUCCESSFUL. `app/build/outputs/apk/debug/app-debug.apk`,
  11,717,817 bytes, sha256 `5059157c…`.

**APK content check.** The dex and `resources.arsc` inside the APK were read directly with
Python's `zipfile`. Present in the dex: `Reopen onboarding`, `Looking for apps`,
`No pinned apps yet`, `Hide and unpin`, `Zendo is optional`, `Set as Home app`,
`No launchable apps found`. Present in `resources.arsc`: `Theme.ZendoLauncher.Base`,
`Theme.ZendoLauncher.Light`, `Theme.ZendoLauncher.Dark`, `launcher_window_background_light`,
`launcher_window_background_dark`. This check is what rules out a stale artifact: the strings
and resource names the redesign introduced are inside the APK that was built in this session,
so the artifact is the redesigned build rather than a cached one.

**CI.** `.github/workflows/build-apk.yml` runs `testDebugUnitTest`, `lintDebug` and
`assembleDebug` on every push to `main`. The run for commit `22273c1` completed `success`
(run 38422283353, 2m59s); the run for `36a2381` completed `success` (run 38422959857,
2m19s). The same three tasks therefore pass on a second machine against a clean checkout.

## 2. Regression checklist

Fourteen items. Each states the check, the evidence, and a verdict. Where the evidence is a
source read of code the redesign did not touch, the verdict is still given, and the limit of
that evidence is stated.

| # | Item | Check and evidence | Verdict |
| --- | --- | --- | --- |
| 1 | Existing user preferences survive | `data/SettingsStore.kt` still reads and writes every setting through one `prefs.edit { }` and updates the `StateFlow` in the same call (audit, "Persistence"). `LauncherUiTest` drives `setTheme`, `setLayout`, `setTodayEnabled`, `setClock24h`, `setZendoEnabled` and reads the values back. No test loads preferences written by a pre-redesign build, so upgrade survival is not covered. | NOT VERIFIED |
| 2 | App discovery still works | `AndroidManifest.xml:11-16` still declares the `MAIN`/`LAUNCHER` `<queries>` block and no `QUERY_ALL_PACKAGES`. `platform/AppRepository.kt` is not in the redesign diff. Robolectric returns no installed apps, so no real enumeration ran. | NOT VERIFIED |
| 3 | PWA and pinned-shortcut support still works | `platform/ShortcutRepository.kt` is not in the redesign diff; the `FLAG_MATCH_PINNED` query, profile loop, blank-label skip and dedupe are unchanged. No test exercises a shortcut, and Robolectric returns none. | NOT VERIFIED |
| 4 | Launching still works | `platform/AppLauncher.kt` is not in the redesign diff; `startMainActivity`, `startShortcut` and the `getLaunchIntentForPackage` fallback are unchanged. No test launches anything. | NOT VERIFIED |
| 5 | Search ranking unchanged | `core/AppQuery.kt` is not in the redesign diff. `core/AppQueryTest` passes 9 of 9, including the ranking order (exact, prefix, word start, contains, initials), the accent and case normalisation, and the alphabetical fallback on an empty query. | PASS |
| 6 | Hidden apps still hidden | `AppQuery.visible` is unchanged and `core/AppQueryTest`'s `hidden apps are removed` passes. `SettingsScreen` now names the hidden entries from `state.allApps` (P1-3), which is a label change, not a filtering change. | PASS |
| 7 | Favourites order preserved | `AppQuery.favourites` is unchanged and `core/AppQueryTest`'s `favourites keep user order and drop stale keys` and `favourites can hold a shortcut key` both pass. | PASS |
| 8 | Theme persists, and the monochrome palette is unchanged | `settings_themeChoice_updatesState` passes: selecting "Dark" then "Light" moves the stored `ThemeMode`. The palette values are still `primary` `0xFFF2F2F0` / `0xFF8A8A88` and `0xFF111112` / `0xFF6E6E6C` (`ui/theme/Theme.kt:36-45`); the redesign diff adds scheme roles and does not change them. | PASS |
| 9 | The build process still works | `testDebugUnitTest`, `lintDebug` and `assembleDebug` all BUILD SUCCESSFUL locally, and the same three tasks pass in CI on commits `22273c1` and `36a2381`. | PASS |
| 10 | No runtime permissions added | `AndroidManifest.xml` was read in full. It declares no `uses-permission` element at all, so it requests no runtime permission and no `INTERNET`. | PASS |
| 11 | No network use | Same read: no `INTERNET` permission is declared, so the app cannot open a socket. A search of `app/src/main` and `app/build.gradle` for `INTERNET`, `okhttp`, `retrofit`, `ktor`, `HttpURLConnection` and `URL(` returns nothing. | PASS |
| 12 | No data leaves the device | Follows from item 11: without `INTERNET` the platform denies network access to this process, so no stored preference or app list can be transmitted. | PASS |
| 13 | Cold-start enumeration ordering preserved | `ui/LauncherViewModel.kt` still registers the package callbacks and enumerates on `Dispatchers.IO` after the first frame (audit, "Cold start"); the new `isLoading` flag is cleared in a `finally` (line 194). `appList_loadingFlag_clearsAfterEnumeration` passes, which is the guard against the flag sticking. | PASS |
| 14 | Deliberate behaviours named in the audit are unchanged: Back handling and the minute-boundary clock tick | Source read: `LauncherActivity` still enables `BackHandler` only off Home (now also off onboarding), and the clock still computes `delay(1_000L * (60 - current.second))` rather than waking every second. `LauncherUiTest` asserts the clock renders as a single node in both 12-hour and 24-hour form, but no test presses Back and none observes the tick timing. | PASS for the clock rendering and the source read; NOT VERIFIED for Back and for the tick timing |

## 3. What could not be verified

The redesign was never seen running. The following were not exercised, and no statement in
this report or in `docs/ui-ux-implementation.md` should be read as evidence that the
interface works or looks right.

- **No device and no emulator.** Every run was a JVM run. The Compose UI executed under
  Robolectric, which composes the real screens but draws them through a software rasteriser,
  not a display.
- **No real font scale.** Robolectric composes at the default scale. The `clockSizeSp`
  shrink (P0-7) is arithmetic from default font metrics and was not observed at a raised
  system font scale.
- **No real IME.** Robolectric simulates no keyboard, and `LauncherUiTest` sets the query on
  the view model rather than typing into the field. The `.imePadding()` change (P0-2) is the
  one change in the set that depends entirely on real IME behaviour, and it has no runtime
  evidence behind it.
- **No TalkBack session.** The accessibility claims (P0-4 announcement, P0-6 selectable
  role, P1-4 state description, P1-12 row toggle) are read from the semantics each composable
  declares in source, not from what a screen reader announces.
- **No real `LauncherApps` app list.** Every test runs against an empty list. List density,
  the sticky letter headers at 100 or more rows (P1-6), and the removal of the per-row
  hairline (P1-5) are unverified at real density.
- **No PWA or shortcut discovery exercised.** `ShortcutRepository` runs only against the
  Robolectric platform, which returns no shortcuts.
- **No visual confirmation of the result, and no before-and-after screenshots.**
  `docs/ui-ux-review/` was not created. A Robolectric screenshot harness was written, and it
  captured 720x1600 PNGs, but decoding them showed zero non-background pixels: the native
  graphics mode rasterised backgrounds and hairlines and drew no text glyphs at all. Three of
  the captures were byte-identical, sharing one SHA-256. The harness's own non-blank guard
  could not detect this, because a solid background satisfies a check for non-background
  pixels. The harness and every image were deleted rather than shipped as misleading
  evidence. For a text-first launcher these images carried no information, and their absence
  is why this report contains no visual claim.

## 4. Findings still open

The first pass left four findings open. The second pass closed three of them in code
(P0-4's affordance, P1-11's undo, P2-3's fixed offset) and closed P2-10's copy; each is
recorded under "Findings closed in the second pass" in `docs/ui-ux-implementation.md`.
What remains is one finding that cannot be closed from this repository, and one accepted
design decision.

- **P2-10, "Show today" still has no visible effect.** Not closable here. The toggle is
  bound to `state.settings.todayEnabled` and `HomeScreen` gates the block on it
  (`ui/HomeScreen.kt:81`), but the block also needs a `state.zendo` payload, and the only
  shipped source is `UnavailableZendoSource` (`ui/LauncherViewModel.kt:17`, `:82`), which
  returns null. The second pass fixed what was actually wrong — the note sat under the
  *other* toggle, so the control that looks broken never explained itself — and the two
  notes now read correctly (`ui/SettingsScreen.kt:110-114`, `:122-125`). The toggle will
  still change nothing on screen until a Zendo source ships. This is a dependency of the
  product, not a defect in the launcher.
- **P0-4 is fixed in the drawer and deliberately not on Home.** The audit asked for "a
  visible, small, text-only affordance to each row in the app list". That now ships:
  `showPinAction` (`ui/Components.kt:131`) renders a trailing `Pin` / `Unpin` action
  (`:175-186`) with a per-app `contentDescription`, and `AppListRow` turns it on
  (`ui/AppListScreen.kt:196`). Home leaves it off, because every Home row is already pinned
  and an `Unpin` on each would be a row of noise for a state the 6dp dot already reports.
  The long press, `onLongClickLabel` and `stateDescription` are unchanged. This is an
  accepted, recorded decision, not an oversight.

## 5. Honesty statement

What is proven: the code compiles, the 37 JVM tests pass, lint reports no errors and four
warnings, the APK built in this session contains the strings and resources the redesign
introduced, and CI reproduces all three tasks on a second machine for two commits.

What is not proven: that the interface looks good, that it feels premium, or that it works on
a real device. No screen was ever rendered for a person to look at. The redesign is verified
as a change that builds and passes its tests, not as a change that has been seen to work.
