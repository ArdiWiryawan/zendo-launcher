# Zendo Launcher

A calm, standalone Android home screen built to reduce social media addiction and
doom scrolling. It replaces your launcher with a tactile surface, clock, Google
Calendar glance, and a clean list of the apps you actually intend to open.

Built as a real native Android app in Kotlin and Jetpack Compose. It is a companion
to Zendo and works 100% standalone without an account or network connection.

```
14:05
Tuesday, 13 October

Essentials
Phone
Messages
Browser
Zendo
Calendar

All apps   Settings
```

## What it does

- **Replaces the home screen.** Registered for `CATEGORY_HOME`, so Android offers it as a
  Home app and remembers it as the default.
- **Launches your installed apps** through the system `LauncherApps` service.
- **Finds installed web apps** that Android exposes as launcher activities (WebAPKs) or
  as pinned shortcuts published by browsers.
- **Search** across every launchable app, ranked so an exact name beats a prefix beats a
  word start beats a substring, ignoring case and accents. The field takes focus as the
  drawer opens, is set up for app names rather than sentences (no autocorrect, no
  auto-capitalisation), and the Search key hides the keyboard while a Clear action
  empties the query, neither leaving the screen.
- **All apps**, listed alphabetically under sticky letter headers while the query is
  empty. While the first scan of installed apps is still running the drawer says so,
  instead of reporting that no apps were found.
- **Refresh and a count in the drawer.** The bottom row carries **Settings** and
  **Refresh**, which rescans installed apps and shortcuts, and the list is headed by a
  count: "N apps" while the query is empty, **Results** once one is typed.
- **Essentials** — pin apps to the home screen, reorder them, unpin them. In the app list
  every row carries a visible **Pin** action, so the feature does not depend on knowing the
  long press; holding a row still opens the same menu as a shortcut.
- **Two Home experiences: Quiet Home (default) and Calm Canvas.** Starts on a
  single ultra-clean Quiet Home (centered by default, toggleable to left; lowercase
  toggleable) with clock, Google Calendar glance, and clean pinned apps without
  row clutter. Switch to Calm Canvas in Settings for a multi-page layout (Home,
  Plan, Today, Usage, Note).
- **Soft Matte Charcoal & Warm Sage themes + custom wallpapers.** Replaces harsh
  void-black with Soft Matte Charcoal and Warm Sage Paper, paired with three
  bundled procedural wallpapers (*Window shadow*, *Washi linen*, *Morning mist*) or
  your own gallery photo behind an adaptive legibility scrim and optional blur.
- **Mindful Gate for distracting apps.** Replaces direct taps on chosen social apps
  with a breathing delay, real-time today screen-usage mirror, an intention prompt
  (with a gentle pivot when *"Just checking / bored"*), and optional 5m/10m/15m
  local notification check-in reminders.
- **Google Calendar glance & Plan (offline).** Reads Google Calendar synced on the
  device (`READ_CALENDAR`), with 1-tap *Use Google Calendars*, a Google-only toggle,
  and a direct *Open Google Calendar* action.
- **Screen usage awareness (optional).** Opt-in `PACKAGE_USAGE_STATS` reads today's
  foreground duration and app-open counts locally — no charts, no guilt, no streaks.
- **Hide apps** you would rather not see, without uninstalling anything. The hidden list
  names apps by their real label, not by package name, and hiding offers an **Undo** that
  stays on screen for a few seconds.
- **Today, local and persistent** (as an optional page). A completable headline action, a
  highlight that stays until archived, and tasks that keep their state — plus one small
  line into local history.
- **Plan page** (optional). The chosen day's full agenda from the device calendar, with
  previous/next date navigation, an in-progress marker, and honest states for
  permission, no calendars, none chosen, sync-off, empty and error.
- **Local history.** One record per day — the action, the highlight and the
  tasks as they stood — kept on this device, newest first. No charts, no
  streaks. A backup writes the whole local state; clear asks first and touches only
  the past.
