package com.example.data.reader

import android.util.LruCache
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Density
import com.example.data.preferences.ReaderSettings
import com.example.theme.MochiThemePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Lightweight descriptor of a chapter or sub-chapter chunk for O(1)-memory lazy pagination.
 */
data class LazyChapterChunk(
    val chunkIndex: Int,
    val chapterIndex: Int,
    val chapterTitle: String,
    val charOffsetStart: Int,
    val charOffsetEnd: Int,
    val blocks: List<ContentBlock>,
    val estimatedPages: Int
)

/**
 * Immutable snapshot consumed by Compose `HorizontalPager` (LazyPager) so UI reads
 * stay thread-safe, lock-free, and scoped strictly to cached adjacent chunks.
 */
class LazyBookSnapshot(
    val chunks: List<LazyChapterChunk>,
    val chunkStartPages: IntArray,
    val chapterStartPages: Map<Int, Int>,
    val chapterEndPages: Map<Int, Int>,
    val totalPages: Int,
    val version: Int,
    val isReady: Boolean,
    private val pageLookup: (Int) -> ReaderPage?
) {
    fun getPage(pageIndex: Int): ReaderPage? =
        if (pageIndex in 0 until totalPages) pageLookup(pageIndex) else null

    fun chunkIndexForPage(pageIndex: Int): Int {
        if (chunks.isEmpty()) return 0
        val clamped = pageIndex.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
        var lo = 0
        var hi = chunks.lastIndex
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val start = chunkStartPages[mid]
            val end = chunkStartPages[mid + 1]
            when {
                clamped < start -> hi = mid - 1
                clamped >= end -> lo = mid + 1
                else -> return mid
            }
        }
        return lo.coerceIn(0, chunks.lastIndex)
    }

    fun chapterIndexForPage(pageIndex: Int): Int =
        chunks.getOrNull(chunkIndexForPage(pageIndex))?.chapterIndex ?: 0

    fun chapterTitleForPage(pageIndex: Int): String =
        chunks.getOrNull(chunkIndexForPage(pageIndex))?.chapterTitle ?: ""

    fun pagesRemainingInChapter(pageIndex: Int): Int {
        val chapIdx = chapterIndexForPage(pageIndex)
        val endPage = chapterEndPages[chapIdx] ?: pageIndex
        return (endPage - pageIndex).coerceAtLeast(0)
    }

    fun locatorForPage(pageIndex: Int): ReaderLocator {
        val safeIdx = pageIndex.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
        val cachedPage = getPage(safeIdx)
        val progression = if (totalPages <= 1) 1f else safeIdx.toFloat() / (totalPages - 1).toFloat()
        if (cachedPage != null) {
            val quote = cachedPage.slices.firstNotNullOfOrNull { slice ->
                (slice as? PageSlice.TextBlockSlice)?.text?.text?.take(64)?.trim()
            }.orEmpty()
            return ReaderLocator(
                chapterIndex = cachedPage.chapterIndex,
                chapterTitle = cachedPage.chapterTitle,
                charOffset = cachedPage.startCharOffset,
                progression = progression.coerceIn(0f, 1f),
                quoteAnchor = quote
            )
        }
        val chunk = chunks.getOrNull(chunkIndexForPage(safeIdx))
        return ReaderLocator(
            chapterIndex = chunk?.chapterIndex ?: 0,
            chapterTitle = chunk?.chapterTitle ?: "",
            charOffset = chunk?.charOffsetStart ?: 0,
            progression = progression.coerceIn(0f, 1f),
            quoteAnchor = ""
        )
    }

    fun pageIndexForLocator(locator: ReaderLocator?): Int {
        if (locator == null || totalPages <= 1 || chunks.isEmpty()) return 0
        val matchingChunks = chunks.filter { it.chapterIndex == locator.chapterIndex }
        if (matchingChunks.isEmpty()) {
            return (locator.progression * (totalPages - 1)).roundToInt().coerceIn(0, totalPages - 1)
        }
        val targetChunk = matchingChunks.firstOrNull {
            locator.charOffset in it.charOffsetStart..it.charOffsetEnd
        } ?: matchingChunks.lastOrNull {
            locator.charOffset >= it.charOffsetStart
        } ?: matchingChunks.first()

        val startP = chunkStartPages[targetChunk.chunkIndex]
        val endP = (chunkStartPages[targetChunk.chunkIndex + 1] - 1).coerceAtLeast(startP)
        for (p in startP..endP) {
            val page = getPage(p) ?: continue
            if (locator.charOffset in page.startCharOffset..page.endCharOffset) {
                return p
            }
        }
        val span = (targetChunk.charOffsetEnd - targetChunk.charOffsetStart).coerceAtLeast(1)
        val frac = ((locator.charOffset - targetChunk.charOffsetStart).toFloat() / span.toFloat()).coerceIn(0f, 1f)
        return (startP + (frac * (endP - startP)).roundToInt()).coerceIn(startP, endP)
    }

    fun imageKeysNearPage(currentPageIndex: Int, isDualSpread: Boolean): List<String> {
        if (totalPages <= 0) return emptyList()
        val start = (currentPageIndex - 1).coerceAtLeast(0)
        val end = (currentPageIndex + if (isDualSpread) 2 else 1).coerceAtMost(totalPages - 1)
        val keys = mutableListOf<String>()
        for (p in start..end) {
            val page = getPage(p) ?: continue
            for (slice in page.slices) {
                val block = (slice as? PageSlice.RichBlockSlice)?.block
                if (block is ContentBlock.ImageBlock) {
                    keys.add(block.imageKey)
                }
            }
        }
        return keys
    }

    companion object {
        fun empty(title: String = ""): LazyBookSnapshot = LazyBookSnapshot(
            chunks = emptyList(),
            chunkStartPages = intArrayOf(0),
            chapterStartPages = mapOf(0 to 0),
            chapterEndPages = mapOf(0 to 0),
            totalPages = 1,
            version = 0,
            isReady = false,
            pageLookup = {
                ReaderPage(0, 0, title, 0, 0, emptyList())
            }
        )
    }
}

