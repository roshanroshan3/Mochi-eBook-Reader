package com.example.data.reader

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.Inflater

data class PdfReflowResult(
    val chapters: List<ReaderChapter>,
    val extractedPageCount: Int,
    val scannedPageIndices: Set<Int>
)

/**
 * Pure-Kotlin, zero-native-dependency, non-destructive PDF text reflow extractor.
 *
 * Keeps the user's original PDF completely untouched and extracts readable text from
 * uncompressed or /FlateDecode compressed PDF content streams, supporting:
 * - Page tree resolution (/Catalog -> /Pages -> /Kids) with indirect /Type /Page fallback
 * - /ToUnicode CMap tables (beginbfchar / beginbfrange) for hex-encoded glyph strings
 * - Text operators: Tj, TJ, ', ", Td, TD, Tm, T*, Tf
 * - Heuristic Heading vs. Paragraph classification by font size and line structure
 * - Graceful detection of scanned/image-only pages
 */
object PdfReflowExtractor {

    private data class PdfObjRef(val num: Int, val gen: Int)

    private data class RawPdfObject(
        val num: Int,
        val gen: Int,
        val body: String,
        val streamStartByte: Int,
        val streamEndByte: Int
    )

    private data class FontCMap(
        val singleChar: Map<Int, String> = emptyMap(),
        val ranges: List<CMapRange> = emptyList()
    ) {
        fun decodeHex(hexRaw: String): String {
            val clean = hexRaw.filter { !it.isWhitespace() }
            if (clean.isEmpty()) return ""
            // Detect UTF-16BE BOM (FEFF)
            if (clean.startsWith("FEFF", ignoreCase = true) && clean.length >= 8) {
                val sb = StringBuilder()
                var idx = 4
                while (idx + 3 < clean.length) {
                    val code = clean.substring(idx, idx + 4).toIntOrNull(16) ?: 32
                    if (code >= 0x20 || code == 0x0A || code == 0x09) sb.append(code.toChar())
                    idx += 4
                }
                return normalizeLigatures(sb.toString())
            }

            if (singleChar.isNotEmpty() || ranges.isNotEmpty()) {
                val stride = if (singleChar.keys.any { it > 0xFF } || ranges.any { it.end > 0xFF }) 4 else 2
                if (clean.length % stride == 0) {
                    val sb = StringBuilder()
                    var idx = 0
                    while (idx + stride <= clean.length) {
                        val code = clean.substring(idx, idx + stride).toIntOrNull(16) ?: 32
                        val mapped = lookup(code)
                        if (mapped != null) {
                            sb.append(mapped)
                        } else if (code in 0x20..0xFFFF) {
                            sb.append(code.toChar())
                        }
                        idx += stride
                    }
                    if (sb.isNotEmpty()) return sb.toString()
                }
            }

            // Fallback: 1-byte WinAnsi/ASCII hex pairs
            val sb = StringBuilder()
            var idx = 0
            while (idx + 1 < clean.length) {
                val b = clean.substring(idx, idx + 2).toIntOrNull(16) ?: 32
                val mapped = decodeWinAnsiByte(b)
                if (mapped != null) sb.append(mapped)
                idx += 2
            }
            return normalizeLigatures(sb.toString())
        }

        private fun lookup(code: Int): String? {
            singleChar[code]?.let { return it }
            for (r in ranges) {
                if (code in r.start..r.end) {
                    val offset = code - r.start
                    val cp = r.targetStartCode + offset
                    return cp.toChar().toString()
                }
            }
            return null
        }
    }

    private data class CMapRange(val start: Int, val end: Int, val targetStartCode: Int)

    suspend fun extractFromUri(
        context: Context,
        uriString: String,
        expectedPageCount: Int = 0
    ): PdfReflowResult = withContext(Dispatchers.IO) {
        val bytes = readPdfBytes(context, uriString)
        if (bytes == null || bytes.isEmpty()) {
            return@withContext PdfReflowResult(
                chapters = buildFallbackChapters(expectedPageCount.coerceAtLeast(1)),
                extractedPageCount = 0,
                scannedPageIndices = (0 until expectedPageCount.coerceAtLeast(1)).toSet()
            )
        }
        extractFromBytes(bytes, expectedPageCount)
    }

