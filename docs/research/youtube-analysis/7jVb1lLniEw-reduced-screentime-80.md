# YouTube Analysis — "how I reduced my screentime by 80% (guide)"

| Field | Value |
|---|---|
| Title | how I reduced my screentime by 80% (guide) |
| Channel | Reysu |
| Duration | 10:34 (634 s) |
| URL | https://youtu.be/7jVb1lLniEw |
| Uploaded | 2024-06-07 |
| Analysis date | 2026-10-19 |
| Source material | 18 evenly-sampled frames + 10 targeted scene-change frames (0.35 threshold) + auto-caption transcript (308 snippets) |

> Evidence note: all timestamps below are frame capture timestamps. Auto-caption ASR contains typos ("dump phone" = "dumb phone"); quotes are normalized minimally. Deductions that are not directly visible/audible are marked **[inference]**.

---

## Thesis & Method

Thesis: a "modern dumb phone" is not a flip phone — it is the smartphone you already own, re-configured so it works *for* you instead of *against* you, keeping QR codes / tickets / maps utility while removing compulsive-usage hooks. He quotes Atomic Habits: design the environment (the phone), not willpower. "It's really the dose that makes the poison" [03:58] — social media is not inherently bad; compulsive use is.

The video covers an iOS **and** Android setup for **under $10 total** (iOS ~$6.99 + $3.99; Android fully free). Five main steps [04:30]:

1. **Delete apps** [04:36–04:58]
   - Review every app; delete anything with an **algorithmic feed** or anything checked compulsively.
   - Social media apps (YouTube, Reddit, Instagram…) → delete; access them **only through the browser** (browser will be de-toxified in step 3).
2. **Turn off all notifications** [04:58–05:15]
   - Keep only messages from real people. No news, no push notifications from any app. Must be done manually: Settings → Notifications (iOS), per-app settings (Android).
3. **Optimize the browser** [05:15–06:12]
   - Install browser extensions on the phone: **iOS = Safari only**; **Android = Firefox** (then add the extension).
   - **Social Focus** — $3.99 on iOS, free on Android. Works on YouTube, Facebook, Instagram, LinkedIn, Reddit, X: removes algorithmic home feeds, suggested/recommended content. It is a Safari extension, configured via Settings → Safari → Extensions → "SocialFocus: Hide Distractions".
   - **Untrap for YouTube** — same developer; removes YouTube thumbnails and the recommended-video sidebar; his phone YouTube becomes "just a list".
4. **Customize the home screen** [06:12–07:27]
   - Use a **list-style launcher instead of an icon grid** — "a little less stimulating and there's no notification badges".
   - iOS: **Dumbify** ($6.99 one-time; replaces his old recommendation "Blank Spaces" which was subscription); apps render as a plain text list.
   - Android: **olauncher** (free) — his spoken recommendation; alt: **Unlauncher** (sideload). On-screen Play Store footage shows a search for "dlauncher" with results "minimalist phone" + "dlauncher, Minimal AF Launcher" **[inference: exact app he installs on camera is ambiguous between this footage and his spoken "olauncher"]**.
   - His resulting app set: books, music, messages, settings, browser, notes, maps.
   - Swap feed apps for non-feed alternatives: **Apple Maps instead of Google Maps**; **MacroFactor instead of MyFitnessPal**.
5. **Schedule social media & browser blocking** [07:27–09:25]
   - **ScreenZen** (free, iOS + Android) — block the browser and social media **on a schedule**, not a time limit: mornings entirely, evenings entirely, and during deep work. He keeps roughly **one hour/day** window (example: 12:00 PM–7:00 PM window; guilt-free access inside window).
   - Rationale: a **time limit** leaves the compulsive "let me check" habit (you still pull out the phone, get blocked, wait); a **schedule** removes the checking reflex — "you will not have to exercise your willpower or discipline anymore; your phone will just naturally become a utility device" [09:16].
   - iOS caveat [09:27]: blocking more than ~50 apps in ScreenZen "bugs out" due to the Screen Time API.
   - **Burnout Buddy** (iOS, free) — alternative to ScreenZen.
6. **Extras** [09:39–10:03]
   - **Full grayscale** mode on both iOS and Android — makes the phone less visually appealing.
   - Hardware: **iPhone Mini + matte screen protector** — "less like a smartphone", reduces tactile/visual pull.
   - Off-phone: he often leaves the phone home and runs with an **iPod Nano**.

