# Preview teardown checkpoint

Live status for the page-preview rebuild. One row per step, updated in the same commit that changes
the code. `todo.md` ("Phase A") holds the plan and the full symbol lists; this file only records how
far the work actually got. Format and rules: "Checkpoint dan Oper Antar Agent" in `agents.md`.

## Resuming here with no prior context

1. **This topic is stopped by the user (2026-11-05). Do not resume it without an explicit request.**
2. If it is resumed: read the "STOPPED" section below first, then `git log --oneline -8`. Do not open
   `MangaDetailsAdapter.kt` as a warm-up and do not start another adapter rule — seven rounds did that.
3. Do not trust a `PASS` you cannot reproduce: it means someone ran the audit on the device.
4. `PENDING` means the code changed and compiled but nobody looked at the phone. Not done, and the
   matching `todo.md` box stays unchecked.
5. Structural questions (`who calls anchorToCurrentPage`, what still depends on a section holder) go
   through the `codebase-memory` skill, or a subagent that returns paths plus symbol names — never a
   manual read of whole files.
6. This file is over its own ±100-line budget, and every future session pays that. Whoever next edits it
   also folds the round-by-round narrative into the verdict table; new rounds are not appended.

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
| 2i | keep the viewport through a dataset swap (`updateItems`) | `d2e536b52c` | ok | FAIL (user, 2026-11-05) — E1 FAIL, E2 PASS; reverted by 2j, the capture itself was wrong |
| 2j | a grid rebuild always re-applies the target landing (rule F2, chosen by the user) | `5b0e9e0401` | ok | FAIL (user, 2026-11-05) — G1 FAIL, G2 not run. Topic stopped here |
| 3 | keep-list contract, verify nothing above was lost | — | — | — |
| 4 | disable-then-measure the priority loader chain | — | — | — |
| 5 | rebuild: full chapter list, pages inserted only on viewport entry | — | — | — |
| 6 | cut the per-frame scans behind the heavy panel scroll | — | — | PENDING — never started, see "Step 6" |

## Decisions already made — do not reopen

- Preview target stays **the last-read page**: `refreshCurrentPage()` reads
  `presenter.allHistory.maxByOrNull { it.last_read }` and `chapter.last_page_read`. The alternative
  ("last page tapped in the preview") was considered and rejected.
- Reopen position memory is **intentionally gone**. After step 2 the panel starts at the top and
  lands directly on the target with no animation. That is the desired end state.
- Step 4 items exist because page sections exploded during layout. Disable and measure; do not delete
  on sight.
- The landing is **a loading rule, not a glide machine**: no anchor state, no `viewportMove`-style
  flags, no timers, no `postDelayed` chains, no re-animation of the open.
- Branch B (row geometry in `ChapterLayoutManager`) is **closed**, decided 2026-11-05 by the observation
  that split it from branch A.
- **A single PASS is not a PASS.** 2f was recorded PASS on one trial and FAIL on repeat tries. Ask for the
  failing shape, not for another clean run.
- **Rule F2 (user, 2026-11-05): in grid mode a rebuild always lands on the last-read page.** The panel's
  position is defined by the data, not by the children a dataset swap scraps. Do not reintroduce a
  preserve-the-viewport rebuild without asking. Chosen knowing the cost (a chapter-list update pulls the
  view back to page 11); G2, the test for that cost, was never run.

## Why the old code was broken (so nobody re-derives it)

The batch window hid chapters and the glide/anchor machinery re-pinned the viewport on every dataset
change; anchoring patched the resulting shifts with timing guesses, so each fix moved the bug elsewhere
(reverts `cbf2dd0564` and `d74e1bf2ef`). The "opens on the first chapters" symptom came from
`f1abb58fac` making `rebuildPageGrid()` iterate `batchedItems()`: the window seeded at index 0 and the
jump to the target went through `ensureChapterInBatch()`, which returned early while history loaded.
Steps 1 and 2 removed both mechanisms. **The symptom is back and was never fixed by 2d-2j.**

## Tracked bugs

- **T1 — the preview does not open on the last-read page (OPEN, stopped by the user).** Every landing test
  failed: T1, B1, D1, E1, G1. Lazy attach and the regression items passed (T3, B2, B3, D2, E2), so the
  panel's data is fine and its position is not.
- **scrolling the panel feels heavy (open, never started).** Every drag frame runs
  `loadVisiblePageSections()` → `onPageSectionBound` → `getGlobalPositionOf(section)`, and FlexibleAdapter
  resolves that by scanning the item list — O(children x items) per frame with 854+ sections.
  `currentPagePosition()` and `sectionPositionOf()` scan the whole list per call too. See "Step 6".
- Closed 2026-11-05: pages stayed detached until one drag (step 2), and the 2026-11-04 form of **T2 — the
  target drifted off screen after the open** (2f: `onPagesLoaded` inserted the chapters above the parked
  target without consulting the gate, and RecyclerView does not compensate an insert above the anchor).

## STOPPED BY THE USER — 2026-11-05

