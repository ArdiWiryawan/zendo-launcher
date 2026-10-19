# YouTube Analysis — "my maximalist iPhone setup in 2026"

| Field | Value |
|---|---|
| Title | my maximalist iPhone setup in 2026 |
| Channel | Reysu |
| Duration | 21:22 (1282 s) |
| URL | https://youtu.be/7LPEVfMaajU |
| Uploaded | 2026-04-25 |
| Analysis date | 2026-10-19 |
| Source material | 20 evenly-sampled frames + 8 targeted scene/contact-sheet frames + 193 scene-change frames (t=0.35 detection, sampled) + auto-caption transcript (688 deduped snippets) |

> Evidence note: all timestamps are frame-capture / caption timestamps (MM:SS). ASR typos are normalized minimally ("joy of the thing" etc.). Anything not directly visible or audible is marked **[inference]**. Icon identification on the phone is limited by 1080p camera distance — icons named from narration are cross-checked against visible shapes; where ambiguous it is flagged.

---

## Setup Walkthrough

Channel: Reysu (Japan-based creator; speaks about books/Anki/language learning/self-hosting). Device: **iPhone 17 Pro** [00:00]. Video is explicitly framed as the **maximalist counterpoint** to his own minimal-setup content: "this definitely doesn't look that minimal, and I'm not a minimalist" [02:42].

Order of tour (as narrated and shown):

1. **Intro [00:00–00:27]** — promises lock screen → apps he uses → settings + shortcuts (Control Center).
2. **Lock screen [00:27–02:34]** — dynamic "Life Calendar" wallpaper, calendar widget, steps widget, Obsidian search button, camera button, left-side widget stack (Things 3 / Battery / Brick Time).
3. **Home screen page 1 [02:40–17:15]** — walks row by row through app categories: habit widget, AI/search, RSS, messaging, language learning, media, self-hosting folder, money, Japan folder, Claude, calculator, bottom row/dock.
4. **Home screen page 2 [17:15–18:26]** — "on this page, I just have two widgets" (Whoop fitness widget + a habit/Anki widget) with a large empty wallpaper area.
5. **Control Center + settings/shortcuts [18:26–21:14]** — color filters (blue-light blocking), Reduce White Point, Zoom filter → grayscale, "app settings" shortcut, QR code, screen recording, flashlight, Things 3 add-task, AssistiveTouch + Full Keyboard Access (mouse/keyboard rig), Background Noise, camera→Claude capture.
6. **Outro [21:09–21:14]**.

The video also uses its own on-screen annotation language: white rounded chips ("home screen app: apollo", "iphone settings: color filters: blue light blocking"), a dashed-box callout with a pixel-type label ("language learning row"), and a hand-drawn arrow annotation ("from whoop" on the lock screen) [01:04].

---

## Phone / Launcher UI Observed

Platform is iOS 26-era; "launcher" behaviour = Home Screen pages + Lock Screen + Control Center. Two home pages only (2 page dots visible, e.g. [03:12], [17:18]).

### Page 1 — app grid (observed [03:12], [06:24], [09:20], [18:10])

Grid: iOS **4 columns × 6 rows** + dock; labels **on** (white SF Pro, small); folder and app icons keep **stock full-colour app icons** — no global icon-pack / no monochrome tint mode visible, with two exceptions that read as custom monochrome glyph icons in the top rows (a white "A"-like mark and a white ECG/heart-rate glyph) [03:12], [06:24].

Text sketch (from visible frames; icon names cross-checked with narration):

```
status bar            17:54 ... wifi/battery
┌───────────────────────────────┐
│ [RIPPLES 2×2 WIDGET][icon][icon]│  row 1  ← widget top-left, 2 icons right
│ [folder-ish lime grid][swirl][?][red dict]│ row 2 (Perplexity nearby)
│ WhatsApp  Signal  Beeper  LINE │  row 3  ← messaging row [06:12]
│ Pleco     魚      [red JP]  Daijirin│ row 4 ← language learning row [06:55]
│ Safari    Books   My Classic  [folder]│ row 5 ← media row [08:29]
│ [folder] [folder]  Perplexity  Calculator│ row 6
│      • •  (2 page dots)
│ [Strong] [Things 3] [Google Maps] [Obsidian] │ dock [15:30–17:15]
└───────────────────────────────┘
```

