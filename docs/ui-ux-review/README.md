# UI/UX review — before and after screenshots

Eleven PNGs, captured from the real Compose UI, at the two revisions the redesign
spans. Nothing here is hand-drawn, retouched, or mocked up: every image is a bitmap
that `View.draw(Canvas)` produced from the launcher's own window.

| Revision | Commit | What it is |
| --- | --- | --- |
| before | `2ac913a` — *Add Zendo Launcher: a monochrome minimalist Android home screen* | The last commit before the redesign |
| after | `main`, at the commit that lands this file's current revision | The redesign, including everything landed since `22273c1` |

The "before" set was captured from a clean checkout of `2ac913a` in a `git worktree`
under the system temp directory; that worktree has been removed and `main` is untouched.
The "after" set has been recaptured three times as the tree moved, always by running the
harness in the working tree it is documenting:

1. at `85a84aa`, the first time the harness worked;
2. at `fd539ba`, the revision the published `debug-0.2.2` release was built from, so the
   images and the shipped APK would come from the same tree;
3. against the working tree whose only rendering-visible source change is the onboarding
   sentence below (the other source edits since `fd539ba` are a deleted unused function and
   two comments, none of which draw), so the capture shows the first-run page as it now
   ships rather than as it was before that fix. This is the set committed here.

`22273c1` (*Redesign the launcher UI*) is the redesign commit itself; the "after" tree
carries the follow-up work that closed the audit's findings, so the images show the
redesign as it actually shipped rather than as it was first written.

Between the second and third captures, `after-apps`, `after-apps-search` and
`after-settings` came back **byte-identical**, and the two Home captures differed only in
the rows the clock occupies (`y=157..288`, 132 rows, every differing pixel inside them).
`after-onboarding` differs because its copy genuinely changed: the page used to teach only
the long press, and now names the visible `Pin` action as well. Its diff is confined to the
text block (`y=563..1444`); the title, the first paragraph and the bottom `Got it` row are
untouched, the last because that row is pinned to the bottom of the screen rather than
following the text.

An earlier pair of runs at `fd539ba` established the same invariance from the other
direction: `after-onboarding`, `after-apps`, `after-apps-search` and `after-settings`
reproduced byte-for-byte, and the two Home captures again differed only within the clock
rows (`y=158..288`). The horizontal extent of a clock difference moves with which digits
changed; the row band does not. The clock is the only element that varies between runs, and
it is the capture-time minute.

## Images

### after (`main`, working tree described above)

