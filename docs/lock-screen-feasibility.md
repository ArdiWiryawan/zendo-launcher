# Lock Screen Feasibility

Verdict: not feasible as a launcher feature; no path in tree.

Scope: versionName 0.2.2 (`app/build.gradle:23`). Manifest zero `<uses-permission>`, single HOME activity (`app/src/main/AndroidManifest.xml:1-48`).
Method: grep over `app/src` for lock/keyguard/auth/overlay/boot APIs. No device/emulator run claimed.

## Why the launcher cannot own the lock

| Requirement | Tree evidence | Gap |
| --- | --- | --- |
| Show above Keyguard | Plain post-unlock Activity, no keyguard flags (`app/src/main/java/com/zendolauncher/LauncherActivity.kt:36-62`) | No `showWhenLocked` / `turnScreenOn` in tree |
| Replace / dismiss Keyguard | No `KeyguardManager`, no dismiss call in tree | Secure lock owned by OS |
| Authenticate PIN/biometric | No `BiometricPrompt`, no credential-confirm intent in tree | Auth lives in SystemUI/Keyguard |
| Lock notifications / shade | No `NotificationListenerService`; shade untouched | OS draws lock shade |
| Enforce lock policy | No `DevicePolicyManager`, no admin receiver in tree | MDM / work policy out of reach |
| Overlay a lock UI | No `SYSTEM_ALERT_WINDOW`, no `TYPE_APPLICATION_OVERLAY`, no `canDrawOverlays` | Pause dialog in-activity only (`app/src/main/java/com/zendolauncher/LauncherActivity.kt:127-135`) |
| Run instead of Keyguard at boot | No `BOOT_COMPLETED` receiver; only HOME filter (`app/src/main/AndroidManifest.xml:39-44`) | OS shows Keyguard before HOME |
| Wake / turn screen on | No wake lock, no `turnScreenOn` flag in tree | Cannot drive lock timing |

## What the tree actually does near this surface

- Post-unlock HOME only: `singleTask` + `clearTaskOnLaunch` + `stateNotNeeded` + `excludeFromRecents` (`app/src/main/AndroidManifest.xml:31-36`).
- Stateless-root comment in code (`app/src/main/java/com/zendolauncher/LauncherActivity.kt:32-35`).
- Lifecycle signals only `onStart` / `onResume` / `onStop` (`app/src/main/java/com/zendolauncher/LauncherActivity.kt:64-77`).
- Status/nav icon legibility: `SystemBarIcons` overrides appearance, leaves scrims (`app/src/main/java/com/zendolauncher/LauncherActivity.kt:91-101`).
- Edge-to-edge via `enableEdgeToEdge()` (`app/src/main/java/com/zendolauncher/LauncherActivity.kt:53`).
- Insets via `systemBarsPadding()` on all screens (`app/src/main/java/com/zendolauncher/ui/HomeScreen.kt:61`, `app/src/main/java/com/zendolauncher/ui/AppListScreen.kt:79`, `app/src/main/java/com/zendolauncher/ui/SettingsScreen.kt:54`, `app/src/main/java/com/zendolauncher/ui/OnboardingScreen.kt:45`).
- Wallpaper: 0.4.x+ applies a bundled bitmap directly via `WallpaperManager.setBitmap` for the chosen target only; the lock *screen* is still untouched — no `KeyguardManager`, no clock or notification surface. "Wallpaper" in Settings > System settings still opens the system picker.
- Pause is not a lock: pre-launch dialog only (`app/src/main/java/com/zendolauncher/ui/LauncherViewModel.kt:287-303`).
- Settings text states no system-wide block, Chrome links not intercepted (`app/src/main/java/com/zendolauncher/ui/SettingsScreen.kt:201-205`).

## Paths deliberately not taken

- `showWhenLocked` clock widget: renders above real lock without auth; misleading second clock, still not a lock. Not implemented.
- Accessibility / overlay / admin simulated lock: no such service in tree; contradicts zero-permission stance (`app/src/main/AndroidManifest.xml:5-10`). Do not add without rewriting `docs/privacy-and-permissions-audit.md`.
- `KeyguardManager.requestDismissKeyguard`: absent; requires unlocked trust, still leaves OS lock in place.
- Wallpaper/guide path only: launcher keeps own icons readable; lock visuals stay with OS.