**Verdicts, in the user's own words: BIG FAIL every time.** T1 FAIL, B1 FAIL, D1 FAIL, E1 FAIL, G1 FAIL.
What did pass: T3, B2, B3, D2, E2 (lazy loading, scroll up/down, filter/sort, thumbnails loading on entry).
Nothing committed is known to be broken; the bug the session chased is simply still there. Last state:
HEAD `5b0e9e0401`, that APK installed on `d89608bf`, launch clean (FATAL 0, crash buffer empty).
**Nothing was pushed.** `todo.md` Phase A stays unchecked, step 2 included.

### Worth keeping from those rounds

2d, 2e, 2f and 2h each closed a real defect: the gate measuring the wrong coordinate space, a one-shot
landing consumed by an attach that had no marker row, an ungated insert above the anchor, and a gate
sitting in front of the page-list **request** (that one caused the bare header with nothing loading until a
drag). 2g generalised the rule to all three mutation sites. **2i was wrong and 2j reverted it**:
`updateItems` read `getChildAt(0)` while the landing's `scrollToPositionWithOffset` was still pending and
not laid out, so it captured item 0 and pinned the panel to the top of the list on purpose.

The one piece of hard evidence is `C:/tmp/t1_fail.png` (2026-11-05, failing state, temporary path — take a
fresh one rather than trusting it): viewport at **item 0**, Ch. 0 / Ch. 1 headers showing "Pages 189/52/55"
with **no thumbnail rows**, Ch. 2's rows attached (pages 1-9 on screen). It reads as "the landing ran and a
dataset swap returned the viewport to 0", which is what 2j was written against, but G1 failing means that
reading is not sufficient and the shot may have been taken mid-open. Hypothesis, not fact.

### A data rule, not a layout rule (raised by the main session, unproven)

`refreshCurrentPage()` sets `currentPage` only when `!chapter.read && chapter.last_page_read > 0`. When the
last-read chapter is flagged **read**, `currentPage` stays null, so `scrollToCurrentPage()` falls back to
`anchorChapterId`, parks on that chapter's **header**, and every page of the chapter then attaches below
the header — page 11 sits several rows down behind pages 1-10, and `isCurrentPage` is false for every row,
so **no marker is drawn anywhere**. That is branch A with no viewport drift at all: nothing is inserted
above the viewport, the target simply was never a page.

- Fits the record: the 2026-11-04 audit already wrote "no blue marker", and the 2026-11-05 answer was
  "stacked with the earlier pages of that chapter".
- Does not fit the agent's `chapterId == null` path: that branch calls `scrollToPosition(0)` and cannot
  park on Ch. 2's header, yet T1 passed. The two are different bugs; do not merge them. Both may fire at
  different moments depending on history arrival and the chapter filters.
- Counter-evidence, so nobody over-reads this: the last read is page **11 of 26**, i.e. mid-chapter, which
  normally means `read == false` and therefore `currentPage` should be set. The "no blue marker" line from
  2026-11-04 is also fully explained by the rows not being attached at all. This branch is only reachable if
  Ch. 2 was finished or marked read afterwards — the toggle check decides, the reasoning does not.
- Settled without reading more code, by one check: in the chapter list, see whether Ch. 2 is marked read,
  toggle it unread, and reopen the preview. Landing on page 11 with a blue marker after the toggle means
  the whole landing problem is this data rule.
- If it is the rule, it contradicts the recorded decision in "Decisions already made" (preview target =
  the last-read page). Then the fix is a decision about what "last read" means for a finished chapter -
  not an eighth rule in the adapter.

### If it is resumed: do not start in the adapter

Seven rounds happened in `MangaDetailsAdapter`. The thing that decides where the viewport ends up is the
open path in `MangaDetailsController`, which was never read end to end:

- `prewarmPreviewPanel` sets the panel `VISIBLE` with `translationX = width`, calls
  `adapter.scrollToCurrentPage()`, then `panel.post { loadVisiblePageSections() }`, then hides it again
  after `PANEL_WARM_SETTLE_MS`. **The landing runs on an off-screen panel that has not been laid out yet**,
  so the layout pass that consumes the pending scroll is not the one the user eventually sees.
- `beginDrag` calls `previewAdapter?.scrollToCurrentPage()` on the real open; `setupPreviewPanel`'s scroll
  listener calls `loadVisiblePageSections()` on DRAGGING (every frame) and on IDLE.
- `setChapters` is called from the chapter/history streams (three sites) and each call rebuilds and, since
  2j, re-lands.

Suspect zero was `prewarmPreviewPanel` — it lands the panel off-screen and hides it again after
`PANEL_WARM_SETTLE_MS`, and its comment still says the grid is "laid out and **anchored**". Tested below
and cleared. Full round-by-round accounting of the seven landing commits (`2d`-`2j`, including the wrong
one, `2i`, reverted by `2j`) and the `C:/tmp/t1_fail.png` evidence: `git show 2089d99c9a:docs/preview-checkpoint.md`.

