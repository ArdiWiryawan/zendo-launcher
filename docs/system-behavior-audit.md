# System Behavior Audit

**Last Updated:** 2026-10-14

## A. Home Screen and App Drawer

### Single-activity Compose Architecture
- **Behavior:** The entire launcher UI is built within a single Android `Activity` (`LauncherActivity`), leveraging Jetpack Compose for navigation between different screens (HOME, APPS, SETTINGS, ONBOARDING). UI state is managed by `LauncherViewModel`.
- **Implementation:**
    - `app/src/main/java/com/zendolauncher/LauncherActivity.kt:104` (`LauncherRoot` composable, `state` parameter)
    - `app/src/main/java/com/zendolauncher/ui/LauncherViewModel.kt:40` (`UiState` data class)
    - `app/src/main/java/com/zendolauncher/ui/LauncherViewModel.kt:228` (navigation functions like `goHome()`, `openApps()`)
- **Owner:** Launcher
- **Possible:** Efficient resource management, simplified lifecycle handling.
- **Limits:** None observed.
- **Solution:** N/A
- **APIs/Perms:** Jetpack Compose, `ViewModel`
- **UX Gain:** Fast screen transitions, consistent UI.
- **Compat Risk:** Low
- **Priority:** P0

### Back Handling
- **Behavior:** The Android system's back gesture/button is intercepted by `BackHandler` in `LauncherActivity`. When on APPS or SETTINGS screens, the back action returns to the HOME screen. ONBOARDING is treated as a distinct flow, also allowing back to HOME.
- **Implementation:**
    - `app/src/main/java/com/zendolauncher/LauncherActivity.kt:107` (`BackHandler` composable)
- **Owner:** Launcher
- **Possible:** Intuitive navigation flow within the launcher.
- **Limits:** None observed.
- **Solution:** N/A
- **APIs/Perms:** `androidx.activity.compose.BackHandler`
- **UX Gain:** Predictable back navigation.
- **Compat Risk:** Low
- **Priority:** P0

### App Drawer (AppListScreen) Behavior
- **Behavior:** The `AppListScreen` (app drawer) automatically focuses its search input field upon entry and applies `imePadding` for proper layout when the software keyboard appears.
- **Implementation:**
    - `app/src/main/java/com/zendolauncher/ui/AppListScreen.kt:59` (`LaunchedEffect` for `focusRequester.requestFocus()`)
    - `app/src/main/java/com/zendolauncher/ui/AppListScreen.kt:71` (`.imePadding()`)
- **Owner:** Launcher
- **Possible:** Ready-to-type search experience.
- **Limits:** None observed.
- **Solution:** N/A
- **APIs/Perms:** `androidx.compose.ui.focus.FocusRequester`, `androidx.compose.foundation.layout.imePadding`
- **UX Gain:** Faster app searching, better keyboard integration.
- **Compat Risk:** Low
- **Priority:** P0

#### Known nav defects (verified, unfixed)
- Query wiped on goHome: `app/src/main/java/com/zendolauncher/ui/LauncherViewModel.kt:227-230` sets HOME and clears `_query`. Back also routes to `goHome()` (`app/src/main/java/com/zendolauncher/LauncherActivity.kt:107-109`), so drawer search text is lost on every exit.
- No list-state retention: `app/src/main/java/com/zendolauncher/ui/AppListScreen.kt:134` builds `LazyColumn` with no `rememberLazyListState` (grep-verified absent); scroll position is not kept.
- No screen animation: `app/src/main/java/com/zendolauncher/LauncherActivity.kt:116-121` is a bare `when` swap; no `AnimatedContent` / `Crossfade` / `NavHost` (grep-verified absent) — instant cut.
- Keyboard pops on every drawer entry: `app/src/main/java/com/zendolauncher/ui/AppListScreen.kt:59` `LaunchedEffect(Unit)` requests focus unconditionally, including re-entry.

