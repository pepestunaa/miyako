package eu.kanade.tachiyomi.ui.manga

import android.os.SystemClock
import android.view.ActionMode
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.TextView
import androidx.core.view.doOnNextLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.icerock.moko.resources.StringResource
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import yokai.i18n.MR
import yokai.util.lang.getString
import dev.icerock.moko.resources.compose.stringResource
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.manga.chapter.BaseChapterAdapter
import eu.kanade.tachiyomi.ui.manga.chapter.ChapterItem
import eu.kanade.tachiyomi.ui.manga.chapter.ChapterPageHolder
import eu.kanade.tachiyomi.ui.manga.chapter.ChapterPageItem
import eu.kanade.tachiyomi.ui.manga.chapter.ChapterPagesSectionItem
import eu.kanade.tachiyomi.util.chapter.ChapterUtil
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.isLTR
import eu.kanade.tachiyomi.util.system.withUIContext
import kotlinx.coroutines.launch
import uy.kohesive.injekt.injectLazy
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

private const val GLIDE_MAX_DELAY_MS = 1000L
private const val INITIAL_BATCH_SIZE = 60
private const val BATCH_EXTEND_STEP = 60
private const val BATCH_EXTEND_MARGIN = 20

class MangaDetailsAdapter(
    val controller: MangaDetailsController,
) : BaseChapterAdapter<IFlexible<*>>(controller) {

    val preferences: PreferencesHelper by injectLazy()

    val hasShownSwipeTut
        get() = preferences.shownChapterSwipeTutorial()

    var items: List<ChapterItem> = emptyList()
        private set

    var pageGridMode = false
        private set

    var suppressPageLoads = false

    private val pageSections = HashMap<Long, ChapterPagesSectionItem>()
    private val chapterPages = HashMap<Long, List<Page>>()
    private var currentPage: Pair<Long, Int>? = null
    private var anchorChapterId: Long? = null
    private var pendingScrollToCurrent = false
    private var priorityChapterId: Long? = null
    private var viewportMovePending = false
    private var viewportAnchor: ViewportAnchor? = null
    private var batchSize = INITIAL_BATCH_SIZE

    private val batchScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            extendBatchIfNeeded()
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        recyclerView.addOnScrollListener(batchScrollListener)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        recyclerView.removeOnScrollListener(batchScrollListener)
        super.onDetachedFromRecyclerView(recyclerView)
    }

    /**
     * Submit chapters in batches so opening a manga with hundreds of rows doesn't hand the
     * whole dataset to RecyclerView at once. Data stays complete in the presenter; the batch
     * only controls what the list shows, and grows as the user reaches the end.
     */
    private fun batchedItems(): List<ChapterItem> {
        val all = filteredItems()
        return if (all.size <= batchSize) all else all.take(batchSize)
    }

    private fun extendBatchIfNeeded() {
        val all = filteredItems()
        if (batchSize >= all.size) return
        val lm = recyclerView?.layoutManager as? LinearLayoutManager ?: return
        val lastVisible = lm.findLastVisibleItemPosition()
        if (lastVisible == RecyclerView.NO_POSITION) return
        if (lastVisible < currentItems.size - BATCH_EXTEND_MARGIN) return
        batchSize = (batchSize + BATCH_EXTEND_STEP).coerceAtMost(all.size)
        if (pageGridMode) rebuildPageGrid() else performFilter()
    }

    private fun ensureChapterInBatch(chapterId: Long) {
        val all = filteredItems()
        val index = all.indexOfFirst { it.chapter.id == chapterId }
        if (index == -1 || index < batchSize) return
        batchSize = (index + 1).coerceAtMost(all.size)
        if (pageGridMode) rebuildPageGrid() else performFilter()
    }

    val delegate: MangaDetailsInterface = controller
    val presenter = controller.presenter

    val decimalFormat = DecimalFormat(
        "#.###",
        DecimalFormatSymbols()
            .apply { decimalSeparator = '.' },
    )

    fun setChapters(items: List<ChapterItem>?) {
        this.items = items ?: emptyList()
        if (pageGridMode) {
            rebuildPageGrid()
            // The panel may have opened before history loaded; re-anchor now that it has.
            if (pendingScrollToCurrent && presenter.historyLoaded) {
                scrollToCurrentPage()
            }
        } else {
            performFilter()
        }
    }

    fun setPageGridMode(enabled: Boolean) {
        if (pageGridMode == enabled) return
        pageGridMode = enabled
        rebuildPageGrid()
    }

    fun rebuildPageGrid() {
        if (!pageGridMode) {
            performFilter()
            return
        }
        refreshCurrentPage()
        val gridItems = mutableListOf<IFlexible<*>>()
        for (chapterItem in batchedItems()) {
            val chapter = chapterItem.chapter
            val chapterId = chapter.id ?: continue
            val section = pageSections.getOrPut(chapterId) { ChapterPagesSectionItem(chapter) }
            section.chapter = chapter
            section.state = when {
                chapterPages.containsKey(chapterId) -> ChapterPagesSectionItem.State.Loaded
                else -> section.state
            }
            section.pageCount = chapterPages[chapterId]?.size ?: 0
            section.pagesAttached = chapterPages.containsKey(chapterId)
            gridItems.add(section)
            chapterPages[chapterId]?.forEach { page ->
                gridItems.add(ChapterPageItem(chapter, page, isCurrentPage(chapter, page)))
            }
        }
        withViewportAnchor { updateItems(gridItems) }
    }

    /**
     * RecyclerView doesn't compensate the viewport when rows appear above it, and a dataset swap
     * resets the scroll position entirely. Both happen while the preview fills in: batches grow,
     * and every chapter that comes into range attaches its thumbnails. Without a landing target the
     * row at the top of the grid is what the reader is looking at, so that gets pinned; with one,
     * the target holds the top no matter how much content arrives around it.
     */
    private fun withViewportAnchor(block: () -> Unit) {
        val recycler = recyclerView
        val layoutManager = recycler?.layoutManager as? LinearLayoutManager
        if (recycler == null || layoutManager == null) {
            block()
            return
        }
        // A jump that hasn't laid out yet, or a scroll still running, owns the viewport: pinning
        // over it would cancel the park or the glide. The settle pass picks the target back up
        // once the list comes to rest.
        if (viewportMovePending || recycler.scrollState != RecyclerView.SCROLL_STATE_IDLE) {
            block()
            return
        }
        val anchor = viewportAnchor ?: captureTopRow(recycler)
        block()
        if (anchor == null) return
        val position = getGlobalPositionOf(anchor.item)
        if (position == RecyclerView.NO_POSITION) return
        layoutManager.scrollToPositionWithOffset(position, anchor.offset)
    }

    /** The row currently at the top of the grid, or null when positions are too stale to trust. */
    private fun captureTopRow(recycler: RecyclerView): ViewportAnchor? {
        val firstChild = recycler.getChildAt(0) ?: return null
        val firstPosition = recycler.getChildAdapterPosition(firstChild)
        if (firstPosition !in 0 until itemCount) return null
        val firstItem = getItem(firstPosition) ?: return null
        return ViewportAnchor(firstItem, firstChild.top - recycler.paddingTop)
    }

    private data class ViewportAnchor(val item: IFlexible<*>, val offset: Int)

    private fun updateItems(items: List<IFlexible<*>>) {
        // FlexibleAdapter skips updateDataSet while a filter is active; filtering is handled by filteredItems().
        val filter = getFilter(String::class.java)
        if (!filter.isNullOrEmpty()) setFilter("")
        updateDataSet(items, false)
        if (!filter.isNullOrEmpty()) setFilter(filter)
    }

    fun pageSectionShouldLoad(section: ChapterPagesSectionItem): Boolean {
        val chapterId = section.chapter.id ?: return false
        return section.state != ChapterPagesSectionItem.State.Loading &&
            !chapterPages.containsKey(chapterId)
    }

    fun markPageSectionLoading(section: ChapterPagesSectionItem) {
        section.state = ChapterPagesSectionItem.State.Loading
        section.pageCount = 0
        section.refreshBoundHolder()
    }

    fun resetPageSection(section: ChapterPagesSectionItem) {
        section.state = ChapterPagesSectionItem.State.Idle
    }

    fun onPageSectionBound(section: ChapterPagesSectionItem) {
        if (!pageGridMode || suppressPageLoads) return
        // Attaching pages mutates the layout, which shifts the viewport mid-scroll.
        if (isScrolling) return
        attachCachedPages(section)
        if (!pageSectionShouldLoad(section)) return
        // Hold back other chapters while the last-read one is still fetching: the panel anchors
        // there, and its thumbnails are the first the user actually sees.
        val priority = priorityChapterId
        if (priority != null && section.chapter.id != priority) return
        markPageSectionLoading(section)
        loadSectionPages(section)
    }

    fun retryPageSection(section: ChapterPagesSectionItem) {
        if (!pageGridMode) return
        resetPageSection(section)
        markPageSectionLoading(section)
        loadSectionPages(section)
    }

    fun loadVisiblePageSections() {
        if (!pageGridMode) return
        val recycler = recyclerView ?: return
        settleAnchorToTop(recycler)
        for (i in 0 until recycler.childCount) {
            val child = recycler.getChildAt(i) ?: continue
            (recycler.getChildViewHolder(child) as? ChapterPageHolder)?.loadImageIfNeeded()
            val position = recycler.getChildAdapterPosition(child)
            if (position == RecyclerView.NO_POSITION) continue
            (getItem(position) as? ChapterPagesSectionItem)?.let { onPageSectionBound(it) }
        }
    }

    fun scrollToCurrentPage() {
        if (!pageGridMode) return
        val recycler = recyclerView ?: return
        refreshCurrentPage()
        // A chapter without a page marker still sits somewhere in the grid: a finished chapter,
        // or one left at page 0, has to open on itself instead of at the very top of the list.
        val chapterId = currentPage?.first ?: anchorChapterId
        if (chapterId == null) {
            // History may still be loading: keep waiting so a later refresh can anchor.
            priorityChapterId = null
            pendingScrollToCurrent = !presenter.historyLoaded
            viewportAnchor = null
            if (presenter.historyLoaded) {
                markViewportMove(recycler)
                recycler.scrollToPosition(0)
            }
            return
        }
        // Fetch the last-read chapter's page list before any other section gets a chance.
        priorityChapterId = if (chapterPages.containsKey(chapterId)) null else chapterId
        // The anchor target may sit past the submitted batch; grow the batch to include it.
        ensureChapterInBatch(chapterId)
        val pagePosition = currentPagePosition()
        if (pagePosition != null) {
            pendingScrollToCurrent = false
            anchorToCurrentPage(getItem(pagePosition) as? ChapterPageItem)
            return
        }
        // The current chapter's thumbnails may not be loaded yet: park on its section and
        // jump to the page itself once they attach. Without a page marker the section is
        // already the destination, so there is nothing left to wait for.
        pendingScrollToCurrent = currentPage != null
        val sectionPosition = sectionPositionOf(chapterId)
        val section = sectionPosition?.let { getItem(it) as? ChapterPagesSectionItem }
        viewportAnchor = section?.let { ViewportAnchor(it, 0) }
        if (sectionPosition == null || section == null) {
            priorityChapterId = null
            return
        }
        markViewportMove(recycler)
        recycler.scrollToPosition(sectionPosition)
        onPageSectionBound(section)
    }

    fun cancelPendingScroll() {
        pendingScrollToCurrent = false
        // The reader took over the grid: their own scroll position is what late loads must keep.
        viewportAnchor = null
    }

    private fun anchorToCurrentPage(item: ChapterPageItem?) {
        if (item == null) return
        val recycler = recyclerView ?: return
        // Nothing may pin the grid while the target is being resolved: a load that lands in this
        // window would be read as the anchor and cut the incoming glide short. The previous claim
        // goes with it, so a stale row can't be re-pinned over the new one.
        viewportAnchor = null
        viewportMovePending = true
        // Resolve the position after pending insertions are laid out: sections above the page
        // attach their thumbnails later and shift every index below them.
        recycler.post {
            try {
                anchorOnto(item, recycler)
            } finally {
                viewportMovePending = false
            }
        }
    }

    private fun anchorOnto(item: ChapterPageItem, recycler: RecyclerView) {
        val position = getGlobalPositionOf(item)
        if (position == RecyclerView.NO_POSITION) return
        val layoutManager = recycler.layoutManager as? LinearLayoutManager
        if (layoutManager == null) {
            claimAnchor(item)
            recycler.scrollToPosition(position)
            return
        }
        // When the page is already laid out nearby, glide straight from the current
        // position. Parking first would show a jump right as the panel opens.
        val targetTop = recycler.findViewHolderForAdapterPosition(position)?.itemView?.top
        if (targetTop != null) {
            val dy = targetTop - recycler.paddingTop
            val maxGlide = (recycler.height - recycler.paddingTop - recycler.paddingBottom) * 2
            if (dy in 0..maxGlide) {
                claimAnchor(item)
                if (dy >= 16.dpToPx) glideTo(recycler, dy)
                return
            }
        }
        val lead = (position - pageScrollLead()).coerceAtLeast(0)
        if (lead >= position) {
            claimAnchor(item)
            layoutManager.scrollToPositionWithOffset(position, 0)
            return
        }
        // Park one grid row above, then glide the page itself into place. smoothScrollToPosition
        // only makes an item visible, it doesn't align it to the top like we need.
        markViewportMove(recycler)
        layoutManager.scrollToPositionWithOffset(lead, 0)
        val parkedAt = SystemClock.uptimeMillis()
        // Measure only once the parked layout has settled; before that the target's top still
        // reflects the old scroll position and the glide would fall short.
        recycler.doOnNextLayout {
            val target = getGlobalPositionOf(item)
            if (target == RecyclerView.NO_POSITION) return@doOnNextLayout
            val top = recycler.findViewHolderForAdapterPosition(target)?.itemView?.top
            if (top == null) {
                // Content above the park grew while it was pending, so the page is off screen
                // again: land on it directly rather than parking one more time.
                claimAnchor(item)
                layoutManager.scrollToPositionWithOffset(target, 0)
                return@doOnNextLayout
            }
            val dy = top - recycler.paddingTop
            claimAnchor(item)
            when {
                // Already at (or above) the top: snap silently, a small correction scroll
                // reads as a glitch.
                dy < 16.dpToPx -> layoutManager.scrollToPositionWithOffset(target, 0)
                // The layout arrived long after the park, so the panel has already settled;
                // animating at this point would look like a stray scroll.
                SystemClock.uptimeMillis() - parkedAt > GLIDE_MAX_DELAY_MS ->
                    layoutManager.scrollToPositionWithOffset(target, 0)
                else -> glideTo(recycler, dy)
            }
        }
    }

    /** Hands the grid top to [item]: every later load pins it here instead of wherever it drifts. */
    private fun claimAnchor(item: ChapterPageItem) {
        viewportAnchor = ViewportAnchor(item, 0)
    }

    private fun glideTo(recycler: RecyclerView, dy: Int) {
        markViewportMove(recycler)
        // Standard easing starts and ends at rest, so the glide doesn't read as a dart.
        val duration = (dy * 1.4f).coerceIn(320f, 700f).toInt()
        recycler.smoothScrollBy(0, dy, PathInterpolator(0.4f, 0f, 0.2f, 1f), duration)
    }

    private fun pageScrollLead(): Int =
        (recyclerView?.layoutManager as? GridLayoutManager)?.spanCount ?: 3

    /**
     * Content that attached above, below or around the target can still shift it while the panel
     * is filling in. Every time the grid comes to rest, walk the target back to where it belongs.
     */
    private fun settleAnchorToTop(recycler: RecyclerView) {
        if (recycler.scrollState != RecyclerView.SCROLL_STATE_IDLE) return
        val anchor = viewportAnchor ?: return
        val layoutManager = recycler.layoutManager as? LinearLayoutManager ?: return
        val position = getGlobalPositionOf(anchor.item)
        if (position == RecyclerView.NO_POSITION) return
        val top = recycler.findViewHolderForAdapterPosition(position)?.itemView?.top
        if (top == null) {
            // Out of reach of any animation worth running: land on it directly.
            layoutManager.scrollToPositionWithOffset(position, anchor.offset)
            return
        }
        val dy = top - recycler.paddingTop - anchor.offset
        when {
            dy > recycler.height -> layoutManager.scrollToPositionWithOffset(position, anchor.offset)
            dy in 16.dpToPx..recycler.height -> glideTo(recycler, dy)
        }
    }

    /**
     * A requested jump only takes effect on the next layout, and until then the viewport still
     * reflects the old one. Pinning during that window would overwrite the jump, so mutations
     * hold off until the layout that consumes it has run.
     */
    private fun markViewportMove(recycler: RecyclerView) {
        viewportMovePending = true
        recycler.doOnNextLayout { viewportMovePending = false }
        // Rows added above shift the requested index before that layout runs, and a scroll asked
        // for inside it is deferred. Re-resolve the target by identity once the frame is over.
        recycler.post { reassertViewportAnchor(recycler) }
    }

    private fun reassertViewportAnchor(recycler: RecyclerView) {
        val anchor = viewportAnchor ?: return
        val layoutManager = recycler.layoutManager as? LinearLayoutManager ?: return
        val position = getGlobalPositionOf(anchor.item)
        if (position == RecyclerView.NO_POSITION) return
        val top = recycler.findViewHolderForAdapterPosition(position)?.itemView?.top
        if (top == null || top - recycler.paddingTop != anchor.offset) {
            layoutManager.scrollToPositionWithOffset(position, anchor.offset)
        }
    }

    private fun currentPagePosition(): Int? {
        for (i in 0 until itemCount) {
            val item = getItem(i) as? ChapterPageItem ?: continue
            if (item.isCurrentPage) return i
        }
        return null
    }

    private fun sectionPositionOf(chapterId: Long): Int? {
        for (i in 0 until itemCount) {
            val item = getItem(i) as? ChapterPagesSectionItem ?: continue
            if (item.chapter.id == chapterId) return i
        }
        return null
    }

    private fun maybeScrollToCurrentPage(chapterId: Long) {
        if (!pendingScrollToCurrent || currentPage?.first != chapterId) return
        pendingScrollToCurrent = false
        val position = currentPagePosition() ?: return
        anchorToCurrentPage(getItem(position) as? ChapterPageItem)
    }

    private fun loadSectionPages(section: ChapterPagesSectionItem) {
        presenter.presenterScope.launch {
            val pages = presenter.loadPageList(section.chapter)
            withUIContext {
                onPagesLoaded(section, pages, attachItems = !isScrolling)
            }
        }
    }

    val isScrolling: Boolean
        get() = recyclerView?.scrollState != RecyclerView.SCROLL_STATE_IDLE

    val isFlinging: Boolean
        get() = recyclerView?.scrollState == RecyclerView.SCROLL_STATE_SETTLING

    fun onPagesLoaded(
        section: ChapterPagesSectionItem,
        pages: List<Page>?,
        attachItems: Boolean = true,
    ) {
        val chapterId = section.chapter.id ?: return
        if (pages == null) {
            if (chapterId == priorityChapterId) {
                priorityChapterId = null
                loadVisiblePageSections()
            }
            section.state = ChapterPagesSectionItem.State.Error
            section.refreshBoundHolder()
            return
        }

        refreshCurrentPage()
        section.state = ChapterPagesSectionItem.State.Loaded
        section.pageCount = pages.size
        chapterPages[chapterId] = pages

        val sectionPosition = getGlobalPositionOf(section)
        when {
            sectionPosition == -1 || !pageGridMode -> Unit
            pages.isEmpty() -> section.pagesAttached = true
            attachItems -> {
                insertPageItems(section, sectionPosition, pages)
                section.pagesAttached = true
                maybeScrollToCurrentPage(chapterId)
            }
            // Held back while scrolling; attachCachedPages() inserts them when the scroll settles.
            else -> section.pagesAttached = false
        }
        section.refreshBoundHolder()
        if (chapterId == priorityChapterId) {
            priorityChapterId = null
            loadVisiblePageSections()
        }
    }

    fun attachCachedPages(section: ChapterPagesSectionItem) {
        if (section.pagesAttached || !pageGridMode) return
        val chapterId = section.chapter.id ?: return
        val pages = chapterPages[chapterId] ?: return
        if (pages.isEmpty()) {
            section.pagesAttached = true
            return
        }
        val recycler = recyclerView
        if (recycler != null && recycler.isComputingLayout) {
            recycler.post {
                if (!isScrolling) attachCachedPages(section)
            }
            return
        }
        val sectionPosition = getGlobalPositionOf(section)
        if (sectionPosition != -1) {
            insertPageItems(section, sectionPosition, pages)
            section.pagesAttached = true
            maybeScrollToCurrentPage(chapterId)
        }
    }

    private fun insertPageItems(
        section: ChapterPagesSectionItem,
        sectionPosition: Int,
        pages: List<Page>,
    ) {
        val chapter = section.chapter
        val pageItems = pages.map {
            ChapterPageItem(chapter, it, isCurrentPage(chapter, it)) as IFlexible<*>
        }
        val recycler = recyclerView
        if (recycler == null) {
            addItems(sectionPosition + 1, pageItems)
            return
        }

        val animator = recycler.itemAnimator
        recycler.itemAnimator = null
        try {
            withViewportAnchor { addItems(sectionPosition + 1, pageItems) }
        } finally {
            recycler.doOnNextLayout {
                recycler.itemAnimator = animator
            }
        }
    }

    fun findPositionForChapter(chapterId: Long): Int? {
        for (position in 0 until itemCount) {
            val chapter = when (val item = getItem(position)) {
                is ChapterItem -> item.chapter
                is ChapterPagesSectionItem -> item.chapter
                else -> null
            }
            if (chapter?.id == chapterId) return position
        }
        return null
    }

    private fun refreshCurrentPage() {
        // Follow the most recently read chapter instead of the next unread one: reading ahead
        // must not leave the marker on an older chapter that was never finished.
        val lastRead = presenter.allHistory.maxByOrNull { it.last_read }
        val chapter = when {
            lastRead != null ->
                presenter.chapters.firstOrNull { it.chapter.id == lastRead.chapter_id }?.chapter
            // Before history arrives the next unread chapter may be an old partial one, so
            // don't guess; fall back only when history is known to be empty.
            presenter.historyLoaded -> presenter.getNextUnreadChapter()?.chapter
            else -> null
        }
        val chapterId = chapter?.id
        anchorChapterId = chapterId
        currentPage = if (chapterId != null && !chapter.read && chapter.last_page_read > 0) {
            chapterId to chapter.last_page_read
        } else {
            null
        }
    }

    private fun isCurrentPage(chapter: Chapter, page: Page): Boolean {
        val current = currentPage ?: return false
        return chapter.id == current.first && page.index == current.second
    }

    private fun filteredItems(): List<ChapterItem> {
        val s = getFilter(String::class.java)
        return if (s.isNullOrBlank()) {
            items
        } else {
            items.filter { it.name.contains(s, true) }
        }
    }

    fun indexOf(item: ChapterItem): Int {
        return items.indexOf(item)
    }

    fun indexOf(chapterId: Long): Int {
        return currentItems.indexOfFirst { it is ChapterItem && it.id == chapterId }
    }

    fun performFilter() {
        if (pageGridMode) {
            rebuildPageGrid()
            return
        }
        updateItems(batchedItems())
    }

    override fun onItemSwiped(position: Int, direction: Int) {
        super.onItemSwiped(position, direction)
        when (direction) {
            ItemTouchHelper.RIGHT -> if (recyclerView.resources.isLTR) {
                controller.bookmarkChapter(position)
            } else {
                controller.toggleReadChapter(position)
            }
            ItemTouchHelper.LEFT -> if (recyclerView.resources.isLTR) {
                controller.toggleReadChapter(position)
            } else {
                controller.bookmarkChapter(position)
            }
        }
    }

    override fun onCreateBubbleText(position: Int): String {
        val chapter = when (val item = getItem(position)) {
            is ChapterItem -> item.chapter
            is ChapterPagesSectionItem -> item.chapter
            else -> return recyclerView.context.getString(MR.strings.top)
        }
        return when (val scrollType = presenter.scrollType) {
            MangaDetailsPresenter.MULTIPLE_VOLUMES, MangaDetailsPresenter.MULTIPLE_SEASONS -> {
                val volume = ChapterUtil.getGroupNumber(chapter)
                if (volume != null) {
                    recyclerView.context.getString(
                        if (scrollType == MangaDetailsPresenter.MULTIPLE_SEASONS) {
                            MR.strings.season_
                        } else {
                            MR.strings.volume_
                        },
                        volume,
                    )
                } else {
                    getChapterName(chapter)
                }
            }
            MangaDetailsPresenter.TENS_OF_CHAPTERS -> recyclerView.context.getString(
                MR.strings.chapters_,
                get10sRange(chapter.chapter_number),
            )
            else -> getChapterName(chapter)
        }
    }

    private fun getChapterName(chapter: Chapter): String {
        return if (chapter.chapter_number > 0) {
            recyclerView.context.getString(
                MR.strings.chapter_,
                decimalFormat.format(chapter.chapter_number),
            )
        } else {
            chapter.name
        }
    }

    private fun get10sRange(value: Float): String {
        val number = value.toInt()
        return if (number < 10) {
            "0-9"
        } else {
            val hundred = number / 10
            "${hundred}0-${hundred + 1}9"
        }
    }

    interface MangaDetailsInterface : MangaHeaderInterface, DownloadInterface

    interface MangaHeaderInterface {
        fun coverColor(): Int?
        fun accentColor(): Int?
        fun mangaPresenter(): MangaDetailsPresenter
        fun prepareToShareManga()
        fun openInWebView()
        fun startDownloadRange(position: Int)
        fun readNextChapter(readingButton: View)
        fun topCoverHeight(): Int
        fun showFloatingActionMode(view: TextView, content: String? = null, isTag: Boolean = false)
        fun showChapterFilter()
        fun favoriteManga(longPress: Boolean)
        fun copyContentToClipboard(content: String, label: StringResource, useToast: Boolean = false)
        fun copyContentToClipboard(content: String, label: Int, useToast: Boolean = false)
        fun customActionMode(view: TextView): ActionMode.Callback
        fun copyContentToClipboard(content: String, label: String?, useToast: Boolean = false)
        fun zoomImageFromThumb(thumbView: View)
        fun showTrackingSheet()
        fun updateScroll()
        fun setFavButtonPopup(popupView: View)
    }
}
