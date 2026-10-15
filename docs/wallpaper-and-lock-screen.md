# Wallpaper and Lock Screen

Scope: versionName 0.5.0 (`app/build.gradle`), minSdk 26 / targetSdk 36.
Method: source read + unit tests (`WallpaperTemplatesTest`,
`WallpaperApplierTest`). No device run claimed for OEM lock behavior.

## Designs

Three bundled designs in `core/WallpaperTemplate.kt` (`WallpaperTemplates.ALL`).
No network, no heavy assets: preview is Compose (`ui/WallpaperScreen.kt`
`WallpaperPreviewBox`); apply renders the design at the device's own wallpaper
size on demand (`platform/WallpaperApplier.kt` `render`) and recycles the
bitmap after hand-off.

1. **Quiet Stone** (`quiet_stone`, the default) — a near-black stone field, no
   type.
2. **Kembali** (`kembali`) — the artwork line "Kembali ke yang penting.",
   exactly as written, set once below the clear zone.
3. **Less, but better** (`less_but_better`) — the principle plus the ZENDO
   wordmark beneath it.

Geometry: the design canvas is 9:20 and nothing sits above ~34% height — the
lock clock owns the top of the screen. Type starts at 46% height and is sized
from the width, so the layout holds at any device size.
`WallpaperTemplatesTest` pins the exact reminder line, the three ids, and the
grayscale rule (max-min RGB spread ≤ 12).

## Gallery behavior (`ui/WallpaperScreen.kt`)

- Three tall thumbnails (9:20), not product cards; tapping selects. The old
  category filters and the 12-template grid are gone (0.5.0).
- Selection persists to `data/WallpaperStore.kt` (`launcher_wallpaper` prefs);
  the selected thumbnail carries a 2dp primary border + "Selected"
  stateDescription. An id from an older build (or none) resolves to Quiet
  Stone, never to a broken or empty state.
- Preview: one large 9:20 preview under the thumbnails. No miniature fake lock
  screen, no sample clock, no invented notifications.
- Target row: Home / Lock screen / Both **only when**
  `WallpaperApplier.lockTargetSupported(context)` reports a separate lock
  target (Android 7.0 floor + capability probe). Otherwise Home only, with a
  sentence saying why; a target chosen earlier snaps to Home so the row and the
  apply always agree.
- Apply: `viewModel.applyWallpaper()` runs `WallpaperApplier.apply` on
  `Dispatchers.IO`. UI shows "Applying…" (`wallpaperApplying`), then exactly
  what the API returned: "Applied to …", a Partial message (Home applied, Lock
  unsupported), or Failed. "Applied" is never shown before the call returns.
  Any message that does not start with "Applied" keeps the "Open system picker
  instead" route (`Intent.ACTION_SET_WALLPAPER`), so a failure is never a dead
  end.

## System application

`platform/WallpaperApplier.kt`:

- `WallpaperManager.getInstance(context)`, `setBitmap(bitmap, null, true, which)`.
- Capability first: `isWallpaperSupported` / `isSetWallpaperAllowed`; a TV
  without wallpaper support or a policy-blocked profile reads as a clear
  message, never a silent no-op.
- HOME → `FLAG_SYSTEM`. LOCK → `FLAG_LOCK` (API 24+; minSdk 26 so guarded but
  reachable). BOTH → two calls; Home failure fails the whole thing, Lock
  failure returns Partial with "Lock target not supported here; Home was
  applied."
- A returned id ≤ 0 means the system did not confirm the write, so it is never
  reported as success.
- `SecurityException` (missing `SET_WALLPAPER`) and any other exception map to
  Failed with a plain message. No crash path.
- Manifest: `<uses-permission android:name="android.permission.SET_WALLPAPER" />`
  (normal, auto-granted). The system-picker fallback needs no permission.

## Lock-screen capabilities and limitations

What the launcher does:

- Sets the lock-screen *background* via `FLAG_LOCK` where the device honors it.
- Offers Lock/Both only when the device reports a separate lock target.
- Falls back to the system picker (`Settings → System settings → Wallpaper
  (system picker)` also dispatches `ACTION_SET_WALLPAPER`).
- In-app guidance: direct apply reports success only after the system
  confirms, and it sets a background — never the clock, notifications, or
  unlock method.

What it does not do (verified absent by grep):

- No `KeyguardManager`, no `showWhenLocked` / `turnScreenOn`, no
  `BiometricPrompt`, no `NotificationListenerService`, no
  `SYSTEM_ALERT_WINDOW` overlay, no `BOOT_COMPLETED` receiver, no wake lock.
  Full table in `docs/android-system-boundaries.md`; feasibility analysis in
  `docs/lock-screen-feasibility.md`.
- No fake lock screen, no PIN/biometric interception, no clock repositioning,
  no AOD control. Those remain with Android/SystemUI and the OEM
  (Pixel / One UI / HyperOS tips stay in `lock-screen-feasibility.md`).

Device-dependent (not verified without hardware): whether `FLAG_LOCK` is
honored, combined Home+Lock atomically, and third-party background visibility
under OEM lock replacements, foldables, and managed-profile locks.
