package eu.kanade.tachiyomi.ui.manga.chapter

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.ui.manga.preview.MangaPreviewAdapter

class ChapterPagesSectionItem(var chapter: Chapter) : AbstractFlexibleItem<ChapterPagesSectionHolder>() {

    enum class State { Idle, Loading, Loaded, Error }

    var state: State = State.Idle
    var pageCount: Int = 0
    var pagesAttached: Boolean = false

    private var boundHolder: ChapterPagesSectionHolder? = null

    fun refreshBoundHolder() {
        boundHolder?.bind(this)
    }

    override fun getLayoutRes(): Int {
        return R.layout.chapter_pages_header_item
    }

    override fun createViewHolder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>): ChapterPagesSectionHolder {
        return ChapterPagesSectionHolder(view, adapter as MangaPreviewAdapter)
    }

    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: ChapterPagesSectionHolder,
        position: Int,
        payloads: MutableList<Any?>?,
    ) {
        boundHolder = holder
        holder.bind(this)
        (adapter as MangaPreviewAdapter).onPageSectionBound(this)
    }

    override fun unbindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>?,
        holder: ChapterPagesSectionHolder?,
        position: Int,
    ) {
        super.unbindViewHolder(adapter, holder, position)
        if (boundHolder === holder) boundHolder = null
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other is ChapterPagesSectionItem) {
            return chapter.id == other.chapter.id
        }
        return false
    }

    override fun hashCode(): Int {
        return (chapter.id ?: 0L).hashCode()
    }

    override fun isSwipeable(): Boolean = false
}
