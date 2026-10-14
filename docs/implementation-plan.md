# Implementation plan — persistent multi-page Home

**Status: implemented in part; see §10 for what shipped, what is dead code, and what this
plan claimed that the tree does not do.** Sections 1–9 below are the original design as
written when nothing was implemented; every path, symbol and line number in them refers to
code as it existed at the time of writing (`versionCode 5` / `0.2.3`). Where a file did not
exist then, it is named as *new*. Read §10 before treating any statement in §§1–9 as a
description of the current build.

The launcher's Home is currently one fixed page. This document specifies how it becomes
1–5 user-arrangeable pages (default three: Today / Launch / Focus), persisted across
process death and reboot, with one source of truth, a migration that loses nothing, and
horizontal paging that does not fight the Android system.

---

## 0. What exists today (the surface this extends)

| Concern | Current code | Note |
| --- | --- | --- |
| Preferences | `data/SettingsStore.kt` — `SharedPreferences("launcher_settings")`, `MODE_PRIVATE` | 13 keys, `read()` at :27, `update()` at :45 |
| Settings model | `data/SettingsStore.kt:121` `LauncherSettings` | Immutable data class, `copy()` for edits |
| Reactive settings | `SettingsStore.settings: StateFlow<LauncherSettings>` :24–25 | Written inside `update()` at :64 |
| Favourites (one flat list) | `KEY_FAVOURITES = "favourites"` :102, newline-joined :50 | `AppQuery.favourites(order, apps)` `core/AppQuery.kt:34` |
| Hidden apps | `KEY_HIDDEN = "hidden"` :103 | `AppQuery.visible` :24 |
| UiState | `ui/LauncherViewModel.kt:41` `UiState` | Built by `combine` at :158–199 |
| Home screen | `ui/HomeScreen.kt:44` `HomeScreen(viewModel, state)` | Clock, Today block, "Essentials" list, undo note |
| Row primitive | `ui/Components.kt:123` `AppRow(...)` | Long-press opens `DropdownMenu` :190 with a `combinedClickable` :143 |
| Navigation | `LauncherViewModel.kt:39` `enum class Screen`; `_screen` | `BackHandler`s in `LauncherActivity.kt:109–114` |
| Theme tokens | `ui/theme/Theme.kt` — `LauncherColors`, `LauncherSpace`, `LauncherType` | Four tones only; see `docs/design-system.md` |
| minSdk / toolchain | `app/build.gradle:20` minSdk 26, compileSdk 36 | `foundation` on the BOM (compose-bom `2026.06.01` :127) — `HorizontalPager` is available without a new dependency |

The one flat `favourites` list is the thing this design has to absorb. Everything that
reads `state.favourites` today (`HomeScreen.kt:89–103`, `AppListScreen.kt:207`) reads a
**derived, page-less** view. The plan keeps that derivation valid: a page's app list is
still resolved through `AppQuery.favourites(page.itemKeys, visibleApps)`.

---

## 1. (a) Data model and serialization

### 1.1 Model

Five small immutable types. All live in `data/` (Android-free except the store itself) so
they stay JVM-unit-testable, matching the existing `LauncherSettings` / `LaunchTarget`
convention.

```kotlin
// data/HomePage.kt  (new)

/** What a page is for. Drives which composable body renders, not what the user sees. */
enum class PageType { TODAY, APPS, NOTES, WIDGET }

/**
 * A page's own settings. A single flat class is used rather than a sealed hierarchy:
 * a page's type changes without its identity, and a sealed type would force the store to
 * understand every variant when writing a partial change. Unknown-at-read-time fields are
 * ignored, which is what keeps an older build from rejecting a newer file (see 1.4).
 */
data class PageConfig(
    val heading: String? = null,        // overrides the default section label, e.g. "Essentials"
    val itemKeys: List<String> = emptyList(), // LaunchTarget.key values, ordered
    val columns: Int = 1,               // reserved; only 1 is honoured today
    val showClock: Boolean = false,     // APPS pages may carry the clock; TODAY always does
)

data class HomePage(
    val id: String,                     // stable, opaque, never derived from the name
    val type: PageType,
    val name: String,                   // user-visible; renamed freely
    val config: PageConfig = PageConfig(),
)

/** The whole Home layout. `pages` is the order. */
data class HomeLayout(
    val pages: List<HomePage> = emptyList(),
    val activePageId: String? = null,   // last page shown; resolved against `pages` on read
) {
    companion object {
        const val MIN_PAGES = 1
        const val MAX_PAGES = 5
        /** The layout a fresh install gets. Ids are stable constants, not random. */
        val DEFAULT: HomeLayout = HomeLayout(
            pages = listOf(
                HomePage("p_today",  PageType.TODAY, "Today",  PageConfig(showClock = true)),
                HomePage("p_launch", PageType.APPS,  "Launch", PageConfig(heading = "Essentials")),
                HomePage("p_focus",  PageType.NOTES, "Focus"),
            ),
            activePageId = "p_today",
        )
    }
}
```

