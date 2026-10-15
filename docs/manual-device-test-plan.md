# Manual Device Test Plan

**Status: NOT EXECUTED.**

This repository has never been run on real hardware. Every behavioural claim in
`docs/implementation-report.md` and `docs/system-behavior-audit.md` is derived from
JVM/Robolectric tests (`app/src/test/java/com/zendolauncher/ui/`) and static reading of
`app/src/main`. Robolectric returns no installed apps, launches no real Settings
activity, and cannot exercise OEM behaviour.

This document therefore records **required tests, not results**. No checkbox here may be
marked pass until a human runs it on a physical device and writes down the build
(`versionName`/`versionCode`, APK SHA-256), device model, and Android version.

Target build for this plan: 0.2.3 (`versionCode 5`), APK SHA-256
`57dfa7a13d910fcf014fa97fa4cdc005f272908d9312de23329fbb5c60cca0f2`
(`docs/implementation-report.md:75-80`). minSdk 26, targetSdk 36.

## Preconditions

- Install the APK on a physical device. Record model, Android version, and build hash.
- Set Zendo Launcher as the default home app (Settings > Apps > Default apps > Home app),
  or run the onboarding "set as default" link.
- Note the device has at least 3 user-installed apps so the drawer is not trivial.
- Keep a second launcher installed as a fallback in case the launcher misbehaves.

---

## A. Home and All Apps: open/close, Back, scroll retention, query restore

1. **Home opens**
   - Steps: Press Home from another app (or reboot and unlock).
   - Expected: Zendo Home renders; no crash; no blank frame persists.
   - FAILURE: crash, ANR, black/blank screen, or the previous app stays on top.

2. **Open All Apps**
   - Steps: From Home, trigger the All Apps / drawer entry.
   - Expected: drawer appears with the installed-app list; search field focused
     (`AppListScreen.kt:59-61`); "Looking for apps…" only while `isLoading`
     (`AppListScreen.kt:137`).
   - FAILURE: empty drawer with no loading text; drawer never appears; keyboard does not
     show (if expected); infinite "Looking for apps…".

3. **Close All Apps to Home**
   - Steps: Press Back while the drawer is open.
   - Expected: returns to Home (`LauncherActivity.kt:112-114`).
   - FAILURE: app exits to system default, drawer stays open, or nothing happens.

4. **Back from Settings returns to origin, not always Home**
   - Steps: Open Settings from the drawer, then press Back.
   - Expected: returns to the drawer, not Home (`LauncherViewModel.kt:249-258`,
     `settingsReturn`).
   - FAILURE: lands on Home (settings-origin not honoured).

5. **Drawer scroll-position retention**
   - Steps: Scroll the drawer partway down, open Settings, press Back to the drawer.
   - Expected: list restores to the same scroll offset
     (`AppListScreen.kt:65-68,145-150`; `drawerIndex/Offset`).
   - FAILURE: list jumps to top on return.

6. **Search-query restore from Settings**
   - Steps: Type a query in the drawer, open Settings, press Back.
   - Expected: query text is preserved (`settingsReturn`,
     `LauncherViewModel.kt:249-258`).
   - FAILURE: query cleared on return.

7. **Query cleared on Home (documented behaviour, confirm it is intended)**
   - Steps: Type a query, then navigate Home.
   - Expected: query is wiped (`goHome`, `LauncherViewModel.kt:260-264`) — this is
     recorded behaviour, not a bug.
   - FAILURE: query persists where the report says it should be cleared, OR the query
     persists as leftover state across a fresh drawer entry in a confusing way.

8. **Drawer keyboard/focus on re-entry**
   - Steps: Enter the drawer, dismiss the keyboard, return to Home, re-enter the drawer.
   - Expected: keyboard/autofocus behaves as documented on every entry
     (`AppListScreen.kt:59-61`, deliberate).
   - FAILURE: keyboard does not appear when it should, or focus is lost so typing goes
     nowhere.

---

## B. Launching an unrestricted app

9. **Unrestricted app launches**
   - Steps: With Intentional Pause OFF, tap any app not in the distract set.
   - Expected: the app opens directly, no dialog
     (`LauncherViewModel.kt:287-294`).
   - FAILURE: pause dialog appears for an unrestricted app; app does not open; wrong app
     opens.

10. **Launch fallback path**
    - Steps: Launch a normal app; also try a pinned shortcut if available.
    - Expected: `LauncherApps.startMainActivity()` succeeds; if it fails, the
      `PackageManager` fallback is used (`AppLauncher.kt:20-29,55-67`) and a toast
      reports the failure rather than a silent no-op.
    - FAILURE: silent failure with no toast; crash on launch.

---

## C. Intentional Pause flow — Continue launches, Cancel returns with no launch

