package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.library.SafBookScanner
import com.example.theme.BundledThemes
import com.example.theme.ThemePackManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MochiLibraryRobolectricTest {

    @Test
    fun localScannerPreservesOriginalFileHashes() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fixtures = TestBookFixtures.createAll(context)
        assertEquals(5, fixtures.size)

        val epub = fixtures.first { it.file.name.endsWith(".epub") }
        val hashBefore = SafBookScanner.computeSha256 { FileInputStream(epub.file) }

        val scanned = SafBookScanner.scanLocalBookFile(
            context = context,
            file = epub.file,
            existing = null,
            shelfOrder = 0,
            fallbackTitle = epub.title,
            fallbackAuthors = epub.authors
        )
        val hashAfter = SafBookScanner.computeSha256 { FileInputStream(epub.file) }

        assertEquals(hashBefore, hashAfter)
        assertEquals(hashBefore, scanned.contentHash)
        assertEquals("The Secret Garden of Kyoto", scanned.title)
        val coverPath = scanned.coverPath
        assertTrue(coverPath != null && File(coverPath).exists())
    }

    @Test
    fun themePackExportImportRoundTripAndZipSlipGuard() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packFile = File(context.cacheDir, "sakura.mochitheme")
        val palette = BundledThemes.byId(BundledThemes.DEFAULT_LIGHT_ID)

        val exportRes = ThemePackManager.exportThemePack(context, Uri.fromFile(packFile), palette)
        assertTrue(exportRes.isSuccess)

        val importRes = ThemePackManager.importThemePack(context, Uri.fromFile(packFile))
        assertTrue(importRes.isSuccess)
        val imported = importRes.getOrThrow().palette
        assertEquals(palette.name, imported.name)
        assertFalse(imported.builtIn)

        // Verify zip-slip rejection
        val badZip = File(context.cacheDir, "malicious.mochitheme")
        ZipOutputStream(FileOutputStream(badZip)).use { zip ->
            zip.putNextEntry(ZipEntry("../evil.txt"))
            zip.write("bad".toByteArray())
            zip.closeEntry()
        }
        val badImport = ThemePackManager.importThemePack(context, Uri.fromFile(badZip))
        assertTrue(badImport.isFailure)
    }
}
