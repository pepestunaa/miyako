package eu.kanade.tachiyomi.ui.manga

import android.view.ActionMode
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import dev.icerock.moko.resources.StringResource
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import yokai.i18n.MR
import yokai.util.lang.getString
import dev.icerock.moko.resources.compose.stringResource
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
        val gridItems = mutableListOf<IFlexible<*>>()
        for (chapterItem in filteredItems()) {
            val chapter = chapterItem.chapter
            val chapterId = chapter.id ?: continue
            val section = pageSections.getOrPut(chapterId) { ChapterPagesSectionItem(chapter) }
            section.state = when {
                chapterPages.containsKey(chapterId) -> ChapterPagesSectionItem.State.Loaded
                else -> section.state
            }
            section.pageCount = chapterPages[chapterId]?.size ?: 0
            gridItems.add(section)
            chapterPages[chapterId]?.forEach { page ->
                gridItems.add(ChapterPageItem(chapter, page))
            }
        }
        updateDataSet(gridItems, false)
    }

    fun pageSectionShouldLoad(section: ChapterPagesSectionItem): Boolean {
        val chapterId = section.chapter.id ?: return false
        return section.state != ChapterPagesSectionItem.State.Loading &&
            !chapterPages.containsKey(chapterId)
    }

    fun markPageSectionLoading(section: ChapterPagesSectionItem) {
        section.state = ChapterPagesSectionItem.State.Loading
        section.pageCount = 0
    }

    fun resetPageSection(section: ChapterPagesSectionItem) {
        section.state = ChapterPagesSectionItem.State.Idle
    }

    fun notifyPageSection(section: ChapterPagesSectionItem) {
        notifySectionChanged(section)
    }

    fun onPagesLoaded(section: ChapterPagesSectionItem, pages: List<Page>?) {
        val chapterId = section.chapter.id ?: return
        if (pages == null) {
            section.state = ChapterPagesSectionItem.State.Error
            notifySectionChanged(section)
            return
        }

        section.state = ChapterPagesSectionItem.State.Loaded
        section.pageCount = pages.size
        chapterPages[chapterId] = pages

        val sectionPosition = getGlobalPositionOf(section)
        if (sectionPosition != -1 && pages.isNotEmpty() && pageGridMode) {
            val pageItems = pages.map { ChapterPageItem(section.chapter, it) as IFlexible<*> }
            addItems(sectionPosition + 1, pageItems)
        }
        notifySectionChanged(section)
    }

    fun pagesForChapter(chapterId: Long): List<Page>? = chapterPages[chapterId]

    private fun notifySectionChanged(section: ChapterPagesSectionItem) {
        val position = getGlobalPositionOf(section)
        if (position != -1) notifyItemChanged(position)
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
        updateDataSet(filteredItems())
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
        val chapter =
            getItem(position) as? ChapterItem ?: return recyclerView.context.getString(MR.strings.top)
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

    private fun getChapterName(item: ChapterItem): String {
        return if (item.chapter_number > 0) {
            recyclerView.context.getString(
                MR.strings.chapter_,
                decimalFormat.format(item.chapter_number),
            )
        } else {
            item.name
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
        fun toggleChapterLayout()
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
