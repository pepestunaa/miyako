package eu.kanade.tachiyomi.data.coil

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import okio.FileSystem

data class ChapterPageImage(
    val chapter: Chapter,
    val page: Page,
    val source: HttpSource,
)

class ChapterPageFetcher(
    private val data: ChapterPageImage,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val page = data.page
        if (page.imageUrl.isNullOrEmpty()) {
            page.imageUrl = data.source.getImageUrl(page)
        }
        val response = data.source.getImage(page)
        val body = checkNotNull(response.body) { "Null response body" }
        return SourceFetchResult(
            source = ImageSource(
                source = body.source(),
                fileSystem = FileSystem.SYSTEM,
            ),
            mimeType = body.contentType()?.toString(),
            dataSource = if (response.cacheResponse != null) DataSource.DISK else DataSource.NETWORK,
        )
    }

    class Factory : Fetcher.Factory<ChapterPageImage> {
        override fun create(data: ChapterPageImage, options: Options, imageLoader: ImageLoader): Fetcher {
            return ChapterPageFetcher(data, options)
        }
    }
}

class ChapterPageKeyer : Keyer<ChapterPageImage> {
    override fun key(data: ChapterPageImage, options: Options): String {
        return "chapter-page:${data.source.id}:${data.chapter.id}:${data.page.index}:${data.page.url}"
    }
}
