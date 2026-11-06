package eu.kanade.tachiyomi.ui.manga.preview

import android.R as AR
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.service.chooser.ChooserAction
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.FloatRange
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.graphics.ColorUtils
import androidx.core.net.toFile
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.doOnNextLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePaddingRelative
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.touchlab.kermit.Logger
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.SizeResolver
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.ControllerChangeType
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import dev.icerock.moko.resources.StringResource
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.SelectableAdapter
import eu.davidea.flexibleadapter.helpers.ItemTouchHelperCallback
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.coil.getBestColor
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.seriesType
import eu.kanade.tachiyomi.data.database.models.vibrantCoverColor
import eu.kanade.tachiyomi.data.download.DownloadJob
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.databinding.MangaDetailsControllerBinding
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.icon
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.base.MaterialMenuSheet
import eu.kanade.tachiyomi.ui.base.SmallToolbarInterface
import eu.kanade.tachiyomi.ui.base.controller.BaseCoroutineController
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.ui.library.FilteredLibraryController
import eu.kanade.tachiyomi.ui.library.LibraryController
import eu.kanade.tachiyomi.ui.main.FloatingSearchInterface
import eu.kanade.tachiyomi.ui.main.HingeSupportedController
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.main.SearchActivity
import eu.kanade.tachiyomi.ui.manga.chapter.ChapterHolder
import eu.kanade.tachiyomi.ui.manga.chapter.ChapterItem
import eu.kanade.tachiyomi.ui.manga.chapter.ChaptersSortBottomSheet
import eu.kanade.tachiyomi.ui.manga.preview.MangaPreviewAdapter
import eu.kanade.tachiyomi.ui.manga.preview.chapter.ChapterPageItem
import eu.kanade.tachiyomi.ui.manga.track.TrackItem
import eu.kanade.tachiyomi.ui.manga.track.TrackingBottomSheet
import eu.kanade.tachiyomi.ui.migration.manga.design.PreMigrationController
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.recents.RecentsController
import eu.kanade.tachiyomi.ui.security.SecureActivityDelegate
import eu.kanade.tachiyomi.ui.source.BrowseController
import eu.kanade.tachiyomi.ui.source.browse.BrowseSourceController
import eu.kanade.tachiyomi.ui.source.globalsearch.GlobalSearchController
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.addOrRemoveToFavorites
import eu.kanade.tachiyomi.util.chapter.updateTrackChapterMarkedAsRead
import eu.kanade.tachiyomi.util.isLocal
import eu.kanade.tachiyomi.util.moveCategories
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.addCheckBoxPrompt
import eu.kanade.tachiyomi.util.system.contextCompatColor
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.e
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.ignoredSystemInsets
import eu.kanade.tachiyomi.util.system.isInNightMode
import eu.kanade.tachiyomi.util.system.isLandscape
import eu.kanade.tachiyomi.util.system.isOnline
import eu.kanade.tachiyomi.util.system.isPromptChecked
import eu.kanade.tachiyomi.util.system.isTablet
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.system.rootWindowInsetsCompat
import eu.kanade.tachiyomi.util.system.setCustomTitleAndMessage
import eu.kanade.tachiyomi.util.system.timeSpanFromNow
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.system.withUIContext
import eu.kanade.tachiyomi.util.view.activityBinding
import eu.kanade.tachiyomi.util.view.copyToClipboard
import eu.kanade.tachiyomi.util.view.findChild
import eu.kanade.tachiyomi.util.view.getText
import eu.kanade.tachiyomi.util.view.isControllerVisible
import eu.kanade.tachiyomi.util.view.previousController
import eu.kanade.tachiyomi.util.view.scrollViewWith
import eu.kanade.tachiyomi.util.view.setAction
import eu.kanade.tachiyomi.util.view.setMessage
import eu.kanade.tachiyomi.util.view.setNegativeButton
import eu.kanade.tachiyomi.util.view.setOnQueryTextChangeListener
import eu.kanade.tachiyomi.util.view.setPositiveButton
import eu.kanade.tachiyomi.util.view.setStyle
import eu.kanade.tachiyomi.util.view.setTextColorAlpha
import eu.kanade.tachiyomi.util.view.snack
import eu.kanade.tachiyomi.util.view.toolbarHeight
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import yokai.domain.manga.models.cover
import yokai.i18n.MR
import yokai.presentation.core.Constants
import yokai.util.lang.getString
import eu.kanade.tachiyomi.ui.manga.ChapterLayoutManager
import eu.kanade.tachiyomi.ui.manga.MangaDetailsController