### Manifest Flags
- **Behavior:** `LauncherActivity` uses `android:stateNotNeeded="true"` and `android:clearTaskOnLaunch="true"` in its manifest declaration. `android:excludeFromRecents="true"` is also set.
- **Implementation:**
    - `app/src/main/AndroidManifest.xml:36` (`android:stateNotNeeded="true"`)
    - `app/src/main/AndroidManifest.xml:31` (`android:clearTaskOnLaunch="true"`)
    - `app/src/main/AndroidManifest.xml:33` (`android:excludeFromRecents="true"`)
- **Owner:** OS (via manifest declaration)
- **Possible:** `stateNotNeeded` allows the system to kill the activity without preserving its state, simplifying state management. `clearTaskOnLaunch` ensures a fresh task when launched. `excludeFromRecents` prevents the launcher from appearing in the Android recents screen.
- **Limits:** None observed.
- **Solution:** N/A
- **APIs/Perms:** Android Manifest flags
- **UX Gain:** More robust and predictable launcher behavior, cleaner recents screen.
- **Compat Risk:** Low
- **Priority:** P0

### Gesture Conflict and Edge-to-Edge
- **Behavior:** No custom gesture detection or conflict resolution logic is implemented. The UI uses `edgeToEdge()` and `systemBarsPadding()` for a modern edge-to-edge display experience, handling system insets automatically.
- **Implementation:**
    - `app/src/main/java/com/zendolauncher/LauncherActivity.kt:52` (`enableEdgeToEdge()`)
    - `app/src/main/java/com/zendolauncher/ui/HomeScreen.kt:61`, `app/src/main/java/com/zendolauncher/ui/AppListScreen.kt:70` (`.systemBarsPadding()`)
- **Owner:** Launcher/OS (Compose + `WindowCompat`)
- **Possible:** Seamless integration with system gestures, full-screen immersive UI.
- **Limits:** None observed.
- **Solution:** N/A
- **APIs/Perms:** `androidx.activity.enableEdgeToEdge`, `WindowCompat`, `androidx.compose.foundation.layout.systemBarsPadding`
- **UX Gain:** Modern full-screen look, no gesture conflicts with system.
- **Compat Risk:** Low
- **Priority:** P0

## B. App Launch and Discovery

### App Launch Mechanism
- **Behavior:** Apps are launched primarily using `LauncherApps.startMainActivity()` for main activities and `LauncherApps.startShortcut()` for pinned shortcuts. A fallback mechanism is in place using `PackageManager.getLaunchIntentForPackage()` if `LauncherApps` fails.
- **Implementation:**
    - `app/src/main/java/com/zendolauncher/platform/AppLauncher.kt:34` (`launcherApps.startMainActivity`)
    - `app/src/main/java/com/zendolauncher/platform/AppLauncher.kt:44` (`launcherApps.startShortcut`)
    - `app/src/main/java/com/zendolauncher/platform/AppLauncher.kt:56` (`fallbackIntent` method)
- **Owner:** OS (via `LauncherApps`), Launcher (fallback logic)
- **Possible:** Reliable app launching, handles various app types (standard, shortcuts).
- **Limits:** None observed.
- **Solution:** N/A
- **APIs/Perms:** `android.content.pm.LauncherApps`, `PackageManager`
- **UX Gain:** Consistent and robust app launch experience.
- **Compat Risk:** Low
- **Priority:** P0

### App Discovery Mechanism
- **Behavior:** App discovery relies on `AppRepository` which queries `LauncherApps.getActivityList()` to retrieve available `MAIN`/`LAUNCHER` activities. It specifically avoids requesting `QUERY_ALL_PACKAGES` permission, adhering to stricter Android 11+ package visibility rules.
- **Implementation:**
    - `app/src/main/java/com/zendolauncher/platform/AppRepository.kt:54` (`launcherApps.getActivityList(null, user)`)
    - `app/src/main/AndroidManifest.xml:7` (comment regarding `LauncherApps` and `<queries>`)
    - `app/src/main/AndroidManifest.xml:13` (`<action android:name="android.intent.action.MAIN" />`)
    - `app/src/main/AndroidManifest.xml:14` (`<category android:name="android.intent.category.LAUNCHER" />`)
