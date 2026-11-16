package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.text.HtmlCompat
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.StringReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

enum class ScanFilterMode {
    ORIGINAL,
    COLOR_ENHANCE,
    GRAYSCALE,
    BLACK_AND_WHITE
}

data class ConversionReport(
    val outputFiles: List<File>,
    val note: String
)

object PdfConverterEngine {

    fun applyScanFilter(source: Bitmap, mode: ScanFilterMode): Bitmap {
        if (mode == ScanFilterMode.ORIGINAL) return source
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        when (mode) {
            ScanFilterMode.COLOR_ENHANCE -> {
                val cm = ColorMatrix(
                    floatArrayOf(
                        1.25f, 0f, 0f, 0f, -20f,
                        0f, 1.25f, 0f, 0f, -20f,
                        0f, 0f, 1.25f, 0f, -20f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(source, 0f, 0f, paint)
            }
            ScanFilterMode.GRAYSCALE -> {
                val cm = ColorMatrix().apply { setSaturation(0f) }
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(source, 0f, 0f, paint)
            }
            ScanFilterMode.BLACK_AND_WHITE -> {
                val cm = ColorMatrix(
                    floatArrayOf(
                        1.8f, 1.8f, 1.8f, 0f, -340f,
                        1.8f, 1.8f, 1.8f, 0f, -340f,
                        1.8f, 1.8f, 1.8f, 0f, -340f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(source, 0f, 0f, paint)
            }
            ScanFilterMode.ORIGINAL -> {}
        }
        return result
    }

    private fun isWinAnsiEncodable(text: String): Boolean {
        return text.all { ch -> ch.code in 32..126 || ch == '\n' || ch == '\r' || ch == '\t' }
    }

    suspend fun textToPdf(
        title: String,
        bodyText: String,
        outputFile: File,
        author: String = "PDF Flow"
    ): File = withContext(Dispatchers.IO) {
        val pageWidth = 595f
        val pageHeight = 842f
        val margin = 48f
        val contentWidthPx = (pageWidth - margin * 2).toInt()

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111827")
            textSize = 20f
            isFakeBoldText = true
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1F2937")
            textSize = 12.5f
        }
        val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6B7280")
            textSize = 9.5f
        }

        val fullText = bodyText.ifBlank { " " }
        val layout = StaticLayout.Builder.obtain(fullText, 0, fullText.length, bodyPaint, contentWidthPx)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(4f, 1.15f)
            .build()

        val canUseVectorText = isWinAnsiEncodable(title) && isWinAnsiEncodable(fullText) && isWinAnsiEncodable(author)

        PDDocument().use { doc ->
            var lineIdx = 0
            var pageNum = 1
            while (lineIdx < layout.lineCount || pageNum == 1) {
                val page = PDPage(PDRectangle(pageWidth, pageHeight))
                doc.addPage(page)

                val pageLines = mutableListOf<String>()
                var yCursor = margin
                var titleHeight = 0f
                if (pageNum == 1 && title.isNotBlank()) {
                    val titleLayout = StaticLayout.Builder.obtain(title, 0, title.length, titlePaint, contentWidthPx).build()
                    titleHeight = titleLayout.height.toFloat()
                    yCursor += titleHeight + 26f
                }

                val bottomLimit = pageHeight - margin - 24f
                while (lineIdx < layout.lineCount) {
                    val lineHeight = (layout.getLineBottom(lineIdx) - layout.getLineTop(lineIdx)).toFloat().coerceAtLeast(14f)
                    if (yCursor + lineHeight > bottomLimit) break
                    val start = layout.getLineStart(lineIdx)
                    val end = layout.getLineEnd(lineIdx)
                    pageLines.add(fullText.substring(start, end).trimEnd('\n', '\r'))
                    yCursor += lineHeight
                    lineIdx++
                }

                var wroteVector = false
                if (canUseVectorText) {
                    wroteVector = runCatching {
                        val boldFont = com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA_BOLD
                        val bodyFont = com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA
                        PDPageContentStream(doc, page).use { cs ->
                            var pdfY = pageHeight - margin
                            if (pageNum == 1 && title.isNotBlank()) {
                                cs.beginText()
                                cs.setFont(boldFont, 18f)
                                cs.newLineAtOffset(margin, pdfY - 18f)
                                cs.showText(title.replace("\n", " "))
                                cs.endText()
                                pdfY -= 32f
                                cs.setStrokingColor(200, 205, 215)
                                cs.moveTo(margin, pdfY)
                                cs.lineTo(pageWidth - margin, pdfY)
                                cs.stroke()
                                pdfY -= 20f
                            }
                            cs.beginText()
                            cs.setFont(bodyFont, 11.5f)
                            cs.setLeading(16f)
                            cs.newLineAtOffset(margin, pdfY - 12f)
                            pageLines.forEach { line ->
                                cs.showText(line)
                                cs.newLine()
                            }
                            cs.endText()

                            cs.beginText()
                            cs.setFont(bodyFont, 9f)
                            cs.newLineAtOffset(margin, 24f)
                            cs.showText("$author  -  Page $pageNum")
                            cs.endText()
                        }
                        true
                    }.getOrDefault(false)
                }

                if (!wroteVector) {
                    val scale = 2
                    val bmp = Bitmap.createBitmap((pageWidth * scale).toInt(), (pageHeight * scale).toInt(), Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    canvas.drawColor(Color.WHITE)
                    canvas.scale(scale.toFloat(), scale.toFloat())
                    var y = margin
                    if (pageNum == 1 && title.isNotBlank()) {
                        val titleLayout = StaticLayout.Builder.obtain(title, 0, title.length, titlePaint, contentWidthPx).build()
                        canvas.save()
                        canvas.translate(margin, y)
                        titleLayout.draw(canvas)
                        canvas.restore()
                        y += titleLayout.height + 10f
                        canvas.drawLine(margin, y, pageWidth - margin, y, metaPaint)
                        y += 16f
                    }
                    pageLines.forEach { line ->
                        canvas.drawText(line, margin, y + 12f, bodyPaint)
                        y += 17f
                    }
                    canvas.drawText("$author  •  Page $pageNum", margin, pageHeight - 22f, metaPaint)

                    val pdImg = JPEGFactory.createFromImage(doc, bmp, 0.90f)
                    bmp.recycle()
                    PDPageContentStream(doc, page).use { cs ->
                        cs.drawImage(pdImg, 0f, 0f, pageWidth, pageHeight)
                    }
                }

                pageNum++
                if (lineIdx >= layout.lineCount) break
            }
            doc.save(outputFile)
        }
        outputFile
    }

    suspend fun htmlToPdf(htmlContent: String, title: String, outputFile: File): File =
        withContext(Dispatchers.IO) {
            val spanned = HtmlCompat.fromHtml(htmlContent, HtmlCompat.FROM_HTML_MODE_LEGACY)
            textToPdf(title = title, bodyText = spanned.toString(), outputFile = outputFile)
        }

    suspend fun imagesToPdf(
        context: Context,
        imageUris: List<Uri>,
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        PdfEngine.ensureInit(context)
        require(imageUris.isNotEmpty()) { "Select at least one image." }
        PDDocument().use { doc ->
            for (uri in imageUris) {
                val bmp = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it)
                } ?: continue
                addBitmapAsPage(doc, bmp)
                bmp.recycle()
            }
            require(doc.numberOfPages > 0) { "No valid images could be decoded." }
            doc.save(outputFile)
        }
        outputFile
    }

    suspend fun bitmapsToPdf(
        context: Context,
        bitmaps: List<Bitmap>,
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        PdfEngine.ensureInit(context)
        require(bitmaps.isNotEmpty()) { "No scanned pages to save." }
        PDDocument().use { doc ->
            for (bmp in bitmaps) {
                addBitmapAsPage(doc, bmp)
            }
            doc.save(outputFile)
        }
        outputFile
    }

    private fun addBitmapAsPage(doc: PDDocument, bmp: Bitmap) {
        val pageWidth = 595f
        val pageHeight = 842f
        val page = PDPage(PDRectangle(pageWidth, pageHeight))
        doc.addPage(page)
        val pdImg = JPEGFactory.createFromImage(doc, bmp, 0.88f)
        val margin = 24f
        val availW = pageWidth - margin * 2
        val availH = pageHeight - margin * 2
        val imgRatio = bmp.width.toFloat() / bmp.height.toFloat().coerceAtLeast(1f)
        val boxRatio = availW / availH
        val (drawW, drawH) = if (imgRatio > boxRatio) {
            availW to (availW / imgRatio)
        } else {
            (availH * imgRatio) to availH
        }
        val x = (pageWidth - drawW) / 2f
        val y = (pageHeight - drawH) / 2f
        PDPageContentStream(doc, page).use { cs ->
            cs.drawImage(pdImg, x, y, drawW, drawH)
        }
    }

    suspend fun pdfToImages(
        context: Context,
        inputFile: File,
        outputDir: File,
        baseName: String,
        asPng: Boolean = false
    ): List<File> = withContext(Dispatchers.IO) {
        outputDir.mkdirs()
        val results = mutableListOf<File>()
        ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val ext = if (asPng) "png" else "jpg"
                val fmt = if (asPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                for (i in 0 until renderer.pageCount) {
                    renderer.openPage(i).use { page ->
                        val w = 1440
                        val h = ((page.height.toFloat() / page.width.toFloat().coerceAtLeast(1f)) * w).toInt()
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val outFile = File(outputDir, "${baseName}_page_${i + 1}.$ext")
                        FileOutputStream(outFile).use { fos ->
                            bmp.compress(fmt, 92, fos)
                        }
                        bmp.recycle()
                        results.add(outFile)
                    }
                }
            }
        }
        results
    }

