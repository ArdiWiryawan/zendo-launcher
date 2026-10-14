# Design system

The launcher's visual system exists in code only. This document writes down what is
there: the tokens in `ui/theme/Theme.kt`, the primitives in `ui/Components.kt`, and the
rules the four screens follow when they compose them.

It describes the shipped system. It does not restate the audit that produced it
(`docs/ui-ux-audit.md`) or the change log that applied it (`docs/ui-ux-implementation.md`);
finding ids are named only where a rule exists because of one.

## 1. Monochrome identity

The palette is `LauncherPalette` in `ui/theme/Theme.kt`: a background, two text tones and
one separator tone, plus an `isDark` flag. There is no fourth colour.

| Role | Light (`LightPalette`) | Dark (`DarkPalette`) | Used for |
| --- | --- | --- | --- |
| `background` | `0xFFF7F7F5` | `0xFF0B0B0C` | The page, and nothing else |
| `primary` | `0xFF111112` | `0xFFF2F2F0` | Titles, row labels, selected values, hairlines that mean "selected" |
| `secondary` | `0xFF6E6E6C` | `0xFF8A8A88` | Section headings, dates, empty notes, unselected values, the favourite dot |
| `separator` | `0xFFE2E2DE` | `0xFF1F1F21` | Every hairline, and the one elevated container Material3 needs |

`LauncherColors.current` (`ui/theme/Theme.kt`) is the accessor components read; it returns
`LocalPalette.current`. `LauncherTheme(mode)` picks `LightPalette`, `DarkPalette`, or, for
`ThemeMode.SYSTEM`, whichever `isSystemInDarkTheme()` reports.

There is no accent colour, and none can appear by accident. `ColorScheme.inLauncherPalette`
in `ui/theme/Theme.kt` maps the Material3 roles the product reads onto these tones:
`background`, `surface`, `onBackground`, `onSurface`, `onSurfaceVariant`, `primary`,
`onPrimary`, `outline`, `outlineVariant`, `scrim`, `surfaceTint`, the five `surfaceContainer*`
roles, `surfaceVariant`, `secondaryContainer`, `onSecondaryContainer`, `inverseSurface` and
`inverseOnSurface`, plus `error` and `onError`, which map to `primary` and `background`. The
rest of the scheme is left unmapped and keeps Material3's own defaults: `primaryContainer`,
`onPrimaryContainer`, `inversePrimary`, `secondary`, `onSecondary`, `tertiary`, `onTertiary`,
`tertiaryContainer`, `onTertiaryContainer`, `errorContainer`, `onErrorContainer`, `surfaceDim`
and `surfaceBright`. No launcher UI reads one of those, because the only Material3 components
the product uses are `Text` and the long-press `DropdownMenu` — and the menu's container is
`surfaceContainer`, its item colour is `onSurfaceVariant` and its elevation tint is
`surfaceTint`, all mapped.

Colour never carries meaning alone. Every state that changes a colour also changes a word,
a weight, a marker, or a semantic property:

- A pinned row's dot is decorative; `AppRow` also sets `stateDescription` to
  "In Essentials" or "Not in Essentials".
- `ToggleRow` changes the word, the weight and the tone together.
- `ChoiceRow` marks the selected option with a hairline and a weight change as well as
  with tone (P0-6).

## 2. Typography

Every style is a `TextStyle` in `LauncherType` in `ui/theme/Theme.kt`. Only `clock` names a
`fontFamily` (`FontFamily.Default`); the rest leave it unset and inherit the same default
through `MaterialTheme(typography = Typography())`, so every style renders in the platform
face. Nothing is uppercase, and only `clock`, `section` and `action` carry letter-spacing, so
hierarchy comes from size and tone rather than from shouting.

| Style | Size | Weight | Line height | Letter spacing | Purpose |
| --- | --- | --- | --- | --- | --- |
| `clock` | 60.sp | `FontWeight.Light` | font default | `-1.5.sp` | The time on Home. The only large element in the product. |
| `date` | 15.sp | `Normal` | font default | none | The date line under the clock. |
| `title` | 20.sp | `Normal` | font default | none | Screen titles, via `ScreenTitle`. Without it a title renders at row size and reads as a row. |
| `section` | 12.sp | `Medium` | font default | `1.2.sp` | Section headings, via `SectionHeading`. |
| `appLabel` | 17.sp | `Normal` | `24.sp` | none | App row labels, and the drawer's search field text. |
| `body` | 15.sp | `Normal` | `22.sp` | none | Settings rows, onboarding lines, menu items. |
| `secondary` | 13.sp | `Normal` | font default | none | `EmptyNote`, `SecondaryText`, and the clock's AM/PM suffix. |
| `action` | 14.sp | `Medium` | font default | `0.2.sp` | `QuietAction` labels and the value word in `ToggleRow` and `ChoiceRow`. |