/**
 * Windowed, off-main-thread pagination engine backing ReaderScreen's HorizontalPager ("LazyPager").
 *
 * - Builds an O(chunks) lightweight index in <1ms without allocating AnnotatedStrings or calling TextMeasurer.
 * - Paginates ONLY the active chunk and immediate neighbors (`MAX_CACHED_CHUNKS = 5`) on Dispatchers.Default.
 * - Evicts distant chunks automatically so multi-hundred-page books stay within a tiny constant memory footprint.
 */
class LazyBookPager(
    private val document: ParsedDocument,
    private val settings: ReaderSettings,
    private val palette: MochiThemePalette,
    private val textMeasurer: TextMeasurer,
    private val density: Density,
    private val pageWidthPx: Int,
    private val pageHeightPx: Int
) {
    private val mutex = Mutex()

    // Retain at most active chunk + 2 neighbor chunks on each side (never full book)
    private val chunkCache = LruCache<Int, List<ReaderPage>>(MAX_CACHED_CHUNKS)
    private val chunks: List<LazyChapterChunk>
    private val chunkPageCounts: IntArray
    private var chunkStartPages: IntArray
    private var snapshotVersion = 0
    private var ready = false

    init {
        val estCharsPerPage = estimateCharsPerPage(settings, density, pageWidthPx, pageHeightPx)
        chunks = buildChunks(document, estCharsPerPage)
        chunkPageCounts = IntArray(chunks.size) { i -> chunks[i].estimatedPages.coerceAtLeast(1) }
        chunkStartPages = recomputeStartPages(chunkPageCounts)
    }

    fun currentSnapshot(): LazyBookSnapshot {
        val startsSnapshot = chunkStartPages.copyOf()
        val total = (startsSnapshot.lastOrNull() ?: 1).coerceAtLeast(1)
        val chapStarts = LinkedHashMap<Int, Int>()
        val chapEnds = LinkedHashMap<Int, Int>()
        for (c in chunks) {
            val s = startsSnapshot[c.chunkIndex]
            val e = (startsSnapshot[c.chunkIndex + 1] - 1).coerceAtLeast(s)
            if (!chapStarts.containsKey(c.chapterIndex)) {
                chapStarts[c.chapterIndex] = s
            }
            chapEnds[c.chapterIndex] = e
        }
        val ver = snapshotVersion
        val isRdy = ready
        return LazyBookSnapshot(
            chunks = chunks,
            chunkStartPages = startsSnapshot,
            chapterStartPages = chapStarts,
            chapterEndPages = chapEnds,
            totalPages = total,
            version = ver,
            isReady = isRdy,
            pageLookup = { globalPage -> lookupCachedPage(globalPage, startsSnapshot) }
        )
    }

    fun cachedChunkCount(): Int = chunkCache.size()

    fun isChunkCached(chunkIndex: Int): Boolean = chunkCache.get(chunkIndex) != null

    private fun lookupCachedPage(globalPage: Int, startsSnapshot: IntArray): ReaderPage? {
        if (chunks.isEmpty()) return null
        val chunkIdx = binarySearchChunk(startsSnapshot, globalPage)
        val cachedList = chunkCache.get(chunkIdx) ?: return null
        val localIdx = globalPage - startsSnapshot[chunkIdx]
        val basePage = cachedList.getOrNull(localIdx) ?: return null
        return if (basePage.pageIndex == globalPage) basePage else basePage.copy(pageIndex = globalPage)
    }

    suspend fun initializeAroundLocator(locator: ReaderLocator?): Pair<LazyBookSnapshot, Int> =
        withContext(Dispatchers.Default) {
            if (chunks.isEmpty() || pageWidthPx <= 40 || pageHeightPx <= 40) {
                mutex.withLock {
                    ready = true
                    snapshotVersion++
                    return@withContext currentSnapshot() to 0
                }
            }
            val targetChunk = chunkIndexForLocator(locator)
            mutex.withLock {
                ensureChunkMeasuredLocked(targetChunk)
                if (targetChunk - 1 >= 0) ensureChunkMeasuredLocked(targetChunk - 1)
                if (targetChunk + 1 < chunks.size) ensureChunkMeasuredLocked(targetChunk + 1)
                ready = true
                snapshotVersion++
                val snap = currentSnapshot()
                val pageIdx = snap.pageIndexForLocator(locator)
                snap to pageIdx
            }
        }

    /**
     * Ensures ONLY the active page + adjacent pages (`[currentPage - 1 .. currentPage + 1]` or `+2`)
     * are materialized in memory, evicting any distant chapters/chunks outside the sliding window.
     *
     * Returns `(updatedSnapshot, anchoredGlobalPageIndex)`.
     */
    suspend fun ensureAdjacentPagesLoaded(
        currentPageIndex: Int,
        isDualSpread: Boolean
    ): Pair<LazyBookSnapshot, Int> = withContext(Dispatchers.Default) {
        if (chunks.isEmpty() || pageWidthPx <= 40 || pageHeightPx <= 40) {
            return@withContext currentSnapshot() to 0
        }

        mutex.withLock {
            val totalBefore = (chunkStartPages.lastOrNull() ?: 1).coerceAtLeast(1)
            val clampedCur = currentPageIndex.coerceIn(0, totalBefore - 1)
            val anchorChunk = binarySearchChunk(chunkStartPages, clampedCur)
            val anchorLocalPage = (clampedCur - chunkStartPages[anchorChunk]).coerceAtLeast(0)

            val prevPage = (clampedCur - 1).coerceAtLeast(0)
            val nextPage = (clampedCur + if (isDualSpread) 2 else 1).coerceAtMost(totalBefore - 1)
            val prevChunk = binarySearchChunk(chunkStartPages, prevPage)
            val nextChunk = binarySearchChunk(chunkStartPages, nextPage)

            val desiredChunks = linkedSetOf(anchorChunk, prevChunk, nextChunk)
            // If near start/end of current chunk, pre-warm immediate neighbor chunk
            if (anchorLocalPage <= 1 && anchorChunk - 1 >= 0) {
                desiredChunks.add(anchorChunk - 1)
            }
            if (clampedCur >= chunkStartPages[anchorChunk + 1] - 2 && anchorChunk + 1 < chunks.size) {
                desiredChunks.add(anchorChunk + 1)
            }

            val minKeep = (desiredChunks.minOrNull() ?: anchorChunk) - 1
            val maxKeep = (desiredChunks.maxOrNull() ?: anchorChunk) + 1
            evictOutsideWindowLocked(minKeep, maxKeep)

            var changed = false
            for (c in desiredChunks) {
                if (chunkCache.get(c) == null) {
                    ensureChunkMeasuredLocked(c)
                    changed = true
                }
            }
            if (!ready) {
                ready = true
                changed = true
            }
            if (changed) {
                snapshotVersion++
            }

            val snap = currentSnapshot()
            val maxAfter = (snap.totalPages - 1).coerceAtLeast(0)
            val anchoredPage = (snap.chunkStartPages[anchorChunk] + anchorLocalPage)
                .coerceIn(snap.chunkStartPages[anchorChunk], (snap.chunkStartPages[anchorChunk + 1] - 1).coerceAtLeast(snap.chunkStartPages[anchorChunk]))
                .coerceIn(0, maxAfter)
            snap to anchoredPage
        }
    }

    fun clear() {
        chunkCache.evictAll()
    }

    private fun evictOutsideWindowLocked(minChunk: Int, maxChunk: Int) {
        val keys = chunkCache.snapshot().keys.toList()
        for (k in keys) {
            if (k < minChunk || k > maxChunk) {
                chunkCache.remove(k)
            }
        }
    }

    private fun ensureChunkMeasuredLocked(chunkIdx: Int) {
        if (chunkIdx !in chunks.indices) return
        if (chunkCache.get(chunkIdx) != null) return

        val chunk = chunks[chunkIdx]
        val result = ReaderPaginator.paginateBlockRange(
            blocks = chunk.blocks,
            chapterIndex = chunk.chapterIndex,
            chapterTitle = chunk.chapterTitle,
            initialCharOffset = chunk.charOffsetStart,
            startingPageIndex = 0,
            isComic = document.isComic,
            settings = settings,
            palette = palette,
            textMeasurer = textMeasurer,
            density = density,
            pageWidthPx = pageWidthPx,
            pageHeightPx = pageHeightPx
        )
        val pages = if (result.pages.isEmpty()) {
            listOf(ReaderPage(0, chunk.chapterIndex, chunk.chapterTitle, chunk.charOffsetStart, chunk.charOffsetEnd, emptyList()))
        } else {
            result.pages
        }
        chunkCache.put(chunkIdx, pages)
        if (chunkPageCounts[chunkIdx] != pages.size) {
            chunkPageCounts[chunkIdx] = pages.size
            chunkStartPages = recomputeStartPages(chunkPageCounts)
        }
    }

    private fun chunkIndexForLocator(locator: ReaderLocator?): Int {
        if (locator == null || chunks.isEmpty()) return 0
        val matching = chunks.filter { it.chapterIndex == locator.chapterIndex }
        if (matching.isEmpty()) {
            val approx = (locator.progression * (chunks.size - 1)).roundToInt().coerceIn(0, chunks.lastIndex)
            return approx
        }
        val exact = matching.firstOrNull { locator.charOffset in it.charOffsetStart..it.charOffsetEnd }
            ?: matching.lastOrNull { locator.charOffset >= it.charOffsetStart }
            ?: matching.first()
        return exact.chunkIndex
    }

    private fun binarySearchChunk(starts: IntArray, globalPage: Int): Int {
        if (chunks.isEmpty()) return 0
        val clamped = globalPage.coerceIn(0, (starts.lastOrNull()?.minus(1) ?: 0).coerceAtLeast(0))
        var lo = 0
        var hi = chunks.lastIndex
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val s = starts[mid]
            val e = starts[mid + 1]
            when {
                clamped < s -> hi = mid - 1
                clamped >= e -> lo = mid + 1
                else -> return mid
            }
        }
        return lo.coerceIn(0, chunks.lastIndex)
    }

    companion object {
        const val MAX_CACHED_CHUNKS = 5
        private const val MAX_BLOCKS_PER_CHUNK = 32

        private fun recomputeStartPages(counts: IntArray): IntArray {
            val starts = IntArray(counts.size + 1)
            var acc = 0
            for (i in counts.indices) {
                starts[i] = acc
                acc += counts[i].coerceAtLeast(1)
            }
            starts[counts.size] = acc.coerceAtLeast(1)
            return starts
        }

        private fun estimateCharsPerPage(
            settings: ReaderSettings,
            density: Density,
            pageWidthPx: Int,
            pageHeightPx: Int
        ): Int {
            if (pageWidthPx <= 40 || pageHeightPx <= 40) return 1400
            val fontPx = settings.fontSizeSp * density.density
            val lineHPx = (fontPx * settings.lineHeightMultiplier).coerceAtLeast(12f)
            val lines = (pageHeightPx / lineHPx).coerceIn(6f, 80f)
            val charsPerLine = (pageWidthPx / (fontPx * 0.52f)).coerceIn(18f, 140f)
            return (lines * charsPerLine * 0.85f).roundToInt().coerceIn(350, 5000)
        }

        private fun blockCharLength(block: ContentBlock): Int = when (block) {
            is ContentBlock.Heading -> block.content.text.length + 1
            is ContentBlock.Paragraph -> block.content.text.length + 1
            is ContentBlock.Blockquote -> block.content.text.length + 1
            is ContentBlock.CodeBlock -> block.code.length + 1
            is ContentBlock.ListBlock -> block.items.sumOf { it.text.text.length + 1 }
            is ContentBlock.TableBlock -> block.rows.size * 40 + 40
            is ContentBlock.FrontMatterCard -> block.entries.size * 24 + 40
            is ContentBlock.ImageBlock -> 450
            ContentBlock.Divider -> 20
        }

        fun buildChunks(document: ParsedDocument, estCharsPerPage: Int): List<LazyChapterChunk> {
            val out = mutableListOf<LazyChapterChunk>()
            for (chapter in document.chapters) {
                val blocks = chapter.blocks
                if (document.isComic || blocks.size <= MAX_BLOCKS_PER_CHUNK) {
                    val totalChars = blocks.sumOf { blockCharLength(it) }
                    val est = if (document.isComic) {
                        blocks.size.coerceAtLeast(1)
                    } else {
                        ceil(totalChars.toDouble() / estCharsPerPage.coerceAtLeast(350)).toInt().coerceAtLeast(1)
                    }
                    out.add(
                        LazyChapterChunk(
                            chunkIndex = out.size,
                            chapterIndex = chapter.index,
                            chapterTitle = chapter.title,
                            charOffsetStart = 0,
                            charOffsetEnd = totalChars,
                            blocks = blocks,
                            estimatedPages = est
                        )
                    )
                } else {
                    var idx = 0
                    var runningChars = 0
                    while (idx < blocks.size) {
                        val end = (idx + MAX_BLOCKS_PER_CHUNK).coerceAtMost(blocks.size)
                        val sub = blocks.subList(idx, end)
                        val subChars = sub.sumOf { blockCharLength(it) }
                        val est = ceil(subChars.toDouble() / estCharsPerPage.coerceAtLeast(350)).toInt().coerceAtLeast(1)
                        out.add(
                            LazyChapterChunk(
                                chunkIndex = out.size,
                                chapterIndex = chapter.index,
                                chapterTitle = chapter.title,
                                charOffsetStart = runningChars,
                                charOffsetEnd = runningChars + subChars,
                                blocks = sub,
                                estimatedPages = est
                            )
                        )
                        runningChars += subChars
                        idx = end
                    }
                }
            }
            if (out.isEmpty()) {
                out.add(
                    LazyChapterChunk(
                        chunkIndex = 0,
                        chapterIndex = 0,
                        chapterTitle = document.title,
                        charOffsetStart = 0,
                        charOffsetEnd = 0,
                        blocks = emptyList(),
                        estimatedPages = 1
                    )
                )
            }
            return out
        }
    }
}
