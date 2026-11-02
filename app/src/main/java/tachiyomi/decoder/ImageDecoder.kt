package tachiyomi.decoder

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.Build
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import org.aomedia.avif.android.AvifDecoder

private const val WHITE_THRESHOLD = 0xF0
private const val CROP_DETECT_MAX_SIZE = 1024

/**
 * Compatibility replacement for the image-decoder library's `ImageDecoder`, which is
 * required at runtime by the subsampling-scale-image-view fork.
 *
 * Decoding is delegated to the platform ([BitmapRegionDecoder]) and libavif is used for
 * AVIF. JXL and HEIF container variants that older Android versions cannot decode are
 * no longer supported.
 */
class ImageDecoder private constructor(
    private val regionDecoder: BitmapRegionDecoder?,
    private val fullBitmap: Bitmap?,
    private val crop: Rect,
    private var tempFile: File?,
    val width: Int,
    val height: Int,
) {
    private val lock = Any()
    private var recycled = false

    fun decode(sRect: Rect, sampleSize: Int): Bitmap? {
        val sample = if (sampleSize < 1) 1 else sampleSize
        synchronized(lock) {
            if (recycled) return null

            regionDecoder?.let { decoder ->
                val rect = Rect(
                    crop.left + sRect.left,
                    crop.top + sRect.top,
                    crop.left + sRect.right,
                    crop.top + sRect.bottom,
                )
                if (!rect.intersect(0, 0, decoder.width, decoder.height) || rect.isEmpty) {
                    return null
                }
                return try {
                    decoder.decodeRegion(
                        rect,
                        BitmapFactory.Options().apply { inSampleSize = sample },
                    )
                } catch (_: IllegalArgumentException) {
                    null
                }
            }

            val bitmap = fullBitmap ?: return null
            val left = (crop.left + sRect.left).coerceIn(crop.left, crop.right)
            val top = (crop.top + sRect.top).coerceIn(crop.top, crop.bottom)
            val right = (crop.left + sRect.right).coerceIn(left, crop.right)
            val bottom = (crop.top + sRect.bottom).coerceIn(top, crop.bottom)
            if (right <= left || bottom <= top) return null

            val region = try {
                Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
            } catch (_: IllegalArgumentException) {
                return null
            }
            if (sample == 1) return region

            val scaled = Bitmap.createScaledBitmap(
                region,
                (region.width + sample - 1) / sample,
                (region.height + sample - 1) / sample,
                true,
            )
            if (region !== bitmap && region !== scaled) region.recycle()
            return scaled
        }
    }

    fun isRecycled(): Boolean = synchronized(lock) { recycled }

    fun recycle() {
        synchronized(lock) {
            if (recycled) return
            recycled = true
            regionDecoder?.recycle()
            fullBitmap?.recycle()
            tempFile?.delete()
            tempFile = null
        }
    }

    @Suppress("deprecation")
    protected fun finalize() {
        recycle()
    }

    companion object {
        fun newInstance(
            stream: InputStream,
            cropBorders: Boolean,
            @Suppress("UNUSED_PARAMETER") displayProfile: ByteArray?,
        ): ImageDecoder? {
            val bytes = try {
                stream.readBytes()
            } catch (_: Exception) {
                return null
            }
            if (bytes.isEmpty()) return null

            val buffer = ByteBuffer.allocateDirect(bytes.size).put(bytes).apply { rewind() }
            val isAvif = try {
                AvifDecoder.isAvifImage(buffer)
            } catch (_: Throwable) {
                false
            }
            return if (isAvif) {
                createAvif(buffer, cropBorders)
            } else {
                createPlatform(bytes, cropBorders)
            }
        }

        private fun createAvif(buffer: ByteBuffer, cropBorders: Boolean): ImageDecoder? {
            val decoder = try {
                AvifDecoder.create(buffer)
            } catch (_: Exception) {
                null
            } ?: return null
            try {
                val bitmap = Bitmap.createBitmap(decoder.width, decoder.height, Bitmap.Config.ARGB_8888)
                if (decoder.nextFrame(bitmap) != 0) {
                    bitmap.recycle()
                    return null
                }
                val crop = if (cropBorders) detectCropRect(bitmap) else Rect(0, 0, bitmap.width, bitmap.height)
                return ImageDecoder(null, bitmap, crop, null, crop.width(), crop.height())
            } finally {
                decoder.release()
            }
        }

        private fun createPlatform(bytes: ByteArray, cropBorders: Boolean): ImageDecoder? {
            var tempFile: File? = null
            val decoder = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    BitmapRegionDecoder.newInstance(bytes, 0, bytes.size, false)
                } else {
                    val file = File.createTempFile("tachiyomi-decoder", null)
                    file.writeBytes(bytes)
                    tempFile = file
                    BitmapRegionDecoder.newInstance(file.absolutePath, false)
                }
            } catch (_: Exception) {
                tempFile?.delete()
                return null
            } ?: return null

            val fullBounds = Rect(0, 0, decoder.width, decoder.height)
            val crop = if (cropBorders) detectCropRect(bytes, fullBounds) else fullBounds
            return ImageDecoder(decoder, null, crop, tempFile, crop.width(), crop.height())
        }

        private fun detectCropRect(bytes: ByteArray, bounds: Rect): Rect {
            val maxDim = maxOf(bounds.width(), bounds.height())
            var sample = 1
            while (maxDim / sample > CROP_DETECT_MAX_SIZE) sample *= 2

            val sampled = try {
                BitmapFactory.decodeByteArray(
                    bytes,
                    0,
                    bytes.size,
                    BitmapFactory.Options().apply { inSampleSize = sample },
                )
            } catch (_: Exception) {
                null
            } ?: return bounds
            val sampledCrop = detectCropRect(sampled)
            val scaleX = bounds.width().toFloat() / sampled.width
            val scaleY = bounds.height().toFloat() / sampled.height
            sampled.recycle()

            val left = (sampledCrop.left * scaleX).toInt().coerceIn(0, bounds.width() - 1)
            val top = (sampledCrop.top * scaleY).toInt().coerceIn(0, bounds.height() - 1)
            val right = (sampledCrop.right * scaleX + 0.999f).toInt().coerceIn(left + 1, bounds.width())
            val bottom = (sampledCrop.bottom * scaleY + 0.999f).toInt().coerceIn(top + 1, bounds.height())
            return Rect(left, top, right, bottom)
        }

        private fun detectCropRect(bitmap: Bitmap): Rect {
            val width = bitmap.width
            val height = bitmap.height
            if (width == 0 || height == 0) return Rect(0, 0, width, height)

            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            fun rowIsWhite(y: Int): Boolean {
                val offset = y * width
                for (x in 0 until width) {
                    if (!isWhite(pixels[offset + x])) return false
                }
                return true
            }

            var top = 0
            while (top < height && rowIsWhite(top)) top++
            if (top == height) return Rect(0, 0, width, height)

            var bottom = height - 1
            while (bottom > top && rowIsWhite(bottom)) bottom--

            fun columnIsWhite(x: Int): Boolean {
                for (y in top..bottom) {
                    if (!isWhite(pixels[y * width + x])) return false
                }
                return true
            }

            var left = 0
            while (left < width && columnIsWhite(left)) left++
            var right = width - 1
            while (right > left && columnIsWhite(right)) right--

            if (left >= right || top >= bottom) return Rect(0, 0, width, height)
            return Rect(left, top, right + 1, bottom + 1)
        }

        private fun isWhite(color: Int): Boolean {
            return (color ushr 16 and 0xFF) >= WHITE_THRESHOLD &&
                (color ushr 8 and 0xFF) >= WHITE_THRESHOLD &&
                (color and 0xFF) >= WHITE_THRESHOLD
        }
    }
}