| File | Screen | Theme |
| --- | --- | --- |
| `after-onboarding.png` | Onboarding, first run | Light (`SYSTEM`, Robolectric's default is day) |
| `after-home-light.png` | Home | Light |
| `after-home-dark.png` | Home | Dark |
| `after-apps.png` | App list, empty query | Dark |
| `after-apps-search.png` | App list, query `ca` | Dark |
| `after-settings.png` | Settings, scrolled to the top | Dark |

The app list, the search screen and Settings are all dark because the capture script
sets the theme to Dark to take `after-home-dark.png` and then leaves it there. They are
not dark by default.

### before (`2ac913a`)

| File | Screen | Theme |
| --- | --- | --- |
| `before-home-light.png` | Home | Light |
| `before-home-dark.png` | Home | Dark |
| `before-apps.png` | App list, empty query | Dark |
| `before-apps-search.png` | App list, query `ca` | Dark |
| `before-settings.png` | Settings, scrolled to the top | Dark |

There is no `before-onboarding.png`: onboarding did not exist at `2ac913a`. The
`ONBOARDING` screen, `OnboardingScreen.kt`, and the `onboardingSeen` setting were all
introduced by the redesign.

## What the images show

Read against each other, the pairs show the redesign's actual changes:

- **Home.** The empty-Essentials line went from *"Nothing pinned yet. Open All apps and
  long-press an app to add it here."* to *"No pinned apps yet."* — the instruction moved
  to onboarding. The clock, date and the `All apps` / `Settings` row are otherwise
  recognisably the same design in both revisions.
- **App list.** `2ac913a` had no screen title; the search field sat at the very top with
  `Home` on the right of the section heading below it. The redesign adds a real
  `All apps` title with `Home` in its trailing slot, and gives the empty state a
  heading (`0 apps` / `Results`) it did not have before.
- **Settings.** The largest visible change. `2ac913a` used Material3 pill switches for
  the three booleans; the redesign replaces them with the text toggle (`On` / `Off`,
  weight and tone carrying the state). Section headings also grew from 11sp to 12sp.
- **Search.** Both revisions filter to nothing for `ca`, so both show the "nothing
  matches" line. The redesign adds the `Clear` affordance next to the field, which
  `2ac913a` had no equivalent of — it could only be emptied by leaving the screen.

## How they were produced

Robolectric 4.17, `@Config(sdk = [36])`, `@GraphicsMode(GraphicsMode.Mode.NATIVE)`, and
`@Config(qualifiers = "w360dp-h800dp-xxhdpi")`, which yields a 1080x2400 bitmap. The
activity under test is the shipped `LauncherActivity`; the state is driven through the
activity's own `LauncherViewModel`, so screens and navigation are the production ones.

**`captureToImage()` does not work here, and that is the finding.** An earlier attempt
used it and produced flat images. The reason is in the stack trace, not in the pixels:
`captureToImage()` routes through `WindowCapture.forceRedraw`, which waits on a
PixelCopy that never completes under Robolectric and throws
`ComposeTimeoutException: Condition still not satisfied after 2000 ms`. Every capture
dies there; nothing about the Compose tree is at fault. The working path is to draw the
window's content view into a software bitmap directly:

```kotlin
val view = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
bitmap.eraseColor(AndroidColor.BLACK)   // not the palette background
view.draw(Canvas(bitmap))
```

Before this was trusted, it was checked against the smallest possible case —
`Text("ABCDEFG")` on white at 64sp — decoded and counted in-process: 1080x2400, 225
distinct colours, 41,281 pixels differing from the white background. A hand-drawn
`Canvas.drawText` control rendered the same glyphs, so the bitmap plumbing was sound and
the earlier failure was the capture API, not the environment.

The harness is `app/src/test/java/com/zendolauncher/ui/ScreenshotCaptureTest.kt` (kept,
so the images can be regenerated and re-checked):

```
./gradlew :app:testDebugUnitTest --tests "com.zendolauncher.ui.ScreenshotCaptureTest" --rerun
```

`--rerun` is not optional if the images have been deleted. `app/build/screenshots/` is not
a declared output of the test task, so Gradle reports the task `up-to-date`, skips it
entirely, and writes nothing — leaving no images and no `stats.txt`, with no error. Without
`--rerun` this only produces fresh images when the test's inputs have changed.

It writes to `app/build/screenshots/`, which is gitignored. The "before" images came from
the same harness adapted to the older API (`2ac913a` has no onboarding and no loading
flag), run in a `git worktree` checked out at `2ac913a`. That worktree has been removed.

The README's numbers are the harness's own output, not a reading of the files after the
fact. The harness decodes each capture in memory and asserts on it: at least 8 distinct
colours, at least 1,000 pixels differing from the modal background, at least 300 pixels
of strong ink (a per-channel distance from the background above 90, which the
anti-aliasing fringe does not reach), and a SHA-256 unique across the run. A flat image,
or two captures that are the same bytes, fails the test. That last check is the one the
earlier attempt lacked: three of its captures shared a digest and nothing noticed.

## Pixel statistics, as observed

Every image is 1080x2400. `nonBackground` counts pixels differing from the modal colour;
`ink` counts pixels far enough from it in every channel to be glyph or hairline rather
than fringe.

### after (`pixel-stats-after.txt`)

| Image | distinct | nonBackground | ink | background | sha256 |
| --- | --- | --- | --- | --- | --- |
| `after-onboarding.png` | 365 | 118101 | 100704 | `#FFF7F7F5` | `b2488866…0f1eaf24` |
| `after-home-light.png` | 366 | 26870 | 24305 | `#FFF7F7F5` | `f906c470…e1508d91` |
| `after-home-dark.png` | 372 | 26737 | 24772 | `#FF0B0B0C` | `cb681899…0f9c2241` |
| `after-apps.png` | 371 | 21545 | 17232 | `#FF0B0B0C` | `a0efe5ae…3405ec` |
| `after-apps-search.png` | 372 | 20605 | 16614 | `#FF0B0B0C` | `8ca1454e…8fff8c0f` |
| `after-settings.png` | 373 | 121352 | 104383 | `#FF0B0B0C` | `a5fcc967…7f636c25` |

### before (`pixel-stats-before.txt`)

| Image | distinct | nonBackground | ink | background | sha256 |
| --- | --- | --- | --- | --- | --- |
| `before-home-light.png` | 366 | 40126 | 35785 | `#FFF7F7F5` | `d60b2f04…7f0fa69` |
| `before-home-dark.png` | 372 | 39890 | 36387 | `#FF0B0B0C` | `26c9d773…0b72949` |
| `before-apps.png` | 368 | 17489 | 13396 | `#FF0B0B0C` | `71568e9d…17663dc4` |
| `before-apps-search.png` | 370 | 15144 | 11392 | `#FF0B0B0C` | `edf3d019…e59ea905` |
| `before-settings.png` | 489 | 142786 | 123224 | `#FF0B0B0C` | `44f7e77f…20363af` |

All eleven digests are distinct, and the digests listed above are the digests of the
files in this directory — they were recomputed after the copy and match. The background
colours are exactly the palette's own: `#F7F7F5` for light, `#0B0B0C` for dark, which is
independent evidence that Compose, not the black seed, painted the page.

## What the images do NOT show

Stated plainly, because the alternative is a screenshot that quietly overstates itself.

- **No installed apps, anywhere.** Robolectric returns an empty installed-app list, so
  `after-apps.png` and `before-apps.png` show the honest empty state (`0 apps`,
  `No launchable apps found.`) and both Home screens show the empty Essentials note.
  No app rows, no letter headers, no icons, and no `Pin` action are visible, because
  there is nothing to draw them for. The redesign's row-level work — the pin affordance
  in the drawer, the favourite dot, the icon layout — is not shown by any image here and
  is covered by `LauncherUiTest` instead.
- **No Today block.** The only shipped `ZendoSource` is `UnavailableZendoSource`, which
  always returns null, so the Today section renders nothing in either revision. This is
  a product state, not a capture failure.
- **No system UI.** Only the activity's content view is drawn, so the status bar,
  navigation bar, their icons and the clock in the status bar are absent. The empty
  bands at the top and bottom of each image are the space `systemBarsPadding()` reserves
  for them, not a rendering gap.
- **No dialogs, menus or transient states.** The row long-press menu, the undo note and
  the loading state are not captured. The dropdown is a separate window and would need
  its own capture path.
- **The clock and date are the moment of capture**, not fixed values: 11:23 in the
  committed "after" set, 09:40 in the "before" set, Wednesday 14 October in all of them.
  The sets were captured hours apart, which is why the times differ. Within the "after"
  set the clock is the only thing that varies between runs: three of its six images
  reproduce byte-for-byte when the harness is re-run, and two more (`after-home-light`,
  `after-home-dark`) differ only within the rows the clock occupies. `after-onboarding`
  also reproduces byte-for-byte now that the copy change is committed; it differed in the
  previous run only because that run was the one that introduced the change.
- **Not a device.** These are Robolectric-rendered bitmaps at a chosen qualifier
  (360x800dp, xxhdpi). Font rasterisation, insets and window behaviour on real hardware
  can differ.
- **`before-settings.png` is scrolled to the top** of a scrollable screen, as is
  `after-settings.png`. Neither shows the pinned-apps or hidden-apps sections further
  down.

## Nothing was omitted silently

Every screen named in the brief is present for both revisions, with the single exception
of onboarding, which does not exist at `2ac913a`. No capture came back blank, partial or
duplicated; had one, it would be described here rather than left out.
