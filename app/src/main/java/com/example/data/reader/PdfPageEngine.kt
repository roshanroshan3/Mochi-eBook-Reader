package com.example.data.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

data class PdfDocumentSummary(
    val pageCount: Int,
    val pageSizes: List<Pair<Int, Int>>
)

/**
 * Thread-safe PDF rasterization engine backed by Android's native [PdfRenderer].
 * - Serializes native PdfRenderer page opens via a coroutine [Mutex].
 * - Maintains a memory-bounded LRU Bitmap cache (keyed by bookUri + pageIndex + bucket width).
 * - Supports non-destructive auto-margin crop detection, smart dark-mode inversion, and
 *   column/content focus detection for smart double-tap zoom.
 */
object PdfPageEngine {

    private val rendererMutex = Mutex()
    private val thumbMutex = Mutex()

    private val maxCacheKb = (Runtime.getRuntime().maxMemory() / 1024L / 7L).toInt().coerceIn(12 * 1024, 56 * 1024)
    private val bitmapCache = object : LruCache<String, Bitmap>(maxCacheKb) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
    }

    private val maxThumbKb = 8 * 1024
    private val thumbnailCache = object : LruCache<String, Bitmap>(maxThumbKb) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
    }

    private val cropBoundsCache = LruCache<String, RectF>(256)

    fun bucketWidth(targetWidthPx: Int): Int =
        ((targetWidthPx.coerceIn(280, 2200) + 127) / 128) * 128

    private fun pageCacheKey(uriString: String, pageIndex: Int, widthBucket: Int, smartDarkInvert: Boolean): String =
        "${uriString}_${pageIndex}_${widthBucket}_${if (smartDarkInvert) 1 else 0}"

    fun getCachedBitmap(
        uriString: String,
        pageIndex: Int,
        targetWidthPx: Int,
        smartDarkInvert: Boolean = false
    ): Bitmap? {
        val exactKey = pageCacheKey(uriString, pageIndex, bucketWidth(targetWidthPx), smartDarkInvert)
        bitmapCache.get(exactKey)?.let { bmp ->
            if (!bmp.isRecycled) return bmp
        }
        // Fallback to any cached resolution for immediate display while crisp zoom re-renders
        val prefix = "${uriString}_${pageIndex}_"
        val suffix = "_${if (smartDarkInvert) 1 else 0}"
        return bitmapCache.snapshot().entries.firstOrNull { (k, bmp) ->
            k.startsWith(prefix) && k.endsWith(suffix) && !bmp.isRecycled
        }?.value
    }

    fun getCachedThumbnail(
        uriString: String,
        pageIndex: Int,
        smartDarkInvert: Boolean = false
    ): Bitmap? = thumbnailCache.get("thumb_${uriString}_${pageIndex}_${if (smartDarkInvert) 1 else 0}")
        ?.takeIf { !it.isRecycled }

    fun getCachedCropBounds(uriString: String, pageIndex: Int): RectF? =
        cropBoundsCache.get("${uriString}_$pageIndex")

    @Suppress("DEPRECATION")
    fun trimMemory(level: Int) {
        when {
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE ||
                level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                bitmapCache.evictAll()
                thumbnailCache.evictAll()
            }
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ||
                level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                bitmapCache.trimToSize(maxCacheKb / 2)
                thumbnailCache.trimToSize(maxThumbKb / 2)
            }
        }
    }

    fun openFileDescriptor(context: Context, uriString: String): ParcelFileDescriptor? {
        val uri = Uri.parse(uriString)
        return when {
            uri.scheme == "file" && uri.path != null -> {
                val f = File(uri.path!!)
                if (f.exists()) ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY) else null
            }
            uri.scheme == null && uriString.startsWith("/") -> {
                val f = File(uriString)
                if (f.exists()) ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY) else null
            }
            else -> context.contentResolver.openFileDescriptor(uri, "r")
        }
    }

    suspend fun inspectPdf(context: Context, uriString: String): PdfDocumentSummary = withContext(Dispatchers.IO) {
        rendererMutex.withLock {
            val pfd = openFileDescriptor(context, uriString)
                ?: throw IllegalStateException("PDF file is no longer accessible.")
            pfd.use { fd ->
                PdfRenderer(fd).use { renderer ->
                    val count = renderer.pageCount
                    if (count <= 0) {
                        throw IllegalStateException("PDF contains zero readable pages.")
                    }
                    val sizes = ArrayList<Pair<Int, Int>>(count)
                    for (i in 0 until count) {
                        renderer.openPage(i).use { page ->
                            sizes.add(page.width to page.height)
                        }
                    }
                    PdfDocumentSummary(pageCount = count, pageSizes = sizes)
                }
            }
        }
    }

    suspend fun renderPageBitmap(
        context: Context,
        uriString: String,
        pageIndex: Int,
        targetWidthPx: Int,
        smartDarkInvert: Boolean = false
    ): Bitmap? = withContext(Dispatchers.IO) {
        val widthBucket = bucketWidth(targetWidthPx)
        val cacheKey = pageCacheKey(uriString, pageIndex, widthBucket, smartDarkInvert)
        bitmapCache.get(cacheKey)?.let { cached ->
            if (!cached.isRecycled) return@withContext cached
        }

        rendererMutex.withLock {
            bitmapCache.get(cacheKey)?.let { cached ->
                if (!cached.isRecycled) return@withLock cached
            }
            renderPageInternal(context, uriString, pageIndex, widthBucket, smartDarkInvert)?.also { finalBmp ->
                bitmapCache.put(cacheKey, finalBmp)
            }
        }
    }

    suspend fun renderThumbnailBitmap(
        context: Context,
        uriString: String,
        pageIndex: Int,
        smartDarkInvert: Boolean = false,
        thumbWidthPx: Int = 220
    ): Bitmap? = withContext(Dispatchers.IO) {
        val key = "thumb_${uriString}_${pageIndex}_${if (smartDarkInvert) 1 else 0}"
        thumbnailCache.get(key)?.let { cached ->
            if (!cached.isRecycled) return@withContext cached
        }
        thumbMutex.withLock {
            thumbnailCache.get(key)?.let { cached ->
                if (!cached.isRecycled) return@withLock cached
            }
            rendererMutex.withLock {
                renderPageInternal(
                    context = context,
                    uriString = uriString,
                    pageIndex = pageIndex,
                    targetWidthPx = thumbWidthPx.coerceIn(140, 280),
                    smartDarkInvert = smartDarkInvert
                )?.also { bmp ->
                    thumbnailCache.put(key, bmp)
                }
            }
        }
    }

    private fun renderPageInternal(
        context: Context,
        uriString: String,
        pageIndex: Int,
        targetWidthPx: Int,
        smartDarkInvert: Boolean
    ): Bitmap? {
        val pfd = openFileDescriptor(context, uriString) ?: return null
        return pfd.use { fd ->
            PdfRenderer(fd).use { renderer ->
                if (pageIndex !in 0 until renderer.pageCount) return@use null
                renderer.openPage(pageIndex).use { page ->
                    val aspect = page.height.toFloat() / page.width.coerceAtLeast(1).toFloat()
                    val w = targetWidthPx.coerceIn(140, 2200)
                    val h = (w * aspect).roundToInt().coerceIn(160, 3200)

                    val rawBmp = allocateBitmapWithRecovery(w, h) ?: return@use null
                    Canvas(rawBmp).drawColor(Color.WHITE)
                    page.render(rawBmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    if (smartDarkInvert) {
                        val inverted = applySmartDarkInvert(rawBmp)
                        if (inverted !== rawBmp && !rawBmp.isRecycled) {
                            rawBmp.recycle()
                        }
                        inverted
                    } else {
                        rawBmp
                    }
                }
            }
        }
    }

    private fun allocateBitmapWithRecovery(width: Int, height: Int): Bitmap? {
        return try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } catch (_: OutOfMemoryError) {
            bitmapCache.trimToSize(maxCacheKb / 4)
            thumbnailCache.evictAll()
            System.gc()
            try {
                Bitmap.createBitmap((width * 0.75f).roundToInt(), (height * 0.75f).roundToInt(), Bitmap.Config.ARGB_8888)
            } catch (_: OutOfMemoryError) {
                null
            }
        }
    }

    suspend fun getOrComputeContentBounds(
        uriString: String,
        pageIndex: Int,
        bitmap: Bitmap
    ): RectF = withContext(Dispatchers.Default) {
        val key = "${uriString}_$pageIndex"
        cropBoundsCache.get(key)?.let { return@withContext it }
        detectNormalizedContentBounds(bitmap).also { rect ->
            cropBoundsCache.put(key, rect)
        }
    }

    /**
     * Produces a dark-mode friendly inversion with warm charcoal paper & preserved hue readability.
     */
    fun applySmartDarkInvert(source: Bitmap): Bitmap {
        val out = allocateBitmapWithRecovery(source.width, source.height) ?: return source
        val canvas = Canvas(out)
        val invertMatrix = ColorMatrix(
            floatArrayOf(
                -0.88f, 0f, 0f, 0f, 240f,
                0f, -0.88f, 0f, 0f, 238f,
                0f, 0f, -0.85f, 0f, 232f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(invertMatrix)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return out
    }

    /**
     * Scans a downsampled version of the page bitmap to compute tight normalized content bounds
     * (`0f..1f` LTRB) for Auto Margin Crop or smart double-tap column zooming.
     */
    fun detectNormalizedContentBounds(bitmap: Bitmap): RectF {
        if (bitmap.isRecycled) return RectF(0f, 0f, 1f, 1f)
        val w = bitmap.width
        val h = bitmap.height
        if (w < 16 || h < 16) return RectF(0f, 0f, 1f, 1f)

        val stepX = (w / 64).coerceAtLeast(1)
        val stepY = (h / 64).coerceAtLeast(1)
        var minX = w
        var maxX = 0
        var minY = h
        var maxY = 0
        var foundInk = false

        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val lum = (r * 299 + g * 587 + b * 114) / 1000
                if (lum < 242) {
                    foundInk = true
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
                x += stepX
            }
            y += stepY
        }

        if (!foundInk || maxX <= minX || maxY <= minY) return RectF(0f, 0f, 1f, 1f)

        val padX = (w * 0.03f)
        val padY = (h * 0.03f)
        return RectF(
            ((minX - padX) / w).coerceIn(0f, 0.35f),
            ((minY - padY) / h).coerceIn(0f, 0.35f),
            ((maxX + padX) / w).coerceIn(0.65f, 1f),
            ((maxY + padY) / h).coerceIn(0.65f, 1f)
        )
    }
}