    fun extractFromBytes(
        pdfBytes: ByteArray,
        expectedPageCount: Int = 0
    ): PdfReflowResult {
        val textView = String(pdfBytes, Charsets.ISO_8859_1)
        val objects = parseIndirectObjects(pdfBytes, textView)
        if (objects.isEmpty()) {
            val count = expectedPageCount.coerceAtLeast(1)
            return PdfReflowResult(buildFallbackChapters(count), 0, (0 until count).toSet())
        }

        val pageObjects = resolveOrderedPageObjects(objects, textView)
        val totalPages = maxOf(pageObjects.size, expectedPageCount)

        if (pageObjects.isEmpty()) {
            val count = totalPages.coerceAtLeast(1)
            return PdfReflowResult(buildFallbackChapters(count), 0, (0 until count).toSet())
        }

        val cMapCache = mutableMapOf<Int, FontCMap>()
        val chapters = mutableListOf<ReaderChapter>()
        val scannedPages = mutableSetOf<Int>()
        var extractedPages = 0

        pageObjects.forEachIndexed { pageIdx, pageObj ->
            val fontCMaps = resolvePageFontCMaps(pageObj, objects, pdfBytes, cMapCache)
            val streamTexts = extractPageStreams(pageObj, objects, pdfBytes)
            val combinedStream = streamTexts.joinToString("\n")
            val blocks = parseContentStreamToBlocks(combinedStream, fontCMaps)

            if (blocks.isEmpty()) {
                scannedPages.add(pageIdx)
                val msg = "Page ${pageIdx + 1} appears to be a scanned image or vector-only drawing without embedded text. Switch to Original PDF view to inspect."
                chapters.add(
                    ReaderChapter(
                        index = pageIdx,
                        id = "pdf_reflow_$pageIdx",
                        title = "Page ${pageIdx + 1} (Image)",
                        blocks = listOf(ContentBlock.Paragraph(StyledText(msg))),
                        plainText = msg
                    )
                )
            } else {
                extractedPages++
                val firstHeading = blocks.filterIsInstance<ContentBlock.Heading>().firstOrNull()?.content?.text
                val title = firstHeading?.take(48) ?: "Page ${pageIdx + 1}"
                val plain = buildPlainText(blocks)
                chapters.add(
                    ReaderChapter(
                        index = pageIdx,
                        id = "pdf_reflow_$pageIdx",
                        title = title,
                        blocks = blocks,
                        plainText = plain
                    )
                )
            }
        }

        return PdfReflowResult(
            chapters = chapters,
            extractedPageCount = extractedPages,
            scannedPageIndices = scannedPages
        )
    }

    /**
     * Extracts PDF /Outlines bookmark titles keyed by 0-based page index so Table of Contents
     * displays real chapter names when the PDF embeds an outline tree.
     */
    suspend fun extractOutlineTitlesFromUri(
        context: Context,
        uriString: String
    ): Map<Int, String> = withContext(Dispatchers.IO) {
        val bytes = readPdfBytes(context, uriString, capBytes = 12 * 1024 * 1024) ?: return@withContext emptyMap()
        extractOutlineTitlesFromBytes(bytes)
    }

    fun extractOutlineTitlesFromBytes(pdfBytes: ByteArray): Map<Int, String> {
        val textView = String(pdfBytes, Charsets.ISO_8859_1)
        val objects = parseIndirectObjects(pdfBytes, textView)
        if (objects.isEmpty()) return emptyMap()
        val pageObjects = resolveOrderedPageObjects(objects, textView)
        if (pageObjects.isEmpty()) return emptyMap()

        val pageNumToIndex = HashMap<Int, Int>(pageObjects.size)
        pageObjects.forEachIndexed { idx, obj -> pageNumToIndex[obj.num] = idx }

        val rootRef = Regex("/Root\\s+(\\d+)\\s+\\d+\\s+R").find(textView)?.groupValues?.get(1)?.toIntOrNull()
        val catalogObj = rootRef?.let { objects[it] }
            ?: objects.values.firstOrNull { it.body.contains("/Type\\s*/Catalog".toRegex()) }
            ?: return emptyMap()
        val outlinesRef = Regex("/Outlines\\s+(\\d+)\\s+\\d+\\s+R").find(catalogObj.body)?.groupValues?.get(1)?.toIntOrNull()
            ?: return emptyMap()

        val result = LinkedHashMap<Int, String>()
        val visited = HashSet<Int>()

        fun walkOutline(objNum: Int, depth: Int) {
            if (depth > 4 || !visited.add(objNum) || result.size >= 200) return
            val obj = objects[objNum] ?: return
            val title = extractPdfDictString(obj.body, "Title")?.trim()
            val destPageRef = Regex("/Dest\\s*\\[\\s*(\\d+)\\s+\\d+\\s+R").find(obj.body)?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex("/A\\s*<<[^>]*?/D\\s*\\[\\s*(\\d+)\\s+\\d+\\s+R").find(obj.body)?.groupValues?.get(1)?.toIntOrNull()
            val pageIdx = destPageRef?.let { pageNumToIndex[it] }
            if (!title.isNullOrBlank() && pageIdx != null && !result.containsKey(pageIdx)) {
                result[pageIdx] = title
            }
            val firstChild = Regex("/First\\s+(\\d+)\\s+\\d+\\s+R").find(obj.body)?.groupValues?.get(1)?.toIntOrNull()
            if (firstChild != null) walkOutline(firstChild, depth + 1)
            val nextSibling = Regex("/Next\\s+(\\d+)\\s+\\d+\\s+R").find(obj.body)?.groupValues?.get(1)?.toIntOrNull()
            if (nextSibling != null) walkOutline(nextSibling, depth)
        }

        walkOutline(outlinesRef, 0)
        return result
    }