**Why `id` is separate from `name`.** Reorder, rename and re-type must not disturb stored
item ordering, the active page, or a future per-page feature flag. A name is a label a
user edits; an id is identity. Ids are therefore never renumbered on reorder — order is
the list position and nothing else.

**As built (see §10):** the five types ship and the model serializes, but `PageType` is not
a live enum. `WIDGET` is unreachable — `SettingsStore.setPageType` has zero callers — so no
page can ever be created with that type, and the "type exists so the enum is stable" claim
in §4 is the whole truth of it. `PageConfig.heading` is likewise frozen: `updatePageConfig`
has no production caller, so neither a page's type nor its heading can be changed at
runtime. `PageConfig.showClock` is stored and never read by `ui/`. `PageConfig.columns` has
no reader anywhere.

**Why `id` is never derived from `name`.** Two pages may legitimately be called "Work",
and a rename must not orphan the `activePageId`.

**Id generation for added pages.** `"p_" + UUID.randomUUID().toString().replace("-", "")`
— 1 + 32 chars, no separator characters, so it is safe inside every encoding below. The
three default ids are fixed literals so migration (section 5) can name them.

### 1.2 Serialization format

**Decision: one JSON array under a single new key `home_pages`.**

```
key:    home_pages
value:  [{"id":"p_today","type":"TODAY","name":"Today","showClock":true},
         {"id":"p_launch","type":"APPS","name":"Launch","heading":"Essentials",
          "items":["com.a/.A","com.browser/shortcut-id"]},
         {"id":"p_focus","type":"NOTES","name":"Focus"}]
keys:   home_active_page_id  -> "p_today"
```

Written with `org.json.JSONArray` / `JSONObject`, which ship in the Android platform —
**no new dependency**. Read through the same classes with every field treated as optional.

**Why JSON and not the existing newline-separated format.** The current format
(`SettingsStore.kt:50`, `joinToString("\n")`) is one flat list of keys with no field
boundaries. Pages need four fields per record (id, type, name, config) and an ordered
sublist per record. Encoding that as delimited text means a delimiter collision the moment
a user renames a page to a string containing the delimiter, and escaping has to be
invented and versioned by hand. JSON gives nesting, self-describing types and escaping for
free; `JSONObject.optString` / `optJSONArray` / `optInt` make every field optional at read
time, which is exactly the forward-compatibility property required below.

**Why one blob and not one key per page.** `SharedPreferences` keys are a flat namespace
and there is no atomic multi-key commit relative to a reader; one `putString` is one
atomic write, so a reorder or a delete can never be observed half-applied. The
alternative — `home_page_<id>_name`, `home_page_<id>_items`, plus a `home_page_order`
index — costs one parse per page on every read and makes reorder a multi-key write whose
intermediate states are visible. One blob also keeps `read()` a single `getString`.

**Why not `SharedPreferences.getStringSet`.** A `StringSet` has no order by contract;
`HashSet` iteration order is unspecified. Order is the whole point here.

**Size.** Worst case, 5 pages × ~40 keys × ~45 bytes ≈ 9 KB. `SharedPreferences` is loaded
into memory wholesale in this process and there is no per-value limit; 9 KB is far below
where a blob would be a problem. The existing `favourites` blob is the same shape.

**Forward-compatibility rules, made explicit.**
1. Every field is optional on read; a missing field takes the Kotlin default.
2. An unknown page `type` reads as the page being *skipped*, not as a crash — see 1.4.
3. An unknown *field* is ignored (JSON parsing naturally does this).
4. A version int `home_pages_version` (written as `1`) is present so a future change can
   tell an old file from a malformed one. It is advisory: v1 readers ignore a higher
   value and still parse field-by-field rather than refusing.

### 1.3 Robustness to reorder and rename

* **Reorder** writes the same objects in a new array order. No id changes, so
  `activePageId` and every `itemKeys` entry stay valid.
* **Rename** writes one `name`. No other field moves. Because items are keyed by
  `LaunchTarget.key` (`model/LaunchTarget.kt:17`) and not by page or index, renaming never
  touches app ordering.
* **Re-type** writes one `type` and leaves `items` intact, so a Launch page switched to
  Focus and back finds its apps still there.
* **Delete** removes one array element. If the deleted page was active, `read()` resolves
  `activePageId` against the surviving pages and falls back to index 0 (section 1.4).
* **Stale item keys** are already handled: `AppQuery.favourites` `mapNotNull`s through
  `apps.associateBy { it.key }` (`core/AppQuery.kt:35–36`), so an uninstalled app drops out
  without disturbing surviving order. This is the existing uninstall behaviour and this
  design inherits it per page.

