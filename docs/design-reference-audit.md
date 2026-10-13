# Design reference audit

Visual/UX research for a monochrome, text-first Android launcher. Method: direct page fetches of App
Store product pages, the Light Phone site, and GitHub READMEs; video pages fetched for transcript
text. No screenshots downloaded or embedded. Content below is retrieved text, or is marked inference.

## Sources inspected

| Source | URL | What I could actually retrieve | Accessible |
|---|---|---|---|
| SyncTasks | https://apps.apple.com/id/app/synctasks/id6575384261?l=id | Full marketing description, feature bullets, version history, metadata, privacy section. Screenshot strip present but images not rendered as text. | partial |
| Blank Spaces Launcher | https://apps.apple.com/id/app/blank-spaces-launcher/id1570856853?l=id | Marketing description, feature bullets, 4 user reviews, long version-history list. No screenshot content. | partial |
| Dumb Phone (DP) | https://apps.apple.com/id/app/dumb-phone-dp/id6504743503?l=id | Localized (Indonesian) description, feature bullets, 4 reviews incl. one critical, "Full Screen Minimalism" update note. No screenshot content. | partial |
| Light Phone | https://www.thelightphone.com/ | Full marketing copy: testimonials, "going light" positioning, LightOS description, tool-menu concept. No UI screenshots as text. | partial |
| Minimal Launcher | https://github.com/RafaelGoulartB/minimal-launcher | Full README: explicit "quiet, text-first" Home screen, app drawer + alphabet rail, appearance settings, DataStore, no INTERNET permission. Screenshots section empty in fetched text. | partial |
| Olauncher | https://github.com/tanujnotes/olauncher | Full README: "Minimal AF" positioning, hidden niche features, Pro variant (widgets/weather/folders), related minimal todo app. No screenshot text. | partial |
| CCLauncher | https://github.com/mlm-games/CCLauncher | Full README: app-hiding-by-default, swipe-up search, plain black/white wallpaper setting, swipe shortcuts, status-bar visibility toggle. | partial |
| KISS | https://github.com/Neamar/KISS | Full README: type-first search, "Browsing for apps is and should be secondary", <250kb claim, four-axis feature table. | partial |
| Videos (5) | see Video references | Partial auto-transcript excerpts only; no on-screen layout retrievable. | partial |

No source was fully accessible in the sense of exposing its rendered UI. Every App Store screenshot
strip and every GitHub "Screenshots" section rendered as image references, not text, so no
pixel-level layout claim is possible. All entries are therefore marked partial.

## Observations per source

### 1. SyncTasks
- **Works well (retrieved):** its best feature is a "minimal task list widget… inspired by dumb
  phones… blends seamlessly with your wallpaper", so the home screen "will display only your tasks,
  with no other distractions". A direct statement of this project's principle. Folder grouping plus
  filter/search is the entire organisation model — no projects, tags, kanban, or priority matrices.
- **Too complex (retrieved):** a "Membership Card" with draggable, shakeable randomised gradient
  colours, called by the author "totally unnecessary but fun" — decorative reward mechanics.
- **Adapt:** one dominant content surface; wallpaper as the only chrome; folders as sole grouping.
  **Do NOT copy:** the membership card, randomised gradient rewards, any gamified unlock.
- **Inference:** "blends with wallpaper" implies low-contrast secondary text on a transparent
  background — supports our muted secondary colour.

### 2. Blank Spaces Launcher
- **Works well (retrieved):** "swaps a home screen full of icons for a simple list of the apps you
  actually need"; "No icons, no badges, no clutter" is a literal promise — the strongest precedent for
  a pure text app list. Friction is placed *before* a distracting app opens — attention design, not
  dashboard design.
- **Too complex (retrieved):** subscriptions, streaks, scheduled and time-limited locks,
  push-up/breathing unlock rituals, weather widget, screen-time visualisation, lock-screen mode,
  theme gallery. Reviews report cosmetic bugs (a Nokia-font complaint, a Liquid Glass outline).
- **Adapt:** the text-list home screen; the iconless/badge-less promise; "60-second setup" depth.
  **Do NOT copy:** streaks, badges, unlock rituals, weather widgets, theme galleries, or the
  habit-tracking layer — a second product wearing a launcher's clothes.
