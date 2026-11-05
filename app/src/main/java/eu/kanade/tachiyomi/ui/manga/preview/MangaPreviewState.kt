package eu.kanade.tachiyomi.ui.manga.preview

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.manga.preview.chapter.ChapterPagesSectionItem

/**
 * Mutable state for the page preview grid. Keeping it in one object makes the adapter's lifecycle
 * and dataset state explicit without changing how the preview behaves.
 */
class MangaPreviewState {
    var pageGridMode: Boolean = false
    var suppressPageLoads: Boolean = false

    val pageSections: HashMap<Long, ChapterPagesSectionItem> = HashMap()
    val chapterPages: HashMap<Long, List<Page>> = HashMap()

    var currentPage: Pair<Long, Int>? = null
    var anchorChapterId: Long? = null
    var pendingScrollToCurrent: Boolean = false
    var landingPending: Boolean = false
    var priorityChapterId: Long? = null
}
