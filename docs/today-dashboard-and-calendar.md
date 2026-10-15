# Today Dashboard and Calendar

Scope: versionName 0.5.0 (first written for 0.3.0; kept current since). Method:
source read + unit tests (`HighlightTest`, `TaskItemTest`, `CalendarWeekTest`,
`CalendarCacheTest`, `CalendarPermissionTest`, codec tests, `LauncherUiTest`).

## Layout

`ui/HomeScreen.kt` `TodayPage`: clock (fitted via `clockSizeSp`), date
`EEEE, d MMMM`, title "Today.", hairline, then `ui/TodayDashboard.kt`
`TodayDashboard` when `settings.todayEnabled`, else the hidden note.
One coherent page with hairlines; no cards, no shadows. Sections:
Today action → hairline → Highlights → hairline → Tasks → hairline → a small
"History" line. Upcoming no longer lives here: the Plan page shows the full
agenda for one chosen day. The Focus shortcut was removed with the focus timer
in 0.5.0; the NOTES page keeps its persistent note only.

## Highlights

Model: `core/Highlight.kt` — id, title, note, createdAt, updatedAt,
isPrimary, archived. `sanitizeAll` drops blank titles and duplicate ids and
enforces exactly one primary among unarchived.

Store: `data/HighlightStore.kt` (`launcher_highlights` prefs, JSON array via
org.json, `StateFlow`). Separate file so highlight edits never rewrite
settings. `upsert` (create/edit), `pinAsPrimary`, `archive`, `delete`.
Persists across restart (SharedPreferences); never auto-erased.

UI:

- Home shows the primary highlight (title primary tone, note secondary) with
  Edit + All highlights. Empty: "Write your priority here. It stays until you
  edit or archive it." + Add highlight.
- Editor: `ui/HighlightOverlays.kt` dialog with Title field, Supporting note
  field, "Pin as primary highlight" toggle, Save / Cancel / Archive (existing
  only). Back dismisses via `LauncherActivity` handler before anything else.
- All highlights: dialog listing visible (Pin/Edit/Archive/Delete, "Primary"
  marker) + Archived section (Restore/Delete) + New / Close.
- Tests: `HighlightTest` (sanitize rules), `HighlightStoreCodecTest`
  (encode/decode round trip, malformed → empty), UI tests assert the new empty
  copy.

## Tasks

Model: `core/TaskItem.kt` — id, title, done, createdAt, updatedAt,
dueDateMillis (reserved, nullable), showOnHome. `sanitizeAll` drops blanks,
dedupes, sorts undone-first then by creation. Helpers `homeSummary(limit=3)`,
`doneCount`, `visibleCount`.

Store: `data/TaskStore.kt` (`launcher_tasks` prefs, same JSON/Flow pattern).
`add`, `toggleDone`, `rename`, `delete`, `setShowOnHome`. No guilt copy, no
streaks, no auto-creation from calendar events.

UI:

- Home header: "Tasks" + "done/visible" count. Summary: `homeSummary(3)`
  with ○/● markers (secondary tone), tap toggles (Role.Checkbox +
  contentDescription "Check/Uncheck …"), done rendered secondary +
  strikethrough. "+ N more" when truncated. Inline add field ("+ Add task"
  hint, "Add" commits, "All tasks" otherwise).
- All tasks: dialog with done/total, per-row Check/Uncheck, Edit (inline
  field + Save/Cancel), Show/Hide on Home, Delete, hairlines, add field,
  Close. Back closes.
- Tests: `TaskItemTest`, `TaskStoreCodecTest`, UI empty copy
  "No tasks yet. Add the first one below."

## Calendar data source

`platform/CalendarRepository.kt`: read-only against the device provider.
**Primary source is the Instances table** — expanded occurrences, so recurring
series, modified and deleted instances, and multi-day spans resolve the way
the provider resolves them. An Events-table fallback covers devices where
Instances is unavailable. No network; uses the local provider only.
`hasPermission` gates everything; without permission both sources return
empty. No Google API, OAuth, or client secret anywhere in the tree (verified
by grep: no `com.google.android.gms.auth`, no credential store) — Google
calendars arrive exclusively through the device provider's sync, which keeps
the permission footprint at read-only `READ_CALENDAR`.

