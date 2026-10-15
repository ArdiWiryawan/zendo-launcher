# Graph Report - Zendo Launcher  (2026-10-14)

## Corpus Check
- 42 files · ~76,390 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 468 nodes · 631 edges · 29 communities (25 shown, 4 thin omitted)
- Extraction: 91% EXTRACTED · 9% INFERRED · 0% AMBIGUOUS · INFERRED: 54 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `9435a396`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- AppListScreen
- P1 — high-impact UX
- LauncherViewModel
- LaunchTarget
- ScreenshotCaptureTest
- LauncherUiTest
- System Behavior Audit
- 1. Implementation summary
- ZendoDayPlan
- Design reference audit
- SettingsStore
- PauseGateTest
- Zendo integration audit
- AppQueryTest
- Candidate repositories
- UI/UX review — before and after screenshots
- Lock Screen Feasibility
- Privacy and Permissions Audit
- P0 — essential
- Build and release
- Design system
- Zendo Launcher
- Android System Boundaries
- Implementation Report
- gradlew
- Android Capability Matrix

## God Nodes (most connected - your core abstractions)
1. `LauncherViewModel` - 53 edges
2. `LauncherUiTest` - 30 edges
3. `LaunchTarget` - 27 edges
4. `AppListScreen()` - 14 edges
5. `SettingsScreen()` - 14 edges
6. `PauseGateTest` - 14 edges
7. `P1 — high-impact UX` - 14 edges
8. `1. Implementation summary` - 13 edges
9. `AppRow()` - 12 edges
10. `HomeScreen()` - 12 edges

## Surprising Connections (you probably didn't know these)
- `Clock()` --calls--> `clockSizeSp()`  [INFERRED]
  app/src/main/java/com/zendolauncher/ui/HomeScreen.kt → app/src/main/java/com/zendolauncher/ui/theme/Theme.kt
- `LauncherActivity` --references--> `LauncherViewModel`  [EXTRACTED]
  app/src/main/java/com/zendolauncher/LauncherActivity.kt → app/src/main/java/com/zendolauncher/ui/LauncherViewModel.kt
- `LauncherRoot()` --calls--> `AppListScreen()`  [INFERRED]
  app/src/main/java/com/zendolauncher/LauncherActivity.kt → app/src/main/java/com/zendolauncher/ui/AppListScreen.kt
- `LauncherRoot()` --calls--> `PauseDialog()`  [INFERRED]
  app/src/main/java/com/zendolauncher/LauncherActivity.kt → app/src/main/java/com/zendolauncher/ui/Components.kt
- `LauncherRoot()` --calls--> `HomeScreen()`  [INFERRED]
  app/src/main/java/com/zendolauncher/LauncherActivity.kt → app/src/main/java/com/zendolauncher/ui/HomeScreen.kt

## Import Cycles
- None detected.

## Communities (29 total, 4 thin omitted)

### Community 0 - "AppListScreen"
Cohesion: 0.11
Nodes (35): Alignment, LauncherRoot(), AppListRow(), AppListScreen(), LetterHeader(), letterOf(), AppIcon(), AppRow() (+27 more)

### Community 1 - "P1 — high-impact UX"
Cohesion: 0.05
Nodes (37): How this was audited, Not verifiable in this environment, P1-10. Pinned-order controls are cramped, P1-11. Hiding an app has no undo and silently unpins it, P1-12. The toggle row's label is not tappable, P1-13. A false "no apps" state while the app list is still loading, P1-1. "Refresh" visibility is coupled to the icon layout, P1-2. No onboarding (+29 more)

### Community 2 - "LauncherViewModel"
Cohesion: 0.07
Nodes (12): AndroidViewModel, StateFlow, LauncherViewModel, Lists, Prefs, Screen, APPS, HOME (+4 more)

### Community 3 - "LaunchTarget"
Cohesion: 0.09
Nodes (10): android, AppQuery, LaunchTarget, AppLauncher, AppRepository, StateFlow, StateFlow, ShortcutRepository (+2 more)

### Community 4 - "ScreenshotCaptureTest"
Cohesion: 0.08
Nodes (18): ThemeMode, DARK, LIGHT, SYSTEM, LauncherActivity, SystemBarIcons(), clockSizeSp(), inLauncherPalette() (+10 more)

### Community 6 - "System Behavior Audit"
Cohesion: 0.08
Nodes (24): A. Home Screen and App Drawer, App Discovery Mechanism, App Drawer (AppListScreen) Behavior, App Hiding (Visual Only), App Launch Mechanism, B. App Launch and Discovery, Back Handling, Build Toolchain (+16 more)

### Community 7 - "1. Implementation summary"
Cohesion: 0.09
Nodes (22): 1. Implementation summary, 2. Before-and-after review, Also in the second pass, App drawer and search, App drawer and search: `ui/AppListScreen.kt`, Cold-start frame, Correction to the first pass, Deliverable status: screenshots produced (+14 more)

### Community 8 - "ZendoDayPlan"
Cohesion: 0.15
Nodes (7): TodaySelector, ZendoDayPlan, AgendaItem, ZendoToday, UnavailableZendoSource, ZendoSource, TodaySelectorTest

### Community 9 - "Design reference audit"
Cohesion: 0.12
Nodes (17): 1. SyncTasks, 2. Blank Spaces Launcher, 3. Dumb Phone (dp), 4. Light Phone, 5. minimal-launcher (RafaelGoulartB), 6. Olauncher (tanujnotes), 7. CCLauncher (mlm-games), 8. KISS (Neamar) (+9 more)