### 1.4 Read-path validation (the single source of truth)

All of the following live in one pure function so the store, the migration and the tests
agree by construction:

```kotlin
// core/HomeLayoutCodec.kt  (new) — pure, no Android types, JVM-testable
object HomeLayoutCodec {
    fun encode(layout: HomeLayout): String
    fun decode(raw: String?): HomeLayout?          // null = absent/unusable, caller decides
    fun sanitize(layout: HomeLayout): HomeLayout   // clamp + repair, see below
}
```

`sanitize` enforces, in order:
1. At most `MAX_PAGES`, at least `MIN_PAGES` (empty → `HomeLayout.DEFAULT`).
2. `id` non-blank and unique; a blank or duplicate id is replaced by a fresh one.
3. `name` non-blank after `trim()`; blank becomes the type's default label.
4. `itemKeys` de-blanked and de-duplicated, order preserved.
5. `activePageId` exists in `pages`; otherwise index 0's id.

Derived-state rule: **`sanitize` runs on every read and after every mutation, in the
store.** Nothing downstream — no composable, no view model — re-derives, clamps or
repairs. That is what makes the store the single source of truth rather than a cache
several components each interpret.

---

## 2. (b) SettingsStore API surface

New key constants alongside the existing ones (`data/SettingsStore.kt:97–112`):

```kotlin
private const val KEY_HOME_PAGES = "home_pages"
private const val KEY_HOME_PAGES_VERSION = "home_pages_version"
private const val KEY_HOME_ACTIVE_PAGE = "home_active_page_id"
private const val HOME_PAGES_VERSION = 1
```

**As built:** `KEY_HOME_PAGES` and `KEY_HOME_ACTIVE_PAGE` are live. `HOME_PAGES_VERSION`
has no reader or writer; it is the unused end of the pair implied by
`KEY_HOME_PAGES_VERSION`, and `SettingsStore.HOME_PAGES_VERSION` in particular has no
caller. Treat both as vestigial.

`LauncherSettings` (`SettingsStore.kt:121`) gains one field:

```kotlin
val home: HomeLayout = HomeLayout.DEFAULT,
```

`read()` gains two lines, after the existing assignments at :42:

```kotlin
home = HomeLayoutCodec.sanitize(
    HomeLayoutCodec.decode(prefs.getString(KEY_HOME_PAGES, null))
        ?.copy(activePageId = prefs.getString(KEY_HOME_ACTIVE_PAGE, null))
        ?: HomeLayout.DEFAULT
),
```

`update()` gains one `putString` inside the existing `prefs.edit { }` block at :47–63:

```kotlin
putString(KEY_HOME_PAGES, HomeLayoutCodec.encode(next.home))
putString(KEY_HOME_ACTIVE_PAGE, next.home.activePageId.orEmpty())
putInt(KEY_HOME_PAGES_VERSION, HOME_PAGES_VERSION)
```

The active-page key is separate from the blob on purpose: switching pages is a hot path
(every horizontal swipe) and must not rewrite the whole page array.

### 2.1 Public functions

```kotlin
/** Adds a page after the active one. No-op at MAX_PAGES. Returns the new id, or null. */
fun addPage(type: PageType = PageType.APPS, name: String? = null): String?

/** Removes a page. No-op when only MIN_PAGES remain. Active page falls back to a neighbour. */
fun removePage(id: String)

/** Renames. Blank input is ignored rather than stored. */
fun renamePage(id: String, name: String)

/** Moves a page to an absolute index, clamped. Order is list position; ids never change. */
fun movePage(id: String, toIndex: Int)

/** Records which page is showing. No-op if the id is not in the layout. */
fun setActivePage(id: String)

/** Replaces one page's config wholesale. */
fun updatePageConfig(id: String, config: PageConfig)

/** Appends a LaunchTarget.key to a page's item list if absent. */
fun addPageItem(pageId: String, key: String)

/** Removes a key from a page's item list. */
fun removePageItem(pageId: String, key: String)

/** Moves an item within its page, clamped; order is list position. */
fun movePageItem(pageId: String, from: Int, to: Int)

/** Appends `keys` to a page, skipping any already present, keeping their order. */
fun addPageItems(pageId: String, keys: List<String>)
```

**As built:** three of these are dead. `updatePageConfig` has no production caller, which
is why a page's type and heading cannot be edited; `movePageItem` has no production caller
(drag-reorder on the Launch page goes through a different path); and `setPageType` — named
here as the re-typing entry point and in §1.1's rationale for a flat `PageConfig` — has zero
callers, so `PageType.WIDGET` can never be reached. `addPage`, `removePage`, `renamePage`,
`movePage`, `setActivePage`, `addPageItem` and `removePageItem` are all reachable from edit
mode.