`appLabel` and `body` carry explicit line heights because text-heavy rows otherwise
inherit the font default (P2-1). The other styles inherit it deliberately.

### `clockSizeSp` fitting

`clockSizeSp(screenWidthDp: Int, fontScale: Float, base: Float = 60f): Float` in
`ui/theme/Theme.kt` is a pure function returning the size the clock should actually
render at. `HomeScreen.Clock` applies it as
`LauncherType.clock.copy(fontSize = clockSizeSp(...).sp)`.

- **Ceiling** is `base`, 60f by default.
- **Floor** is 28f sp, applied by `coerceIn(28f, base)`.
- It returns `base` unchanged when `fontScale <= 0f` or when `screenWidthDp <= 0`. The
  second guard matters because a caller that measures the window before layout can pass 0,
  which would otherwise compute a negative available width and floor the clock at 28sp on
  a screen with plenty of room.

It exists because a fixed 60.sp clock renders wider than the text column at a raised
system font scale and clips (P0-7). The available width is
`screenWidthDp - 2 * LauncherSpace.gutter.value`; the widest realistic string is "12:59"
plus the AM/PM run, whose advances are estimated pessimistically (digits at 0.60em each,
colon at 0.30em, each suffix character at 0.55em of `LauncherType.secondary.fontSize`,
minus `LauncherSpace.sm` of gap). Over-estimating only costs a little size at very large
font scales, which is the safe direction to be wrong in. The result is arithmetic from
default font metrics, not a device measurement; the documented upgrade path is per-glyph
autosize once `TextAutoSize` ships in the pinned Compose version.

## 3. Spacing

`LauncherSpace` in `ui/theme/Theme.kt` is the whole vocabulary. It is an `object` of `Dp`
values; no screen defines a spacing constant of its own.

| Token | Value | What it is for |
| --- | --- | --- |
| `xs` | 4.dp | A label and the thing that belongs to it. |
| `sm` | 8.dp | A heading and its first row; padding inside a row. |
| `md` | 16.dp | One control and the next; a hairline and the content after it. |
| `lg` | 24.dp | Top-of-screen offset; sibling actions in one row. |
| `xl` | 40.dp | A full section break. |
| `gutter` | 24.dp | The screen's horizontal margin. |
| `hairline` | 1.dp | The only border weight in the product. |
| `touchTarget` | 48.dp | The Android minimum interactive size. |

Usage rules the screens actually follow:

- **Gutter.** `padding(horizontal = LauncherSpace.gutter)` is applied to the scrolling column
  of `HomeScreen`, `SettingsScreen` and `OnboardingScreen`, and to the outer `Column` of
  `AppListScreen`, whose scrolling list is a `LazyColumn` rather than a padded column. The
  fixed bottom action row therefore carries the gutter on all three bars that have one
  (`HomeScreen`, `AppListScreen`, `OnboardingScreen`). Elsewhere the gutter is not on the
  outer `Column`, so a child that sits outside the scrolling column has to carry its own:
  `HomeScreen`'s `UndoNote` is padded explicitly for that reason.
- **`xs` vs `sm` vs `md`.** `xs` joins an item to its own companion — the clock to the
  date, a `ChoiceRow` label to its options, an option's text to its marker, a toggle to
  the sentence explaining it, and `EmptyNote`'s vertical padding. `sm` separates a
  heading from the content it introduces and pads inside a row (`AppRow` uses
  `padding(vertical = LauncherSpace.sm)`; `LetterHeader` too). `md` is the step between
  peer controls in one section (`SettingsScreen` between two `ChoiceRow`s or a `ChoiceRow`
  and a `ToggleRow`), the space after a `Hairline` before the next heading
  (`AppListScreen`, `SettingsScreen`), and the gap between a row's trailing actions
  (`PinnedOrderRow`, `AppRow`).
- **`lg`** is the top offset of a screen (`AppListScreen`, `SettingsScreen`) and the gap
  between sibling actions packed into one row: `Arrangement.spacedBy(LauncherSpace.lg)` in
  the bottom bars of `HomeScreen` and `AppListScreen`, and between `ChoiceRow` options.
  `OnboardingScreen`'s bottom bar is the third and does not use it: it gives its first child
  `weight(1f)`, so the closing action sits on the trailing edge instead.