Widgets on page 1:
- **Ripples habit-tracker widget, 2×2, top-left [03:13–03:59]** — lime/yellow-green rounded tile with a small dotted/matrix grid of habit checkmarks (worked-out days that week) plus a small figure icon. Two notable mechanics: (a) it is **interactive** — tap to tick a habit without opening the app; (b) it is **scrollable** — swiping it pages to a second habit set (vitamins). Data is filled automatically via a **Shortcuts automation** ("when I start a workout in Apple Health, it adds a checkbox here") [03:28–03:43].
- No other widget on page 1; the whole grid is apps + folders.

Folders (iOS folder = blurred wallpaper backdrop + white folder label):
- **"self hosting" [11:45]** — 3×3 grid: Jellyfin, Immich, Music, Still, Jump, Termius, Tailscale, Apollo + overflow. Icons retain stock colours on a heavily blurred/zoomed wallpaper background.
- **"日本" (Japan apps) [13:53]** — LUUP, SmartNews-style news tile, 7-Eleven-style app, PayPay ("P"), a maps/transport app, Suica-style card app, Uber, a smile icon [some identifications marked **[inference]** at this resolution].

Dock (same on both pages): **Strong (workout), Things 3, Google Maps, Obsidian** [15:30–17:15]. These are literally "the apps I probably use the most".

Status elements: clock left, location arrow, Wi-Fi, battery — untouched stock; page dots; no badge clutter visible.

Wallpaper (page 1): very dark, near-black with a warm brown/charcoal mottled cast; in warm-filter frames it reads ~#1A120B–#0C0A09. Blur-through in the folder view shows soft warm bokeh lights and a lime blob (= the Ripples widget bleeding through the blur) [11:45], [13:53] — i.e. the wallpaper is a dark, low-information surface that the widgets provide the only strong accent colours against **[inference on the exact source image]**.

### Page 2 — widgets-only page (observed [17:18])

Text sketch:

```
┌───────────────────────────────┐
│ [WHOOP WIDGET 4×2: recovery % ,  │  ← red/orange metric + calories
│  sleep score 91, calories 1,148] │
│ [HABIT/ANKI WIDGET: dotted grid + big 91 + small metrics]│
│                                 │
│        (large empty wallpaper)  │
│      • •  (2 page dots)
│ [Strong][Things 3][Maps][Obsidian]│ dock
└───────────────────────────────┘
```

Two stacked widgets, then deliberate emptiness: page 2 exists purely as a **widget dashboard** — no app icons at all. Narration [17:15–17:19]: "moving on to the next page, on this page I just have two widgets." Widget 1 = **Whoop** (recovery 18% in orange/red, sleep "91", calories 1,148) [17:19–17:44]; widget 2 = a dotted-grid habit widget tied to **AnkiLock** ("your phone is locked down until you finish your Anki reps"; free app) [17:57–18:24]. Wallpaper is the same near-black home wallpaper — the widgets are the only content.

### Lock screen (observed [01:04], [00:47], [01:26])

Text sketch:

```
Sun 22  ☁ No Upcoming Events        ← date pill / calendar widget
             17:44                  ← large warm-cream clock (gold-tinted)
              29                    ← tiny day-counter under the clock
 ▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒      ← Life Calendar dot-grid wallpaper
 ▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒         (cream dots ~#E8DFC8 on black ~#0A0A0A)
 [left column: Things 3 / Battery / Brick Time]
        [steps widget · runner glyph · 1.9k  (annotated "from whoop")]
        [yellow battery readout  1350 mAh · 63 %]
 (🔍 search = Obsidian)      (camera)   ← bottom corner buttons
```

- **Wallpaper = "The Life Calendar"** — free dynamic wallpaper web tool; you enter an end date and the shortcut re-renders it daily; a small counter under the clock shows days remaining ("29"). Motivational framing: "I just find this to be really motivating … so that I don't waste any days doing stuff I don't want to" [00:51–01:08].
- Widgets: **calendar/upcoming events** at top [01:24]; **steps** at the bottom [01:31]; **left-side stack = Things 3 (to-dos), Battery, Brick Time** [02:08–02:31]. Brick Time = a tap-to-block app list (used here to mute notifications, not primarily for blocking).
- Bottom corners: **search button → searches his Obsidian vault from the lock screen** [01:41–01:47]; **custom camera app** on the right [01:49]; standard camera still reachable by swipe [02:01].
- Colour: monochrome cream-on-black, warm gold cast (he films with the blue-light filter on, see below).