    private fun extractPdfDictString(body: String, key: String): String? {
        val keyMatch = Regex("/$key\\b").find(body) ?: return null
        var idx = keyMatch.range.last + 1
        while (idx < body.length && body[idx].isWhitespace()) idx++
        if (idx >= body.length) return null

        if (body[idx] == '(') {
            idx++
            var depth = 1
            val rawBytes = ArrayList<Byte>(64)
            while (idx < body.length && depth > 0) {
                val ch = body[idx]
                when (ch) {
                    '\\' -> {
                        idx++
                        if (idx >= body.length) break
                        val esc = body[idx]
                        when (esc) {
                            'n' -> rawBytes.add('\n'.code.toByte())
                            'r' -> rawBytes.add('\r'.code.toByte())
                            't' -> rawBytes.add('\t'.code.toByte())
                            'b' -> rawBytes.add('\b'.code.toByte())
                            'f' -> rawBytes.add(0x0C)
                            '\r' -> if (idx + 1 < body.length && body[idx + 1] == '\n') idx++
                            '\n' -> {}
                            in '0'..'7' -> {
                                var oct = esc.code - '0'.code
                                var digits = 1
                                while (digits < 3 && idx + 1 < body.length && body[idx + 1] in '0'..'7') {
                                    idx++
                                    oct = (oct shl 3) or (body[idx].code - '0'.code)
                                    digits++
                                }
                                rawBytes.add((oct and 0xFF).toByte())
                            }
                            else -> rawBytes.add(esc.code.toByte())
                        }
                    }
                    '(' -> {
                        depth++
                        rawBytes.add('('.code.toByte())
                    }
                    ')' -> {
                        depth--
                        if (depth > 0) rawBytes.add(')'.code.toByte())
                    }
                    else -> rawBytes.add(ch.code.toByte())
                }
                idx++
            }
            val arr = rawBytes.toByteArray()
            if (arr.size >= 2 && arr[0] == 0xFE.toByte() && arr[1] == 0xFF.toByte()) {
                return String(arr, 2, arr.size - 2, Charsets.UTF_16BE)
            }
            return decodeWinAnsiString(String(arr, Charsets.ISO_8859_1))
        }

        if (body[idx] == '<' && (idx + 1 >= body.length || body[idx + 1] != '<')) {
            val closeIdx = body.indexOf('>', idx + 1)
            if (closeIdx > idx + 1) {
                return FontCMap().decodeHex(body.substring(idx + 1, closeIdx))
            }
        }
        return null
    }

    private fun readPdfBytes(context: Context, uriString: String, capBytes: Int = 48 * 1024 * 1024): ByteArray? = runCatching {
        val uri = Uri.parse(uriString)
        val input = when {
            uri.scheme == "file" && uri.path != null -> FileInputStream(File(uri.path!!))
            uri.scheme == null && uriString.startsWith("/") -> FileInputStream(File(uriString))
            else -> context.contentResolver.openInputStream(uri)
        } ?: return@runCatching null

        input.use { stream ->
            val baos = ByteArrayOutputStream()
            val buf = ByteArray(16384)
            var total = 0
            while (total < capBytes) {
                val read = stream.read(buf)
                if (read <= 0) break
                baos.write(buf, 0, read)
                total += read
            }
            baos.toByteArray()
        }
    }.getOrNull()

    private fun parseIndirectObjects(
        pdfBytes: ByteArray,
        textView: String
    ): Map<Int, RawPdfObject> {
        val map = LinkedHashMap<Int, RawPdfObject>()
        val objHeaderRegex = Regex("(?m)(?:^|\\r|\\n)\\s*(\\d+)\\s+(\\d+)\\s+obj\\b")
        val matches = objHeaderRegex.findAll(textView).toList()

        for (i in matches.indices) {
            val m = matches[i]
            val num = m.groupValues[1].toIntOrNull() ?: continue
            val gen = m.groupValues[2].toIntOrNull() ?: 0
            val contentStart = m.range.last + 1
            val searchLimit = if (i + 1 < matches.size) matches[i + 1].range.first else textView.length
            val endObjIdx = textView.indexOf("endobj", contentStart)
            val bodyEnd = if (endObjIdx in contentStart..searchLimit) endObjIdx else searchLimit
            val rawRegion = textView.substring(contentStart, bodyEnd)

            // Detect embedded stream ... endstream safely via byte offsets
            val streamMarker = Regex("\\bstream(?:\\r\\n|\\n|\\r)").find(rawRegion)
            val endStreamIdx = rawRegion.lastIndexOf("endstream")
            val (dictPart, streamStart, streamEnd) = if (streamMarker != null && endStreamIdx > streamMarker.range.last) {
                val absStart = contentStart + streamMarker.range.last + 1
                var absEnd = contentStart + endStreamIdx
                while (absEnd > absStart && (pdfBytes[absEnd - 1] == '\n'.code.toByte() || pdfBytes[absEnd - 1] == '\r'.code.toByte())) {
                    absEnd--
                }
                Triple(rawRegion.substring(0, streamMarker.range.first), absStart, absEnd)
            } else {
                Triple(rawRegion, -1, -1)
            }

            map[num] = RawPdfObject(
                num = num,
                gen = gen,
                body = dictPart,
                streamStartByte = streamStart,
                streamEndByte = streamEnd
            )
        }
        return map
    }

