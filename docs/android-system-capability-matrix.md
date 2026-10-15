# Android System Capability Matrix

One row per phone-wide surface from the product vision. Categories:
**Direct** (public API) / **Auth** (needs user grant or setup) /
**Indirect** (official settings page or intent) / **Dependent** (varies,
must be tested) / **Denied** (privileged role required — not claimed).

| Surface | Class | Launcher behavior |
| --- | --- | --- |
| Home screen rendering | Direct | own activity, own theme, pager |
| Default Home role | Auth | system picker via `ACTION_HOME_SETTINGS`; never set programmatically |
| All-apps discovery | Direct | `LauncherApps` + `<queries>` MAIN/LAUNCHER; no `QUERY_ALL_PACKAGES` |
| PWA / pinned shortcuts | Auth+Dependent | `FLAG_MATCH_PINNED`, readable only as default Home; browser-dependent |
| App launching | Direct | `startMainActivity`/`startShortcut` + fallback intent + toast |
| Gesture / 3-button nav | Denied (system) | no interception; insets respected |
| Predictive Back | Direct | opt-in flag; no custom transitions |
| Status bar / nav icons | Direct | icon appearance derived from palette; scrims untouched |
| Notification shade, QS tiles | Denied | Indirect only: panels/settings intents |
| Recents | Denied | hides self (`excludeFromRecents`) only |
| Wallpaper Home/Lock | Direct+Dependent | `WallpaperManager` + picker fallback (see wallpaper audits) |
| Secure lock screen | Denied | background only; no clock/auth/notification control |
| Calendar read | Auth | `READ_CALENDAR`, read-only Instances (+ Events fallback), offline; Google selection + Google-only filter |
| Calendar write | Denied (undeclared) | not requested; editor covers tasks instead |
| Open system calendar | Indirect | VIEW time-URI intent with generic fallback |
| Usage stats | Denied (undeclared) | no `PACKAGE_USAGE_STATS`; no usage numbers shown |
| App blocking/suspend | Denied | visual hide + voluntary Pause only (see wellbeing roadmap) |
| Notification access | Denied (undeclared) | no listener; lock-notification guidance via system pages |
| Wi-Fi / mobile data | Indirect | settings panels (`ACTION_INTERNET_CONNECTIVITY`, wifi/sound/display pages); no toggles |
| Overlays / accessibility | Denied (undeclared) | no `SYSTEM_ALERT_WINDOW`, no `AccessibilityService` |
| Background execution | Dependent | no services/alarms; callbacks re-register on start; manual Refresh |

Rule of thumb applied everywhere: if the OS owns it, the launcher opens the
OS surface instead of imitating it. Every Indirect row fires a real intent
with an `ActivityNotFoundException` → toast fallback, asserted in tests.
