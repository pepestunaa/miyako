package eu.kanade.tachiyomi.ui.manga

import android.content.res.Resources
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.R
import kotlin.math.roundToInt

class GridSpanResolver(resources: Resources) : View.OnLayoutChangeListener {

    private val cellWidth =
        resources.getDimension(R.dimen.chapter_page_grid_preferred_width) +
            resources.getDimension(R.dimen.chapter_page_grid_spacing)

    var spanCount: Int = resolve(resources.displayMetrics.widthPixels)
        private set

    override fun onLayoutChange(
        view: View,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        oldLeft: Int,
        oldTop: Int,
        oldRight: Int,
        oldBottom: Int,
    ) {
        val recyclerView = view as? RecyclerView ?: return
        val width = recyclerView.width - recyclerView.paddingStart - recyclerView.paddingEnd
        if (width <= 0) return
        spanCount = resolve(width)
        (recyclerView.layoutManager as? GridLayoutManager)?.spanCount = spanCount
    }

    fun resolve(widthPx: Int): Int = (widthPx / cellWidth).roundToInt().coerceAtLeast(MIN_SPAN_COUNT)

    companion object {
        private const val MIN_SPAN_COUNT = 2
    }
}