- **Owner:** OS (via `LauncherApps`), Launcher (`AppRepository`)
- **Possible:** Efficient and privacy-preserving app discovery; only visible apps are queried.
- **Limits:** Will only discover apps explicitly exposing `MAIN`/`LAUNCHER` intent filters, or those pre-declared in the manifest `<queries>` block. Does not see all packages on the device without `QUERY_ALL_PACKAGES`.
- **Solution:** N/A (deliberate design choice for privacy and modern Android standards)
- **APIs/Perms:** `android.content.pm.LauncherApps`, `<queries>` manifest tag
- **UX Gain:** Faster app list loading, improved user privacy.
- **Compat Risk:** Low (follows modern platform guidelines)
- **Priority:** P0

### Shortcut Discovery (PWA support)
- **Behavior:** `ShortcutRepository` retrieves pinned shortcuts from other apps using `LauncherApps.getShortcuts()` with `LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED`. This allows the launcher to display Progressive Web Apps (PWAs) installed via browsers.
- **Implementation:**
    - `app/src/main/java/com/zendolauncher/platform/ShortcutRepository.kt:52` (`setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)`)
    - `app/src/main/java/com/zendolauncher/platform/ShortcutRepository.kt:59` (`launcherApps.getShortcuts(query, user)`)
- **Owner:** OS (via `LauncherApps`), Launcher (`ShortcutRepository`)
- **Possible:** Integration of PWAs into the app list.
- **Limits:** Requires the launcher to be set as the default Home app to read pinned shortcuts from other apps.
- **Solution:** N/A
- **APIs/Perms:** `android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED`
- **UX Gain:** Displays PWA shortcuts alongside native apps.
- **Compat Risk:** Low
- **Priority:** P1

### Runtime Permissions
- **Behavior:** No runtime permissions are requested by the Zendo Launcher.
- **Implementation:**
    - `app/src/main/AndroidManifest.xml:6` (comment: "No runtime permissions are requested.")
- **Owner:** Launcher (deliberate omission)
- **Possible:** Enhanced user privacy, simplified installation and usage.
- **Limits:** Restricted from accessing sensitive user data or system functionalities that require runtime permissions (e.g., location, contacts, storage).
- **Solution:** N/A (deliberate design choice)
- **APIs/Perms:** N/A
- **UX Gain:** Privacy-first design, no permission prompts.
- **Compat Risk:** Low
- **Priority:** P0

## C. Lock Screen

### Lock Screen Interaction
- **Behavior:** Zendo Launcher has no specific code for interacting with the lock screen (e.g., widgets, custom lock screen UI, bypassing lock).
- **Implementation:** No relevant code found in grep results for `Keyguard`, `lock screen`.
- **Owner:** OS
- **Possible:** N/A (not implemented)
- **Limits:** Cannot provide launcher functionality on the lock screen.
- **Solution:** N/A
- **APIs/Perms:** None
- **UX Gain:** Focuses on core launcher experience without lock screen complexities.
- **Compat Risk:** Low
- **Priority:** P2

## D. Quick Settings

### Quick Settings Tiles
- **Behavior:** Zendo Launcher does not provide any custom Quick Settings tiles.
- **Implementation:** No relevant code found in grep results for `TileService`.
- **Owner:** OS
- **Possible:** N/A (not implemented)
- **Limits:** Cannot offer quick actions directly from Quick Settings.
- **Solution:** N/A
- **APIs/Perms:** None
- **UX Gain:** Simplification, avoiding potential Quick Settings congestion.
- **Compat Risk:** Low
- **Priority:** P2

