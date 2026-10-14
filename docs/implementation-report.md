# Implementation Report

Tree state: versionName 0.2.4 (`app/build.gradle:23`), compileSdk 36 / minSdk 26 / targetSdk 36 (`app/build.gradle:16-21`).
Method: `git diff` over working tree plus grep over `app/src`. No device/emulator run claimed.
Unit suite: 49 `@Test` methods under `app/src/test/java/com/zendolauncher/ui/` (23 `LauncherUiTest.kt`, 10 `PauseGateTest.kt`, 16 `ScreenshotCaptureTest.kt`).

## Changed this pass

| File | Diff | What |
| --- | --- | --- |
| `app/src/main/java/com/zendolauncher/LauncherActivity.kt` | +15/-1 | BackHandler split SETTINGS/APPS (`:108-114`); `PauseDialog` overlay on `pendingPause` (`:127-135`) |
| `app/src/main/java/com/zendolauncher/data/SettingsStore.kt` | +8 | `distract` + `pauseEnabled` keys read/write (`:38-39,53-54,100-101,114-115`) |
| `app/src/main/java/com/zendolauncher/ui/LauncherViewModel.kt` | +62/-3 | `_pendingPause` (`:123`), `settingsReturn` (`:126`), `drawerIndex/Offset` (`:129-130`), `launch` interpose (`:287-294`), `confirmPause`/`cancelPause` (`:297-303`), `toggleDistract` (`:307-310`), `openSettings(from)` + `backFromSettings` (`:249-258`) |
| `app/src/main/java/com/zendolauncher/ui/AppListScreen.kt` | +19/-2 | `rememberLazyListState` seeded from view model (`:65-68`); `DisposableEffect` saves position (`:145-150`); `openSettings(Screen.APPS)` (`:183`) |
| `app/src/main/java/com/zendolauncher/ui/Components.kt` | +26 | `PauseDialog` composable, Continue/Cancel, no timer (`:353-377`) |
| `app/src/main/java/com/zendolauncher/ui/HomeScreen.kt` | +1/-1 | `openSettings(Screen.HOME)` (`:130`) |
| `app/src/main/java/com/zendolauncher/ui/SettingsScreen.kt` | +31 | Intentional Pause section with honest scope disclaimer (`:196-208`); System settings QuietAction shortcuts incl. Wallpaper -> `Intent.ACTION_SET_WALLPAPER` (`:230-246`) over helper `openSystemSettings(context, action, fallback)` catching `ActivityNotFoundException` (`:319-332`), import at `:3` |
| `app/src/main/res/drawable/wallpaper_mono_dark.xml` | +15 (new) | Repo asset, unreferenced by `app/src/main`: monochrome 360dp x 720dp vector, `#FF0B0B0C` fill + `#FF1F1F21` horizon hairline |
| `app/src/main/res/drawable/wallpaper_mono_light.xml` | +15 (new) | Repo asset, unreferenced by `app/src/main`: monochrome light counterpart vector |
| `app/src/test/java/com/zendolauncher/ui/LauncherUiTest.kt` | +1 | `setPauseEnabled(false)` in setup (`:67`) |
| `app/src/test/java/com/zendolauncher/ui/PauseGateTest.kt` | +182 (new) | 11 Robolectric tests (`:52,68,84,95,106,118,129,138,153,166,176`): gate, cancel, unrestricted, pause-disabled, toggle, empty distract, system-settings render (`:130`), pause dialog render (`:139`), back-from-Settings (`:153`), display intent (`:166`), wallpaper row resolves system picker (`:176`) |

Docs: `android-capability-matrix.md` (24 lines), `android-system-boundaries.md` (67), `privacy-and-permissions-audit.md` (74), `lock-screen-feasibility.md` (87, includes apply instructions + per-OEM lock guidance), `system-behavior-audit.md` (247, prior baseline), this file.

## Supported (verified)