- **`xl`** is the section break: one full blank step between a section and the next, and
  between the last section and the bottom of the scroll. Every `SettingsScreen` heading
  after the first is preceded by it, and `HomeScreen` uses it before "Essentials", before
  "Today" and at the end of the list. It is also the deliberate distance between the
  reorder pair and the destructive Unpin in `PinnedOrderRow`.
- **Home's top gap** is the one derived value:
  `(heightDp * 0.06f).coerceIn(LauncherSpace.lg, LauncherSpace.xl)` in `HomeScreen`, with
  `lg` as the floor and `xl` as the ceiling, so the block sits at the same height on a
  short and a tall screen (P2-3).
- **Composed steps** appear in `OnboardingScreen` as arithmetic on tokens (`xl + md`,
  `lg + xs`, `lg - xs`, `xl - xs`); no new value is introduced.
- **Literals outside the scale** remain where a dimension is not a spacing step:
  `QuietAction`'s `padding(horizontal = 2.dp)`, the `14.dp` gap between an `AppRow` icon
  and its label, `ChoiceRow`'s `20.dp` marker width, and the `6.dp` favourite dot.

## 4. Surface and border treatment

- **One border primitive.** `Hairline` in `ui/Components.kt` is a `Box` that fills its
  width, is `LauncherSpace.hairline` tall, and is filled with the `separator` tone.
- **No cards and no containers.** There is no `Card`, `Surface`, shadow or rounded
  container anywhere in `ui/`. Screens are a `Column` with
  `background(palette.background)` and text placed directly on it. `ScreenTitle` draws its
  title on the page with a trailing slot, not in a bar. `UndoNote` has no container,
  background or border: it is a line of the page, so it costs the screen nothing when
  absent.
- **`surfaceTint` is transparent.** `ColorScheme.inLauncherPalette` in
  `ui/theme/Theme.kt` sets `surfaceTint = Color.Transparent` because Material3 otherwise
  tints every elevated surface toward the primary tone. Page-level containers
  (`surfaceContainerLowest`, `surfaceContainerLow`, `surfaceVariant`,
  `secondaryContainer`) map to `background`; elevated ones (`surfaceContainer`,
  `surfaceContainerHigh`, `surfaceContainerHighest`) map to `separator`, which is the
  palette's single step off the background and is the long-press menu's container (P0-1).
  `outline` and `outlineVariant` also map to `separator`, and `onSurfaceVariant` to
  `primary` so menu item text stays the launcher's own tone.
- **Empty space is deliberate.** `HomeScreen` is the case the rule is written for: the
  clock is the only large element, and nothing is added to fill the screen. `TodayBlock`
  returns early when it has neither an action nor an agenda entry rather than drawing a
  heading over nothing.
- **A separator sits above what it separates, never below.** `SettingsScreen` draws
  `if (index > 0) Hairline()` in the Essentials and Hidden lists, so the rule belongs to
  the row beneath it and the list has no trailing rule — a list never looks closed
  (P2-9).
- **Where a separator is drawn.**
  - Under a screen header, between the title block and the page content: `AppListScreen`
    (after the search row) and `SettingsScreen` (after `ScreenTitle`).
  - Between rows of a short list the user edits: `SettingsScreen`'s Essentials and Hidden
    sections.
  - Between the onboarding's explanatory lines and its closing line, in
    `OnboardingScreen`.
  - Not in the app drawer's alphabetical list: `AppListRow` draws no separator at all,
    because at that length a rule per row is texture (P1-5). The letter headers carry the
    structure instead.
- **The drawer's bottom row and the heading under its rule.** `AppListScreen`'s bottom row
  carries two actions of its own, `QuietAction("Settings")` and
  `QuietAction("Refresh", onClick = { viewModel.refreshApps() })`, placed directly on the
  page, and the `SectionHeading` under the search row's hairline states what is showing —
  `"${list.size} apps"`, or `"Results"` once a query is typed.
- **Elevated surfaces are the exception, not a second system.** The only place the
  `separator` tone is used as a fill rather than a rule is the long-press `DropdownMenu`,
  and it arrives there through the `ColorScheme` mapping rather than through launcher code.

## 5. Icons

