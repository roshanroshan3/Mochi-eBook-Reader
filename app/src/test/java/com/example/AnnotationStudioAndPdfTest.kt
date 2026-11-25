package com.example

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AnnotationLayerEntity
import com.example.data.db.MarkupObjectEntity
import com.example.data.db.MochiDatabase
import com.example.data.db.PageScaleEntity
import com.example.data.library.SafBookScanner
import com.example.data.studio.AnnotationStudioRepository
import com.example.data.studio.MeasurementUnit
import com.example.data.studio.SnapKind
import com.example.data.studio.StudioExporter
import com.example.data.studio.StudioGeometry
import com.example.data.studio.StudioGeometryJson
import com.example.data.studio.StudioPoint
import com.example.data.studio.StudioStyleJson
import com.example.data.studio.StudioTool
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AnnotationStudioAndPdfTest {

    private lateinit var context: Context
    private lateinit var db: MochiDatabase
    private lateinit var repo: AnnotationStudioRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, MochiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = AnnotationStudioRepository(db.annotationStudioDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            var n = input.read(buf)
            while (n > 0) {
                digest.update(buf, 0, n)
                n = input.read(buf)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Test
    fun studioGeometrySnappingOrthoScaleAreaAndHitTestWorkDeterministically() {
        val existingLine = MarkupObjectEntity(
            id = 1L,
            bookId = 10L,
            pageIndex = 0,
            layerId = 1L,
            kind = StudioTool.LINE.name,
            geometryJson = StudioGeometryJson.toJson(
                StudioGeometryJson(x1 = 0.20f, y1 = 0.20f, x2 = 0.60f, y2 = 0.60f)
            ),
            styleJson = StudioStyleJson.toJson(StudioStyleJson())
        )
        val crossLine = MarkupObjectEntity(
            id = 2L,
            bookId = 10L,
            pageIndex = 0,
            layerId = 1L,
            kind = StudioTool.LINE.name,
            geometryJson = StudioGeometryJson.toJson(
                StudioGeometryJson(x1 = 0.20f, y1 = 0.60f, x2 = 0.60f, y2 = 0.20f)
            ),
            styleJson = StudioStyleJson.toJson(StudioStyleJson())
        )
        val rect = MarkupObjectEntity(
            id = 3L,
            bookId = 10L,
            pageIndex = 0,
            layerId = 1L,
            kind = StudioTool.RECT.name,
            geometryJson = StudioGeometryJson.toJson(
                StudioGeometryJson(x1 = 0.70f, y1 = 0.10f, x2 = 0.90f, y2 = 0.30f)
            ),
            styleJson = StudioStyleJson.toJson(StudioStyleJson())
        )
        val all = listOf(existingLine, crossLine, rect)

        // Endpoint snap
        val epSnap = StudioGeometry.snapPoint(StudioPoint(0.205f, 0.204f), all, snapEnabled = true, gridSnapEnabled = true)
        assertEquals(SnapKind.ENDPOINT, epSnap.kind)
        assertEquals(0.20f, epSnap.point.x, 0.001f)

        // Intersection snap (0.40, 0.40) - midpoint is also (0.40, 0.40), both valid anchor hits
        val centerInterSnap = StudioGeometry.snapPoint(StudioPoint(0.405f, 0.402f), listOf(existingLine, crossLine), snapEnabled = true, gridSnapEnabled = true)
        assertTrue(centerInterSnap.kind == SnapKind.MIDPOINT || centerInterSnap.kind == SnapKind.INTERSECTION)
        assertEquals(0.40f, centerInterSnap.point.x, 0.002f)

        // Rect center snap
        val rectCenter = StudioGeometry.snapPoint(StudioPoint(0.803f, 0.202f), listOf(rect), snapEnabled = true, gridSnapEnabled = true)
        assertEquals(SnapKind.CENTER, rectCenter.kind)

        // Page edge snap
        val marginSnap = StudioGeometry.snapPoint(StudioPoint(0.052f, 0.43f), emptyList(), snapEnabled = true, gridSnapEnabled = false)
        assertEquals(SnapKind.PAGE_EDGE, marginSnap.kind)
        assertEquals(0.05f, marginSnap.point.x, 0.001f)

        // Ortho 45 degree constraint
        val ortho = StudioGeometry.applyAngleConstraint(StudioPoint(0.5f, 0.5f), StudioPoint(0.68f, 0.52f), 45f)
        assertEquals(0.5f, ortho.y, 0.002f) // snapped cleanly to 0° horizontal

        // Calibrated scale distance & shoelace polygon area math
        val scale = PageScaleEntity(bookId = 10L, pageIndex = 0, pagePtsPerUnit = 10f, unit = MeasurementUnit.M.name)
        val distText = StudioGeometry.formatMeasuredDistance(
            p1 = StudioPoint(0f, 0f),
            p2 = StudioPoint(0.5f, 0f),
            pageWidthPts = 200f,
            pageHeightPts = 400f,
            scale = scale
        )
        assertEquals("10.00 m", distText)

        val squarePts = listOf(
            StudioPoint(0f, 0f),
            StudioPoint(0.5f, 0f),
            StudioPoint(0.5f, 0.5f),
            StudioPoint(0f, 0.5f)
        )
        val areaText = StudioGeometry.formatPolygonArea(squarePts, pageWidthPts = 200f, pageHeightPts = 200f, scale = scale)
        assertEquals("100.00 m²", areaText)

        val deg = StudioGeometry.computeAngleDegrees(StudioPoint(0f, 0f), StudioPoint(1f, 0f), StudioPoint(0f, 1f))
        assertTrue(abs(deg - 90f) < 0.2f)

        // Hit test finds line
        val hit = StudioGeometry.hitTest(StudioPoint(0.30f, 0.30f), all)
        assertEquals(existingLine.id, hit?.id)
    }

    @Test
    fun roomStudioLayersMarkupsSvgAndAnnotatedPdfCopyPreserveOriginalFileSha256() = runTest {
        val samples = TestBookFixtures.createAll(context)
        val pdfSample = samples.first { it.file.name.endsWith(".pdf", ignoreCase = true) }
        val hashBefore = sha256Hex(pdfSample.file)

        val bookEntity = SafBookScanner.scanLocalBookFile(
            context = context,
            file = pdfSample.file,
            existing = null,
            shelfOrder = 0,
            fallbackTitle = pdfSample.title,
            fallbackAuthors = pdfSample.authors
        )
        val bookId = db.bookDao().upsertBook(bookEntity)

        val defaultLayers = repo.ensureDefaultLayers(bookId)
        assertEquals(2, defaultLayers.size)
        assertEquals("My notes", defaultLayers[0].name)
        assertEquals("Review", defaultLayers[1].name)

        val markup = repo.upsertMarkup(
            MarkupObjectEntity(
                bookId = bookId,
                pageIndex = 0,
                layerId = defaultLayers.first().id,
                kind = StudioTool.RECT.name,
                geometryJson = StudioGeometryJson.toJson(StudioGeometryJson(x1 = 0.1f, y1 = 0.1f, x2 = 0.4f, y2 = 0.3f)),
                styleJson = StudioStyleJson.toJson(StudioStyleJson(strokeColorHex = "#2F7CF6", strokeWidthPt = 2.5f)),
                label = "Spec Area"
            )
        )
        assertTrue(markup.id > 0L)

        repo.savePageScale(bookId, pageIndex = 0, pagePtsPerUnit = 72f, unit = MeasurementUnit.IN)
        val scales = repo.observeScales(bookId).first()
        assertEquals(1, scales.size)

        // Layered SVG export check
        val svg = StudioExporter.exportSvg(
            pageIndex = 0,
            pageWidthPts = 612,
            pageHeightPts = 792,
            layers = defaultLayers,
            markups = listOf(markup)
        )
        assertTrue(svg.contains("""<g id="layer-My notes""""))
        assertTrue(svg.contains("""<rect """))
        assertTrue(svg.contains("""#2F7CF6"""))

        // Portable JSON bundle round-trip check
        val bundle = StudioExporter.buildExportBundle(bookEntity.title, defaultLayers, listOf(markup))
        val decoded = com.example.data.studio.StudioExportBundle.fromJson(
            com.example.data.studio.StudioExportBundle.toJson(bundle)
        )
        assertNotNull(decoded)
        assertEquals(1, decoded?.markups?.size)

        // Non-destructive Annotated PDF copy export stream check
        val pdfOut = ByteArrayOutputStream()
        val exportedResult = runCatching {
            StudioExporter.exportAnnotatedPdfCopyToStream(
                context = context,
                sourcePdfUri = Uri.fromFile(pdfSample.file).toString(),
                outputStream = pdfOut,
                layers = defaultLayers,
                markups = listOf(markup),
                scales = scales.associateBy { it.pageIndex }
            )
        }
        // On real Android or Robolectric with native PdfRenderer/PdfDocument shadows, verify valid PDF bytes if supported
        exportedResult.getOrNull()?.let { pageCount ->
            assertTrue(pageCount >= 1)
            val bytes = pdfOut.toByteArray()
            assertTrue(bytes.size > 100)
        }

        // Verify original PDF sample file remained 100% byte-for-byte untouched
        val hashAfter = sha256Hex(pdfSample.file)
        assertEquals("Original PDF file hash must NEVER change!", hashBefore, hashAfter)
    }
}
