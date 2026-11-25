package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class TestBookFixture(
    val file: File,
    val title: String,
    val authors: String,
    val series: String? = null,
    val seriesIndex: Float? = null
)

object TestBookFixtures {

    fun createAll(context: Context): List<TestBookFixture> {
        val root = File(context.cacheDir, "test_fixtures").apply { mkdirs() }
        val epubFile = File(root, "the_secret_garden_of_kyoto.epub").apply { writeEpub(this) }
        val pdfFile = File(root, "cozy_typography_guide.pdf").apply { writePdf(this) }
        val mdFile = File(root, "mochi_field_notes.md").apply { writeMarkdown(this) }
        val txtFile = File(root, "the_gift_of_the_magi.txt").apply { writeTxt(this) }
        val cbzFile = File(root, "mochis_midnight_snack.cbz").apply { writeCbz(this) }

        return listOf(
            TestBookFixture(epubFile, "The Secret Garden of Kyoto", "Hana Takahashi", "Kyoto Seasons", 1f),
            TestBookFixture(pdfFile, "A Field Guide to Cozy Typography", "Mochi Press"),
            TestBookFixture(mdFile, "Mochi Reader Field Notes", "Studio Notes"),
            TestBookFixture(txtFile, "The Gift of the Magi", "O. Henry", "Pocket Classics", 1f),
            TestBookFixture(cbzFile, "Mochi's Midnight Snack", "K. Sato", "Mochi Shorts", 1f)
        )
    }

