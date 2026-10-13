# Zendo Launcher

A standalone, monochrome Android home screen. It replaces your launcher with a clock, a
date, a short list of apps you actually use, and — optionally — one compact block from
Zendo. Nothing else.

Built as a real native Android app in Kotlin and Jetpack Compose. It is a separate
product from Zendo and works fully without it.

```
14:05
Tuesday, 13 October

Today
Finish the report
Main action
Draft section 2

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
  word start beats a substring, ignoring case and accents.
- **Essentials** — pin apps to the home screen, reorder them, unpin them.
- **Hide apps** you would rather not see, without uninstalling anything.
- **Optional Today block** sourced from Zendo. Off by default, hideable entirely, and the
  home screen never waits for it.
- **Light and dark**, both monochrome. Text-first by default, optional icon-plus-text.
- **Everything is local.** No account, no network use, no runtime permissions.

## Download the APK

The APK is built by GitHub Actions — no Android Studio, no local SDK, nothing to install.

1. Open the repository's **Actions** tab.
2. Pick **Build APK** → **Run workflow** → variant `debug` → **Run workflow**.
3. When the run finishes, download the artifact `zendo-launcher-debug-<sha>` from the run
   page and unzip it to get `ZendoLauncher-debug.apk`.

Pushing a tag like `v0.1.0` builds a signed release APK and attaches it to a GitHub
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
| [docs/zendo-integration.md](docs/zendo-integration.md) | Verified Zendo data contract, and what a real integration would need |
| [docs/build-and-release.md](docs/build-and-release.md) | Building, downloading artifacts, signing, troubleshooting |

## Licence

The launcher is original work. No code was copied from any existing launcher — the four
maintained candidates reviewed are GPL-3.0, and the fifth carries no licence at all, so all
five were used as reference only. See [docs/opensource-review.md](docs/opensource-review.md).

Third-party dependencies are AndroidX and Jetpack Compose (Apache-2.0) and the Kotlin
standard library (Apache-2.0).
