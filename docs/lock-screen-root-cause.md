# Lock Screen Root Cause

Scope: versionName 0.3.1 (`app/build.gradle`), minSdk 26 / targetSdk 36.
Method: full trace of the Apply path in source + Robolectric tests. No
physical-device run claimed; FLAG_LOCK behavior stays device-dependent (see §5).

## Files in the Apply path

| Step | File |
| --- | --- |
| Gallery, preview, target picker, Apply button | `ui/WallpaperScreen.kt` |
| Selection + target state, apply orchestration | `ui/LauncherViewModel.kt` (`selectWallpaper`, `setWallpaperTarget`, `applyWallpaper`) |
| Selection record + last-result record | `data/WallpaperStore.kt` (`launcher_wallpaper` prefs) |
| Render + system call + result mapping | `platform/WallpaperApplier.kt` |
| Permission | `app/src/main/AndroidManifest.xml` (`SET_WALLPAPER`, normal) |

Trace: template tap → `selectWallpaper(id)` → preview minis render in Compose
→ target `ChoiceRow` → `setWallpaperTarget` → Apply → `applyWallpaper()`
(double-submit guard) → `Dispatchers.IO` → `WallpaperApplier.apply` →
`Applied | Partial | Failed` → `recordResult` + message → UI shows
"Applying…" meanwhile and only the returned message afterwards.

## Findings per failure mode

**A. UI action reaches the API — verified.** The Apply button calls
`viewModel.applyWallpaper()` directly with the stored selection and target;
no navigation callback interferes, and it is hidden (replaced by status text)
while applying. The one gap was a missing in-VM duplicate guard for the
frame before recomposition swaps the button — fixed (`if
(_wallpaperApplying.value) return`).

**B. Targets are correct — verified.** `FLAG_SYSTEM` for Home, `FLAG_LOCK`
for Lock, two sequential calls for Both (Partial when Home lands and Lock
does not). The 4-arg `setBitmap` overload and `FLAG_LOCK` need API 24;
minSdk is 26, and the `< 24` branches are retained as deliberate dead-code
fallbacks. `minSdk 26` means no supported device can reach them, which is
stated here rather than deleted so the fallback survives any future
minSdk change.

**C. Manifest + capability — fixed.** `SET_WALLPAPER` (normal, auto-granted)
is declared; no storage, accessibility, or other permissions were added.
Previously there were no pre-write capability checks, so a TV without
wallpaper support or a managed profile forbidding it only failed deep in the
call. `probe()` now checks `isWallpaperSupported()` /
`isSetWallpaperAllowed()` first (exceptions fall back to capable, never to a
block) with plain messages for each case.

**D. Asset loading — eliminated by construction.** Templates are rendered
from code (`render()`, 1080x1920 ARGB) — there are no resource IDs, asset
paths, URIs, or network loads to be wrong. `render` is still wrapped so a
graphics failure reads as "Could not render wallpaper."

**E. Return-value handling — fixed (finding #1).** `setBitmap` returns a
wallpaper ID, and the previous code ignored it: any return without an
exception read as success. `isConfirmed(id) = id > 0` now gates every branch;
a non-positive ID reports "The system did not confirm the wallpaper."
`SecurityException` (missing permission) and all other exceptions still map
to explicit Failed messages. Nothing is swallowed: every catch produces a
user-visible message.

**F. State and lifecycle — verified.** Selection (`selectedId`) and outcome
(`lastSuccess`, `lastAppliedMillis`, `wallpaperMessage`) are separate keys;
a failed attempt records failure without overwriting the selection, and a new
selection clears the stale message. Recomposition cannot re-fire apply (the
call lives in a coroutine launched once from the click, not in composition).
The screen stays responsive (IO dispatcher); leaving mid-apply shows the true
in-flight/done message on return, never a fabricated success.

## Wallpaper vs lock screen

Setting a background via `FLAG_LOCK` is the whole scope. The tree contains no
`KeyguardManager`, `showWhenLocked`, `BiometricPrompt`,
`NotificationListenerService`, overlay, boot receiver, or wake lock (grep in
`docs/android-system-boundaries.md`), and none was added. No fake lock
screen, no clock/notification re-layout, no auth interference. The gallery
caption and lock preview both state the mini is not the real lock screen.

## Tests performed

- `platform/WallpaperApplierTest` (Robolectric, SDK 36): every template
  renders at the requested size; `isConfirmed` accepts only positive IDs;
  `probe` answers without throwing; `apply` to HOME / LOCK / BOTH reaches the
  shadow manager and returns a decided state (no crash, no invented success).
- Full suite `testDebugUnitTest` green (see implementation report).
- Device verification: NOT performed — no physical device or emulator run in
  this pass. Repeatable checklist for the user:
  1. Apply Obsidian to Home → Home background turns black.
  2. Apply Paper to Lock → lock the device → lock background turns paper.
  3. Apply Both → lock and check both surfaces.
  4. On a managed profile with wallpaper disabled → clear policy message.
  5. Rapid double-tap Apply → single operation, "Applying…" shown once.
  Record Android version, manufacturer/model, and which of Applied / Partial /
  Failed each step produced.