- **The icon box is 28dp.** `AppIcon` in `ui/Components.kt` is called from `AppRow` with
  `sizeDp = 28`; it draws a `Box(Modifier.size(sizeDp.dp))` containing an `Image` with
  `ContentScale.Fit`, `contentDescription = null` (the row's label carries the name).
- **The slot is emitted even when the bitmap is null.** The `Box` is unconditional; only
  the `Image` inside it is guarded by `bitmap?.let`. A bitmap that resolves after the first
  frame therefore cannot shift the label sideways as the list scrolls (P1-7).
- **Icons are opt-in through `LayoutMode`.** `AppRow` emits `AppIcon` and the
  `14.dp` spacer after it only when `layout == LayoutMode.ICON_TEXT`. `LayoutMode` in
  `data/SettingsStore.kt` is `TEXT, ICON_TEXT`.
- **Text is the default.** `SettingsStore.read()` falls back to `LayoutMode.TEXT`, so a
  fresh install draws no icon at all. `SettingsScreen` exposes the choice as a `ChoiceRow`
  labelled "Layout" with the options "Text" and "Icon + text".
- **A shortcut with no icon keeps its slot and shows its label alone.**
  `AppRepository.bitmapFor` resolves through `iconFor`, which reads only the
  `LauncherActivityInfo` cache; a pinned shortcut has no entry there, so the bitmap is null
  and the empty box remains. The launcher does not request the shortcut icon permission.
- **Icons load off the main thread.** `AppIcon` uses `produceState` keyed on `target.key`
  to call `viewModel.bitmapFor(target, px)`, with `px` derived from `LocalDensity`.

## 6. Interaction states

- **Press.** There is no custom indication anywhere in `ui/`; each control takes the
  default ripple from the composition's `LocalIndication`, applied by `combinedClickable`
  (`QuietAction`, `AppRow`), `clickable` (`OnboardingScreen`'s private `TextAction`),
  `toggleable` (`ToggleRow`) or `selectable` (`ChoiceRow`). `QuietAction` keeps the
  clickable outermost so the ripple and the hit area both span the full minimum height
  (P2-4), with the label centred inside it.
- **Focus.** No control defines a focus style and no row is explicitly `focusable`. The
  only focus handling in the product is `AppListScreen`'s `FocusRequester`, which requests
  focus for the search field as the drawer opens so the user does not pay a tap each time.
- **Disabled.** No control renders a disabled state: nothing in `ui/` sets `enabled =
  false`, and there is no disabled tone or opacity. Every rendered control is interactive.
  A control that currently changes nothing says so in words instead —
  `SettingsScreen` explains the "Show today" toggle with a `SecondaryText` rather than
  disabling it (P2-10).
- **Selected.** `ChoiceRow` marks the selected option three ways at once: tone (`primary`
  against `secondary`), weight (`Medium` against `Normal`), and a `20.dp` by
  `LauncherSpace.hairline` marker under the label, filled with `primary` when selected and
  `Color.Transparent` when not, so nothing shifts on selection (P0-6).
- **`ToggleRow` state without colour.** The trailing word is "On" or "Off", its weight is
  `Medium` when checked and `Normal` when not, and its tone is `primary` when checked and
  `secondary` when not — three signals that move together. The word is used rather than a
  tick glyph because the default font family does not cover U+2713 and a system fallback
  could render a tofu box (P1-9). The whole row is the target (P1-12).
- **Favourite state without colour.** `AppRow` draws a `6.dp` circle in the `secondary`
  tone, and separately sets `stateDescription` so TalkBack announces the state the dot
  only implies.
- **48dp minimum touch target.** `LauncherSpace.touchTarget` is applied as
  `defaultMinSize(minHeight = ...)` on `QuietAction`, `AppRow`, `ToggleRow` and each
  `ChoiceRow` option column, and as `heightIn(min = ...)` on `OnboardingScreen`'s
  `TextAction`. This is the Android minimum (P0-5).
- **Declared roles and semantics.**

  | Control | Role / semantics |
  | --- | --- |
  | `QuietAction` | `role = Role.Button` on its `combinedClickable` |
  | `AppRow` | `role = Role.Button`; `onLongClickLabel` states the long-press action; `stateDescription` reports Essentials membership; its Pin/Unpin action carries `contentDescription` naming the app |
  | `ToggleRow` | `toggleable(value, role = Role.Switch)` on the whole row |
  | `ChoiceRow` | `selectable(selected, role = Role.RadioButton)` per option |
  | `TextAction` (Onboarding) | `clickable` with no explicit role |
  | Drawer search field | `semantics { contentDescription = "Search apps" }`, because the visible "Search" placeholder is a sibling `Text` and not part of the field |
  | Settings row actions | `contentDescription` naming the app and the direction: "Move <label> up", "Move <label> down", "Unpin <label>" |

  Repeated labels are always disambiguated this way: a list of identical "Pin", "Up" or
  "Unpin" words is otherwise indistinguishable to a screen reader.
