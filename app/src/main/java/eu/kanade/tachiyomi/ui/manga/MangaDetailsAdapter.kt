package eu.kanade.tachiyomi.ui.manga

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
import eu.kanade.tachiyomi.ui.manga.chapter.ChapterPageItem
import eu.kanade.tachiyomi.ui.manga.chapter.ChapterPagesSectionItem
import eu.kanade.tachiyomi.util.chapter.ChapterUtil
import eu.kanade.tachiyomi.util.system.isLTR
import uy.kohesive.injekt.injectLazy
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

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

    private val pageSections = HashMap<Long, ChapterPagesSectionItem>()
    private val chapterPages = HashMap<Long, List<Page>>()
    private var currentPage: Pair<Long, Int>? = null

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
            section.pagesAttached = chapterPages.containsKey(chapterId)
            gridItems.add(section)
            chapterPages[chapterId]?.forEach { page ->
                gridItems.add(ChapterPageItem(chapter, page, isCurrentPage(chapter, page)))
            }
        }
        updateItems(gridItems)
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

    fun onPagesLoaded(
        section: ChapterPagesSectionItem,
        pages: List<Page>?,
        attachItems: Boolean = true,
    ) {
        val chapterId = section.chapter.id ?: return
        if (pages == null) {
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
            }            // Held back while scrolling; attachCachedPages() inserts them when the scroll settles.
            else -> section.pagesAttached = false
        }
        section.refreshBoundHolder()
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
                if (!controller.isListScrolling()) attachCachedPages(section)
            }
            return
        }
        val sectionPosition = getGlobalPositionOf(section)
        if (sectionPosition != -1) {
            insertPageItems(section, sectionPosition, pages)
            section.pagesAttached = true
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

        // RecyclerView doesn't compensate the scroll position when rows appear above the viewport,
        // so pin the current first visible row before inserting pages for an off-screen section.
        val firstChild = recycler.getChildAt(0)
        val firstPosition = firstChild?.let { recycler.getChildAdapterPosition(it) }
            ?: RecyclerView.NO_POSITION
        val firstItem = if (firstPosition != RecyclerView.NO_POSITION) {
            getItem(firstPosition)
        } else {
            null
        }
        val firstTop = firstChild?.top
        val insertsAboveViewport =
            firstPosition != RecyclerView.NO_POSITION && sectionPosition + 1 <= firstPosition

        val animator = recycler.itemAnimator
        recycler.itemAnimator = null
        try {
            addItems(sectionPosition + 1, pageItems)
        } finally {
            recycler.doOnNextLayout { recycler.itemAnimator = animator }
        }

        if (insertsAboveViewport && firstItem != null && firstTop != null) {
            val newPosition = getGlobalPositionOf(firstItem)
            if (newPosition != RecyclerView.NO_POSITION) {
                (recycler.layoutManager as? LinearLayoutManager)
                    ?.scrollToPositionWithOffset(newPosition, firstTop - recycler.paddingTop)
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
        val chapter = presenter.getNextUnreadChapter()?.chapter
        val chapterId = chapter?.id
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
        fun setChapterLayout(usePageGrid: Boolean)
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