**Google identification and selection.** `listCalendars` reads `ACCOUNT_NAME`
and `ACCOUNT_TYPE`; `DeviceCalendar.isGoogle` is true only for
`accountType == "com.google"`. The Calendar screen groups calendars by
account ("addr@gmail.com · Google" vs "On this device"). Since 0.5.0 the
selection is **explicit**: `settings.shownCalendarIds` empty means *none
chosen*, never *all* — nothing is read until the user picks calendars one by
one, and the choice is remembered. The old "Google calendars only" toggle was
removed; `calendarGoogleOnly` survives only as a migration input: until
`calendarSelectionSchema` is true, `effectiveCalendarIds` still intersects it,
and the one-time `settleCalendarSelection()` materializes the previous view
(all calendars, or Google ids under the legacy flag) when the first calendar
list becomes readable.

**Plan agenda** (`ui/PlanPage.kt`): compact prev/next date navigation and the
full chronological agenda for the selected day — every event, not a capped
next-three. Rows show `HH:mm` start/end or an "All-day" label, duration, and an
"In progress" marker for an event happening now on today (ticking per minute).
States are distinct and honest: no permission, no calendars on the device,
none chosen, sync-off warning, no events, and an error that keeps stale results
with a Retry. `core/UpcomingMerge` was removed with the old merged view; its
staleness rule lives on as `core/CalendarCache`.

Permission: `<uses-permission android:name="android.permission.READ_CALENDAR" />`
(runtime). Requested only from the explicit Allow tap in the one shared
`CalendarAccessAction` (Plan page + Calendar screen), with the sentence "The
launcher reads your device calendar only, offline. Nothing is uploaded."
Denied/revoked → calm off-state + Allow/Settings action; events list stays
empty (never crashes; queries wrapped in `runCatching`).

Selection display: toggles grouped by account, each checked only when its id is
in `shownCalendarIds`; an explicit "No calendars chosen yet" line sits with the
list and in the agenda's empty state, with a route to the Calendar screen from
the Plan page.

Sync and diagnostics: calendars carry the provider's `SYNC_EVENTS` flag
(defaulting to syncing when the column is absent, so no false warnings); when
visible calendars exist but none sync, the Calendar screen says sync looks off
and points at account sync. A Diagnostics section reports source, permission,
visible count, reading filter, events in view, and last refresh — counts and
ids only, never titles or locations.

Formatting: local zone, `HH:mm` times, duration minutes, all-day rows show
"All-day". Long titles wrap; no clipping path beyond the existing gutter.

## Refresh and sync

- Agenda refreshes when the Plan page settles into view **only if the cache is
  older than a minute** (`CalendarCache.isStale`, pure); swiping past does not
  hammer the provider.
- A `ContentObserver` on the Instances URI (registered `onStart`, removed
  `onStop`) re-queries once on provider change notifications — no polling, no
  timers, never on the main thread (IO dispatcher).
- Explicit Refresh/Retry actions re-query on demand.
- Failures keep the last valid results beneath a "Showing earlier results."
  line plus the error and Retry; permission loss clears to the off-state.
- Source honesty: the Calendar screen shows "Device calendar · updated HH:mm"
  (or Loading). Sync-disabled or delayed provider data is never claimed live
  from Google's servers.

## Widget layouts and screens

No `AppWidgetHost` (deliberate; documented below). The "widget" is an
in-launcher agenda with three scopes:

- Compact (Plan page): the selected day's full agenda with prev/next date
  navigation, the in-progress marker, and the distinct off/empty/error states,
  + "Open calendar".
- Medium/Expanded (Calendar screen `ui/CalendarScreen.kt`): month label
  `MMMM yyyy` uppercase, "Your week" Monday-first strip (`core/CalendarWeek.kt`
  `weekFor`) with day letters, 36dp touch circles (selected = primary fill +
  background text, `stateDescription` Selected/Today), event dots, "today"
  caption under unselected today, then `TODAY · N EVENTS` (or date + count)
  agenda, Refresh + Open system calendar (VIEW `CalendarContract.CONTENT_URI`
  time URI with generic event VIEW fallback), and the explicit per-calendar
  selection toggles.
- Full: the Calendar screen itself is the agenda; tapping a strip date calls
  `viewModel.selectDate` and re-queries that week. No month grid is forced
  into Home.

Tapping the Plan page's "Open calendar" calls `viewModel.openCalendar()`
(`Screen.CALENDAR`); Back returns Home preserving the pager page
(`settings.home.activePageId` untouched). The system-calendar intent is a
best-effort VIEW; failure is silent (no dead button: the in-app agenda
already shows the data).

Deferred honestly: native third-party widget hosting (`AppWidgetHost`,
bind permission, host lifecycle). `PageType.WIDGET` remains a recognized
empty state, not hosting. No static imitation is labeled native support.
