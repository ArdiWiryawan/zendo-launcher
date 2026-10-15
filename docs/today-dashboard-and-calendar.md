# Today Dashboard and Calendar

Scope: versionName 0.3.0. Method: source read + unit tests (`HighlightTest`,
`TaskItemTest`, `CalendarWeekTest`, codec tests, `LauncherUiTest`).

## Layout

`ui/HomeScreen.kt` `TodayPage`: clock (fitted via `clockSizeSp`), date
`EEEE, d MMMM`, title "Today.", hairline, then `ui/TodayDashboard.kt`
`TodayDashboard` when `settings.todayEnabled`, else the hidden note.
One coherent page with hairlines; no cards, no shadows. Sections:
Highlights → hairline → Tasks → hairline → Upcoming. Focus shortcut stays
below when a NOTES page exists.

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
account ("addr@gmail.com · Google" vs "On this device") and offers
"Google calendars only": when on, the agenda query filters to Google ids,
and an explicit empty Google set yields an empty agenda rather than silently
mixing local calendars back in (`effectiveCalendarIds`, unit-tested). One
Google account needs no extra setup beyond the toggle.

**Upcoming algorithm** (`core/UpcomingMerge.next`, pure and unit-tested):
exclude ended (via conservative `effectiveEnd`: missing/zero end = start +
1 h, never indefinite), include in-progress, sort by start, cap at three,
fill short todays from future dates, all-day handled, deduped by
(event, start) at the query layer. Tasks join only when undone, visible, and
due-dated; undated tasks stay in the task list. Sources stay labeled
("In progress", "Today"/"Tomorrow"/"d MMM", "Task"); nothing synthetic ever
fills a slot.

Permission: `<uses-permission android:name="android.permission.READ_CALENDAR" />`
(runtime). Requested only from the explicit Allow tap in the one shared
`CalendarAccessAction` (Upcoming + Calendar screen), with the sentence "The
launcher reads your device calendar only, offline. Nothing is uploaded."
Denied/revoked → calm off-state + Allow/Settings action; events list stays
empty (never crashes; queries wrapped in `runCatching`).

Selection: `settings.shownCalendarIds` (empty = all), grouped by account with
Google accounts tagged, plus `settings.calendarGoogleOnly` (empty Google set
reads as empty, never as "all mixed back in"). First narrowing expands
empty→all before removing. All-shown collapses back to empty.

Formatting: local zone, `HH:mm` times, duration minutes, all-day rows show
"All-day". Long titles wrap; no clipping path beyond the existing gutter.

## Refresh and sync

- Agenda refreshes when the Plan & Focus page settles into view **only if the
  cache is older than a minute** (`calendarStale`, pure); swiping past does
  not hammer the provider.
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

- Compact (Home Upcoming): next 3 events with `endMillis >= now`, time + title
  + duration, or the off/empty states, + "Open calendar".
- Medium/Expanded (Calendar screen `ui/CalendarScreen.kt`): month label
  `MMMM yyyy` uppercase, "Your week" Monday-first strip (`core/CalendarWeek.kt`
  `weekFor`) with day letters, 36dp touch circles (selected = primary fill +
  background text, `stateDescription` Selected/Today), event dots, "today"
  caption under unselected today, then `TODAY · N EVENTS` (or date + count)
  agenda, Refresh + Open system calendar (VIEW `CalendarContract.CONTENT_URI`
  time URI with generic event VIEW fallback), calendar toggles.
- Full: the Calendar screen itself is the agenda; tapping a strip date calls
  `viewModel.selectDate` and re-queries that week. No month grid is forced
  into Home.

Tapping Upcoming/Home "Open calendar" calls `viewModel.openCalendar()`
(`Screen.CALENDAR`); Back returns Home preserving the pager page
(`settings.home.activePageId` untouched). The system-calendar intent is a
best-effort VIEW; failure is silent (no dead button: the in-app agenda
already shows the data).

Deferred honestly: native third-party widget hosting (`AppWidgetHost`,
bind permission, host lifecycle). `PageType.WIDGET` remains a recognized
empty state, not hosting. No static imitation is labeled native support.
