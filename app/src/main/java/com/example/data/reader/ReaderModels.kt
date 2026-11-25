package com.example.data.reader

import com.example.data.db.BookEntity
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

enum class InlineSpanType {
    BOLD,
    ITALIC,
    STRIKETHROUGH,
    CODE,
    FOOTNOTE_REF,
    WIKILINK,
    EXTERNAL_LINK,
    SYNTAX_KEYWORD,
    SYNTAX_STRING,
    SYNTAX_COMMENT
}

data class InlineSpan(
    val start: Int,
    val end: Int,
    val type: InlineSpanType,
    val target: String? = null
)

data class StyledText(
    val text: String,
    val spans: List<InlineSpan> = emptyList()
)

data class ListItemNode(
    val text: StyledText,
    val checked: Boolean? = null,
    val orderedNumber: Int? = null
)

sealed class ContentBlock {
    data class FrontMatterCard(val entries: List<Pair<String, String>>) : ContentBlock()
    data class Heading(val level: Int, val content: StyledText, val anchorId: String? = null) : ContentBlock()
    data class Paragraph(val content: StyledText, val isFirstAfterHeading: Boolean = false) : ContentBlock()
    data class Blockquote(val content: StyledText) : ContentBlock()
    data class CodeBlock(val language: String?, val code: String, val spans: List<InlineSpan>) : ContentBlock()
    data class TableBlock(val headers: List<String>, val rows: List<List<String>>) : ContentBlock()
    data class ListBlock(val items: List<ListItemNode>) : ContentBlock()
    data class ImageBlock(val imageKey: String, val caption: String? = null) : ContentBlock()
    data object Divider : ContentBlock()
}

data class ReaderChapter(
    val index: Int,
    val id: String,
    val title: String,
    val blocks: List<ContentBlock>,
    val plainText: String
)

data class FootnoteEntry(
    val id: String,
    val title: String,
    val body: String
)

data class ParsedDocument(
    val title: String,
    val authors: String,
    val chapters: List<ReaderChapter>,
    val footnotes: Map<String, FootnoteEntry> = emptyMap(),
    val images: Map<String, ByteArray> = emptyMap(),
    val archiveImageKeys: Set<String> = emptySet(),
    val backlinks: List<BookEntity> = emptyList(),
    val isMarkdown: Boolean = false,
    val isComic: Boolean = false,
    val isFixedLayoutPdf: Boolean = false,
    val pdfPageCount: Int = 0,
    val pdfPageSizes: List<Pair<Int, Int>> = emptyList(),
    val bookUri: String = ""
)

data class ReaderSearchMatch(
    val chapterIndex: Int,
    val chapterTitle: String,
    val charOffset: Int,
    val snippetBefore: String,
    val matchText: String,
    val snippetAfter: String
)

@JsonClass(generateAdapter = true)
data class ReaderLocator(
    val chapterIndex: Int = 0,
    val chapterTitle: String = "",
    val charOffset: Int = 0,
    val progression: Float = 0f,
    val quoteAnchor: String = ""
) {
    companion object {
        private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        private val adapter = moshi.adapter(ReaderLocator::class.java)

        fun toJson(locator: ReaderLocator): String = adapter.toJson(locator)
        fun fromJson(json: String?): ReaderLocator? =
            if (json.isNullOrBlank()) null else runCatching { adapter.fromJson(json) }.getOrNull()
    }
}