    private fun renderCoverPng(
        title: String,
        subtitle: String,
        topColor: Int,
        bottomColor: Int,
        accentColor: Int
    ): ByteArray {
        val width = 420
        val height = 600
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), topColor, bottomColor, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        val decorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            alpha = 70
        }
        canvas.drawCircle(width * 0.78f, height * 0.22f, 110f, decorPaint)
        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = 180
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawRoundRect(RectF(28f, 28f, width - 28f, height - 28f), 20f, 20f, framePaint)
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF231820.toInt()
            textSize = 36f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        canvas.drawText(title.take(22), 52f, 170f, titlePaint)
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF5A3D49.toInt()
            textSize = 24f
        }
        canvas.drawText(subtitle, 52f, height - 76f, subPaint)
        return ByteArrayOutputStream().use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 90, out)
            bmp.recycle()
            out.toByteArray()
        }
    }

    private fun writeEpub(target: File) {
        val coverPng = renderCoverPng(
            title = "The Secret Garden of Kyoto",
            subtitle = "Hana Takahashi",
            topColor = 0xFFFFE4EC.toInt(),
            bottomColor = 0xFFFFC2D4.toInt(),
            accentColor = 0xFFFF6F9C.toInt()
        )
        val containerXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles>
                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
        """.trimIndent()
        val opf = """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:identifier id="uid">mochi-test-kyoto-01</dc:identifier>
                <dc:title>The Secret Garden of Kyoto</dc:title>
                <dc:creator>Hana Takahashi</dc:creator>
                <dc:language>en</dc:language>
                <meta name="cover" content="cover-image"/>
              </metadata>
              <manifest>
                <item id="cover-image" href="cover.png" media-type="image/png" properties="cover-image"/>
                <item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
                <item id="ch2" href="ch2.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
              <spine>
                <itemref idref="ch1"/>
                <itemref idref="ch2"/>
              </spine>
            </package>
        """.trimIndent()
        val ch1 = """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head><title>Chapter 1: Morning Mist</title></head>
            <body>
              <h1>Chapter 1: Morning Mist</h1>
              <p>Behind the cedar gate of the old teahouse, moss softened every stone step.</p>
            </body>
            </html>
        """.trimIndent()
        val ch2 = """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head><title>Chapter 2: Rain on Cedar</title></head>
            <body>
              <h1>Chapter 2: Rain on Cedar</h1>
              <p>By noon a gentle rain arrived from the northern hills.</p>
            </body>
            </html>
        """.trimIndent()

        FileOutputStream(target).use { fos ->
            ZipOutputStream(fos).use { zip ->
                val mimeBytes = "application/epub+zip".toByteArray(Charsets.US_ASCII)
                val mimeEntry = ZipEntry("mimetype").apply {
                    method = ZipEntry.STORED
                    size = mimeBytes.size.toLong()
                    compressedSize = mimeBytes.size.toLong()
                    crc = CRC32().apply { update(mimeBytes) }.value
                }
                zip.putNextEntry(mimeEntry)
                zip.write(mimeBytes)
                zip.closeEntry()
                fun addText(path: String, body: String) {
                    zip.putNextEntry(ZipEntry(path))
                    zip.write(body.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
                addText("META-INF/container.xml", containerXml)
                addText("OEBPS/content.opf", opf)
                addText("OEBPS/ch1.xhtml", ch1)
                addText("OEBPS/ch2.xhtml", ch2)
                zip.putNextEntry(ZipEntry("OEBPS/cover.png"))
                zip.write(coverPng)
                zip.closeEntry()
            }
        }
    }

    private fun writePdf(target: File) {
        val pagesContent = listOf(
            "q 1 1 1 rg 0 0 595 842 re f Q\nBT /F1 24 Tf 0.1 0.1 0.2 rg 76 708 Td (A Field Guide to Cozy Typography) Tj ET\n",
            "q 1 1 1 rg 0 0 595 842 re f Q\nBT /F1 18 Tf 0.1 0.1 0.2 rg 64 740 Td (Chapter 1 - Generous Margins) Tj ET\n"
        )
        val objects = mutableListOf<String>()
        val pageObjIds = pagesContent.indices.map { 4 + it * 2 }
        objects.add("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
        objects.add("2 0 obj\n<< /Type /Pages /Kids [${pageObjIds.joinToString(" ") { "$it 0 R" }}] /Count ${pagesContent.size} >>\nendobj\n")
        objects.add("3 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n")
        pagesContent.forEachIndexed { idx, stream ->
            val pageId = 4 + idx * 2
            val streamId = pageId + 1
            val streamBytes = stream.toByteArray(Charsets.US_ASCII)
            objects.add("$pageId 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 3 0 R >> >> /Contents $streamId 0 R >>\nendobj\n")
            objects.add("$streamId 0 obj\n<< /Length ${streamBytes.size} >>\nstream\n${stream}endstream\nendobj\n")
        }
        val out = ByteArrayOutputStream()
        fun writeAscii(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
        writeAscii("%PDF-1.4\n")
        val offsets = mutableListOf<Int>()
        for (obj in objects) {
            offsets.add(out.size())
            writeAscii(obj)
        }
        val xrefPos = out.size()
        writeAscii("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
        for (off in offsets) writeAscii(String.format("%010d 00000 n \n", off))
        writeAscii("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xrefPos\n%%EOF\n")
        target.writeBytes(out.toByteArray())
    }

    private fun writeMarkdown(target: File) {
        target.writeText(
            """
            ---
            title: Mochi Reader Field Notes
            author: Studio Notes
            ---

            # Welcome to Mochi Reader
            Notes remain local on your device.
            """.trimIndent(),
            Charsets.UTF_8
        )
    }

    private fun writeTxt(target: File) {
        target.writeText("The Gift of the Magi\n\nOne dollar and eighty-seven cents. That was all.", Charsets.UTF_8)
    }

    private fun writeCbz(target: File) {
        val page1 = renderCoverPng("Panel 1", "Test", 0xFFD9F3E9.toInt(), 0xFFB8E2D2.toInt(), 0xFF3E9C7B.toInt())
        FileOutputStream(target).use { fos ->
            ZipOutputStream(fos).use { zip ->
                zip.putNextEntry(ZipEntry("page_01.png"))
                zip.write(page1)
                zip.closeEntry()
            }
        }
    }
}
