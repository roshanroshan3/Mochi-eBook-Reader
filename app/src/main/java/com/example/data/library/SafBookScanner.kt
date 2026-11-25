package com.example.data.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Xml
import androidx.documentfile.provider.DocumentFile
import com.example.data.db.BookEntity
import com.example.data.db.BookFormat
import com.example.data.db.ReadingStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

data class ScannedBookMetadata(
    val title: String,
    val authors: String,
    val series: String? = null,
    val seriesIndex: Float? = null,
    val pageCountEstimate: Int = 220,
    val coverPath: String? = null
)

object SafBookScanner {

    fun computeSha256(streamProvider: () -> InputStream?): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(16 * 1024)
        streamProvider()?.use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    suspend fun scanSafTree(
        context: Context,
        treeUri: Uri,
        existingBooksByUri: Map<String, BookEntity>,
        nextShelfOrderStart: Int
    ): List<BookEntity> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
        val found = mutableListOf<BookEntity>()
        var order = nextShelfOrderStart

        fun walk(dir: DocumentFile, relPath: String) {
            for (child in dir.listFiles()) {
                val childName = child.name ?: continue
                if (child.isDirectory) {
                    val nextRel = if (relPath.isEmpty()) childName else "$relPath/$childName"
                    walk(child, nextRel)
                } else if (child.isFile) {
                    val format = BookFormat.fromFilename(childName) ?: continue
                    val uriStr = child.uri.toString()
                    val existing = existingBooksByUri[uriStr]
                    if (existing != null && existing.fileSizeBytes == child.length()) {
                        found.add(existing.copy(relativeFolderPath = relPath))
                        continue
                    }

                    val hash = computeSha256 { context.contentResolver.openInputStream(child.uri) }
                    val meta = extractMetadataAndCover(
                        context = context,
                        filename = childName,
                        format = format,
                        contentHash = hash,
                        fileSize = child.length(),
                        streamProvider = { context.contentResolver.openInputStream(child.uri) },
                        pfdProvider = { context.contentResolver.openFileDescriptor(child.uri, "r") }
                    )
                    found.add(
                        BookEntity(
                            id = existing?.id ?: 0L,
                            uri = uriStr,
                            contentHash = hash,
                            title = meta.title,
                            authors = meta.authors,
                            series = meta.series,
                            seriesIndex = meta.seriesIndex,
                            format = format,
                            coverPath = meta.coverPath,
                            addedAt = existing?.addedAt ?: System.currentTimeMillis(),
                            lastOpenedAt = existing?.lastOpenedAt,
                            progress = existing?.progress ?: 0f,
                            status = existing?.status ?: ReadingStatus.UNREAD,
                            shelfOrder = existing?.shelfOrder ?: order++,
                            fileSizeBytes = child.length(),
                            pageCountEstimate = meta.pageCountEstimate,
                            folderTreeUri = treeUri.toString(),
                            relativeFolderPath = relPath,
                            hidden = existing?.hidden ?: false,
                            stackId = existing?.stackId,
                            stackTitle = existing?.stackTitle,
                            isSample = false
                        )
                    )
                }
            }
        }

