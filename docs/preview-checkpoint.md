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
5. Do not re-derive what is written under "Why the old code was broken", and do not re-run the device
   items under "Audit results kept for provenance" — those verdicts are recorded.
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
| 2d | gate the page attach on the panel's own top row (`bc58e53595` + `4118c1ca7d`) | `4118c1ca7d` | ok | PASS in part (user, 2026-11-05) — removed a real blocker, the bug it targeted stayed |
| 2e | land from the data path: pending survives a miss, rebuild applies it | `1aef7fef87` | ok | PASS in part (user, 2026-11-05) — the top row became a thumbnail row instead of the header |
| 2f | check the attach gate where the dataset is mutated (`onPagesLoaded`) | `14079bfb32` | ok | FAIL (user, 2026-11-05) — correct on the first trial, wrong on repeat tries: the landing is intermittent |
| 3 | keep-list contract, verify nothing above was lost | — | — | — |
| 4 | disable-then-measure the priority loader chain | — | — | — |
| 5 | rebuild: full chapter list, pages inserted only on viewport entry | — | — | — |
| 6 | cut the per-frame scans behind the heavy panel scroll | — | — | PENDING — open, but parked behind the T1 question below |

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
  gate and dataset-order changes, and it binds the fix that closes T1 too.
- Branch B (row geometry in `ChapterLayoutManager`) is **closed**, decided 2026-11-05 by the on-screen
  observation that split it from branch A: the marker row was pushed down by inserts above it, which is
  branch A.
- **A single PASS is not a PASS.** 2f was recorded PASS on one trial and is FAIL on repeat tries; the
  verdict column says so. Ask for the failing shape, not for another clean run.

## Why the old code was broken (so nobody re-derives it)

The batch window hid chapters and the glide/anchor machinery re-pinned the viewport on every dataset
change; anchoring patched the resulting shifts with timing guesses, so each fix moved the bug elsewhere
(reverts `cbf2dd0564` and `d74e1bf2ef`). The "opens on the first chapters" symptom came from
`f1abb58fac` making `rebuildPageGrid()` iterate `batchedItems()`: the window seeded at index 0 and the
jump to the target went through `ensureChapterInBatch()`, which returned early while history loaded.
Steps 1 and 2 removed both mechanisms.

## Tracked bugs

- **T1 — the last-read row is on top only sometimes (open, intermittent).** Confirmed good on one trial
  on `14079bfb32`, then wrong on repeated tries. So the marker row does get built and does get landed
  on, but not every open. The cause is something that differs between opens — see the three candidates
  and the one observation in "Next action".
- **scrolling the panel feels heavy (open).** Every drag frame runs
  `loadVisiblePageSections()` → `onPageSectionBound` → `getGlobalPositionOf(section)`, and FlexibleAdapter
  resolves that by scanning the item list — with 854+ sections that is O(children x items) per frame.
  `currentPagePosition()` and `sectionPositionOf()` scan the whole list per call too. Not measured yet.
  This is step 6 and it waits for T1.
- Closed 2026-11-05: pages stayed detached until one drag (fixed by step 2), and the 2026-11-04 form of
  **T2 — the target drifted off screen after the open** (fixed by 2f: `onPagesLoaded` inserted the
  chapters above the parked target without consulting the gate, and RecyclerView does not compensate an
  insert above the anchor). Write-ups: `git show 1aef7fef87:docs/preview-checkpoint.md` and
  `git show 14079bfb32:docs/preview-checkpoint.md`.

## Next action — T1 is intermittent: one observation decides it

Do not change code before the answer arrives. `14079bfb32` proves the landing works at least once per
session, which rules out every explanation that is true on all opens (the flag logic, the ascending sort,
the `paddingTop` line). Three candidates survive, and they look different on screen:

1. **the gate now holds the target itself back.** With the panel parked on the Ch. 2 header, a rebuild or
   a second park can leave that header ending above `paddingTop`, so `isAboveFirstVisibleRow(section)`
   calls the target "above the viewport" and its own rows never insert. Screen: the Ch. 2 **header text**
   on the top row and an empty thumbnail area until a drag.
2. **an above-viewport insert still gets through** (a fetch finishing between the park and the landing, or
   `attachCachedPages` running from a path that does not re-check the gate). Screen: page 11 starts on top
   and then moves down.
3. **the landing runs late, after the panel has settled somewhere else.** Screen: the open starts at the
   top of the list or on Ch. 2's first pages, then snaps to page 11.

One item back from the device, same manga (Black Clover, 854 chapters, last read Ch. 2 page 11), no
screenshot needed — one line each:

- **A1** — open and close the preview until it fails, then say which of these you saw: the Ch. 2 header,
  an earlier page row, page 11 then a shift down, or a snap into place a moment later.
- **A2** — does it ever fail on the *first* open right after entering the manga, or only after several
  open/close cycles?

Then, and only then: A1 = header → candidate 1, fix the gate so the target section is never treated as
above its own top row. A1 = shift → candidate 2, close the remaining insert path. A1 = snap → candidate
3, that is a dataset-order question, not a new mechanism. A1 with "first open only" or "repeat only"
tells the same story from the side. Nothing here needs a timer, a retry, or a second anchor flag.

## Audit results kept for provenance

- 2026-11-04 (`9e4cce9784`, 854-chapter manga): T1 lands on Ch. 2 PASS, T3 all chapters with no gap PASS,
  T5 swipe row and back PASS, T6 open/close PASS; T2 FAIL; T4 PASS functionally but back-to-top heavy.
  FATAL 0, crash buffer empty (agent).
- 2026-11-05 (`bc58e53595`, `4118c1ca7d`, `1aef7fef87`, `14079bfb32`): four retests of the same target,
  each one moving the panel closer (header → pages 1-9 → page 11 on the top row). FATAL 0, crash buffer
  empty (agent). T4 (filter, sort, back to top, end) stayed functionally PASS throughout and is retired
  with the gate; the scroll weight inside it is the separate step-6 bug.

## What is measured, not asserted

`verify: PASS` on step 0 refers to the checks named in the table only. Nothing here claims a RAM or
smoothness win; the memory claim stays unproven until step 1 and step 5 are compared on device, and the
scroll fix stays unproven until the user says the drag feels lighter.
