# Design reference audit

Visual and UX research for a monochrome, text-first Android launcher. Method: direct page
fetches of the App Store product pages, the Light Phone site, and the four GitHub READMEs,
all re-fetched for this revision. Nothing was cloned, downloaded, or embedded. Every claim
below is either retrieved text or explicitly marked as inference. No screenshot imagery was
retrievable as text from any source, so no source is described visually.

This file records design *principles* only. No proprietary assets, logos, screen designs,
colour values, spacing values, or component names are reproduced from any reference.

## Access, stated plainly

- **App Store product pages (SyncTasks, Blank Spaces Launcher, Dumb Phone):** accessible as
  marketing copy only, plus ratings, review text, and version history. No UI screenshots
  retrievable, so no layout claims are made.
- **thelightphone.com:** accessible as marketing copy only. No UI screenshots retrievable, so
  no layout claims are made. The LightOS interface itself is unverified.
- **GitHub READMEs (minimal-launcher, Olauncher, CCLauncher, KISS):** accessible in full as
  text. Every one of the four has a Screenshots section that renders as image references, not
  text, so no visual layout claim is possible. These are behavioural and structural
  references only.

## Sources inspected

| Source | URL | What could actually be retrieved | Accessible |
| --- | --- | --- | --- |
| SyncTasks | https://apps.apple.com/id/app/synctasks/id6575384261 | Full marketing description, feature bullets, 3.8 rating from 9 ratings, review text including one bug report and one crash report, version notes for v2.6 | Marketing copy only. No UI screenshots retrievable, so no layout claims are made. |
| Blank Spaces Launcher | https://apps.apple.com/id/app/blank-spaces-launcher/id1570856853 | Full marketing description, 4.9 rating from 107 ratings, 4 reviews including two cosmetic bug reports, a long version history | Marketing copy only. No UI screenshots retrievable, so no layout claims are made. |
| Dumb Phone (dp) | https://apps.apple.com/id/app/dumb-phone-dp/id6504743503 | Full marketing description, 4.9 rating from 35 ratings, in-app purchase list, privacy and compatibility data, "Adapt to Liquid Glass changes" note | Marketing copy only. No UI screenshots retrievable, so no layout claims are made. |
| Light Phone | https://www.thelightphone.com/ | Full marketing copy: testimonials, the "going light" positioning, LightOS description, the customizable tool menu, hardware SKUs | Marketing copy only. No UI screenshots retrievable, so no layout claims are made. |
| minimal-launcher | https://github.com/RafaelGoulartB/minimal-launcher | Full README: "a quiet, text-first Android home screen", Home plus swipe-away drawer with alphabet rail, folders, custom labels, hide and block, appearance settings, DataStore, no INTERNET permission, optional Usage Access | Text only. Screenshots section empty in fetched text. |
| Olauncher | https://github.com/tanujnotes/olauncher | Full README: "Minimal AF", hidden niche features, Pro variant with widgets, weather and folders, related minimal todo app | Text only. No screenshot text. |
| CCLauncher | https://github.com/mlm-games/CCLauncher | Full README: "App Hiding by Default", swipe-up search, long-press for settings, double-tap to lock, plain black and white wallpapers, swipe shortcuts, status bar visibility | Text only. No screenshot text. |
| KISS | https://github.com/Neamar/KISS | Full README: the "Browsing for apps is and should be secondary" thesis, type-first search, promotion of frequently used results, the four-column feature table | Text only. No screenshot text. |

## Licence position, and why this file is principles-only

`docs/opensource-review.md` concluded that none of the five candidate launchers is usable as a
code foundation: four are GPL-3.0 (Olauncher, CCLauncher, KISS, and focus_launcher, which the
open-source review covers and which is not a design reference here), and
RafaelGoulartB/minimal-launcher has no licence at all, which means all rights reserved. That
conclusion stands and is not revisited here. The practical consequence for this file is
narrower: the four open-source launchers may be read and reasoned about, but no code, string,
layout, or asset from any of them may be copied, and no visual claim about them can be made
from their READMEs, which do not render their screens.

## Observations per source

### 1. SyncTasks