    private fun resolveOrderedPageObjects(
        objects: Map<Int, RawPdfObject>,
        textView: String
    ): List<RawPdfObject> {
        // Locate /Root ref from trailer if present
        val rootRef = Regex("/Root\\s+(\\d+)\\s+\\d+\\s+R").find(textView)?.groupValues?.get(1)?.toIntOrNull()
        val catalogObj = rootRef?.let { objects[it] }
            ?: objects.values.firstOrNull { it.body.contains("/Type\\s*/Catalog".toRegex()) }
        val pagesRef = catalogObj?.let {
            Regex("/Pages\\s+(\\d+)\\s+\\d+\\s+R").find(it.body)?.groupValues?.get(1)?.toIntOrNull()
        } ?: objects.values.firstOrNull {
            it.body.contains("/Type\\s*/Pages".toRegex()) && !it.body.contains("/Parent")
        }?.num

        val ordered = mutableListOf<RawPdfObject>()
        val visited = HashSet<Int>()

        fun walkPagesNode(objNum: Int) {
            if (!visited.add(objNum)) return
            val obj = objects[objNum] ?: return
            val isPagesNode = obj.body.contains("/Kids") || obj.body.contains("/Type\\s*/Pages\\b".toRegex())
            if (isPagesNode && obj.body.contains("/Kids")) {
                val kidsBlock = Regex("/Kids\\s*\\[(.*?)]", RegexOption.DOT_MATCHES_ALL).find(obj.body)?.groupValues?.get(1).orEmpty()
                val kidRefs = Regex("(\\d+)\\s+\\d+\\s+R").findAll(kidsBlock).mapNotNull { it.groupValues[1].toIntOrNull() }
                for (kid in kidRefs) {
                    walkPagesNode(kid)
                }
            } else if (obj.body.contains("/Type\\s*/Page\\b".toRegex()) || obj.body.contains("/Contents")) {
                ordered.add(obj)
            }
        }

        if (pagesRef != null) {
            walkPagesNode(pagesRef)
        }

        if (ordered.isEmpty()) {
            // Fallback: all /Type /Page objects in object number order
            objects.values
                .filter { it.body.contains("/Type\\s*/Page\\b".toRegex()) && !it.body.contains("/Type\\s*/Pages\\b".toRegex()) }
                .forEach { ordered.add(it) }
        }
        return ordered
    }

    private fun resolvePageFontCMaps(
        pageObj: RawPdfObject,
        objects: Map<Int, RawPdfObject>,
        pdfBytes: ByteArray,
        cache: MutableMap<Int, FontCMap>
    ): Map<String, FontCMap> {
        val result = mutableMapOf<String, FontCMap>()
        val resBody = resolveResourcesBody(pageObj, objects)
        if (resBody.isBlank()) return result

        // Find /Font << /F1 12 0 R ... >> or indirect /Font 10 0 R
        val fontDictBody = Regex("/Font\\s+(\\d+)\\s+\\d+\\s+R").find(resBody)?.groupValues?.get(1)?.toIntOrNull()
            ?.let { objects[it]?.body }
            ?: Regex("/Font\\s*<<(.*?)>>", RegexOption.DOT_MATCHES_ALL).find(resBody)?.groupValues?.get(1)
            ?: resBody

        Regex("/([A-Za-z0-9_+.-]+)\\s+(\\d+)\\s+\\d+\\s+R").findAll(fontDictBody).forEach { m ->
            val fontName = m.groupValues[1]
            val fontObjNum = m.groupValues[2].toIntOrNull() ?: return@forEach
            val fontObj = objects[fontObjNum] ?: return@forEach
            val toUniRef = Regex("/ToUnicode\\s+(\\d+)\\s+\\d+\\s+R").find(fontObj.body)?.groupValues?.get(1)?.toIntOrNull()
            if (toUniRef != null) {
                val cmap = cache.getOrPut(toUniRef) {
                    val cmapObj = objects[toUniRef]
                    if (cmapObj != null) {
                        val raw = decodeObjectStream(cmapObj, pdfBytes)?.toString(Charsets.ISO_8859_1).orEmpty()
                        parseToUnicodeCMap(raw)
                    } else FontCMap()
                }
                result[fontName] = cmap
            }
        }
        return result
    }