- **Inference:** recurring cosmetic bug reports suggest theming is where complexity leaked; keep
  theming to two fixed palettes.

### 3. Dumb Phone (DP)
- **Works well (retrieved):** multiple named launchers ("work", "evening", "relax") so the home screen
  shows only what matters for that context — a good model for a hideable TODAY section. Also an
  explicit light/dark minimal theme with customisable background, text, and elements; a reviewer calls
  it the most customisable of its competitors and says the UI "is also nicely designed" — depth did
  not cost visual quality.
- **Too complex (retrieved):** Control Center widgets, Detox Mode with Screen Time blocking, scheduled
  detox, notification hiding, and a platform limitation the developer must explain in review replies.
  One review ("it's just a widget… Useless") shows the promise/mechanism gap.
- **Adapt:** context presets as the mechanism for the optional TODAY section; an honest, small feature
  set that delivers what the home screen shows. **Do NOT copy:** detox/scheduling/blocking machinery,
  Control Center integration, platform-fighting features. Android allows a real launcher; no
  widget-plus-settings theatre is needed.
- **Inference:** the "Fake / just a widget" review warns against describing capabilities the platform
  cannot honestly provide.

### 4. Light Phone
- **Works well (retrieved):** the site sells *absence* — "no getting lost in a scroll hole", "tools
  that serve you, not the other way around", "a customizable menu of tools". Value is the experience.
  A small, curated, user-managed tool menu is the whole information architecture — it maps almost
  exactly onto our ESSENTIALS list. Copy is testimonial-led, quiet, never shouting.
- **Too complex / caution (retrieved):** the marketing surface is very long (testimonials, FAQ,
  movement narrative, two hardware SKUs). That length suits a hardware site and must not leak into the
  app's settings or first-run flow.
- **Adapt:** "tool menu" framing; restraint as the premium signal; a fixed, small tool set.
  **Do NOT copy:** the sales narrative, testimonial walls, or "movement" tone inside the app.
- **Inference:** LightOS's structure implies an ordered plain list with plain names, no categories.

### 5. Minimal Launcher (RafaelGoulartB)
- **Works well (retrieved):** the README is the closest textual match to our brief — "a quiet,
  text-first Android home screen"; selected apps + folders on Home; full app list "one swipe away";
  local-first with no INTERNET permission. An explicit interaction split: a small curated Home plus a
  searchable drawer with an alphabet rail for the long tail — exactly our ESSENTIALS model. Appearance
  is configurable (typeface, text size, accent colour, clock format, which Home details show) without
  becoming a dashboard.
- **Too complex / caution (retrieved):** an *accent colour* setting and an optional battery +
  screen-time Home summary — both cut against a strictly monochrome, minimal home screen if default.
- **Adapt:** the Home/drawer split, alphabet rail, "choose which Home details display", local storage.
  **Do NOT copy:** accent-colour theming, and the screen-time/battery summary as a default element.
- **Inference:** the empty Screenshots section means its visual density is unverifiable; structural
  reference only.

### 6. Olauncher
- **Works well (retrieved):** "Minimal AF" with 3.8k+ stars proves the text-first minimal launcher is
  a wanted product, not a niche curiosity. "A few niche features are available but hidden" —
  progressive disclosure as deliberate complexity management; daily wallpapers are the only expressive
  element.
- **Too complex / caution (retrieved):** the author's Pro Launcher adds widgets, weather, and folders
  — an explicit acknowledgement that the base product must not contain them.
- **Adapt:** hidden-but-present advanced settings; expressive content in the background, not in cards.
  **Do NOT copy:** the Pro-tier creep list into the free core.
- **Inference:** "hidden features" implies a long settings screen never surfaced on the home screen —
  a good target for our own settings depth.

### 7. CCLauncher
- **Works well (retrieved):** "App Hiding by Default" — the default state is near-empty and the user
  opts apps *in*: the purest expression of a minimal default. Plain black/white wallpapers ship as a
  setting, plus a status-bar visibility toggle — both serve a monochrome, chrome-free home screen.
  Gestures are small and consistent: swipe up = search, long-press empty space = settings, double-tap
  = lock, four-direction swipe shortcuts.