    suspend fun pdfToTxt(context: Context, inputFile: File, outputFile: File): File =
        withContext(Dispatchers.IO) {
            val pages = PdfEngine.extractAllText(context, inputFile)
            val combined = pages.mapIndexed { idx, text ->
                "--- Page ${idx + 1} ---\n$text"
            }.joinToString("\n\n")
            outputFile.writeText(combined.ifBlank { "(No selectable text found. Try OCR for scanned PDFs.)" })
            outputFile
        }

    suspend fun pdfToDocx(context: Context, inputFile: File, outputFile: File): ConversionReport =
        withContext(Dispatchers.IO) {
            val pages = PdfEngine.extractAllText(context, inputFile)
            val paragraphs = pages.flatMapIndexed { idx, pageText ->
                listOf("=== Page ${idx + 1} ===") + pageText.lines().filter { it.isNotBlank() }
            }.ifEmpty { listOf("No selectable text extracted. Use OCR for scanned PDFs.") }

            writeMinimalDocx(paragraphs, outputFile)
            ConversionReport(
                outputFiles = listOf(outputFile),
                note = "Exported extracted text paragraphs into OOXML (.docx). Complex multi-column layouts or embedded vector graphics are simplified."
            )
        }

    suspend fun docxToPdf(context: Context, docxUri: Uri, title: String, outputFile: File): ConversionReport =
        withContext(Dispatchers.IO) {
            val xmlText = readDocxXml(context, docxUri)
                ?: throw IllegalArgumentException("Only modern .docx (OOXML) files can be converted offline. Legacy binary .doc files require external conversion.")
            val extracted = extractTextFromWordXml(xmlText)
            textToPdf(title = title, bodyText = extracted, outputFile = outputFile)
            ConversionReport(
                outputFiles = listOf(outputFile),
                note = "Converted .docx paragraphs into PDF offline. Complex tables, custom fonts, or embedded Word charts may be simplified."
            )
        }