### 2.2 Existing functions: kept, reinterpreted, never removed

The signature of each is unchanged, so `LauncherViewModel` (`LauncherViewModel.kt:320`,
:369) and every `AppRow` caller (`Components.kt:129–130`) compile untouched.

```kotlin
fun toggleFavourite(key: String)   // unchanged signature
fun moveFavourite(key: String, delta: Int)  // unchanged signature
```

New semantics, chosen so no data is silently lost:

* `toggleFavourite(key)` adds/removes `key` in the **active page's** `itemKeys` — but only
  when the active page's type is `APPS`; on any other type it applies to the first `APPS`
  page, so a pin from the drawer always lands somewhere real. If no `APPS` page exists, it
  creates one named per `settings/layout` convention. `favourites` in the flat legacy
  sense is now the union, in page order, of every page's `itemKeys`.
* `moveFavourite(key, delta)` moves the key within **the page that contains it**; a key on
  no page is a no-op. This keeps the existing Home reorder gesture (`SettingsScreen`'s
  favourites section) working on whatever page the key lives on.

**Why reinterpret rather than add parallel methods.** Every call site already means "the
user is arranging apps on Home". Adding `togglePageItem` alongside would leave two
functions meaning the same thing and a future reader unable to tell which is canonical.
Reinterpreting keeps one name per concept.

### 2.3 Migration key handling

`KEY_FAVOURITES` (`favourites`) is **read, not written, during migration and left in place
afterwards.** Removing it would make a rollback to `0.2.3` show an empty Essentials list.
It is dead weight of a few hundred bytes; correctness across a downgrade is worth more.
The same applies to every pre-existing key — this plan writes no key that did not already
exist and deletes none.

---

## 3. (c) UiState changes

`ui/LauncherViewModel.kt:41` `UiState` gains:

```kotlin
val pages: List<HomePage> = emptyList(),
val activePageIndex: Int = 0,
val pageEditing: Boolean = false,
val editingPageId: String? = null,     // null = overview grid; set = editing one page
val pagerTargetPage: Int? = null,      // one-shot index for scrollToPage; cleared after use
```

`pages`, `activePageIndex` and `editingPageId` are derived from
`settingsStore.settings.home`; `pageEditing` is view-model-local (`_pageEditing`, a
`MutableStateFlow<Boolean>`) because edit mode is ephemeral UI state and must not be
persisted — a launcher that reopens in edit mode after a reboot is a bug.

`activePageIndex` is why the pager is hoisted rather than remembered in the composable:
Settings and the drawer can change the layout, and the pager must follow. Index, not id,
crosses into `UiState` because `PagerState.currentPage` is an `Int`; it is derived from
`activePageId` by `indexOfFirst`, and `pagerTargetPage` carries an explicit destination for
the canonical "add a page, then jump to it" flow.

`favourites` keeps its existing meaning (`UiState` :45) as **the active page's resolved
apps**, so `HomeScreen` continues to read a single list and no existing consumer breaks.
A second field is not needed: pages that are not active render from their own
`PageConfig.itemKeys` inside the pager.

New view-model functions (thin wrappers over the store):

```kotlin
fun enterPageEdit(overview: Boolean = true)  // pageEditing = true
fun editPage(id: String)                     // editingPageId = id
fun leavePageEdit()                          // pageEditing = false; editingPageId = null
fun addPage(); fun removePage(id: String); fun renamePage(id: String, name: String)
fun movePage(id: String, toIndex: Int); fun goToPage(index: Int)
```

Both `_screen` (`LauncherViewModel.kt:261`) and `Screen` (:39) are **unchanged**: pages
live inside `Screen.HOME`, not as new screens. Onboarding keeps its own `BackHandler`
(`LauncherActivity.kt:107`), and Home remains the back-stack root.

---

## 4. (d) Page types → composables, real vs empty

| `PageType` | Composable | Status | Renders |
| --- | --- | --- | --- |
| `TODAY` | `TodayPage` (new, in `ui/pages/`) | **Real.** Composed from `Clock` (`HomeScreen.kt:162`) + `TodayBlock` (:191) + the `date` line (:75) | Clock, date, Zendo block when `todayEnabled` and `state.zendo` is non-empty. No app list. |
| `APPS` | `AppsPage` (new) | **Real.** Composed from `state.settings.layout` + `AppRow` (`Components.kt:123`) | An optional heading and `config.itemKeys` resolved through `AppQuery.favourites` (`AppQuery.kt:34`). Empty → `EmptyNote("No pinned apps yet.")` (`Components.kt:387`), the wording Home already uses at `HomeScreen.kt:90`. |
| `NOTES` | `NotesPage` (new) | **Real but empty.** | Its `EmptyNote` copy **is the feature**: it states the page holds nothing yet and that notes are not built. No storage is implied. What shipped is a live note *field* (a Focus page whose text you can type into), with the honest empty state alongside it — the "no notes are stored" half held, the "nothing to type into" half did not. |
| `WIDGET` | `WidgetPage` (new) | **Not reachable at all.** | The empty state is not merely unimplemented as a composable: no page can ever carry this type, because nothing calls `setPageType`. The enum value compiles and is serialized if hand-written into prefs, and that is the entire extent of it. App widgets are not hosted, for the reason given below (`AppWidgetHost` needs a bind permission, a host id and a lifecycle this launcher does not have — compare `docs/lock-screen-feasibility.md`). |

