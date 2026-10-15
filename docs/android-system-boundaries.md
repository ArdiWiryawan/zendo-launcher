# Android System Boundaries

Scope: Zendo Launcher versionName 0.5.0 (`app/build.gradle`), minSdk 26 / targetSdk 36 / compileSdk 36.
Method: full read of `app/src/main/AndroidManifest.xml` plus grep over `app/src` for both absence and presence claims, re-run for 0.5.0. No device/emulator run claimed. The first draft of this doc was 0.2.2; the boundary principle is unchanged and the permission inventory below is current.

## Manifest root

- Exactly three `<uses-permission>` entries, each narrow and user-triggered: `SET_WALLPAPER` (normal, auto-granted; direct wallpaper apply), `READ_CALENDAR` (runtime; requested only when a calendar feature is opened), `PACKAGE_USAGE_STATS` (special access; opt-in screen-time summary, off by default).
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
| Wallpaper | Three bundled grayscale designs rendered on demand (`core/WallpaperTemplate.kt`, `platform/WallpaperApplier.kt`); direct apply to Home and, where supported, Lock via `WallpaperManager.setBitmap` (`FLAG_SYSTEM` / `FLAG_LOCK`); system-picker fallback | Picker, live engine, OEM lock screens | Only the official home/lock bitmap API: no wallpaper *service*, no `KeyguardManager`, no live-wallpaper APIs | Sets a background only |
| Connectivity / system shortcuts | Fires system settings pages, never owns toggles: `ACTION_HOME_SETTINGS` (`app/src/main/java/com/zendolauncher/ui/SettingsScreen.kt:142-143`, `app/src/main/java/com/zendolauncher/ui/OnboardingScreen.kt:95`), `Settings.Panel.ACTION_INTERNET_CONNECTIVITY` with `ACTION_WIFI_SETTINGS` fallback (`app/src/main/java/com/zendolauncher/ui/SettingsScreen.kt:230-237`), `ACTION_DISPLAY_SETTINGS` (`:238-240`), `ACTION_SOUND_SETTINGS` (`:241-243`) | WiFi, BT, mobile data, system settings | No `WifiManager` / `ConnectivityManager` / `BluetoothAdapter`; no BT or Digital Wellbeing intent | Opens OS pages |
| Default Home role | Reads resolver, directs to picker (`app/src/main/java/com/zendolauncher/platform/ShortcutRepository.kt:83-88`) | Home selection dialog | No direct set-default API | OS decides |
| App suspend / block | Visual-only hide set (`app/src/main/java/com/zendolauncher/data/SettingsStore.kt:33,68-73`) | Suspend, disable, MDM | No `DevicePolicyManager` / admin in tree | Filters own list |
| Chrome / URLs | Starts launcher target only; no interception (`app/src/main/java/com/zendolauncher/ui/SettingsScreen.kt:204-208`) | Link resolution, Custom Tabs, PWA install | No Custom Tabs / `WebView` in tree | OS owns |
| Pause enforcement | In-activity dialog interpose (`app/src/main/java/com/zendolauncher/ui/LauncherViewModel.kt:287-303`) | Execution, URL dispatch | No stats / accessibility / overlay | Bypassable |

## Keep-absent inventory

Grep over `app/src` returns no hits for execution or policy APIs; only XML namespace `http://schemas.android.com` matched URL grep.
Keep absent: `QUERY_ALL_PACKAGES`, `AccessibilityService`, `DevicePolicyManager`, `NotificationListenerService`, `TileService`, `KeyguardManager`, `WifiManager` / `ConnectivityManager` / `BluetoothAdapter`, `SYSTEM_ALERT_WINDOW` / overlay, `INTERNET` / `OkHttp` / `Retrofit`, Custom Tabs / Trusted Web Activity.
Documented exceptions, each narrow and never a background owner: `PACKAGE_USAGE_STATS` / `UsageStatsManager` (0.3.3+, off by default, numbers-only summary), `WallpaperManager` (0.4.x+, sets only the home/lock bitmap the user asked for), calendar provider reads under `READ_CALENDAR` (0.4.x+, on demand, read-only). The three matching manifest permissions are listed under Manifest root above.
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
- Applying each bundled design directly (Settings → Wallpaper) and confirming the honest lock-target behavior, per `docs/wallpaper-and-lock-screen.md`.
- Pinned-shortcut visibility when default vs not default.
- `excludeFromRecents` + `singleTask` return-to-home after Home press.
- Hidden/paused apps still launch from system search, voice, other launchers.
- Chrome URL taps bypass pause entirely.
- API 26 vs 36 bars; no shade/QS intrusion observed.
- Airplane-mode run; all launcher functions offline.
