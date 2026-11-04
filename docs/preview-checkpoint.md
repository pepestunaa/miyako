# Preview teardown checkpoint

Live status for the page-preview rebuild. One row per step, updated in the same commit that changes
the code. `todo.md` ("Phase A") holds the plan and the full symbol lists; this file only records how
far the work actually got. Format and rules: "Checkpoint dan Oper Antar Agent" in `agents.md`.

## Resuming here with no prior context

1. Read this file, then `git log --oneline -5`, then the "Next action" section below. Start there.
2. Do not open `MangaDetailsAdapter.kt`, `MangaDetailsController.kt` or `todo.md` as a warm-up, and do
   not re-read the plan steps: the work list is in `todo.md` lines 53-74 and is only needed when a step
   is being implemented, not when an audit is being run.
3. Do not trust a `PASS` you cannot reproduce: it means someone ran the audit on the device.
4. `PENDING` means the code changed and compiled but nobody looked at the phone. Not done, and the
   matching `todo.md` box stays unchecked.
5. Do not re-derive what is already written under "Why the old code was broken" below.
6. Structural questions (`who calls anchorToCurrentPage`, what still depends on a section holder) go
   through the `codebase-memory` skill, or a subagent that returns paths plus symbol names — never a
   manual read of whole files.

## Status

| step | scope | commit | build | verify |
|------|-------|--------|-------|--------|
| 0 | drop WIP diagnostics (debug logs, `onPreviewScrollIdle`) | folded into `545aad8583` baseline | ok | PASS (launch clean, FATAL 0, crash buffer empty) |
| 1 | remove the batch window (`batchedItems` and friends) | `248f70c0ca` | ok | PENDING — its own target (all chapters present, filter/sort/back-to-top) is still untested; the first audit only found the tracked bug below |
| 2 | remove the glide/anchor machinery | `git log --grep 'smooth scroll'` | ok | PENDING — combined audit with step 1 after 2c |
| 2b | remove reopen-position leftovers (`ViewportAnchor`, `savedPreviewAnchor`) | `git log --grep 'viewport anchor'` | ok | PENDING — combined audit after 2c. `anchorChapterId` kept: `scrollToCurrentPage` lands on a finished chapter with it, not the marker |
| 2c | clean up callers in `MangaDetailsController` | `git log --grep 'scroll-cancel'` | ok | PENDING — combined audit (steps 1, 2, 2b, 2c) next. `onPreviewScrollIdle` was already gone in step 0 |
| 3 | keep-list contract, verify nothing above was lost | — | — | — |
| 4 | disable-then-measure the priority loader chain | — | — | — |
| 5 | rebuild: full chapter list, pages inserted only on viewport entry | — | — | — |

## Decisions already made — do not reopen

- Preview target stays **the last-read page**: `refreshCurrentPage()` reads
  `presenter.allHistory.maxByOrNull { it.last_read }` and `chapter.last_page_read`. The alternative
  ("last page tapped in the preview") was considered and rejected.
- Reopen position memory is **intentionally gone**. After step 2 the panel starts at the top and
  lands directly on the target with no animation. That is the desired end state.
- Step 4 items exist because page sections exploded during layout. Disable and measure; do not delete
  on sight.

## Why the old code was broken (so nobody re-derives it)

The batch window hid chapters and the glide/anchor machinery re-pinned the viewport on every dataset
change; anchoring patched the resulting shifts with timing guesses, so each fix moved the bug elsewhere
(reverts `cbf2dd0564` and `d74e1bf2ef`). The "opens on the first chapters" symptom came from
`f1abb58fac` making `rebuildPageGrid()` iterate `batchedItems()`: the window seeded at index 0 and the
jump to the target went through `ensureChapterInBatch()`, which returned early while history loaded.
Steps 1 and 2 removed both mechanisms.

## Tracked bugs

- **pages stay detached after open until one drag.** First audit (Black Clover, 854 chapters, build
  `bd3eaa47a5`, reproducible across two opens, last read Ch. 2 page 11 of 26): the panel opened on the
  Ch. 2 header, which said "Pages 26", but no thumbnails and no marker appeared; one 60 px drag attached
  them. Cause from the code: `loadSectionPages` hands `attachItems = !isScrolling` to `onPagesLoaded`,
  and the glide was the only thing leaving the recycler non-IDLE while the panel opens; the retry path
  needs a scroll-state change. Pre-existing and masked by the glide, not a step 1 regression. Settled by
  A2 below. Full first-audit report: `git show bbfb192a5e:docs/preview-checkpoint.md`.

## Next action — one combined device audit (steps 1, 2, 2b, 2c)

Black Clover, 854 chapters, last read = Ch. 2, page 11 of 26. Build / install / logcat commands are in
the "Audit E2E" section of `agents.md`. The user may be using this phone: check the foreground app
first and take short, controlled snapshots. Report one line per item, with what was observed.

- A1 the preview opens on the last-read chapter, not on the first chapters
- A2 thumbnails attach on their own with no drag, and the blue marker sits on Ch. 2 page 11 — this is
  the tracked bug above: the first audit showed "Pages 26" in the header and no rows until a 60 px drag
- A3 scrolling to the very top reaches all 854 chapters, no window anywhere
- A4 filter, sort, back to top, scroll to end
- A5 chapter-row swipe (read / bookmark) on the main list, and back navigation
- A6 fast and slow open/close, repeated open/close, diagonal drag, vertical drag
- A7 `FATAL` count 0 and the crash buffer empty

Decision tree:

- All PASS → set verify=PASS for steps 1, 2, 2b and 2c here, close the tracked bug, check the matching
  boxes in `todo.md`, commit the docs, then stop and ask the user before step 4.
- Only A2 fails → the glide was not the cause. `loadSectionPages` hands `attachItems = !isScrolling` to
  `onPagesLoaded`, and the else-branch leaves `pagesAttached = false` with no retry because
  `loadVisiblePageSections` only runs on a scroll-state change. Make the open path re-attach cached
  pages once — no timer, no poll loop, no extra `postDelayed` chain. Own commit, then re-run A1 and A2.
- A3-A7 fail → step 1 regression: record the exact symptom here, leave the boxes unchecked, stop, report.
- Any crash → stop immediately and report the stack trace from the crash buffer.

## What is measured, not asserted

`verify: PASS` on step 0 and 1 refers to the checks named in the table only. Nothing here yet claims a
RAM or smoothness win; the memory claim in particular stays unproven until step 1 and step 5 are
compared on device.