Supporting argument run [00:57–04:22]: personal doomscroll history; apps are redesigned to addict (MyFitnessPal went from 3 tabs to a dopamine-widget feed + infinite scroll; even Google Maps now has an infinite place feed; Instagram/TikTok order comments by predicted engagement); the fix is app selection + environment design.

---

## Phone / Launcher UI Observed

### 1. Dumbify text-list home screen (the centerpiece) `[00:45]`
- iPhone (notch, dark green-ish case) lying on a dark felt desk at an angle.
- **Home screen = a plain left-aligned text list on near-black background**, no wallpaper art visible (flat dark, ≈ #0A0A0C–#141416 under soft warm room light).
- List reads, one lowercase word per line, generous line spacing (~1.6–1.8×), small bold-ish sans/serif-feel lowercase type, white ≈ #F2F2F2:
  `books / music / messages / settings / web / notes / maps`
- No icon grid, **no dock row, no badges, no search bar, no clock widget**.
- Single tiny **camera affordance** at bottom-center: small white rounded-square glyph with a circle (camera) — the only "graphical" element on screen.
- Status bar: `18:32`, Wi-Fi glyph, battery icon, **12%** (dark, no percentage stress indicators beyond icon).
- Typography feel [inference]: looks like Dumbify's list mode (monospace-adjacent, calm, document-like), not system SF grid.
- Emotional effect: reads like a table of contents, not an app launcher.

### 2. YouTube watch-screen ("before" example) `[00:35]`
- iPhone edge-on, dark-mode YouTube app, 16:9-ish content area:
  - Top-right controls: pause pill toggle, `CC`, gear icon (white/light gray on black).
  - Center: buffering ring (thin white circle).
  - `0:00 / 30:28` white timecode, right-aligned PiP icon.
  - **Orange progress bar** (≈ #FF7A00) with orange dot scrubber — YouTube's accent color.
  - Title: "10x Your Profit. 10x Your Pricing. 10x Your Business. [Here's how]" — bold white, wraps to 2 lines.
  - Meta line: `19K views • 4 hours ago ...more` gray ≈ #AAAAAA.
  - Channel row: avatar circle "Alex Hormozi" white bold + `2.42M` gray; right: **"Subscribed" pill** (light gray fill #E8E8E8, dark text — inverted vs dark theme).
- This is the doomscroll target the whole video fights; note every engagement hook (progress bar, subscriber count, views) visible in one frame.

### 3. Old MyFitnessPal UI (6 years ago), on an old black Android `[03:19]`
- Light theme: off-white background (#F4F4F4), sparse content — huge empty area.
- Bottom tab bar (3 tabs only): **Home** (filled blue house + blue label ≈ #1E9CD7), **Diary** (outline book icon, gray), **Profile** (outline person, gray).
- Large **blue circular "+" FAB** floating bottom-right of content area.
- Android capacitive back key visible below the screen.
- Impression: functional, calm, tab-scoped — the "good old" baseline he contrasts against.

### 4. Modern MyFitnessPal UI ("today") `[03:24]`
- Same old device, updated app: **dark navy header band** (≈ #1B4F7A / deep blue) with circular avatar top-left, white wordmark.
- Below: **big blue arc "calorie ring" gauge** (≈ #2196F3 arc on white card) — described in audio as "dopamine widgets".
- Multiple stat rows stacked under it, bottom nav partially visible with a blue-accented active tab.
- Denser, dashboard-like, feed-oriented — the visual argument "apps became less functional, more engaging".

### 5. Tools & pricing overlay card `[04:07]`
- Video graphic: **two white cards on blurred background**.
- Left: **iOS (no subscriptions)** — homescreen: `dumbify [$6.99]`; extension(s): `socialfocus or untrap for YouTube [$3.99]`; app blocker: `screenzen or burnout buddy [free]`.
- Right: **Android (all free)** — homescreen: `olauncher [free]`; alt: `unlauncher [side load]`; extension(s): `socialfocus or untrap for YouTube [free]`; app blocker: `screenzen [free]`.
- Style: bold black headers, nested bullet indentation, prices in a violet/purple inline "code/link" style (≈ #6C5CE7); generous white space, minimal, high trust/legibility (black on white, no borders).

### 6. iOS App Store search — "socialfocus" `[05:17]`
- Dark-mode App Store search field: gray rounded search bar (≈ #1C1C1E) with magnifier, typed `socialfocus|`, clear (ⓧ) button, blue "Cancel".
- Suggestions list: `socialfocus: hide distractions`, `socialfocus`, `social focus`, `social focus app`; thin separators, each with gray magnifier icon.
- Status bar: `17:14`, Wi-Fi, **50%** battery. A thumb/finger enters from the right — shot held over a desk mat.

### 7. iOS Settings → Notifications → Mail `[05:11]`
- Standard iOS dark Settings UI: back chevron "‹ Mail" blue, centered bold white title **Notifications**.
- **"Allow Notifications" grouped row with toggle ON** (white knob, mint-green fill — this is the visual of "what he turns OFF manually").
- Section header `ALERTS`, below: three preview thumbnails (Lock Screen / Notification Center / Banner mini-phones, outlined in teal).
- Status bar additionally shows a **moon (Focus/Do Not Disturb) icon** next to the time `17:12` — a subtle wellbeing-adjacent status element.

### 8. SocialFocus Safari-extension panel `[05:44]`
- Sheet title row: **"SocialFocus: Hide Distractions"** (bold white) + blue-teal "Done" button; below, a header with hamburger menu icon, centered "SocialFocus", and a **green power toggle (ON)**.
- Site list on near-black background (≈ #111113), white bold rows, thin spacing: `YouTube` (expanded), `Facebook`, `Instagram`, `LinkedIn`, `Reddit`, `X`.
- Expanded "YouTube" row rendered as a **cyan/teal filled header** (≈ #35A7BF) with up-caret — the accordion pattern for per-site settings.
- Bottom edge shows a settings row "Hide Suggestions on Home Page" with an off-state white/gray toggle — cut off by the finger; the list of toggles continues below frame.
- This is the only "settings-heavy extension" screen in the video: dark, list-based, one accent color, toggles right-aligned.

### 9. Optimized YouTube on phone (Untrap) `[05:52]`
- Dark-mode YouTube **without thumbnails**: each row = circular channel avatar (left, ≈ 36dp), bold white 2-line title, gray meta "`Channel • 23K views • 1 day ago`", a right-side **orange-tinted ⋮** three-dot menu; thin separators between rows.
- Visible titles (context: general feed noise he no longer sees as tempting): "How to Recover from big mistakes in Business" (Alex Hormozi), "Exposing My Failures" (George Dunnett), "Is This Bitcoin Range Ending Soon?" (TechnicalRoundup), "What I learned from going mega-viral on Instagram" (Sean Gulashin), "He Spent 100 Days Underwater…" (Slim Land), "Dr. Mary Claire Haver…".
- No thumbnails = rows read as **text list**, same de-stimulation principle as the home screen. He calls it "just a list where I'm unlikely to fall into a binging rabbit hole" [audio 06:05].

### 10. Android — Play Store, searching launcher `[06:46]`
- Pixel-style Android phone on felt mat; light-mode Play Store:
  - Search bar shows typed `dlauncher`; result cards: "**minimalist phone: Sc…** — OS13 Lolo • Productivity • 4.3★ • 15m downloads • Everyone" with **Install** pill, and a row "**dlauncher, Minimal AF La…** — 9+ installed".
  - "Available on more devices — ONYX Palma Phone" section with device card.
  - Screenshots strip: minimal launcher previews (dark cards, sparse layouts — e-ink phone-adjacent aesthetic).
  - Caption on the app card readable in frame: "Launcher to help you reduce screen time."
- Compliance angle: all Android picks are Play-Store apps or a sideload (Unlauncher), reinforcing "free on Android".

### 11. ScreenZen on both platforms, side by side `[08:04]`
- Two phones held in one shot, both showing the ScreenZen store pages simultaneously:
  - **Left (Android/Play Store)**: "ScreenZen - App Blocker" by screenzen, "In-app purchases", buttons **Uninstall** (outline) + **Open** (filled), "Available on more devices — ONYX Palma Phone", "What's new: Last updated May 10, 2024 • Default language en-US • Bug fixes • Updated UI", "Rate this app" 5-star row, "Write a review", "App support". Time `4:58`.
  - **Right (iOS/App Store)**: blue "‹ Search" back, "**ScreenZen - Screen Time Control**" by ScreenZen LLC; stat row: `4.6K RATINGS • 4.4★ • AGE 4+ • CATEGORY Productivity • DEV ScreenZen LLC`; "What's New — Version 1.50, 2w ago: Fixed bug with 'Continue' button; Fixed bug with 'Custom Unlock Time'"; **Preview images**: dark panel with logo + a light panel reading "The most customizable app blocker". Time `16:58`.
- Key read: the blocker itself is **deliberately boring** — utilitarian store page, no gamification shown.

### 12. App Store search — typing "bur…" `[08:11]`
- Same dark App Store search UI, text `bur|` — mid-typing "burnout buddy". Status `17:14`, 50% battery. [inference: confirms the Burnout Buddy iOS recommendation visually].

### 13. Grayscale demo — "this is grayscale" `[09:24]`
- iPhone Mini flat on desk, **caption overlay** "this is grayscale" (white text, left side).
- Warm tungsten cast over the whole shot [note: room lighting, not UI color].
- Home screen in **grayscale**: mixed **icon grid + widgets** layout:
  - Top-right: **music/podcast widget** (rounded rect, gray) with track text ("A-Ha…?" unreadable) and ◀◀ ▶ ▶▶ playback controls.
  - Two rounded-rect widget slots right of top rows (one appears blank/white outline, one colored block rendered gray).
  - A **"Focus" pill button** (moon icon + word) inline with icons — focus-mode shortcut on the grid.
  - Icons: small rounded squares, white glyphs on dark-gray fields, some colored icons desaturated to mid-gray (distinguishable shapes, zero chroma).
  - Roughly 5 columns × 5–6 rows in the visible area with a bottom-center small icon near the dock zone; denser than the Dumbify list — this is "extras" mode, not his daily driver.
- Status bar: `T-Mobile`, `6:31`?, battery `77%` with battery-style icon; all elements gray.

### 14. Couch B-roll `[08:48]`
- Reenactment shot: he lies on a gray sofa, phone in right hand, wired earphones, laptop foreground — the "compulsive lying-down scroll" image the schedule step is meant to eliminate. (No UI visible.)

### 15. Transcript-only elements (no clear frame) — flagged
- "Super minimalistic **lock screen**" [00:35 audio] — never captured in sampled frames. [limitation]
- His live ScreenZen **schedule configuration screens** (time windows, blocklists) — audio describes them [08:05–09:00]; no clean frame captured (scene frames at 08:00–08:20 show store pages instead).
- The exact **olauncher/Unlauncher app-list screen in use on Android** — shown only as store screenshots strip; his spoken list (books/music/messages/settings/web/notes/maps) is visible only on the iOS Dumbify shot.

---

## Patterns & Principles

1. **Environment over willpower** — the launcher/ap is the environment. One-time setup ("half an hour to an hour" [02:34]) buys permanent behavior change. Launcher-relevant: ship opinionated *presets* ("modern dumb phone mode") — users configure once, not daily.
2. **Friction by schedule, not by interruption** — time limits and "pause dialogs" preserve the checking habit (you still reach for the phone); hard windows eliminate the reflex [08:36–09:00]. Launcher-relevant: scheduled access windows > usage timers > nudges, in that order of effectiveness.
3. **De-stimulation is subtractive** — no thumbnails, no badges, no feeds, no icons, no color. Each removal targets a specific hook: thumbnails → curiosity gap; badges → compulsive check; color/icons → visual appetite; feeds → infinite scroll.
4. **Utility preserved, temptation removed (hide vs delete)** — delete the *app*, keep the *function* through the browser; delete Google Maps' feed app, keep maps. Launcher-relevant: "uninstall but keep a web/utility route" is a first-class action; blocking should never break QR codes, tickets, navigation.
5. **List > grid** — text rows reduce scanning speed and visual appetite while improving intentionality ("less stimulating, no badges" [06:16]).
6. **Honest affordances** — every element on his screens communicates state plainly (plain text names, plain toggles, plain store pages). No streaks, no red dots, no celebration animation in his stack.
7. **Make the object less appealing** (grayscale, matte protector, iPhone Mini) — reduce the phone as an attractive object, not just its apps.
8. **One purpose** — "the phone has only one purpose and it's to work for me and not against me" [02:02]; if usage creeps up, add more restrictions [02:08] — an iterative tautening loop, i.e. the launcher is a *living constraint system*, not a static theme.

---

## Concrete Ideas For Zendo Launcher

| # | Idea | Tag | Effort |
|---|---|---|---|
| 1 | **Text-list home mode**: apps as a left-aligned lowercase text column on flat dark wallpaper, no icons/badges/dock/search bar; optionally one tiny accent affordance (camera-like) | [UI] | S |
| 2 | **Grayscale-first rendering toggle**: desaturate launcher + optionally expose deep-link to system grayscale settings; make monochrome the default onboarding look | [UI] | S |
| 3 | **Badge-free by decree**: never render notification badges/dots anywhere in the launcher; unread state expressed only inside apps | [UI] | S |
| 4 | **Access windows ("social hours")**: per-app / per-category schedule where apps are launchable only inside chosen time windows — not timers | [behavior] | L |
| 5 | **No false hope dialogs**: when outside a window, block with one static screen showing the next window time; no "one more minute" escape hatch (matches "schedule kills the checking habit") | [behavior] | M |
| 6 | **"Uninstall, keep the utility" flow**: one-tap uninstall + auto-create a browser/web shortcut in a separate "web" entry (his web/notes/maps pattern) | [behavior] | M |
| 7 | **Minimal status strip**: time + battery only, no percentage-first displays, no red indicators, on lock/home | [UI] | S |
| 8 | **5-step "Modern Dumb Phone" setup wizard**: delete apps → notifications audit (deep-link per-app settings) → browser de-feed checklist → launcher style → schedule; mirrors his guide order | [behavior] | M |
| 9 | **Feed-app detector & swap map**: flag installed apps with algorithmic feeds at onboarding; suggest non-feed alternatives (Apple Maps/MacroFactor pattern) — curated mapping list | [philosophy] | M |
| 10 | **Search-first drawer**: app access primarily via search/typing rather than icon browsing; icons de-emphasized or textual | [UI] | S |
| 11 | **"Dose" dashboard**: weekly usage framed as dose vs baseline (his "dose makes the poison" frame), no streak shaming, neutral tone | [philosophy] | M |
| 12 | **Focus bundle automation**: one toggle that combines grayscale + window rules + allowed-apps whitelist (his "deep work" blocking) | [behavior] | M |
| 13 | **Constraint ratchet**: when weekly screen time exceeds target, the launcher gently tightens (e.g., suggests one more app deletion or one narrower window) — "if it's getting more addicting, add more restrictions" | [philosophy] | M |
| 14 | **Deliberately boring surfaces**: launcher settings/about/store-facing screens styled as plain black-on-white or white-on-black text, no gradients/illustrations — the trust signal his overlay card and ScreenZen pages use | [UI] | S |

---

## Key Quotes

1. [00:00] "This is the modern dumb phone. It's a system to help you take back your attention and kill your screen time — as someone who's gone down the dumb phone rabbit hole and looked into flip phones, really the best dumb phone is the phone that you already have. You just need to customize it to make it work for you and not against you."
2. [01:43] "It's really important to set up your environment to make it work for you and not against you."
3. [02:02] "The core concepts stay the same, where the phone has only one purpose — and it's to work for me and not against me. And if I find that it's getting more addicting, then it's time to put on more restrictions on my phone."
4. [03:58] "It's really the dose that makes the poison. It's only when you start to use social media compulsively and you start to watch or consume content that isn't really adding anything to your life that it becomes harmful."
5. [06:16] "Personally I like using a launcher that makes the apps into a list versus icons, because it's a little less stimulating and there's no notification badges."
6. [08:36] "I would recommend setting up a schedule versus doing a time limit — because when you have a time limit, or you have one of those apps that make you pause for a few seconds, you still have the habit of checking compulsively before knowing that it's blocked."
7. [09:16] "Once you set up these restrictions you will not have to exercise your willpower or discipline anymore — your phone will just naturally become a utility device."
8. [05:16] "A lot of times when I'm going out I don't even need to really bring it, and now when I go running or when I go work out I don't even bring my phone — I just bring my iPod Nano."

---

## Limitations

1. **Subtitles**: official yt-dlp subtitle download was rate-limited (HTTP 429, repeated). Transcript was obtained via youtube-transcript-api (auto-captions) — wording is ASR-quality; timestamps are per-caption-line, ±2–3 s. Treat non-quoted wording as paraphrase.
2. **Lock screen demo** [00:35 audio] was never captured in any sampled frame — its exact layout is unknown.
3. **ScreenZen schedule screens** (the actual time-window configuration UI) were described in audio but not captured; implementation details of its blocker screen (what the user sees when blocked) are unknown.
4. **olauncher vs dlauncher ambiguity**: audio says "olauncher", on-screen Play Store footage shows a search for "dlauncher"; the exact app installed on camera cannot be confirmed from frames.
5. **Colors**: frames are 1024 px-wide video captures with cinematic color grading; all hex values are approximations of screen regions, not pixel-exact UI colors.
6. **SocialFocus panel** bottom half (list of per-site toggles) is occluded by the presenter's hand; only the top accordion + one toggle label are legible.
7. **Grayscale home screen** details (widget identities, exact icon set) are partially unreadable at capture resolution and under tungsten warm cast.
8. Sampling density: 28 frames over 634 s (~1 per 23 s); short-lived screens transitioned between samples, so additional unseen screens may exist.
