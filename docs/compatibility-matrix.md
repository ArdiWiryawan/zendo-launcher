# Compatibility Matrix

Primary test device designation: TECNO CAMON 40. No TECNO-specific code
exists in the tree (verified: no `Build.MANUFACTURER`/`Build.BRAND` branches
anywhere under `app/src/main`); behavior differences below are handled
through public-API checks, not OEM hacks.

## Legend

- ✅ shipped, unit-verified (Robolectric SDK 36 + CI `testDebugUnitTest`)
- 🔶 shipped, unit-verified, hardware/OEM behavior unverified
- ❌ not available to an ordinary app — honest empty state / fallback instead

## API levels (minSdk 26, targetSdk 36, compileSdk 36)

| Area | 26–28 | 29–33 | 34–36 | Notes |
| --- | --- | --- | --- | --- |
| Launcher role (`CATEGORY_HOME`) | ✅ | ✅ | ✅ | singleTask root, system owns the picker |
| Predictive Back (`enableOnBackInvokedCallback`) | n/a (opt-in from 33) | ✅ 33+ | ✅ | no custom transitions to conflict |
| `FLAG_LOCK` wallpaper | ✅ (needs 24+) | ✅ | ✅ | OEM-honoring varies 🔶 |
| Calendar provider | ✅ | ✅ | ✅ | runtime `READ_CALENDAR` all levels 🔶 |
| Edge-to-edge + insets | ✅ | ✅ | ✅ | `enableEdgeToEdge`, `systemBarsPadding`, IME order |
| Notification shade / QS tiles | ❌ | ❌ | ❌ | system-owned; settings intents only |

## OEM dimensions (no device runs claimed)

| Dimension | Approach | Status |
| --- | --- | --- |
| Gesture vs 3-button nav | no custom gesture code; pager + chrome swipes only; insets via platform | 🔶 |
| Battery optimization killing enumerate callbacks | `LauncherApps.Callback` re-registers on `onStart`; refresh button always available | 🔶 |
| OEM lock replacements ignoring `FLAG_LOCK` | Partial result + picker fallback | 🔶 |
| OEM calendar provider quirks | queries guarded → empty, never crash | 🔶 |
| Font/display-scale extremes | fitted clock (`clockSizeSp`), wrapping rows, 48dp targets | ✅ (unit) / 🔶 (visual) |
| Work profile calendars/apps | per-profile enumeration via platform; policy denial → honest message | 🔶 |

## Permissions

| Permission | Type | When asked | Revocation |
| --- | --- | --- | --- |
| `SET_WALLPAPER` | normal | install (auto-grant) | picker fallback covers loss |
| `READ_CALENDAR` | runtime | explicit Allow tap only | resume re-check → calm off-state |
| everything else | — | never | — (`INTERNET` undeclared) |

## Verification log

| What | Where | Result |
| --- | --- | --- |
| 180 unit tests ( navigating, stores, editors, permission, wallpaper) | local + CI | pass |
| lint + assembleDebug | local + CI Build APK | success |
| TECNO CAMON 40 | — | NOT performed (no device access) |
| Any OEM hardware / emulator | — | NOT performed |

Nothing in this file claims universal support. Every 🔶 row needs the
device checklist in `docs/implementation-report.md` before it becomes ✅.