    private fun resolveResourcesBody(
        pageObj: RawPdfObject,
        objects: Map<Int, RawPdfObject>
    ): String {
        val directRes = Regex("/Resources\\s*<<(.*?)>>", RegexOption.DOT_MATCHES_ALL).find(pageObj.body)?.groupValues?.get(1)
        if (directRes != null) return directRes
        val indirectRef = Regex("/Resources\\s+(\\d+)\\s+\\d+\\s+R").find(pageObj.body)?.groupValues?.get(1)?.toIntOrNull()
        if (indirectRef != null) return objects[indirectRef]?.body.orEmpty()
        val parentRef = Regex("/Parent\\s+(\\d+)\\s+\\d+\\s+R").find(pageObj.body)?.groupValues?.get(1)?.toIntOrNull()
        if (parentRef != null) {
            val parentObj = objects[parentRef]
            if (parentObj != null) return resolveResourcesBody(parentObj, objects)
        }
        return ""
    }

    private fun parseToUnicodeCMap(cmapText: String): FontCMap {
        if (cmapText.isBlank()) return FontCMap()
        val single = HashMap<Int, String>()
        val ranges = ArrayList<CMapRange>()

        val bfCharBlock = Regex("beginbfchar(.*?)endbfchar", RegexOption.DOT_MATCHES_ALL)
        bfCharBlock.findAll(cmapText).forEach { block ->
            Regex("<([0-9A-Fa-f]+)>\\s*<([0-9A-Fa-f]+)>").findAll(block.groupValues[1]).forEach { pair ->
                val src = pair.groupValues[1].toIntOrNull(16) ?: return@forEach
                val dst = decodeUtf16BeHex(pair.groupValues[2])
                if (dst.isNotEmpty()) single[src] = dst
            }
        }

        val bfRangeBlock = Regex("beginbfrange(.*?)endbfrange", RegexOption.DOT_MATCHES_ALL)
        bfRangeBlock.findAll(cmapText).forEach { block ->
            Regex("<([0-9A-Fa-f]+)>\\s*<([0-9A-Fa-f]+)>\\s*<([0-9A-Fa-f]+)>").findAll(block.groupValues[1]).forEach { m ->
                val s = m.groupValues[1].toIntOrNull(16) ?: return@forEach
                val e = m.groupValues[2].toIntOrNull(16) ?: return@forEach
                val dst = m.groupValues[3].toIntOrNull(16) ?: return@forEach
                if (e >= s && e - s < 4096) {
                    ranges.add(CMapRange(s, e, dst))
                }
            }
        }
        return FontCMap(single, ranges)
    }

    private fun decodeUtf16BeHex(hex: String): String {
        val clean = hex.filter { !it.isWhitespace() }
        if (clean.length <= 2) {
            val code = clean.toIntOrNull(16) ?: return ""
            return code.toChar().toString()
        }
        val sb = StringBuilder()
        var i = 0
        while (i + 3 < clean.length) {
            val code = clean.substring(i, i + 4).toIntOrNull(16) ?: 32
            sb.append(code.toChar())
            i += 4
        }
        return sb.toString()
    }

    private fun extractPageStreams(
        pageObj: RawPdfObject,
        objects: Map<Int, RawPdfObject>,
        pdfBytes: ByteArray
    ): List<String> {
        val refs = mutableListOf<Int>()
        val arrayMatch = Regex("/Contents\\s*\\[(.*?)]", RegexOption.DOT_MATCHES_ALL).find(pageObj.body)
        if (arrayMatch != null) {
            Regex("(\\d+)\\s+\\d+\\s+R").findAll(arrayMatch.groupValues[1]).forEach { m ->
                m.groupValues[1].toIntOrNull()?.let { refs.add(it) }
            }
        } else {
            Regex("/Contents\\s+(\\d+)\\s+\\d+\\s+R").find(pageObj.body)?.groupValues?.get(1)?.toIntOrNull()?.let {
                refs.add(it)
            }
        }

        val out = mutableListOf<String>()
        for (ref in refs) {
            val streamObj = objects[ref] ?: continue
            val decoded = decodeObjectStream(streamObj, pdfBytes) ?: continue
            out.add(String(decoded, Charsets.ISO_8859_1))
        }
        return out
    }

    private fun decodeObjectStream(obj: RawPdfObject, pdfBytes: ByteArray): ByteArray? {
        if (obj.streamStartByte < 0 || obj.streamEndByte <= obj.streamStartByte) return null
        val slice = pdfBytes.copyOfRange(obj.streamStartByte, obj.streamEndByte.coerceAtMost(pdfBytes.size))
        return if (obj.body.contains("/FlateDecode")) {
            inflateBytes(slice, nowrap = false) ?: inflateBytes(slice, nowrap = true) ?: slice
        } else {
            slice
        }
    }

