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
5. Do not re-derive what is already written under "Why the old code was broken" below, or re-run the
   device items under "Closed" — both are settled.
6. Structural questions (`who calls anchorToCurrentPage`, what still depends on a section holder) go
   through the `codebase-memory` skill, or a subagent that returns paths plus symbol names — never a
   manual read of whole files.

## Status

| step | scope | commit | build | verify |
|------|-------|--------|-------|--------|
| 0 | drop WIP diagnostics (debug logs, `onPreviewScrollIdle`) | folded into `545aad8583` baseline | ok | PASS (launch clean, FATAL 0, crash buffer empty) |
| 1 | remove the batch window (`batchedItems` and friends) | `248f70c0ca` | ok | PASS (user, 2026-11-04) — own target: all 854 chapters present, filter/sort intact |
| 2 | remove the glide/anchor machinery | `d1e378bc91` | ok | PASS (user, 2026-11-04) — pages attach by themselves on open, no re-animation |
| 2b | remove reopen-position leftovers (`ViewportAnchor`, `savedPreviewAnchor`) | `a21864ae59` | ok | PASS (user, 2026-11-04) — open/close fast and slow, diagonal, vertical: no jump, no flicker |
| 2c | clean up callers in `MangaDetailsController` | `9e4cce9784` | ok | PASS (agent, 2026-11-04) — startup clean, no regression in the gesture paths it touched |
| 2d | gate the page attach on the panel's own top row (`bc58e53595` + `4118c1ca7d`) | `4118c1ca7d` | ok | PASS in part (user, 2026-11-05) — removed a real blocker; T2 still failed, the rest of the gate is sound |
| 2e | land from the data path: pending survives a miss, rebuild applies it | `1aef7fef87` | ok | PASS in part (user, 2026-11-05) — top row became a thumbnail row; the marker was still not on it |
| 2f | check the attach gate where the dataset is mutated (`onPagesLoaded`) | `14079bfb32` | ok | PASS (user, 2026-11-05) — page 11's row is the top row, nothing shifts afterwards |
| 3 | keep-list contract, verify nothing above was lost | — | — | — |
| 4 | disable-then-measure the priority loader chain | — | — | — |
| 5 | rebuild: full chapter list, pages inserted only on viewport entry | — | — | — |
| 6 | cut the per-frame scans behind the heavy panel scroll | — | — | PENDING — the only open item, see "Next action" |

## Decisions already made — do not reopen

- Preview target stays **the last-read page**: `refreshCurrentPage()` reads
  `presenter.allHistory.maxByOrNull { it.last_read }` and `chapter.last_page_read`. The alternative
  ("last page tapped in the preview") was considered and rejected.
- Reopen position memory is **intentionally gone**. After step 2 the panel starts at the top and
  lands directly on the target with no animation. That is the desired end state.
- Step 4 items exist because page sections exploded during layout. Disable and measure; do not delete
  on sight.
- The landing is **a loading rule, not a glide machine**: no anchor state, no `viewportMove`-style
  flags, no timers, no `postDelayed` chains, no re-animation of the open. That is why 2d/2e/2f are all
  gate and dataset-order changes.
- Branch B (row geometry in `ChapterLayoutManager`) is **closed**, decided 2026-11-05 by the on-screen
  observation that split it from branch A: the marker row was pushed down by inserts above it, which is
  branch A. It no longer needs a second look.

## Why the old code was broken (so nobody re-derives it)

The batch window hid chapters and the glide/anchor machinery re-pinned the viewport on every dataset
change; anchoring patched the resulting shifts with timing guesses, so each fix moved the bug elsewhere
(reverts `cbf2dd0564` and `d74e1bf2ef`). The "opens on the first chapters" symptom came from
`f1abb58fac` making `rebuildPageGrid()` iterate `batchedItems()`: the window seeded at index 0 and the
jump to the target went through `ensureChapterInBatch()`, which returned early while history loaded.
Steps 1 and 2 removed both mechanisms.

## Tracked bugs

- **scrolling the panel feels heavy (open).** Every drag frame runs
  `loadVisiblePageSections()` → `onPageSectionBound` → `getGlobalPositionOf(section)`, and FlexibleAdapter
  resolves that by scanning the item list — with 854+ sections that is O(children x items) per frame.
  `currentPagePosition()` and `sectionPositionOf()` scan the whole list per call too. Not measured, so
  step 6 starts by cutting the calls, not the algorithms. See "Next action".
- Closed 2026-11-05: pages stayed detached until one drag (fixed by step 2), and **T2 — the target
  drifted off screen after the open** (fixed by 2f: `onPagesLoaded` was inserting the chapters above the
  parked target without consulting the gate, and RecyclerView does not compensate an insert above the
  anchor). Write-ups: `git show 1aef7fef87:docs/preview-checkpoint.md` and
  `git show 14079bfb32:docs/preview-checkpoint.md`.

## Next action — step 6: stop doing per-frame work during a drag

The T2 chain is done, so the old device list (T1 park, T2 attach, T4 filter/sort regression) is
retired — T4 only ever existed as a regression check for the attach gate, and that gate no longer
changes. The one item the user has failed twice is the scroll weight, so this is the only open topic.

One step: make `MangaDetailsController`'s preview scroll listener and the `MangaDetailsAdapter` position
lookups stop rescanning per frame, **without changing what the user sees while dragging** — no UX
trade-off is authorised here. Read `git show 14079bfb32:docs/preview-checkpoint.md` for the gate rules
first; `loadVisiblePageSections()` must keep attaching whatever enters the viewport, it must just not
ask "where is this section?" by walking the whole adapter every time it does.

Then one item back from the device, same manga (Black Clover, 854 chapters):

- **S1** — open the panel, fling to the end, back to the top, then fling through the chapters list with
  the panel closed. Expect: the same thumbnails appear while dragging as before; the drag either feels
  lighter or it does not. Say which, and nothing else.

If S1 comes back "still heavy", the next move is to measure rather than to guess again: the remaining
suspect is thumbnail decoding in `ChapterPageHolder`, which is a different file and a different commit.

## Audit results kept for provenance

- 2026-11-04 (`9e4cce9784`, 854-chapter manga): T1 lands on Ch. 2 PASS, T3 all chapters with no gap PASS,
  T5 swipe row and back PASS, T6 open/close PASS; T2 FAIL; T4 PASS functionally but back-to-top heavy.
  FATAL 0, crash buffer empty (agent).
- 2026-11-05 (`bc58e53595`, `4118c1ca7d`, `1aef7fef87`): three retests of the same T2 bug, each one
  moving the panel closer (header → thumbnail row of pages 1-9 → page 11 on the top row). The last one,
  on `14079bfb32`, is PASS (user): page 11 on the top row with no shift, thumbnails fill in under it,
  T4 functionally intact. FATAL 0, crash buffer empty (agent).

## What is measured, not asserted

`verify: PASS` on step 0 refers to the checks named in the table only. Nothing here claims a RAM or
smoothness win; the memory claim stays unproven until step 1 and step 5 are compared on device, and the
scroll-weight fix stays unproven until S1 says the drag feels lighter.