- Single-activity Compose nav HOME/APPS/SETTINGS/ONBOARDING (`LauncherActivity.kt:121-126`); back-to-home from APPS (`:112-114`); back-to-origin from SETTINGS (`:109-111`, `LauncherViewModel.kt:255-258`).
- Discovery via `LauncherApps` + pinned shortcuts (`AppRepository.kt:54`; `ShortcutRepository.kt:52,59`); launch with toast fallback (`AppLauncher.kt:20-29,55-67`).
- Visual-only hide + 6s undo (`LauncherViewModel.kt:317-339`); second hide replaces note (`:320-324`).
- Pause interpose: distract-listed + flag on opens dialog (`LauncherViewModel.kt:287-294`); Continue launches once (`:297-300`); Cancel drops (`:303`).
- Drawer scroll retained across Settings re-entry (`AppListScreen.kt:65-68,145-150` plus `drawerIndex/Offset`).
- Local-only prefs (`SettingsStore.kt:19-22`); backup disabled (`AndroidManifest.xml:19-21`).
- Search ranked accent-insensitive (`AppQuery.kt:39-59`); favourites keep order, dead keys dropped (`:34-37`).
- Onboarding one page, Back dismisses (`OnboardingScreen.kt:39`); re-openable from Settings (`SettingsScreen.kt:263`).
- `isLoading` true until first enumeration finishes, `finally` clears (`LauncherViewModel.kt:107,231-240`); drawer shows "Looking for apps…" (`AppListScreen.kt:137`).
- Honest System settings shortcuts (`SettingsScreen.kt:224-246`): Internet & Wi-Fi opens `Settings.Panel.ACTION_INTERNET_CONNECTIVITY` on API >= Q else `Settings.ACTION_WIFI_SETTINGS` (`:230-237`); Display & lock screen opens `Settings.ACTION_DISPLAY_SETTINGS` (`:238-240`); Sound & notifications opens `Settings.ACTION_SOUND_SETTINGS` (`:241-243`); Wallpaper opens `Intent.ACTION_SET_WALLPAPER` (`:244-246`). Helper retries the fallback action then toasts "Settings page unavailable" (`:319-332`); every dispatch carries `FLAG_ACTIVITY_NEW_TASK`.
- Bundled monochrome wallpapers are repo assets, not shipped UI (`app/src/main/res/drawable/wallpaper_mono_dark.xml`, `wallpaper_mono_light.xml`, 15 lines each): no `app/src/main` code loads them, so they must be rendered/exported and chosen in the system picker by hand. Application steps and per-OEM notification guidance live in `docs/lock-screen-feasibility.md:62-72`. No `WallpaperManager` call and no `SET_WALLPAPER` permission: applying stays with the system picker, which the Wallpaper row opens.

## Corrected against prior audit claims

- `goHome` still wipes query (`LauncherViewModel.kt:260-264`): retained, not fixed. In-drawer clear uses `clearQuery()` (`:281`).
- `LazyListState` now present (`AppListScreen.kt:65-68,151`): prior no-state defect fixed by this pass.
- No screen animation still true: comments state no animation library (`AppListScreen.kt:63,143`); instant swap.
- Drawer-entry keyboard pop is deliberate autofocus (`AppListScreen.kt:59-61` `LaunchedEffect(Unit)` + `requestFocus`): by design, not defect.
- Back-from-Settings keeps query via `settingsReturn` (`LauncherViewModel.kt:249-258`); `goHome` resets it (`:262`).

## Unsupported / Deferred (verified absent)

Recents control beyond self-exclusion; shade/QS tiles; lock screen; connectivity toggles; app suspend/block; usage stats; Custom Tabs. Full per-surface table in `docs/android-system-boundaries.md`.
System settings links are now implemented as honest system-page dispatchers (`SettingsScreen.kt:230-246`) rather than duplicated toggles; `Settings.ACTION_HOME_SETTINGS` remains the default-home link (`SettingsScreen.kt:142-143`, `OnboardingScreen.kt:95`). Bluetooth and Digital Wellbeing settings links remain absent. Wallpaper application remains OS-owned with no `WallpaperManager` calls and no `SET_WALLPAPER` permission (`LauncherActivity.kt:91-101`); the Wallpaper row only opens `Intent.ACTION_SET_WALLPAPER`, and the two bundled vectors (`app/src/main/res/drawable/wallpaper_mono_{dark,light}.xml`) are repository assets for manual use, not applied by the app.
Pause is launcher-level pre-launch only, Chrome URLs not intercepted (`SettingsScreen.kt:204-208`).

## Limitations