    private fun inflateBytes(data: ByteArray, nowrap: Boolean): ByteArray? = runCatching {
        val inflater = Inflater(nowrap)
        inflater.setInput(data)
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (!inflater.finished()) {
            val count = inflater.inflate(buf)
            if (count == 0) {
                if (inflater.needsInput() || inflater.needsDictionary()) break
            } else {
                out.write(buf, 0, count)
            }
        }
        inflater.end()
        val bytes = out.toByteArray()
        if (bytes.isEmpty()) null else bytes
    }.getOrNull()

    private data class ExtractedLine(
        val text: String,
        val fontSize: Float,
        val yGapFromPrev: Float
    )

    private fun parseContentStreamToBlocks(
        stream: String,
        fontCMaps: Map<String, FontCMap>
    ): List<ContentBlock> {
        if (stream.isBlank()) return emptyList()

        val lines = mutableListOf<ExtractedLine>()
        val defaultCMap = FontCMap()
        var currentFontSize = 12f
        var currentCMap = defaultCMap
        var currentY = 0f
        var lastLineY = Float.NaN

        val currentLine = StringBuilder()
        var currentLineFontSize = 12f

        fun flushLine(yGap: Float = 0f) {
            val cleaned = currentLine.toString()
                .replace(Regex("\\s+"), " ")
                .trim()
            if (cleaned.isNotEmpty()) {
                lines.add(ExtractedLine(cleaned, currentLineFontSize, yGap))
            }
            currentLine.clear()
            currentLineFontSize = currentFontSize
        }

        // Process BT ... ET text blocks (or whole stream if unbracketed operators exist)
        val btRegex = Regex("\\bBT\\b(.*?)\\bET\\b", RegexOption.DOT_MATCHES_ALL)
        val segments = btRegex.findAll(stream).map { it.groupValues[1] }.toList().ifEmpty { listOf(stream) }

        for (seg in segments) {
            val tokens = tokenizeContentStream(seg)
            val operandStack = mutableListOf<StreamToken>()

            for (tok in tokens) {
                if (tok.isOperator) {
                    when (tok.value) {
                        "Tf" -> {
                            val size = operandStack.lastOrNull()?.value?.toFloatOrNull()
                            val fontName = operandStack.getOrNull(operandStack.size - 2)?.value?.removePrefix("/")
                            if (size != null && size > 1f) {
                                currentFontSize = size
                                if (currentLine.isEmpty()) currentLineFontSize = size
                            }
                            if (fontName != null) {
                                currentCMap = fontCMaps[fontName] ?: defaultCMap
                            }
                        }
                        "Td", "TD" -> {
                            val ty = operandStack.lastOrNull()?.value?.toFloatOrNull() ?: 0f
                            val tx = operandStack.getOrNull(operandStack.size - 2)?.value?.toFloatOrNull() ?: 0f
                            currentY += ty
                            if (kotlin.math.abs(ty) > currentFontSize * 0.35f) {
                                flushLine(kotlin.math.abs(ty))
                            } else if (tx > currentFontSize * 0.25f && currentLine.isNotEmpty() && !currentLine.endsWith(" ")) {
                                currentLine.append(' ')
                            }
                            lastLineY = currentY
                        }
                        "Tm" -> {
                            val f = operandStack.lastOrNull()?.value?.toFloatOrNull()
                            if (f != null) {
                                val deltaY = if (lastLineY.isNaN()) 0f else kotlin.math.abs(f - lastLineY)
                                if (deltaY > currentFontSize * 0.35f) {
                                    flushLine(deltaY)
                                }
                                currentY = f
                                lastLineY = f
                            }
                        }
                        "T*" -> {
                            flushLine(currentFontSize * 1.2f)
                        }
                        "Tj" -> {
                            val strTok = operandStack.lastOrNull()
                            if (strTok != null) {
                                appendDecodedToken(strTok, currentCMap, currentLine)
                            }
                        }
                        "'" -> {
                            flushLine(currentFontSize * 1.2f)
                            val strTok = operandStack.lastOrNull()
                            if (strTok != null) {
                                appendDecodedToken(strTok, currentCMap, currentLine)
                            }
                        }
                        "\"" -> {
                            flushLine(currentFontSize * 1.2f)
                            val strTok = operandStack.lastOrNull()
                            if (strTok != null) {
                                appendDecodedToken(strTok, currentCMap, currentLine)
                            }
                        }
                        "TJ" -> {
                            // Accumulate array operands until '[' marker
                            val startIdx = operandStack.indexOfLast { it.value == "[" }
                            if (startIdx >= 0) {
                                for (k in (startIdx + 1) until operandStack.size) {
                                    val elem = operandStack[k]
                                    val num = elem.value.toFloatOrNull()
                                    if (num != null) {
                                        if (num < -110f && currentLine.isNotEmpty() && !currentLine.endsWith(" ")) {
                                            currentLine.append(' ')
                                        }
                                    } else {
                                        appendDecodedToken(elem, currentCMap, currentLine)
                                    }
                                }
                            }
                        }
                    }
                    operandStack.clear()
                } else {
                    operandStack.add(tok)
                    if (operandStack.size > 256) {
                        operandStack.removeAt(0)
                    }
                }
            }
            flushLine(currentFontSize * 1.6f)
        }

        if (lines.isEmpty()) return emptyList()

        val sortedSizes = lines.map { it.fontSize }.sorted()
        val bodyFontSize = sortedSizes[sortedSizes.size / 2].coerceAtLeast(8f)

        val blocks = mutableListOf<ContentBlock>()
        val paraBuffer = StringBuilder()

        fun flushParagraph() {
            val txt = paraBuffer.toString().replace(Regex("\\s+"), " ").trim()
            if (txt.isNotEmpty()) {
                blocks.add(ContentBlock.Paragraph(StyledText(txt)))
            }
            paraBuffer.clear()
        }

        lines.forEachIndexed { idx, line ->
            val isHeading = line.fontSize >= bodyFontSize * 1.22f && line.text.length in 2..90
            if (isHeading) {
                flushParagraph()
                val level = if (line.fontSize >= bodyFontSize * 1.55f) 1 else 2
                blocks.add(ContentBlock.Heading(level, StyledText(line.text)))
            } else {
                val startsNewPara = paraBuffer.isNotEmpty() &&
                    (line.yGapFromPrev > line.fontSize * 1.65f ||
                        (paraBuffer.endsWith(".") || paraBuffer.endsWith("!") || paraBuffer.endsWith("?")) &&
                        line.text.firstOrNull()?.isUpperCase() == true &&
                        idx > 0 && lines[idx - 1].text.length < 62)
                if (startsNewPara) {
                    flushParagraph()
                }
                if (paraBuffer.isNotEmpty()) {
                    if (paraBuffer.endsWith("-") && line.text.firstOrNull()?.isLowerCase() == true) {
                        paraBuffer.deleteCharAt(paraBuffer.length - 1)
                    } else {
                        paraBuffer.append(' ')
                    }
                }
                paraBuffer.append(line.text)
            }
        }
        flushParagraph()
        return blocks
    }

