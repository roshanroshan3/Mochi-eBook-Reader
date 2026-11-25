package com.example

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.BookEntity
import com.example.data.db.BookFormat
import com.example.data.db.FolderEntity
import com.example.data.db.MochiDatabase
import com.example.data.library.GutenbergRepository
import com.example.data.library.LibraryRepository
import com.example.theme.BundledThemes
import com.example.ui.designsystem.LiquidGlassSlider
import com.example.ui.designsystem.LiquidGlassSliderMath
import com.example.ui.theme.MochiExpressiveTheme
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GutenbergAndSliderTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sliderMathMapsThumbCentersAccurately() {
        // Total width 220px, thumb 20px => usable travel [10px .. 210px]
        assertEquals(0f, LiquidGlassSliderMath.fractionForX(10f, 220f, 20f), 0.001f)
        assertEquals(0.5f, LiquidGlassSliderMath.fractionForX(110f, 220f, 20f), 0.001f)
        assertEquals(1f, LiquidGlassSliderMath.fractionForX(210f, 220f, 20f), 0.001f)
        assertEquals(0f, LiquidGlassSliderMath.fractionForX(-10f, 220f, 20f), 0.001f)
        assertEquals(1f, LiquidGlassSliderMath.fractionForX(300f, 220f, 20f), 0.001f)
    }

    @Test
    fun liquidGlassSliderRespondsToHorizontalDragAndTapInsideScrollableColumnWithUpdatedClosures() {
        var sliderValue by mutableFloatStateOf(0.1f)
        var finishedCount by mutableIntStateOf(0)
        var callbackGeneration by mutableIntStateOf(1)
        var lastSeenGeneration by mutableIntStateOf(0)

        composeRule.setContent {
            val palette = BundledThemes.byId(BundledThemes.DEFAULT_LIGHT_ID)
            val capturedGen = callbackGeneration
            MochiExpressiveTheme(palette = palette) {
                Column(
                    modifier = Modifier
                        .width(320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    LiquidGlassSlider(
                        value = sliderValue,
                        onValueChange = { next ->
                            lastSeenGeneration = capturedGen
                            sliderValue = next
                        },
                        onValueChangeFinished = { finishedCount++ },
                        valueRange = 0f..1f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_glass_slider")
                    )
                }
            }
        }

        // Trigger recomposition updating onValueChange closure before interacting
        composeRule.runOnIdle { callbackGeneration = 2 }
        composeRule.waitForIdle()

        // Horizontal drag starting on/near thumb should drag cleanly even inside verticalScroll
        composeRule.onNodeWithTag("test_glass_slider").performTouchInput {
            swipeRight(startX = width * 0.1f, endX = width * 0.85f, durationMillis = 120)
        }
        composeRule.waitForIdle()

        assertTrue("Expected slider value to increase after swipeRight, was $sliderValue", sliderValue > 0.6f)
        assertEquals("Slider must invoke latest recomposed onValueChange closure", 2, lastSeenGeneration)
        assertTrue("onValueChangeFinished should fire on release", finishedCount >= 1)

        // Tap toward the start resets cleanly toward lower bound
        val prevFinished = finishedCount
        composeRule.onNodeWithTag("test_glass_slider").performTouchInput {
            down(Offset(width * 0.15f, height * 0.5f))
            up()
        }
        composeRule.waitForIdle()

        assertTrue("Expected tap to move slider left, was $sliderValue", sliderValue < 0.35f)
        assertTrue(finishedCount > prevFinished)
    }

    @Test
    fun gutenbergCatalogParserPrioritizesIllustratedEpubAndFormatsAuthors() {
        val sampleJson = """
            {
              "count": 2,
              "next": "https://gutendex.com/books?page=2",
              "previous": null,
              "results": [
                {
                  "id": 1342,
                  "title": "Pride and Prejudice",
                  "authors": [{"name": "Austen, Jane"}],
                  "subjects": ["England -- Social life and customs -- 19th century -- Fiction"],
                  "bookshelves": ["Best Books Ever Listings"],
                  "languages": ["en"],
                  "download_count": 58420,
                  "formats": {
                    "application/epub+zip": "https://www.gutenberg.org/ebooks/1342.epub.noimages",
                    "application/epub+zip; charset=utf-8": "https://www.gutenberg.org/ebooks/1342.epub.images",
                    "image/jpeg": "https://www.gutenberg.org/cache/epub/1342/pg1342.cover.medium.jpg"
                  }
                },
                {
                  "id": 84,
                  "title": "Frankenstein",
                  "authors": [{"name": "Shelley, Mary Wollstonecraft"}],
                  "subjects": ["Science fiction"],
                  "bookshelves": ["Gothic Fiction"],
                  "languages": ["en"],
                  "download_count": 41200,
                  "formats": {
                    "application/octet-stream": "https://www.gutenberg.org/files/84/84-0.zip",
                    "text/plain; charset=utf-8": "https://www.gutenberg.org/files/84/84-0.txt"
                  }
                }
              ]
            }
        """.trimIndent()

        val page = GutenbergRepository.parseCatalogJson(sampleJson)
        assertEquals(2, page.count)
        assertEquals("https://gutendex.com/books?page=2", page.nextUrl)
        assertEquals(2, page.results.size)

        val pride = page.results[0]
        assertEquals(1342, pride.id)
        assertEquals("Pride and Prejudice", pride.title)
        assertEquals("Jane Austen", pride.authors)
        assertEquals(BookFormat.EPUB, pride.format)
        assertTrue(pride.downloadUrl.contains(".epub.images"))
        assertEquals("https://www.gutenberg.org/cache/epub/1342/pg1342.cover.medium.jpg", pride.coverUrl)

        val frank = page.results[1]
        assertEquals(BookFormat.TXT, frank.format)
        assertEquals("https://www.gutenberg.org/files/84/84-0.txt", frank.downloadUrl)
        assertEquals("Mary Wollstonecraft Shelley", frank.authors)
    }

    @Test
    fun libraryInitializeDefaultsSeedsZeroSamplesAndPurgesLegacySampleRows() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, MochiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            // Pre-populate legacy sample row + legacy sample directory
            val legacyDir = File(context.filesDir, "samples").apply { mkdirs() }
            File(legacyDir, "old_sample.txt").writeText("old")
            db.folderDao().upsertFolder(
                FolderEntity("samples://local", "Mochi Samples", 1L, 1L, 1)
            )
            db.bookDao().upsertBook(
                BookEntity(
                    uri = "file:///samples/old_sample.txt",
                    contentHash = "oldhash",
                    title = "Legacy Sample",
                    authors = "Tester",
                    format = BookFormat.TXT,
                    addedAt = 1L,
                    folderTreeUri = "samples://local",
                    isSample = true
                )
            )
            // Also keep a real user-imported book untouched
            db.bookDao().upsertBook(
                BookEntity(
                    uri = "content://user/my_real_book.epub",
                    contentHash = "realhash",
                    title = "User Book",
                    authors = "Real Author",
                    format = BookFormat.EPUB,
                    addedAt = 2L,
                    folderTreeUri = "imported://documents",
                    isSample = false
                )
            )

            val repo = LibraryRepository(
                context = context,
                bookDao = db.bookDao(),
                folderDao = db.folderDao(),
                collectionDao = db.collectionDao(),
                tagDao = db.tagDao(),
                themeDao = db.themeDao()
            )
            repo.initializeDefaults()

            val remainingBooks = db.bookDao().getAllBooksOnce()
            assertEquals(1, remainingBooks.size)
            assertEquals("User Book", remainingBooks.first().title)
            assertFalse(db.folderDao().getAllFoldersOnce().any { it.treeUri == "samples://local" })
            assertFalse(legacyDir.exists())
        } finally {
            db.close()
        }
    }
}
