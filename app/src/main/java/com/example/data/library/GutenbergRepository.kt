package com.example.data.library

import android.content.Context
import com.example.data.db.BookFormat
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class GutenbergBook(
    val id: Int,
    val title: String,
    val authors: String,
    val subjects: List<String>,
    val bookshelves: List<String>,
    val languages: List<String>,
    val downloadCount: Int,
    val coverUrl: String?,
    val downloadUrl: String,
    val format: BookFormat
)

data class GutenbergPage(
    val count: Int,
    val nextUrl: String?,
    val previousUrl: String?,
    val results: List<GutenbergBook>
)

data class DownloadedGutenbergFile(
    val bookFile: File,
    val coverCacheFile: File?
)

internal data class GutendexResponseDto(
    val count: Int = 0,
    val next: String? = null,
    val previous: String? = null,
    val results: List<GutendexBookDto> = emptyList()
)

internal data class GutendexBookDto(
    val id: Int = 0,
    val title: String = "",
    val authors: List<GutendexPersonDto> = emptyList(),
    val subjects: List<String> = emptyList(),
    val bookshelves: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    @Json(name = "download_count") val downloadCount: Int = 0,
    val formats: Map<String, String> = emptyMap()
)

internal data class GutendexPersonDto(
    val name: String = ""
)

class GutenbergRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {
    suspend fun fetchCatalog(
        query: String = "",
        topic: String? = null,
        pageUrl: String? = null
    ): Result<GutenbergPage> = withContext(Dispatchers.IO) {
        runCatching {
            val targetUrl = if (!pageUrl.isNullOrBlank()) {
                val parsed = pageUrl.toHttpUrlOrNull()
                require(parsed != null && parsed.scheme == "https") { "Invalid catalog page URL" }
                parsed
            } else {
                val base = "https://gutendex.com/books".toHttpUrlOrNull()!!
                    .newBuilder()
                    .addQueryParameter("sort", "popular")
                val cleanTopic = topic?.trim().orEmpty()
                if (cleanTopic.isNotEmpty() && !cleanTopic.equals("Popular", ignoreCase = true)) {
                    base.addQueryParameter("topic", cleanTopic)
                }
                val cleanQuery = query.trim()
                if (cleanQuery.isNotEmpty()) {
                    base.addQueryParameter("search", cleanQuery)
                }
                base.build()
            }

            val req = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", "MochiReader/1.1 (Android)")
                .header("Accept", "application/json")
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    error("Project Gutenberg catalog unavailable (HTTP ${resp.code})")
                }
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) error("Empty response from Project Gutenberg catalog")
                parseCatalogJson(body)
            }
        }
    }

    suspend fun downloadBook(
        context: Context,
        book: GutenbergBook,
        onProgress: (Float) -> Unit
    ): Result<DownloadedGutenbergFile> = withContext(Dispatchers.IO) {
        runCatching {
            val targetDir = File(context.filesDir, "gutenberg").apply { mkdirs() }
            val finalFile = File(targetDir, "pg_${book.id}.${book.format.extension}")
            val tempFile = File(targetDir, "pg_${book.id}.${book.format.extension}.part")

            if (!finalFile.exists() || finalFile.length() == 0L) {
                val req = Request.Builder()
                    .url(book.downloadUrl)
                    .header("User-Agent", "MochiReader/1.1 (Android)")
                    .build()

                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        error("Download failed (HTTP ${resp.code})")
                    }
                    val body = resp.body ?: error("Empty download stream")
                    val total = body.contentLength()
                    var downloaded = 0L
                    val buf = ByteArray(16 * 1024)

                    onProgress(0.02f)
                    body.byteStream().use { input ->
                        FileOutputStream(tempFile).use { out ->
                            while (true) {
                                val read = input.read(buf)
                                if (read <= 0) break
                                out.write(buf, 0, read)
                                downloaded += read
                                if (total > 0L) {
                                    onProgress((downloaded.toFloat() / total.toFloat()).coerceIn(0.02f, 0.99f))
                                }
                            }
                            out.flush()
                        }
                    }

                    if (!tempFile.exists() || tempFile.length() == 0L) {
                        tempFile.delete()
                        error("Downloaded file is empty")
                    }
                    if (finalFile.exists()) finalFile.delete()
                    if (!tempFile.renameTo(finalFile)) {
                        tempFile.copyTo(finalFile, overwrite = true)
                        tempFile.delete()
                    }
                }
            }

            // Cache remote cover image if available (used as fallback if EPUB/TXT lacks internal cover)
            val coverFile = book.coverUrl?.let { url ->
                val coversDir = File(context.cacheDir, "covers").apply { mkdirs() }
                val dest = File(coversDir, "gutenberg_${book.id}.jpg")
                if (!dest.exists()) {
                    runCatching {
                        val coverReq = Request.Builder()
                            .url(url)
                            .header("User-Agent", "MochiReader/1.1 (Android)")
                            .build()
                        client.newCall(coverReq).execute().use { coverResp ->
                            if (coverResp.isSuccessful) {
                                coverResp.body?.bytes()?.takeIf { it.isNotEmpty() }?.let { bytes ->
                                    dest.writeBytes(bytes)
                                }
                            }
                        }
                    }
                }
                dest.takeIf { it.exists() && it.length() > 0L }
            }

            onProgress(1f)
            DownloadedGutenbergFile(bookFile = finalFile, coverCacheFile = coverFile)
        }
    }

    companion object {
        val TOPICS: List<String> = listOf(
            "Popular",
            "Fiction",
            "Science Fiction",
            "Mystery",
            "Fantasy",
            "Adventure",
            "Philosophy",
            "History",
            "Poetry",
            "Romance"
        )

        private val moshi: Moshi by lazy {
            Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()
        }

        fun parseCatalogJson(json: String): GutenbergPage {
            val adapter = moshi.adapter(GutendexResponseDto::class.java)
            val dto = adapter.fromJson(json) ?: GutendexResponseDto()
            val books = dto.results.mapNotNull { item ->
                val resolved = selectBestDownload(item.formats) ?: return@mapNotNull null
                val authorsText = item.authors
                    .mapNotNull { formatAuthorName(it.name) }
                    .distinct()
                    .joinToString(", ")
                    .ifBlank { "Public Domain" }
                val coverUrl = item.formats.entries
                    .firstOrNull { it.key.startsWith("image/jpeg", ignoreCase = true) }
                    ?.value
                    ?.takeIf { it.startsWith("http") }

                GutenbergBook(
                    id = item.id,
                    title = item.title.trim().ifBlank { "Untitled #${item.id}" },
                    authors = authorsText,
                    subjects = item.subjects,
                    bookshelves = item.bookshelves,
                    languages = item.languages,
                    downloadCount = item.downloadCount,
                    coverUrl = coverUrl,
                    downloadUrl = resolved.first,
                    format = resolved.second
                )
            }
            return GutenbergPage(
                count = dto.count,
                nextUrl = dto.next,
                previousUrl = dto.previous,
                results = books
            )
        }

        private fun selectBestDownload(formats: Map<String, String>): Pair<String, BookFormat>? {
            val epubs = formats.entries
                .filter { it.key.startsWith("application/epub+zip", ignoreCase = true) && it.value.startsWith("http") }
                .map { it.value }
            if (epubs.isNotEmpty()) {
                val preferred = epubs.firstOrNull { it.contains(".epub.images", ignoreCase = true) }
                    ?: epubs.firstOrNull { !it.contains("noimages", ignoreCase = true) }
                    ?: epubs.first()
                return preferred to BookFormat.EPUB
            }

            val txts = formats.entries
                .filter {
                    it.key.startsWith("text/plain", ignoreCase = true) &&
                        it.value.startsWith("http") &&
                        !it.value.endsWith(".zip", ignoreCase = true)
                }
                .map { it.key to it.value }
            if (txts.isNotEmpty()) {
                val utf8 = txts.firstOrNull { it.first.contains("utf-8", ignoreCase = true) }?.second
                    ?: txts.first().second
                return utf8 to BookFormat.TXT
            }

            return null
        }

        private fun formatAuthorName(raw: String): String? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            // Gutendex often uses "Austen, Jane" -> convert to "Jane Austen"
            val parts = trimmed.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            return if (parts.size == 2) "${parts[1]} ${parts[0]}" else trimmed
        }
    }
}