- **Too complex / caution (retrieved):** four-direction shortcuts plus double-tap lock plus app
  renaming is a lot of invisible surface for a simplicity-first product; discoverability depends on
  remembering gestures.
- **Adapt:** hide-by-default onboarding, plain black/white backgrounds, a tiny consistent gesture set.
  **Do NOT copy:** four-direction gesture shortcuts (two suffice) and gesture-only discoverability.
- **Inference:** being a Compose rewrite of Olauncher, its visual language is likely close to
  Olauncher's — but no screenshot was retrievable, so this is structural only.

### 8. KISS Launcher
- **Works well (retrieved):** the whole thesis is one sentence — "Browsing for apps is and should be
  secondary." The correct hierarchy for a quiet home screen. Typing the first letters of anything and
  clicking is the primary interaction; frequent results are promoted — search-first beats grid-first.
  The <250kb claim makes extreme economy a public feature.
- **Too complex / caution (retrieved):** "Customize everything" plus 69 open issues suggests a
  settings tree far larger than a calm launcher needs; the Java-era UI is unlikely to match a premium
  modern typographic feel.
- **Adapt:** search-first access to the long tail; promotion of frequent results; "less interface" as
  a stated goal. **Do NOT copy:** the "customize everything" ethos as a product value, or its dated
  visual language. **Inference:** KISS is a functional (behavioural) reference, not a visual one.

## Video references

Partial auto-generated transcript text retrieved for each — the opening portion only, never the
on-screen layout. I did **not** watch any video; claims are limited to the retrieved text.

- **7LPEVfMaajU — "my maximalist iPhone setup in 2026" (Reysu).** Despite the title, the retrieved
  portion is an iOS lock-screen tour: a "life calendar" wallpaper counting days to a goal, a calendar
  widget for today's events, a step-count widget, an Obsidian search shortcut, a camera app, and a
  Things 3 to-do widget. Takeaway: at-a-glance date + today's tasks on the first surface. Not an
  Android launcher; no home-screen layout described.
- **iCUB7JZmmig — "I Turned My iPhone Into an AI Productivity Machine" (Better Creating).** Sponsored.
  Argues for "getting rid of most" apps, an on-device AI attention guard, and "nice design choices to
  make this feel simpler". Almost nothing on typography, colour, or layout.
- **krityj3ITVE — "my anti-brainrot iphone setup" (unwiredo).** iPhone XR, 10–30 min/day screen time.
  Distraction is environmental, not a willpower problem; remove anything that does not serve the
  person you want to become; the phone becomes a communication and music device.
- **7jVb1lLniEw — "how I reduced my screentime by 80%" (Reysu).** The configured smartphone as "the
  modern dumb phone": minimal lock screen, notifications off except people, addictiveness removed from
  websites (YouTube thumbnails hidden). Under $10 on iOS and Android.
- **oUoxlF_s7wk — "This Video Will Save You 9+ Years…" (Maxwell Rhoe).** 4 h/day ≈ 11.5 years of life,
  then "7 changes"; only silent-mode-almost-always-on was captured.

**Limitations, bluntly:** partial transcripts, not visual inspections. I cannot confirm what any of
these home screens look like — arrangement, type sizes, colours, or spacing. They are evidence of
*intent and philosophy* (environment over willpower, minimal lock screen, notifications off, curated
tool sets), not visual references.

## Synthesized visual direction

My own design decisions, informed by the retrieved material and the stated brief. Not copied.

### Colour tokens

Two fixed palettes. No accent colour, no theme picker.

| Token | Dark | Light |
|---|---|---|
| `background` | `#000000` | `#FAF9F7` (warm off-white) |
| `surface` (rarely used) | `#0E0E0E` | `#FFFFFF` |
| `textPrimary` | `#F5F5F5` | `#111111` |
| `textSecondary` | `#8C8C8C` | `#6B6B6B` |
| `textTertiary` | `#5A5A5A` | `#9A9A9A` |
| `separator` | `#1F1F1F` | `#E6E4E1` |
| `dividerStrong` (section rules) | `#2A2A2A` | `#D8D5D1` |
| `pressedState` | `#141414` | `#F0EEEB` |

