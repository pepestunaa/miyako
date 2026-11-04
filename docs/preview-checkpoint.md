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
| 2d | gate the page attach to the panel's own top row (`bc58e53595` + the measurement fix here) | this commit | ok | PENDING — T2 target; steps and verdict: "Next action" |
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
- **target drifts off screen after the open (T2).** Seen 2026-11-04 among the chapters above the target;
  the attach gate `bc58e53595` removed that part, and the 2026-11-05 retest still failed among the target
  chapter's own pages. Cause found without the device (see "Next action") and fixed in row 2d.
- **back to top feels heavy and not smooth (T4, 2026-11-04).** Functionally correct, scroll is janky.
  Not measured; may be the same dataset-change cost as the bug above or the step 4 loader chain.

## Next action — T2: re-run the device list on the top-row gate fix

The branch-A question is answered; it was sub-question 1, and the landing call was simply unreachable
on the open pass. Nothing else about the landing was wrong.

- `bc58e53595` closed `scrollToCurrentPage()` with the deferred `loadVisiblePageSections()` instead of
  the direct `onPageSectionBound(section)`, so the whole open pass now routes through
  `isAboveFirstVisibleRow`.
- That gate asked `layoutManager.findFirstVisibleItemPosition()`, which `ChapterLayoutManager` overrides
  with `getFirstPos(recycler, toolbarHeight)` (`widget/LinearLayoutManagerAccurateOffset.kt`): it drops
  every child whose **recycler-local** bottom is above the **screen** line `statusInset +
  mainActionBarSize` (~80dp). The override exists for the chapters list, whose rows are padded below the
  app bar. `pages_preview_recycler` has no such padding, so the section header parked at the panel top
  (minHeight 48dp) is always dropped and `firstVisible` reports the row *below* it — the parked target
  compared as `position < firstVisible`, `onPageSectionBound` returned before `attachCachedPages` and
  `markPageSectionLoading`, so no `insertPageItems` and no `maybeScrollToCurrentPage`.
- `pendingScrollToCurrent` and `currentPage?.first == chapterId` both still held: the flag is only
  cleared inside `maybeScrollToCurrentPage`. Sub-question 2 is a non-issue, and branch B (row geometry)
  stays closed — the rows were intact, they just arrived late.
- The rows did arrive through two escape hatches, which is why thumbnails are not permanently missing:
  while the parked header is the only child laid out the filtered list is empty → `NO_POSITION` → gate
  lets it through; and after a drag the header's bottom grows past the line. In those passes the
  viewport is parked on the header, so Ch. 2 pages 1-10 insert between the header and page 11 — exactly
  what the user reported.

The fix keeps the gate and changes only the measurement: the top row is the smallest adapter position of
a child that still overlaps the panel's own bounds. The target chapter attaches on open and the existing
landing re-lands on page 11, i.e. branch 1 ("cheap"), one visible snap. No anchor state, no flags, no
timers, no re-animation, and no step 5 insert-above compensation.

Handoff, in order:

1. Confirm the built APK is this commit; rebuild only if the tree moved (commands in `agents.md`).
2. `adb -s d89608bf install -r <apk>`, then `logcat -c`. Hand the phone to the user.
3. Post items T1, T2, T4 below verbatim, then stop and wait. One line back per item.
4. On the report: `logcat -d | grep -c FATAL` (must be 0) and `logcat -d -b crash` (must be empty).
5. Record results here with provenance, commit the docs, then follow the decision tree.

The user does, on the phone (Black Clover, 854 chapters, last read Ch. 2 page 11):

- **T1** — open the detail page, wait for the swipe hint, drag the preview panel open from a chapter
  row. Expect: it parks on Ch. 2 and then snaps **once** onto page 11, marker on the first visible row.
  No second jump, no need to touch the screen.
- **T2** — after it settles, hold still ~5 s without touching anything. Expect: Ch. 2's thumbnails fill
  in on their own and the blue marker stays on page 11 at the top. Earlier pages of Ch. 2 must not sit
  above the marker.
- **T4** — filter, sort (reverse), back to top, scroll to the end. Expect: same as the 2026-11-04
  record — functionally fine; the heavy back-to-top is step 5 work, not part of this verdict.

Decision tree:

- T1 + T2 pass, crash checks clean → row 2d verify=PASS (user, date); close the T2 bug; re-open the
  panel a few times to confirm the snap is not intermittent; then step 3/4 planning with the user.
- T1 passes but the marker still sits below Ch. 2's earlier pages → the landing fires and the
  `scrollToPositionWithOffset` on a mid-row page item is what fails. That is branch B
  (`ChapterLayoutManager` row geometry) and it needs the user's permission to reopen.
- T1 fails or thumbnails stay empty until a drag → the open pass is gated out again; report which of
  the two states the panel is in, do not add a timer or a retry.
- T4 regresses → record the exact symptom, leave the boxes unchecked, stop and report.

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
