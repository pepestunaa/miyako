# Preview teardown checkpoint

Live status for the page-preview rebuild. One row per step, updated in the same commit that changes
the code. `todo.md` ("Phase A") holds the plan and the full symbol lists; this file only records how
far the work actually got. Format and rules: "Checkpoint dan Oper Antar Agent" in `agents.md`.
Full pre-compaction narrative (round-by-round landing history, both data hypotheses, the audit
tables behind the verdicts): `git show 3239c2fb84:docs/preview-checkpoint.md`.

## Resuming here with no prior context

1. **The landing bug (T1) is open and the direction is decided.** Start at "Next action" below.
2. Do not open `MangaDetailsAdapter.kt` as a warm-up and do not add another adapter viewport rule —
   seven rounds did that and every one of them failed T1.
3. Do not test a data hypothesis on the phone: both candidates were disproved from the database
   (see "Settled from the database").
4. Do not trust a `PASS` you cannot reproduce: it means someone ran the audit on the device.
   `PENDING` means code changed and compiled but nobody looked at the phone — not done, and the
   matching `todo.md` box stays unchecked.
5. Structural questions (`who calls anchorToCurrentPage`, what still depends on a section holder) go
   through the `codebase-memory` skill, or a subagent that returns paths plus symbol names.
6. This file is a fixed cost for every session. Keep it near 100 lines: fold old rounds into the
   verdict table, do not append them.

## Status

| step | scope | commit | build | verify |
|------|-------|--------|-------|--------|
| 0 | drop WIP diagnostics (debug logs, `onPreviewScrollIdle`) | folded into `545aad8583` baseline | ok | PASS (launch clean, FATAL 0, crash buffer empty) |
| 1 | remove the batch window (`batchedItems` and friends) | `248f70c0ca` | ok | PASS (user, 2026-11-04) — all 854 chapters present, filter/sort intact |
| 2 | remove the glide/anchor machinery | `d1e378bc91` | ok | PASS (user, 2026-11-04) on lazy attach; **its landing target is still FAIL, see T1** |
| 2b | remove reopen-position leftovers (`ViewportAnchor`, `savedPreviewAnchor`) | `a21864ae59` | ok | PASS (user, 2026-11-04) — open/close fast and slow, diagonal, vertical: no jump, no flicker |
| 2c | clean up callers in `MangaDetailsController` | `9e4cce9784` | ok | PASS (agent, 2026-11-04) — startup clean, no regression in the gesture paths it touched |
| 2d | gate the page attach on the panel's own top row | `bc58e53595` + `4118c1ca7d` | ok | PASS in part (user, 2026-11-05) — removed a real blocker, T2 stayed |
| 2e | land from the data path: pending survives a miss, rebuild applies it | `1aef7fef87` | ok | PASS in part (user, 2026-11-05) — the top row became a thumbnail row instead of the header |
| 2f | check the attach gate where the dataset is mutated (`onPagesLoaded`) | `14079bfb32` | ok | FAIL (user, 2026-11-05) — right on one trial, wrong on repeat tries |
| 2g | one gate rule for all three dataset mutations, target exempt | `fae961c88c` | ok | FAIL (user, 2026-11-05) — B1 unchanged, B2 and B3 pass |
| 2h | gate rows at the mutation instead of the request, cover the re-entrant attach | `c18f85ed5b` | ok | FAIL (user, 2026-11-05) — D1 FAIL, D2 PASS |
| 2i | keep the viewport through a dataset swap (`updateItems`) | `d2e536b52c` | ok | FAIL (user, 2026-11-05) — E1 FAIL, E2 PASS; the capture itself was wrong, reverted by 2j |
| 2j | a grid rebuild always re-applies the target landing (rule F2, chosen by the user) | `5b0e9e0401` | ok | FAIL (user, 2026-11-05) — G1 FAIL, G2 not run. Topic stopped here |
| 3 | target chapter enters at the last-read page, one landing after the insert | this commit | ok | PENDING — one question to the user, below |
| 4 | disable-then-measure the priority loader chain | — | — | sequenced after T1, see Decisions |
| 5 | rebuild: full chapter list, pages inserted only on viewport entry | — | — | — |
| 6 | cut the per-frame scans behind the heavy panel scroll | — | — | PENDING — never started, see "Step 6" |