        walk(root, "")
        found
    }

    suspend fun scanSingleDocument(
        context: Context,
        uri: Uri,
        displayName: String,
        fileSize: Long,
        existing: BookEntity?,
        shelfOrder: Int
    ): BookEntity? = withContext(Dispatchers.IO) {
        val format = BookFormat.fromFilename(displayName) ?: return@withContext null
        val hash = computeSha256 { context.contentResolver.openInputStream(uri) }
        val meta = extractMetadataAndCover(
            context = context,
            filename = displayName,
            format = format,
            contentHash = hash,
            fileSize = fileSize,
            streamProvider = { context.contentResolver.openInputStream(uri) },
            pfdProvider = { context.contentResolver.openFileDescriptor(uri, "r") }
        )
        BookEntity(
            id = existing?.id ?: 0L,
            uri = uri.toString(),
            contentHash = hash,
            title = meta.title,
            authors = meta.authors,
            series = meta.series,
            seriesIndex = meta.seriesIndex,
            format = format,
            coverPath = meta.coverPath,
            addedAt = existing?.addedAt ?: System.currentTimeMillis(),
            lastOpenedAt = existing?.lastOpenedAt,
            progress = existing?.progress ?: 0f,
            status = existing?.status ?: ReadingStatus.UNREAD,
            shelfOrder = existing?.shelfOrder ?: shelfOrder,
            fileSizeBytes = fileSize,
            pageCountEstimate = meta.pageCountEstimate,
            folderTreeUri = "imported://documents",
            relativeFolderPath = "",
            hidden = false,
            isSample = false
        )
    }

    suspend fun scanLocalBookFile(
        context: Context,
        file: File,
        existing: BookEntity?,
        shelfOrder: Int,
        folderTreeUri: String = "imported://documents",
        relativeFolderPath: String = "",
        fallbackTitle: String? = null,
        fallbackAuthors: String? = null,
        coverPathOverride: String? = null
    ): BookEntity = withContext(Dispatchers.IO) {
        val uriStr = Uri.fromFile(file).toString()
        val format = BookFormat.fromFilename(file.name) ?: BookFormat.TXT
        val hash = computeSha256 { FileInputStream(file) }
        val meta = extractMetadataAndCover(
            context = context,
            filename = file.name,
            format = format,
            contentHash = hash,
            fileSize = file.length(),
            streamProvider = { FileInputStream(file) },
            pfdProvider = { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) }
        )
        val resolvedTitle = when {
            !fallbackTitle.isNullOrBlank() && (meta.title.isBlank() || meta.title.startsWith("Pg ")) -> fallbackTitle
            meta.title.isNotBlank() -> meta.title
            !fallbackTitle.isNullOrBlank() -> fallbackTitle
            else -> file.nameWithoutExtension
        }
        val resolvedAuthors = when {
            !fallbackAuthors.isNullOrBlank() && (meta.authors.isBlank() || meta.authors == "Unknown Author" || meta.authors == "PDF Document") -> fallbackAuthors
            meta.authors.isNotBlank() -> meta.authors
            !fallbackAuthors.isNullOrBlank() -> fallbackAuthors
            else -> "Unknown Author"
        }
        BookEntity(
            id = existing?.id ?: 0L,
            uri = uriStr,
            contentHash = hash,
            title = resolvedTitle,
            authors = resolvedAuthors,
            series = meta.series ?: existing?.series,
            seriesIndex = meta.seriesIndex ?: existing?.seriesIndex,
            format = format,
            coverPath = meta.coverPath ?: coverPathOverride ?: existing?.coverPath,
            addedAt = existing?.addedAt ?: System.currentTimeMillis(),
            lastOpenedAt = existing?.lastOpenedAt,
            progress = existing?.progress ?: 0f,
            status = existing?.status ?: ReadingStatus.UNREAD,
            shelfOrder = existing?.shelfOrder ?: shelfOrder,
            fileSizeBytes = file.length(),
            pageCountEstimate = meta.pageCountEstimate,
            folderTreeUri = folderTreeUri,
            relativeFolderPath = relativeFolderPath,
            hidden = existing?.hidden ?: false,
            stackId = existing?.stackId,
            stackTitle = existing?.stackTitle,
            isSample = false
        )
    }

    private fun extractMetadataAndCover(
        context: Context,
        filename: String,
        format: BookFormat,
        contentHash: String,
        fileSize: Long,
        streamProvider: () -> InputStream?,
        pfdProvider: () -> ParcelFileDescriptor?
    ): ScannedBookMetadata {
        val fallbackTitle = filename.substringBeforeLast('.')
            .replace('_', ' ')
            .replace('-', ' ')
            .replaceFirstChar { it.uppercase() }
        val coverDir = File(context.cacheDir, "covers").apply { mkdirs() }
        val coverFile = File(coverDir, "${contentHash.take(24)}.png")

        return when (format) {
            BookFormat.EPUB -> extractEpubMetadata(fallbackTitle, coverFile, streamProvider)
            BookFormat.PDF -> extractPdfMetadata(fallbackTitle, coverFile, pfdProvider)
            BookFormat.CBZ -> extractCbzMetadata(fallbackTitle, coverFile, streamProvider)
            BookFormat.MD -> extractMarkdownMetadata(fallbackTitle, fileSize, streamProvider)
            else -> ScannedBookMetadata(
                title = fallbackTitle,
                authors = "Unknown Author",
                pageCountEstimate = ((fileSize / 1600L).toInt()).coerceIn(12, 1200)
            )
        }
    }

    private fun extractEpubMetadata(
        fallbackTitle: String,
        coverFile: File,
        streamProvider: () -> InputStream?
    ): ScannedBookMetadata {
        var opfPath: String? = null
        var title = fallbackTitle
        var author = "Unknown Author"
        var series: String? = null
        var coverHref: String? = null
        var spineCount = 0

        // Pass 1: find container.xml -> OPF path
        streamProvider()?.use { raw ->
            ZipInputStream(BufferedInputStream(raw)).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    if (entry.name.equals("META-INF/container.xml", ignoreCase = true)) {
                        opfPath = parseContainerOpfPath(zip)
                        break
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

        // Pass 2: parse OPF metadata
        val resolvedOpf = opfPath ?: "OEBPS/content.opf"
        val opfDir = resolvedOpf.substringBeforeLast('/', "")
        var coverId: String? = null
        val manifestHrefs = mutableMapOf<String, String>()
        var coverImageHrefFromProps: String? = null

        streamProvider()?.use { raw ->
            ZipInputStream(BufferedInputStream(raw)).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    if (entry.name.equals(resolvedOpf, ignoreCase = true) ||
                        (opfPath == null && entry.name.endsWith(".opf", ignoreCase = true))
                    ) {
                        val parser = Xml.newPullParser()
                        parser.setInput(zip, "UTF-8")
                        var event = parser.eventType
                        while (event != XmlPullParser.END_DOCUMENT) {
                            if (event == XmlPullParser.START_TAG) {
                                when (parser.name.lowercase()) {
                                    "title", "dc:title" -> {
                                        val t = runCatching { parser.nextText().trim() }.getOrNull()
                                        if (!t.isNullOrBlank()) title = t
                                    }
                                    "creator", "dc:creator" -> {
                                        val a = runCatching { parser.nextText().trim() }.getOrNull()
                                        if (!a.isNullOrBlank()) author = a
                                    }
                                    "meta" -> {
                                        val nameAttr = parser.getAttributeValue(null, "name")
                                        val propAttr = parser.getAttributeValue(null, "property")
                                        if (nameAttr == "cover") {
                                            coverId = parser.getAttributeValue(null, "content")
                                        } else if (propAttr == "belongs-to-collection") {
                                            series = runCatching { parser.nextText().trim() }.getOrNull()
                                        }
                                    }
                                    "item" -> {
                                        val id = parser.getAttributeValue(null, "id")
                                        val href = parser.getAttributeValue(null, "href")
                                        val props = parser.getAttributeValue(null, "properties") ?: ""
                                        if (id != null && href != null) {
                                            manifestHrefs[id] = href
                                            if (props.contains("cover-image")) {
                                                coverImageHrefFromProps = href
                                            }
                                        }
                                    }
                                    "itemref" -> spineCount++
                                }
                            }
                            event = parser.next()
                        }
                        break
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

        val rawCoverHref = coverImageHrefFromProps ?: coverId?.let { manifestHrefs[it] }
        if (rawCoverHref != null) {
            coverHref = if (opfDir.isEmpty()) rawCoverHref else "$opfDir/$rawCoverHref"
        }

        // Pass 3: extract cover bitmap if not yet cached
        if (!coverFile.exists() && coverHref != null) {
            streamProvider()?.use { raw ->
                ZipInputStream(BufferedInputStream(raw)).use { zip ->
                    var entry: ZipEntry? = zip.nextEntry
                    while (entry != null) {
                        if (entry.name.equals(coverHref, ignoreCase = true) ||
                            entry.name.endsWith("/${coverHref.substringAfterLast('/')}", ignoreCase = true)
                        ) {
                            saveDownsampledBitmap(zip, coverFile)
                            break
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
        }

        return ScannedBookMetadata(
            title = title,
            authors = author,
            series = series?.takeIf { it.isNotBlank() },
            pageCountEstimate = (spineCount * 18).coerceIn(24, 900),
            coverPath = if (coverFile.exists()) coverFile.absolutePath else null
        )
    }

    private fun parseContainerOpfPath(input: InputStream): String? = runCatching {
        val parser = Xml.newPullParser()
        parser.setInput(input, "UTF-8")
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "rootfile") {
                return@runCatching parser.getAttributeValue(null, "full-path")
            }
            event = parser.next()
        }
        null
    }.getOrNull()

    private fun extractPdfMetadata(
        fallbackTitle: String,
        coverFile: File,
        pfdProvider: () -> ParcelFileDescriptor?
    ): ScannedBookMetadata {
        var pageCount = 120
        runCatching {
            pfdProvider()?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    pageCount = renderer.pageCount.coerceAtLeast(1)
                    if (!coverFile.exists() && renderer.pageCount > 0) {
                        renderer.openPage(0).use { page ->
                            val targetW = 420
                            val targetH = ((targetW.toFloat() / page.width) * page.height).toInt().coerceIn(200, 720)
                            val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            coverFile.parentFile?.mkdirs()
                            FileOutputStream(coverFile).use { out ->
                                bmp.compress(Bitmap.CompressFormat.PNG, 88, out)
                            }
                            bmp.recycle()
                        }
                    }
                }
            }
        }
        return ScannedBookMetadata(
            title = fallbackTitle,
            authors = "PDF Document",
            pageCountEstimate = pageCount,
            coverPath = if (coverFile.exists()) coverFile.absolutePath else null
        )
    }

    private fun extractCbzMetadata(
        fallbackTitle: String,
        coverFile: File,
        streamProvider: () -> InputStream?
    ): ScannedBookMetadata {
        var imageCount = 0
        val imageNames = mutableListOf<String>()
        streamProvider()?.use { raw ->
            ZipInputStream(BufferedInputStream(raw)).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && isImageName(entry.name)) {
                        imageCount++
                        imageNames.add(entry.name)
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        val firstImage = imageNames.sorted().firstOrNull()
        if (!coverFile.exists() && firstImage != null) {
            streamProvider()?.use { raw ->
                ZipInputStream(BufferedInputStream(raw)).use { zip ->
                    var entry: ZipEntry? = zip.nextEntry
                    while (entry != null) {
                        if (entry.name == firstImage) {
                            saveDownsampledBitmap(zip, coverFile)
                            break
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
        }
        return ScannedBookMetadata(
            title = fallbackTitle,
            authors = "Comic Archive",
            pageCountEstimate = imageCount.coerceAtLeast(1),
            coverPath = if (coverFile.exists()) coverFile.absolutePath else null
        )
    }

    private fun extractMarkdownMetadata(
        fallbackTitle: String,
        fileSize: Long,
        streamProvider: () -> InputStream?
    ): ScannedBookMetadata {
        var title = fallbackTitle
        var author = "Markdown Note"
        runCatching {
            streamProvider()?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                val lines = reader.lineSequence().take(25).toList()
                if (lines.firstOrNull()?.trim() == "---") {
                    for (line in lines.drop(1)) {
                        if (line.trim() == "---") break
                        if (line.startsWith("title:", ignoreCase = true)) {
                            title = line.substringAfter(":").trim().removeSurrounding("\"")
                        } else if (line.startsWith("author:", ignoreCase = true)) {
                            author = line.substringAfter(":").trim().removeSurrounding("\"")
                        }
                    }
                } else {
                    val heading = lines.firstOrNull { it.startsWith("# ") }
                    if (heading != null) {
                        title = heading.removePrefix("# ").trim()
                    }
                }
            }
        }
        return ScannedBookMetadata(
            title = title,
            authors = author,
            pageCountEstimate = ((fileSize / 1400L).toInt()).coerceIn(4, 400)
        )
    }

    private fun isImageName(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp")
    }

    private fun saveDownsampledBitmap(input: InputStream, dest: File) {
        runCatching {
            val bytes = input.readBytes()
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            var sample = 1
            while (opts.outWidth / sample > 560) {
                sample *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return
            dest.parentFile?.mkdirs()
            FileOutputStream(dest).use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 88, out)
            }
            bmp.recycle()
        }
    }
}
