package eu.kanade.tachiyomi.ui.manga.chapter

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.manga.preview.MangaPreviewAdapter

class ChapterPageItem(
    val chapter: Chapter,
    val page: Page,
    val isCurrentPage: Boolean = false,
) : AbstractFlexibleItem<ChapterPageHolder>() {

    override fun getLayoutRes(): Int {
        return R.layout.chapter_page_item
    }

    override fun createViewHolder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>): ChapterPageHolder {
        return ChapterPageHolder(view, adapter as MangaPreviewAdapter)
    }

    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: ChapterPageHolder,
        position: Int,
        payloads: MutableList<Any?>?,
    ) {
        holder.bind(this)
    }

    override fun unbindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>?,
        holder: ChapterPageHolder?,
        position: Int,
    ) {
        super.unbindViewHolder(adapter, holder, position)
        holder?.dispose()
    }

    override fun isSwipeable(): Boolean = false

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other is ChapterPageItem) {
            return chapter.id == other.chapter.id &&
                page.index == other.page.index &&
                isCurrentPage == other.isCurrentPage
        }
        return false
    }

    override fun hashCode(): Int {
        return 31 * (31 * (chapter.id ?: 0L).hashCode() + page.index) + isCurrentPage.hashCode()
    }
}
