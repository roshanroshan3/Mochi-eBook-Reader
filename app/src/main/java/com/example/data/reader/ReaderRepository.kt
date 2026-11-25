package com.example.data.reader

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.db.BookDao
import com.example.data.db.BookEntity
import com.example.data.db.BookSettingsDao
import com.example.data.db.BookSettingsEntity
import com.example.data.db.PositionDao
import com.example.data.db.PositionEntity
import com.example.data.db.ReadingStatus
import com.example.data.preferences.ImportedFontInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object ReaderDocumentCache {
    private val docCache = android.util.LruCache<String, ParsedDocument>(2)

    private val maxImageKb = (Runtime.getRuntime().maxMemory() / 1024L / 14L).toInt().coerceIn(6 * 1024, 24 * 1024)
    private val imageCache = object : android.util.LruCache<String, android.graphics.Bitmap>(maxImageKb) {
        override fun sizeOf(key: String, value: android.graphics.Bitmap): Int =
            (value.byteCount / 1024).coerceAtLeast(1)
    }

    fun docKey(book: BookEntity): String = "${book.id}_${book.fileSizeBytes}_${book.uri}"

    fun getDocument(book: BookEntity): ParsedDocument? = docCache.get(docKey(book))

    fun putDocument(book: BookEntity, doc: ParsedDocument) {
        docCache.put(docKey(book), doc)
    }

    fun imageCacheKey(bookUri: String, imageKey: String, maxDimensionPx: Int): String =
        "${bookUri}_${imageKey}_$maxDimensionPx"

    suspend fun loadAndDecodeImageBlock(
        context: Context,
        bookUri: String,
        inlineImages: Map<String, ByteArray>,
        imageKey: String,
        maxDimensionPx: Int = 1280
    ): android.graphics.Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = imageCacheKey(bookUri, imageKey, maxDimensionPx)
        imageCache.get(cacheKey)?.let { bmp ->
            if (!bmp.isRecycled) return@withContext bmp
        }
        val inlineBytes = inlineImages[imageKey] ?: inlineImages[imageKey.substringAfterLast('/')]
        if (inlineBytes != null) {
            return@withContext decodeImageBlock(cacheKey, inlineBytes, maxDimensionPx)
        }
        if (bookUri.isBlank()) return@withContext null
        val streamedBytes = readZipEntryBytes(context, bookUri, imageKey) ?: return@withContext null
        decodeImageBlock(cacheKey, streamedBytes, maxDimensionPx)
    }

    suspend fun prewarmImageBlocks(
        context: Context,
        bookUri: String,
        inlineImages: Map<String, ByteArray>,
        imageKeys: List<String>,
        maxDimensionPx: Int = 1280
    ) = withContext(Dispatchers.IO) {
        imageKeys.distinct().forEach { key ->
            loadAndDecodeImageBlock(context, bookUri, inlineImages, key, maxDimensionPx)
        }
    }

    private fun readZipEntryBytes(context: Context, bookUri: String, imageKey: String): ByteArray? = runCatching {
        val uri = Uri.parse(bookUri)
        val fileNameOnly = imageKey.substringAfterLast('/')
        if (uri.scheme == "file" && uri.path != null) {
            val file = File(uri.path!!)
            if (!file.exists()) return@runCatching null
            java.util.zip.ZipFile(file).use { zf ->
                val direct = zf.getEntry(imageKey) ?: zf.entries().asSequence().firstOrNull { e ->
                    !e.isDirectory && (e.name.equals(imageKey, ignoreCase = true) ||
                        e.name.substringAfterLast('/').equals(fileNameOnly, ignoreCase = true))
                }
                if (direct != null) zf.getInputStream(direct).use { it.readBytes() } else null
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use { raw ->
                java.util.zip.ZipInputStream(java.io.BufferedInputStream(raw)).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory && (entry.name.equals(imageKey, ignoreCase = true) ||
                                entry.name.substringAfterLast('/').equals(fileNameOnly, ignoreCase = true))
                        ) {
                            return@use zip.readBytes()
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                    null
                }
            }
        }
    }.getOrNull()

    suspend fun decodeImageBlock(
        cacheKey: String,
        bytes: ByteArray,
        maxDimensionPx: Int = 1280
    ): android.graphics.Bitmap? = withContext(Dispatchers.IO) {
        imageCache.get(cacheKey)?.let { bmp ->
            if (!bmp.isRecycled) return@withContext bmp
        }
        runCatching {
            val bounds = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
            while (maxDim / sample > maxDimensionPx) {
                sample *= 2
            }
            val opts = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
            }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.also { decoded ->
                imageCache.put(cacheKey, decoded)
            }
        }.getOrNull()
    }

    fun getCachedImageBlock(cacheKey: String): android.graphics.Bitmap? =
        imageCache.get(cacheKey)?.takeIf { !it.isRecycled }

    @Suppress("DEPRECATION")
    fun trimMemory(level: Int) {
        when {
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE ||
                level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                docCache.evictAll()
                imageCache.evictAll()
            }
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ||
                level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                docCache.trimToSize(1)
                imageCache.trimToSize(maxImageKb / 2)
            }
        }
    }
}