11. **Pause dialog appears for a listed app**
    - Steps: Enable "Pause before opening", add an app to the distract set, tap that app.
    - Expected: `PauseDialog` overlay with the app label, "Take a breath. Open it
      anyway?", and Continue / Cancel (`Components.kt:353-377`).
    - FAILURE: no dialog; dialog with no label; wrong app name; two dialogs.

12. **Continue launches**
    - Steps: In the dialog, tap Continue.
    - Expected: the app launches exactly once (`confirmPause`,
      `LauncherViewModel.kt:297-300`).
    - FAILURE: nothing launches; app launches twice; dialog reappears.

13. **Cancel launches nothing**
    - Steps: In the dialog, tap Cancel.
    - Expected: dialog dismisses, no app launches, drawer/Home remains
      (`cancelPause`, `LauncherViewModel.kt:303`).
    - FAILURE: app launches anyway; dialog stuck; wrong screen shown.

14. **Dialog dismiss (back/outside tap) behaves as Cancel**
    - Steps: Show the dialog, then press Back or tap outside.
    - Expected: treated as Cancel (`Dialog(onDismissRequest = onCancel)`); no launch.
    - FAILURE: dismiss launches the app; dialog cannot be dismissed.

15. **No timer on the pause**
    - Steps: Show the dialog and wait ~30 s without touching it.
    - Expected: no countdown, no auto-launch, no auto-cancel — the dialog waits
      (`Components.kt` pause dialog, "no timer" per report).
    - FAILURE: auto-launch or auto-dismiss after a delay.

---

## D. Disabling pause and confirming direct launch

16. **Pause OFF = direct launch**
    - Steps: Turn "Pause before opening" off; tap an app still in the distract set.
    - Expected: app launches directly, no dialog (flag gating,
      `LauncherViewModel.kt:287-294`).
    - FAILURE: dialog still appears with the toggle off.

17. **Toggle persistence**
    - Steps: Toggle pause off/on, kill the launcher, reopen Settings.
    - Expected: setting persists (`SettingsStore.kt:39,54,114-115`, local prefs only).
    - FAILURE: setting resets; setting writes to a remote location (it must not — prefs
      are local, backup disabled).

---

## E. Denied-permission safe-fail (the app requests NO runtime permissions)

18. **Confirm no permission prompts**
    - Steps: Cold-start the launcher, open the drawer, launch an app, open Settings.
    - Expected: zero runtime-permission dialogs at any point
      (`AndroidManifest.xml` requests no permissions; app discovery is via
      `LauncherApps`, with only a `<queries>` MAIN/LAUNCHER block).
    - FAILURE: any permission dialog appears.

19. **Confirm the manifest on device, not just in source**
    - Steps: Run `adb shell dumpsys package com.zendolauncher | grep -i permission`
      (or inspect the installed package via Settings > Apps > Zendo Launcher >
      Permissions). Optionally `adb shell pm list permissions -g -d` is not needed; the
      per-package dump is authoritative.
    - Expected: no `requested permissions` beyond the implicit ones; no runtime
      permission group granted or requested; no `INTERNET` permission.
    - FAILURE: any dangerous/runtime permission is listed; `INTERNET` appears (the
      launcher ships no network client and the Zendo Today source is `Unavailable`).

20. **Safe-fail when a system page is unavailable**
    - Steps: From Settings, tap a System settings row on a device/OEM where the target
      activity is missing (e.g. an OEM build without the stock Settings action).
    - Expected: helper catches `ActivityNotFoundException`, retries the fallback action,
      then toasts "Settings page unavailable" (`SettingsScreen.kt:319-332`).
    - FAILURE: crash; silent no-op with no toast; wrong page opens.

---

## F. Process death / launcher restart state restoration

21. **Kill and relaunch the launcher**
    - Steps: From the drawer, background the launcher, then
      `adb shell am force-stop com.zendolauncher` (or use "Force stop"), then press Home.
    - Expected: launcher restarts cleanly to Home; no crash; drawer rebuilds; settings
      (pause flag, distract set, hidden apps) persist from local prefs.
    - FAILURE: crash on relaunch; settings lost; stale drawer content.

22. **Process death while in the drawer**
    - Steps: Enable "Don't keep activities" (Developer options), open the drawer, open
      another app, return Home.
    - Expected: launcher recovers; no crash; state is at worst reset to Home (the
      manifest declares `stateNotNeeded="true"` and `clearTaskOnLaunch="true"`, so a
      reset to Home is acceptable — a crash is not).
    - FAILURE: crash; corrupted UI; settings lost.

23. **Return-to-home from another app**
    - Steps: Launch an app from the launcher, use it, press Home.
    - Expected: Zendo Home returns; launcher does not appear in Recents
      (`excludeFromRecents="true"`); `singleTask` returns the existing task.
    - FAILURE: launcher appears in Recents; duplicate launcher tasks; app switch breaks.