### Community 10 - "SettingsStore"
Cohesion: 0.19
Nodes (7): StateFlow, LauncherSettings, LayoutMode, ICON_TEXT, TEXT, SettingsStore, SharedPreferences

### Community 12 - "Zendo integration audit"
Cohesion: 0.14
Nodes (13): 1. No planning-data API, 2. What the Supabase integration does, 3. Exact data shape for the Today block, 4. Auth, 5. Row Level Security, Data contract available to the launcher, Explicit non-goals, Recommended integration boundary for the launcher (+5 more)

### Community 14 - "Candidate repositories"
Cohesion: 0.17
Nodes (11): 1. RafaelGoulartB/minimal-launcher, 2. tanujnotes/Olauncher, 3. mlm-games/CCLauncher, 4. Neamar/KISS, 5. mslalith/focus_launcher, Candidate repositories, Compliance decisions, Dependencies selected for this project (+3 more)

### Community 15 - "UI/UX review — before and after screenshots"
Cohesion: 0.17
Nodes (11): after (`main`, working tree described above), after (`pixel-stats-after.txt`), before (`2ac913a`), before (`pixel-stats-before.txt`), How they were produced, Images, Nothing was omitted silently, Pixel statistics, as observed (+3 more)

### Community 16 - "Lock Screen Feasibility"
Cohesion: 0.18
Nodes (10): Calm monochrome wallpaper assets & apply instructions, Device-specific lock-screen tips (Pixel, Samsung One UI, Xiaomi/ColorOS), Limitations, Lock Screen Feasibility, Lock-screen notification visibility guidance, Manual tests still required, Paths deliberately not taken, Related surfaces owned by OS (+2 more)

### Community 17 - "Privacy and Permissions Audit"
Cohesion: 0.20
Nodes (9): Data handling table, Discovery and launch surface, Keep-absent (grep-verified, no hits in `app/src`), Limitations, Manual tests still required, Network: none, Permissions: zero, Privacy and Permissions Audit (+1 more)

### Community 18 - "P0 — essential"
Cohesion: 0.20
Nodes (10): P0-1. The only popup in the app is off-palette (Material default surface), P0-2. Missing IME inset handling: the keyboard covers the drawer, P0-3. Search requires a manual tap every time and cannot be submitted, P0-4. The primary customisation action is hidden behind an undocumented long-press, P0-5. Touch targets below the 48dp minimum, P0-6. Selected state is communicated by colour alone, P0-7. The clock overflows at large system font scale, P0-8. Status-bar icon contrast ignores the in-app theme (+2 more)

### Community 19 - "Build and release"
Cohesion: 0.22
Nodes (9): Build and release, Debug vs release, Local build, Remote build (the primary path), The `debug-*` pre-releases, Troubleshooting, Verified toolchain, Versioning (+1 more)

### Community 20 - "Design system"
Cohesion: 0.22
Nodes (8): 1. Monochrome identity, 2. Typography, 3. Spacing, 4. Surface and border treatment, 5. Icons, 6. Interaction states, `clockSizeSp` fitting, Design system

### Community 21 - "Zendo Launcher"
Cohesion: 0.22
Nodes (9): Documentation, Download the APK, First run, Install it, Known limitations, Licence, Privacy, What it does (+1 more)

### Community 22 - "Android System Boundaries"
Cohesion: 0.25
Nodes (7): Android System Boundaries, Discovery and launch chain, Keep-absent inventory, Limitations, Manifest root, Manual tests still required, Per-surface boundary table

### Community 23 - "Implementation Report"
Cohesion: 0.25
Nodes (7): Changed this pass, Corrected against prior audit claims, Implementation Report, Limitations, Manual tests still required (not claimed passing), Supported (verified), Unsupported / Deferred (verified absent)

### Community 24 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

## Knowledge Gaps
- **181 isolated node(s):** `SYSTEM`, `LIGHT`, `DARK`, `TEXT`, `ICON_TEXT` (+176 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **4 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `LauncherViewModel` connect `LauncherViewModel` to `AppListScreen`, `LaunchTarget`, `ScreenshotCaptureTest`, `LauncherUiTest`, `ZendoDayPlan`, `SettingsStore`, `PauseGateTest`?**
  _High betweenness centrality (0.171) - this node is a cross-community bridge._
- **Why does `LaunchTarget` connect `LaunchTarget` to `AppListScreen`, `LauncherViewModel`, `ScreenshotCaptureTest`, `PauseGateTest`, `AppQueryTest`?**
  _High betweenness centrality (0.093) - this node is a cross-community bridge._
- **Why does `LauncherUiTest` connect `LauncherUiTest` to `AppListScreen`, `LauncherViewModel`, `ScreenshotCaptureTest`?**
  _High betweenness centrality (0.055) - this node is a cross-community bridge._
- **Are the 5 inferred relationships involving `LaunchTarget` (e.g. with `.app()` and `.shortcut()`) actually correct?**
  _`LaunchTarget` has 5 INFERRED edges - model-reasoned connections that need verification._
- **Are the 8 inferred relationships involving `AppListScreen()` (e.g. with `LauncherRoot()` and `EmptyNote()`) actually correct?**
  _`AppListScreen()` has 8 INFERRED edges - model-reasoned connections that need verification._
- **Are the 9 inferred relationships involving `SettingsScreen()` (e.g. with `LauncherRoot()` and `ChoiceRow()`) actually correct?**
  _`SettingsScreen()` has 9 INFERRED edges - model-reasoned connections that need verification._
- **What connects `SYSTEM`, `LIGHT`, `DARK` to the rest of the system?**
  _181 weakly-connected nodes found - possible documentation gaps or missing edges._