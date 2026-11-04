# Preview teardown checkpoint

Live status for the page-preview rebuild. One row per step, updated in the same commit that changes
the code. `todo.md` ("Phase A") holds the plan and the full symbol lists; this file only records how
far the work actually got. Format and rules: "Checkpoint dan Oper Antar Agent" in `agents.md`.

## Resuming here with no prior context

1. Read `agents.md`, then this file, then `git log --oneline -5`, then `todo.md` lines 53-77.
2. Do not trust a `PASS` you cannot reproduce: it means someone ran the audit on the device.
3. `PENDING` means the code changed and compiled but nobody looked at the phone. Not done, and the
   matching `todo.md` box stays unchecked.
4. Do not re-derive what is already written under "Why the old code was broken" below.
5. Structural questions during step 2 and 5 (`who calls anchorToCurrentPage`, what still depends on
   `ViewportAnchor`) go through the `codebase-memory` skill before grepping by hand.

## Status

| step | scope | commit | build | verify |
|------|-------|--------|-------|--------|
| 0 | drop WIP diagnostics (debug logs, `onPreviewScrollIdle`) | folded into `545aad8583` baseline | ok | PASS (launch clean, FATAL 0, crash buffer empty) |
| 1 | remove the batch window (`batchedItems` and friends) | `248f70c0ca` | ok | PENDING — the "opens on the first chapters" symptom has not been re-checked on device |
| 2 | remove the glide/anchor machinery | — | — | — |
| 2b | remove reopen-position leftovers (`ViewportAnchor`, `savedPreviewAnchor`) | — | — | — |
| 2c | clean up callers in `MangaDetailsController` | — | — | — |
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

## What is measured, not asserted

`verify: PASS` on step 0 and 1 refers to the checks named in the table only. Nothing here yet claims a
RAM or smoothness win; the memory claim in particular stays unproven until step 1 and step 5 are
compared on device.
