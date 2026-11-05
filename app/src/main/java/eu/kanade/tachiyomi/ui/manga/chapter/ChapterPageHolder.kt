package eu.kanade.tachiyomi.ui.manga.chapter

import android.view.View
import androidx.core.content.ContextCompat
import coil3.Image
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
import eu.kanade.tachiyomi.ui.manga.preview.MangaPreviewAdapter
import eu.kanade.tachiyomi.util.system.getResourceColor
import kotlin.math.roundToInt

class ChapterPageHolder(
    view: View,
    private val adapter: MangaPreviewAdapter,
) : BaseFlexibleViewHolder(view, adapter) {

    private val binding = ChapterPageItemBinding.bind(view)
    private var disposable: Disposable? = null
    private var boundPage: Pair<Long, Int>? = null
    private var loadedPage: Pair<Long, Int>? = null
    private var boundItem: ChapterPageItem? = null

    private val imageTarget = object : ImageViewTarget(binding.pageImage) {
        override fun onSuccess(result: Image) {
            super.onSuccess(result)
            loadedPage = boundPage
        }
    }

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
            loadedPage = null
            disposable?.dispose()
            disposable = null
            binding.pageImage.setImageDrawable(null)
        }
        boundItem = item
        // Bind runs before the holder is positioned; check visibility after the layout pass.
        itemView.post { loadImageIfNeeded() }
    }

    fun loadImageIfNeeded() {
        if (disposable != null) return
        // Recycling keeps the drawable, so re-binding the same page must not re-request it.
        if (loadedPage != null && loadedPage == boundPage && binding.pageImage.drawable != null) return
        // During a fling the grid passes over many chapters; only fetch what stays on screen.
        if (adapter.isFlinging) return
        if (!isOnScreen()) return
        val item = boundItem ?: return
        val context = itemView.context
        val source = adapter.presenter.getHttpSource() ?: return
        val request = ImageRequest.Builder(context)
            .data(ChapterPageImage(item.chapter, item.page, source))
            .size(thumbnailSize)
            .scale(Scale.FILL)
            .target(imageTarget)
            .build()
        disposable = context.imageLoader.enqueue(request)
    }

    private fun isOnScreen(): Boolean {
        val recycler = adapter.recyclerView ?: return false
        if (itemView.parent !== recycler) return false
        val top = recycler.paddingTop
        val bottom = recycler.height - recycler.paddingBottom
        return itemView.bottom > top && itemView.top < bottom
    }

    /**
     * Recycling a thumbnail must not throw its image away: scrolling back up would otherwise
     * re-fetch and re-decode every page that comes back into view. Only the in-flight request
     * is cancelled; the drawable stays until the holder is bound to a different page.
     */
    fun dispose() {
        disposable?.dispose()
        disposable = null
        boundItem = null
    }

    companion object {
        private const val PAGE_ASPECT_RATIO = 3f / 2f
    }
}