## Next action � STOPPED (row 3 reverted)

Row 3's dataset-side filter (`rowsFor()` dropping pages with `index < last_page_read`) removed the pages
above the marker from the dataset entirely, so the user could not browse upward to them. The marker row
did not become the first visible row. Reverted to the pre-row-3 adapter. No second adapter rule added.

What was learned: dropping pages above the target removes the stacking but also removes content the user
scrolls back to; the compensating insert (step 5) is a prerequisite for this direction, not a follow-up.
The landing target is still lost to the insert-above-viewport problem, not row count alone. Reopen only
after step 5's compensating insert exists, or via a different mechanism.

Next: resume step 6 (panel scroll weight) or step 5 (compensating insert), at the user's call.

## Decisions already made — do not reopen

- Preview target stays **the last-read page**: `refreshCurrentPage()` reads
  `presenter.allHistory.maxByOrNull { it.last_read }` and `chapter.last_page_read`. The alternative
  ("last page tapped in the preview") was considered and rejected.
- Reopen position memory is **intentionally gone**. The panel starts at the top and lands directly on the
  target with no animation. That is the desired end state.
- **The landing is fixed from the dataset, not from the viewport** (main session, 2026-11-05, with the
  user's delegation). When the target chapter attaches, insert its pages **from the target index onward**,
  then land once. The pages above the target are not in the dataset at open, so nothing can push the
  target down; their absence while browsing upward belongs to step 5's compensating insert. Do not reopen
  the viewport-fighting approaches.
- **Rule F2 (user, 2026-11-05): in grid mode a rebuild always lands on the last-read page.** The panel's
  position is defined by the data, not by the children a dataset swap scraps. Do not reintroduce a
  preserve-the-viewport rebuild without asking. Chosen knowing the cost; G2, the test for that cost, was
  never run.
- The landing is **a loading rule, not a glide machine**: no anchor state, no `viewportMove`-style flags,
  no timers, no `postDelayed` chains, no re-animation of the open.
- **Step 4 (measure the priority chain) is out of the current work order** — not cancelled, sequenced
  after T1, because measuring a panel that lands wrong measures noise. When resumed: those items exist
  because page sections exploded during layout; disable and measure, do not delete on sight.
- Branch B (row geometry in `ChapterLayoutManager`) is **closed** — split from branch A by the 2026-11-05
  observation.
- **A single PASS is not a PASS.** 2f was recorded PASS on one trial and FAIL on repeat tries. Ask for the
  failing shape, not for another clean run.

## Tracked bugs

- **T1 — the preview does not open on the last-read page (OPEN, stopped by the user 2026-11-05).** Every
  landing verdict failed: T1, B1, D1, E1, G1. The user's own words: "BIG FAIL every time", "still stacked
  with the earlier pages of that chapter". Lazy attach and the regression items passed (T3, B2, B3, D2,
  E2), so the panel's data is fine and its position is not.
- **scrolling the panel feels heavy (open, never started).** Every drag frame runs
  `loadVisiblePageSections()` → `onPageSectionBound` → `getGlobalPositionOf(section)`, and FlexibleAdapter
  resolves that by scanning the item list — O(children x items) per frame with 854+ sections.
  `currentPagePosition()` and `sectionPositionOf()` scan the whole list per call too. See "Step 6".
- Closed: pages stayed detached until one drag (step 2); the 2026-11-04 form of **T2 — target drifted off
  screen after the open** (`onPagesLoaded` inserted chapters above the parked target without consulting the
  gate, and RecyclerView does not compensate an insert above the anchor).

## Settled from the database — do not re-run these on the phone

Read from `databases/tachiyomi.db` via `run-as eu.kanade.tachiyomi.debugYokai` on 2026-11-05 (not the UI):
manga `_id=12`, 854 chapters, last-read chapter `_id=851` "Ch. 2 - Magic Knights Entrance Exam",
**`read=0`, `bookmark=0`, `last_page_read=13`, `pages_left=13`** (26 pages; "page 11" in older entries is
this row), `history` newest points at 851.

