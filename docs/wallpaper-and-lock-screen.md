# Wallpaper and Lock Screen

Scope: versionName 0.3.0 (`app/build.gradle`), minSdk 26 / targetSdk 36.
Method: source read + unit tests. No device run claimed for OEM lock behavior.

## Templates

Bundled in `core/WallpaperTemplate.kt` (`WallpaperTemplates.ALL`, 12 items).
No network, no heavy assets. Preview is Compose (`ui/WallpaperScreen.kt`
`WallpaperPreviewBox`); apply renders a 1080x1920 Bitmap on demand
(`platform/WallpaperApplier.kt` `render`) and recycles it after hand-off.

Solid (6): Obsidian `#000000`, Paper `#F7F7F5`, Stone `#A8A8A6`,
Graphite `#2B2D30`, Mist `#E4E6E8`, Charcoal `#17181A`.

Subtle (6): Soft gradient (vertical black gradient), Soft grain (deterministic
random dots, seed = id hash), Linear (one hairline at 70% height on paper),
Two-tone (72/28 dark/light split), Tonal drift (diagonal grayscale gradient),
Horizon (one hairline on dark).

First release is grayscale-first: `WallpaperTemplatesTest` asserts every base
color has max-min RGB spread <= 12.

## Gallery behavior (`ui/WallpaperScreen.kt`)

- Category filters: All / Solid / Subtle (`ChoiceRow`, no new dependency).
- Grid: 3-column simple previews (color field + name), not product cards.
- Selection: `viewModel.selectWallpaper(id)` persists to
  `data/WallpaperStore.kt` (`launcher_wallpaper` prefs). Selected row gets a
  2dp primary border + primary name tone + "Selected" stateDescription.
- Preview: shown only after a selection. Two restrained minis side by side:
  Home (small clock + "Essentials" hint) and Lock (large clock + date +
  "2 notifications hidden"). Caption states it is a miniature preview only
  and does not replace the real lock screen.
- Readability: `darkBackground` flag drives "Light text reads well" /
  "Dark text reads well". No fake contrast number.
- Target: `ChoiceRow` Home / Lock screen / Both → `WallpaperTarget`
  persisted in the same store.
- Apply: `viewModel.applyWallpaper()` runs `WallpaperApplier.apply` on
  `Dispatchers.IO`. UI shows "Applying…" (`wallpaperApplying`), then only the
  message the API returned: "Applied to …", a Partial message (Home applied,
  Lock unsupported), or Failed. "Applied" is never shown before the call
  returns. Failures keep the selection and offer "Open system picker instead"
  (`Intent.ACTION_SET_WALLPAPER`).

## System application

`platform/WallpaperApplier.kt`:

- `WallpaperManager.getInstance(context)`, `setBitmap(bitmap, null, true, which)`.
- HOME → `FLAG_SYSTEM`. LOCK → `FLAG_LOCK` (API 24+, minSdk 26 so guarded
  but reachable). BOTH → two calls; Home failure fails the whole thing,
  Lock failure returns Partial with "Lock target not supported here; Home was
  applied."
- `SecurityException` (missing `SET_WALLPAPER`) and any other exception map
  to Failed with a plain message. No crash path.
- Manifest: `<uses-permission android:name="android.permission.SET_WALLPAPER" />`
  (normal, auto-granted). The system-picker fallback needs no permission.

## Lock-screen capabilities and limitations

What the launcher does:

- Sets the lock-screen *background* via `FLAG_LOCK` where the device honors it.
- Previews lock readability with a large clock + sample text.
- Falls back to the system picker (`Settings → System settings → Wallpaper
  (system picker)` also dispatches `ACTION_SET_WALLPAPER`).
- In-app guidance: "Lock wallpaper depends on the device (Android 7.0+). If
  direct apply fails here, the system picker can still set it. This never
  replaces your lock screen — only its background."

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
