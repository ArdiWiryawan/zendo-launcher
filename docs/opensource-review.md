# Open-source review

Paper audit only. No repository was cloned and no code was copied. All facts below were read
from the GitHub REST API, raw `LICENSE` files on the default branch, and Maven POM files on
2026-10-13. Anything not directly observed is marked UNVERIFIED.

## Summary recommendation

None of the five candidates is a suitable **code** foundation for a permissively distributable
launcher. Four (Olauncher, CCLauncher, KISS, focus_launcher) are **GPL-3.0**, so adapting their
code would force the entire Zendo Launcher — every file, not just the borrowed ones — to be
released under GPL-3.0, with full corresponding source offered to anyone who receives the APK.
The fifth (RafaelGoulartB/minimal-launcher) has **no LICENSE file and no license field on GitHub**,
which by default means all rights reserved: reuse is not permitted at all, despite it being the
closest match in spirit (Kotlin + Compose, text-first, no internet permission).

Recommendation: build Zendo Launcher from scratch on Apache-2.0 AndroidX/Compose, and use all five
repositories strictly as **architectural and UX inspiration** (screen structure, list performance
approach, PWA-launch mechanics) with no code, strings, layouts, or assets copied. If a GPL-3.0
codebase is ever adapted, the decision must be explicit and the whole project relicensed GPL-3.0.

Note on the actual goal: none of the five repos was found to contain PWA / WebAPK / Trusted Web
Activity handling code (searched repo trees for `pwa`, `webapk`, `trustedweb`, `browserhelper`,
`twa`). PWA launching must be designed fresh regardless of the foundation choice.

## Candidate repositories

### 1. RafaelGoulartB/minimal-launcher
- URL: https://github.com/RafaelGoulartB/minimal-launcher
- License: **none declared**. GitHub API `license` field is `null`; `GET /repos/.../license`
  returns **404**; the default-branch root listing contains no `LICENSE` file; the README has no
  license section. Effect: all rights reserved, no reuse permission granted.