- `!chapter.read && last_page_read > 0` **is satisfied** → `currentPage` is set, so the "parks on the
  header, no marker anywhere" branch does not fire. The `chapterId == null → scrollToPosition(0)` branch
  does not fire either: an unread chapter cannot be dropped by a read filter, and the audit shows Ch. 2
  and its rows on screen. Both hypotheses are dead; the two device observations they asked for are
  **withdrawn**.
- With a valid target (851, index 13) the panel still does not land there. What is left: the target row is
  computed and the position is lost after the landing — the pages 0..12 of Ch. 2 sit above it.
- Open-path facts, read end to end at HEAD `5b0e9e0401`: order is `setChapters` → prewarm (1600 ms
  `postDelayed`) → `beginDrag`, so **`beginDrag` runs last** and issues a fresh landing;
  `scrollToPositionWithOffset` is a pending request that survives an unlayouted/GONE panel;
  `prewarmPreviewPanel` cannot overwrite the open (`previewWarmed` once, bails on `previewOpen`) —
  **suspect zero cleared**. Separate real defect, not T1: a swipe started inside the 1600 ms window is
  yanked back once by the warm block — one frame of jank, final position still the drag's own.

## Step 6, never started: measure the panel scroll

Make the preview scroll listener and the adapter position lookups stop rescanning per frame **without
changing what the user sees while dragging** — no UX trade-off is authorised. `loadVisiblePageSections()`
must keep attaching whatever enters the viewport, it must just not ask "where is this section?" by walking
the whole adapter every time.

- **S1** — open the panel, fling to the end, back to the top, then fling through the chapter list with the
  panel closed. Expect: the same thumbnails appear while dragging as before; the drag either feels lighter
  or it does not. Say which, and nothing else.

"Feels lighter" alone is not a result — one number before, one after, same gesture, report both:

```sh
adb -s emulator-5554 shell dumpsys gfxinfo eu.kanade.tachiyomi.debugYokai reset   # then do the fling
adb -s emulator-5554 shell dumpsys gfxinfo eu.kanade.tachiyomi.debugYokai | grep -E "Janky frames|Total frames"
```

If S1 says "still heavy" and the jank percentage did not move, the per-frame scans were not the cost. The
next suspect is thumbnail decoding in `ChapterPageHolder` — a different file and a different commit.

## Audit provenance

- 2026-11-04 (`9e4cce9784`, 854-chapter manga): T1 lands on Ch. 2 PASS, T3 all chapters with no gap PASS,
  T5 swipe row and back PASS, T6 open/close PASS; T2 FAIL; T4 PASS functionally but back-to-top heavy.
  FATAL 0, crash buffer empty (agent).
- 2026-11-05 (`bc58e53595` → `5b0e9e0401`, same manga and marker): six retest rounds of the same target.
  The panel got closer each round in the agent's reading (header → pages 1-9 → page 11 on top), then the
  user's repeat tries took every verdict back to FAIL. FATAL 0 and crash buffer empty in every round
  (agent). T4 stayed functionally PASS and is retired with the gate; the scroll weight inside it is the
  separate step-6 bug.
- `C:/tmp/t1_fail.png` (2026-11-05, failing state, temporary path — take a fresh one rather than trusting
  it): viewport at item 0, Ch. 0 / Ch. 1 headers showing "Pages 189/52/55" with no thumbnail rows, Ch. 2's
  rows attached (pages 1-9 on screen). Reads as "the landing ran and a dataset swap returned the viewport
  to 0", which is what 2i/2j were written against; G1 failing means that reading is not sufficient, and
  the shot may have been taken mid-open. Hypothesis, not fact.

## What is measured, not asserted

Nothing here claims a RAM or smoothness win: unproven until steps 1 and 5 are compared on device, and the
scroll fix stays unproven until S1 reports both a felt verdict and a jank number that moved.
**The preview landing is not yet verified — that is the open fact of this file.** Last installed APK on
`d89608bf`: this commit, launch clean, FATAL 0, crash buffer empty (agent, 2026-11-05). `todo.md` Phase A stays unchecked, step 2 included.