    private data class StreamToken(
        val value: String,
        val kind: TokenKind
    ) {
        val isOperator: Boolean get() = kind == TokenKind.OPERATOR
    }

    private enum class TokenKind {
        LITERAL_STRING,
        HEX_STRING,
        ARRAY_DELIM,
        NAME,
        NUMBER,
        OPERATOR
    }

    private fun tokenizeContentStream(seg: String): List<StreamToken> {
        val tokens = ArrayList<StreamToken>()
        val n = seg.length
        var i = 0
        while (i < n) {
            val c = seg[i]
            when {
                c.isWhitespace() -> i++
                c == '%' -> {
                    while (i < n && seg[i] != '\n' && seg[i] != '\r') i++
                }
                c == '(' -> {
                    val sb = StringBuilder()
                    i++
                    var depth = 1
                    while (i < n && depth > 0) {
                        val ch = seg[i]
                        if (ch == '\\' && i + 1 < n) {
                            val next = seg[i + 1]
                            when (next) {
                                'n' -> { sb.append('\n'); i += 2 }
                                'r' -> { sb.append('\r'); i += 2 }
                                't' -> { sb.append('\t'); i += 2 }
                                'b' -> { sb.append('\b'); i += 2 }
                                'f' -> { sb.append(' '); i += 2 }
                                '(', ')', '\\' -> { sb.append(next); i += 2 }
                                '\r', '\n' -> {
                                    i += 2
                                    if (next == '\r' && i < n && seg[i] == '\n') i++
                                }
                                in '0'..'7' -> {
                                    var octEnd = i + 2
                                    while (octEnd < n && octEnd < i + 4 && seg[octEnd] in '0'..'7') octEnd++
                                    val code = seg.substring(i + 1, octEnd).toIntOrNull(8) ?: 32
                                    sb.append(code.toChar())
                                    i = octEnd
                                }
                                else -> { sb.append(next); i += 2 }
                            }
                        } else if (ch == '(') {
                            depth++
                            sb.append(ch)
                            i++
                        } else if (ch == ')') {
                            depth--
                            if (depth > 0) sb.append(ch)
                            i++
                        } else {
                            sb.append(ch)
                            i++
                        }
                    }
                    tokens.add(StreamToken(sb.toString(), TokenKind.LITERAL_STRING))
                }
                c == '<' -> {
                    if (i + 1 < n && seg[i + 1] == '<') {
                        i += 2
                    } else {
                        val end = seg.indexOf('>', i + 1)
                        if (end > i) {
                            tokens.add(StreamToken(seg.substring(i + 1, end), TokenKind.HEX_STRING))
                            i = end + 1
                        } else {
                            i++
                        }
                    }
                }
                c == '>' -> {
                    if (i + 1 < n && seg[i + 1] == '>') i += 2 else i++
                }
                c == '[' || c == ']' -> {
                    tokens.add(StreamToken(c.toString(), TokenKind.ARRAY_DELIM))
                    i++
                }
                c == '/' -> {
                    val start = i
                    i++
                    while (i < n && !seg[i].isWhitespace() && seg[i] !in "()<>[]{}/%") i++
                    tokens.add(StreamToken(seg.substring(start, i), TokenKind.NAME))
                }
                else -> {
                    val start = i
                    while (i < n && !seg[i].isWhitespace() && seg[i] !in "()<>[]{}/%") i++
                    val word = seg.substring(start, i)
                    val kind = if (word.toFloatOrNull() != null) TokenKind.NUMBER else TokenKind.OPERATOR
                    tokens.add(StreamToken(word, kind))
                }
            }
        }
        return tokens
    }