### Control Center & settings layer [18:26–21:14]

Not captured in readable frames; content confirmed by narration + partial frames [18:10 warm-tinted home screen], [19:14 pixel-font overlay], [20:18 Background Sounds panel]:
- colour filters / blue-light blocking; Reduce White Point (dimmer than minimum); Zoom filter → **grayscale**; a shortcut that opens **the current app's settings**; QR code; screen recording; flashlight; Things 3 "add task" without opening the app; AssistiveTouch + Full Keyboard Access (for the phone-on-stand + keyboard/mouse rig); Background Noise (rain/fire/white noise); camera → Claude ("take a picture and ask it something").
- The **screen-recording / My Classic overlay is styled like a retro iPod** (black circular pad with MENU + transport buttons, pixel typeface) — see [19:14].

---

## Maximalism Done Well

What keeps this dense setup attractive instead of noisy:

1. **One accent colour per surface.** Lock screen = cream/gold monochrome. Home = dark neutral + stock app-icon colours as the only saturation. Nothing else competes.
2. **Grid discipline is preserved.** 6 rows max, no crowding beyond standard density; folders absorb the long tail (self-hosting 9+ apps, Japan apps) rather than spilling icons.
3. **Rows carry meaning.** Messaging row, language-learning row, media row, money row, bottom row = daily essentials. He literally points at "the language learning row" with a callout. **Row = category** replaces folders for apps he uses daily.
4. **Position = priority.** Highest-value things at the top (habit widget), least-glanceable things mid-grid, most-used four in the dock.
5. **Widgets do the heavy lifting, icons do the rest.** The most information-dense element (Ripples matrix) sits exactly where the eye lands first; widget count per page is 1 on page 1, 2 on page 2 — far below the iOS maximum.
6. **A page can be a dashboard, not a place.** Page 2 has zero icons: widgets + empty space + dock. Purposeful emptiness reads as calm even in a maximalist setup.
7. **Blur as a separator.** Folder backgrounds blur/zoom the wallpaper (system behaviour), so opening a folder changes the visual layer without changing the palette.
8. **Interactive + scrollable widgets** reduce app-opens: tick habits, page through habit sets, add a to-do — all without leaving the home screen.
9. **Consistent annotation style** in the video itself (white chip captions, dashed callouts, pixel type, hand-drawn arrows) — a house style that makes dense information scannable.
10. **Anti-clutter is behavioural, not visual** — long feeds are cut at the source (RSS instead of YouTube app, browser + Untrack/Social Focus instead of social apps, AnkiLock gating), so the home screen is dense but the *content* is filtered.

Transferable rules: (a) density is fine when each cluster has one job; (b) never let widgets exceed one "visual block" per page region; (c) favourite/utility apps in the dock, category apps in rows, rare apps in folders; (d) keep saturation for app identity only, keep the background near-black; (e) reserve one page for pure glanceable status; (f) earn density with automation (auto-ticked habits, daily-rebuilt wallpaper).

---

## Concrete Ideas For Zendo Launcher