- **Calendar agenda.** Monday-first week strip, selectable dates, explicit
  per-calendar selection — nothing is read until you choose — and a
  best-effort system-calendar intent. Read-only and offline.
- **Text only, light or dark.** No app artwork anywhere: a row is a label, a state dot and
  one text action. Two themes, both monochrome, and the window, status bar and navigation
  bar icons follow the theme you chose rather than the system's night setting.
- **Everything is local.** No account, no network permission, no telemetry. Calendar
  permission is requested only when you open a calendar feature, and is read-only.

## Download the APK

The APK is built by GitHub Actions — no Android Studio, no local SDK, nothing to install.

**Latest debug build:** [Releases → all Zendo Launcher builds](https://github.com/ArdiWiryawan/zendo-launcher/releases)
— open the newest `debug-*` release and download `ZendoLauncher-debug.apk` directly, no
unzipping. This link deliberately points at the releases list, not at a version: every
build here is a **prerelease**, and GitHub's `/releases/latest/` form skips prereleases, so
a pinned tag or `/releases/latest/` would go stale (or 404) the moment a new build ships.
The newest build is currently `debug-0.8.1` (versionCode 19). See the releases list
for its sha256.

To build a fresh one yourself:

1. Open the repository's **Actions** tab.
2. Pick **Build APK** → **Run workflow** → variant `debug` → **Run workflow**.
3. When the run finishes, download the artifact `zendo-launcher-debug-<sha>` from the run
   page and unzip it to get `ZendoLauncher-debug.apk`.

Pushing a tag like `v0.5.0` builds a signed release APK and attaches it to a GitHub
Release, once release signing secrets are configured. See
[docs/build-and-release.md](docs/build-and-release.md).

To build locally instead:

```powershell
.\gradlew.bat assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```

## Install it

1. Copy the APK to the phone, or download it directly on the phone.
2. Open the downloaded file.
3. Android will warn that the file comes from an unknown source. Allow installation from
   that source (usually your browser or file manager) when prompted.
4. Install the app.
5. Press Home. Android asks which app should open the home screen — choose **Launcher** —
   or go to **Settings → Apps → Default apps → Home app** and select it.

Setting a default Home app is a per-device choice. This launcher does not, and cannot,
replace your existing home screen setup automatically.

## First run

The first time it opens, the launcher shows a one-page explanation instead of the home
screen. It says what the launcher replaces, how to add apps to Home, that other pages
exist only when you add them, and how to get your previous Home screen back.

The same page is reachable afterwards from **Settings → About → Reopen onboarding**.

To get your previous Home screen back, open **Settings → Apps → Default apps → Home app**
and choose the other launcher. Android owns that setting: this launcher never changes it
on its own, and it can always be changed back.

## Known limitations

- **Pinned shortcuts need to be the default Home app.** Android only hands a launcher the
  pinned shortcuts published by other apps when that launcher is the active home app. Until
  you select it, web shortcuts will not appear; installed WebAPKs still do. The Settings
  screen tells you which state you are in.
- **Not every browser publishes installed web apps as shortcuts.** Browsers that create a
  WebAPK (Chrome) register a real launcher activity and are picked up automatically. A
  browser that keeps a web app inside itself may expose nothing at all. There is no
  supported API to read those, and this launcher deliberately does not use accessibility
  scraping or package-name guessing to fake it. Manually adding a web shortcut is the
  honest future fix.
- **Shortcut labels only.** A pinned shortcut is shown with its label; the launcher never
  reads app or shortcut icons at all, so no icon permission is needed anywhere.
- **No widget hosting, no app-blocking.** The calendar is an in-launcher agenda, not
  hosted third-party widgets. Nothing here claims to block apps system-wide.
  Anything like that would need a real, verifiable mechanism and a transparent permission
  request, so it is not in this version.
- **The Zendo snapshot import is gone.** Records imported by an older build still show in
  History; nothing new can be imported. A local backup file remains the only way data
  moves between installs.

## Privacy

- The launcher does not declare the `INTERNET` permission and never uploads anything.
- `READ_CALENDAR` is requested only when you open a calendar feature and reads synced
  on-device calendars (such as Google Calendar) read-only and offline.
- `PACKAGE_USAGE_STATS` is strictly optional (granted in system Usage Access settings)
  and reads today's local usage summary only when you open Usage or trigger a Mindful
  Gate pause.
- `POST_NOTIFICATIONS` (Android 13+) is requested only if you pick a Mindful Gate
  timebox reminder.
- Your app list is discovered through the system's `LauncherApps` service (`QUERY_ALL_PACKAGES`
  is not used; `<queries>` covers `MAIN`/`LAUNCHER` activities and `com.google.android.calendar`).
