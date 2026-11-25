package com.example.data.reader

import android.content.Context
import android.net.Uri
import android.util.Xml
import com.example.data.db.BookEntity
import com.example.data.db.BookFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object DocumentParsers {

    suspend fun parseBook(
        context: Context,
        book: BookEntity,
        allBooks: List<BookEntity>
    ): ParsedDocument = withContext(Dispatchers.IO) {
        val streamOpener: () -> InputStream? = {
            val uri = Uri.parse(book.uri)
            if (uri.scheme == "file") {
                uri.path?.let { FileInputStream(File(it)) }
            } else {
                context.contentResolver.openInputStream(uri)
            }
        }

        when (book.format) {
            BookFormat.EPUB -> parseEpub(book, streamOpener)
            BookFormat.MD -> {
                val rawText = streamOpener()?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                parseMarkdown(book, rawText, allBooks, context)
            }
            BookFormat.TXT -> {
                val rawText = streamOpener()?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                parsePlainText(book, rawText)
            }
            BookFormat.CBZ -> parseCbz(book, streamOpener)
            BookFormat.PDF -> {
                val summary = PdfPageEngine.inspectPdf(context, book.uri)
                val outlineTitles = runCatching {
                    PdfReflowExtractor.extractOutlineTitlesFromUri(context, book.uri)
                }.getOrDefault(emptyMap())
                val chapters = summary.pageSizes.mapIndexed { idx, (w, h) ->
                    val label = outlineTitles[idx] ?: "Page ${idx + 1} (${w}×${h} pt)"
                    ReaderChapter(
                        index = idx,
                        id = "pdf_page_$idx",
                        title = label,
                        blocks = listOf(ContentBlock.Paragraph(StyledText("PDF Page ${idx + 1}"))),
                        plainText = label
                    )
                }
                ParsedDocument(
                    title = book.title,
                    authors = book.authors,
                    chapters = chapters,
                    isFixedLayoutPdf = true,
                    pdfPageCount = summary.pageCount,
                    pdfPageSizes = summary.pageSizes,
                    bookUri = book.uri
                )
            }
            else -> ParsedDocument(
                title = book.title,
                authors = book.authors,
                chapters = listOf(
                    ReaderChapter(
                        index = 0,
                        id = "unsupported",
                        title = book.title,
                        blocks = listOf(
                            ContentBlock.Heading(1, StyledText(book.title)),
                            ContentBlock.Paragraph(
                                StyledText("${book.format.displayName} rendering is scheduled for a future milestone.")
                            )
                        ),
                        plainText = book.title
                    )
                )
            )
        }
    }

    // =========================================================================
    // EPUB 2 / 3 PARSER (Memory-bounded: avoids buffering huge archive images)
    // =========================================================================
    private const val MAX_INLINE_EPUB_IMAGE_BYTES = 4 * 1024 * 1024
    private const val MAX_INLINE_CBZ_IMAGE_BYTES = 2 * 1024 * 1024

    private fun isImageAssetPath(path: String): Boolean {
        val lower = path.lowercase()
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
            lower.endsWith(".webp") || lower.endsWith(".gif")
    }

    private fun parseEpub(book: BookEntity, streamOpener: () -> InputStream?): ParsedDocument {
        val entries = mutableMapOf<String, ByteArray>()
        val images = mutableMapOf<String, ByteArray>()
        val archiveImageKeys = linkedSetOf<String>()
        var inlineImageBytes = 0

        streamOpener()?.use { raw ->
            ZipInputStream(BufferedInputStream(raw)).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val name = entry.name
                        if (isImageAssetPath(name)) {
                            archiveImageKeys.add(name)
                            archiveImageKeys.add(name.substringAfterLast('/'))
                            val entryLen = entry.size.takeIf { it > 0L }?.toInt()
                            if (inlineImageBytes < MAX_INLINE_EPUB_IMAGE_BYTES &&
                                (entryLen == null || inlineImageBytes + entryLen <= MAX_INLINE_EPUB_IMAGE_BYTES)
                            ) {
                                val bytes = zip.readBytes()
                                if (inlineImageBytes + bytes.size <= MAX_INLINE_EPUB_IMAGE_BYTES) {
                                    images[name] = bytes
                                    images[name.substringAfterLast('/')] = bytes
                                    inlineImageBytes += bytes.size
                                }
                            }
                        } else {
                            val lower = name.lowercase()
                            if (lower.endsWith(".opf") || lower.endsWith(".ncx") || lower.endsWith(".xml") ||
                                lower.endsWith(".xhtml") || lower.endsWith(".html") || lower.endsWith(".htm")
                            ) {
                                entries[name] = zip.readBytes()
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

        val containerBytes = entries.entries.firstOrNull {
            it.key.equals("META-INF/container.xml", ignoreCase = true)
        }?.value
        val opfPath = containerBytes?.let { parseContainerOpf(it) }
            ?: entries.keys.firstOrNull { it.endsWith(".opf", ignoreCase = true) }
            ?: "OEBPS/content.opf"
        val opfDir = opfPath.substringBeforeLast('/', "")
        val opfBytes = entries[opfPath]

        var docTitle = book.title
        var docAuthor = book.authors
        val manifest = mutableMapOf<String, Pair<String, String>>() // id -> (href, mediaType)
        val spineIds = mutableListOf<String>()
        var ncxId: String? = null
        var navHref: String? = null

        if (opfBytes != null) {
            runCatching {
                val parser = Xml.newPullParser()
                parser.setInput(ByteArrayInputStream(opfBytes), "UTF-8")
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG) {
                        when (parser.name.lowercase()) {
                            "title", "dc:title" -> {
                                val t = runCatching { parser.nextText().trim() }.getOrNull()
                                if (!t.isNullOrBlank()) docTitle = t
                            }
                            "creator", "dc:creator" -> {
                                val a = runCatching { parser.nextText().trim() }.getOrNull()
                                if (!a.isNullOrBlank()) docAuthor = a
                            }
                            "item" -> {
                                val id = parser.getAttributeValue(null, "id")
                                val href = parser.getAttributeValue(null, "href")
                                val media = parser.getAttributeValue(null, "media-type") ?: ""
                                val props = parser.getAttributeValue(null, "properties") ?: ""
                                if (id != null && href != null) {
                                    manifest[id] = href to media
                                    if (props.contains("nav")) navHref = href
                                    if (media.contains("ncx")) ncxId = id
                                }
                            }
                            "itemref" -> {
                                val idref = parser.getAttributeValue(null, "idref")
                                if (idref != null) spineIds.add(idref)
                            }
                            "spine" -> {
                                val tocAttr = parser.getAttributeValue(null, "toc")
                                if (tocAttr != null) ncxId = tocAttr
                            }
                        }
                    }
                    event = parser.next()
                }
            }
        }

        val tocTitles = mutableMapOf<String, String>()
        val ncxPath = ncxId?.let { manifest[it]?.first }?.let { resolvePath(opfDir, it) }
        if (ncxPath != null && entries.containsKey(ncxPath)) {
            parseNcxTitles(entries[ncxPath]!!, tocTitles)
        }
        val resolvedNavPath = navHref?.let { resolvePath(opfDir, it) }
        if (tocTitles.isEmpty() && resolvedNavPath != null && entries.containsKey(resolvedNavPath)) {
            parseHtmlNavTitles(entries[resolvedNavPath]!!, tocTitles)
        }

        val footnotes = mutableMapOf<String, FootnoteEntry>()
        val chapters = mutableListOf<ReaderChapter>()

        val spinePaths = if (spineIds.isNotEmpty()) {
            spineIds.mapNotNull { manifest[it]?.first }.map { resolvePath(opfDir, it) }
        } else {
            entries.keys.filter { it.endsWith(".xhtml", true) || it.endsWith(".html", true) }.sorted()
        }

        spinePaths.forEachIndexed { idx, chapterPath ->
            val rawBytes = entries[chapterPath]
                ?: entries.entries.firstOrNull { it.key.equals(chapterPath, ignoreCase = true) }?.value
                ?: return@forEachIndexed
            val html = rawBytes.toString(Charsets.UTF_8)
            val parsed = parseXhtmlChapter(
                html = html,
                chapterDir = chapterPath.substringBeforeLast('/', ""),
                tocTitle = tocTitles[chapterPath.substringAfterLast('/')] ?: tocTitles[chapterPath],
                fallbackTitle = "Chapter ${idx + 1}",
                footnotes = footnotes
            )
            if (parsed.blocks.isNotEmpty()) {
                chapters.add(
                    parsed.copy(index = chapters.size, id = chapterPath)
                )
            }
        }

        if (chapters.isEmpty()) {
            chapters.add(
                ReaderChapter(
                    index = 0,
                    id = "empty",
                    title = docTitle,
                    blocks = listOf(ContentBlock.Paragraph(StyledText("No readable chapters found in this EPUB."))),
                    plainText = "No readable chapters found in this EPUB."
                )
            )
        }

        return ParsedDocument(
            title = docTitle,
            authors = docAuthor,
            chapters = chapters,
            footnotes = footnotes,
            images = images,
            archiveImageKeys = archiveImageKeys,
            bookUri = book.uri
        )
    }

    private fun resolvePath(baseDir: String, href: String): String {
        val cleanHref = href.substringBefore('#').removePrefix("./")
        if (baseDir.isEmpty()) return cleanHref
        val parts = (baseDir.split("/") + cleanHref.split("/")).toMutableList()
        val stack = mutableListOf<String>()
        for (p in parts) {
            when (p) {
                "", "." -> {}
                ".." -> if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
                else -> stack.add(p)
            }
        }
        return stack.joinToString("/")
    }

    private fun parseContainerOpf(bytes: ByteArray): String? = runCatching {
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "rootfile") {
                return@runCatching parser.getAttributeValue(null, "full-path")
            }
            event = parser.next()
        }
        null
    }.getOrNull()

    private fun parseNcxTitles(bytes: ByteArray, out: MutableMap<String, String>) {
        runCatching {
            val parser = Xml.newPullParser()
            parser.setInput(ByteArrayInputStream(bytes), "UTF-8")
            var event = parser.eventType
            var currentLabel: String? = null
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name.lowercase()) {
                        "text" -> currentLabel = runCatching { parser.nextText().trim() }.getOrNull()
                        "content" -> {
                            val src = parser.getAttributeValue(null, "src")?.substringBefore('#')
                            if (src != null && !currentLabel.isNullOrBlank()) {
                                out[src] = currentLabel!!
                                out[src.substringAfterLast('/')] = currentLabel!!
                            }
                        }
                    }
                }
                event = parser.next()
            }
        }
    }

    private fun parseHtmlNavTitles(bytes: ByteArray, out: MutableMap<String, String>) {
        val html = bytes.toString(Charsets.UTF_8)
        val linkRegex = Regex("<a[^>]*href=[\"']([^\"'#]+)[^>]*>(.*?)</a>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        linkRegex.findAll(html).forEach { match ->
            val href = match.groupValues[1]
            val label = decodeHtmlEntities(match.groupValues[2].replace(Regex("<[^>]+>"), "").trim())
            if (label.isNotBlank()) {
                out[href] = label
                out[href.substringAfterLast('/')] = label
            }
        }
    }

    private fun parseXhtmlChapter(
        html: String,
        chapterDir: String,
        tocTitle: String?,
        fallbackTitle: String,
        footnotes: MutableMap<String, FootnoteEntry>
    ): ReaderChapter {
        val bodyMatch = Regex("<body[^>]*>(.*?)</body>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)
        val bodyHtml = bodyMatch?.groupValues?.get(1) ?: html
        val cleaned = bodyHtml
            .replace(Regex("<script[^>]*>.*?</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
            .replace(Regex("<style[^>]*>.*?</style>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")

        val blocks = mutableListOf<ContentBlock>()
        var detectedTitle = tocTitle
        var afterHeading = false

        // Extract footnote blocks (aside[epub:type=footnote] or div.footnote)
        val asideRegex = Regex("<aside[^>]*id=[\"']([^\"']+)[\"'][^>]*>(.*?)</aside>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        asideRegex.findAll(cleaned).forEach { m ->
            val id = m.groupValues[1]
            val text = stripTags(m.groupValues[2]).trim()
            if (text.isNotBlank()) {
                footnotes[id] = FootnoteEntry(id = id, title = "Note $id", body = text)
            }
        }

        val blockRegex = Regex(
            "<(h[1-6]|p|blockquote|pre|li|hr|img)([^>]*)>(.*?)</\\1>|<img([^>]*)/?>|<hr[^>]*/?>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )

        val listBuffer = mutableListOf<ListItemNode>()
        fun flushList() {
            if (listBuffer.isNotEmpty()) {
                blocks.add(ContentBlock.ListBlock(listBuffer.toList()))
                listBuffer.clear()
            }
        }

        for (match in blockRegex.findAll(cleaned)) {
            val tag = match.groupValues[1].lowercase()
            val attrs = match.groupValues[2]
            val inner = match.groupValues[3]
            val selfImgAttrs = match.groupValues[4]

            if (tag == "li") {
                val styled = parseHtmlInlineSpans(inner)
                if (styled.text.isNotBlank()) listBuffer.add(ListItemNode(styled))
                continue
            } else {
                flushList()
            }

            if (tag.isEmpty()) {
                if (selfImgAttrs.isNotBlank()) {
                    val src = extractAttr(selfImgAttrs, "src")
                    val alt = extractAttr(selfImgAttrs, "alt")
                    if (src != null) {
                        blocks.add(ContentBlock.ImageBlock(resolvePath(chapterDir, src), alt))
                    }
                } else {
                    blocks.add(ContentBlock.Divider)
                }
                continue
            }

            when {
                tag.startsWith("h") -> {
                    val level = tag.drop(1).toIntOrNull()?.coerceIn(1, 4) ?: 2
                    val styled = parseHtmlInlineSpans(inner)
                    if (styled.text.isNotBlank()) {
                        if (detectedTitle == null) detectedTitle = styled.text
                        blocks.add(ContentBlock.Heading(level, styled))
                        afterHeading = true
                    }
                }
                tag == "p" -> {
                    // Check if paragraph contains an inline img
                    val imgMatch = Regex("<img([^>]*)/?>", RegexOption.IGNORE_CASE).find(inner)
                    if (imgMatch != null) {
                        val src = extractAttr(imgMatch.groupValues[1], "src")
                        val alt = extractAttr(imgMatch.groupValues[1], "alt")
                        if (src != null) {
                            blocks.add(ContentBlock.ImageBlock(resolvePath(chapterDir, src), alt))
                        }
                    }
                    val styled = parseHtmlInlineSpans(inner)
                    if (styled.text.isNotBlank()) {
                        blocks.add(ContentBlock.Paragraph(styled, isFirstAfterHeading = afterHeading))
                        afterHeading = false
                    }
                }
                tag == "blockquote" -> {
                    val styled = parseHtmlInlineSpans(inner)
                    if (styled.text.isNotBlank()) {
                        blocks.add(ContentBlock.Blockquote(styled))
                    }
                }
                tag == "pre" -> {
                    val codeText = decodeHtmlEntities(stripTags(inner)).trimEnd()
                    if (codeText.isNotBlank()) {
                        blocks.add(ContentBlock.CodeBlock(null, codeText, highlightCodeSpans(codeText)))
                    }
                }
            }
        }
        flushList()

        if (blocks.isEmpty()) {
            val fallback = decodeHtmlEntities(stripTags(cleaned)).trim()
            fallback.split(Regex("\n\\s*\n")).filter { it.isNotBlank() }.forEach { para ->
                blocks.add(ContentBlock.Paragraph(StyledText(para.trim())))
            }
        }

        val plain = buildPlainText(blocks)
        return ReaderChapter(
            index = 0,
            id = "",
            title = detectedTitle ?: fallbackTitle,
            blocks = blocks,
            plainText = plain
        )
    }

    private fun parseHtmlInlineSpans(html: String): StyledText {
        val out = StringBuilder()
        val spans = mutableListOf<InlineSpan>()
        val tokenRegex = Regex("(<[^>]+>)|([^<]+)")
        var boldStart = -1
        var italicStart = -1
        var codeStart = -1
        var linkStart = -1
        var linkTarget: String? = null
        var isFootnote = false

        for (m in tokenRegex.findAll(html)) {
            val tag = m.groupValues[1]
            val text = m.groupValues[2]
            if (text.isNotEmpty()) {
                out.append(decodeHtmlEntities(text).replace(Regex("\\s+"), " "))
            } else if (tag.isNotEmpty()) {
                val lower = tag.lowercase()
                when {
                    lower.startsWith("<b") || lower.startsWith("<strong") -> boldStart = out.length
                    lower.startsWith("</b") || lower.startsWith("</strong") -> {
                        if (boldStart in 0 until out.length) spans.add(InlineSpan(boldStart, out.length, InlineSpanType.BOLD))
                        boldStart = -1
                    }
                    lower.startsWith("<i") || lower.startsWith("<em") -> italicStart = out.length
                    lower.startsWith("</i") || lower.startsWith("</em") -> {
                        if (italicStart in 0 until out.length) spans.add(InlineSpan(italicStart, out.length, InlineSpanType.ITALIC))
                        italicStart = -1
                    }
                    lower.startsWith("<code") -> codeStart = out.length
                    lower.startsWith("</code") -> {
                        if (codeStart in 0 until out.length) spans.add(InlineSpan(codeStart, out.length, InlineSpanType.CODE))
                        codeStart = -1
                    }
                    lower.startsWith("<a") -> {
                        linkStart = out.length
                        val href = extractAttr(tag, "href")
                        linkTarget = href?.substringAfter('#')
                        isFootnote = lower.contains("noteref") || (href?.contains("#fn") == true)
                    }
                    lower.startsWith("</a") -> {
                        if (linkStart in 0 until out.length && !linkTarget.isNullOrBlank()) {
                            spans.add(
                                InlineSpan(
                                    start = linkStart,
                                    end = out.length,
                                    type = if (isFootnote) InlineSpanType.FOOTNOTE_REF else InlineSpanType.EXTERNAL_LINK,
                                    target = linkTarget
                                )
                            )
                        }
                        linkStart = -1
                        linkTarget = null
                        isFootnote = false
                    }
                    lower.startsWith("<br") -> out.append("\n")
                }
            }
        }
        return StyledText(out.toString().trim(), spans)
    }

    // =========================================================================
    // GITHUB-FLAVORED MARKDOWN PARSER (+ Frontmatter, Tables, Footnotes, Wikilinks)
    // =========================================================================
    fun parseMarkdown(
        book: BookEntity,
        rawText: String,
        allBooks: List<BookEntity>,
        context: Context? = null
    ): ParsedDocument {
        val lines = rawText.replace("\r\n", "\n").split("\n")
        var idx = 0
        val frontMatter = mutableListOf<Pair<String, String>>()
        var docTitle = book.title
        var docAuthor = book.authors

        // YAML Frontmatter
        if (lines.firstOrNull()?.trim() == "---") {
            idx = 1
            while (idx < lines.size && lines[idx].trim() != "---") {
                val line = lines[idx]
                if (line.contains(":")) {
                    val key = line.substringBefore(":").trim()
                    val value = line.substringAfter(":").trim().removeSurrounding("\"")
                    frontMatter.add(key to value)
                    if (key.equals("title", true) && value.isNotBlank()) docTitle = value
                    if (key.equals("author", true) && value.isNotBlank()) docAuthor = value
                }
                idx++
            }
            if (idx < lines.size) idx++
        }

        // Collect footnote definitions [^id]: text
        val footnotes = mutableMapOf<String, FootnoteEntry>()
        val fnDefRegex = Regex("^\\[\\^([^\\]]+)]:\\s*(.+)$")
        val contentLines = mutableListOf<String>()
        for (i in idx until lines.size) {
            val l = lines[i]
            val fn = fnDefRegex.matchEntire(l.trim())
            if (fn != null) {
                val fnId = fn.groupValues[1]
                footnotes[fnId] = FootnoteEntry(fnId, "Footnote [$fnId]", fn.groupValues[2])
            } else {
                contentLines.add(l)
            }
        }

        val images = mutableMapOf<String, ByteArray>()
        val chapters = mutableListOf<ReaderChapter>()
        var currentTitle = docTitle
        var currentBlocks = mutableListOf<ContentBlock>()
        if (frontMatter.isNotEmpty()) {
            currentBlocks.add(ContentBlock.FrontMatterCard(frontMatter))
        }

        fun flushChapter() {
            if (currentBlocks.isNotEmpty()) {
                chapters.add(
                    ReaderChapter(
                        index = chapters.size,
                        id = "md_${chapters.size}",
                        title = currentTitle,
                        blocks = currentBlocks.toList(),
                        plainText = buildPlainText(currentBlocks)
                    )
                )
                currentBlocks = mutableListOf()
            }
        }

        var i = 0
        var afterHeading = false
        while (i < contentLines.size) {
            val line = contentLines[i]
            val trimmed = line.trim()

            if (trimmed.isEmpty()) {
                i++
                continue
            }

            // Fenced code block
            if (trimmed.startsWith("```")) {
                val lang = trimmed.removePrefix("```").trim().takeIf { it.isNotEmpty() }
                val codeLines = mutableListOf<String>()
                i++
                while (i < contentLines.size && !contentLines[i].trim().startsWith("```")) {
                    codeLines.add(contentLines[i])
                    i++
                }
                val code = codeLines.joinToString("\n")
                currentBlocks.add(ContentBlock.CodeBlock(lang, code, highlightCodeSpans(code)))
                i++
                continue
            }

            // Horizontal divider
            if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
                currentBlocks.add(ContentBlock.Divider)
                i++
                continue
            }

            // ATX Heading
            val headingMatch = Regex("^(#{1,6})\\s+(.+)$").matchEntire(trimmed)
            if (headingMatch != null) {
                val level = headingMatch.groupValues[1].length.coerceIn(1, 4)
                val titleText = headingMatch.groupValues[2].trim()
                if (level <= 2 && currentBlocks.any { it !is ContentBlock.FrontMatterCard }) {
                    flushChapter()
                }
                if (level <= 2) currentTitle = titleText
                currentBlocks.add(ContentBlock.Heading(level, parseMarkdownInline(titleText)))
                afterHeading = true
                i++
                continue
            }

            // GFM Table
            if (trimmed.startsWith("|") && i + 1 < contentLines.size && contentLines[i + 1].trim().matches(Regex("^\\|[\\s:\\-|]+\\|$"))) {
                val headers = trimmed.trim('|').split("|").map { it.trim() }
                i += 2
                val rows = mutableListOf<List<String>>()
                while (i < contentLines.size && contentLines[i].trim().startsWith("|")) {
                    rows.add(contentLines[i].trim().trim('|').split("|").map { it.trim() })
                    i++
                }
                currentBlocks.add(ContentBlock.TableBlock(headers, rows))
                continue
            }

            // Image ![alt](path)
            val imgMatch = Regex("^!\\[(.*?)]\\((.+?)\\)$").matchEntire(trimmed)
            if (imgMatch != null) {
                val alt = imgMatch.groupValues[1].takeIf { it.isNotBlank() }
                val relPath = imgMatch.groupValues[2].trim()
                loadSiblingImageIfLocal(book, relPath)?.let { bytes ->
                    images[relPath] = bytes
                }
                currentBlocks.add(ContentBlock.ImageBlock(relPath, alt))
                i++
                continue
            }

            // Blockquote
            if (trimmed.startsWith(">")) {
                val quoteLines = mutableListOf<String>()
                while (i < contentLines.size && contentLines[i].trim().startsWith(">")) {
                    quoteLines.add(contentLines[i].trim().removePrefix(">").trim())
                    i++
                }
                currentBlocks.add(ContentBlock.Blockquote(parseMarkdownInline(quoteLines.joinToString(" "))))
                continue
            }

            // Task list / Unordered / Ordered list
            val listRegex = Regex("^([-*+]|\\d+\\.)\\s+(.*)$")
            if (listRegex.matches(trimmed)) {
                val listItems = mutableListOf<ListItemNode>()
                while (i < contentLines.size) {
                    val m = listRegex.matchEntire(contentLines[i].trim()) ?: break
                    val marker = m.groupValues[1]
                    var itemBody = m.groupValues[2]
                    var checked: Boolean? = null
                    if (itemBody.startsWith("[ ] ")) {
                        checked = false
                        itemBody = itemBody.removePrefix("[ ] ")
                    } else if (itemBody.startsWith("[x] ", ignoreCase = true)) {
                        checked = true
                        itemBody = itemBody.drop(4)
                    }
                    val ordNum = marker.removeSuffix(".").toIntOrNull()
                    listItems.add(ListItemNode(parseMarkdownInline(itemBody), checked, ordNum))
                    i++
                }
                currentBlocks.add(ContentBlock.ListBlock(listItems))
                continue
            }

            // Standard paragraph (accumulate consecutive lines)
            val paraLines = mutableListOf<String>()
            while (i < contentLines.size) {
                val l = contentLines[i].trim()
                if (l.isEmpty() || l.startsWith("#") || l.startsWith("```") || l.startsWith(">") ||
                    l.startsWith("|") || listRegex.matches(l) || l == "---"
                ) break
                paraLines.add(l)
                i++
            }
            val joined = paraLines.joinToString(" ")
            currentBlocks.add(ContentBlock.Paragraph(parseMarkdownInline(joined), isFirstAfterHeading = afterHeading))
            afterHeading = false
        }
        flushChapter()

        if (chapters.isEmpty()) {
            chapters.add(ReaderChapter(0, "md_0", docTitle, listOf(ContentBlock.Paragraph(StyledText(rawText))), rawText))
        }

        // Vault backlinks: find other books referencing [[this book title]]
        val baseTitle = book.title.lowercase()
        val backlinks = allBooks.filter { candidate ->
            candidate.id != book.id && candidate.format == BookFormat.MD &&
                candidate.folderTreeUri == book.folderTreeUri &&
                runCatching {
                    val uri = Uri.parse(candidate.uri)
                    val txt = if (uri.scheme == "file") uri.path?.let { File(it).readText() } else context?.contentResolver?.openInputStream(uri)?.bufferedReader()?.readText()
                    txt?.lowercase()?.contains("[[$baseTitle]]") == true
                }.getOrDefault(false)
        }

        return ParsedDocument(
            title = docTitle,
            authors = docAuthor,
            chapters = chapters,
            footnotes = footnotes,
            images = images,
            backlinks = backlinks,
            isMarkdown = true
        )
    }

    fun parseMarkdownInline(input: String): StyledText {
        val out = StringBuilder()
        val spans = mutableListOf<InlineSpan>()
        var i = 0
        while (i < input.length) {
            when {
                input.startsWith("**", i) -> {
                    val end = input.indexOf("**", i + 2)
                    if (end > i + 2) {
                        val s = out.length
                        out.append(input.substring(i + 2, end))
                        spans.add(InlineSpan(s, out.length, InlineSpanType.BOLD))
                        i = end + 2
                        continue
                    }
                }
                input.startsWith("~~", i) -> {
                    val end = input.indexOf("~~", i + 2)
                    if (end > i + 2) {
                        val s = out.length
                        out.append(input.substring(i + 2, end))
                        spans.add(InlineSpan(s, out.length, InlineSpanType.STRIKETHROUGH))
                        i = end + 2
                        continue
                    }
                }
                input[i] == '*' || input[i] == '_' -> {
                    val delim = input[i]
                    val end = input.indexOf(delim, i + 1)
                    if (end > i + 1) {
                        val s = out.length
                        out.append(input.substring(i + 1, end))
                        spans.add(InlineSpan(s, out.length, InlineSpanType.ITALIC))
                        i = end + 1
                        continue
                    }
                }
                input[i] == '`' -> {
                    val end = input.indexOf('`', i + 1)
                    if (end > i + 1) {
                        val s = out.length
                        out.append(input.substring(i + 1, end))
                        spans.add(InlineSpan(s, out.length, InlineSpanType.CODE))
                        i = end + 1
                        continue
                    }
                }
                input.startsWith("[[", i) -> {
                    val end = input.indexOf("]]", i + 2)
                    if (end > i + 2) {
                        val target = input.substring(i + 2, end)
                        val s = out.length
                        out.append(target.substringAfter('|'))
                        spans.add(InlineSpan(s, out.length, InlineSpanType.WIKILINK, target.substringBefore('|')))
                        i = end + 2
                        continue
                    }
                }
                input.startsWith("[^", i) -> {
                    val end = input.indexOf(']', i + 2)
                    if (end > i + 2) {
                        val fnId = input.substring(i + 2, end)
                        val s = out.length
                        out.append("[$fnId]")
                        spans.add(InlineSpan(s, out.length, InlineSpanType.FOOTNOTE_REF, fnId))
                        i = end + 1
                        continue
                    }
                }
                input[i] == '[' -> {
                    val closeBracket = input.indexOf(']', i + 1)
                    if (closeBracket > i && closeBracket + 1 < input.length && input[closeBracket + 1] == '(') {
                        val closeParen = input.indexOf(')', closeBracket + 2)
                        if (closeParen > closeBracket) {
                            val label = input.substring(i + 1, closeBracket)
                            val url = input.substring(closeBracket + 2, closeParen)
                            val s = out.length
                            out.append(label)
                            spans.add(InlineSpan(s, out.length, InlineSpanType.EXTERNAL_LINK, url))
                            i = closeParen + 1
                            continue
                        }
                    }
                }
            }
            out.append(input[i])
            i++
        }
        return StyledText(out.toString(), spans)
    }

    // =========================================================================
    // TXT & CBZ PARSERS
    // =========================================================================
    private fun parsePlainText(book: BookEntity, rawText: String): ParsedDocument {
        val blocks = rawText.replace("\r\n", "\n")
            .split(Regex("\n\\s*\n"))
            .map { it.trim().replace(Regex("\\s*\n\\s*"), " ") }
            .filter { it.isNotBlank() }

        val chapters = mutableListOf<ReaderChapter>()
        var currentTitle = book.title
        var currentBlocks = mutableListOf<ContentBlock>()
        val chapterHeading = Regex("^(Chapter|CHAPTER|Part|PART)\\s+[\\w\\d.:-]+.*$")

        for (para in blocks) {
            if (chapterHeading.matches(para) && para.length < 80) {
                if (currentBlocks.isNotEmpty()) {
                    chapters.add(ReaderChapter(chapters.size, "txt_${chapters.size}", currentTitle, currentBlocks, buildPlainText(currentBlocks)))
                    currentBlocks = mutableListOf()
                }
                currentTitle = para
                currentBlocks.add(ContentBlock.Heading(1, StyledText(para)))
            } else {
                currentBlocks.add(ContentBlock.Paragraph(StyledText(para)))
            }
        }
        if (currentBlocks.isNotEmpty() || chapters.isEmpty()) {
            if (currentBlocks.isEmpty()) currentBlocks.add(ContentBlock.Paragraph(StyledText("Empty text file.")))
            chapters.add(ReaderChapter(chapters.size, "txt_${chapters.size}", currentTitle, currentBlocks, buildPlainText(currentBlocks)))
        }

        return ParsedDocument(title = book.title, authors = book.authors, chapters = chapters)
    }

    private fun parseCbz(book: BookEntity, streamOpener: () -> InputStream?): ParsedDocument {
        val images = mutableMapOf<String, ByteArray>()
        val imageKeys = mutableListOf<String>()
        var inlineImageBytes = 0

        streamOpener()?.use { raw ->
            ZipInputStream(BufferedInputStream(raw)).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (!entry.isDirectory && isImageAssetPath(name)) {
                        imageKeys.add(name)
                        val entryLen = entry.size.takeIf { it > 0L }?.toInt()
                        if (inlineImageBytes < MAX_INLINE_CBZ_IMAGE_BYTES &&
                            (entryLen == null || inlineImageBytes + entryLen <= MAX_INLINE_CBZ_IMAGE_BYTES)
                        ) {
                            val bytes = zip.readBytes()
                            if (inlineImageBytes + bytes.size <= MAX_INLINE_CBZ_IMAGE_BYTES) {
                                images[name] = bytes
                                inlineImageBytes += bytes.size
                            } else {
                                // Archive exceeds inline budget; clear inline cache and stream per-page lazily
                                images.clear()
                                inlineImageBytes = MAX_INLINE_CBZ_IMAGE_BYTES
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        val sortedKeys = imageKeys.sorted()
        val chapters = sortedKeys.mapIndexed { idx, key ->
            ReaderChapter(
                index = idx,
                id = key,
                title = "Page ${idx + 1}",
                blocks = listOf(ContentBlock.ImageBlock(key, "Page ${idx + 1} of ${sortedKeys.size}")),
                plainText = "Page ${idx + 1}"
            )
        }
        return ParsedDocument(
            title = book.title,
            authors = book.authors,
            chapters = chapters.ifEmpty {
                listOf(ReaderChapter(0, "empty", book.title, listOf(ContentBlock.Paragraph(StyledText("No pages found in comic archive."))), ""))
            },
            images = images,
            archiveImageKeys = sortedKeys.toSet(),
            isComic = true,
            bookUri = book.uri
        )
    }

    private fun loadSiblingImageIfLocal(book: BookEntity, relPath: String): ByteArray? = runCatching {
        val uri = Uri.parse(book.uri)
        if (uri.scheme == "file" && uri.path != null) {
            val sibling = File(File(uri.path!!).parentFile, relPath)
            if (sibling.exists()) sibling.readBytes() else null
        } else null
    }.getOrNull()

    private fun highlightCodeSpans(code: String): List<InlineSpan> {
        val spans = mutableListOf<InlineSpan>()
        val keywordRegex = Regex("\\b(fun|val|var|class|object|if|else|return|when|for|while|import|package|def|function|const|let|async|await)\\b")
        keywordRegex.findAll(code).forEach { m ->
            spans.add(InlineSpan(m.range.first, m.range.last + 1, InlineSpanType.SYNTAX_KEYWORD))
        }
        val stringRegex = Regex("\"[^\"\\n]*\"")
        stringRegex.findAll(code).forEach { m ->
            spans.add(InlineSpan(m.range.first, m.range.last + 1, InlineSpanType.SYNTAX_STRING))
        }
        val commentRegex = Regex("//.*|#.*", RegexOption.MULTILINE)
        commentRegex.findAll(code).forEach { m ->
            spans.add(InlineSpan(m.range.first, m.range.last + 1, InlineSpanType.SYNTAX_COMMENT))
        }
        return spans
    }

    private fun stripTags(html: String): String = html.replace(Regex("<[^>]+>"), "")

    private fun extractAttr(tagOrAttrs: String, attr: String): String? {
        val r = Regex("$attr\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
        return r.find(tagOrAttrs)?.groupValues?.get(1)
    }

    private fun decodeHtmlEntities(input: String): String = input
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&nbsp;", " ")
        .replace("&mdash;", "—")
        .replace("&ndash;", "–")
        .replace("&hellip;", "…")

    private fun buildPlainText(blocks: List<ContentBlock>): String = buildString {
        blocks.forEach { b ->
            when (b) {
                is ContentBlock.Heading -> appendLine(b.content.text)
                is ContentBlock.Paragraph -> appendLine(b.content.text)
                is ContentBlock.Blockquote -> appendLine(b.content.text)
                is ContentBlock.CodeBlock -> appendLine(b.code)
                is ContentBlock.ListBlock -> b.items.forEach { appendLine(it.text.text) }
                else -> {}
            }
        }
    }
}
