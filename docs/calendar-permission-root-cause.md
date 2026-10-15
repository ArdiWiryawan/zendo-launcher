# Calendar Permission Root Cause

Scope: versionName 0.3.2 (`app/build.gradle`), minSdk 26 / targetSdk 36.
Method: grep over `app/src/main` for every permission call site + Robolectric
tests. No physical-device run claimed.

## Symptom

After opening the calendar (or returning from it) and pressing Back, the app
seemed to ask for calendar permission again.

## Evidence: what the code actually does

Permission call sites (exhaustive — grep for `RequestPermission`,
`requestPermissions`, `READ_CALENDAR` in `app/src/main`):

| Site | Fires from | Evidence |
| --- | --- | --- |
| `ui/CalendarPermission.kt` `CalendarAccessAction` | explicit "Allow calendar access" tap only | the only `launch(READ_CALENDAR)` in the tree |
| `ui/TodayDashboard.kt` Upcoming off-state | renders `CalendarAccessAction` | no launcher of its own |
| `ui/CalendarScreen.kt` off-state | renders `CalendarAccessAction` | `LaunchedEffect(Unit)` calls `refreshCalendar()` (data query only, never a request) |
| `LauncherViewModel.refreshHomeState()` (onStart/onResume/refresh) | re-checks `hasPermission()` | read-only `checkSelfPermission`, never a dialog |
| `platform/CalendarRepository` | guards both queries with `hasPermission()` | denied → empty list, no throw |

There is **no auto-request** on composition, navigation, resume, or re-entry
anywhere in the tree. The system dialog can only appear from the one shared
tap handler.

## Actual root causes (two, both fixed in 0.3.2)

1. **Dead-dialog loop after permanent denial.** The old callback recorded
   nothing on denial, so every "Allow calendar access" tap re-fired the
   system dialog. After "don't ask again" the platform auto-denies without
   showing anything — same tap, same nothing, indistinguishable from being
   asked again. Fix: `settings.calendarAsked` persists the first answer;
   `asked && !shouldShowRequestPermissionRationale` routes to the app
   Settings page (`ACTION_APPLICATION_DETAILS_SETTINGS`) instead of the dead
   dialog. Both end states (grant there, or leave denied) are calm and final.
2. **Stale off-state after an external grant.** Permission was only re-read
   when entering the Calendar screen, so granting in Settings left Today
   showing "off" until re-entry. Fix: `refreshHomeState()` (called on
   `onStart`/`onResume`/refresh) now also re-checks `hasPermission()` — a
   read, never a request.

## Lifecycle contract (now enforced)

- Request only from the explicit Allow tap, only when `!hasPermission()`.
- Re-check (never request) on resume and on Calendar entry.
- Denial (once or permanent) never loops: first denial re-arms Allow with the
  on-screen explanation; permanent denial offers Settings.
- Revocation mid-run: next resume/query reads denied → off-state with retry.
- Calendar stays optional: Home, drawer, highlights, tasks, and wallpaper all
  work without it; `READ_CALENDAR` is the only calendar permission declared
  (no write access — the feature is read-only).

## Tests

- `ui/CalendarPermissionTest`: denial records the ask without granting;
  grant path performs no dialog; permanent-denial shape renders the Settings
  action and zero Allow actions; first-ask shape renders Allow; flag persists
  across reads.
- Manual checklist (device required, not performed here): deny once → Allow
  still offered with explanation; deny with "don't ask again" → Settings link
  instead of a dialog; grant in Settings → return → Upcoming updates without
  any dialog; revoke in Settings → return → calm off-state; Back from the
  external system calendar → no dialog, same Home page.
