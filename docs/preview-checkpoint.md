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
  the attach gate `bc58e53595` removed that part, and the 2026-11-05 retest still fails, now among the
  target chapter's own pages. Branch and routes: "Next action".
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

## Next action — T2 still fails after the attach gate: new diagnosis needed

Retest 2026-11-05 on `bc58e53595` (Black Clover, 854 chapters, last read Ch. 2 page 11):

| item | result |
|------|--------|
| T1 lands on Ch. 2, no animation | PASS (user, 2026-11-05) |
| T2 auto attach + marker on page 11 | FAIL (user, 2026-11-05) — Ch. 2 thumbnails are still stacked on the earlier pages of the same chapter |
| T4 filter, sort, top, end | same as 2026-11-04 (user, 2026-11-05): functionally fine, back to top still heavy |

What this changes: the remaining shift is among pages of the **target chapter itself**, so the 2026-11-04
mechanism is at best incomplete. The gate (`isAboveFirstVisibleRow`) and the layout-aware
`loadVisiblePageSections` are still worth keeping; do not revert them without a reason.

The report is ambiguous, and **one screenshot decides the branch** — ask the user for exactly one before
diagnosing, and do not chase both readings. "Tertumpuk dengan halaman sebelumnya" can mean:

- **A — pushed down by its own chapter.** Pages 1-10 of Ch. 2 sit before page 11, so attaching the chapter
  inserts rows above the target row and the marker drifts down. Every row is intact and nothing covers
  anything. RecyclerView cannot overlap children, so this is the likely one. Route: the target chapter's
  own attach must re-land the viewport on the target row — check whether `maybeScrollToCurrentPage` still
  runs after `bc58e53595` moved the open pass, and whether `pendingScrollToCurrent` survives it; if the
  re-land is correct but ugly, attach the target chapter from the target index onward instead.
- **B — two rows drawn on top of each other.** A real overlap is row geometry, not scroll position.
  Route: `ChapterLayoutManager` span/height handling and the page holder's measured size.

T4 is unchanged by this and stays step 5 work (`currentPagePosition()` and `sectionPositionOf()` scan
every item per call).

Guardrails: no anchor state, no `viewportMove`-style flags, no timers, no `postDelayed` chains, no
re-animation of the open. The fix is a loading rule, not a second glide machine.

## What is measured, not asserted

`verify: PASS` on step 0 refers to the checks named in the table only. Nothing here claims a RAM or
smoothness win; the memory claim stays unproven until step 1 and step 5 are compared on device.
