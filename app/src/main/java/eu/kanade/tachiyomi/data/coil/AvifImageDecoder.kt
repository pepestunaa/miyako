package eu.kanade.tachiyomi.data.coil

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.DecodeUtils
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.request.maxBitmapSize
import coil3.util.component1
import coil3.util.component2
import java.nio.ByteBuffer
import kotlinx.coroutines.runInterruptible
import okio.BufferedSource
import okio.EOFException
import org.aomedia.avif.android.AvifDecoder

/**
 * A [Decoder] that uses libavif to decode AVIF images, which the platform only supports from Android 12.
 */
class AvifImageDecoder(
    private val source: ImageSource,
    private val options: Options,
) : Decoder {

    override suspend fun decode(): DecodeResult = runInterruptible {
        val bytes = source.source().readByteBuffer()
        val decoder = AvifDecoder.create(bytes) ?: error("Failed to initialize decoder.")
        try {
            // libavif only supports RGBA_8888 output bitmaps.
            val bitmap = createBitmap(decoder.width, decoder.height, Bitmap.Config.ARGB_8888)
            val result = decoder.nextFrame(bitmap)
            if (result != 0) {
                bitmap.recycle()
                error(AvifDecoder.resultToString(result))
            }

            val (dstWidth, dstHeight) = DecodeUtils.computeDstSize(
                srcWidth = bitmap.width,
                srcHeight = bitmap.height,
                targetSize = options.size,
                scale = options.scale,
                maxSize = options.maxBitmapSize,
            )
            if (dstWidth < bitmap.width || dstHeight < bitmap.height) {
                val scaled = bitmap.scale(dstWidth, dstHeight)
                bitmap.recycle()
                DecodeResult(image = scaled.asImage(), isSampled = true)
            } else {
                DecodeResult(image = bitmap.asImage(), isSampled = false)
            }
        } finally {
            decoder.release()
        }
    }

    class Factory : Decoder.Factory {
        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? {
            return if (result.mimeType == AVIF_MIME || result.source.source().peek().isAvif()) {
                AvifImageDecoder(result.source, options)
            } else {
                null
            }
        }

        override fun equals(other: Any?) = other is Factory

        override fun hashCode() = javaClass.hashCode()

        private fun BufferedSource.isAvif(): Boolean {
            val header = try {
                peek().readByteArray(32)
            } catch (_: EOFException) {
                return false
            }
            if (header[4] != FTYP[0] || header[5] != FTYP[1] ||
                header[6] != FTYP[2] || header[7] != FTYP[3]
            ) {
                return false
            }
            var offset = 8
            while (offset + 4 <= header.size) {
                val brand = String(header, offset, 4, Charsets.US_ASCII)
                if (brand == "avif" || brand == "avis") return true
                offset += 4
            }
            return false
        }

        companion object {
            private const val AVIF_MIME = "image/avif"
            private val FTYP = "ftyp".toByteArray(Charsets.US_ASCII)
        }
    }
}

private fun BufferedSource.readByteBuffer(): ByteBuffer {
    val bytes = readByteArray()
    return ByteBuffer.allocateDirect(bytes.size).put(bytes).apply { rewind() }
}