## Related surfaces owned by OS

- Recents: launcher hides self only (`app/src/main/AndroidManifest.xml:33`). Full table in `docs/android-system-boundaries.md`.
- Shade / QS / tiles: none in tree. See `docs/android-system-boundaries.md`.
- Notifications: no listener service. See `docs/privacy-and-permissions-audit.md`.

## Limitations

- Static audit. OEM lock replacements, foldable postures, managed-profile locks unverified.
- No lock-timing measurement possible from tree; no foreground/background signal beyond activity lifecycle.
- Secure-lock behavior (PIN/biometric/face) unverified without device.

## Manual tests still required

- Cold boot: confirm Keyguard appears before launcher; launcher never prompts for auth.
- Lock then Home press: launcher renders only after unlock.
- Secure-lock device: no launcher surface above Keyguard.
- Confirm no lock notification, no unlock path, no auth fallback in app.
- Confirm pause dialog never appears over Keyguard; in-activity only.
- Foldable / cover display: confirm no launcher surface leaks above Keyguard on any posture.
- Managed profile with separate lock: confirm launcher never bypasses the profile challenge.

## Calm monochrome wallpapers: direct apply and system picker (0.5.0)

Zendo Launcher ships three grayscale designs and applies them directly through the official bitmap API — `WallpaperManager.setBitmap(..., FLAG_SYSTEM / FLAG_LOCK)` — under `android.permission.SET_WALLPAPER` (normal, auto-granted). Code: `core/WallpaperTemplate.kt` (designs), `platform/WallpaperApplier.kt` (render + apply + capability probe), `ui/WallpaperScreen.kt` (gallery). Full behavior notes: `docs/wallpaper-and-lock-screen.md`.

What did not change: the launcher never owns the lock screen. It sets a background bitmap only — never the clock, notifications, or unlock method — and reports success only after the system confirms the write. Lock/Both targets are offered only when the device reports them; otherwise Home only, and the system picker stays the honest fallback: **Settings > System settings > Wallpaper** dispatches `Intent.ACTION_SET_WALLPAPER` through `openSystemSettings`, with the "Settings page unavailable" toast when no picker exists.

The 0.3.x repository vectors (`wallpaper_mono_dark.xml` / `wallpaper_mono_light.xml`) were removed in 0.5.0 — the gallery renders its three designs on demand instead, so there are no loose assets to export by hand.

## Lock-screen notification visibility guidance

Because Zendo Launcher intentionally omits `NotificationListenerService` to guarantee privacy, quiet lock-screen behavior is configured directly in Android system settings:

1. **Hide sensitive or all lock-screen notifications:** **Settings > Notifications > Notifications on lock screen** -> select **Don't show any notifications** (or **Show alerting notifications only**).
2. **Silence lock-screen wakeups:** Disable **Wake screen for notifications** under **Settings > Display > Lock screen**.
3. **Disable ambient distractions:** Turn off **Always show time and info (AOD)** if a completely blank resting display is preferred.

## Device-specific lock-screen tips (Pixel, Samsung One UI, Xiaomi/ColorOS)

- **Google Pixel (Pixel UI):** **Settings > Display > Lock screen** -> set **Shortcuts** (left/right bottom) to **None**, disable **Show wallet** / **Show device controls**, and set clock size to **Small** (single-line) instead of dynamic double-line.
- **Samsung (One UI):** **Settings > Lock screen and AOD** -> tap **Edit Lock screen**, remove bottom app shortcuts (Camera/Phone), set the clock to the smallest plain text style, disable **Wallpaper Services** (Dynamic Lock Screen / Glance), and set **Notifications** to **Icons only** or **Off**.
- **Xiaomi (HyperOS / MIUI) & OPPO/OnePlus (ColorOS / OxygenOS):** Disable **Wallpaper Carousel** / **Lock Screen Magazine** under **Settings > Lock screen**, remove lock-screen pocket/swipe-left drawers, and disable **Wake Lock screen for notifications**.

