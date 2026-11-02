package eu.kanade.tachiyomi.ui.manga.chapter

import android.view.View
import coil3.imageLoader
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.target.ImageViewTarget
import eu.kanade.tachiyomi.data.coil.ChapterPageImage
import eu.kanade.tachiyomi.databinding.ChapterPageItemBinding
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.ui.manga.MangaDetailsAdapter

class ChapterPageHolder(
    view: View,
    private val adapter: MangaDetailsAdapter,
) : BaseFlexibleViewHolder(view, adapter) {

    private val binding = ChapterPageItemBinding.bind(view)
    private var disposable: Disposable? = null

    fun bind(item: ChapterPageItem) {
        val source = adapter.presenter.getHttpSource() ?: return
        binding.pageNumber.text = item.page.number.toString()
        disposable?.dispose()
        binding.pageImage.setImageDrawable(null)

        val request = ImageRequest.Builder(itemView.context)
            .data(ChapterPageImage(item.chapter, item.page, source))
            .target(ImageViewTarget(binding.pageImage))
            .build()
        disposable = itemView.context.imageLoader.enqueue(request)
    }

    fun dispose() {
        disposable?.dispose()
        disposable = null
        binding.pageImage.setImageDrawable(null)
    }
}
