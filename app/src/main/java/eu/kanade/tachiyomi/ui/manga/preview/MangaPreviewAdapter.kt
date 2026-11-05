package eu.kanade.tachiyomi.ui.manga.preview


import android.view.ActionMode
import android.view.View

import android.widget.TextView
import androidx.core.view.doOnNextLayout

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
import eu.kanade.tachiyomi.ui.manga.preview.chapter.ChapterPageHolder
import eu.kanade.tachiyomi.ui.manga.preview.chapter.ChapterPageItem
import eu.kanade.tachiyomi.ui.manga.preview.chapter.ChapterPagesSectionItem
import eu.kanade.tachiyomi.ui.manga.MangaDetailsAdapter
import eu.kanade.tachiyomi.ui.manga.MangaDetailsController
import eu.kanade.tachiyomi.ui.manga.MangaDetailsPresenter
import eu.kanade.tachiyomi.util.chapter.ChapterUtil

import eu.kanade.tachiyomi.util.system.isLTR
import eu.kanade.tachiyomi.util.system.withUIContext
import kotlinx.coroutines.launch
import uy.kohesive.injekt.injectLazy
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols


class MangaPreviewAdapter(
    val controller: MangaDetailsController,
) : BaseChapterAdapter<IFlexible<*>>(controller) {

    val preferences: PreferencesHelper by injectLazy()

    val hasShownSwipeTut
        get() = preferences.shownChapterSwipeTutorial()

    var items: List<ChapterItem> = emptyList()
        private set

    val previewState = MangaPreviewState()

    val pageGridMode: Boolean
        get() = previewState.pageGridMode
    var suppressPageLoads: Boolean
        get() = previewState.suppressPageLoads
        set(value) { previewState.suppressPageLoads = value }

    private val pageSections: HashMap<Long, ChapterPagesSectionItem>
        get() = previewState.pageSections

    private val chapterPages: HashMap<Long, List<Page>>
        get() = previewState.chapterPages

    private var currentPage: Pair<Long, Int>?
        get() = previewState.currentPage
        set(value) { previewState.currentPage = value }

    private var anchorChapterId: Long?
        get() = previewState.anchorChapterId
        set(value) { previewState.anchorChapterId = value }

    private var pendingScrollToCurrent: Boolean
        get() = previewState.pendingScrollToCurrent
        set(value) { previewState.pendingScrollToCurrent = value }

    private var landingPending: Boolean
        get() = previewState.landingPending
        set(value) { previewState.landingPending = value }

    private var priorityChapterId: Long?
        get() = previewState.priorityChapterId
        set(value) { previewState.priorityChapterId = value }
    val delegate: MangaDetailsAdapter.MangaDetailsInterface = controller
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
        } else {
            performFilter()
        }
    }

    fun setPageGridMode(enabled: Boolean) {
        if (previewState.pageGridMode == enabled) return
        previewState.pageGridMode = enabled
        rebuildPageGrid()
    }
    fun rebuildPageGrid() {
        if (!pageGridMode) {
            performFilter()
            return
        }
        refreshCurrentPage()
        val landingChapterId = currentPage?.first ?: anchorChapterId
        landingPending = landingChapterId != null
        if (landingPending) {
            pendingScrollToCurrent = true
        }
        val gridItems = mutableListOf<IFlexible<*>>()
        for (chapterItem in filteredItems()) {
            val chapter = chapterItem.chapter
            val chapterId = chapter.id ?: continue
            val section = pageSections.getOrPut(chapterId) { ChapterPagesSectionItem(chapter) }
            section.chapter = chapter
            section.state = when {
                chapterPages.containsKey(chapterId) -> ChapterPagesSectionItem.State.Loaded
                else -> section.state
            }
            section.pageCount = chapterPages[chapterId]?.size ?: 0
            if (!chapterPages.containsKey(chapterId)) section.pagesAttached = false
            gridItems.add(section)
            val pages = chapterPages[chapterId]
            // During the initial landing, only the target chapter may contribute cached rows. Other
            // cached chapters must stay header-only until the target is settled, or they can push it
            // down while the adapter is still building the first viewport.
            val mayAttachCachedPages = if (landingPending) {
                chapterId == landingChapterId
            } else {
                section.pagesAttached || !pageAttachIsHeldBack(section)
            }
            if (pages != null && mayAttachCachedPages) {
                pages.forEach { page ->
                    gridItems.add(ChapterPageItem(chapter, page, isCurrentPage(chapter, page)))
                }
            } else if (pages != null) {
                section.pagesAttached = false
            }
        }
        updateItems(gridItems)
        // A dataset swap scraps every child, so a rebuild cannot keep the panel where it was. In grid mode
        // the position is defined by the last-read page, so that is where a rebuild puts it.
        scrollToCurrentPage()
    }

    private fun completeLandingAfterLayout() {
        val recycler = recyclerView ?: return
        recycler.doOnNextLayout {
            landingPending = false
            pendingScrollToCurrent = false
        }
    }

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
        // The gate is a rule about rows, not about requests: asking for a page list changes nothing in
        // the dataset, and gating it here is what left a parked chapter sitting on a bare header with
        // nothing loading until a drag. `attachCachedPages` holds the rows themselves back.
        attachCachedPages(section)
        if (!pageSectionShouldLoad(section)) return
        // Hold back other chapters while the last-read one is still fetching: the panel anchors
        // there, and its thumbnails are the first the user actually sees.
        val priority = priorityChapterId
        if (priority != null && section.chapter.id != priority) return
        markPageSectionLoading(section)
        previewLoader.load(section)
    }

    private fun isAboveFirstVisibleRow(section: ChapterPagesSectionItem): Boolean {
        val position = getGlobalPositionOf(section)
        if (position == -1) return false
        val recycler = recyclerView ?: return false
        // The panel is padded below the app bar, so a child that ends above that line is behind the
        // bar and is not content the user is looking at. Letting it define the top row makes the
        // parked target look "above the viewport" and lets the chapters above it shove it down.
        val contentTop = recycler.paddingTop
        var topRow = RecyclerView.NO_POSITION
        for (i in 0 until recycler.childCount) {
            val child = recycler.getChildAt(i) ?: continue
            if (child.bottom <= contentTop || child.top >= recycler.height) continue
            val childPosition = recycler.getChildAdapterPosition(child)
            if (childPosition == RecyclerView.NO_POSITION) continue
            if (topRow == RecyclerView.NO_POSITION || childPosition < topRow) topRow = childPosition
        }
        return topRow != RecyclerView.NO_POSITION && position < topRow
    }

    /**
     * The single rule for every place that puts page rows into the dataset: rows going in above the
     * panel's top row are not compensated, so they shove whatever the user is looking at down. The
     * exception is the chapter the landing still owes the viewport: holding its rows back is what
     * leaves the panel parked on an empty header.
     */
    private fun pageAttachIsHeldBack(section: ChapterPagesSectionItem): Boolean {
        val chapterId = section.chapter.id ?: return false
        if (landingPending &&
            (chapterId == currentPage?.first || chapterId == anchorChapterId || chapterId == priorityChapterId)
        ) {
            return false
        }
        return isAboveFirstVisibleRow(section)
    }

    fun retryPageSection(section: ChapterPagesSectionItem) {
        if (!pageGridMode) return
        resetPageSection(section)
        markPageSectionLoading(section)
        previewLoader.load(section)
    }

    fun loadVisiblePageSections() {
        if (!pageGridMode) return
        val recycler = recyclerView ?: return
        // A jump that has not laid out yet leaves the pre-jump children in place; walking them
        // would treat the top of the list as what is on screen.
        if (recycler.isLayoutRequested) {
            recycler.doOnNextLayout { loadVisiblePageSections() }
            return
        }
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
            if (presenter.historyLoaded) recycler.scrollToPosition(0)
            return
        }
        // Fetch the last-read chapter's page list before any other section gets a chance.
        priorityChapterId = if (chapterPages.containsKey(chapterId)) null else chapterId

        val pagePosition = currentPagePosition()
        if (pagePosition != null) {
            anchorToCurrentPage(pagePosition)
            if (landingPending) completeLandingAfterLayout()
            else pendingScrollToCurrent = false
            return
        }
        // The current chapter's thumbnails may not be loaded yet: park on its section and
        // jump to the page itself once they attach. A finished chapter is already the
        // destination, so only a marker history has not delivered yet is worth waiting for.
        pendingScrollToCurrent = currentPage != null || !presenter.historyLoaded
        val sectionPosition = sectionPositionOf(chapterId)
        val section = sectionPosition?.let { getItem(it) as? ChapterPagesSectionItem }
        if (sectionPosition == null || section == null) {
            priorityChapterId = null
            return
        }
        scrollToRowAtTop(recycler, sectionPosition)
        if (currentPage == null && landingPending) completeLandingAfterLayout()
        loadVisiblePageSections()
    }


    private fun anchorToCurrentPage(position: Int) {
        val recycler = recyclerView ?: return
        scrollToRowAtTop(recycler, position)
    }

    private fun scrollToRowAtTop(recycler: RecyclerView, position: Int) {
        val layoutManager = recycler.layoutManager as? LinearLayoutManager
        if (layoutManager != null) {
            layoutManager.scrollToPositionWithOffset(position, 0)
        } else {
            recycler.scrollToPosition(position)
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
        val position = currentPagePosition() ?: return
        anchorToCurrentPage(position)
        if (landingPending) completeLandingAfterLayout()
        else pendingScrollToCurrent = false
    }

    val previewLoader = MangaPreviewLoader(this)
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
            attachItems && !pageAttachIsHeldBack(section) -> {
                insertPageItems(section, sectionPosition, pages)
                section.pagesAttached = true
                maybeScrollToCurrentPage(chapterId)
            }
            // Held back while scrolling, or because the list was fetched while its section was above
            // the viewport: inserting it now would only push the content the user is looking at down.
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
        // Checked here and not at the caller: this runs again from a post once a layout settles, and a
        // caller-side check does not travel with that re-entry.
        if (pageAttachIsHeldBack(section)) return
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
            addItems(sectionPosition + 1, pageItems)
        } finally {
            recycler.doOnNextLayout {
                recycler.itemAnimator = animator
            }
        }
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

    fun performFilter() {
        if (pageGridMode) {
            rebuildPageGrid()
            return
        }
        updateItems(filteredItems())
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
