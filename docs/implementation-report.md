# Implementation Report

## 0.5.0 — Intentional launcher: quieter Today, agenda-only Plan, manual bridge

Tree state: versionName 0.5.0 / versionCode 13 (`app/build.gradle`).

The refinement brief's phase order was followed, not a rewrite: data layer first,
then screens, then polish. The Focus timer was removed from the launcher entirely —
sessions belong to the Zendo app; the note page keeps its persistent note. Zendo
integration is now a manual, one-way snapshot file; no live source, token or
network claim survives in the tree.

### What shipped (by phase)

1. **Data layer** (commit 12836c6): `core/DayRecord.kt` (day records keyed by
   local date with a stable id, `MAX_DAYS = 400`), `core/ZendoBridge.kt` +
   `core/BridgePlanner.kt` (strict snapshot v1 parse; merge fills blanks and
   refreshes only records previously imported, replace-imported removes only
   `origin = zendo-pwa` for the target date; future-dated snapshots rejected),
   `data/HistoryStore.kt` (capture today on content change; past records never
   touched), `data/BridgeCodec.kt` (snapshot/backup JSON; strict validation;
   backup contains no package names and no calendar ids).
2. **History + Today**: `ui/HistoryScreen.kt` (newest-first day records,
   loading/empty states, export backup + clear with confirmation); Today shows
   only the Today Action, Highlight, Tasks and one small History line. Upcoming,
   the Focus shortcut and the usage summary are gone from Today.
3. **Plan rework**: `ui/PlanPage.kt` replaces the old Plan & Focus page —
   compact prev/next date navigation, the full chronological agenda for the
   chosen day, all-day labels, an in-progress marker ticking per minute, and
   distinct states (permission / no calendars / none chosen / sync off / no
   events / error with stale results kept).
4. **Explicit calendar selection**: `shownCalendarIds` empty now means *none*,
   never *all*. One-time migration (`calendarSelectionSchema` +
   `settleCalendarSelection`) materializes the previous view onto the first
   readable calendar list, intersects the legacy `calendarGoogleOnly` flag until
   migrated, then clears it; the Google-only toggle is gone from the UI.
5. **Essentials**: pin limit is 5 or 6 by user choice
   (`MIN/MAX/DEFAULT_PIN_LIMIT`); a full page refuses with "This page is full.
   Unpin an app first." instead of silently doing nothing; grayscale icons are
   the default (`LocalMonochromeIcons` + `ColorMatrix` desaturation) with an
   Appearance toggle; no folders, badges or usage counts exist.
6. **Screen Time**: off and unrendered by default; enabling opens
   `ACTION_USAGE_ACCESS_SETTINGS` once, the grant is re-verified read-only on
   activity resume (no repeat prompts), "Hide summary" is separate, and all
   queries stop when the feature is off.
