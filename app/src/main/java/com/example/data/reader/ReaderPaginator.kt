package com.example.data.reader

import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.preferences.ImportedFontInfo
import com.example.data.preferences.ReaderSettings
import com.example.data.preferences.ReaderTextAlignment
import com.example.theme.MochiThemePalette
import com.example.ui.theme.CaveatFontFamily
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LexendFontFamily
import com.example.ui.theme.LiterataFontFamily
import com.example.ui.theme.PatrickHandFontFamily
import java.io.File
import kotlin.math.roundToInt

sealed class PageSlice {
    data class TextBlockSlice(
        val text: AnnotatedString,
        val style: TextStyle,
        val isBlockquote: Boolean,
        val chapterCharStart: Int,
        val chapterCharEnd: Int
    ) : PageSlice()

    data class RichBlockSlice(
        val block: ContentBlock,
        val chapterCharStart: Int
    ) : PageSlice()
}

data class ReaderPage(
    val pageIndex: Int,
    val chapterIndex: Int,
    val chapterTitle: String,
    val startCharOffset: Int,
    val endCharOffset: Int,
    val slices: List<PageSlice>
)

data class PaginatedBook(
    val pages: List<ReaderPage>,
    val chapterStartPages: Map<Int, Int>
) {
    val totalPages: Int get() = pages.size.coerceAtLeast(1)

    fun pageIndexForLocator(locator: ReaderLocator?): Int {
        if (locator == null || pages.isEmpty()) return 0
        val inChapter = pages.filter { it.chapterIndex == locator.chapterIndex }
        if (inChapter.isEmpty()) {
            return (locator.progression * (pages.size - 1)).roundToInt().coerceIn(0, pages.lastIndex)
        }
        val exact = inChapter.firstOrNull {
            locator.charOffset in it.startCharOffset..it.endCharOffset
        }
        return (exact ?: inChapter.first()).pageIndex
    }

    fun locatorForPage(pageIndex: Int): ReaderLocator {
        val page = pages.getOrNull(pageIndex.coerceIn(0, pages.lastIndex))
            ?: return ReaderLocator()
        val progression = if (pages.size <= 1) 1f else page.pageIndex.toFloat() / (pages.size - 1).toFloat()
        val quote = page.slices.firstNotNullOfOrNull { slice ->
            (slice as? PageSlice.TextBlockSlice)?.text?.text?.take(64)?.trim()
        }.orEmpty()
        return ReaderLocator(
            chapterIndex = page.chapterIndex,
            chapterTitle = page.chapterTitle,
            charOffset = page.startCharOffset,
            progression = progression.coerceIn(0f, 1f),
            quoteAnchor = quote
        )
    }

    fun pagesRemainingInChapter(pageIndex: Int): Int {
        val page = pages.getOrNull(pageIndex) ?: return 0
        val lastInChapter = pages.lastOrNull { it.chapterIndex == page.chapterIndex }?.pageIndex ?: pageIndex
        return (lastInChapter - pageIndex).coerceAtLeast(0)
    }
}

object ReaderFontResolver {
    data class FontOption(val id: String, val label: String, val family: FontFamily)

    private val customFontCache = java.util.concurrent.ConcurrentHashMap<String, FontFamily>()

    val builtInFonts = listOf(
        FontOption("literata", "Literata", LiterataFontFamily),
        FontOption("inter", "Inter", InterFontFamily),
        FontOption("lexend", "Lexend", LexendFontFamily),
        FontOption("jetbrains_mono", "JetBrains Mono", JetBrainsMonoFontFamily),
        FontOption("caveat", "Caveat", CaveatFontFamily),
        FontOption("patrick_hand", "Patrick Hand", PatrickHandFontFamily)
    )

