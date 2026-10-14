# Highlights and Tasks UX

Scope: versionName 0.3.1. Full-screen editors (`ui/Editors.kt`); lists stay
as dialogs (`ui/HighlightOverlays.kt`); stores unchanged in shape
(`data/HighlightStore.kt`, `data/TaskStore.kt` + new `notes` field).

## Principle

Highlights need room for thought, tasks need speed. Same monochrome language
(background/text tones, hairlines, `QuietAction`), different interactions:
a full-screen writing surface for highlights, one-line Quick Add plus an
optional-details editor for tasks.

## Highlights

**Layout** (`HighlightEditorScreen`): pinned header (Back … Save) + scrolling
content. Title single-line (optional; Next moves focus to the body), large
multiline body (`heightIn(min = 200.dp)`), "Show as primary highlight on
Today" toggle, Manage section for existing records (Archive, Delete). Save
never scrolls away: the header is outside the scroll, and `imePadding` +
`systemBarsPadding` keep the keyboard off the active input. No pager on this
screen, so long-form scrolling can never trigger a page swipe. Autofocus goes
to the title on open.

**Validation (documented rule).** Title is optional, content is not: a fully
empty draft refuses with "Add a title or some thoughts first." and the editor
stays open with the text intact. A note-only draft derives its title from the
body's first line (80 chars), like a paper note. Previously a blank title
closed the editor and silently stored nothing — the input was lost.

**Drafts.** Title/note/pin live in the view model, seeded on open and written
to the store only on Save — never per keystroke, so typing cannot duplicate
records and rotation cannot lose text. Back with a dirty draft opens "Discard
this highlight? / Discard / Keep editing"; a clean Back returns Home
directly. Discard clears the draft; the confirm itself closes on a second
Back without touching the draft.

**List.** Home shows the primary highlight (title + note + Edit / All
highlights). "All highlights" dialog: tap a row to edit (opens the
full-screen editor), body preview capped at 2 lines, Primary marker instead
of a Pin button on the primary, Archive inline (reversible via Restore),
Delete behind an explicit confirm dialog naming the record. Archived items
are sectioned off with Restore/Delete. Full long-form text never renders in
a row.

**Persistence.** `launcher_highlights` prefs, JSON array, `StateFlow`; never
auto-erased (no midnight logic anywhere). Edit rewrites one record by id;
archive/delete map/filter by id, so siblings are untouched (covered by
`HighlightStorePersistenceTest`).

## Tasks

**Quick Add** stays one line everywhere it appears (Home summary, All-tasks
dialog): type, Done submits, input clears for the next one, blank input is
ignored and never stored. The draft is dialog-local because it is submitted
or cleared immediately — there is no unsaved work to protect.

**Details editor** (`TaskEditorScreen`): title (required, autofocus, Done
saves), Due date (optional row: "Choose date" opens the system
`DatePickerDialog`, formatted `d MMM yyyy`, Clear removes it), Notes
(optional multiline, 120dp min). Only title is required; due date and notes
are never invented when left empty. Exposed fields match the model exactly:
`TaskItem` gained `notes` (backward-compatible JSON: omitted when blank,
`optString` default on read, so pre-0.3.1 blobs load unchanged); priority,
categories, reminders, and subtasks are deliberately absent.

**Management.** Check/Uncheck toggles (quiet ○/●, done secondary +
strikethrough), "Hide/Show on Home" per task, edit opens the full editor,
delete asks first. Home shows `homeSummary(3)` + done/visible count; the
dialog shows due date ("Due 15 Oct"), first notes line, and Home visibility
per row.

**Keyboard.** Autofocus on open, single-line title (Done = save, no multiline
conflict), `imePadding` on both editor and dialog content, buttons keep
48dp targets with content-description labels. No double-submit path: Quick
Add clears on submit; editor Save writes one record per tap and a blank title
refuses instead of storing.

## Accessibility and themes

Both editors reuse `LauncherType`/`LauncherSpace`, honor light/dark via the
palette, keep the 48dp minimum on every action, and label every repeated
action ("Delete", "Check") through its dialog/row context. Week-strip and
pager semantics are untouched.

## Known limitations

- No rich text, reminders, or sync — local plain text only, by design.
- Due date is a date, not a time; no notification fires from it.
- `DatePickerDialog` is the platform picker (system-styled, not monochrome);
  chosen deliberately over a custom calendar dialog.
- Drafts survive rotation (view model) but not process death; a kill
  mid-typing loses the draft while saved records are safe.