    private fun appendDecodedToken(tok: StreamToken, cmap: FontCMap, out: StringBuilder) {
        when (tok.kind) {
            TokenKind.LITERAL_STRING -> {
                out.append(decodeWinAnsiString(tok.value))
            }
            TokenKind.HEX_STRING -> {
                out.append(cmap.decodeHex(tok.value))
            }
            else -> {}
        }
    }

    private fun decodeWinAnsiString(raw: String): String {
        var hasHighByte = false
        for (c in raw) {
            if (c.code in 0x80..0x9F) {
                hasHighByte = true
                break
            }
        }
        val mapped = if (!hasHighByte) raw else buildString(raw.length) {
            for (c in raw) {
                val decoded = decodeWinAnsiByte(c.code)
                if (decoded != null) append(decoded)
            }
        }
        return normalizeLigatures(mapped)
    }

    private fun decodeWinAnsiByte(b: Int): Char? = when (b) {
        0x80 -> '\u20AC' // €
        0x82 -> '\u201A' // ‚
        0x83 -> '\u0192' // ƒ
        0x84 -> '\u201E' // „
        0x85 -> '\u2026' // …
        0x86 -> '\u2020' // †
        0x87 -> '\u2021' // ‡
        0x88 -> '\u02C6' // ˆ
        0x89 -> '\u2030' // ‰
        0x8A -> '\u0160' // Š
        0x8B -> '\u2039' // ‹
        0x8C -> '\u0152' // Œ
        0x8E -> '\u017D' // Ž
        0x91 -> '\u2018' // ‘
        0x92 -> '\u2019' // ’
        0x93 -> '\u201C' // “
        0x94 -> '\u201D' // ”
        0x95 -> '\u2022' // •
        0x96 -> '\u2013' // –
        0x97 -> '\u2014' // —
        0x98 -> '\u02DC' // ˜
        0x99 -> '\u2122' // ™
        0x9A -> '\u0161' // š
        0x9B -> '\u203A' // ›
        0x9C -> '\u0153' // œ
        0x9E -> '\u017E' // ž
        0x9F -> '\u0178' // Ÿ
        else -> if (b >= 0x20 || b == 0x0A || b == 0x09) b.toChar() else null
    }

    private fun normalizeLigatures(s: String): String {
        if (!s.contains('\uFB00') && !s.contains('\uFB01') && !s.contains('\uFB02') &&
            !s.contains('\uFB03') && !s.contains('\uFB04')
        ) {
            return s
        }
        return s
            .replace("\uFB00", "ff")
            .replace("\uFB01", "fi")
            .replace("\uFB02", "fl")
            .replace("\uFB03", "ffi")
            .replace("\uFB04", "ffl")
    }

    private fun buildPlainText(blocks: List<ContentBlock>): String = buildString {
        blocks.forEach { b ->
            when (b) {
                is ContentBlock.Heading -> appendLine(b.content.text)
                is ContentBlock.Paragraph -> appendLine(b.content.text)
                else -> {}
            }
        }
    }

    private fun buildFallbackChapters(pageCount: Int): List<ReaderChapter> = List(pageCount) { idx ->
        val msg = "Page ${idx + 1} has no extractable text stream. Switch back to Original PDF to inspect."
        ReaderChapter(
            index = idx,
            id = "pdf_reflow_$idx",
            title = "Page ${idx + 1}",
            blocks = listOf(ContentBlock.Paragraph(StyledText(msg))),
            plainText = msg
        )
    }
}
