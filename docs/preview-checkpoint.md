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
| 2 | remove the glide/anchor machinery | `git log --grep 'smooth scroll'` | ok | PENDING — same audit; T2 FAIL is in this area |
| 2b | remove reopen-position leftovers (`ViewportAnchor`, `savedPreviewAnchor`) | `git log --grep 'viewport anchor'` | ok | PENDING — same audit. `anchorChapterId` kept: `scrollToCurrentPage` lands on a finished chapter with it, not the marker |
| 2c | clean up callers in `MangaDetailsController` | `git log --grep 'scroll-cancel'` | ok | PENDING — same audit. `onPreviewScrollIdle` was already gone in step 0 |
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

- **pages stay detached after open until one drag** — first audit (build `bd3eaa47a5`), cause from the
  code: `loadSectionPages` hands `attachItems = !isScrolling` to `onPagesLoaded`; the glide was the only
  thing keeping the recycler non-IDLE on open. Full report: `git show bbfb192a5e:docs/preview-checkpoint.md`.
  **Not reproduced on 2026-11-04**: thumbnails now attach by themselves (T2), so the symptom changed
  into the next bug. Do not close it formally until T2 passes.
- **target drifts off screen after the open (T2, seen 2026-11-04).** Pages attach on their own, but
  the chapters above the target also load their pages; the target's thumbnails end up overlapped or
  stacked under the chapters above, and the blue marker keeps moving down until it leaves the screen.
  User observation only; cause not investigated.
- **back to top feels heavy and not smooth (T4, 2026-11-04).** Functionally correct, scroll is janky.
  Not measured; may be the same dataset-change cost as the bug above or the step 4 loader chain.

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

Neither decision-tree branch fits: it is not "only T2 fails" in the attach sense, so the re-attach fix
was not applied. Rows for steps 1, 2, 2b, 2c stay PENDING and the `todo.md` boxes stay unchecked.

## Next action

Stop and ask the user which to take first; do not start on a guess:

1. T2 drift — find what changes the layout above the target after pages load (query the graph for the
   section holder / `onPagesLoaded` path; do not read whole files). Fix, own commit, re-run T1 and T2 only.
2. T4 back-to-top smoothness — measure first, then decide whether it belongs to step 4.

## What is measured, not asserted

`verify: PASS` on step 0 refers to the checks named in the table only. Nothing here claims a RAM or
smoothness win; the memory claim stays unproven until step 1 and step 5 are compared on device.