---

## G. Power-button lock/unlock and secure lock screen

24. **Power button locks and unlocks normally**
    - Steps: Press power to lock; press power again; unlock with the device PIN/biometric.
    - Expected: the device's own lock screen appears; PIN/biometric auth works exactly as
      without the launcher; after unlock the launcher is where you left it.
    - FAILURE: lock screen bypassed; PIN/biometric rejected or skipped; launcher shown
      over the lock screen.

25. **Confirm the launcher claims no control over the secure lock screen**
    - Steps: Check Settings > Security (device PIN/biometrics still enforced), and
      confirm no "Zendo" entry under lock-screen/device-admin settings.
    - Expected: no device-admin, no lock-screen control, no `DISABLE_KEYGUARD` or
      lock-screen permissions (the manifest requests none); the app only declares
      `HOME`/`LAUNCHER`/`DEFAULT` intent filters.
    - FAILURE: launcher registered as device admin; any lock-screen API is in play; the
      device PIN can be disabled from within the launcher.

26. **Unlock lands on the launcher**
    - Steps: Lock the device while Home is showing, unlock.
    - Expected: Zendo Home is shown after auth (it is the default home app).
    - FAILURE: a different launcher or a blank screen appears.

---

## H. Notification shade and Quick Settings

27. **Shade opens**
    - Steps: Swipe down from the top of the Home screen and from the drawer.
    - Expected: the system notification shade opens normally; edge-to-edge layout does
      not block the gesture (`enableEdgeToEdge`, `systemBarsPadding`).
    - FAILURE: the swipe is swallowed; the shade does not open; UI is drawn under the
      shade incorrectly.

28. **Quick Settings opens**
    - Steps: Swipe down twice (or swipe down from the top-right) to open QS tiles.
    - Expected: QS opens; toggles work; closing QS returns to the launcher.
    - FAILURE: QS does not open; launcher intercepts the gesture; the launcher's own UI
      is left in a broken state after QS closes.

---

## I. Settings system-settings rows, pre-Q Wi-Fi fallback, ActivityNotFoundException toast

29. **Each System settings row resolves on device**
    - Steps: Tap each row in Settings > System settings:
      - Internet & Wi-Fi
      - Display & lock screen
      - Sound & notifications
      - Wallpaper
    - Expected:
      - Internet & Wi-Fi opens `Settings.Panel.ACTION_INTERNET_CONNECTIVITY` on API >= Q
        (`SettingsScreen.kt:230-237`).
      - Display & lock screen opens `Settings.ACTION_DISPLAY_SETTINGS`
        (`SettingsScreen.kt:238-240`).
      - Sound & notifications opens `Settings.ACTION_SOUND_SETTINGS`
        (`SettingsScreen.kt:241-243`).
      - Wallpaper opens `Intent.ACTION_SET_WALLPAPER` (`SettingsScreen.kt:244-246`).
      - Every dispatch carries `FLAG_ACTIVITY_NEW_TASK`.
    - FAILURE: any row is a no-op; the wrong page opens; a crash occurs.

30. **Pre-Android-Q Wi-Fi fallback**
    - Steps: On an API 26-28 device, tap Internet & Wi-Fi.
    - Expected: the Q-only panel action is not used; the code falls back to
      `Settings.ACTION_WIFI_SETTINGS` (`SettingsScreen.kt:230-237`).
    - FAILURE: crash on pre-Q; nothing opens; the panel action is dispatched and throws.

31. **ActivityNotFoundException toast path**
    - Steps: On a device/OEM where the target Settings action has no handler (or via a
      test build with the action removed), tap the affected row.
    - Expected: the helper catches `ActivityNotFoundException`, retries the fallback
      action, then toasts "Settings page unavailable" (`SettingsScreen.kt:319-332`).
    - FAILURE: crash; no toast; the user is left on a blank screen.

---

## J. Wallpaper picker flow and applying a bundled vector wallpaper

32. **Wallpaper picker opens**
    - Steps: Tap Settings > System settings > Wallpaper.
    - Expected: the system wallpaper picker opens (`Intent.ACTION_SET_WALLPAPER`).
    - FAILURE: nothing opens; crash; the wrong picker appears.

33. **Apply a bundled wallpaper directly (0.5.0)**
    - Steps: Settings → Wallpaper. Note the three designs. Apply Quiet Stone to
      Home; then apply "Less, but better" to Lock/Both on a device that reports a
      lock target.
    - Expected: "Applied to…" appears only after the system call returns; the
      artwork line sits below the top third, clear of the lock clock; on devices
      without a lock target only Home is offered, with the picker fallback line;
      a failed apply offers "Open system picker instead".
    - FAILURE: "Applied" before the API returns; text under the clock; Lock/Both
      offered on a device that cannot take them; crash.

