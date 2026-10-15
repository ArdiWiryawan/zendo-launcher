# Wallpaper Capability Audit

Scope: versionName 0.3.2, minSdk 26 / targetSdk 36. Companion to
`docs/lock-screen-root-cause.md` (which owns the defect analysis); this file
owns the capability table.

## Per-target support (ordinary third-party app, public APIs only)

| Target | API used | minSdk 26 status | Notes |
| --- | --- | --- | --- |
| Home | `setBitmap(b, null, true, FLAG_SYSTEM)` | supported | return ID gated by `isConfirmed` |
| Lock | `setBitmap(b, null, true, FLAG_LOCK)` | supported, needs API 24+ (always true at minSdk 26) | honored per OEM; Partial result when Home lands and Lock does not |
| Both | two sequential calls | supported as composition | never atomic; reported as Applied or Partial, never invented |
| System picker fallback | `ACTION_SET_WALLPAPER` | supported | needs no permission; opening it is reported as "opened", not "applied" |

## Pre-write checks (`WallpaperApplier.probe`)

| Check | Denied behavior |
| --- | --- |
| `isWallpaperSupported()` false (e.g. some TVs, managed devices) | Failed: "Wallpapers are not supported on this device." + picker fallback |
| `isSetWallpaperAllowed()` false (device policy) | Failed: policy message + picker fallback |
| probe itself throws (unknown platform) | treated as capable; the call decides |

Exceptions map 1:1 to messages: `SecurityException` → permission,
everything else → "System rejected the wallpaper." plus the unconfirmed-ID
case ("The system did not confirm …"). No catch is silent.

## Assets and performance

Templates are code-rendered 1080x1920 ARGB bitmaps (~8 MB transient,
recycled in `finally` after hand-off); previews are Compose (gradient,
seeded grain, hairline) with zero bitmap cost. No network, no decode
failures by construction (`render` still guarded).

## Version / OEM matrix

| Dimension | Status |
| --- | --- |
| API 26–36 | same code path; `< 24` branches retained as dead-code fallback |
| Pixel / AOSP | expected honored (unverified on hardware) |
| Samsung One UI / Xiaomi HyperOS / OPPO-ColorOS | `FLAG_LOCK` historically honored but OEM lock replacements may ignore third-party backgrounds — unverified, Partial path covers it |
| Managed profile / Device Owner policy | `isSetWallpaperAllowed` gate + honest message |
| Live wallpaper engine active | `setBitmap` replaces static wallpaper only; engine state untouched |

## Verification status

Robolectric (`WallpaperApplierTest`): render sizes, ID confirmation rule,
probe answers, HOME/LOCK/BOTH reach the shadow manager with decided results.
Hardware lock/unlock check: NOT performed — user checklist in
`docs/lock-screen-root-cause.md` §Tests.
