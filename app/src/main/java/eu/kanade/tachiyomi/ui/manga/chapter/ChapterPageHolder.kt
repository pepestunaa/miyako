package eu.kanade.tachiyomi.ui.manga.chapter

import android.view.View
import androidx.core.content.ContextCompat
import coil3.imageLoader
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.size.Scale
import coil3.size.Size
import coil3.target.ImageViewTarget
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.coil.ChapterPageImage
import eu.kanade.tachiyomi.databinding.ChapterPageItemBinding
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.ui.manga.MangaDetailsAdapter
import eu.kanade.tachiyomi.util.system.getResourceColor
import kotlin.math.roundToInt

class ChapterPageHolder(
    view: View,
    private val adapter: MangaDetailsAdapter,
) : BaseFlexibleViewHolder(view, adapter) {

    private val binding = ChapterPageItemBinding.bind(view)
    private var disposable: Disposable? = null
    private var boundPage: Pair<Long, Int>? = null
    private var boundItem: ChapterPageItem? = null

    private val thumbnailSize: Size by lazy {
        val width = itemView.resources.getDimensionPixelSize(R.dimen.chapter_page_grid_preferred_width)
        Size(width, (width * PAGE_ASPECT_RATIO).roundToInt())
    }

    fun bind(item: ChapterPageItem) {
        val context = itemView.context
        val pageNumber = binding.pageNumber
        pageNumber.text = item.page.number.toString()
        if (item.isCurrentPage) {
            pageNumber.setBackgroundResource(R.drawable.bg_page_number_badge_current)
            pageNumber.setTextColor(context.getResourceColor(com.google.android.material.R.attr.colorOnPrimary))
        } else {
            pageNumber.setBackgroundResource(R.drawable.bg_page_number_badge)
            pageNumber.setTextColor(ContextCompat.getColor(context, R.color.md_white_1000))
        }

        val pageKey = (item.chapter.id ?: 0L) to item.page.index
        if (pageKey != boundPage) {
            boundPage = pageKey
            disposable?.dispose()
            disposable = null
            binding.pageImage.setImageDrawable(null)
        }
        boundItem = item
        loadImageIfNeeded()
    }

    fun loadImageIfNeeded() {
        if (disposable != null) return
        // During a fling the grid passes over many chapters; only fetch what stays on screen.
        if (adapter.isFlinging) return
        val item = boundItem ?: return
        val context = itemView.context
        val source = adapter.presenter.getHttpSource() ?: return
        val request = ImageRequest.Builder(context)
            .data(ChapterPageImage(item.chapter, item.page, source))
            .size(thumbnailSize)
            .scale(Scale.FILL)
            .target(ImageViewTarget(binding.pageImage))
            .build()
        disposable = context.imageLoader.enqueue(request)
    }

    fun dispose() {
        disposable?.dispose()
        disposable = null
        boundPage = null
        boundItem = null
        binding.pageImage.setImageDrawable(null)
    }

    companion object {
        private const val PAGE_ASPECT_RATIO = 3f / 2f
    }
}
