# Zendo Launcher

A standalone, monochrome Android home screen. It replaces your launcher with a clock, a
date, and a short list of apps you actually use. Nothing else.

Built as a real native Android app in Kotlin and Jetpack Compose. It is a separate
product from Zendo and works fully without it.

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
  auto-capitalisation), and the Search key or a Clear action ends the query without
  leaving the screen.
- **All apps**, listed alphabetically under sticky letter headers while the query is
  empty. While the first scan of installed apps is still running the drawer says so,
  instead of reporting that no apps were found.
- **Essentials** — pin apps to the home screen, reorder them, unpin them. In the app list
  every row carries a visible **Pin** action, so the feature does not depend on knowing the
  long press; holding a row still opens the same menu as a shortcut.
- **Hide apps** you would rather not see, without uninstalling anything. The hidden list
  names apps by their real label, not by package name, and hiding offers an **Undo** that
  stays on screen for a few seconds.
- **A Today block with nothing in it.** The setting exists and the home screen has a place
  for it, but Zendo publishes no planning API, so connecting it fetches nothing and the
  block never appears. See [docs/zendo-integration.md](docs/zendo-integration.md) for the
  verified data contract and the backend change required.
- **Light and dark**, both monochrome. Text-first by default, optional icon-plus-text.
  The window background and the status and navigation bar icons follow the theme you
  chose, not the system's night setting.
- **Everything is local.** No account, no network use, no runtime permissions.

## Download the APK

The APK is built by GitHub Actions — no Android Studio, no local SDK, nothing to install.

**Latest debug build:** [Releases → Zendo Launcher 0.2.2 (debug test build)](https://github.com/ArdiWiryawan/zendo-launcher/releases/tag/debug-0.2.2)
— download `ZendoLauncher-debug.apk` directly, no unzipping.

To build a fresh one yourself:

1. Open the repository's **Actions** tab.
2. Pick **Build APK** → **Run workflow** → variant `debug` → **Run workflow**.
3. When the run finishes, download the artifact `zendo-launcher-debug-<sha>` from the run
   page and unzip it to get `ZendoLauncher-debug.apk`.

Pushing a tag like `v0.2.2` builds a signed release APK and attaches it to a GitHub
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
screen. It says what the launcher replaces, how to choose your essentials from the app
list, that Android owns the Home app setting, how to get your previous Home screen back,
and that Zendo is optional.

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
- **Shortcut labels only.** A pinned shortcut is shown with its label; the launcher does
  not ask for the shortcut icon permission it would need to draw it in the icon layout.
- **No widget support, no app-blocking.** Nothing here claims to block apps system-wide.
  Anything like that would need a real, verifiable mechanism and a transparent permission
  request, so it is not in this version.
- **Zendo integration is a boundary, not a feature yet.** Zendo exposes no planning API
  today, so turning it on does not fetch anything. See
  [docs/zendo-integration.md](docs/zendo-integration.md) for the verified data contract and
  the backend change required.

## Privacy

- The launcher requests **no runtime permissions** and does not declare the `INTERNET`
  permission.
- Your app list is discovered through the system's `LauncherApps` service, which is how
  Android intends launchers to work. `QUERY_ALL_PACKAGES` is not used; a narrow `<queries>`
  entry for `MAIN`/`LAUNCHER` activities is.
- Pinned apps, hidden apps, theme, layout and clock format are stored in local
  `SharedPreferences` on the device. They are never uploaded, and app backups exclude them.
- Nothing is sent anywhere. There is no analytics, no account and no remote config.

## Documentation

| Document | Contents |
| --- | --- |
| [docs/opensource-review.md](docs/opensource-review.md) | Licence audit of the launcher projects reviewed before writing any code |
| [docs/design-reference-audit.md](docs/design-reference-audit.md) | Visual references inspected, and the design decisions taken from them |
| [docs/design-system.md](docs/design-system.md) | The design system as built: palette, type scale, spacing, surface treatment, icons, interaction states |
| [docs/zendo-integration.md](docs/zendo-integration.md) | Verified Zendo data contract, and what a real integration would need |
| [docs/ui-ux-audit.md](docs/ui-ux-audit.md) | Source-level audit of the shipped UI: the problems found, and what could not be verified |
| [docs/ui-ux-implementation.md](docs/ui-ux-implementation.md) | What the redesign changed, which finding each change closes, and the before-and-after |
| [docs/ui-ux-verification.md](docs/ui-ux-verification.md) | What was mechanically verified, the regression checklist, and what remains open |
| [docs/ui-ux-review/](docs/ui-ux-review/README.md) | Before-and-after screenshots of the redesign, and what they do and do not show |
| [docs/build-and-release.md](docs/build-and-release.md) | Building, downloading artifacts, signing, troubleshooting |

## Licence

The launcher is original work. No code was copied from any existing launcher — the four
maintained candidates reviewed are GPL-3.0, and the fifth carries no licence at all, so all
five were used as reference only. See [docs/opensource-review.md](docs/opensource-review.md).

Third-party dependencies are AndroidX and Jetpack Compose (Apache-2.0) and the Kotlin
standard library (Apache-2.0).
