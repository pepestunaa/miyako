# Preview teardown checkpoint

Live status for the page-preview rebuild. One row per step; update the row in the same commit that
changes the code. `todo.md` ("Phase A") holds the plan and the full symbol lists — this file only
records how far the work actually got.

## Resuming here with no prior context

1. Read this file, then `git log --oneline -5`, then `todo.md` lines 53-77.
2. Do not trust a `PASS` you cannot reproduce: it means someone ran it on the device. Re-run if the
   row is older than your last change.
3. `device: PENDING` means the code changed and compiled but nobody looked at the phone. That is not
   done, and the matching `todo.md` box must stay unchecked.
4. Every commit touching `app/src/main/java/eu/kanade/tachiyomi/ui/manga/` needs the checkpoint line,
   enforced by `.githooks/commit-msg`.

## Status

| step | scope | commit | build | device |
|------|-------|--------|-------|--------|
| 0 | drop WIP diagnostics (debug logs, `onPreviewScrollIdle`) | folded into `545aad8583` baseline | ok | PASS (app launched, FATAL 0, crash buffer empty, no `YokaiPanel` in logcat) |
| 1 | remove the batch window (`batchedItems` and friends) | `248f70c0ca` | ok | PENDING — symptom not yet re-checked on device |
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

The visible symptom the user reported, "the preview opens on the first chapters instead of the one I
was reading", traces to `f1abb58fac`, which made `rebuildPageGrid()` iterate `batchedItems()` instead
of `filteredItems()`. The grid window seeded at index 0, and the jump to the target happened later via
`scrollToCurrentPage()` -> `ensureChapterInBatch()`, which returns early while history is still
loading. Step 1 removes that structure, so the symptom should be gone once verified on device.

## Known caveat on the checkpoint line

`device:` describes the state at the moment of the commit. Later steps can still regress it, which is
why the `todo.md` step-3 keep-list check and the final e2e audit exist.