class ReaderRepository(
    private val context: Context,
    private val bookDao: BookDao,
    private val positionDao: PositionDao,
    private val bookSettingsDao: BookSettingsDao
) {
    suspend fun getBook(bookId: Long): BookEntity? = withContext(Dispatchers.IO) {
        bookDao.getBookById(bookId)
    }

    suspend fun loadDocument(book: BookEntity): ParsedDocument = withContext(Dispatchers.IO) {
        ReaderDocumentCache.getDocument(book)?.let { return@withContext it }
        val allBooks = bookDao.getAllBooksOnce()
        DocumentParsers.parseBook(context, book, allBooks).also { parsed ->
            ReaderDocumentCache.putDocument(book, parsed)
        }
    }

    suspend fun getSavedLocator(bookId: Long): ReaderLocator? = withContext(Dispatchers.IO) {
        positionDao.getPosition(bookId)?.let { ReaderLocator.fromJson(it.locatorJson) }
    }

    suspend fun savePosition(bookId: Long, locator: ReaderLocator) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        positionDao.upsertPosition(
            PositionEntity(
                bookId = bookId,
                locatorJson = ReaderLocator.toJson(locator),
                updatedAt = now
            )
        )
        val status = when {
            locator.progression >= 0.99f -> ReadingStatus.FINISHED
            locator.progression > 0f -> ReadingStatus.READING
            else -> ReadingStatus.READING
        }
        bookDao.updateReadingProgress(
            bookId = bookId,
            progress = locator.progression.coerceIn(0f, 1f),
            lastOpenedAt = now,
            status = status
        )
    }

    fun observeBookSettings(bookId: Long): Flow<BookSettingsEntity?> =
        bookSettingsDao.observeBookSettings(bookId)

    suspend fun setBookThemeOverride(bookId: Long, themeId: String?) = withContext(Dispatchers.IO) {
        val existing = bookSettingsDao.getBookSettings(bookId)
        if (themeId == null && existing?.fontOverride == null && existing?.layoutOverrideJson == null) {
            bookSettingsDao.clearBookSettings(bookId)
        } else {
            bookSettingsDao.upsertBookSettings(
                BookSettingsEntity(
                    bookId = bookId,
                    themeId = themeId,
                    fontOverride = existing?.fontOverride,
                    layoutOverrideJson = existing?.layoutOverrideJson
                )
            )
        }
    }

    suspend fun importFontFromSaf(uri: Uri): ImportedFontInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val fontsDir = File(context.filesDir, "fonts").apply { mkdirs() }
            var displayName = "Custom Font"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) displayName = cursor.getString(idx) ?: displayName
                }
            }
            val cleanName = displayName.substringBeforeLast('.')
            val ext = if (displayName.endsWith(".otf", true)) "otf" else "ttf"
            val id = "font_${UUID.randomUUID().toString().take(8)}"
            val dest = File(fontsDir, "$id.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { out -> input.copyTo(out) }
            } ?: return@withContext null

            ImportedFontInfo(
                id = id,
                displayName = cleanName,
                filePath = dest.absolutePath
            )
        }.getOrNull()
    }
}