    suspend fun pdfToCsv(context: Context, inputFile: File, outputFile: File): ConversionReport =
        withContext(Dispatchers.IO) {
            val pages = PdfEngine.extractAllText(context, inputFile)
            val csvLines = mutableListOf("Page,Line,Content")
            pages.forEachIndexed { pIdx, text ->
                text.lines().filter { it.isNotBlank() }.forEachIndexed { lIdx, line ->
                    val escaped = "\"" + line.replace("\"", "\"\"") + "\""
                    csvLines.add("${pIdx + 1},${lIdx + 1},$escaped")
                }
            }
            outputFile.writeText(csvLines.joinToString("\n"))
            ConversionReport(
                outputFiles = listOf(outputFile),
                note = "Exported structured page lines to Excel-compatible CSV (.csv). Binary .xlsx formatting requires an external Office engine."
            )
        }

    suspend fun csvOrSpreadsheetTextToPdf(
        context: Context,
        uri: Uri,
        title: String,
        outputFile: File
    ): ConversionReport = withContext(Dispatchers.IO) {
        val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: throw IllegalArgumentException("Unable to read spreadsheet file.")
        val formatted = raw.lines().joinToString("\n") { line ->
            line.split(",").joinToString("   |   ") { it.trim().removeSurrounding("\"") }
        }
        textToPdf(title = title, bodyText = formatted, outputFile = outputFile)
        ConversionReport(
            outputFiles = listOf(outputFile),
            note = "Converted CSV/text rows into formatted PDF."
        )
    }

    private fun readDocxXml(context: Context, uri: Uri): String? {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            ZipInputStream(stream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "word/document.xml") {
                        return zip.bufferedReader().readText()
                    }
                    entry = zip.nextEntry
                }
            }
        }
        return null
    }

    private fun extractTextFromWordXml(xml: String): String {
        val sb = StringBuilder()
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> if (parser.name == "t" || parser.name == "w:t") {
                    sb.append(parser.nextText())
                }
                XmlPullParser.END_TAG -> if (parser.name == "p" || parser.name == "w:p") {
                    sb.append("\n")
                }
            }
            event = parser.next()
        }
        return sb.toString().trim()
    }

    private fun writeMinimalDocx(paragraphs: List<String>, outputFile: File) {
        val escapedParas = paragraphs.joinToString("") { p ->
            val clean = p.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            "<w:p><w:r><w:t xml:space=\"preserve\">$clean</w:t></w:r></w:p>"
        }
        val documentXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>$escapedParas<w:sectPr/></w:body>
</w:document>"""
        val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""
        val rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

        ZipOutputStream(FileOutputStream(outputFile)).use { zip ->
            fun put(name: String, data: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(data.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("[Content_Types].xml", contentTypes)
            put("_rels/.rels", rels)
            put("word/document.xml", documentXml)
        }
    }
}
