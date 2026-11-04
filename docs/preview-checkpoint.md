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
| 1 | remove the batch window (`batchedItems` and friends) | `248f70c0ca` | ok | PENDING — device audit 2026-11-04 (see "Audit result"): its own target (all chapters present T3, filter/sort T4) passed, but T2 and the T4 back-to-top smoothness did not |
| 2 | remove the glide/anchor machinery | `d1e378bc91` | ok | PENDING — same audit; T2 FAIL is in this area |
| 2b | remove reopen-position leftovers (`ViewportAnchor`, `savedPreviewAnchor`) | `a21864ae59` | ok | PENDING — same audit. `anchorChapterId` kept: `scrollToCurrentPage` lands on a finished chapter with it, not the marker |
| 2c | clean up callers in `MangaDetailsController` | `9e4cce9784` | ok | PENDING — same audit. `onPreviewScrollIdle` was already gone in step 0 |
| 2d | gate the page attach on the panel's own top row (`bc58e53595` + `4118c1ca7d`) | `4118c1ca7d` | ok | FAIL (user, 2026-11-05) — removed one blocker, T2 unchanged; route: "Next action" |
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

- **pages stay detached after open until one drag** — cause was `attachItems = !isScrolling` meeting the
  glide, which kept the recycler non-IDLE on open. Fixed by step 2: on 2026-11-04 pages attach by
  themselves. Not closed formally until T2 passes. Full report: `git show bbfb192a5e:docs/preview-checkpoint.md`.
- **target drifts off screen after the open (T2).** The 2026-11-04 mechanism (chapters above the target)
  is gone; the 2026-11-05 report is only the target chapter's own pages sitting above the marker. Row 2d
  removed a real second blocker and did not fix this. Two routes left: "Next action".
- **scrolling the panel feels heavy (T4, still FAIL 2026-11-05).** Every drag frame runs
  `loadVisiblePageSections()` → `onPageSectionBound` → `getGlobalPositionOf(section)`, and FlexibleAdapter
  resolves that by scanning the item list — with 854+ sections that is O(children x items) per frame. Not
  measured yet. `currentPagePosition()` and `sectionPositionOf()` scan the whole list per call too.

## Next action — T2: the gate fix is not enough, one observation decides the rest

Retest 2026-11-05 on `4118c1ca7d` (Black Clover, 854 chapters, last read Ch. 2 page 11, user report):

| item | result |
|------|--------|
| T1 parks on Ch. 2 | PASS — parks on the chapter |
| T1 marker on the top row | FAIL (user, 2026-11-05) — page 11 and its marker are not on the first visible row |
| T2 auto attach, no stack | FAIL (user, 2026-11-05) — same stack of earlier pages as before |
| scroll up/down still heavy | FAIL (user, 2026-11-05) — unchanged, own topic, see "Tracked bugs" |
| `logcat -d \| grep -c FATAL` | 0 (agent, 2026-11-05) |
| `logcat -d -b crash` | empty (agent, 2026-11-05) |

The gate was measuring the wrong thing and now measures the panel's own top row (`bc58e53595` asked
`findFirstVisibleItemPosition()`, which `ChapterLayoutManager` overrides with `getFirstPos`, dropping
every child above the **screen** line `statusInset + mainActionBarSize`; `pages_preview_recycler` has no
padding, so the header parked at its top was always dropped and the parked target compared as "above the
viewport"). That much was real. It did not fix T2, so it removed a *second* blocker, not the one the user
sees. Full write-up of the reasoning: `git show 4118c1ca7d:docs/preview-checkpoint.md`.

What is still true from the two-file answer: `pendingScrollToCurrent` and `currentPage?.first == chapterId`
hold (nothing clears the flag outside `maybeScrollToCurrentPage`, and the marker is drawn, so
`currentPage != null`), and `maybeScrollToCurrentPage` is the only caller of `anchorToCurrentPage` on the
attach path. So the remaining candidates are narrow:

1. the attach the user sees happens on a path that never calls `maybeScrollToCurrentPage` — `addItems`
   from `rebuildPageGrid()` (a dataset rebuild keeps the pixel anchor, so the header stays at the top and
   pages 1-10 appear under it, with **no snap**), reachable through `setChapters` only when
   `pendingScrollToCurrent` is already false; or
2. `anchorToCurrentPage` runs and `scrollToPositionWithOffset` does not move the viewport for a page item
   that sits mid-row (`ChapterLayoutManager`, span 3) — that is branch B and it needs the user's OK.

**One question decides them, no device session:** after the panel has opened and sat still for ~10 s,
what is on the very first row — the Ch. 2 *header* ("Chapter 2 / 24 pages"), or a thumbnail row? Header
means the viewport never moved off the park, so the landing did not fire → route 1. Thumbnail means the
viewport moved but not to page 11 → route 2 (branch B).

Then: route 1 → make the rebuild path re-land a pending target (reuse `pendingScrollToCurrent`, no new
state). Route 2 → ask the user before reopening branch B.

## Audit result — 2026-11-04 (Black Clover, 854 chapters, last read Ch. 2 page 11, HEAD `8f0b266f56`, code `9e4cce9784`)

| item | result |
|------|--------|
| T1 lands on Ch. 2 | PASS (user, 2026-11-04) |
| T2 auto attach + marker on page 11 | FAIL (user, 2026-11-04) — attach works, but chapters above load and overlap the target; marker drifts down off screen |
| T3 all 854 chapters, no gap | PASS (user, 2026-11-04) |
| T4 filter, sort, top, end | PASS functionally (user, 2026-11-04); back to top scroll heavy / not smooth |
| T5 swipe row, back | PASS (user, 2026-11-04) |
| T6 open/close fast+slow, diagonal, vertical | PASS (user, 2026-11-04) — no jump, no flicker |
| `logcat -d \| grep -c FATAL` | 0 (agent, 2026-11-04) |
| `logcat -d -b crash` | empty (agent, 2026-11-04) |

## Audit result — 2026-11-05 retest on `bc58e53595` (same manga and marker)

| item | result |
|------|--------|
| T1 lands on Ch. 2, no animation | PASS (user, 2026-11-05) |
| T2 auto attach + marker on page 11 | FAIL (user, 2026-11-05) — Ch. 2 thumbnails still stacked on the earlier pages of the same chapter |
| T4 filter, sort, top, end | PASS functionally (user, 2026-11-05); back to top still heavy |

The user picked branch A on 2026-11-05 (the target chapter's own pages 1-10 sit above the target row,
rows intact, nothing overlaps); that is the case the "Next action" answer explains. Branch B — real
overlap from row geometry in `ChapterLayoutManager` — stays closed. Full branch write-up:
`git show d80b030dc2:docs/preview-checkpoint.md`.

## What is measured, not asserted

`verify: PASS` on step 0 refers to the checks named in the table only. Nothing here claims a RAM or
smoothness win; the memory claim stays unproven until step 1 and step 5 are compared on device.