    fun resolve(fontId: String, imported: List<ImportedFontInfo>): FontFamily {
        builtInFonts.find { it.id == fontId }?.let { return it.family }
        val custom = imported.find { it.id == fontId } ?: return LiterataFontFamily
        val cacheKey = "${custom.id}:${custom.filePath}"
        customFontCache[cacheKey]?.let { return it }
        if (File(custom.filePath).exists()) {
            val loaded = runCatching {
                FontFamily(Typeface.createFromFile(custom.filePath))
            }.getOrNull()
            if (loaded != null) {
                customFontCache[cacheKey] = loaded
                return loaded
            }
        }
        return LiterataFontFamily
    }
}

object ReaderPaginator {

    fun toAnnotatedString(
        styled: StyledText,
        palette: MochiThemePalette,
        hyphenate: Boolean
    ): AnnotatedString {
        // Hyphenation preserves indices safely when applied per-word? If spans exist, remap or hyphenate only plain spans
        val raw = if (hyphenate && styled.spans.isEmpty()) {
            KnuthLiangHyphenator.hyphenateText(styled.text)
        } else {
            styled.text
        }
        return buildAnnotatedString {
            append(raw)
            if (raw.length == styled.text.length) {
                for (span in styled.spans) {
                    val s = span.start.coerceIn(0, raw.length)
                    val e = span.end.coerceIn(s, raw.length)
                    if (s >= e) continue
                    when (span.type) {
                        InlineSpanType.BOLD -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), s, e)
                        InlineSpanType.ITALIC -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), s, e)
                        InlineSpanType.STRIKETHROUGH -> addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), s, e)
                        InlineSpanType.CODE -> addStyle(
                            SpanStyle(
                                fontFamily = JetBrainsMonoFontFamily,
                                background = Color(palette.surfaceVariant)
                            ),
                            s, e
                        )
                        InlineSpanType.FOOTNOTE_REF -> {
                            addStyle(
                                SpanStyle(
                                    color = Color(palette.link),
                                    fontWeight = FontWeight.SemiBold,
                                    textDecoration = TextDecoration.Underline
                                ),
                                s, e
                            )
                            if (span.target != null) {
                                addStringAnnotation("footnote", span.target, s, e)
                            }
                        }
                        InlineSpanType.WIKILINK -> {
                            addStyle(
                                SpanStyle(
                                    color = Color(palette.link),
                                    fontWeight = FontWeight.Medium,
                                    textDecoration = TextDecoration.Underline
                                ),
                                s, e
                            )
                            if (span.target != null) {
                                addStringAnnotation("wikilink", span.target, s, e)
                            }
                        }
                        InlineSpanType.EXTERNAL_LINK -> {
                            addStyle(
                                SpanStyle(
                                    color = Color(palette.link),
                                    textDecoration = TextDecoration.Underline
                                ),
                                s, e
                            )
                        }
                        InlineSpanType.SYNTAX_KEYWORD -> addStyle(SpanStyle(color = Color(palette.accent), fontWeight = FontWeight.SemiBold), s, e)
                        InlineSpanType.SYNTAX_STRING -> addStyle(SpanStyle(color = Color(palette.link)), s, e)
                        InlineSpanType.SYNTAX_COMMENT -> addStyle(SpanStyle(color = Color(palette.textSecondary), fontStyle = FontStyle.Italic), s, e)
                    }
                }
            }
        }
    }

    data class ChunkPaginationResult(
        val pages: List<ReaderPage>,
        val endCharOffset: Int
    )

    fun paginateBlockRange(
        blocks: List<ContentBlock>,
        chapterIndex: Int,
        chapterTitle: String,
        initialCharOffset: Int,
        startingPageIndex: Int,
        isComic: Boolean,
        settings: ReaderSettings,
        palette: MochiThemePalette,
        textMeasurer: TextMeasurer,
        density: Density,
        pageWidthPx: Int,
        pageHeightPx: Int
    ): ChunkPaginationResult {
        if (pageWidthPx <= 40 || pageHeightPx <= 40) {
            return ChunkPaginationResult(emptyList(), initialCharOffset)
        }

        val fontFamily = ReaderFontResolver.resolve(settings.fontId, settings.importedFonts)
        val bodyStyle = TextStyle(
            fontFamily = fontFamily,
            fontSize = settings.fontSizeSp.sp,
            lineHeight = (settings.fontSizeSp * settings.lineHeightMultiplier).sp,
            letterSpacing = settings.letterSpacingSp.sp,
            textAlign = if (settings.alignment == ReaderTextAlignment.JUSTIFY) TextAlign.Justify else TextAlign.Left,
            color = Color(palette.text)
        )
        val paraGapPx = with(density) { settings.paragraphSpacingDp.dp.toPx() }
        val indentSpaces = if (settings.firstLineIndentDp >= 8f) "\u2003\u2003" else ""
        val quoteIndentPx = with(density) { 20.dp.toPx() }.toInt()

        val pages = mutableListOf<ReaderPage>()
        var currentSlices = mutableListOf<PageSlice>()
        var usedHeightPx = 0f
        var pageCharStart = initialCharOffset
        var runningCharOffset = initialCharOffset

        fun flushPage() {
            if (currentSlices.isNotEmpty()) {
                pages.add(
                    ReaderPage(
                        pageIndex = startingPageIndex + pages.size,
                        chapterIndex = chapterIndex,
                        chapterTitle = chapterTitle,
                        startCharOffset = pageCharStart,
                        endCharOffset = runningCharOffset.coerceAtLeast(pageCharStart),
                        slices = currentSlices.toList()
                    )
                )
                currentSlices = mutableListOf()
                usedHeightPx = 0f
                pageCharStart = runningCharOffset
            }
        }

        if (isComic) {
            blocks.forEach { block ->
                pages.add(
                    ReaderPage(
                        pageIndex = startingPageIndex + pages.size,
                        chapterIndex = chapterIndex,
                        chapterTitle = chapterTitle,
                        startCharOffset = 0,
                        endCharOffset = 1,
                        slices = listOf(PageSlice.RichBlockSlice(block, 0))
                    )
                )
            }
            return ChunkPaginationResult(pages, runningCharOffset)
        }

        for (block in blocks) {
            when (block) {
                is ContentBlock.Heading -> {
                    val scale = when (block.level) {
                        1 -> 1.48f
                        2 -> 1.28f
                        3 -> 1.15f
                        else -> 1.06f
                    }
                    val hStyle = bodyStyle.copy(
                        fontSize = (settings.fontSizeSp * scale).sp,
                        lineHeight = (settings.fontSizeSp * scale * 1.28f).sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Start
                    )
                    val annotated = toAnnotatedString(block.content, palette, hyphenate = false)
                    val measured = textMeasurer.measure(
                        text = annotated,
                        style = hStyle,
                        constraints = Constraints(maxWidth = pageWidthPx)
                    )
                    val h = measured.size.height.toFloat() + paraGapPx * 1.4f
                    if (usedHeightPx > 0f && usedHeightPx + h > pageHeightPx) {
                        flushPage()
                    }
                    val charEnd = runningCharOffset + annotated.length
                    currentSlices.add(PageSlice.TextBlockSlice(annotated, hStyle, false, runningCharOffset, charEnd))
                    usedHeightPx += h
                    runningCharOffset = charEnd + 1
                }

                is ContentBlock.Paragraph, is ContentBlock.Blockquote -> {
                    val isQuote = block is ContentBlock.Blockquote
                    val rawStyled = if (block is ContentBlock.Paragraph) block.content else (block as ContentBlock.Blockquote).content
                    val applyIndent = block is ContentBlock.Paragraph && !block.isFirstAfterHeading && indentSpaces.isNotEmpty()
                    val effectiveStyled = if (applyIndent) {
                        rawStyled.copy(
                            text = indentSpaces + rawStyled.text,
                            spans = rawStyled.spans.map { it.copy(start = it.start + indentSpaces.length, end = it.end + indentSpaces.length) }
                        )
                    } else rawStyled

                    val pStyle = if (isQuote) {
                        bodyStyle.copy(fontStyle = FontStyle.Italic, color = Color(palette.textSecondary))
                    } else bodyStyle

                    val annotated = toAnnotatedString(effectiveStyled, palette, settings.hyphenationEnabled)
                    val widthLimit = if (isQuote) (pageWidthPx - quoteIndentPx).coerceAtLeast(80) else pageWidthPx
                    val measured = textMeasurer.measure(
                        text = annotated,
                        style = pStyle,
                        constraints = Constraints(maxWidth = widthLimit)
                    )

                    var lineStartIdx = 0
                    while (lineStartIdx < measured.lineCount) {
                        val remainingPx = (pageHeightPx - usedHeightPx).coerceAtLeast(0f)
                        val firstLineHeight = measured.getLineBottom(lineStartIdx) - measured.getLineTop(lineStartIdx)
                        if (usedHeightPx > 0f && remainingPx < firstLineHeight + 4f) {
                            flushPage()
                            continue
                        }

                        var lineEndIdx = lineStartIdx
                        while (lineEndIdx + 1 < measured.lineCount) {
                            val nextSliceHeight = measured.getLineBottom(lineEndIdx + 1) - measured.getLineTop(lineStartIdx)
                            if (nextSliceHeight <= (pageHeightPx - usedHeightPx)) {
                                lineEndIdx++
                            } else {
                                break
                            }
                        }

                        val charStartInBlock = measured.getLineStart(lineStartIdx)
                        val charEndInBlock = measured.getLineEnd(lineEndIdx, visibleEnd = true).coerceIn(charStartInBlock, annotated.length)
                        val subText = annotated.subSequence(charStartInBlock, charEndInBlock)
                        val sliceH = (measured.getLineBottom(lineEndIdx) - measured.getLineTop(lineStartIdx)) +
                            if (lineEndIdx == measured.lineCount - 1) paraGapPx else 0f

                        currentSlices.add(
                            PageSlice.TextBlockSlice(
                                text = subText,
                                style = pStyle,
                                isBlockquote = isQuote,
                                chapterCharStart = runningCharOffset + charStartInBlock,
                                chapterCharEnd = runningCharOffset + charEndInBlock
                            )
                        )
                        usedHeightPx += sliceH
                        lineStartIdx = lineEndIdx + 1

                        if (lineStartIdx < measured.lineCount) {
                            flushPage()
                        }
                    }
                    runningCharOffset += annotated.length + 1
                }

                is ContentBlock.FrontMatterCard -> {
                    val estH = with(density) { (48 + block.entries.size * 22).dp.toPx() }
                    if (usedHeightPx > 0f && usedHeightPx + estH > pageHeightPx) flushPage()
                    currentSlices.add(PageSlice.RichBlockSlice(block, runningCharOffset))
                    usedHeightPx += estH + paraGapPx
                }

                is ContentBlock.CodeBlock -> {
                    val lineCount = block.code.lines().size.coerceIn(2, 24)
                    val estH = with(density) { (36 + lineCount * 20).dp.toPx() }.coerceAtMost(pageHeightPx * 0.85f)
                    if (usedHeightPx > 0f && usedHeightPx + estH > pageHeightPx) flushPage()
                    currentSlices.add(PageSlice.RichBlockSlice(block, runningCharOffset))
                    usedHeightPx += estH + paraGapPx
                    runningCharOffset += block.code.length + 1
                }

                is ContentBlock.TableBlock -> {
                    val estH = with(density) { ((block.rows.size + 1) * 34 + 20).dp.toPx() }.coerceAtMost(pageHeightPx * 0.85f)
                    if (usedHeightPx > 0f && usedHeightPx + estH > pageHeightPx) flushPage()
                    currentSlices.add(PageSlice.RichBlockSlice(block, runningCharOffset))
                    usedHeightPx += estH + paraGapPx
                }

                is ContentBlock.ListBlock -> {
                    val estH = with(density) { (block.items.size * 30 + 12).dp.toPx() }.coerceAtMost(pageHeightPx * 0.85f)
                    if (usedHeightPx > 0f && usedHeightPx + estH > pageHeightPx) flushPage()
                    currentSlices.add(PageSlice.RichBlockSlice(block, runningCharOffset))
                    usedHeightPx += estH + paraGapPx
                }

                is ContentBlock.ImageBlock -> {
                    val estH = (pageHeightPx * 0.45f).coerceAtLeast(180f)
                    if (usedHeightPx > 0f && usedHeightPx + estH > pageHeightPx) flushPage()
                    currentSlices.add(PageSlice.RichBlockSlice(block, runningCharOffset))
                    usedHeightPx += estH + paraGapPx
                }

                ContentBlock.Divider -> {
                    val estH = with(density) { 24.dp.toPx() }
                    if (usedHeightPx > 0f && usedHeightPx + estH > pageHeightPx) flushPage()
                    currentSlices.add(PageSlice.RichBlockSlice(block, runningCharOffset))
                    usedHeightPx += estH
                }
            }
        }
        flushPage()
        return ChunkPaginationResult(pages, runningCharOffset)
    }

    fun paginate(
        document: ParsedDocument,
        settings: ReaderSettings,
        palette: MochiThemePalette,
        textMeasurer: TextMeasurer,
        density: Density,
        pageWidthPx: Int,
        pageHeightPx: Int
    ): PaginatedBook {
        if (pageWidthPx <= 40 || pageHeightPx <= 40) {
            return PaginatedBook(emptyList(), emptyMap())
        }

        val pages = mutableListOf<ReaderPage>()
        val chapterStarts = mutableMapOf<Int, Int>()

        for (chapter in document.chapters) {
            chapterStarts[chapter.index] = pages.size
            val result = paginateBlockRange(
                blocks = chapter.blocks,
                chapterIndex = chapter.index,
                chapterTitle = chapter.title,
                initialCharOffset = 0,
                startingPageIndex = pages.size,
                isComic = document.isComic,
                settings = settings,
                palette = palette,
                textMeasurer = textMeasurer,
                density = density,
                pageWidthPx = pageWidthPx,
                pageHeightPx = pageHeightPx
            )
            pages.addAll(result.pages)
        }

        if (pages.isEmpty()) {
            pages.add(ReaderPage(0, 0, document.title, 0, 0, emptyList()))
        }
        return PaginatedBook(pages = pages, chapterStartPages = chapterStarts)
    }

    fun search(document: ParsedDocument, query: String): List<ReaderSearchMatch> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        val matches = mutableListOf<ReaderSearchMatch>()
        val lowerQ = q.lowercase()

        for (chapter in document.chapters) {
            val text = chapter.plainText
            val lowerText = text.lowercase()
            var idx = 0
            while (matches.size < 120) {
                val hit = lowerText.indexOf(lowerQ, idx)
                if (hit < 0) break
                val beforeStart = (hit - 36).coerceAtLeast(0)
                val afterEnd = (hit + q.length + 48).coerceAtMost(text.length)
                matches.add(
                    ReaderSearchMatch(
                        chapterIndex = chapter.index,
                        chapterTitle = chapter.title,
                        charOffset = hit,
                        snippetBefore = text.substring(beforeStart, hit).replace('\n', ' '),
                        matchText = text.substring(hit, hit + q.length),
                        snippetAfter = text.substring(hit + q.length, afterEnd).replace('\n', ' ')
                    )
                )
                idx = hit + q.length
            }
        }
        return matches
    }
}
