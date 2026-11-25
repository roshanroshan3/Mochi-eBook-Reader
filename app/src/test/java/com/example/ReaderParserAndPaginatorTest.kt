package com.example

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.BookEntity
import com.example.data.db.BookFormat
import com.example.data.db.MochiDatabase
import com.example.data.db.ReadingStatus
import com.example.data.library.SafBookScanner
import com.example.data.reader.ContentBlock
import com.example.data.reader.DocumentParsers
import com.example.data.reader.InlineSpanType
import com.example.data.reader.KnuthLiangHyphenator
import com.example.data.reader.ReaderChapter
import com.example.data.reader.ReaderLocator
import com.example.data.reader.ReaderPaginator
import com.example.data.reader.ReaderRepository
import com.example.data.reader.ParsedDocument
import com.example.data.reader.StyledText
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReaderParserAndPaginatorTest {

    private lateinit var context: Context
    private lateinit var db: MochiDatabase
    private lateinit var repository: ReaderRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, MochiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ReaderRepository(
            context = context,
            bookDao = db.bookDao(),
            positionDao = db.positionDao(),
            bookSettingsDao = db.bookSettingsDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun markdownParserHandlesFrontmatterGfmTablesChecklistsFootnotesCodeAndWikilinks() {
        val mdBook = BookEntity(
            id = 10L,
            uri = "file:///tmp/vault/note_a.md",
            contentHash = "hash_a",
            title = "Note A",
            authors = "Author",
            format = BookFormat.MD,
            addedAt = 1L,
            folderTreeUri = "vault://local"
        )
        val rawMarkdown = """
            ---
            title: Typography Lab
            author: Studio Mochi
            tags: [fonts, paper]
            ---

            # Overview

            Here is **bold**, *italic*, `inline()`, a footnote[^1], and [[Target Book|Target]].

            - [x] Done task
            - [ ] Pending task

            | Setting | Default |
            | --- | --- |
            | Font | Literata |

            ```kotlin
            fun greet() = "hello"
            ```

            [^1]: Footnote explanation body.
        """.trimIndent()

        val doc = DocumentParsers.parseMarkdown(
            book = mdBook,
            rawText = rawMarkdown,
            allBooks = listOf(mdBook),
            context = context
        )

        assertTrue(doc.isMarkdown)
        assertEquals("Typography Lab", doc.title)
        assertEquals("Studio Mochi", doc.authors)
        assertNotNull(doc.footnotes["1"])
        assertEquals("Footnote explanation body.", doc.footnotes["1"]?.body)

        val blocks = doc.chapters.first().blocks
        assertTrue(blocks.any { it is ContentBlock.FrontMatterCard })
        assertTrue(blocks.any { it is ContentBlock.Heading && it.content.text == "Overview" })
        assertTrue(blocks.any { it is ContentBlock.TableBlock && it.headers == listOf("Setting", "Default") })
        assertTrue(blocks.any { it is ContentBlock.CodeBlock && it.language == "kotlin" })

        val listBlock = blocks.filterIsInstance<ContentBlock.ListBlock>().first()
        assertEquals(true, listBlock.items[0].checked)
        assertEquals(false, listBlock.items[1].checked)

        val para = blocks.filterIsInstance<ContentBlock.Paragraph>().first()
        assertTrue(para.content.spans.any { it.type == InlineSpanType.BOLD })
        assertTrue(para.content.spans.any { it.type == InlineSpanType.ITALIC })
        assertTrue(para.content.spans.any { it.type == InlineSpanType.CODE })
        assertTrue(para.content.spans.any { it.type == InlineSpanType.FOOTNOTE_REF && it.target == "1" })
        assertTrue(para.content.spans.any { it.type == InlineSpanType.WIKILINK && it.target == "Target Book" })
    }

    @Test
    fun epubTxtAndCbzParsersExtractChaptersAndImagesFromSyntheticArchives() = runTest {
        // 1. EPUB synthetic archive with OPF + NCX + XHTML + image + footnote
        val epubFile = File(context.cacheDir, "test_book.epub")
        ZipOutputStream(FileOutputStream(epubFile)).use { zip ->
            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            zip.write(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
                </container>
                """.trimIndent().toByteArray()
            )
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zip.write(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Pocket Kyoto</dc:title>
                    <dc:creator>Hana Sato</dc:creator>
                  </metadata>
                  <manifest>
                    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                    <item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
                    <item id="img1" href="img/lantern.png" media-type="image/png"/>
                  </manifest>
                  <spine toc="ncx"><itemref idref="ch1"/></spine>
                </package>
                """.trimIndent().toByteArray()
            )
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("OEBPS/toc.ncx"))
            zip.write(
                """
                <ncx><navMap><navPoint><navLabel><text>Cedar Gate</text></navLabel><content src="ch1.xhtml"/></navPoint></navMap></ncx>
                """.trimIndent().toByteArray()
            )
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("OEBPS/ch1.xhtml"))
            zip.write(
                """
                <html><body>
                  <h1>Cedar Gate</h1>
                  <p>Lanterns glow beside the moss<a epub:type="noteref" href="#fn1">[1]</a>.</p>
                  <img src="img/lantern.png" alt="Paper lantern"/>
                  <aside id="fn1">Hand-oiled mulberry paper.</aside>
                </body></html>
                """.trimIndent().toByteArray()
            )
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("OEBPS/img/lantern.png"))
            zip.write(byteArrayOf(1, 2, 3, 4))
            zip.closeEntry()
        }

        val epubEntity = BookEntity(
            uri = Uri.fromFile(epubFile).toString(),
            contentHash = "epub_hash",
            title = "Fallback",
            authors = "Unknown",
            format = BookFormat.EPUB,
            addedAt = 1L
        )
        val epubDoc = DocumentParsers.parseBook(context, epubEntity, emptyList())
        assertEquals("Pocket Kyoto", epubDoc.title)
        assertEquals("Hana Sato", epubDoc.authors)
        assertEquals(1, epubDoc.chapters.size)
        assertEquals("Cedar Gate", epubDoc.chapters[0].title)
        assertNotNull(epubDoc.footnotes["fn1"])
        assertTrue(epubDoc.images.containsKey("OEBPS/img/lantern.png"))

        // 2. TXT with Chapter markers
        val txtFile = File(context.cacheDir, "story.txt")
        txtFile.writeText(
            """
            Chapter 1 Dawn

            First morning paragraph in the quiet valley.

            Chapter 2 Dusk

            Evening settles softly over the cedar roof.
            """.trimIndent()
        )
        val txtEntity = BookEntity(
            uri = Uri.fromFile(txtFile).toString(),
            contentHash = "txt_hash",
            title = "Story",
            authors = "Anon",
            format = BookFormat.TXT,
            addedAt = 1L
        )
        val txtDoc = DocumentParsers.parseBook(context, txtEntity, emptyList())
        assertEquals(2, txtDoc.chapters.size)
        assertEquals("Chapter 1 Dawn", txtDoc.chapters[0].title)
        assertEquals("Chapter 2 Dusk", txtDoc.chapters[1].title)

        // 3. CBZ synthetic archive
        val cbzFile = File(context.cacheDir, "comic.cbz")
        ZipOutputStream(FileOutputStream(cbzFile)).use { zip ->
            zip.putNextEntry(ZipEntry("002.png"))
            zip.write(byteArrayOf(5, 6))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("001.png"))
            zip.write(byteArrayOf(7, 8))
            zip.closeEntry()
        }
        val cbzEntity = BookEntity(
            uri = Uri.fromFile(cbzFile).toString(),
            contentHash = "cbz_hash",
            title = "Comic",
            authors = "Artist",
            format = BookFormat.CBZ,
            addedAt = 1L
        )
        val cbzDoc = DocumentParsers.parseBook(context, cbzEntity, emptyList())
        assertTrue(cbzDoc.isComic)
        assertEquals(2, cbzDoc.chapters.size)
        assertEquals("001.png", cbzDoc.chapters[0].id)
        assertEquals("002.png", cbzDoc.chapters[1].id)
    }

    @Test
    fun knuthLiangHyphenatorInsertsSoftHyphensInLongWordsOnly() {
        val longHyphenated = KnuthLiangHyphenator.hyphenateText("comprehension and typography")
        assertTrue(longHyphenated.contains('\u00AD'))

        val shortText = KnuthLiangHyphenator.hyphenateText("cat dog fox")
        assertFalse(shortText.contains('\u00AD'))
    }

    @Test
    fun paginatorSearchReturnsContextSnippets() {
        val doc = ParsedDocument(
            title = "Test",
            authors = "Tester",
            chapters = listOf(
                ReaderChapter(
                    index = 0,
                    id = "ch1",
                    title = "Morning",
                    blocks = emptyList(),
                    plainText = "Warm light drifted across the cedar floor beside the quiet matcha kettle."
                )
            )
        )
        val matches = ReaderPaginator.search(doc, "matcha")
        assertEquals(1, matches.size)
        assertEquals("Morning", matches[0].chapterTitle)
        assertEquals("matcha", matches[0].matchText)
        assertTrue(matches[0].snippetBefore.contains("quiet"))
        assertTrue(matches[0].snippetAfter.contains("kettle"))
    }

    @Test
    fun repositoryLoadsBooksAndPersistsLocatorsAndBookThemeOverrides() = runTest {
        val fixtures = TestBookFixtures.createAll(context)
        val epubFixture = fixtures.first { it.file.name.endsWith(".epub") }
        val scanned = SafBookScanner.scanLocalBookFile(
            context = context,
            file = epubFixture.file,
            existing = null,
            shelfOrder = 0,
            fallbackTitle = epubFixture.title,
            fallbackAuthors = epubFixture.authors
        )
        val insertedId = db.bookDao().upsertBook(scanned)
        val book = repository.getBook(insertedId)!!

        val loadedDoc = repository.loadDocument(book)
        assertTrue(loadedDoc.chapters.isNotEmpty())

        val locator = ReaderLocator(
            chapterIndex = 1,
            chapterTitle = "Chapter 2: Rain on Cedar",
            charOffset = 42,
            progression = 0.65f,
            quoteAnchor = "gentle rain arrived"
        )
        repository.savePosition(insertedId, locator)

        val reloadedLocator = repository.getSavedLocator(insertedId)
        assertEquals(locator, reloadedLocator)

        val updatedBook = repository.getBook(insertedId)!!
        assertEquals(0.65f, updatedBook.progress, 0.001f)
        assertEquals(ReadingStatus.READING, updatedBook.status)

        repository.setBookThemeOverride(insertedId, "catppuccin_mocha")
        val bookSettings = db.bookSettingsDao().getBookSettings(insertedId)
        assertEquals("catppuccin_mocha", bookSettings?.themeId)
    }
}