- **Worth adapting (retrieved):** the feature the developer calls his favourite is a
  "Minimal Task List Widget" that is "inspired by dumb phones" and "blends seamlessly with your
  wallpaper", so that "your home screen will display only your tasks, with no other
  distractions". That is this project's thesis stated by a shipped product. Organisation is
  deliberately flat: folders, plus filter and search. No projects, no tags, no priority
  matrix.
- **Must not be copied (retrieved):** the "Exclusive Membership Card" with draggable,
  shakeable randomised gradient colours, which the developer himself calls "totally
  unnecessary but fun". It is a decorative reward mechanic attached to a premium purchase.
- **What the reviews show (retrieved):** the top review is from a graphic designer who calls
  the UI "satisfying" and "seamless" and then reports that reminder times will not leave AM,
  with no in-app way to report it. A later review reports the app failing to open after an
  upgrade. Both are ordinary shipping bugs, but they are a reminder that a small, calm
  surface still has to be correct.
- **Inference, marked as such:** "blends seamlessly with your wallpaper" implies low-contrast
  secondary text over a transparent background. That supports the muted secondary tone this
  project already uses, but it is an inference from a sentence, not an observation.

### 2. Blank Spaces Launcher

- **Worth adapting (retrieved):** "swaps a home screen full of icons for a simple list of the
  apps you actually need", and "No icons, no badges, no clutter" as a literal product promise.
  This is the strongest precedent for a pure text app list. Its setup claim is "60 seconds",
  which is the right ambition for a launcher's first run.
- **Must not be copied (retrieved):** subscriptions, streaks, scheduled and time-limited locks,
  unlock rituals by breathing exercise or push-ups, a weather widget, screen-time
  visualisation, a lock-screen mode, and a theme gallery. That is a second product, a
  habit-and-focus app, wearing a launcher's clothes.
- **What the reviews show (retrieved):** the negative reviews are cosmetic, a Nokia-font
  complaint and a Liquid Glass outline square on the wallpaper, and both got developer
  responses promising fixes. The recurring theme is that theming is where the complexity
  leaks.
- **Inference, marked as such:** that pattern supports keeping theming to two fixed palettes
  and refusing a theme gallery, which this project already does.

### 3. Dumb Phone (dp)

- **Worth adapting (retrieved):** multiple named launchers, described as launchers for
  leisure, work, and night, so the home screen shows only what matters for that context. Also
  an explicit Dark or Light minimalist style with customisable background and text. The
  description is direct about the goal: replace "colorful app icons, pesky notification
  badges, and anything that might give you a dopamine hit" with "simple buttons to open your
  essential apps".
- **Must not be copied (retrieved):** Detox Mode with Screen Time blocking, scheduled detox,
  notification hiding, and Control Center widgets. These are a large machinery for a launcher,
  and several depend on an OS capability the developer does not control, which is visible in
  the version note that the latest update exists to "adapt to Liquid Glass changes" that Apple
  introduced.
- **What the reviews show (retrieved):** the page carries 4.9 from 35 ratings and the reviews
  are positive, but the feature list is the honest signal: the app is a widget layer plus a
  settings surface plus a blocking engine.
- **Inference, marked as such:** on Android a launcher can genuinely replace the home screen,
  so none of the widget-plus-blocking theatre is needed. The value to take is the idea of
  named, contextual app sets, and only if the project ever wants them.

### 4. Light Phone

- **Worth adapting (retrieved):** the site sells absence. "No getting lost in a scroll hole",
  "tools that serve you, not the other way around", and a "customizable menu of tools managed
  from the dashboard website". The whole information architecture is a small, curated,
  user-managed tool menu, which maps almost exactly onto an Essentials list. The tone is
  testimonial-led and quiet, and the product's value is framed as an experience rather than a
  feature set.
- **Must not be copied (retrieved):** the marketing surface. It is long, testimonial-heavy,
  and built for a hardware brand with two SKUs. None of that belongs inside an app's settings
  or first-run flow.
- **Inference, marked as such:** LightOS's described structure implies an ordered plain list of
  tools with plain names and no categories. That is an inference from the copy, not an
  observation of the interface.

### 5. minimal-launcher (RafaelGoulartB)

