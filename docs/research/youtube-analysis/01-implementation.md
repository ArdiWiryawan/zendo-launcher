# 01 — Implementation: the Calm Canvas set

The five analyses in this folder feed one UI feature set, implemented here as **Calm
Canvas**: a calmer Home made of a wallpaper field, a pinned dock, a search pill, and
icon appearance the user can actually tune — plus the one honest way out to the system's
own grayscale. This page records which video proposed what, what shipped, the settings
keys, and the screenshot evidence.

State as of 2026-10-19: implemented in the working tree, all 236 unit tests green
(`28` classes, `0` failures). Not committed.

## Feature → video map

| Feature | Source video | Proposal in the analysis | Shipped as |
| --- | --- | --- | --- |
| **Icon styles** — Monochrome / Tinted / Original | `krityj3ITVE` (rec. 1: "tinted/monochrome icon engine with wallpaper eyedropper… warm-gold and neutral presets", rec. 9: grayscale focus skin) and `iCUB7JZmmig` (rec. 1: "monochrome adaptive icon treatment") | One tint for every glyph, with the wallpaper as a possible source; original icons as the escape hatch | `IconStyle` (`MONOCHROME` default, `TINTED`, `NATIVE`) rendered as a duotone colour-matrix wash in `AppIcon`; Settings → Appearance → "App icons" |
| **Icon wash colour** — six swatches + "Match wallpaper" | `krityj3ITVE` (7:17 iOS 18 "Tinted" sheet, eyedropper → "wallpaper colour that contrasts most"; observed warm gold at 5:50) and `7jVb1lLniEw` (rec. 2: "grayscale-first rendering toggle… monochrome as the default onboarding look") | Warm-gold and neutral presets exist as data, not as a colour picker | `ICON_TINTS` (Cream / Gold / Mint / Fog / Blush / Slate); `iconTintMatchWallpaper` resolves to the applied template's `dominantAccent()` — the gold capture uses `less_but_better`'s `#C9A96A` |
| **Home backdrop** — "Wallpaper on home" | `7LPEVfMaajU` (rec. 8: "near-black wallpaper default", page 2 read as "large empty wallpaper area"; rec. 9: countdown surface in the clock's own weight) and `krityj3ITVE` (rec. 7: "wallpaper-first theming… full-bleed art") | The wallpaper the user picked for calm can sit behind Home itself | `homeBackdrop` toggle; Home draws the text-free template render (`includeText = false`) at the window's own pixel size behind a legibility scrim, dark theme deepens the bottom edge; drawer and Settings stay flat |
| **Today dock** — pinned row above the chrome | `oUoxlF_s7wk` (03:59 "after" state: empty page, only dock + search pill; four squircle icons evenly spaced) and `7LPEVfMaajU` (rec. 14: "dock = daily drivers only (max 4)"; observed dock of four) and `krityj3ITVE` (dock-only home, four tinted glyphs) and `iCUB7JZmmig` (dock of four monochrome tiles) | The four most-used apps are the only icons the first page needs | `todayDockTargets` in `HomeScreen.kt`: the Today page's own pinned order, icon-only, capped at `DOCK_MAX_ITEMS = 5`; 40dp glyphs in 48dp targets |
| **Search pill** | `oUoxlF_s7wk` (03:59: dark translucent capsule with magnifier + "Search" centred above the dock) | One text affordance between the dock and the chrome, instead of a glyph | Text-only pill above the bottom chrome (`RoundedCornerShape(999.dp)`, hairline border); tapping opens the drawer with the cursor in the field and the keyboard up (`openApps(autoFocusSearch = true)`, consumed on arrival). The magnifier is deliberately dropped: it would be the one pictorial element in the launcher |
| **Display & grayscale** | `7jVb1lLniEw` (rec. 2: "optionally expose deep-link to system grayscale settings"; 13: grayscale demo) | Don't re-implement grayscale — hand the user the real switch | `QuietAction("Display & grayscale")` in Settings opens `Settings.ACTION_DISPLAY_SETTINGS` |

Not implemented from these analyses, deliberately: notification-badge suppression
(`iCUB7JZmmig`) needs notification access the launcher does not ask for; the widget
surfaces (`7LPEVfMaajU`, `iCUB7JZmmig`) and the daily-regenerated wallpaper remain
concepts.

## Settings keys

Stored in the existing `SettingsStore` blob (local only, never uploaded).

| Key | Type | Default | Meaning |
| --- | --- | --- | --- |
| `icon_style` | string | `MONOCHROME` | `IconStyle` name; how Home and drawer icons are drawn |
| `icon_tint` | string `#RRGGBB` | `#EDE6D8` (cream) | Wash colour for `MONOCHROME`; sanitized on read and write |
| `icon_tint_match_wallpaper` | boolean | `false` | When on, the wash follows the applied design's accent (`WallpaperStore.appliedId`, falling back to the default design) |
| `home_backdrop` | boolean | `false` | Draw the selected design behind Home, below the scrim |
| `monochrome_icons` | boolean (legacy) | `true` | Pre-enum key, kept in step: `true` reads as `MONOCHROME`, `false` as `NATIVE` |

## Screenshots

Produced by `ScreenshotCaptureTest` into `app/build/screenshots/`
(`& ./gradlew.bat :app:testDebugUnitTest --tests "com.zendolauncher.ui.ScreenshotCaptureTest" --rerun`):

| File | Shows |
| --- | --- |
| `calm-home-dock-dark.png` | Dark Home: four pinned cream icons in the dock above the search pill |
| `calm-home-tint-backdrop-dark.png` | Dark Home with `TINTED` gold (`#C9A96A` from `less_but_better`) dock icons over the full-screen backdrop scrim |
| `calm-home-light.png` | Light Home, the same calm layout on the warm paper theme |

Each capture is decoded and checked in-process: distinct colours, pixels differing from
the modal background, a SHA-256 unique across the run.

## Edge cases handled

- **No wallpaper + backdrop on** — no template resolves, so Home paints the flat
  background and says nothing (toggle stays on for when a wallpaper is chosen).
- **Dock with one item** — renders as a single icon, no padding hacks; **more than
  five** — the first five in the page's own order, never backfilled.
- **Corrupted tint hex** — `sanitizeIconTint` / `parseTint` fall back to the cream
  `#EDE6D8`, on read, on write, and at draw time.
- **Unknown wallpaper id with match on** — falls back to the stored swatch, so the
  resolved tint is always parseable.

## Where the code is

`data/SettingsStore.kt` (keys, defaults, migration), `ui/Components.kt`
(`LocalIconStyle` / `LocalIconTint`, `duotoneMatrix`, `parseTint`, `AppIcon`),
`LauncherActivity.kt` (`resolvedIconTint`), `ui/HomeScreen.kt` (backdrop, dock, pill),
`ui/SettingsScreen.kt` (controls, swatches, deep link),
`core/WallpaperTemplate.kt` (`dominantAccent`),
`platform/WallpaperApplier.kt` (`includeText`). Design rules: `docs/design-system.md`
§7 "Icon styles" and §8 "Home backdrop".
