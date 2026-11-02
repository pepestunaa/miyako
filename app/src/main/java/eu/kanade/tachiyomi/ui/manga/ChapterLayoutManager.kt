package eu.kanade.tachiyomi.ui.manga

import android.content.Context
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.manga.chapter.ChapterItem
import eu.kanade.tachiyomi.ui.manga.chapter.ChapterPageItem
import eu.kanade.tachiyomi.widget.getFirstCompletePos
import eu.kanade.tachiyomi.widget.getFirstPos

/**
 * Single layout manager for both chapter list and page grid modes. Keeping one instance means
 * switching layouts only changes span sizes, so children are rebound in place instead of being
 * detached and reappearing a frame later.
 */
class ChapterLayoutManager(
    context: Context,
    private val adapter: MangaDetailsAdapter,
    initialSpanCount: Int,
) : GridLayoutManager(context, initialSpanCount) {

    private var attachedRecycler: RecyclerView? = null

    private val toolbarHeight: Int by lazy {
        val attrs = intArrayOf(R.attr.mainActionBarSize)
        val array = context.obtainStyledAttributes(attrs)
        val height = array.getDimensionPixelSize(0, 0)
        array.recycle()
        height
    }

    init {
        spanSizeLookup = object : SpanSizeLookup() {
            init {
                isSpanIndexCacheEnabled = true
            }

            override fun getSpanSize(position: Int): Int {
                if (!adapter.pageGridMode) return spanCount
                return when (adapter.getItem(position)) {
                    is ChapterItem, is ChapterPageItem -> 1
                    else -> spanCount
                }
            }
        }
    }

    fun updateLayoutMode() {
        spanSizeLookup.invalidateSpanIndexCache()
    }

    override fun onAttachedToWindow(view: RecyclerView?) {
        super.onAttachedToWindow(view)
        attachedRecycler = view
    }

    override fun onDetachedFromWindow(view: RecyclerView?, recycler: RecyclerView.Recycler?) {
        super.onDetachedFromWindow(view, recycler)
        attachedRecycler = null
    }

    override fun findFirstVisibleItemPosition(): Int = getFirstPos(attachedRecycler, toolbarHeight)

    override fun findFirstCompletelyVisibleItemPosition(): Int =
        getFirstCompletePos(attachedRecycler, toolbarHeight)
}
