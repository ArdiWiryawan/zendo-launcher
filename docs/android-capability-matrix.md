# Android Capability Matrix

Scope: Zendo Launcher as installed. Verified against `app/src/main/AndroidManifest.xml`, `app/src/main/java/com/zendolauncher/LauncherActivity.kt`, `app/src/main/java/com/zendolauncher/platform/AppLauncher.kt`, `app/src/main/java/com/zendolauncher/platform/AppRepository.kt`, `app/src/main/java/com/zendolauncher/platform/ShortcutRepository.kt`, `app/src/main/java/com/zendolauncher/ui/SettingsScreen.kt`, `app/src/main/java/com/zendolauncher/ui/OnboardingScreen.kt`.

No `AccessibilityService`, no `TileService`, no usage access, no device admin, no wallpaper calls in tree.

| Area | Launcher can do | Android-controlled |
| --- | --- | --- |
| Home | Render own Home screen: clock, Today block slot, Essentials list. Own theme, layout mode, 24-hour option. Own transitions between HOME / APPS / SETTINGS / ONBOARDING. Set status/nav icon appearance for own background. | Home app selection. Nav gestures, system bars, notification shade. Window background before first Compose frame. |
| All Apps | List launcher activities via `LauncherApps` plus pinned shortcuts when default home. Search, alphabetical grouping, pin, reorder, hide. Refresh on package callbacks. | Package visibility (`queries` MAIN/LAUNCHER only, no `QUERY_ALL_PACKAGES`). Multi-profile enumeration. Shortcut publication by other apps. |
| App launches | Start activity or pinned shortcut tapped in own UI via `LauncherApps.startMainActivity` / `startShortcut`, fallback to `getLaunchIntentForPackage`. Toast on failure. | Launches from notifications, links, Recents, and other apps bypass the launcher. OS resolves and starts those intents. |
| Recent Apps | Exclude own activity via `excludeFromRecents="true"`. Stateless root via `singleTask` + `clearTaskOnLaunch` + `stateNotNeeded`. | Overview/Recents UI, task list, task clearing, gesture navigation. |
| Lock screen | None. No keyguard UI, no unlock path. | Secure lock, keyguard, biometric/PIN/pattern auth, lock-screen notifications. |
| Quick Settings | None. No `TileService` in manifest or tree. | Shade, Quick Settings tiles, system toggles, media output. |
| App usage | None. No `PACKAGE_USAGE_STATS`, no `UsageStatsManager`, no timers or limits. Own hidden-apps list only hides rows in own UI. | Digital Wellbeing, usage stats, app timers, parental controls. |
| PWA | List and start what the platform exposes: WebAPK launcher activities via `AppRepository`, pinned shortcuts via `ShortcutRepository` (`FLAG_MATCH_PINNED` only). | PWA install flow, WebAPK registration, whether a browser publishes a pinned shortcut. Browser-dependent; nothing to configure in launcher. |
| Device restriction | None. No `DevicePolicyManager`, no admin receiver, no lock/wipe/block. | Work profile, device policy, app restrictions, school/MDM controls. |

## Notes

- Settings changes outside own UI go through system intents only. Tree fires `Settings.ACTION_HOME_SETTINGS` from Onboarding and Settings; it does not set the default itself.
- Wallpaper: no `WallpaperManager` calls in tree. Wallpaper change stays with the system picker.
- Single-activity Compose root owns only its own screens. Gestures, Overview, shade, secure lock, and auth remain with the OS.