1. **[UI] Widget-forward first page** — allow the top-left slot to be a 2×2 status widget (habit/streak matrix) instead of icons. Effort: M. *Core default* (wellbeing = the habit matrix is the most meaningful data).
2. **[UI] "Dashboard page" concept** — let a page contain widgets only, with intentional empty space, and still inherit the dock. Effort: S. *Core default.*
3. **[behavior] Interactive widget actions** — tap-to-log inside a widget (habit tick, "took vitamins") without opening an app. Effort: M. *Core default.*
4. **[behavior] Scrollable/stacked widget sets** — same footprint cycles between 2–4 data views (steps, screen time, Anki reps). Effort: M. *Optional maximalist mode.*
5. **[UI] Row-as-category layout guidance** — an editor/onboarding hint that groups apps into labelled rows ("messaging", "focus tools") rather than folder-only grouping. Effort: S. *Core default (opt-in).*
6. **[UI] Blur-backed folder overlays** — reuse wallpaper-derived blur + brightness dim as the folder/search backdrop to keep palette stable. Effort: S. *Core default.*
7. **[philosophy] Density is allowed if each page has one purpose** — offer three preset densities (calm / standard / maximalist) instead of enforcing minimalism. Effort: M. *Optional maximalist mode.*
8. **[philosophy] Near-black wallpaper default with one accent hue**, and let widgets inherit the accent so system colour harmony is automatic. Effort: S. *Core default.*
9. **[UI] Countdown/"days left" surface** — a lock-screen or home widget for a personal goal date (birthday/goal), rendered in the same typographic weight as the clock. Effort: M. *Optional maximalist mode* (motivational, mirrors Life Calendar).
10. **[behavior] Daily-regenerated wallpaper/status** — a small worker that refreshes a generated wallpaper or a "days left" figure at midnight. Effort: M. *Optional.*
11. **[behavior] Lock-screen quick entry points to the user's own vault** — search-as-first-class action (Obsidian-style) instead of a generic search bar. Effort: M. *Core default* (wellbeing: search instead of feed-open).
12. **[UI] Retro/pixel accent typography option** for callouts and widget chrome — a personality layer that lets dense layouts look intentional. Effort: S. *Optional maximalist mode.*
13. **[behavior] "Current app settings" style shortcut** — from the running app, one gesture reveals that app's own limits/usage controls (the equivalent of his app-settings shortcut) — turns the launcher into the well-being control surface. Effort: M. *Core default.*
14. **[philosophy] Dock = daily drivers only (max 4)** — enforce/label the dock as "most-used" and let everything else live in rows/folders. Effort: S. *Core default.*

---

## Key Quotes

1. "This definitely doesn't look that minimal, and I'm not a minimalist, but compared to the previous iPhone setup I had, I had a really minimal setup that I really liked, but because of the latest update to iOS, that setup no longer works … So I've kind of reverted back to a regular-looking home screen layout." [02:40–02:57]
2. "On the top left, this is a habit tracker from this app called Ripples … I still like to have this visual reminder of how many times I worked out that week when I don't have my journal on the go." [03:13–03:26]
3. "One of the reasons I like this app is because you can set up a shortcut automation so that updates your habits automatically … So I don't actually have to click anything." [03:28–03:45]
4. "But this widget right here is actually scrollable, so I actually use this area to track my vitamins if I took them every day." [03:50–03:58]
5. "If I'm accessing social media, I like to do that through the Safari browser … you can actually install extensions on the browser to help you make the feeds less addictive." [08:45–08:57]
6. "You just want it to do the exact thing that it's supposed to do really well." (on Things 3 / Strong vs "crazy AI features") [16:17–16:25]
7. "Moving on to the next page — on this page, I just have two widgets." [17:15–17:18]
8. "Until you finish your Anki cards for the day, you can't open up your messages, you can't open up the news app, and you can't use social media. And I found this to be super effective in maintaining the habit of Anki effortlessly." [18:10–18:22]

---

## Limitations

- The phone is filmed on a green cutting mat from an arm's-length camera; at 1080p the **icon labels are mostly unreadable**, so app identification leans on narration + icon-shape matching. Icons flagged **[inference]** are uncertain.
- **Control Center was never captured in a readable frame** (scene detection around [18:31–19:04] returned talking-head shots); its contents are reproduced from narration, not from pixels.
- Page 2's widget details (exact metric labels on the Whoop card and the second dotted-grid widget) are partly legible at best; values like recovery 18% / sleep 91 / calories 1,148 are read from a lower-resolution tile.
- Only ~2 home pages are shown and the video does not show bringing up the widget gallery, icon-tint settings, or the App Library — so the **theming method** (stock icons, no wallpaper tinting) is an observation of the result, not of his configuration steps.
- Lock-screen left-side widget stack was not visible in a clear frame; it is narration-confirmed [02:08–02:31].
- Everything reflects a single iOS 26-era device and the creator's own habits; wallpaper hex values are eyeballed approximations from warm-filtered footage and are approximate.
