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

Two mechanisms fought each other: the batch window hid chapters from the adapter, then the
glide/anchor machinery re-pinned the viewport every time that dataset changed. Batching created the
position shifts; anchoring patched them with timing guesses, so each fix moved the bug elsewhere. The
history reflects it — reverts `cbf2dd0564` and `d74e1bf2ef`, follow-ups `f9b85bccca` and `545aad8583`.

The symptom the user reported, "the preview opens on the first chapters instead of the one I was
reading", traces to `f1abb58fac`, which made `rebuildPageGrid()` iterate `batchedItems()` instead of
`filteredItems()`. The grid window seeded at index 0, and the jump to the target happened later via
`scrollToCurrentPage()` -> `ensureChapterInBatch()`, which returns early while history is still
loading. Step 1 removes that structure, so the symptom should be gone once verified on device.

## Tracked bugs

- **pages stay detached after open until a drag** - suspected glide-induced `attachItems = false`, to be
  re-checked after step 2. Found in the first step 1 audit; not a step 1 regression.

## Step 1 device audit (OPPO CPH1931, debug build of `bd3eaa47a5`)

Manga: Black Clover, 854 chapters. Recents shows last read = Ch. 2, page 11 of 26.

Observed, reproducible on two consecutive opens (waited 3 s and 6 s, state did not change):

- Swipe left opens the panel on the **Ch. 2 section header** ("Pages 26") at the very end of the
  list. The old "opens on the first chapters" symptom is gone.
- **No page thumbnails are attached under the Ch. 2 header and no blue marker is visible.** The
  section state is `Loaded` (`pageCount` 26) but the rows are missing, so the panel is not on page 11.
- One short vertical drag (60 px) is enough: on the next IDLE `loadVisiblePageSections()` runs, the
  pages attach and the rest of the chapter appears. So the data is fine; the attach is never retried
  after the open.
- Logcat: FATAL 0, crash buffer empty. Back navigation to Library/Recents worked.

Cause, from reading the code: `loadSectionPages` passes `attachItems = !isScrolling` to `onPagesLoaded`.
While the panel opens, the only thing that leaves the recycler non-IDLE is the glide (`glideTo` /
`animateToPosition`). If the page list arrives inside that window, `pagesAttached = false` and the
items are never inserted. The recovery path (`loadVisiblePageSections` -> `attachCachedPages`) needs a
scroll-state change, `settleAnchorToTop` early-returns while non-IDLE, and the corrections that use
`scrollToPositionWithOffset` emit no state change. So nothing retries until the user drags. Pre-existing
bug that the glide used to mask, which step 2 should remove. Still to be confirmed on device after 2c.

Not checked in this pass: that every chapter is reachable by scrolling to the top, filter/sort/back to
top, chapter-row swipe (read/bookmark). These go into the combined audit after step 2c.

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
