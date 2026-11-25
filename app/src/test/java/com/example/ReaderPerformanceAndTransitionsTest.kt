package com.example

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.data.db.BookEntity
import com.example.data.db.BookFormat
import com.example.data.preferences.PageTurnMode
import com.example.data.preferences.ReaderSettings
import com.example.data.reader.ContentBlock
import com.example.data.reader.ParsedDocument
import com.example.data.reader.PdfPageEngine
import com.example.data.reader.PdfReflowExtractor
import com.example.data.reader.ReaderChapter
import com.example.data.reader.ReaderLocator
import com.example.data.reader.StyledText
import com.example.theme.BundledThemes
import com.example.theme.GlassIntensity
import com.example.ui.designsystem.LocalMochiWidthClass
import com.example.ui.designsystem.MochiWidthClass
import com.example.ui.reader.PdfViewportMath
import com.example.ui.reader.ReaderScreen
import com.example.ui.reader.ReaderUiState
import com.example.ui.theme.MochiExpressiveTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReaderPerformanceAndTransitionsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun pdfViewportClampPanZeroesAt1xAndBoundsWhenZoomed() {
        assertEquals(Offset.Zero, PdfViewportMath.clampPan(Offset(250f, -180f), zoomScale = 1.0f, viewportW = 1080f, viewportH = 1920f))
        assertEquals(Offset.Zero, PdfViewportMath.clampPan(Offset(100f, 100f), zoomScale = 1.01f, viewportW = 1080f, viewportH = 1920f))

        // At 2.0x on 1000x1600, maxX = 500, maxY = 800
        val clamped = PdfViewportMath.clampPan(Offset(1200f, -1500f), zoomScale = 2.0f, viewportW = 1000f, viewportH = 1600f)
        assertEquals(500f, clamped.x, 0.01f)
        assertEquals(-800f, clamped.y, 0.01f)

        val inside = PdfViewportMath.clampPan(Offset(-120f, 340f), zoomScale = 2.0f, viewportW = 1000f, viewportH = 1600f)
        assertEquals(-120f, inside.x, 0.01f)
        assertEquals(340f, inside.y, 0.01f)
    }

    @Test
    fun pdfPageEngineBucketWidthAndSmartDarkInvertStayBounded() {
        assertEquals(384, PdfPageEngine.bucketWidth(200))
        assertEquals(1152, PdfPageEngine.bucketWidth(1080))
        assertEquals(2304, PdfPageEngine.bucketWidth(4000))

        val src = Bitmap.createBitmap(128, 160, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
            // Paint central ink region so margin crop detection trims whitespace cleanly
            for (y in 40..120) {
                for (x in 32..96) {
                    setPixel(x, y, Color.BLACK)
                }
            }
        }
        val inverted = PdfPageEngine.applySmartDarkInvert(src)
        assertTrue(inverted !== src)
        assertEquals(128, inverted.width)
        assertEquals(160, inverted.height)
        assertEquals(Bitmap.Config.ARGB_8888, inverted.config)
        assertFalse(inverted.isRecycled)

        val bounds = PdfPageEngine.detectNormalizedContentBounds(src)
        assertTrue("Expected left margin crop > 0.15, got ${bounds.left}", bounds.left > 0.15f)
        assertTrue("Expected right margin crop < 0.85, got ${bounds.right}", bounds.right < 0.85f)
        assertTrue("Expected top margin crop > 0.15, got ${bounds.top}", bounds.top > 0.15f)
        assertTrue("Expected bottom margin crop < 0.85, got ${bounds.bottom}", bounds.bottom < 0.85f)

        src.recycle()
        inverted.recycle()
    }

    @Test
    fun pdfReflowDecodesWinAnsiPunctuationAndLigaturesAndExtractsOutlineChapters() {
        val baos = ByteArrayOutputStream()
        fun emitBytes(bytes: ByteArray) = baos.write(bytes)
        fun emit(s: String) = baos.write(s.toByteArray(Charsets.ISO_8859_1))

        val streamBytes = ByteArrayOutputStream().apply {
            write("BT /F1 12 Tf 72 720 Td (".toByteArray(Charsets.ISO_8859_1))
            // \x93Smart quotes\x94, em-dash \x97, ellipsis \x85, euro \x80
            write(byteArrayOf(0x93.toByte()))
            write("Craft".toByteArray(Charsets.ISO_8859_1))
            write(byteArrayOf(0x94.toByte(), 0x20, 0x97.toByte(), 0x20))
            write("fine".toByteArray(Charsets.ISO_8859_1))
            write(byteArrayOf(0x85.toByte(), 0x20, 0x80.toByte()))
            write("100) Tj 0 -16 Td ".toByteArray(Charsets.ISO_8859_1))
            write("<FEFFFB01> Tj (rst ) Tj <FEFFFB02> Tj (ow) Tj ET".toByteArray(Charsets.ISO_8859_1))
        }.toByteArray()

        val page2Stream = "BT /F1 12 Tf 72 720 Td (Second Chapter Body) Tj ET".toByteArray(Charsets.ISO_8859_1)

        emit("%PDF-1.4\n")
        emit("1 0 obj\n<< /Type /Catalog /Pages 2 0 R /Outlines 6 0 R >>\nendobj\n")
        emit("2 0 obj\n<< /Type /Pages /Kids [3 0 R 4 0 R] /Count 2 >>\nendobj\n")
        emit("3 0 obj\n<< /Type /Page /Parent 2 0 R /Contents 5 0 R >>\nendobj\n")
        emit("4 0 obj\n<< /Type /Page /Parent 2 0 R /Contents 9 0 R >>\nendobj\n")

        emit("5 0 obj\n<< /Length ${streamBytes.size} >>\nstream\n")
        emitBytes(streamBytes)
        emit("\nendstream\nendobj\n")

        emit("6 0 obj\n<< /Type /Outlines /First 7 0 R /Last 8 0 R /Count 2 >>\nendobj\n")
        emit("7 0 obj\n<< /Title (Prologue & Timber) /Parent 6 0 R /Next 8 0 R /Dest [3 0 R /Fit] >>\nendobj\n")
        emit("8 0 obj\n<< /Title (Chapter 2\\: Joinery) /Parent 6 0 R /Prev 7 0 R /A << /S /GoTo /D [4 0 R /XYZ 0 792 0] >> >>\nendobj\n")

        emit("9 0 obj\n<< /Length ${page2Stream.size} >>\nstream\n")
        emitBytes(page2Stream)
        emit("\nendstream\nendobj\n")
        emit("trailer\n<< /Root 1 0 R >>\n%%EOF\n")

        val pdfBytes = baos.toByteArray()
        val outlines = PdfReflowExtractor.extractOutlineTitlesFromBytes(pdfBytes)
        assertEquals("Prologue & Timber", outlines[0])
        assertEquals("Chapter 2: Joinery", outlines[1])

        val reflow = PdfReflowExtractor.extractFromBytes(pdfBytes, expectedPageCount = 2)
        val p1 = reflow.chapters[0].plainText
        assertTrue("Expected smart quotes in '$p1'", p1.contains("\u201CCraft\u201D"))
        assertTrue("Expected em-dash in '$p1'", p1.contains("\u2014"))
        assertTrue("Expected ellipsis in '$p1'", p1.contains("\u2026"))
        assertTrue("Expected euro sign in '$p1'", p1.contains("\u20AC100"))
        assertTrue("Expected normalized ligatures first/flow in '$p1'", p1.contains("first") && p1.contains("flow"))
    }

    @Test
    @Config(qualifiers = "w411dp-h915dp")
    fun streamlinedReaderChromeExposesOverflowMenuAndQuickThemeChip() {
        val palette = BundledThemes.byId(BundledThemes.DEFAULT_LIGHT_ID)
        val book = BookEntity(
            id = 42L,
            uri = "file:///tmp/sample.epub",
            contentHash = "hash42",
            title = "Quiet Architecture",
            authors = "K. Sato",
            format = BookFormat.EPUB,
            addedAt = 1000L
        )
        val doc = ParsedDocument(
            title = book.title,
            authors = book.authors,
            chapters = listOf(
                ReaderChapter(
                    index = 0,
                    id = "c1",
                    title = "Chapter 1: Light & Space",
                    blocks = listOf(
                        ContentBlock.Heading(1, StyledText("Chapter 1: Light & Space")),
                        ContentBlock.Paragraph(StyledText("Daylight shapes every quiet room without cluttering the eye."))
                    ),
                    plainText = "Chapter 1: Light & Space\nDaylight shapes every quiet room without cluttering the eye."
                )
            )
        )
        var themeToggleClicks = 0
        var activeTheme by mutableStateOf(palette)

        composeRule.setContent {
            MochiExpressiveTheme(
                palette = activeTheme,
                glassIntensity = GlassIntensity.RICH,
                solidChrome = false,
                reduceMotion = true,
                useDynamicColor = false,
                matchSystemBars = false,
                warmLightEnabled = false,
                warmLightIntensity = 0f
            ) {
                CompositionLocalProvider(LocalMochiWidthClass provides MochiWidthClass.COMPACT) {
                    ReaderScreen(
                        state = ReaderUiState(
                            isLoading = false,
                            book = book,
                            document = doc,
                            chromeVisible = true,
                            currentLocator = ReaderLocator(chapterIndex = 0, chapterTitle = "Chapter 1: Light & Space")
                        ),
                        settings = ReaderSettings(pageTurnMode = PageTurnMode.SLIDE),
                        themes = BundledThemes.all,
                        activeTheme = activeTheme,
                        warmLightEnabled = false,
                        onBack = {},
                        onOpenBookById = {},
                        onFindWikilinkTarget = { null },
                        onOpenBookInfo = {},
                        onToggleChrome = {},
                        onUpdatePage = { _, _ -> },
                        onUpdateScrollProgression = { _, _, _ -> },
                        onJumpWithUndo = {},
                        onUndoJump = { null },
                        onDismissUndo = {},
                        onSearchQueryChange = {},
                        onOpenFootnote = {},
                        onCloseFootnote = {},
                        onOpenImageZoom = {},
                        onCloseImageZoom = {},
                        onToggleAutoScroll = {},
                        onUpdateSettings = {},
                        onSelectGlobalTheme = {
                            themeToggleClicks++
                            activeTheme = it
                        },
                        onSetBookThemeOverride = {},
                        onToggleWarmLight = {},
                        onImportFont = {},
                        onCreateHighlight = { _, _, _, _, _, _, _, _, _ -> },
                        onUpdateAnnotation = { _, _, _, _, _, _ -> },
                        onCreateOrUpdateBookmark = { _, _, _, _, _ -> },
                        onDeleteAnnotation = {},
                        onSaveTemplate = { _, _, _ -> },
                        onDeleteTemplate = {},
                        onSaveVaultFolderUri = {},
                        onShowMessage = {}
                    )
                }
            }
        }

        // Top floating pill retains core actions cleanly + overflow trigger
        composeRule.onNodeWithTag("reader_back_button").assertIsDisplayed()
        composeRule.onNodeWithTag("reader_toc_button").assertIsDisplayed()
        composeRule.onNodeWithTag("reader_aa_button").assertIsDisplayed()
        composeRule.onNodeWithTag("reader_more_button").assertIsDisplayed()

        // 1-tap bottom quick theme toggle works immediately
        composeRule.onNodeWithTag("quick_theme_toggle_chip").assertIsDisplayed().performClick()
        assertEquals(1, themeToggleClicks)

        // Overflow menu reveals secondary tools cleanly on compact screens
        composeRule.onNodeWithTag("reader_more_button").performClick()
        composeRule.onNodeWithTag("reader_bookmark_button").assertIsDisplayed()
        composeRule.onNodeWithTag("reader_annotations_button").assertIsDisplayed()
        composeRule.onNodeWithTag("reader_studio_button").assertIsDisplayed()
        composeRule.onNodeWithTag("reader_command_palette_button").assertIsDisplayed()
    }
}
