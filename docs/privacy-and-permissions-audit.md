# Privacy and Permissions Audit

Scope: versionName 0.2.2 (`app/build.gradle:23`), minSdk 26 / targetSdk 36 (`app/build.gradle:20-21`).
Method: full read `app/src/main/AndroidManifest.xml:1-48`; grep over `app/src` for permission/network/service APIs. No device/emulator run claimed.

## Permissions: zero

- Zero `<uses-permission>` (`app/src/main/AndroidManifest.xml:1-48`). No runtime request, no `INTERNET`.
- `<queries>` narrowed to MAIN/LAUNCHER only (`app/src/main/AndroidManifest.xml:11-16`).
- `QUERY_ALL_PACKAGES` deliberately absent; comment states intent (`app/src/main/AndroidManifest.xml:5-10`).
- Only system intents are `Settings.ACTION_HOME_SETTINGS` (`app/src/main/java/com/zendolauncher/ui/SettingsScreen.kt:139`, `app/src/main/java/com/zendolauncher/ui/OnboardingScreen.kt:95`).
- No write-settings, no connectivity toggle, no install/uninstall request.
- Deps add no silent permission: Compose BOM, core-ktx, activity-compose, lifecycle, junit, Robolectric, Compose UI test only (`app/build.gradle:127-149`). No analytics, crash reporter, ads, network lib.

## Data handling table

| Data | Where | Leaves device | Evidence |
| --- | --- | --- | --- |
| Theme | Prefs key `theme` | No | `app/src/main/java/com/zendolauncher/data/SettingsStore.kt:28-29,45,92` |
| Layout | Prefs key `layout` | No | `app/src/main/java/com/zendolauncher/data/SettingsStore.kt:30-31,46,93` |
| Favourites order | Prefs key `favourites`, newline-joined | No | `app/src/main/java/com/zendolauncher/data/SettingsStore.kt:32,47,94` |
| Hidden set | Prefs key `hidden`, newline-joined | No | `app/src/main/java/com/zendolauncher/data/SettingsStore.kt:33,48,95` |
| Today / clock / zendo / onboarding flags | Prefs booleans | No | `app/src/main/java/com/zendolauncher/data/SettingsStore.kt:34-37,49-52,96-99` |
| Distract set | Prefs string set `distract_set` | No | `app/src/main/java/com/zendolauncher/data/SettingsStore.kt:38,53,100` |
| Pause flag | Prefs key `pause_enabled` | No | `app/src/main/java/com/zendolauncher/data/SettingsStore.kt:39,54,101` |
| App list / labels / icons | In-memory `MutableStateFlow` + `MutableMap` cache | No | `app/src/main/java/com/zendolauncher/platform/AppRepository.kt:24-29,48-71` |
| Pinned shortcuts | In-memory flow only | No | `app/src/main/java/com/zendolauncher/platform/ShortcutRepository.kt:27-31,50-80` |
| Backup / transfer | Disabled | No | `allowBackup=false`, `fullBackupContent=false` (`app/src/main/AndroidManifest.xml:19-21`) |
| User-facing statement | Settings Privacy section | N/A | `app/src/main/java/com/zendolauncher/ui/SettingsScreen.kt:221-227` |

## Storage notes

- Store: `SharedPreferences` `launcher_settings`, `MODE_PRIVATE` (`app/src/main/java/com/zendolauncher/data/SettingsStore.kt:19-22`).
- In-code comment "nothing here is ever uploaded" (`app/src/main/java/com/zendolauncher/data/SettingsStore.kt:14-18`).
- Handful of keys + two lists; comment rejects database/DataStore (`app/src/main/java/com/zendolauncher/data/SettingsStore.kt:15-17`).
- Default-home check compares package name only; no account or profile ID stored (`app/src/main/java/com/zendolauncher/platform/ShortcutRepository.kt:83-88`).
- `LaunchTarget` free of Android types; ordering unit-testable on JVM (`app/src/main/java/com/zendolauncher/model/LaunchTarget.kt:1-21`).

## Discovery and launch surface

- Discovery `getActivityList(null, user)` per profile, self excluded (`app/src/main/java/com/zendolauncher/platform/AppRepository.kt:52-59`).
- `SecurityException` yields empty list (`app/src/main/java/com/zendolauncher/platform/AppRepository.kt:55-56`).
- Shortcuts pinned-only `FLAG_MATCH_PINNED` (`app/src/main/java/com/zendolauncher/platform/ShortcutRepository.kt:52`).
- Shortcut `RuntimeException` yields empty, never crash (`app/src/main/java/com/zendolauncher/platform/ShortcutRepository.kt:57-65`).
- Launch `startMainActivity` / `startShortcut` (`app/src/main/java/com/zendolauncher/platform/AppLauncher.kt:34,44`).
- Fallback `getLaunchIntentForPackage` + `FLAG_ACTIVITY_NEW_TASK` (`app/src/main/java/com/zendolauncher/platform/AppLauncher.kt:56-60`).
- Failure toasts (`app/src/main/java/com/zendolauncher/platform/AppLauncher.kt:27`).
- Pause pre-launch only: sets `_pendingPause` (`app/src/main/java/com/zendolauncher/ui/LauncherViewModel.kt:287-294`); Continue launches once (`:297-300`); Cancel drops (`:303`).
- Settings states no system-wide block, Chrome not intercepted (`app/src/main/java/com/zendolauncher/ui/SettingsScreen.kt:201-205`).

## Network: none

- No `INTERNET`, HTTP client, socket, `OkHttp` / `Retrofit` / `WebView` / Custom Tabs / Trusted Web Activity in tree.
- URL grep hits only `http://schemas.android.com` XML namespaces.
- `zendoEnabled` fetches nothing; Settings states no planning API (`app/src/main/java/com/zendolauncher/ui/SettingsScreen.kt:122-126`).
- Source interface stubbed `UnavailableZendoSource` (`app/src/main/java/com/zendolauncher/ui/LauncherViewModel.kt:94-95`).

## Keep-absent (grep-verified, no hits in `app/src`)

`QUERY_ALL_PACKAGES`, `PACKAGE_USAGE_STATS` / `UsageStatsManager`, `AccessibilityService`, `DevicePolicyManager` / admin, `NotificationListenerService`, `TileService`, `KeyguardManager`, `WallpaperManager`, `WifiManager` / `ConnectivityManager` / `BluetoothAdapter`, `SYSTEM_ALERT_WINDOW` / overlay, Custom Tabs / Trusted Web Activity.
Do not add any without updating this doc and `docs/android-system-boundaries.md`.

## Limitations

- Static audit only. Transitive-dep prompts, OEM permissions, adb/root backup unverified.
- Real API 30+ visibility with restricted profiles unverified; Robolectric returns no apps.

## Manual tests still required

- Fresh install: zero runtime permission dialogs through onboarding, set-default, launch, hide, pause.
- Proxy / traffic log: no egress during discovery, launch, shortcut refresh.
- Uninstall/reinstall: prefs do not restore.
- Non-default-home: shortcut list empty by design.
- Airplane-mode: all functions work offline.