### Answered 2026-11-05 (diagnosis only, reading HEAD `5b0e9e0401`): the ordering is not the cause

- **Order in a normal open:** `setChapters` (inside `updateChapters`, which calls prewarm right after) →
  prewarm (`view.postDelayed`, 1600 ms) → `beginDrag` (the user's gesture). **`beginDrag` runs last.**
  `setChapters` is last only when `updateChapters()` fires again after the open (toggle-read, filter,
  refresh, `onAttach` after the reader); its rebuild re-lands, which is F2 by design.
- **The scroll is pending, and pending is fine.** Both `prewarmPreviewPanel` and `beginDrag` flip the
  panel to `VISIBLE` and then call `scrollToCurrentPage()` in the same synchronous block, so no traversal
  runs in between. `scrollToRowAtTop` uses `scrollToPositionWithOffset`, whose contract is "remember the
  row, apply it at the next `onLayoutChildren`". A GONE child is never laid out, so a pending scroll even
  survives the closed panel. The warm pass consumes its own landing; `beginDrag` then issues a fresh one.
- **Prewarm cannot overwrite the open:** `previewWarmed` is set once, and the delayed block bails on
  `previewOpen`. It never runs after the panel is open. **Suspect zero is cleared.**
- One real but separate defect: a swipe started inside the 1600 ms window sets neither `previewOpen` nor
  `swipeAnimating` until UP, so the warm block still fires and yanks `translationX` back to `width` and
  re-lands mid-drag. One frame of jank; the final position is still the drag's own landing.
- **What the ordering proves instead:** the last landing written is `beginDrag`'s, and it picks its row
  from **data** (`currentPagePosition()` walks `getItem`), never from geometry. So the bug is the value it
  is handed, not when it runs. Exactly one branch of `scrollToCurrentPage` writes 0:
  `chapterId == null && presenter.historyLoaded → scrollToPosition(0)`. `chapterId` is null iff
  `refreshCurrentPage()` cannot find the last-read chapter in `presenter.chapters` — the **filtered**
  list, not the raw one — or `allHistory` is empty and `getNextUnreadChapter()` is null. That same lookup
  sets `currentPage`, so on those inputs **no page row in the dataset carries `isCurrentPage`.**

**The one device observation that settles it** (failing state, panel at item 0): flick down to Ch. 2
page 11. If that row shows the last-read marker, the target was computed and its position was lost after
the landing — back to the adapter/gate path. If no row in the panel is marked at all,
`scrollToCurrentPage` took the `chapterId == null` branch and went to 0 by design, and the fault is
`refreshCurrentPage` resolving against the filtered chapter list.

## Step 6, never started: measure the panel scroll

One step: make `MangaDetailsController`'s preview scroll listener and the `MangaDetailsAdapter` position
lookups stop rescanning per frame, **without changing what the user sees while dragging** — no UX
trade-off is authorised here. `loadVisiblePageSections()` must keep attaching whatever enters the
viewport, it must just not ask "where is this section?" by walking the whole adapter every time.

- **S1** — open the panel, fling to the end, back to the top, then fling through the chapters list with
  the panel closed. Expect: the same thumbnails appear while dragging as before; the drag either feels
  lighter or it does not. Say which, and nothing else.

"Feels lighter" alone is not a result, so take one number before and one after, same gesture, and report
both — the output is two lines:

```sh
adb -s d89608bf shell dumpsys gfxinfo eu.kanade.tachiyomi.debugYokai reset   # then do the fling
adb -s d89608bf shell dumpsys gfxinfo eu.kanade.tachiyomi.debugYokai | grep -E "Janky frames|Total frames"
```

If S1 comes back "still heavy" and the jank percentage did not move, the per-frame scans were not the
cost. The next move is then to measure rather than guess again: the remaining suspect is thumbnail
decoding in `ChapterPageHolder`, which is a different file and a different commit.

## Audit results kept for provenance

- 2026-11-04 (`9e4cce9784`, 854-chapter manga): T1 lands on Ch. 2 PASS, T3 all chapters with no gap PASS,
  T5 swipe row and back PASS, T6 open/close PASS; T2 FAIL; T4 PASS functionally but back-to-top heavy.
  FATAL 0, crash buffer empty (agent).
- 2026-11-05 (`bc58e53595` → `5b0e9e0401`, same manga and marker): six retest rounds of the same target.
  The panel got closer each round in the agent's reading (header → pages 1-9 → page 11 on top), then the
  user's repeat tries took every one of those verdicts back to FAIL. FATAL 0 and the crash buffer empty in
  every round (agent). T4 (filter, sort, back to top, end) stayed functionally PASS and is retired with the
  gate; the scroll weight inside it is the separate step-6 bug.

## What is measured, not asserted

`verify: PASS` on step 0 refers to the checks named in the table only. Nothing here claims a RAM or
smoothness win; the memory claim stays unproven until step 1 and step 5 are compared on device, the scroll
fix stays unproven until S1 reports both a felt verdict and a jank number that moved, and **the preview
landing is not working — that is the open fact of this file.**
