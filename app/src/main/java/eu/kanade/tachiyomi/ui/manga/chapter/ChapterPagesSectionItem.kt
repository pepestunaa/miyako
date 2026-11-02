package eu.kanade.tachiyomi.ui.manga.chapter

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.ui.manga.MangaDetailsAdapter

class ChapterPagesSectionItem(val chapter: Chapter) : AbstractFlexibleItem<ChapterPagesSectionHolder>() {

    enum class State { Idle, Loading, Loaded, Error }

    var state: State = State.Idle
    var pageCount: Int = 0

    override fun getLayoutRes(): Int {
        return R.layout.chapter_pages_header_item
    }

    override fun createViewHolder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>): ChapterPagesSectionHolder {
        return ChapterPagesSectionHolder(view, adapter as MangaDetailsAdapter)
    }

    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: ChapterPagesSectionHolder,
        position: Int,
        payloads: MutableList<Any?>?,
    ) {
        holder.bind(this)
        (adapter as MangaDetailsAdapter).controller.onPageSectionBound(this)
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