- Static audit + uncommitted diff only. CI (`.github/workflows/build-apk.yml`: JDK 21, `testDebugUnitTest`, `lintDebug`) not re-run in this pass.
- 49 unit tests counted by `@Test` annotations; the assertion "49 pass" is a source count, not a recorded run in this pass.
- Robolectric returns no installed apps; enumeration, shortcut gating, undo timer unverified at runtime.
- OEM behavior, managed profiles, API 26 vs 36 bars unverified without device.
- Pause dialog visuals (monochrome, spacing) asserted in code only, no screenshot taken.
- System settings shortcuts asserted only as an intent action via Robolectric shadow (`PauseGateTest.kt:167-181`); no real Settings activity launched.
- Bundled wallpaper vectors (`app/src/main/res/drawable/wallpaper_mono_{dark,light}.xml`) are repo assets for manual rendering, not loaded by any `app/src/main` code; visual fit on real devices unverified. The Wallpaper row dispatches `Intent.ACTION_SET_WALLPAPER` and asserts nothing about the assets.

## Manual tests still required (not claimed passing)

- Pause on/off x listed/unlisted app; Continue launches once; Cancel launches nothing; dismiss returns to list.
- Back Settings-to-drawer keeps query and scroll; Back drawer-to-home; Home wipes query.
- Drawer re-entry restores scroll position after Settings detour.
- Drawer keyboard/focus on entry and re-entry; Search IME action hides keyboard (`AppListScreen.kt:96-104`).
- Undo-hide 6s timer expiry; second hide replaces note.
- Set-as-default flow via both `ACTION_HOME_SETTINGS` intents; pinned shortcuts default vs not.
- Each System settings row on a real device, including the pre-Q Wi-Fi fallback and the `ActivityNotFoundException` toast path.
- `excludeFromRecents` + `singleTask` return-to-home.
- Airplane-mode full run; zero permission dialogs; no egress.

## Release containing this audit

0.2.3 (versionCode 5) is the release that contains the **earlier** audit described in the
sections above. Its APK SHA-256 is
`57dfa7a13d910fcf014fa97fa4cdc005f272908d9312de23329fbb5c60cca0f2`. That build is
**NOT device-validated** — every runtime claim above remains unverified on hardware, and the
manual tests listed in the previous section are still outstanding.

## Release containing the multi-page Home work

The persistent multi-page Home described in `docs/implementation-plan.md` ships in
**`debug-0.2.4`, `versionCode 6`** (`versionName 0.2.4`). Its APK SHA-256 is
`30b40e93df1db20023127e993bf319a081bd3358531853ec2cbf1a5a8717ed6b`, size 11,803,510 bytes.
It is a debug-key build published as a pre-release, and it installs **in place over
`debug-0.2.3` as an upgrade, preserving existing data** — the debug key is stable across
builds and `versionCode` was raised from 5 to 6, which is what makes Android accept the
update rather than refuse it. The Home-layout migration is non-destructive: the legacy flat
`favourites` key is read during migration and left on disk, so a downgrade to 0.2.3 still
finds it.

**Nothing in this feature has been validated on real hardware.** There is no device or
emulator run behind any of it: the pager, the edit mode, the persistence and the migration
are supported by source inspection and unit tests only. Every runtime and OEM-specific
claim — gesture-edge behaviour under gesture navigation, install-over-upgrade on a real
device, appearance under a light or dark system theme — remains unverified. Treat 0.2.4 as
a test build, which is what the `debug-*` pre-release marking already says.

### What the implementation actually reaches

Partly reachable, and less than `docs/implementation-plan.md` originally described. See
§10 of that document for the itemised list. In short: the pager, tap-to-switch page
indicator, persisted active page, edit mode (rename / up / down / remove / add), the Today
page with honest empty states, the Launch page with drag-reorder and pin/unpin, and the
Focus page's live note field all work. `PageType.WIDGET` is unreachable
(`SettingsStore.setPageType` has zero callers), a page's type and heading cannot be edited
(`updatePageConfig` has no production caller), and `PageConfig.showClock` is stored but
never read by `ui/`. `PageConfig.columns`, `PageMove`, `activeAppsPageId`,
`LauncherViewModel.movePageItem` and `SettingsStore.HOME_PAGES_VERSION` have no callers at
all. `ui/SettingsScreen.kt` also still carries stale copy — it places the Today block
"between the date and your pinned apps" and calls the pinned section "Essentials", when the
pinned list now lives on the Launch page and is edited by dragging in Home.