- **Worth adapting (retrieved):** the README is the closest textual match to this project's
  brief. "A quiet, text-first Android home screen built with Kotlin and Jetpack Compose."
  Selected apps and folders on Home, the complete searchable app list "one swipe away", an
  alphabet rail for long lists, drag-to-reorder, local-first storage, no account, no ads, no
  analytics, no background service, and no INTERNET permission. Appearance is configurable
  (typeface, text size, clock format, which Home details display) without becoming a
  dashboard.
- **Must not be copied (retrieved):** the accent colour setting, and the optional battery and
  screen-time Home summary, which arrives behind a Usage Access grant. Both cut against a
  strictly monochrome, permission-light launcher.
- **Licence note:** no licence file and no licence field, so reuse of any kind is not
  permitted. See `docs/opensource-review.md`.
- **Inference, marked as such:** its Screenshots section is empty in the fetched text, so its
  visual density is unverifiable. Structural reference only.

### 6. Olauncher (tanujnotes)

- **Worth adapting (retrieved):** "a few niche features are available but hidden" is
  progressive disclosure stated as a deliberate policy, which is the right model for settings
  depth in a calm launcher. The star count behind a text-first minimal launcher is evidence
  that this is a wanted product rather than a niche curiosity.
- **Must not be copied (retrieved):** the Pro Launcher feature list (widgets, weather,
  folders). The existence of a separate paid product for those features is the author's own
  statement that they do not belong in the base launcher.
- **Inference, marked as such:** "hidden features" implies a settings screen deeper than what
  the home screen exposes, which is a reasonable target for this project's own settings tree.

### 7. CCLauncher (mlm-games)

- **Worth adapting (retrieved):** "App Hiding by Default: only open apps that you would want to
  open". The default state is near-empty and the user opts apps in, which is the purest form
  of a minimal default. Plain black and white wallpapers ship as a setting, and a status bar
  visibility toggle exists, both of which serve a chrome-free monochrome home screen. Its
  gesture set is small and stated in four lines: swipe up to search, long press empty space for
  settings, double tap to lock, plus swipe shortcuts.
- **Must not be copied (retrieved):** four-direction swipe shortcuts plus double-tap lock plus
  app renaming. That is a large invisible surface, and discoverability depends on the user
  remembering gestures.
- **Inference, marked as such:** it is a Compose rewrite of a text-first launcher, so its
  visual language is likely close to Olauncher's, but no screenshot was retrievable. This is a
  structural reference only.

### 8. KISS (Neamar)

- **Worth adapting (retrieved):** the thesis is one sentence, "Browsing for apps is and should
  be secondary", which is the correct hierarchy for a quiet home screen. The primary
  interaction is typing the first letters of an app, a contact, or a setting, and clicking, and
  "results clicked more often are promoted". Search-first beats grid-first.
- **Must not be copied (retrieved):** the "Customize everything" column in its own feature
  table. That ethos is the opposite of this project's stated direction, and it is a large
  settings tree by design.
- **Inference, marked as such:** KISS is a behavioural reference, not a visual one. Its README
  carries no visual claim, and its toolkit is dated relative to this project's Compose stack.

## Cross-source patterns worth keeping

- **A text list is the home screen.** Blank Spaces states it as a promise, minimal-launcher
  implements it, KISS assumes it. Three of four sources converge on it.
- **Search is the primary access path, not browsing.** KISS states it as a thesis; CCLauncher
  and minimal-launcher both bind search to a gesture.
- **Default to empty, opt apps in.** CCLauncher is explicit about it; Olauncher's base product
  is defined by what it refuses to include.
- **A small curated set beats a complete list.** Light Phone's tool menu and minimal-launcher's
  Home both work this way.
- **Restraint is the premium signal.** Light Phone sells absence; SyncTasks calls its widget
  "distraction-free".
- **Every source that added a reward or habit layer acquired a second product's worth of
  complexity.** SyncTasks' membership card, Blank Spaces' streaks and rituals, Dumb Phone's
  detox engine. All three are the parts of those products that this project should not want.

## What this means for Zendo Launcher

Each principle below is already applied in the current implementation, or is the target of the
redesign. Each names the reference it came from.

- **Text-first rows with one hairline as the default list unit.** From Blank Spaces Launcher
  ("No icons, no badges, no clutter") and minimal-launcher ("text-first Home screen"). Applied:
  `AppRow` renders a label and a separator, and the icon box is opt-in behind `LayoutMode`.