Separators are 1dp (0.5dp hairline at high density), inset to the text gutter. No shadows, elevation
tint, glass, or blur. Pure black in dark mode avoids OLED smearing and reads as calm.

### Type scale (sp)

One family throughout (neutral grotesque; system default acceptable). Weight carries hierarchy, not
colour or size alone.

| Element | Size | Weight | Notes |
|---|---|---|---|
| Clock | 56sp | Light/Regular (300–400) | Tabular figures, tight tracking; largest element on screen |
| Date | 15sp | Regular (400) | `textSecondary`; sits under the clock, not bold |
| Section heading (TODAY, ESSENTIALS) | 12sp | Medium (500) | `textTertiary`; letter-spacing +0.08em; **not** forced uppercase |
| Task title / app label | 17sp | Regular (400) | `textPrimary`; the primary list content |
| Task meta, time, status, hint | 13sp | Regular (400) | `textSecondary`, or `textTertiary` for hints/empty state |

Only two elements may ever be bold, never both at once: the clock (optional) and the current task.
No all-caps except by explicit user setting.

### Spacing rhythm

Base unit 4dp. Steps: 4 / 8 / 12 / 16 / 24 / 32 / 48. Screen gutter 24dp (never below 20dp). Clock
block top offset 48dp from the top content edge. Clock → date 8dp; date → first section heading 32dp;
section heading → first row 8dp; between sections 24dp; task title → meta 4dp.

### List rows and density

- App row height 56dp (comfortable single-line target, above the 48dp minimum). Task row 64dp, two
  lines: title + meta.
- No card background, no rounded container, no per-row border. Separation is one 1dp separator between
  rows, inset to the 24dp gutter, using the separator token.
- Section headings separate from the list by whitespace; a single `dividerStrong` rule may close a
  section. Whitespace is the primary separator, rules the secondary.

### Home-screen information hierarchy

1. Clock (56sp) — the anchor. 2. Date (15sp secondary). 3. `TODAY` section — optional and hideable;
when hidden, the screen jumps from date to ESSENTIALS. One primary task title, then at most one "Next"
line with a muted meta line (e.g. `Learning · 16:00`). 4. `ESSENTIALS` section — app list, plain text
labels, no icons by default. 5. Long tail: swipe up for a searchable app drawer with an alphabet rail
(Minimal Launcher / KISS model).

When TODAY is hidden, the clock/date block and ESSENTIALS must still leave a deliberate gap — do not
shrink the top block to fill the space.

### Interaction

Tap a row: launch. Long-press empty space: settings. Swipe up: drawer/search. Two gestures beyond
tapping. No double-tap lock, no four-direction swipes.

## What this product must not become

- A dashboard: no summary tiles, screen-time cards, or battery chips on the home screen.
- A gamified product: no streaks, badges, membership cards, or randomised reward colours.
- A theme gallery: two fixed palettes, no colour picker, no accent colour.
- A decorative surface: no gradients, glassmorphism, drop shadows, or large rounded rectangles.
- An icon grid: no colourful app grids, no notification badges, no icon-driven home screen.
- A settings labyrinth: no "customize everything" ethos; a short, opinionated settings tree.
- A ritual engine: no push-up/breathing unlocks, no scheduled detox machinery in the launcher.
- An over-claiming product: no promises the platform cannot deliver (see the DP "just a widget" review).
- A noisy typographic system: do not make every label uppercase or bold.

## Unverified / inaccessible

- No App Store screenshot imagery was retrievable as text for SyncTasks, Blank Spaces, or Dumb Phone.
  Any visual layout claim about them would be invented, so none is made.
- GitHub README "Screenshots" sections for Minimal Launcher, Olauncher, CCLauncher, and KISS did not
  render as text; their visual appearance is unverified. Behavioural/structural references only.
- thelightphone.com UI imagery was not retrievable as text; only marketing copy was observed. The
  actual LightOS interface is unverified.
- All five videos: partial auto-generated transcripts only. I did not view any video and cannot
  describe their on-screen layouts, colours, or typography.
- No screenshots, logos, or assets from any source were downloaded, copied, or embedded here.
- Rating counts and review text reflect pages as fetched on 2026-10-13 and will drift.
- Every colour, size, and spacing value above is my own design decision, not a measured value.
