package eu.kanade.tachiyomi.ui.manga.preview

import eu.kanade.tachiyomi.ui.manga.preview.chapter.ChapterPagesSectionItem
import eu.kanade.tachiyomi.util.system.withUIContext
import kotlinx.coroutines.launch

/**
 * Loads a chapter page list for the preview grid. The adapter still owns the resulting dataset
 * changes, so loading stays a separate responsibility from rendering state.
 */
class MangaPreviewLoader(
    private val adapter: MangaPreviewAdapter,
) {
    fun load(section: ChapterPagesSectionItem) {
        adapter.presenter.presenterScope.launch {
            val pages = adapter.presenter.loadPageList(section.chapter)
            withUIContext {
                adapter.onPagesLoaded(section, pages, attachItems = !adapter.isScrolling)
            }
        }
    }
}
