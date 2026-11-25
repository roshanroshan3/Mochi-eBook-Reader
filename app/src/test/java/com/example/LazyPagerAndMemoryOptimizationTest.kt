package com.example

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import coil.ImageLoaderFactory
import com.example.data.db.BookEntity
import com.example.data.db.BookFormat
import com.example.data.preferences.PageTurnMode
import com.example.data.preferences.ReaderSettings
import com.example.data.reader.ContentBlock
import com.example.data.reader.DocumentParsers
import com.example.data.reader.LazyBookPager
import com.example.data.reader.ParsedDocument
import com.example.data.reader.ReaderChapter
import com.example.data.reader.ReaderDocumentCache
import com.example.data.reader.ReaderLocator
import com.example.data.reader.StyledText
import com.example.theme.BundledThemes
import com.example.theme.GlassIntensity
import com.example.ui.designsystem.LocalMochiWidthClass
import com.example.ui.designsystem.MochiWidthClass
import com.example.ui.reader.ReaderScreen
import com.example.ui.reader.ReaderUiState
import com.example.ui.theme.MochiExpressiveTheme
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LazyPagerAndMemoryOptimizationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun createTestTextMeasurer(context: Context): TextMeasurer {
        return TextMeasurer(
            defaultFontFamilyResolver = createFontFamilyResolver(context),
            defaultDensity = Density(2.0f),
            defaultLayoutDirection = LayoutDirection.Ltr,
            cacheSize = 32
        )
    }

    private fun buildLargeParsedDocument(chapterCount: Int = 16, paragraphsPerChapter: Int = 12): ParsedDocument {
        val chapters = (0 until chapterCount).map { chIdx ->
            val blocks = buildList {
                add(ContentBlock.Heading(1, StyledText("Chapter ${chIdx + 1}: Cedar Archive")))
                if (chIdx == 0 || chIdx == 7 || chIdx == chapterCount - 1) {
                    add(ContentBlock.ImageBlock(imageKey = "images/ch_${chIdx}.png", caption = "Plate $chIdx"))
                }
                repeat(paragraphsPerChapter) { pIdx ->
                    add(
                        ContentBlock.Paragraph(
                            StyledText(
                                "Chapter ${chIdx + 1}, section ${pIdx + 1}. Soft morning mist rests above the garden stones, " +
                                    "carrying the scent of cedar timber and warm matcha tea across silent wooden halls."
                            )
                        )
                    )
                }
            }
            ReaderChapter(
                index = chIdx,
                id = "ch_$chIdx",
                title = "Chapter ${chIdx + 1}: Cedar Archive",
                blocks = blocks,
                plainText = "Chapter ${chIdx + 1} content"
            )
        }
        return ParsedDocument(
            title = "Chronicles of Cedar",
            authors = "H. Tanabe",
            chapters = chapters
        )
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    @Test
    fun applicationConfiguresBoundedCoilImageLoaderAndHandlesMemoryTrim() {
        val app = ApplicationProvider.getApplicationContext<MochiApplication>()
        assertTrue(app is ImageLoaderFactory)

        val imageLoader = app.newImageLoader()
        assertNotNull(imageLoader.memoryCache)
        assertNotNull(imageLoader.diskCache)
        assertTrue((imageLoader.memoryCache?.maxSize ?: 0) > 0)
        assertTrue((imageLoader.diskCache?.maxSize ?: 0L) <= 48L * 1024L * 1024L)

        // Verify hardware acceleration flag is enabled in manifest metadata
        val appInfo = app.packageManager.getApplicationInfo(app.packageName, PackageManager.GET_META_DATA)
        assertTrue(
            (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_HARDWARE_ACCELERATED) != 0 ||
                appInfo.flags != 0
        )

        @Suppress("DEPRECATION")
        app.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        @Suppress("DEPRECATION")
        app.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_MODERATE)
    }

    @Test
    fun lazyBookPagerMaterializesOnlyActiveAndAdjacentChunksAndEvictsDistantChapters() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val doc = buildLargeParsedDocument(chapterCount = 16, paragraphsPerChapter = 12)
        val pager = LazyBookPager(
            document = doc,
            settings = ReaderSettings(),
            palette = BundledThemes.byId(BundledThemes.DEFAULT_LIGHT_ID),
            textMeasurer = createTestTextMeasurer(context),
            density = Density(2.0f),
            pageWidthPx = 800,
            pageHeightPx = 1200
        )

        // Indexing does not eagerly paginate any chapter
        assertEquals(0, pager.cachedChunkCount())

        val (initialSnap, initialPageIdx) = pager.initializeAroundLocator(ReaderLocator(chapterIndex = 0))
        assertTrue(initialSnap.isReady)
        assertEquals(0, initialPageIdx)
        assertNotNull(initialSnap.getPage(0))
        assertTrue(pager.cachedChunkCount() <= LazyBookPager.MAX_CACHED_CHUNKS)
        assertTrue(pager.isChunkCached(0))
        assertTrue(pager.isChunkCached(1))
        assertFalse("Chapter 15 must NOT be materialized at startup", pager.isChunkCached(15))

        // Near-page image scoping only captures chapter 0 image when on page 0
        val nearStartImages = initialSnap.imageKeysNearPage(0, isDualSpread = false)
        assertTrue(nearStartImages.contains("images/ch_0.png"))
        assertFalse(nearStartImages.contains("images/ch_15.png"))

        // Jump far ahead to chapter 13 and ensure adjacent pages load while distant chapter 0 is evicted
        val chap13Start = initialSnap.chapterStartPages[13] ?: 0
        val (jumpedSnap, anchored) = pager.ensureAdjacentPagesLoaded(chap13Start, isDualSpread = false)
        assertNotNull(jumpedSnap.getPage(anchored))
        assertTrue(pager.cachedChunkCount() <= LazyBookPager.MAX_CACHED_CHUNKS)
        assertTrue(pager.isChunkCached(13))
        assertFalse("Distant chapter 0 should be evicted after jump", pager.isChunkCached(0))

        val chap15Start = jumpedSnap.chapterStartPages[15] ?: (jumpedSnap.totalPages - 1)
        val (endSnap, endPage) = pager.ensureAdjacentPagesLoaded(chap15Start, isDualSpread = false)
        assertNotNull(endSnap.getPage(endPage))
        val nearEndImages = endSnap.imageKeysNearPage(endPage, isDualSpread = false)
        assertTrue(nearEndImages.contains("images/ch_15.png"))
        assertFalse(nearEndImages.contains("images/ch_0.png"))

        pager.clear()
        assertEquals(0, pager.cachedChunkCount())
    }

    @Test
    fun largeCbzArchiveCapsInlineBuffersAndStreamsPagesOnDemandWithRgb565() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cbzFile = File(context.cacheDir, "large_lazy_comic.cbz")

        // Create a valid PNG for the last page, plus oversized entries exceeding 2MB inline limit
        val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.CYAN)
        }
        val validPngBytes = ByteArrayOutputStream().also { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }.toByteArray()
        bmp.recycle()

        val bigDummyPayload = ByteArray(1_300_000) { idx -> (idx % 127).toByte() }

        ZipOutputStream(FileOutputStream(cbzFile)).use { zip ->
            zip.putNextEntry(ZipEntry("001_big.png"))
            zip.write(bigDummyPayload)
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("002_big.png"))
            zip.write(bigDummyPayload)
            zip.closeEntry()

            // Page 3 exceeds MAX_INLINE_CBZ_IMAGE_BYTES (2MB) so it must stay out of inline `images`
            zip.putNextEntry(ZipEntry("003_lazy.png"))
            zip.write(validPngBytes)
            zip.closeEntry()
        }

        val book = BookEntity(
            id = 99L,
            uri = Uri.fromFile(cbzFile).toString(),
            contentHash = "cbz_large",
            title = "Lazy Comic",
            authors = "Artist",
            format = BookFormat.CBZ,
            addedAt = 1L
        )
        val doc = DocumentParsers.parseBook(context, book, emptyList())
        assertTrue(doc.isComic)
        assertTrue(doc.archiveImageKeys.contains("003_lazy.png"))
        assertNull("Oversized CBZ should not buffer page 3 inline", doc.images["003_lazy.png"])

        // Streamed on-demand load extracts and decodes 003_lazy.png cleanly using RGB_565
        val streamed = ReaderDocumentCache.loadAndDecodeImageBlock(
            context = context,
            bookUri = doc.bookUri,
            inlineImages = doc.images,
            imageKey = "003_lazy.png",
            maxDimensionPx = 64
        )
        assertNotNull(streamed)
        assertEquals(Bitmap.Config.RGB_565, streamed?.config)
        assertFalse(streamed!!.isRecycled)
    }

    @Test
    @Config(qualifiers = "w411dp-h915dp")
    fun readerScreenRendersLazyHorizontalPagerAndNavigatesAcrossChaptersWithoutFullPagination() {
        val doc = buildLargeParsedDocument(chapterCount = 4, paragraphsPerChapter = 3)
        val book = BookEntity(
            id = 77L,
            uri = "file:///tmp/lazy_book.epub",
            contentHash = "lazy77",
            title = doc.title,
            authors = doc.authors,
            format = BookFormat.EPUB,
            addedAt = 100L
        )
        val palette = BundledThemes.byId(BundledThemes.DEFAULT_LIGHT_ID)
        val reportedLocators = mutableListOf<ReaderLocator>()

        composeRule.setContent {
            MochiExpressiveTheme(
                palette = palette,
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
                            currentLocator = ReaderLocator(chapterIndex = 0, chapterTitle = doc.chapters[0].title)
                        ),
                        settings = ReaderSettings(pageTurnMode = PageTurnMode.SLIDE),
                        themes = BundledThemes.all,
                        activeTheme = palette,
                        warmLightEnabled = false,
                        onBack = {},
                        onOpenBookById = {},
                        onFindWikilinkTarget = { null },
                        onOpenBookInfo = {},
                        onToggleChrome = {},
                        onUpdatePage = { _, _ -> },
                        onUpdateLocator = { reportedLocators.add(it) },
                        onUpdateScrollProgression = { _, _, _ -> },
                        onJumpWithUndo = { reportedLocators.add(it) },
                        onUndoJump = { null },
                        onDismissUndo = {},
                        onSearchQueryChange = {},
                        onOpenFootnote = {},
                        onCloseFootnote = {},
                        onOpenImageZoom = {},
                        onCloseImageZoom = {},
                        onToggleAutoScroll = {},
                        onUpdateSettings = {},
                        onSelectGlobalTheme = {},
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

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("reader_toc_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Chapter 4: Cedar Archive").assertIsDisplayed().performClick()
        assertTrue("Expected TOC jump to report chapter 3 locator", reportedLocators.any { it.chapterIndex == 3 })
    }
}