class MangaPreviewController(
    val controller: eu.kanade.tachiyomi.ui.manga.MangaDetailsController,
    val binding: eu.kanade.tachiyomi.databinding.MangaDetailsControllerBinding,
) {

    var previewAdapter: eu.kanade.tachiyomi.ui.manga.preview.MangaPreviewAdapter? = null
    var previewOpen: Boolean = false
    var swipeAnimating: Boolean = false
    var hintScheduled: Boolean = false
    var hintAnimating: Boolean = false
    var previewWarmed: Boolean = false

    fun reset() {
        previewAdapter = null
        previewOpen = false
        swipeAnimating = false
        hintScheduled = false
        hintAnimating = false
        previewWarmed = false
    }

    fun setupPreviewPanel() {
        val adapter = MangaPreviewAdapter(controller).apply {
            setPageGridMode(true)
            addListener(
                object : FlexibleAdapter.OnItemClickListener, FlexibleAdapter.OnItemLongClickListener {
                    override fun onItemClick(view: View?, position: Int): Boolean {
                        val item = getItem(position) as? ChapterPageItem ?: return false
                        controller.openChapter(item.chapter, page = item.page.index)
                        return false
                    }

                    override fun onItemLongClick(position: Int) = Unit
                },
            )
            setChapters(controller.presenter.chapters)
        }
        previewAdapter = adapter
        val recycler = binding.pagesPreviewRecycler
        recycler.adapter = adapter
        recycler.layoutManager = ChapterLayoutManager(
            recycler.context,
            adapter,
            controller.gridSpanResolver?.spanCount ?: DEFAULT_GRID_SPAN_COUNT,
        )
        recycler.setHasFixedSize(true)
        recycler.itemAnimator = null
        recycler.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {

                    if (newState == RecyclerView.SCROLL_STATE_IDLE) adapter.loadVisiblePageSections()
                }

                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    // Fill thumbnails as rows enter the viewport during a drag; flings load on IDLE.
                    if (recyclerView.scrollState == RecyclerView.SCROLL_STATE_DRAGGING) {
                        adapter.loadVisiblePageSections()
                    } else {
                        // A fling can outrun a fixed window, so keep appending rows as it moves.
                        adapter.extendVisiblePageWindows()
                    }
                }
            },
        )
    }

    /**
     * Derive the backdrop offset from the header's actual position instead of accumulating every
     * scroll delta. App-bar settling and RecyclerView corrections can emit opposing deltas in one
     * frame; accumulating them made the image stutter when the detail page was dragged back up.
     */

    fun setupPanelSwipe(recycler: RecyclerView, opensPanel: Boolean) {
        val touchSlop = ViewConfiguration.get(recycler.context).scaledTouchSlop
        val velocityTracker = VelocityTracker.obtain()
        var downRawX = 0f
        var downRawY = 0f
        var lastRawX = 0f
        var lastRawY = 0f
        var dragging = false
        var blockSwipe = false
        var dragStartTranslation = 0f
        var dragWidth = 1f
        // Another touch listener can hold the start of a gesture (a row snapping back, for
        // example) and then this listener never sees its DOWN, so the first move re-anchors.
        var anchored = false

        fun panelAvailable(): Boolean = if (opensPanel) !previewOpen else previewOpen

        fun anchor(e: MotionEvent) {
            if (hintAnimating) {
                hintAnimating = false
                previewAdapter?.suppressPageLoads = false
                binding.pagesPreviewRecycler.animate().cancel()
                if (!previewOpen) {
                    binding.pagesPreviewRecycler.visibility = View.GONE
                    binding.pagesPreviewRecycler.translationX = 0f
                }
            }
            downRawX = e.rawX
            downRawY = e.rawY
            lastRawX = e.rawX
            lastRawY = e.rawY
            dragging = false
            blockSwipe = opensPanel && startsOnSwipeableChapter(recycler, e)
            velocityTracker.clear()
            velocityTracker.addMovement(e)
            anchored = true
        }

        fun moveDrag(e: MotionEvent) {
            val dx = e.rawX - downRawX
            binding.pagesPreviewRecycler.translationX =
                (dragStartTranslation + dx).coerceIn(0f, dragWidth)
            updatePreviewScrimAlpha()
        }

        fun beginDrag(e: MotionEvent): Boolean {
            if (opensPanel) {
                val width = binding.recycler.width
                if (width <= 0) return false
                // The panel stays GONE while closed, so show it already parked off-screen right.
                binding.pagesPreviewRecycler.visibility = View.VISIBLE
                showPreviewScrims()
                dragStartTranslation = width.toFloat()
                dragWidth = width.toFloat()
            } else {
                dragStartTranslation = 0f
                dragWidth = binding.pagesPreviewRecycler.width.toFloat().coerceAtLeast(1f)
            }
            binding.pagesPreviewRecycler.translationX = dragStartTranslation
            moveDrag(e)
            return true
        }

        fun settleDrag(velocityX: Float = 0f) {
            val translation = binding.pagesPreviewRecycler.translationX
            val complete = if (opensPanel) {
                translation < dragWidth - dragWidth * PANEL_SWIPE_THRESHOLD ||
                    velocityX < -PANEL_FLING_VELOCITY
            } else {
                translation > dragWidth * PANEL_SWIPE_THRESHOLD ||
                    velocityX > PANEL_FLING_VELOCITY
            }
            val opened = complete == opensPanel
            animatePreviewPanel(if (opened) 0f else dragWidth) {
                if (opened) onPreviewOpened() else onPreviewClosed()
            }
        }

        fun finishDrag() {
            velocityTracker.computeCurrentVelocity(1000)
            settleDrag(velocityTracker.xVelocity)
        }

        fun cancelDrag() {
            // RecyclerView can cancel a horizontal drag when another touch participant wins
            // the gesture. Keep the panel's current progress as the source of truth instead of
            // always snapping back; otherwise a mostly completed swipe flashes in and closes.
            settleDrag()
        }

        recycler.addOnItemTouchListener(
            object : RecyclerView.SimpleOnItemTouchListener() {
                override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                    if (swipeAnimating) return false
                    when (e.actionMasked) {
                        MotionEvent.ACTION_DOWN -> anchor(e)
                        MotionEvent.ACTION_MOVE -> {
                            if (!anchored) {
                                anchor(e)
                                return false
                            }
                            velocityTracker.addMovement(e)
                            val dx = e.rawX - downRawX
                            val dy = e.rawY - downRawY
                            // Judge by the latest movement too: a stale anchor must not turn a
                            // vertical drag into a panel swipe.
                            val stepX = e.rawX - lastRawX
                            val stepY = e.rawY - lastRawY
                            if (!dragging &&
                                !blockSwipe &&
                                !controller.isTablet &&
                                controller.manga?.isLocal() != true &&
                                panelAvailable() &&
                                (if (opensPanel) dx < 0f else dx > 0f) &&
                                abs(dx) > touchSlop &&
                                abs(dx) > abs(dy) * 1.25f &&
                                abs(stepX) > abs(stepY) * 1.25f
                            ) {
                                dragging = beginDrag(e)
                            } else if (dragging) {
                                moveDrag(e)
                            }
                            lastRawX = e.rawX
                            lastRawY = e.rawY
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            dragging = false
                            anchored = false
                        }
                    }
                    // Intercepting cancels the child press so a swipe doesn't also click the row/button under it.
                    return dragging
                }

                override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
                    when (e.actionMasked) {
                        MotionEvent.ACTION_MOVE -> {
                            if (swipeAnimating) return
                            if (!anchored) {
                                anchor(e)
                                return
                            }
                            velocityTracker.addMovement(e)
                            if (dragging) moveDrag(e)
                        }
                        MotionEvent.ACTION_UP -> {
                            if (dragging) finishDrag()
                            dragging = false
                            anchored = false
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            if (dragging) cancelDrag()
                            dragging = false
                            anchored = false
                        }
                    }
                }
            },
        )
    }


    fun scheduleSwipeHint() {
        if (hintScheduled || controller.isTablet) return
        val mangaId = controller.manga?.id ?: return
        if (controller.presenter.preferences.hasShownDetailSwipeHint(mangaId)) return
        val view = controller.view ?: return
        // Chapters load asynchronously; stay unarmed until the preview grid has content,
        // otherwise a slow manga would consume the one scheduled hint and never show it.
        if ((previewAdapter?.itemCount ?: 0) == 0) return
        hintScheduled = true
        controller.view?.postDelayed({ showSwipeHint() }, PANEL_HINT_DELAY_MS)
    }


    fun prewarmPreviewPanel() {
        if (previewWarmed || controller.isTablet) return
        val view = controller.view ?: return
        if ((previewAdapter?.itemCount ?: 0) == 0) return
        // Warm off-screen once (after the hint) so the first swipe only translates a grid
        // that is already laid out and anchored instead of inflating it mid-drag.
        controller.view?.postDelayed({
            val adapter = previewAdapter ?: return@postDelayed
            val panel = binding.pagesPreviewRecycler
            if (previewWarmed || previewOpen || hintAnimating || swipeAnimating) return@postDelayed
            if (panel.visibility == View.VISIBLE) return@postDelayed
            val width = binding.recycler.width.toFloat()
            if (width <= 0f) return@postDelayed
            previewWarmed = true
            panel.translationX = width
            panel.visibility = View.VISIBLE
            panel.post {
                adapter.loadVisiblePageSections()
                panel.postDelayed({
                    if (!previewOpen && !hintAnimating && !swipeAnimating &&
                        panel.translationX >= width
                    ) {
                        panel.visibility = View.GONE
                    }
                }, PANEL_WARM_SETTLE_MS)
            }
        }, PANEL_WARM_DELAY_MS)
    }


    fun showSwipeHint() {
        if (controller.view == null) return
        val manga = controller.manga ?: return
        val mangaId = manga.id ?: return
        if (controller.presenter.preferences.hasShownDetailSwipeHint(mangaId)) return
        if (controller.isTablet || manga.isLocal() || previewOpen || swipeAnimating || hintAnimating) return
        val panel = binding.pagesPreviewRecycler
        if (panel.visibility != View.GONE) return
        val adapter = previewAdapter ?: return
        val width = binding.recycler.width.toFloat()
        if (width <= 0f || adapter.itemCount == 0) return
        controller.presenter.preferences.markDetailSwipeHintShown(mangaId)
        // Nudge the right-docked panel into view and back so the swipe affordance is obvious.
        hintAnimating = true
        adapter.suppressPageLoads = true
        panel.visibility = View.VISIBLE
        panel.translationX = width
        panel.animate().cancel()
        panel.animate()
            .translationX(width * (1f - PANEL_HINT_PEEK))
            .setDuration(PANEL_HINT_IN_MS)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                if (!hintAnimating || previewOpen) return@withEndAction
                panel.animate()
                    .translationX(width)
                    .setStartDelay(PANEL_HINT_HOLD_MS)
                    .setDuration(PANEL_HINT_OUT_MS)
                    .setInterpolator(AccelerateInterpolator())
                    .withEndAction {
                        hintAnimating = false
                        adapter.suppressPageLoads = false
                        if (!previewOpen) panel.visibility = View.GONE
                    }
                    .start()
            }
            .start()
    }


    fun animatePreviewPanel(target: Float, onEnd: () -> Unit) {
        val panel = binding.pagesPreviewRecycler
        swipeAnimating = true
        val width = binding.recycler.width.toFloat().coerceAtLeast(1f)
        val distance = abs(target - panel.translationX)
        val duration = (PANEL_ANIM_MS * (distance / width))
            .roundToLong()
            .coerceIn(PANEL_ANIM_MIN_MS, PANEL_ANIM_MS)
        panel.animate().cancel()
        panel.animate()
            .translationX(target)
            .setDuration(duration)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                swipeAnimating = false
                onEnd()
            }
            .start()
        animatePreviewScrimAlpha(1f - (target / width).coerceIn(0f, 1f), duration)
    }


    fun showPreviewScrims() {
        binding.pagesPreviewScrimTop.apply {
            visibility = View.VISIBLE
            alpha = 0f
        }
        binding.pagesPreviewScrimBottom.apply {
            visibility = View.VISIBLE
            alpha = 0f
        }
    }


    fun updatePreviewScrimAlpha() {
        if (binding.pagesPreviewScrimTop.visibility != View.VISIBLE) return
        val width = binding.pagesPreviewRecycler.width.toFloat().coerceAtLeast(1f)
        val alpha = 1f - (binding.pagesPreviewRecycler.translationX / width).coerceIn(0f, 1f)
        binding.pagesPreviewScrimTop.alpha = alpha
        binding.pagesPreviewScrimBottom.alpha = alpha
    }


    fun animatePreviewScrimAlpha(alpha: Float, duration: Long) {
        if (binding.pagesPreviewScrimTop.visibility != View.VISIBLE) return
        binding.pagesPreviewScrimTop.animate().cancel()
        binding.pagesPreviewScrimBottom.animate().cancel()
        binding.pagesPreviewScrimTop.animate().alpha(alpha).setDuration(duration).start()
        binding.pagesPreviewScrimBottom.animate().alpha(alpha).setDuration(duration).start()
    }


    fun hidePreviewScrims() {
        binding.pagesPreviewScrimTop.animate().cancel()
        binding.pagesPreviewScrimBottom.animate().cancel()
        binding.pagesPreviewScrimTop.visibility = View.GONE
        binding.pagesPreviewScrimBottom.visibility = View.GONE
    }


    fun onPreviewOpened() {
        previewOpen = true
        controller.manga?.id?.let { controller.presenter.preferences.markDetailSwipeHintShown(it) }
        binding.pagesPreviewScrimTop.apply {
            visibility = View.VISIBLE
            alpha = 1f
        }
        binding.pagesPreviewScrimBottom.apply {
            visibility = View.VISIBLE
            alpha = 1f
        }
        binding.fab.hide()
        binding.scrollToTop.hide()
        binding.fastScroller.isVisible = false
        binding.swipeRefresh.isEnabled = false
    }


    fun onPreviewClosed() {
        previewOpen = false


        binding.pagesPreviewRecycler.visibility = View.GONE
        hidePreviewScrims()
        binding.swipeRefresh.isEnabled = true
        binding.fastScroller.isVisible = true
        controller.updateFab()
        if (binding.fab.isEnabled) controller.updateFabVisibility()
        controller.updateScrollToTopVisibility()
    }


    fun startsOnSwipeableChapter(recycler: RecyclerView, event: MotionEvent): Boolean {
        // Chapter rows already handle horizontal swipes (bookmark/read), so leave those to the helper.
        // Rows that can't be swiped don't block the panel swipe, otherwise the gesture would be dead.
        val adapter = controller.adapter ?: return false
        val child = recycler.findChildViewUnder(event.x, event.y) ?: return false
        val position = recycler.getChildAdapterPosition(child)
        if (position == RecyclerView.NO_POSITION) return false
        return (adapter.getItem(position) as? ChapterItem)?.isSwipeable() == true
    }



    companion object {
        const val DEFAULT_GRID_SPAN_COUNT = 3
        const val PANEL_SWIPE_THRESHOLD = 0.05f
        const val PANEL_FLING_VELOCITY = 220f
        const val PANEL_ANIM_MS = 240L
        const val PANEL_ANIM_MIN_MS = 100L
        const val PANEL_HINT_DELAY_MS = 700L
        const val PANEL_WARM_DELAY_MS = 1600L
        const val PANEL_WARM_SETTLE_MS = 500L
        const val PANEL_HINT_PEEK = 0.18f
        const val PANEL_HINT_IN_MS = 320L
        const val PANEL_HINT_HOLD_MS = 180L
        const val PANEL_HINT_OUT_MS = 260L
    }
}