- Pinned apps, hidden apps, the Home layout, theme, highlights, tasks, history records and
  the explicit calendar selection are stored in local `SharedPreferences` on the device.
  They are never uploaded, Android's automatic backup is off, and the only way data leaves
  is a backup file you choose to write.
- A backup is a plain local file. The restore previews what would change before writing;
  the save writes only to the location you pick in the system file picker.
- Nothing is sent anywhere. There is no analytics, no account and no remote config.

## Documentation

| Document | Contents |
| --- | --- |
| [docs/opensource-review.md](docs/opensource-review.md) | Licence audit of the launcher projects reviewed before writing any code |
| [docs/design-reference-audit.md](docs/design-reference-audit.md) | Visual references inspected, and the design decisions taken from them |
| [docs/design-system.md](docs/design-system.md) | The design system as built: palette, type scale, spacing, surface treatment, icons, interaction states |
| [docs/ui-ux-audit.md](docs/ui-ux-audit.md) | Source-level audit of the shipped UI: the problems found, and what could not be verified |
| [docs/ui-ux-implementation.md](docs/ui-ux-implementation.md) | What the redesign changed, which finding each change closes, and the before-and-after |
| [docs/ui-ux-verification.md](docs/ui-ux-verification.md) | What was mechanically verified, the regression checklist, and what remains open |
| [docs/ui-ux-review/](docs/ui-ux-review/README.md) | Before-and-after screenshots of the redesign, and what they do and do not show |
| [docs/build-and-release.md](docs/build-and-release.md) | Building, downloading artifacts, signing, troubleshooting |
| [docs/today-dashboard-and-calendar.md](docs/today-dashboard-and-calendar.md) | Highlights/tasks models, calendar source, agenda layouts, permissions |
| [docs/interaction-behavior.md](docs/interaction-behavior.md) | Back, swipe/scroll ownership, paging, drawer, device differences |
| [docs/implementation-report.md](docs/implementation-report.md) | Files changed, test results, known problems |
| [docs/compatibility-matrix.md](docs/compatibility-matrix.md) | API/OEM matrix, permission table, verification log |
| [docs/android-system-capability-matrix.md](docs/android-system-capability-matrix.md) | Direct/Auth/Indirect/Dependent/Denied per phone surface |
| [docs/calendar-permission-root-cause.md](docs/calendar-permission-root-cause.md) | Repeat-prompt root cause, lifecycle contract, tests |
| [docs/back-navigation-audit.md](docs/back-navigation-audit.md) | Every Back path, animation evidence, state preservation |
| [docs/digital-wellbeing-roadmap.md](docs/digital-wellbeing-roadmap.md) | Shipped controls, approach comparison, next priorities |

## Licence

The launcher is original work. No code was copied from any existing launcher — the four
maintained candidates reviewed are GPL-3.0, and the fifth carries no licence at all, so all
five were used as reference only. See [docs/opensource-review.md](docs/opensource-review.md).

The launcher itself is intended to be released under the Apache License 2.0, but no `LICENSE`
file has been added to this repository yet, so no licence is granted at present.

Third-party dependencies are AndroidX and Jetpack Compose (Apache-2.0), the Kotlin standard
library (Apache-2.0), and kotlinx-coroutines (Apache-2.0), which the APK ships transitively
through Compose.