- **A single large focal element, the clock, and nothing else competing with it.** From
  Light Phone's framing of the phone as a simple device, and SyncTasks' "no other
  distractions". Applied: the clock is the only large text in the type scale
  (`ui/theme/Theme.kt:61-66`); the audit's P0-7 exists to keep it from breaking.
- **A small curated Home plus a searchable long tail one gesture away.** From
  minimal-launcher's Home and drawer split, and KISS's search-first thesis. Applied: the
  Essentials list on Home, the app list behind "All apps" (`ui/HomeScreen.kt:98`,
  `ui/LauncherViewModel.kt:165`).
- **Search first, browse second, and make typing the fastest path.** From KISS ("Browsing for
  apps is and should be secondary"). Partly applied: `core/AppQuery.kt` ranks exact, prefix,
  word-start, contains, then initials. Not yet applied at the surface: the audit's P0-3 lists
  what the search field is still missing.
- **Default to empty and let the user opt apps in.** From CCLauncher ("App Hiding by Default")
  and Olauncher's refusal to ship widgets and weather in the base product. Applied: a fresh
  install shows an empty Essentials list and a hidden-apps mechanism
  (`data/SettingsStore.kt:62-67`).
- **Two fixed palettes, no accent colour, no theme gallery.** From the cosmetic-bug pattern in
  Blank Spaces' reviews, and from minimal-launcher's accent colour being the one thing its
  README offers that cuts against this direction. Applied: `DarkPalette` and `LightPalette`
  carry two text tones and one separator (`ui/theme/Theme.kt:32-46`).
- **Depth in settings rather than on the home screen.** From Olauncher ("a few niche features
  are available but hidden") and Dumb Phone's separate launcher sets. Applied: a single
  Settings screen holds every preference; nothing on Home configures anything.
- **No habit, streak, reward, or blocking layer.** From SyncTasks' membership card, Blank
  Spaces' streaks and push-up unlocks, and Dumb Phone's detox engine, all of which are the
  complexity each product acquired after the calm core. Applied by omission, and worth keeping
  that way.
- **An honest feature set: do not ship a control that appears to do nothing.** From Dumb
  Phone's dependence on an OS capability it does not control. This is the one principle the
  current implementation does not fully meet: the Today toggle is wired correctly but has no
  visible effect while the only Zendo source returns null. See P2-10 in `docs/ui-ux-audit.md`.
- **Say what the launcher is before the user has to ask.** From Blank Spaces' "setup takes 60
  seconds" and Light Phone's framing of the change as the product. Not yet applied: there is
  no onboarding screen. See P1-2 in `docs/ui-ux-audit.md`.

## What this product must not become

- A dashboard: no summary tiles, screen-time cards, or battery chips on the home screen.
- A gamified product: no streaks, badges, membership cards, or randomised reward colours.
- A theme gallery: two fixed palettes, no colour picker, no accent colour.
- A decorative surface: no gradients, glass, drop shadows, or large rounded rectangles.
- An icon grid: no colourful app grids, no notification badges, no icon-driven home screen.
- A settings labyrinth: no "customize everything" ethos, a short opinionated settings tree.
- A ritual engine: no push-up or breathing unlocks, no scheduled detox machinery.
- An over-claiming product: no control that appears to work and does not.
- A noisy typographic system: no all-caps labels, no bold everywhere.

## Unverified

- No App Store screenshot imagery was retrievable as text for SyncTasks, Blank Spaces
  Launcher, or Dumb Phone. Any visual layout claim about them would be invented, so none is
  made.
- The GitHub Screenshots sections for minimal-launcher, Olauncher, CCLauncher, and KISS did
  not render as text. Their visual appearance is unverified. Behavioural and structural
  references only.
- thelightphone.com UI imagery was not retrievable as text. Only marketing copy was observed,
  and the actual LightOS interface is unverified.
- Rating counts, review text, and version notes reflect the pages as fetched on 2026-10-13 and
  will drift.
- No screenshots, logos, colour values, spacing values, or assets from any source were
  downloaded, copied, or embedded here. Every design decision recorded in this file is this
  project's own.