7. **Wallpapers**: three designs (Quiet Stone default; "Kembali ke yang
   penting."; "Less, but better" + ZENDO wordmark), 9:20 geometry with the top
   third kept clear, official `WallpaperManager` FLAG_SYSTEM/FLAG_LOCK,
   Lock/Both offered only when `lockTargetSupported` reports them, an honest
   system-picker fallback, and "Applied" only after the API confirms.
8. **Settings**: grouped Apps / Calendar / Zendo / History / Screen Time /
   Wallpaper / Appearance (plus Setup & permissions, Home app, Intentional
   Pause, System settings, Privacy, About). The old "Connect Zendo" toggle is
   gone; the Zendo and History groups carry the SAF pickers, previews and
   confirmations.

### Files changed (vs 0.4.1)

Added: `core/CalendarCache.kt`, `core/DayRecord.kt`, `core/Origins.kt`,
`core/ZendoBridge.kt`, `core/BridgePlanner.kt`, `data/HistoryStore.kt`,
`data/BridgeCodec.kt`, `ui/HistoryScreen.kt`, `ui/PlanPage.kt`,
`ui/DataDialogs.kt`, and their tests.

Removed: `core/FocusSession.kt`, `core/TodaySelector.kt`,
`core/UpcomingMerge.kt`, `data/FocusStore.kt`, `platform/ZendoSource.kt`,
`model/ZendoToday.kt`, `ui/PlanFocusPage.kt`, and their tests. The calendar
staleness rule was preserved as `core/CalendarCache.kt`.

Changed: `LauncherViewModel.kt`, `LauncherActivity.kt`, `HomeScreen.kt`,
`TodayDashboard.kt`, `CalendarScreen.kt`, `SettingsScreen.kt`, `AppListScreen.kt`,
`Components.kt`, `HighlightOverlays.kt`, `SettingsStore.kt`, `WallpaperStore.kt`,
`core/WallpaperTemplate.kt`, `platform/WallpaperApplier.kt`,
`ui/WallpaperScreen.kt`, `app/build.gradle` (0.5.0/13), README and docs.

### Build and test results

- `:app:testDebugUnitTest` BUILD SUCCESSFUL, 0 failures.
- `:app:lintDebug` run; `abortOnError false` per project config.
- `:app:assembleDebug` BUILD SUCCESSFUL (local); the APK is attached to the
  `debug-0.5.0` pre-release.

### Verification status

- Everything above is JVM/Robolectric + source-level verification only.
- No physical device run, no live Google account, no OEM lock-screen run: every
  claim about FLAG_LOCK being honored, real sync state, or system-Settings
  return flows remains a design + test claim, not a device-verified one. The
  checks that require hardware are listed in
  `docs/manual-device-test-plan.md`.

### Remaining limitations

- The bridge is manual and one-way by design; no background sync, no account.
- The Zendo PWA export is a separate file the user moves by hand (SAF).
- No month grid, no calendar writes, no widget hosting (unchanged from 0.4.x).
- Usage numbers stay device-only and are never uploaded.

---

## 0.4.1 — Calendar agenda reliability, wallpaper lifecycle, tasks coherence

Tree state: versionName 0.4.1 / versionCode 12 (`app/build.gradle`).
Audit-first fixes for four reported symptoms; honestly distinguishes confirmed
causes from unreproduced hypotheses (see Root causes).

### Root causes

1. **Agenda empty.** No broken data path found in code: permission gating,
   Instances expansion with Events fallback, overlap matching, and sorting all
   verify by review and test. Real gaps closed: (a) sync-off blindness —
   `SYNC_EVENTS` is now read and surfaced with recovery guidance; (b) no
   diagnostics — Calendar screen now reports source, permission, counts,
   reading filter, and last refresh (ids/counts only, never event content).
   Most likely remaining cause on a real device is environmental (no synced
   Google account, denied permission, sync disabled), each with its own UI
   state.
2. **Wallpaper kills launcher.** No in-code trigger exists: no HOME intents,
   no `ROLE_HOME` requests, no wallpaper services, apply runs on IO, and all
   launcher state is prefs-backed so recreation restores context. Hardened
   anyway: render at `desiredMinimumWidth/Height` instead of fixed 1080x1920
   (memory + crop correctness). Unreproduced — logcat checklist in
   Limitations; hypothesis (low-RAM kill during oversized bitmap work) now
   removed by construction.
3. **Back "animates".** Confirmed absent a second time: zero transition APIs
   in the tree; the only horizontal motion is pager physics and genuine
   programmatic moves. The perceived slide is the platform's own edge-back /
   predictive-back effect. No code change was the correct fix.
4. **Tasks incoherence.** Confirmed and fixed: no view filters (added
   Today/Upcoming/All/Done over existing states + per-view empty states),
   no overdue signal (added quiet "Overdue" word with day counts), rapid
   double-tap created twin records in tasks AND highlights (1.5 s same-title
   guard in both stores).

### Files changed (vs 0.4.0)

Changed: `data/HighlightStore.kt` + `data/TaskStore.kt` (double-submit
guards), `core/TaskItem.kt` (`TaskView`, `forView`, overdue helpers),
`ui/HighlightOverlays.kt` (view tabs, per-view empty states, overdue meta),
`model/CalendarEvent.kt` (+`syncEvents`), `platform/CalendarRepository.kt`
(`SYNC_EVENTS` column), `platform/WallpaperApplier.kt` (device-size render),
`ui/CalendarScreen.kt` (sync-off hint, Diagnostics), `app/build.gradle`
(0.4.1/12), README (pointer).

### Calendar / wallpaper / navigation / tasks results

- Calendar: provider path kept (Option A — valid, fixed not rewritten);
  read-only `READ_CALENDAR` only; verified by unit tests, not a live account.
- Wallpaper: direct `setBitmap` path kept (no crop intent needed — templates
  are exact-size renders; no FileProvider/URIs involved); resume restores via
  persisted selection + page + screen state.
- Navigation: unchanged, verified again; Back matrix in
  `docs/back-navigation-audit.md` still accurate.
- Tasks: filtered views, overdue word, dedupe guards, existing confirms kept;
  English retained (examples in the request were illustrative, not a locale
  change order).

### Build and test results

- `:app:testDebugUnitTest` BUILD SUCCESSFUL, 213 tests, 0 failures (209 carried
  over + 4 new: double-submit guards, view filters, overdue labels).
- `:app:lintDebug` run; `abortOnError false` per project config.
- `:app:assembleDebug` BUILD SUCCESSFUL (local). APK staged to `dist/` on CI.
- Red-green notes: 2 missing cross-package imports, 2 missing test imports,
  1 wrong-package test import — all compile-time, fixed without touching
  behavior.

### Remaining limitations

- No live-Google-account verification; no TECNO CAMON 40 run; no emulator run.
- Wallpaper chooser symptom unreproduced: if seen again, capture logcat lines
  for `ActivityManager` force-stop/kill, `WallpaperService` exceptions, and
  the default-Home resolver around the apply timestamp.
- No month grid anywhere (by design); no calendar write; no widget hosting.

---

## 0.4.0 — Three pages, focus timer, setup and permission center

Tree state: versionName 0.4.0 / versionCode 11 (`app/build.gradle`).
Default Home is now Today / Plan & Focus / Essentials; existing installs gain
the Plan page once, after Today, without touching order, pins, or names.

### What shipped

- **Today action**: one completable headline (`TodayActionStore`), inline
  edit with VM-held draft, blank refused inline, persisted, never auto-cleared.
- **Plan & Focus page** (`PageType.PLAN`): agenda timeline (max three nearest
  from Instances-based calendar occurrences + due local tasks, "In progress"
  status, Today/Tomorrow/date labels, slim indicator, tap-to-details) with
  loading/error/stale states, plus the focus timer.
- **Google calendar source**: account metadata (`ACCOUNT_NAME`/`ACCOUNT_TYPE`)
  with `isGoogle` detection, per-account grouping, Google-only filter that
  never mixes local calendars back in, setup-step explainer with calendar
  picker, no-Google empty state. No Google API/OAuth in the tree — provider
  sync only, documented as the deliberate choice.
- **Selection algorithm** (pure, tested): exclude ended, include in-progress,
  sort by start, cap three, fill from future dates, all-day + midnight-span +
  recurring via provider expansion, (event, start) dedup, conservative 1-hour
  grace for missing end times, never synthetic.
- **Refresh without polling**: staleness-gated refresh on page visibility
  (60 s), provider `ContentObserver` (start/stop-bound), explicit Refresh/Retry,
  failures keep last results marked stale, source line shows device-calendar
  update time (never claimed live from Google).
- **Focus timer**: timestamp-based start/pause/resume/finish/break
  (`FocusSession` pure math, `FocusStore` persistence + history), 1-second UI
  tick only while running, natural-end auto-complete exactly once, death
  recovery via `recover()` on read, next-event overlap warning (warn only,
  never interrupt), start-from-task, per-session durations with persisted
  defaults, history with clear.
- **Calendar repository on Instances**: recurring/modified/deleted instances
  and multi-day spans resolved by the provider, Events-table fallback,
  (event, start) dedup.
- **Setup + Permission Center**: skippable first-run checklist with live OS
  status (default Home, calendar, essentials, focus defaults, wallpaper,
  pause) and a permissions screen re-checking truth on every entry
  (calendar, usage, wallpaper, default Home).
- **Pause reminder note**: user-written line in the pause dialog.
- Essentials empty state gained an "Open All apps" action; Launch renamed
  Essentials for fresh installs.

### Decisions recorded

- Highlights are dateless by design: no midnight wipe exists, edits are
  explicit, and history is the archive list — nothing is silently overwritten.
- No calendar write, no AppWidgetHost, no AccessibilityService, no blocking
  claims; each documented where it would live.

### Files changed (vs 0.3.3)

New: `data/TodayActionStore.kt`, `core/FocusSession.kt`, `data/FocusStore.kt`,
`core/UpcomingMerge.kt`, `ui/PlanFocusPage.kt`, `ui/SetupScreens.kt`,
`core/FocusSessionTest.kt`, `core/UpcomingMergeTest.kt`,
`data/TodayActionStoreTest.kt`, `data/FocusStorePersistenceTest.kt`,
`ui/FocusStateTest.kt`.

Changed: `platform/CalendarRepository.kt` (Instances + fallback + event URI +
account columns), `core/CalendarWeek.kt` (+`coversDate`), `core/UpcomingMerge.kt`
(status/day-label/staleness helpers), `core/HomeLayout.kt` (`PLAN`,
new DEFAULT), `data/SettingsStore.kt` (PLAN migration, pause message, focus
defaults, Google-only flag), `ui/LauncherViewModel.kt` (today action, focus,
setup/permissions nav, event URI, focus-from-task, Google filter, loading /
error / last-refresh, staleness refresh, provider observer),
`LauncherActivity.kt` (SETUP/PERMISSIONS), `ui/HomeScreen.kt` (PLAN branch,
Essentials action, visible-page refresh), `ui/TodayDashboard.kt`
(action section, merged Upcoming), `ui/CalendarScreen.kt` (overlap matching,
account grouping, Google-only toggle, source/loading/error lines),
`ui/PlanFocusPage.kt` (header, timeline, states), `ui/HighlightOverlays.kt`
(Focus action), `ui/Components.kt` (pause message), `ui/SettingsScreen.kt`
(pause note, setup/permissions rows), tests for the new DEFAULT and
migration, `app/build.gradle` (0.4.0/11), README (three pages, pointer).

### Build and test results

- `:app:testDebugUnitTest` BUILD SUCCESSFUL, 209 tests, 0 failures (203 carried
  over + 6 new: 4 upcoming-algorithm/date-span/staleness, 2 Google-only filter).
- `:app:lintDebug` run; `abortOnError false` per project config.
- `:app:assembleDebug` BUILD SUCCESSFUL (local). APK staged to `dist/` on CI.
- A red-green pass is recorded honestly: the suite first failed on 4 missing
  cross-package imports, 1 wrong test import, 2 stale expectations (1970-based
  session recovered by design; repaired layout now gains the plan page), and
  the fixes touched tests and imports only where the behavior was already
  correct.

### Verification status

- TECNO CAMON 40: NOT verified (no device access).
- Other devices/emulators: NOT verified.
- Verified: unit tests, lint, assemble (local + CI). Manual checklists in
  `calendar-permission-root-cause.md`, `lock-screen-root-cause.md`,
  `compatibility-matrix.md`.

---

## 0.3.3 — Optional usage summary, Focus links, two more templates

Tree state: versionName 0.3.3 / versionCode 10 (`app/build.gradle`).
Completes the remaining P2 items: supported system-settings integrations,
wellbeing/focus features behind explicit checks, and extra templates.

### What shipped

- **Usage summary (optional, numbers only)**: `platform/UsageRepository.kt`
  (guarded `UsageStatsManager` reads, empty without access), `core/UsageFormat`
  readouts, VM explicit-load flows, Settings section with access explainer,
  Load/Refresh, top-8 labeled rows, and revoke path. Manifest gains
  `PACKAGE_USAGE_STATS` (special access only — granted in system Settings,
  never requested in-app). No polling, no background work, no enforcement.
- **Indirect Focus/Wellbeing links**: usage-access deep-link
  (`ACTION_USAGE_ACCESS_SETTINGS`) + system Settings entry with guidance
  (stock Android exposes no Focus Mode deep-link; documented instead of hacked).
- **Templates 11–12**: Charcoal (solid) + Horizon (dark hairline); grayscale
  invariant still enforced by test.
- Focus page copy updated (usage now exists as an opt-in Settings feature).

### Files changed (vs 0.3.2)

New: `model/AppUsage.kt`, `core/UsageFormat.kt`,
`platform/UsageRepository.kt`, `core/UsageFormatTest.kt`,
`platform/UsageRepositoryTest.kt`.

Changed: `AndroidManifest.xml` (+`PACKAGE_USAGE_STATS`, documented),
`ui/LauncherViewModel.kt` (usage flows), `ui/SettingsScreen.kt` (usage
section + access re-check on entry), `ui/HomeScreen.kt` (Focus copy),
`core/WallpaperTemplate.kt` (+2), `core/WallpaperTemplatesTest.kt`
(12/6/6), `data/TaskStoreCodecTest.kt` (notes/due cases),
`ui/LauncherUiTest.kt` (Focus copy), `docs/digital-wellbeing-roadmap.md`
(shipped checklist), `docs/wallpaper-and-lock-screen.md` (12 templates),
`app/build.gradle` (0.3.3/10), README (pointer + privacy + feature copy).

### Build and test results

- `:app:testDebugUnitTest` BUILD SUCCESSFUL, 189 tests, 0 failures (185 carried
  over + 4 new: 2 usage-format, 2 usage-guard).
- `:app:lintDebug` run; `abortOnError false` per project config.
- `:app:assembleDebug` BUILD SUCCESSFUL (local). APK staged to `dist/` on CI.
- Test-environment notes: Robolectric's AppOps shadow default-allows the usage
  op, so the denied path is asserted as the guarded empty answer rather than a
  staged denial; the usage load test waits boundedly because the query runs on
  IO while the main looper is pumped manually.

### Verification status

- TECNO CAMON 40: NOT verified (no device access).
- Other devices/emulators: NOT verified.
- Verified: 189 unit tests, lint, assemble (local + CI). Manual checklists in
  `calendar-permission-root-cause.md`, `lock-screen-root-cause.md`,
  `compatibility-matrix.md`.

---

## 0.3.2 — Calendar permission loop fix, Back audit, universal compatibility docs

Tree state: versionName 0.3.2 / versionCode 9 (`app/build.gradle`).
Method: grep audit + `testDebugUnitTest` + `lintDebug` + `assembleDebug`
locally, plus CI Build APK. No physical-device run claimed (including no
TECNO CAMON 40 run — explicitly unverified, checklist below).

### P0 root causes (all traced in source, not guessed)

1. **Repeated calendar permission.** Only one `launch(READ_CALENDAR)` exists
   (`CalendarAccessAction`), tap-only — no auto-request on composition,
   resume, or re-entry. Real defects: denial recorded nothing (re-tap looped
   into an auto-denying dead dialog after "don't ask again") and permission
   was never re-checked on resume (external grant left stale UI). Fixed with
   persisted `settings.calendarAsked` + Settings-page routing for permanent
   denial + resume re-check in `refreshHomeState()`. See
   `docs/calendar-permission-root-cause.md`.
2. **Back slide.** No screen-transition animation exists in the tree
   (`AnimatedContent`/`Crossfade`/`NavHost`: zero hits); only pager flings
   and genuine programmatic moves (dot tap, add page, Focus shortcut,
   `reduceMotion` → instant). Plain Home enables no `BackHandler`, so Back
   falls through with nothing to animate. Ownership table + evidence in
   `docs/back-navigation-audit.md`.
3. **Wallpaper false success.** Fixed in 0.3.1 (return-ID gating, capability
   probe, double-apply guard); capability table added in
   `docs/wallpaper-capability-audit.md`.
4. **Data loss scan.** Editor saves are id-keyed (edit never duplicates),
   archive/delete filter by id, drafts survive rotation; no midnight wipe
   anywhere. No new defects found.

### Files changed (vs 0.3.1)

New: `ui/CalendarPermission.kt`, `ui/CalendarPermissionTest.kt`, docs
`calendar-permission-root-cause.md`, `back-navigation-audit.md`,
`wallpaper-capability-audit.md`, `compatibility-matrix.md`,
`android-system-capability-matrix.md`, `digital-wellbeing-roadmap.md`.

Changed: `data/SettingsStore.kt` (+`calendarAsked`), `ui/LauncherViewModel.kt`
(`onCalendarPermissionResult`, `setCalendarAsked`, resume re-check),
`ui/TodayDashboard.kt` + `ui/CalendarScreen.kt` (shared permission action,
dead launchers removed), `app/build.gradle` (0.3.2/9), README (0.3.2 pointer
+ doc table).

### Build and test results (0.3.2)

- `:app:testDebugUnitTest` BUILD SUCCESSFUL, 185 tests, 0 failures (180 carried
  over + 5 new calendar-permission regression tests).
- `:app:lintDebug` run; `abortOnError false` per project config.
- `:app:assembleDebug` BUILD SUCCESSFUL (local). APK staged to `dist/` on CI.

### Verification status (deliverables 5–7)

- TECNO CAMON 40: NOT verified (no device access).
- Other devices/emulators: NOT verified.
- Verified: 180+ unit tests incl. 5 new permission regression tests, lint,
  assemble (local + CI). Manual device checklists live in
  `calendar-permission-root-cause.md`, `lock-screen-root-cause.md`, and
  `compatibility-matrix.md`.
- Permissions/setup for the user: calendar Allow on first use (or Settings
  after permanent denial); wallpaper needs nothing; default-Home via system
  picker as before.

### Remaining risks / next

OEM edge-back vs pager feel, `FLAG_LOCK` honoring per ROM, provider quirks
(all guarded to empty + message). Next: Focus Mode shortcuts (Indirect, no
permission), then optional local usage summary behind special access.

---

## 0.3.1 — Lock-screen diagnosis, full-screen editors, task details

Tree state: versionName 0.3.1 / versionCode 8 (`app/build.gradle`).
Method: source trace + `testDebugUnitTest` + `lintDebug` + `assembleDebug` locally,
plus CI Build APK. No physical-device run claimed.

### Root cause (wallpaper)

The direct-apply path was structurally sound (button → VM → `WallpaperApplier`
→ correct `FLAG_SYSTEM`/`FLAG_LOCK`, messages only after the call). The audit
found and fixed three real defects: (1) `setBitmap`'s returned wallpaper ID
was ignored, so a non-positive ID could read as success — now gated by
`isConfirmed(id) = id > 0`; (2) no pre-write capability checks — `probe()`
now reads `isWallpaperSupported()` / `isSetWallpaperAllowed()` first with
plain per-case messages and no new permissions; (3) no in-VM duplicate guard
for the frame before recomposition swaps the button. Full analysis in
`docs/lock-screen-root-cause.md`. No fake lock screen exists or was added.

### Files changed (vs 0.3.0)

New: `platform/WallpaperApplierTest.kt`, `data/HighlightStorePersistenceTest.kt`,
`data/TaskStorePersistenceTest.kt`, `ui/EditorStateTest.kt`,
`ui/Editors.kt` (`HighlightEditorScreen`, `TaskEditorScreen`), docs
`lock-screen-root-cause.md`, `highlights-and-tasks-ux.md`.

Changed: `platform/WallpaperApplier.kt` (capability probe, ID confirmation),
`core/TaskItem.kt` (+`notes`), `data/TaskStore.kt` (notes codec,
`updateDetails`), `ui/LauncherViewModel.kt` (`Screen.HIGHLIGHT_EDITOR` /
`TASK_EDITOR`, VM-held drafts, validation, discard/delete confirms,
double-apply guard, extended Back order), `LauncherActivity.kt` (editor +
confirm handlers, new screen branches), `ui/HighlightOverlays.kt` (lists +
delete confirms; editor moved out), `ui/Editors.kt`, `data/TaskStoreCodecTest.kt`
(notes/due cases), `app/build.gradle` (0.3.1/8).

Kept deliberately: `Highlight.sanitizeAll` (blank titles still dropped at the
storage boundary; the title-optional rule lives in `saveHighlight`, which
derives from the body first line), `renameTask`, list-dialog structure.

### Features implemented

- Full-screen highlight editor: autofocus title (optional), 200dp multiline
  body, primary toggle, Archive/Delete for existing, inline validation that
  keeps the editor open, rotation-safe drafts, discard confirm.
- Full-screen task editor: required title (Done saves), optional due date via
  system picker with Clear, optional notes; Quick Add unchanged.
- Delete confirmations naming the record (highlights + tasks); archive stays
  one-tap (restorable).
- Wallpaper: capability probe, return-ID confirmation, double-apply guard.

### Build and test results

- `:app:testDebugUnitTest` BUILD SUCCESSFUL, 180 tests, 0 failures (160 carried
  over + 20 new: 6 wallpaper diagnostics, 3 highlight persistence, 4 task
  codec/persistence, 7 editor state-machine).
- `:app:lintDebug` run; `abortOnError false` per project config.
- `:app:assembleDebug` BUILD SUCCESSFUL (local). APK at
  `app/build/outputs/apk/debug/app-debug.apk`, staged to `dist/` on CI.
- Regression note: `EditorStateTest` initially failed 6/7 because `state`
  shares `WhileSubscribed` with no collector in plain unit tests (only the
  initial value was ever visible) and the Robolectric main looper is not
  pumped — fixed with an eager collector + `idleMainLooper()` after actions.
  No production code changed for this; the tests now assert the real flow.

### Known issues

- Robolectric shadows stand in for `WallpaperManager`; OEM `FLAG_LOCK`
  behavior unverified without hardware (user checklist in
  `docs/lock-screen-root-cause.md`).
- Editor drafts survive rotation but not process death.
- Due date is date-only; no notifications.

### Physical device testing status

Not performed in this pass. Required user actions: run the wallpaper checklist
per device and confirm editor keyboard behavior with the real IME.

---

## 0.3.0 — Today dashboard, wallpaper gallery, calendar agenda, gesture ownership

Tree state: versionName 0.3.0 / versionCode 7 (`app/build.gradle`), compileSdk 36 / minSdk 26 / targetSdk 36.
Method: source read + `testDebugUnitTest` + `lintDebug` run locally. No device/emulator run claimed.
Unit suite: 160 tests pass (`testDebugUnitTest`, BUILD SUCCESSFUL).

### Files changed (vs 0.2.4)

New: `core/Highlight.kt`, `core/TaskItem.kt`, `core/WallpaperTemplate.kt`,
`core/CalendarWeek.kt`, `data/HighlightStore.kt`, `data/TaskStore.kt`,
`data/WallpaperStore.kt`, `platform/WallpaperApplier.kt`,
`platform/CalendarRepository.kt`, `model/CalendarEvent.kt`,
`ui/TodayDashboard.kt`, `ui/HighlightOverlays.kt`, `ui/CalendarScreen.kt`,
`ui/WallpaperScreen.kt`, docs `wallpaper-and-lock-screen.md`,
`today-dashboard-and-calendar.md`, `interaction-behavior.md`.
New tests: `core/HighlightTest`, `core/TaskItemTest`, `core/CalendarWeekTest`,
`core/WallpaperTemplatesTest`, `data/HighlightStoreCodecTest`,
`data/TaskStoreCodecTest`.

Changed: `AndroidManifest.xml` (+`SET_WALLPAPER` normal, +`READ_CALENDAR` runtime,
both documented), `data/SettingsStore.kt` (+`swipeUpEnabled`, +`pagingEnabled`,
+`reduceMotion`, +`shownCalendarIds`), `ui/LauncherViewModel.kt`
(`Screen.WALLPAPERS/CALENDAR`, TodayBundle combine, highlight/task/calendar/
wallpaper/gesture functions, overlay-aware `back()`), `LauncherActivity.kt`
(per-layer BackHandlers, plain Home claims nothing; new screen branches),
`ui/HomeScreen.kt` (TodayPage → persistent dashboard, swipe-up chrome,
reduce-motion paging, highlight overlays), `ui/AppListScreen.kt` (header
swipe-down, pin state from Home pages not the legacy flat list),
`ui/SettingsScreen.kt` (Today/Wallpapers/Gestures copy, system-picker rename,
privacy copy), `app/build.gradle` (0.3.0/7), tests (`LauncherUiTest`,
`PauseGateTest` updated to the new copy).

### Features implemented

- Today dashboard with persistent Highlights (pin/archive/restore/delete) and
  Tasks (add/toggle/rename/hide-on-Home/delete, done count), plus Upcoming
  from the device calendar with permission-gated empty states.
- Calendar screen with Monday-first week strip, selectable dates, agenda,
  multi-calendar toggles, refresh, and best-effort system-calendar intent.
- Wallpaper gallery (10 grayscale templates, All/Solid/Subtle, selected
  state, Home/Lock mini previews, Home/Lock/Both target, API-reported
  success only, system-picker fallback).
- Back ownership fix (no always-on handler; plain Home falls through),
  swipe-up (bottom bar) / swipe-down (drawer header) with scroll precedence,
  paging + reduce-motion settings.

### Partially implemented / deferred

- Native `AppWidgetHost` hosting (needs bind permission + host lifecycle);
  served as an honest in-launcher agenda instead. `PageType.WIDGET` stays a
  recognized empty state.
- No calendar event creation/editing (read-only is the shipped scope).
- No OEM-verified lock behavior, gesture-edge widths, or provider quirks.

### Build and test results

- `:app:testDebugUnitTest` BUILD SUCCESSFUL, 160 tests, 0 failures.
- `:app:lintDebug` run; `abortOnError false` per project config.
- `:app:assembleDebug` BUILD SUCCESSFUL (local). APK at
  `app/build/outputs/apk/debug/app-debug.apk`, staged to `dist/` on CI.
- Manual device tests still required: Back on Home does nothing; drawer Back
  keeps page; paging vs vertical scroll; highlights/tasks survive restart;
  calendar granted/denied/revoked + empty + multi-event days; wallpaper
  Home/Lock/Both + cancel/fail + picker fallback; large text + small screens.

### Known problems

- Robolectric discovers no installed apps: enumeration, shortcut gating,
  undo timer, and real calendar rows are source-verified only.
- Lock `FLAG_LOCK` honored or ignored per device; combined Both may be
  Partial on some ROMs (reported honestly in UI).
- `dist/` contains a local debug APK; no signed release (signing secrets not
  configured).

---

Tree state (prior): versionName 0.2.4 (`app/build.gradle:23`), compileSdk 36 / minSdk 26 / targetSdk 36 (`app/build.gradle:16-21`).
Method: `git diff` over working tree plus grep over `app/src`. No device/emulator run claimed.
Unit suite: 49 `@Test` methods under `app/src/test/java/com/zendolauncher/ui/` (23 `LauncherUiTest.kt`, 10 `PauseGateTest.kt`, 16 `ScreenshotCaptureTest.kt`).

## Changed this pass

| File | Diff | What |
| --- | --- | --- |
| `app/src/main/java/com/zendolauncher/LauncherActivity.kt` | +15/-1 | BackHandler split SETTINGS/APPS (`:108-114`); `PauseDialog` overlay on `pendingPause` (`:127-135`) |
| `app/src/main/java/com/zendolauncher/data/SettingsStore.kt` | +8 | `distract` + `pauseEnabled` keys read/write (`:38-39,53-54,100-101,114-115`) |
| `app/src/main/java/com/zendolauncher/ui/LauncherViewModel.kt` | +62/-3 | `_pendingPause` (`:123`), `settingsReturn` (`:126`), `drawerIndex/Offset` (`:129-130`), `launch` interpose (`:287-294`), `confirmPause`/`cancelPause` (`:297-303`), `toggleDistract` (`:307-310`), `openSettings(from)` + `backFromSettings` (`:249-258`) |
| `app/src/main/java/com/zendolauncher/ui/AppListScreen.kt` | +19/-2 | `rememberLazyListState` seeded from view model (`:65-68`); `DisposableEffect` saves position (`:145-150`); `openSettings(Screen.APPS)` (`:183`) |
| `app/src/main/java/com/zendolauncher/ui/Components.kt` | +26 | `PauseDialog` composable, Continue/Cancel, no timer (`:353-377`) |
| `app/src/main/java/com/zendolauncher/ui/HomeScreen.kt` | +1/-1 | `openSettings(Screen.HOME)` (`:130`) |
| `app/src/main/java/com/zendolauncher/ui/SettingsScreen.kt` | +31 | Intentional Pause section with honest scope disclaimer (`:196-208`); System settings QuietAction shortcuts incl. Wallpaper -> `Intent.ACTION_SET_WALLPAPER` (`:230-246`) over helper `openSystemSettings(context, action, fallback)` catching `ActivityNotFoundException` (`:319-332`), import at `:3` |
| `app/src/main/res/drawable/wallpaper_mono_dark.xml` | +15 (new) | Repo asset, unreferenced by `app/src/main`: monochrome 360dp x 720dp vector, `#FF0B0B0C` fill + `#FF1F1F21` horizon hairline |
| `app/src/main/res/drawable/wallpaper_mono_light.xml` | +15 (new) | Repo asset, unreferenced by `app/src/main`: monochrome light counterpart vector |
| `app/src/test/java/com/zendolauncher/ui/LauncherUiTest.kt` | +1 | `setPauseEnabled(false)` in setup (`:67`) |
| `app/src/test/java/com/zendolauncher/ui/PauseGateTest.kt` | +182 (new) | 11 Robolectric tests (`:52,68,84,95,106,118,129,138,153,166,176`): gate, cancel, unrestricted, pause-disabled, toggle, empty distract, system-settings render (`:130`), pause dialog render (`:139`), back-from-Settings (`:153`), display intent (`:166`), wallpaper row resolves system picker (`:176`) |

Docs: `android-capability-matrix.md` (24 lines), `android-system-boundaries.md` (67), `privacy-and-permissions-audit.md` (74), `lock-screen-feasibility.md` (87, includes apply instructions + per-OEM lock guidance), `system-behavior-audit.md` (247, prior baseline), this file.

## Supported (verified)

- Single-activity Compose nav HOME/APPS/SETTINGS/ONBOARDING (`LauncherActivity.kt:121-126`); back-to-home from APPS (`:112-114`); back-to-origin from SETTINGS (`:109-111`, `LauncherViewModel.kt:255-258`).
- Discovery via `LauncherApps` + pinned shortcuts (`AppRepository.kt:54`; `ShortcutRepository.kt:52,59`); launch with toast fallback (`AppLauncher.kt:20-29,55-67`).
- Visual-only hide + 6s undo (`LauncherViewModel.kt:317-339`); second hide replaces note (`:320-324`).
- Pause interpose: distract-listed + flag on opens dialog (`LauncherViewModel.kt:287-294`); Continue launches once (`:297-300`); Cancel drops (`:303`).
- Drawer scroll retained across Settings re-entry (`AppListScreen.kt:65-68,145-150` plus `drawerIndex/Offset`).
- Local-only prefs (`SettingsStore.kt:19-22`); backup disabled (`AndroidManifest.xml:19-21`).
- Search ranked accent-insensitive (`AppQuery.kt:39-59`); favourites keep order, dead keys dropped (`:34-37`).
- Onboarding one page, Back dismisses (`OnboardingScreen.kt:39`); re-openable from Settings (`SettingsScreen.kt:263`).
- `isLoading` true until first enumeration finishes, `finally` clears (`LauncherViewModel.kt:107,231-240`); drawer shows "Looking for apps…" (`AppListScreen.kt:137`).
- Honest System settings shortcuts (`SettingsScreen.kt:224-246`): Internet & Wi-Fi opens `Settings.Panel.ACTION_INTERNET_CONNECTIVITY` on API >= Q else `Settings.ACTION_WIFI_SETTINGS` (`:230-237`); Display & lock screen opens `Settings.ACTION_DISPLAY_SETTINGS` (`:238-240`); Sound & notifications opens `Settings.ACTION_SOUND_SETTINGS` (`:241-243`); Wallpaper opens `Intent.ACTION_SET_WALLPAPER` (`:244-246`). Helper retries the fallback action then toasts "Settings page unavailable" (`:319-332`); every dispatch carries `FLAG_ACTIVITY_NEW_TASK`.
- Bundled monochrome wallpapers are repo assets, not shipped UI (`app/src/main/res/drawable/wallpaper_mono_dark.xml`, `wallpaper_mono_light.xml`, 15 lines each): no `app/src/main` code loads them, so they must be rendered/exported and chosen in the system picker by hand. Application steps and per-OEM notification guidance live in `docs/lock-screen-feasibility.md:62-72`. No `WallpaperManager` call and no `SET_WALLPAPER` permission: applying stays with the system picker, which the Wallpaper row opens.

## Corrected against prior audit claims

- `goHome` still wipes query (`LauncherViewModel.kt:260-264`): retained, not fixed. In-drawer clear uses `clearQuery()` (`:281`).
- `LazyListState` now present (`AppListScreen.kt:65-68,151`): prior no-state defect fixed by this pass.
- No screen animation still true: comments state no animation library (`AppListScreen.kt:63,143`); instant swap.
- Drawer-entry keyboard pop is deliberate autofocus (`AppListScreen.kt:59-61` `LaunchedEffect(Unit)` + `requestFocus`): by design, not defect.
- Back-from-Settings keeps query via `settingsReturn` (`LauncherViewModel.kt:249-258`); `goHome` resets it (`:262`).

## Unsupported / Deferred (verified absent)

Recents control beyond self-exclusion; shade/QS tiles; lock screen; connectivity toggles; app suspend/block; usage stats; Custom Tabs. Full per-surface table in `docs/android-system-boundaries.md`.
System settings links are now implemented as honest system-page dispatchers (`SettingsScreen.kt:230-246`) rather than duplicated toggles; `Settings.ACTION_HOME_SETTINGS` remains the default-home link (`SettingsScreen.kt:142-143`, `OnboardingScreen.kt:95`). Bluetooth and Digital Wellbeing settings links remain absent. Wallpaper application remains OS-owned with no `WallpaperManager` calls and no `SET_WALLPAPER` permission (`LauncherActivity.kt:91-101`); the Wallpaper row only opens `Intent.ACTION_SET_WALLPAPER`, and the two bundled vectors (`app/src/main/res/drawable/wallpaper_mono_{dark,light}.xml`) are repository assets for manual use, not applied by the app.
Pause is launcher-level pre-launch only, Chrome URLs not intercepted (`SettingsScreen.kt:204-208`).

## Limitations

- Static audit + uncommitted diff only. CI (`.github/workflows/build-apk.yml`: JDK 21, `testDebugUnitTest`, `lintDebug`) not re-run in this pass.
- 49 unit tests counted by `@Test` annotations; the assertion "49 pass" is a source count, not a recorded run in this pass.
- Robolectric returns no installed apps; enumeration, shortcut gating, undo timer unverified at runtime.
- OEM behavior, managed profiles, API 26 vs 36 bars unverified without device.
- Pause dialog visuals (monochrome, spacing) asserted in code only, no screenshot taken.
- System settings shortcuts asserted only as an intent action via Robolectric shadow (`PauseGateTest.kt:167-181`); no real Settings activity launched.
- Bundled wallpaper vectors (`app/src/main/res/drawable/wallpaper_mono_{dark,light}.xml`) are repo assets for manual rendering, not loaded by any `app/src/main` code; visual fit on real devices unverified. The Wallpaper row dispatches `Intent.ACTION_SET_WALLPAPER` and asserts nothing about the assets.

## Manual tests still required (not claimed passing)

- Pause on/off x listed/unlisted app; Continue launches once; Cancel launches nothing; dismiss returns to list.
- Back Settings-to-drawer keeps query and scroll; Back drawer-to-home; Home wipes query.
- Drawer re-entry restores scroll position after Settings detour.
- Drawer keyboard/focus on entry and re-entry; Search IME action hides keyboard (`AppListScreen.kt:96-104`).
- Undo-hide 6s timer expiry; second hide replaces note.
- Set-as-default flow via both `ACTION_HOME_SETTINGS` intents; pinned shortcuts default vs not.
- Each System settings row on a real device, including the pre-Q Wi-Fi fallback and the `ActivityNotFoundException` toast path.
- `excludeFromRecents` + `singleTask` return-to-home.
- Airplane-mode full run; zero permission dialogs; no egress.

## Release containing this audit

0.2.3 (versionCode 5) is the release that contains the **earlier** audit described in the
sections above. Its APK SHA-256 is
`57dfa7a13d910fcf014fa97fa4cdc005f272908d9312de23329fbb5c60cca0f2`. That build is
**NOT device-validated** — every runtime claim above remains unverified on hardware, and the
manual tests listed in the previous section are still outstanding.

## Release containing the multi-page Home work

The persistent multi-page Home described in `docs/implementation-plan.md` ships in
**`debug-0.2.4`, `versionCode 6`** (`versionName 0.2.4`). Its APK SHA-256 is
`30b40e93df1db20023127e993bf319a081bd3358531853ec2cbf1a5a8717ed6b`, size 11,803,510 bytes.
It is a debug-key build published as a pre-release, and it installs **in place over
`debug-0.2.3` as an upgrade, preserving existing data** — the debug key is stable across
builds and `versionCode` was raised from 5 to 6, which is what makes Android accept the
update rather than refuse it. The Home-layout migration is non-destructive: the legacy flat
`favourites` key is read during migration and left on disk, so a downgrade to 0.2.3 still
finds it.

**Nothing in this feature has been validated on real hardware.** There is no device or
emulator run behind any of it: the pager, the edit mode, the persistence and the migration
are supported by source inspection and unit tests only. Every runtime and OEM-specific
claim — gesture-edge behaviour under gesture navigation, install-over-upgrade on a real
device, appearance under a light or dark system theme — remains unverified. Treat 0.2.4 as
a test build, which is what the `debug-*` pre-release marking already says.

### What the implementation actually reaches

Partly reachable, and less than `docs/implementation-plan.md` originally described. See
§10 of that document for the itemised list. In short: the pager, tap-to-switch page
indicator, persisted active page, edit mode (rename / up / down / remove / add), the Today
page with honest empty states, the Launch page with drag-reorder and pin/unpin, and the
Focus page's live note field all work. `PageType.WIDGET` is unreachable
(`SettingsStore.setPageType` has zero callers), a page's type and heading cannot be edited
(`updatePageConfig` has no production caller), and `PageConfig.showClock` is stored but
never read by `ui/`. `PageConfig.columns`, `PageMove`, `activeAppsPageId`,
`LauncherViewModel.movePageItem` and `SettingsStore.HOME_PAGES_VERSION` have no callers at
all. `ui/SettingsScreen.kt` also still carries stale copy — it places the Today block
"between the date and your pinned apps" and calls the pinned section "Essentials", when the
pinned list now lives on the Launch page and is edited by dragging in Home.
