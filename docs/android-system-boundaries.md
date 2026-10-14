# Android System Boundaries

Scope: Zendo Launcher versionName 0.2.2 (`app/build.gradle:23`), minSdk 26 / targetSdk 36 / compileSdk 36 (`app/build.gradle:16-21`).
Method: full read of `app/src/main/AndroidManifest.xml:1-48`; grep over `app/src` for absence claims. No device/emulator run claimed.

## Manifest root

- Zero `<uses-permission>` in `app/src/main/AndroidManifest.xml:1-48`.
- `<queries>` narrowed to MAIN/LAUNCHER intent only (`app/src/main/AndroidManifest.xml:11-16`).
- `QUERY_ALL_PACKAGES` deliberately absent; comment states intent (`app/src/main/AndroidManifest.xml:5-10`).
- Single activity `.LauncherActivity`, exported (`app/src/main/AndroidManifest.xml:29-34`).
- `singleTask` + `clearTaskOnLaunch` + `stateNotNeeded` stateless root (`app/src/main/AndroidManifest.xml:31-36`).
- `excludeFromRecents="true"` hides self only (`app/src/main/AndroidManifest.xml:33`).
- HOME intent-filter MAIN + HOME + DEFAULT + LAUNCHER (`app/src/main/AndroidManifest.xml:39-44`).
- `windowSoftInputMode="adjustResize"` (`app/src/main/AndroidManifest.xml:38`).
- Backup disabled: `allowBackup=false`, `fullBackupContent=false`, `dataExtractionRules` (`app/src/main/AndroidManifest.xml:19-21`).

## Discovery and launch chain

- Discovery per profile via `launcherApps.getActivityList(null, user)` (`app/src/main/java/com/zendolauncher/platform/AppRepository.kt:52-54`).
- Iterates `userManager.userProfiles`; `SecurityException` yields empty list (`app/src/main/java/com/zendolauncher/platform/AppRepository.kt:52-56`).
- Self excluded by package-name check (`app/src/main/java/com/zendolauncher/platform/AppRepository.kt:59`).
- Pinned shortcuts only: `FLAG_MATCH_PINNED` (`app/src/main/java/com/zendolauncher/platform/ShortcutRepository.kt:52`).
- Shortcut `RuntimeException` yields empty, never crash (`app/src/main/java/com/zendolauncher/platform/ShortcutRepository.kt:57-65`).
- Default-home check via `resolveActivity(CATEGORY_HOME, MATCH_DEFAULT_ONLY)` (`app/src/main/java/com/zendolauncher/platform/ShortcutRepository.kt:83-88`).
- Launch via `startMainActivity` (`app/src/main/java/com/zendolauncher/platform/AppLauncher.kt:34`).
- Shortcut start via `startShortcut` (`app/src/main/java/com/zendolauncher/platform/AppLauncher.kt:44`).
- Fallback `getLaunchIntentForPackage` + `FLAG_ACTIVITY_NEW_TASK` (`app/src/main/java/com/zendolauncher/platform/AppLauncher.kt:56-60`).
- Failure toasts, never crashes (`app/src/main/java/com/zendolauncher/platform/AppLauncher.kt:27`).

## Per-surface boundary table