`HomeScreen.kt:44` becomes a pager *shell* and nothing else — it keeps
`.fillMaxSize().background(palette.background).systemBarsPadding()` (:57–61) and the undo
note strip (:108), and its body becomes the pager below. `TodayBlock` (:191) and the
`display()` helper (:211) move into `TodayPage.kt` in the same change; **HomeScreen ends up
containing no page content**, which is the concrete answer to "no hardcoded three-page
config scattered across components."

The three default pages are produced by `HomeLayout.DEFAULT` (section 1.1) — one literal,
one place. Nothing else in the codebase may name a page count, a page name or a page index.

---

## 5. (e) Migration from the single-Home state

Trigger: `home_pages` is absent from prefs. Presence of the key at any version means a
layout exists and migration does not run again. This is idempotent by construction.

```
read(theme, layout, favourites, hidden, ...)          // existing keys, untouched
  |
  +-- getString("home_pages", null) != null ?
  |      yes -> decode + sanitize                       (normal path)
  |      no  -> migrateLegacyHome(prefs)                 (one time only)
  |
migrateLegacyHome:
   legacyFavourites = favourites.toKeyList()             // line 32's parse, reused
   todayEnabled     = getBoolean("today_enabled", true)
   pages = []
   if (legacyFavourites.isNotEmpty())
       pages += HomePage("p_launch", PageType.APPS, "Essentials" or the page's default name,
                         PageConfig(itemKeys = legacyFavourites))   // order preserved exactly
   if (todayEnabled)
       pages += HomePage("p_today", PageType.TODAY, "Today", PageConfig(showClock = true))
   if (pages.isEmpty())                                  // today off, no favourites
       pages = HomeLayout.DEFAULT.pages                  // never migrate to zero pages
   layout = HomeLayout(pages, activePageId = pages.first().id)
   write it under "home_pages" + "home_active_page_id" + "home_pages_version"
```

Properties, each testable on the JVM with a Robolectric `SharedPreferences`:

1. **No data loss.** `legacyFavourites` is copied verbatim and in order. A user with 30
   pinned apps sees 30 rows on a page named after Essentials, in the same order. This is
   the requirement "must not break existing favourites/pins/PWA shortcuts" and it is met
   by *copying the key list*, not by re-deriving it.
2. **PWA shortcuts survive.** Shortcut keys are `"$packageName/$shortcutId"`
   (`LaunchTarget.kt:18`). They are opaque strings to this layer and travel through the
   copy unchanged. Nothing in the codec parses or rewrites a key.
3. **Hidden apps are unaffected.** `hidden` stays where it is and `AppQuery.visible`
   (`AppQuery.kt:24`) still filters before resolution, so a hidden-but-pinned app stays
   hidden and simply does not resolve.
4. **Favourites is not cleared.** After migration `KEY_FAVOURITES` still holds its old
   value, so a downgrade to `0.2.3` finds the flat list intact.
5. **Order of writes.** The blob, the active id and the version int are written in one
   `prefs.edit { }` (the existing block at `SettingsStore.kt:47`), so a kill mid-write
   cannot leave a version without a blob. `apply()` in `androidx.core.content.edit`
   commits to memory synchronously and to disk asynchronously; the in-memory state is
   consistent immediately.
6. **Reboot.** Nothing here is in-memory-only. `read()` is a pure function of
   `SharedPreferences`, which the platform loads from the file before `onCreate`
   (`LauncherActivity.kt:48` already relies on exactly this for `theme`).
