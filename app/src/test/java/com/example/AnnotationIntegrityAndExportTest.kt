package com.example

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.annotations.AnnotationExporter
import com.example.data.annotations.AnnotationRepository
import com.example.data.annotations.AnnotationType
import com.example.data.annotations.AnnotationWithBook
import com.example.data.annotations.CozySticker
import com.example.data.annotations.HighlightColorHelper
import com.example.data.annotations.HighlightStyle
import com.example.data.annotations.QuoteCardGenerator
import com.example.data.db.MochiDatabase
import com.example.data.library.SafBookScanner
import com.example.data.preferences.AutoDarkMode
import com.example.data.preferences.MochiPreferences
import com.example.data.reader.ReaderLocator
import com.example.theme.BundledThemes
import com.example.theme.ThemePackManager
import kotlinx.coroutines.flow.first
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
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AnnotationIntegrityAndExportTest {

    private lateinit var context: Context
    private lateinit var db: MochiDatabase
    private lateinit var annotationRepo: AnnotationRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, MochiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        annotationRepo = AnnotationRepository(
            annotationDao = db.annotationDao(),
            exportTemplateDao = db.exportTemplateDao(),
            bookDao = db.bookDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            var read = input.read(buf)
            while (read > 0) {
                digest.update(buf, 0, read)
                read = input.read(buf)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Test
    fun creatingEditingExportingAndDeletingAnnotationsNeverModifiesOriginalBookFiles() = runTest {
        val samples = TestBookFixtures.createAll(context)
        assertTrue(samples.isNotEmpty())

        // Record SHA-256 of every original file before touching annotations
        val hashesBefore = samples.associate { it.file.absolutePath to sha256Hex(it.file) }

        val bookIds = samples.mapIndexed { idx, sample ->
            val entity = SafBookScanner.scanLocalBookFile(
                context = context,
                file = sample.file,
                existing = null,
                shelfOrder = idx,
                fallbackTitle = sample.title,
                fallbackAuthors = sample.authors
            )
            db.bookDao().upsertBook(entity)
        }
        val firstBookId = bookIds.first()
        val firstBook = db.bookDao().getBookById(firstBookId)!!

        annotationRepo.ensureDefaultTemplates()
        val templates = annotationRepo.observeTemplates().first()
        assertTrue(templates.size >= 3)

        // Create highlights across all 5 styles, plus a Markdown note & bookmark
        val created = mutableListOf<Long>()
        HighlightStyle.entries.forEachIndexed { idx, style ->
            val hl = annotationRepo.createHighlight(
                bookId = firstBookId,
                locator = ReaderLocator(
                    chapterIndex = 0,
                    chapterTitle = "Chapter 1",
                    charOffset = idx * 20,
                    progression = 0.15f + idx * 0.05f,
                    quoteAnchor = "Sample quote $idx"
                ),
                quoteExact = "A quiet passage, \"quoted\" on line $idx",
                colorRole = idx % 8,
                style = style,
                noteMarkdown = "My **Markdown** thought #focus",
                tags = "focus, study",
                sticker = CozySticker.SPARKLE
            )
            created.add(hl.id)
        }

        val bm = annotationRepo.createBookmark(
            bookId = firstBookId,
            locator = ReaderLocator(chapterIndex = 0, chapterTitle = "Chapter 1", progression = 0.42f),
            label = "Key Scene",
            snippet = "Cedar roof"
        )
        assertEquals(AnnotationType.BOOKMARK.name, bm.type)

        // Update an annotation note
        val stored = annotationRepo.getBookAnnotationsOnce(firstBookId)
        assertEquals(HighlightStyle.entries.size + 1, stored.size)
        val firstAnn = stored.first()
        annotationRepo.upsertAnnotation(firstAnn.copy(noteMarkdown = "Updated Markdown note"))

        // Run all exports (Markdown, JSON, CSV)
        val allWithBooks = annotationRepo.getAllAnnotationsOnce()
        val mdExport = AnnotationExporter.exportBookMarkdown(firstBook, stored, templates.first().body)
        val jsonExport = AnnotationExporter.exportJson(allWithBooks)
        val csvExport = AnnotationExporter.exportCsv(allWithBooks)

        assertTrue(mdExport.contains(firstBook.title))
        assertTrue(mdExport.contains("#focus"))
        assertTrue(jsonExport.contains("A quiet passage"))
        assertTrue(csvExport.contains("\"A quiet passage, \"\"quoted\"\" on line 0\""))

        // Generate 1080x1350 PNG Quote Card
        val cardBmp = QuoteCardGenerator.generateQuoteCardBitmap(
            context = context,
            quote = firstAnn.quoteExact,
            note = firstAnn.noteMarkdown,
            bookTitle = firstBook.title,
            bookAuthor = firstBook.authors,
            chapterTitle = "Chapter 1",
            palette = BundledThemes.byId(BundledThemes.DEFAULT_LIGHT_ID),
            fontId = "literata",
            importedFonts = emptyList(),
            sticker = CozySticker.MOCHI,
            colorRole = 0,
            colorBlindSafe = false
        )
        assertEquals(1080, cardBmp.width)
        assertEquals(1350, cardBmp.height)
        val shareUri = QuoteCardGenerator.createShareablePngUri(context, cardBmp).getOrThrow()
        assertNotNull(shareUri)

        // Delete one annotation
        annotationRepo.deleteAnnotation(created.first())
        assertEquals(HighlightStyle.entries.size, annotationRepo.getBookAnnotationsOnce(firstBookId).size)

        // Verify EVERY original book file hash is byte-for-byte identical
        samples.forEach { sample ->
            val afterHash = sha256Hex(sample.file)
            assertEquals(
                "Original file ${sample.file.name} was altered!",
                hashesBefore[sample.file.absolutePath],
                afterHash
            )
        }
    }

    @Test
    fun exporterSubstitutesAllTemplatePlaceholdersAndCitationCopy() = runTest {
        val firstFixture = TestBookFixtures.createAll(context).first()
        val book = SafBookScanner.scanLocalBookFile(
            context = context,
            file = firstFixture.file,
            existing = null,
            shelfOrder = 0,
            fallbackTitle = firstFixture.title,
            fallbackAuthors = firstFixture.authors
        ).copy(id = 99L)

        val tpl = "T={{title}}|A={{author}}|C={{chapter}}|P={{page}}|Q={{quote}}|N={{note}}|K={{color}}|G={{tags}}|D={{date}}"
        val ann = com.example.data.db.AnnotationEntity(
            id = 5L,
            bookId = 99L,
            type = AnnotationType.HIGHLIGHT.name,
            colorRole = 2,
            locatorJson = ReaderLocator.toJson(ReaderLocator(chapterIndex = 1, chapterTitle = "Dawn", progression = 0.5f)),
            quoteExact = "Silence holds the room.",
            noteMarkdown = "Great opening",
            tags = "opening, craft",
            style = HighlightStyle.WASHI_TAPE.name,
            createdAt = 1700000000000L,
            updatedAt = 1700000000000L
        )

        val rendered = AnnotationExporter.applyTemplate(tpl, ann, book)
        assertTrue(rendered.contains("T=${book.title}"))
        assertTrue(rendered.contains("C=Dawn"))
        assertTrue(rendered.contains("P=50% (ch. 2)"))
        assertTrue(rendered.contains("Q=Silence holds the room."))
        assertTrue(rendered.contains("N=Great opening"))
        assertTrue(rendered.contains("K=Matcha"))
        assertTrue(rendered.contains("G=#opening #craft"))

        val citationCopy = AnnotationExporter.formatCopyMarkdown(ann, book, withCitation = true)
        assertTrue(citationCopy.startsWith("> Silence holds the room."))
        assertTrue(citationCopy.contains("— *${book.title}*"))
        assertTrue(citationCopy.contains("Dawn (50%)"))

        val plainCopy = AnnotationExporter.formatQuickSelectionCopy(
            quote = "Silence holds the room.",
            bookTitle = book.title,
            bookAuthor = book.authors,
            chapterTitle = "Dawn",
            progression = 0.5f,
            withCitation = false
        )
        assertEquals("Silence holds the room.", plainCopy)
    }

    @Test
    fun colorBlindHighlightPaletteAndThemeSwitchingWorkImmediately() = runTest {
        val lightTheme = BundledThemes.byId(BundledThemes.DEFAULT_LIGHT_ID)
        val normal = HighlightColorHelper.resolveColors(lightTheme, colorBlindSafe = false)
        val okabeIto = HighlightColorHelper.resolveColors(lightTheme, colorBlindSafe = true)
        assertEquals(8, normal.size)
        assertEquals(8, okabeIto.size)
        assertFalse(normal == okabeIto)

        // Verify every bundled theme survives JSON roundtrip
        BundledThemes.all.forEach { palette ->
            val json = ThemePackManager.toJson(palette)
            val decoded = ThemePackManager.fromJson(json)
            assertNotNull("Failed to decode ${palette.id}", decoded)
            assertEquals(palette.id, decoded?.id)
            assertEquals(palette.background, decoded?.background)
        }

        // Verify selectTheme immediately switches active theme & disables Auto/Dynamic override
        val prefs = MochiPreferences(context)
        prefs.setAutoDarkMode(AutoDarkMode.FOLLOW_SYSTEM)
        prefs.setMaterialYouDynamic(true)
        prefs.selectTheme("catppuccin_mocha", isDark = true)

        val state = prefs.state.first()
        assertEquals("catppuccin_mocha", state.activeThemeId)
        assertEquals("catppuccin_mocha", state.activeDarkThemeId)
        assertEquals(AutoDarkMode.MANUAL, state.autoDarkMode)
        assertFalse(state.useMaterialYouDynamic)
    }
}
