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
import eu.kanade.tachiyomi.ui.manga.chapter.BaseChapterAdapter
import eu.kanade.tachiyomi.ui.manga.chapter.ChapterItem
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

    val delegate: MangaDetailsInterface = controller
    val presenter = controller.presenter

    val decimalFormat = DecimalFormat(
        "#.###",
        DecimalFormatSymbols()
            .apply { decimalSeparator = '.' },
    )

    fun setChapters(items: List<ChapterItem>?) {
        this.items = items ?: emptyList()
        performFilter()
    }







    private fun updateItems(items: List<IFlexible<*>>) {
        // FlexibleAdapter skips updateDataSet while a filter is active; filtering is handled by filteredItems().
        val filter = getFilter(String::class.java)
        if (!filter.isNullOrEmpty()) setFilter("")
        updateDataSet(items, false)
        if (!filter.isNullOrEmpty()) setFilter(filter)
    }











    /**
     * The single rule for every place that puts page rows into the dataset: rows going in above the
     * panel's top row are not compensated, so they shove whatever the user is looking at down. The
     * exception is the chapter the landing still owes the viewport: holding its rows back is what
     * leaves the panel parked on an empty header.
     */































    private fun filteredItems(): List<ChapterItem> {
        val s = getFilter(String::class.java)
        return if (s.isNullOrBlank()) {
            items
        } else {
            items.filter { it.name.contains(s, true) }
        }
    }

    fun performFilter() {
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