### Settings Integration
- **Behavior:** The `SettingsScreen` within the launcher is purely internal. It does not launch external system settings intents (except for the specific "Open Home app settings" intent to navigate the user to the system's default launcher settings).
- **Implementation:**
    - `app/src/main/java/com/zendolauncher/ui/SettingsScreen.kt:139` (`Intent(Settings.ACTION_HOME_SETTINGS)`)
    - No other `android.content.Intent` actions related to system settings found in `SettingsScreen.kt`.
- **Owner:** Launcher (internal), OS (for `ACTION_HOME_SETTINGS`)
- **Possible:** Self-contained settings experience.
- **Limits:** Users must navigate to system settings manually for other device configurations.
- **Solution:** N/A
- **APIs/Perms:** `android.provider.Settings.ACTION_HOME_SETTINGS`
- **UX Gain:** Clear separation of launcher settings from system settings.
- **Compat Risk:** Low
- **Priority:** P1

## E. Wellbeing and Restrictions

### Digital Wellbeing Features
- **Behavior:** Zendo Launcher does not implement any digital wellbeing features, app usage restrictions, pausing capabilities, or usage statistics collection. It does not use `AccessibilityService` or any background services for monitoring.
- **Implementation:** No relevant code found in grep results for `UsageStats`, `DevicePolicy`, `AccessibilityService`, `background service`.
- **Owner:** N/A (not implemented)
- **Possible:** N/A (not implemented)
- **Limits:** Cannot enforce usage limits or provide wellbeing insights.
- **Solution:** N/A
- **APIs/Perms:** None
- **UX Gain:** Simplicity, no interference with user's app usage.
- **Compat Risk:** Low
- **Priority:** P2

### App Hiding (Visual Only)
- **Behavior:** `SettingsStore.hidden` provides a mechanism for users to visually hide apps from the launcher list. This is purely a visual filter within the launcher and does not restrict app execution or visibility in the system.
- **Implementation:**
    - `app/src/main/java/com/zendolauncher/data/SettingsStore.kt:33` (`hidden = prefs.getString(KEY_HIDDEN, "").orEmpty().toKeyList().toSet()`)
    - `app/src/main/java/com/zendolauncher/data/SettingsStore.kt:64` (`fun toggleHidden(key: String)`)
- **Owner:** Launcher
- **Possible:** User customization of app visibility.
- **Limits:** Does not prevent hidden apps from running, being launched via other means, or appearing in system settings.
- **Solution:** N/A
- **APIs/Perms:** `SharedPreferences`
- **UX Gain:** De-cluttering the app list for the user.
- **Compat Risk:** Low
- **Priority:** P1

## Toolchain and CI

### Build Toolchain
- **Behavior:** The project builds with `compileSdk 36`, Android Gradle Plugin (AGP) `8.13.2`, Kotlin `2.4.21`.
- **Implementation:**
    - `app/build.gradle:16` (`compileSdk 36`)
    - `build.gradle:2` (`id 'com.android.application' version '8.13.2' apply false`)
    - `build.gradle:3` (`id 'org.jetbrains.kotlin.android' version '2.4.21' apply false`)
- **Owner:** Launcher project configuration
- **Possible:** Standard Android development stack.
- **Limits:** Tied to specific versions; upgrades may require compatibility checks.
- **Solution:** N/A
- **APIs/Perms:** Gradle, Android SDK
- **UX Gain:** Stable development environment.
- **Compat Risk:** Low
- **Priority:** P0

### Continuous Integration (CI)
- **Behavior:** CI pipeline (`.github/workflows/build-apk.yml`) runs on `push` to `main` and `tags` (v*), and manually via `workflow_dispatch`. It uses JDK 21 to `assembleDebug`/`assembleRelease`, `testDebugUnitTest`, and `lintDebug`.
- **Implementation:**
    - `.github/workflows/build-apk.yml:3` (`on:`)
    - `.github/workflows/build-apk.yml:15` (`branches: ['main']`)
    - `.github/workflows/build-apk.yml:39` (`java-version: '21'`)
    - `.github/workflows/build-apk.yml:59` (`testDebugUnitTest`)
    - `.github/workflows/build-apk.yml:62` (`lintDebug`)
    - `.github/workflows/build-apk.yml:65` (`assemble${{ steps.variant.outputs.name == 'release' && 'Release' || 'Debug' }}`)
- **Owner:** Project CI configuration
- **Possible:** Automated testing and build verification for every change.
- **Limits:** None observed.
- **Solution:** N/A
- **APIs/Perms:** GitHub Actions
- **UX Gain:** Ensures code quality and prevents regressions.
- **Compat Risk:** Low
- **Priority:** P0