34. **Wallpaper survives launcher restart**
    - Steps: Apply a wallpaper, restart the launcher.
    - Expected: wallpaper unchanged; the launcher stores only the selection and
      never reapplies anything on start.
    - FAILURE: launcher resets or overrides the wallpaper.

---

## K. PWA / pinned-shortcut launch

35. **Pinned shortcut appears**
    - Steps: As the default home app, confirm pinned shortcuts published by apps
      (including a PWA/WebAPK) appear in the launcher
      (`ShortcutRepository.kt:51-59`, `FLAG_MATCH_PINNED`; only the active home app may
      read pinned shortcuts, `:82`).
    - Expected: pinned shortcuts are listed and launch correctly via
      `LauncherApps.startShortcut()` (`AppLauncher.kt:44`).
    - FAILURE: shortcuts missing when the launcher IS the default home app; launch
      fails; wrong target opens.

36. **PWA / WebAPK launch**
    - Steps: Install/use a PWA (e.g. install a site as an app), find it, launch it.
    - Expected: the PWA opens. Android's own WebAPK flow registers a real launcher
      activity that `AppRepository` picks up directly (`ShortcutRepository.kt:16-19`).
    - FAILURE: PWA missing from the drawer; launch does nothing; crash.

37. **Shortcuts hidden when not the default home app**
    - Steps: With the launcher NOT set as default home, check whether pinned shortcuts
      are exposed.
    - Expected: pinned shortcuts are not available when this is not the active home app
      (`ShortcutRepository.kt:82`) — a platform limit, not a defect.
    - FAILURE: the launcher claims shortcuts it cannot legally read, then crashes or
      shows stale entries.

---

## L. Zendo bridge: manual snapshot import (0.5.0, no API shipped)

38. **Snapshot import: preview, merge and idempotence**
    - Steps: In the Zendo app, export the launcher snapshot; transfer the file to
      the device; Settings → Zendo → "Import a Zendo snapshot"; pick the file.
      Apply, then import the same file again. Then import once with "Replace
      imported".
    - Expected: a preview states the file's target date and exactly what would
      change; nothing is written before Apply. Re-importing reports "already
      applied" (no duplicate tasks). Merge never overwrites local text; Replace
      imported removes only records that came from Zendo for that date. A
      future-dated snapshot is rejected with a clear message.
    - FAILURE: import without preview; duplicates after re-import; local text
      overwritten; records from other dates wiped; any claim of live sync.

39. **No network egress**
    - Steps: Run the launcher under airplane mode, and separately observe traffic
      (e.g. `adb shell dumpsys netstats` or a packet capture) during normal use.
    - Expected: full run works in airplane mode; zero permission dialogs; no egress.
      The manifest declares no `INTERNET` permission.
    - FAILURE: any network attempt; any permission dialog; features break without
      connectivity that should be local-only.

---

## M. Plan, calendar selection, history and screen time (0.5.0)

40. **Plan page states**
    - Steps: Open the Plan page with calendar permission off; grant it; observe
      before choosing any calendar; choose one; reopen on another day.
    - Expected: distinct states in order — permission note with Allow; loading;
      "No calendars chosen yet" until one is picked; then that day's agenda.
      Previous/next move one day; all-day rows are labeled; an event happening
      now carries "In progress". Robolectric cannot verify provider content —
      this needs a device with real calendars.
    - FAILURE: an empty agenda rendered as if the device had nothing; calendars
      auto-enabled; events from unchosen calendars.

41. **History and backup round-trip**
    - Steps: Use Today (action, highlight, a task). Open History from Today.
      Complete the task, reopen History. Export a backup, clear history, restore
      the backup file.
    - Expected: today's record reflects the latest state; past days never change;
      clear asks for confirmation and touches only the past; restore replaces the
      live lists and upserts day records without deleting dates the file does not
      contain.
    - FAILURE: past records edited in place; clear removing current data; restore
      erasing local-only dates.

42. **Screen Time grant flow**
    - Steps: Settings → Screen Time, toggle on. Grant usage access in system
      Settings, return. Toggle "Hide summary", then toggle the feature off and
      reopen.
    - Expected: the system screen opens once from the toggle; the access state is
      re-read on return without a second prompt; "Hide summary" only hides the
      numbers; turning the feature off removes the section and stops queries.
    - FAILURE: repeat permission prompts; summary visible while hidden; usage
      rows alive after off.

---

## Result recording

For each item above, record: build SHA-256, device model, Android version, date, tester,
PASS/FAIL, and — on FAILURE — the exact steps, the observed behaviour, and a screenshot
or logcat excerpt. Until that is done, the item remains **unverified**, and
`docs/implementation-report.md` must not claim it passing.