| Surface | Launcher does | System owns | Blocking API absent in tree | Verdict |
| --- | --- | --- | --- | --- |
| Recents / Overview | Hides self via `excludeFromRecents` (`app/src/main/AndroidManifest.xml:33`) | Task list, clearing, gesture nav | No `getAppTasks` / `removeTask` call in tree | Hides self only |
| Notification shade | `systemBarsPadding` only (`app/src/main/java/com/zendolauncher/ui/HomeScreen.kt:61`, `app/src/main/java/com/zendolauncher/ui/AppListScreen.kt:79`) | Shade, notifications, media output | No `NotificationListenerService` in tree | OS owns |
| Quick Settings | None | Tiles, toggles | No `TileService` in manifest or tree | OS owns |
| Lock screen / Keyguard | None; post-unlock HOME only | PIN/pattern/biometric, lock shade | No `KeyguardManager` in tree | OS owns |
| Wallpaper | Solid monochrome surface + `SystemBarIcons` legibility only (`app/src/main/java/com/zendolauncher/LauncherActivity.kt:91-101`); ships two preview vectors (`app/src/main/res/drawable/wallpaper_mono_dark.xml`, `app/src/main/res/drawable/wallpaper_mono_light.xml`) and documents the manual apply path in `docs/lock-screen-feasibility.md:62-72`; never sets wallpaper itself | Picker, live engine | No `WallpaperManager` call in tree | OS owns |
| Connectivity / system shortcuts | Fires system settings pages, never owns toggles: `ACTION_HOME_SETTINGS` (`app/src/main/java/com/zendolauncher/ui/SettingsScreen.kt:142-143`, `app/src/main/java/com/zendolauncher/ui/OnboardingScreen.kt:95`), `Settings.Panel.ACTION_INTERNET_CONNECTIVITY` with `ACTION_WIFI_SETTINGS` fallback (`app/src/main/java/com/zendolauncher/ui/SettingsScreen.kt:230-237`), `ACTION_DISPLAY_SETTINGS` (`:238-240`), `ACTION_SOUND_SETTINGS` (`:241-243`) | WiFi, BT, mobile data, system settings | No `WifiManager` / `ConnectivityManager` / `BluetoothAdapter`; no BT or Digital Wellbeing intent | Opens OS pages |
| Default Home role | Reads resolver, directs to picker (`app/src/main/java/com/zendolauncher/platform/ShortcutRepository.kt:83-88`) | Home selection dialog | No direct set-default API | OS decides |
| App suspend / block | Visual-only hide set (`app/src/main/java/com/zendolauncher/data/SettingsStore.kt:33,68-73`) | Suspend, disable, MDM | No `DevicePolicyManager` / admin in tree | Filters own list |
| Chrome / URLs | Starts launcher target only; no interception (`app/src/main/java/com/zendolauncher/ui/SettingsScreen.kt:204-208`) | Link resolution, Custom Tabs, PWA install | No Custom Tabs / `WebView` in tree | OS owns |
| Pause enforcement | In-activity dialog interpose (`app/src/main/java/com/zendolauncher/ui/LauncherViewModel.kt:287-303`) | Execution, URL dispatch | No stats / accessibility / overlay | Bypassable |

## Keep-absent inventory

Grep over `app/src` returns no hits for execution or policy APIs; only XML namespace `http://schemas.android.com` matched URL grep.
Keep absent: `QUERY_ALL_PACKAGES`, `PACKAGE_USAGE_STATS` / `UsageStatsManager`, `AccessibilityService`, `DevicePolicyManager`, `NotificationListenerService`, `TileService`, `KeyguardManager`, `WallpaperManager`, `WifiManager` / `ConnectivityManager` / `BluetoothAdapter`, `SYSTEM_ALERT_WINDOW` / overlay, `INTERNET` / `OkHttp` / `Retrofit`, Custom Tabs / Trusted Web Activity.
Settings **intents** are the deliberate exception: the tree sends `ACTION_HOME_SETTINGS`, `ACTION_WIFI_SETTINGS`, `ACTION_DISPLAY_SETTINGS`, `ACTION_SOUND_SETTINGS` and `Settings.Panel.ACTION_INTERNET_CONNECTIVITY`, all as `Intent(action)` string dispatches through `openSystemSettings` (`app/src/main/java/com/zendolauncher/ui/SettingsScreen.kt:316-329`). None of them read or mutate state, so no matching manager class enters the tree.
Do not add any without updating this doc and `docs/privacy-and-permissions-audit.md`.

## Limitations

- Static audit only. Managed profiles, work policy, OEM task managers unverified.
- Robolectric returns no installed apps; real enumeration unverified.
- Shortcut readability flips on default-home status; non-default empty by design.
- Edge-to-edge behavior on API 26 vs 36 unverified without device.

## Manual tests still required

- Set-as-default flow via both `ACTION_HOME_SETTINGS` intents; confirm picker shown.
- Each System settings row resolves on a real device: `ACTION_INTERNET_CONNECTIVITY` panel, pre-Q `ACTION_WIFI_SETTINGS` fallback, `ACTION_DISPLAY_SETTINGS`, `ACTION_SOUND_SETTINGS`.
- `ActivityNotFoundException` path shows "Settings page unavailable" and leaves the app alive.
- Applying `wallpaper_mono_dark.xml` / `wallpaper_mono_light.xml` via the system picker, per `docs/lock-screen-feasibility.md:62-72`.
- Pinned-shortcut visibility when default vs not default.
- `excludeFromRecents` + `singleTask` return-to-home after Home press.
- Hidden/paused apps still launch from system search, voice, other launchers.
- Chrome URL taps bypass pause entirely.
- API 26 vs 36 bars; no shade/QS intrusion observed.
- Airplane-mode run; all launcher functions offline.
