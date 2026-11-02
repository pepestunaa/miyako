package eu.kanade.tachiyomi.ui.manga.chapter

import android.view.View
import androidx.core.view.isVisible
import eu.kanade.tachiyomi.databinding.ChapterPagesHeaderItemBinding
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.ui.manga.MangaDetailsAdapter
import eu.kanade.tachiyomi.util.chapter.ChapterUtil
import eu.kanade.tachiyomi.util.chapter.ChapterUtil.Companion.preferredChapterName
import yokai.i18n.MR
import yokai.util.lang.getString

class ChapterPagesSectionHolder(
    view: View,
    private val adapter: MangaDetailsAdapter,
) : BaseFlexibleViewHolder(view, adapter) {

    private val binding = ChapterPagesHeaderItemBinding.bind(view)

    fun bind(item: ChapterPagesSectionItem) {
        val context = itemView.context
        binding.chapterPagesTitle.text =
            item.chapter.preferredChapterName(context, adapter.presenter.manga, adapter.preferences)
        ChapterUtil.setTextViewForChapter(
            binding.chapterPagesTitle,
            item.chapter,
            showBookmark = false,
        )

        binding.chapterPagesProgress.isVisible = item.state == ChapterPagesSectionItem.State.Loading
        val status = when (item.state) {
            ChapterPagesSectionItem.State.Loaded ->
                if (item.pageCount == 0) context.getString(MR.strings.no_pages_found) else null
            ChapterPagesSectionItem.State.Error -> context.getString(MR.strings.retry)
            else -> null
        }
        binding.chapterPagesStatus.isVisible = status != null
        binding.chapterPagesStatus.text = status
        itemView.setOnClickListener(
            if (item.state == ChapterPagesSectionItem.State.Error) {
                View.OnClickListener { adapter.controller.retryPageSection(item) }
            } else {
                null
            },
        )
    }
}