- Last activity: last commit **2026-09-06** ("feat: add website landing page and user
  documentation"). Repo created 2026-08-08. Small and young.
- Language / toolkit: **Kotlin + Jetpack Compose** (Compose BOM `2026.06.00`, Material3,
  `activity-compose`, `datastore-preferences` 1.2.1). 37 `.kt` files, 114 tree entries. Also
  ships an HTML/CSS website (`www/`) that inflates the language stats (HTML/CSS/JS).
- Stars: **2**.
- Complexity: low. Text-first home screen, searchable app drawer, folders, custom labels, hide
  apps, DataStore preferences, optional Usage Access for screen time. No INTERNET permission
  claimed in README. No widgets, weather, or icon packs. Architecturally the closest match.
- Reuse decision: **inspiration only** — and legally the strictest of the five, since no license
  means no grant. Do not copy code.
- License obligation if copied: none granted; copying would be a plain copyright infringement,
  not a licensing question.

### 2. tanujnotes/Olauncher
- URL: https://github.com/tanujnotes/Olauncher
- License: **GPL-3.0**. Verified twice: GitHub API `license.spdx_id = "GPL-3.0"`, and
  `raw.githubusercontent.com/tanujnotes/Olauncher/master/LICENSE` begins with the GNU General
  Public License Version 3, 29 June 2007 text.
- Last activity: last commit **2026-09-29** ("Load screen time off the main thread"); release
  `v6.9.17` published 2026-09-17. Actively maintained.
- Language / toolkit: **Kotlin + XML views** (`viewBinding = true`, AppCompat, RecyclerView,
  Material Components; 13 XML layouts). No Compose. 31 `.kt` files, 241 tree entries.
- Stars: **3872** (most popular of the five).
- Complexity: medium. Adds daily wallpapers (network), screen-time tracking, rename/hide,
  fastlane metadata and many translation locales.
- Reuse decision: **inspiration only**. UX patterns (gesture handling, text list layout) are
  useful to study; the code is not reusable under a permissive license.
- License obligation if copied: whole derived launcher must be GPL-3.0, source disclosed, and
  full corresponding source offered to every APK recipient.

### 3. mlm-games/CCLauncher
- URL: https://github.com/mlm-games/CCLauncher
- License: **GPL-3.0**. Verified: API `license.spdx_id = "GPL-3.0"`; `LICENSE` on the `compose`
  branch is the GPLv3 text.
- Last activity: last commit **2026-10-09** (a Dependabot dependency bump merged by
  github-actions). Maintenance is largely automated. Note: default branch is **`compose`**, not
  `main`.
- Language / toolkit: **Kotlin + Jetpack Compose** (Compose BOM, Koin DI, Navigation3,
  DataStore, Gson, Material Icons Extended). 54 `.kt` files, 290 tree entries.
- Stars: **97**.
- Complexity: medium-high for the size. DI framework, navigation graph, serialization, work
  manager, and a committed `release.keystore` at repo root. Includes
  `androidx.browser:androidbrowserhelper` as a dependency (Custom Tabs helper), but no WebAPK /
  TWA integration code was found.
- Reuse decision: **inspiration only**. The Compose rewrite is the most structurally similar
  Kotlin/Compose launcher here, but it is GPL-3.0.
- License obligation if copied: GPL-3.0 for the whole derived app, source disclosure, source
  offer to APK recipients.
- Security note: a `release.keystore` file is committed in the repository root. Do not reuse it
  or any build signing material from this repo.

### 4. Neamar/KISS
- URL: https://github.com/Neamar/KISS
- License: **GPL-3.0**. Verified: API `license.spdx_id = "GPL-3.0"`; `LICENSE` is GPLv3 text; the
  README explicitly states "Copylefted libre software, licensed GPLv3+".
- Last activity: last commit **2026-10-06** (Weblate translation commits); latest release
  `v3.24.2` published 2026-03-11. Maintained, though recent commits are mostly translations.
- Language / toolkit: **Java + XML views** (AppCompat, Fragment, RecyclerView, Preference;
  no Compose; 31 packages under `fr.neamar.kiss`, including `dataprovider`, `searcher`,
  `shortcut`, `notification`, `kustomsupport`, `icons`, `db`, `pojo`). 1977 tree entries;
  repo size ~167 MB.
- Stars: **3516**.
- Complexity: **high** — the largest codebase here by far. Contacts search, tags, widgets,
  Kustom support, notifications, root handler, DB layer, icon handlers, custom shortcuts,
  a `test/` and `qa-suite.md`. Far more surface than a minimalist text launcher needs.
- Reuse decision: **inspiration only**. Worth studying for app-list loading performance and
  search ranking ideas; nothing more.
- License obligation if copied: GPL-3.0 (README says GPLv3+), whole derived app, source
  disclosure, source offer to recipients.

### 5. mslalith/focus_launcher
- URL: https://github.com/mslalith/focus_launcher
- License: **GPL-3.0**. Verified: API `license.spdx_id = "GPL-3.0"`; `LICENSE` is GPLv3 text;
  README shows the "License: GPL v3" badge.
- Last activity: **effectively dormant on the default branch**. `main` last commit
  **2024-03-02**; the only recent commits are on `develop` and `renovate/*` branches
  (dependency bumps, latest 2026-10-08). Latest release `v0.9.0`, 2024-03-02. The API
  `pushed_at` of 2026-10-08 reflects bot branches, not shipped code.
- Language / toolkit: **Kotlin + Jetpack Compose**, heavily modularized
  (`core/`, `feature/`, `screens/`, `build-logic/`, `baselineprofile/`; Circuit, Hilt,
  Sentry, Profile Installer). 1267 tree entries; ~921 KB of Kotlin.
- Stars: **264**.
- Complexity: **high** for a "minimalist" launcher — icon packs, lunar phase, animated clock,
  favorites, hide apps, theme engine, Sentry crash reporting, baseline profiles.
- Reuse decision: **inspiration only**. The multi-module Gradle setup is the most instructive
  artifact here, but it is GPL-3.0 and over-engineered for this project's scope.
- License obligation if copied: GPL-3.0, whole derived app, source disclosure, source offer.

## Dependencies selected for this project

Licenses verified from the artifact POM on the vendor Maven repository on 2026-10-13.

| Dependency | Coordinate checked | License | Verified from |
|---|---|---|---|
| AndroidX Jetpack Compose (foundation) | `androidx.compose.foundation:foundation:1.7.0` | Apache-2.0 | Google Maven POM `<licenses>` = "The Apache Software License, Version 2.0" |
| AndroidX activity-compose | `androidx.activity:activity-compose:1.9.0` | Apache-2.0 | Google Maven POM |
| AndroidX core-ktx | `androidx.core:core-ktx:1.13.1` | Apache-2.0 | Google Maven POM |
| AndroidX DataStore Preferences | `androidx.datastore:datastore-preferences:1.1.1` | Apache-2.0 | Google Maven POM |
| Kotlin stdlib | `org.jetbrains.kotlin:kotlin-stdlib:2.0.0` | Apache-2.0 | Maven Central POM ("The Apache License, Version 2.0") |
| kotlinx-coroutines-core | `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1` | Apache-2.0 | Maven Central POM |
| `SharedPreferences` | n/a — Android framework API | Part of the Android platform (AOSP, Apache-2.0) | **UNVERIFIED as a discrete artifact**: it is an OS API, not a bundled library, so no separate POM exists to cite |

The exact versions above are the ones checked; the versions actually pinned in
`gradle/libs.versions.toml` were not read for this audit and should be re-checked against the
same POM `<licenses>` field if they differ materially.

## Compliance decisions

- Chosen approach: original code, Apache-2.0 licensed, with no GPL-3.0 code adapted. This keeps
  the APK permissively distributable and imposes **no** source-disclosure duty.
- Attribution obligations for Apache-2.0 dependencies: retain the license text and copyright
  notices, and state that the app uses Apache-2.0-licensed libraries. Ship a
  `NOTICE`/open-source-licenses screen or bundled license text file in the APK, and include the
  same notices in the distributed source. Apache-2.0 requires preserving notices and stating
  significant changes; it does **not** require disclosing Zendo Launcher's own source.
- Because all five reviewed launchers are excluded from code reuse (four GPL-3.0, one
  unlicensed), no GPL notice, source offer, or copyleft file needs to ship with the APK.
- Record in the repository that these five projects were reviewed and deliberately not reused,
  so a future contributor does not paste GPL-3.0 code in by accident.
- If PWA launch code is later taken from an AOSP sample (e.g. `androidx.browser` TWA samples),
  re-verify that specific file's license before copying; it is not covered by this audit.

## Unverified items

- **RafaelGoulartB/minimal-launcher license**: not merely unverified — confirmed *absent* (API
  `license: null`, `/license` 404, no `LICENSE` file, no README license section). Treated as
  all-rights-reserved. If the author adds a license later, this assessment must be revisited.
- **focus_launcher maintenance status**: `main` is stale since 2024-03-02; `pushed_at`
  (2026-10-08) comes from Renovate dependency branches. Whether the project is genuinely
  maintained is therefore ambiguous and marked UNVERIFIED.
- **CCLauncher default branch intent**: default is `compose`, and it is GPL-3.0, but whether a
  separate older non-Compose branch carries a different license was not checked. UNVERIFIED.
- **Android platform `SharedPreferences`**: no standalone license artifact to cite; treated as
  part of the Apache-2.0 AOSP platform. Marked UNVERIFIED as a discrete dependency.
- **PWA / WebAPK / TWA support in any of the five repos**: no matching source files were found
  in any repo tree. Absence of a match is strong evidence but was not a full-text code read;
  treat as "not found", not as a guaranteed negative.
- **Transitive dependency licenses**: only the direct dependencies above were checked. No
  transitive graph (Compose runtime, Material3, Koin, etc. pulled in by third-party code) was
  enumerated, since no third-party code is being reused.
- **Repo star counts, sizes and dates** are point-in-time values from the GitHub API on
  2026-10-13 and will drift.