7. **Process death.** `_screen` and edit mode are view-model state and are allowed to
   reset (matching the manifest's `stateNotNeeded` comment at `LauncherActivity.kt:33`);
   `home` is persistence and is not.

Migration runs inside `read()`, matching how the store already has no separate init step.
It is **write-on-read**: the first `read()` after the update persists the new blob. The
alternative — migrating lazily on first edit — leaves a window where a crash shows a Home
with no pages.

---

## 6. (f) Edit-mode design

**Entering, three ways.**
1. Long-press on the empty area of any page (a `detectTapGestures(onLongPress)` on the
   page background, *behind* the app rows, so `AppRow`'s own long-press at
   `Components.kt:145` still wins; hit-testing gives the child priority).
2. Long-press a page dot in the indicator strip.
3. Settings → "Home pages", which enters overview mode.

Entering sets `pageEditing = true`, `editingPageId = null` → the **overview grid**: all
pages as numbered tiles in order, current one marked. The home content dims behind it. The
word "Edit" and the tile numbers carry the state, not colour alone — the accessibility rule
`docs/design-system.md` §1 states for every state change.

**Dragging (reorder).** In overview, a tile is dragged by its own `pointerInput` on the
tile: `detectDragGesturesAfterLongPress` while editing. Feedback is the dragged tile lifted
(`Modifier.graphicsLayer { translationX/Y }` plus `zIndex`) and the sibling tiles animating
to close the gap (`animateItem()` on a `LazyRow`/`LazyVerticalGrid` item). On drop the
view emits `movePage(id, toIndex)`; the store writes the new array order and
`activePageId` is untouched because ids did not change.

**Renaming.** A tile carries the page name; tapping the name (not the tile body) opens an
inline `BasicTextField` in place of the `Text` — the pattern already used in
`AppListScreen.kt` for the search field (`BasicTextField` + `FocusRequester` +
`KeyboardActions`). Commit on `ImeAction.Done` or focus loss; `escape` and Back revert.
Blank input is discarded by the store (`renamePage` ignores blank), so the page can never
end up nameless. No dialog: a modal for one text field is disproportionate, and the
existing codebase has exactly one dialog (`PauseDialog`, `Components.kt:410`) that earns it.

**Deleting.** A tile gets a "Remove" action. At `MIN_PAGES = 1` the action is absent, not
disabled-then-inert, and the reason is stated in the strip (`EmptyNote`-style line). No
confirm dialog: the deletion is reversible in the same session by "Undo", rendered with
the existing `UndoNote` (`Components.kt:438`), which is the launcher's established pattern
for a reversible destructive action (`HomeScreen.kt:108`, hiding an app). Undo restores
the page *at its old index* with its `itemKeys` intact — which is why delete must keep the
removed `HomePage` in view-model state, not just its id.

**Exiting.** A "Done" `QuietAction` (`Components.kt:94`) in the strip, or Back.

**Back, step by step** (all inside `Screen.HOME`, added to `LauncherRoot` in
`LauncherActivity.kt:104` as one more `BackHandler` *above* the existing ones, so it is
consulted first while Home is showing):

| State when Back is pressed | Result |
| --- | --- |
| Inline rename field focused | Commit-by-revert: exit the field, discard the edit. Stays in edit mode. |
| Editing one page (`editingPageId != null`) | Return to the overview grid. Stays in edit mode. |
| Overview grid (`pageEditing`, `editingPageId == null`) | Exit edit mode, return to the page. |
| Not editing, on a page that is not the first | Go to page 0. (Decided, and stated here because it is the one non-obvious step.) |
| Not editing, on page 0 | **Not handled** — falls through to the system, which is Home. |

Every state above is a distinct row because `BackHandler(enabled = …)` must be a single
predicate; the implementation is one handler whose `enabled` is
`state.screen == Screen.HOME && (state.pageEditing || state.activePageIndex > 0)` and
whose body switches on the same cases in the same order.

**As built:** the inline-rename row and the two edit-mode rows did not survive contact
with the tree. Back from the pager returns to page 0 (`activePageIndex > 0`) and edit mode
is exited by its own action rather than by Back; there is no `editingPageId` branch and no
rename-field branch, because renaming happens in the edit surface's own field and commits
there. Read the table as the design, not as a map of the shipped Back behaviour.

---

## 7. (g) Gesture plan for horizontal paging

### The conflict, named

Android 10+ system navigation offers gesture navigation with edge swipes: back from either
the left or right screen edge, home from the bottom. An app-drawn horizontal pager that
starts at an edge competes with the system for the same gesture. Android's own guidance
for launchers (and the platform's own `ViewPager2`-era behaviour) is to inset the pager
away from the edges and let the system own them. Additionally, two Compose mechanisms
already want horizontal drags here: `AppRow`'s host if it ever becomes a list with swipe
actions, and any nested scrolling — none exists today (`HomeScreen.kt:67` is a
`verticalScroll`, `AppListScreen.kt` a `LazyColumn`), which is the fact that makes a
horizontal pager safe to introduce now and the reason the plan pins it before one appears.

### Chosen APIs

1. **`HorizontalPager` + `rememberPagerState`** from
   `androidx.compose.foundation.pager` — already on the BOM
   (`app/build.gradle:136`, compose-bom `2026.06.01`); **no new dependency**. Used with
   `pageCount = { state.pages.size }`, and a `key = { state.pages[it].id }` so page
   identities survive reorder without recomposing unrelated pages.

2. **Page width = the measured content width**, so a page occupies exactly one viewport
   and a swipe never reveals two pages at once. A page indicator (dots) is drawn in the
   strip; it is `LauncherType.section`-sized and `secondary`-toned, with the current dot
   `primary` **and** carrying `contentDescription` "Page 2 of 3" — colour is not the only
   signal.

3. **Edge exclusion via `Modifier.systemGestureExclusion`** on the pager's root:
   `Modifier.systemGestureExclusion(Rect(left = edgeWidthPx, right = widthPx - edgeWidthPx,
   top = 0f, bottom = height))`. `edgeWidthPx` comes from the platform's own exclusion
   limit, `ViewConfiguration.get(context).scaledHorizontalScrollFactor`-independent
   constant read through `LocalView` → `view.rootWindowInsets.systemGestureExclusion` or,
   on API < 29, a fallback of 24.dp. This is the *inverse* of the usual use — it tells the
   system "do not claim these horizontal edges for Back" while leaving the middle free to
   the pager. Only the middle band is excluded from system gestures so the pager can have
   it; the outer `edgeWidthPx` on each side stays system-owned.

   Note the truth about this API: `systemGestureExclusion` is **a request the system may
   ignore** (it caps the excluded region, and in gesture navigation it cannot cover the
   full height of the screen on some versions). The design therefore does not depend on it
   for correctness — it is an optimisation on top of the layout decision below.

4. **The pager's content is inset from both edges** by `edgeWidthPx` (the same value, used
   as a horizontal padding), so a swipe that begins in the system's edge zone begins on
   inset background the pager does not consume and the system Back gesture is unambiguous.
   This is the approach that does not rely on (3) being honoured. Visually the inset is
   absorbed by the existing `LauncherSpace.gutter` (`Theme.kt:68`) — the page gutter grows
   from 24.dp to `24.dp + edgeWidthPx`, which is inside the design system's spacing scale
   and reads as intentional.

5. **Widget interaction and nested scroll.** `HorizontalPager`'s drag is fed by a nested
   scroll connection; a child that consumes horizontal drags (an app row with future swipe
   actions) takes the gesture first because nested scrolling dispatches child-to-parent.
   The design states the rule for that future case: any horizontal-draggable child must
   claim its drag and the pager will yield, which is the correct priority — the user
   dragging a row means the row. Today no child does this, so the pager receives every
   horizontal drag in the inset middle band.

6. **Vertical scroll and the pager coexist without special handling.** A page's vertical
   content keeps `verticalScroll` (`HomeScreen.kt:67`); Compose's nested scroll dispatches
   by axis, so a vertical drag scrolls the page and a horizontal drag pages. No
   `orientationLock`, no custom `NestedScrollConnection` is needed, and none is planned —
   adding one would be the mechanism that *creates* the conflict it is meant to solve.

7. **`userScrollEnabled = !state.pageEditing`.** Paging is disabled while editing, because
   in edit mode a horizontal drag means "reorder the tiles". This is a hard rule; without
   it, dragging a tile to the edge would page instead of reorder.

8. **`settledPage` for persistence.** A `LaunchedEffect(pagerState)` keyed on
   `pagerState.settledPage` calls `viewModel.goToPage(index)` when it differs from
   `state.activePageIndex`. Listening to `settledPage` and not `currentPage` is the point:
   `currentPage` changes at the halfway mark of a fling and would write a page the user
   then abandons.

9. **One-shot programmatic scroll.** `state.pagerTargetPage` is consumed by a
   `LaunchedEffect` that calls `pagerState.scrollToPage(index)` and then
   `viewModel.clearPagerTarget()` — `animateScrollToPage` is **not** used at startup, so
   the first frame is on the persisted page rather than animating to it from 0.

### What is deliberately not used

* **`Modifier.pointerInput { detectHorizontalDragGestures }` hand-rolled paging.** It
  cannot participate in nested scroll and would win against children by construction.
* **`AnchoredDraggable` / a custom offset-based pager.** Reimplements `HorizontalPager`
  without its fling physics or its `PagerState`.
* **`ViewPager2` via `AndroidView`.** Mixes view and Compose lifetimes for no gain.
* **`HorizontalPager` with `beyondViewportPageCount = 1` indefinitely.** Only the
  neighbours matter; the default (0, plus `beyondViewportPageCount = 1` while settling) is
  used, because each page composes app rows that resolve icon bitmaps through
  `LauncherViewModel.bitmapFor` (`LauncherViewModel.kt:407`) and pre-composing pages the
  user is not on would spend those loads on nothing.

### Gesture summary table

| Gesture | Region | Owner |
| --- | --- | --- |
| Horizontal drag, vertical centre band | `[edge, width-edge]` | `HorizontalPager` |
| Horizontal drag from left/right screen edge | `[0, edge)` / `(width-edge, width]` | System (Back) |
| Vertical drag, anywhere | page content | `verticalScroll` on the page |
| Tap on an app row | row bounds | `AppRow` (`combinedClickable`, `Components.kt:143`) |
| Long-press on an app row | row bounds | `AppRow` → `DropdownMenu` (:190) |
| Long-press on page background | outside rows | Enters edit mode |
| Long-press then drag (editing) | overview tile | Tile reorder; pager disabled by `userScrollEnabled = false` |

---

## 8. Files touched (design intent, no edits made)

| Path | Change |
| --- | --- |
| `data/HomePage.kt` | **New.** `PageType`, `PageConfig`, `HomePage`, `HomeLayout`, `DEFAULT`. |
| `core/HomeLayoutCodec.kt` | **New.** `encode` / `decode` / `sanitize`, pure and JVM-testable. |
| `data/SettingsStore.kt` | `LauncherSettings.home`; ~3 lines in `read()` (:27–43); 3 `put`s in `update()` (:47–63); 10 new functions; 4 new key constants (:97–112); `toggleFavourite`/`moveFavourite` (:67, :83) reinterpreted, signatures unchanged. |
| `ui/LauncherViewModel.kt` | 5 `UiState` fields (:41); `favourites` flow (:143) reads the active page; `pageEditing` state; 7 new functions; 4 new `UiState` fields wired into the `combine` at :158–199. |
| `ui/HomeScreen.kt` | Becomes a pager shell; `TodayBlock` (:191) and `display()` (:211) move out; page content leaves this file entirely. |
| `ui/pages/` | **New.** `TodayPage.kt`, `AppsPage.kt`, `NotesPage.kt`, `WidgetPage.kt`, `PageStrip.kt` (indicator + label + Done), `PageEditGrid.kt`. |
| `ui/Components.kt` | No signature changes. Reused: `AppRow` (:123), `EmptyNote` (:387), `QuietAction` (:94), `UndoNote` (:438), `SectionHeading` (:63). |
| `LauncherActivity.kt` | One `BackHandler` added in `LauncherRoot` (:104) ahead of the existing ones. |
| `ui/SettingsScreen.kt` | A "Home pages" row opening overview edit mode; the favourites section reorders within the current page. |
| `app/src/test/java/com/zendolauncher/core/` | **New** `HomeLayoutCodecTest.kt` (JVM) and `HomeMigrationTest.kt` (Robolectric, real `SharedPreferences`). |

## 9. What this plan does not claim

* `WIDGET` is a documented empty state, not widget hosting. As built it is not even that —
  no page can carry the type (§4, §10).
* `NOTES` holds no notes and stores nothing. As built it has a live text field whose
  contents are not persisted.
* `PageConfig.columns` is reserved and only `1` is honoured; `2` is not designed here. As
  built nothing reads it at all.
* `systemGestureExclusion` is a request the platform may decline; the inset in §7.4 is what
  the design actually depends on.
* This does not enable `HomeScreen`'s long-press behaviour changing `AppRow`'s menu — the
  row's long-press is untouched.

## 10. As-built status (revision to §§1–9)

Sections 1–9 were written before any code existed. A verification pass over the tree found
the shipped feature is **partly user-reachable**, and that the sections above describe more
than shipped. This section is normative; where it and §§1–9 disagree, this section wins.

### Reachable today

* **1–5 page pager** with horizontal paging, and a page indicator you can tap to switch
  pages.
* **Persisted active page** across process death and reboot.
* **Edit mode**: rename a page, move it up, move it down, remove it, add a page.
* **Today page** with honest empty states.
* **Launch page** with drag-reorder and pin/unpin.
* **Focus page** with a live note field (text you type is not persisted).

### Not reachable — dead or inert

* `PageType.WIDGET` can never be created: `SettingsStore.setPageType` has zero callers. The
  enum value is unreachable from any UI path.
* A page's **type** and **heading** cannot be edited: `updatePageConfig` has no production
  caller.
* `PageConfig.showClock` is stored but ignored — nothing under `ui/` reads it.
* No callers anywhere: `PageConfig.columns`, `PageMove`, `activeAppsPageId`,
  `LauncherViewModel.movePageItem`, `SettingsStore.HOME_PAGES_VERSION`.

### Known stale copy

`ui/SettingsScreen.kt` still says the Today block sits "between the date and your pinned
apps" and calls the pinned section "Essentials". The pinned list now lives on the **Launch
page** and is edited by dragging in Home, not in Settings. The Settings copy has not been
rewritten to match; treat those two strings as known-wrong until they are.

