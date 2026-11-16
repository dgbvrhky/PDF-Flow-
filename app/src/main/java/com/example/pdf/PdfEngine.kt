package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.multipdf.Splitter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitWidthDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

data class PdfPageSpec(
    val sourcePageIndex: Int,
    val rotationDeltaDegrees: Int = 0,
    val cropNormalized: RectF? = null
)

data class PdfSearchHit(
    val pageIndex: Int,
    val snippet: String
)

data class PdfMetadataInfo(
    val title: String,
    val author: String,
    val subject: String,
    val keywords: String,
    val creator: String,
    val producer: String,
    val pageCount: Int,
    val isEncrypted: Boolean,
    val fileSizeBytes: Long
)

data class PdfBookmarkItem(
    val title: String,
    val pageIndex: Int
)

enum class CompressionLevel(val label: String, val jpegQuality: Float, val maxDimensionPx: Int) {
    LOW("Low", 0.85f, 2000),
    MEDIUM("Medium", 0.62f, 1400),
    HIGH("High", 0.38f, 1000),
    CUSTOM("Custom", 0.55f, 1200)
}

data class CompressionResult(
    val originalBytes: Long,
    val compressedBytes: Long,
    val savedPercent: Int,
    val wasReduced: Boolean,
    val outputFile: File
)

data class PdfComparisonResult(
    val pageCountA: Int,
    val pageCountB: Int,
    val identicalPages: Int,
    val differingPages: List<Int>,
    val summaryNotes: List<String>
)

object PdfEngine {
    @Volatile
    private var initialized = false

    fun ensureInit(context: Context) {
        if (!initialized) {
            synchronized(this) {
                if (!initialized) {
                    PDFBoxResourceLoader.init(context.applicationContext)
                    initialized = true
                }
            }
        }
    }

    suspend fun getPageCount(context: Context, file: File, password: String? = null): Int =
        withContext(Dispatchers.IO) {
            ensureInit(context)
            if (password.isNullOrEmpty()) {
                runCatching {
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                        PdfRenderer(pfd).use { it.pageCount }
                    }
                }.getOrElse {
                    PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()).use { it.numberOfPages }
                }
            } else {
                PDDocument.load(file, password, MemoryUsageSetting.setupTempFileOnly()).use { it.numberOfPages }
            }
        }

    suspend fun renderPageBitmap(
        context: Context,
        file: File,
        pageIndex: Int,
        targetWidthPx: Int = 1080,
        password: String? = null
    ): Bitmap? = withContext(Dispatchers.IO) {
        ensureInit(context)
        var tempDecrypted: File? = null
        try {
            val renderFile = if (!password.isNullOrEmpty()) {
                val temp = File.createTempFile("unlocked_", ".pdf", context.cacheDir)
                PDDocument.load(file, password, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                    doc.isAllSecurityToBeRemoved = true
                    doc.save(temp)
                }
                tempDecrypted = temp
                temp
            } else {
                file
            }

            ParcelFileDescriptor.open(renderFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null
                    renderer.openPage(pageIndex).use { page ->
                        val width = targetWidthPx.coerceIn(160, 2400)
                        val height = ((page.height.toFloat() / page.width.toFloat().coerceAtLeast(1f)) * width)
                            .toInt().coerceIn(160, 3400)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                }
            }
        } finally {
            tempDecrypted?.delete()
        }
    }

    suspend fun extractAllText(context: Context, file: File, password: String? = null): List<String> =
        withContext(Dispatchers.IO) {
            ensureInit(context)
            PDDocument.load(file, password.orEmpty(), MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                val stripper = PDFTextStripper()
                (1..doc.numberOfPages).map { pageNum ->
                    stripper.startPage = pageNum
                    stripper.endPage = pageNum
                    stripper.getText(doc).trim()
                }
            }
        }

    suspend fun searchInPdf(
        context: Context,
        file: File,
        query: String,
        password: String? = null
    ): List<PdfSearchHit> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val pages = extractAllText(context, file, password)
        val q = query.trim()
        pages.mapIndexedNotNull { idx, text ->
            val pos = text.indexOf(q, ignoreCase = true)
            if (pos >= 0) {
                val start = (pos - 36).coerceAtLeast(0)
                val end = (pos + q.length + 54).coerceAtMost(text.length)
                val snippet = text.substring(start, end).replace('\n', ' ')
                PdfSearchHit(idx, if (start > 0) "...$snippet..." else snippet)
            } else null
        }
    }

    suspend fun mergePdfs(context: Context, inputs: List<File>, outputFile: File): File =
        withContext(Dispatchers.IO) {
            ensureInit(context)
            require(inputs.size >= 2) { "Select at least two PDF files to merge." }
            val merger = PDFMergerUtility()
            merger.destinationFileName = outputFile.absolutePath
            inputs.forEach { merger.addSource(it) }
            merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
            outputFile
        }

    suspend fun splitPdfEveryNPages(
        context: Context,
        inputFile: File,
        pagesPerSplit: Int,
        outputDir: File,
        baseName: String
    ): List<File> = withContext(Dispatchers.IO) {
        ensureInit(context)
        outputDir.mkdirs()
        val created = mutableListOf<File>()
        PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
            val splitter = Splitter()
            splitter.setSplitAtPage(pagesPerSplit.coerceAtLeast(1))
            val parts = splitter.split(doc)
            parts.forEachIndexed { idx, partDoc ->
                partDoc.use {
                    val out = File(outputDir, "${baseName}_part_${idx + 1}.pdf")
                    it.save(out)
                    created.add(out)
                }
            }
        }
        created
    }

    suspend fun applyPageOperations(
        context: Context,
        inputFile: File,
        specs: List<PdfPageSpec>,
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        ensureInit(context)
        require(specs.isNotEmpty()) { "At least one page must remain in the PDF." }
        PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { srcDoc ->
            PDDocument().use { dstDoc ->
                for (spec in specs) {
                    if (spec.sourcePageIndex in 0 until srcDoc.numberOfPages) {
                        val srcPage = srcDoc.getPage(spec.sourcePageIndex)
                        val imported = dstDoc.importPage(srcPage)
                        val newRot = ((imported.rotation + spec.rotationDeltaDegrees) % 360 + 360) % 360
                        imported.rotation = newRot
                        spec.cropNormalized?.let { norm ->
                            val box = imported.cropBox ?: imported.mediaBox
                            val newX = box.lowerLeftX + norm.left.coerceIn(0f, 0.8f) * box.width
                            val newY = box.lowerLeftY + (1f - norm.bottom.coerceIn(0.2f, 1f)) * box.height
                            val newW = (norm.width().coerceIn(0.15f, 1f)) * box.width
                            val newH = (norm.height().coerceIn(0.15f, 1f)) * box.height
                            imported.cropBox = PDRectangle(newX, newY, newW, newH)
                        }
                    }
                }
                dstDoc.save(outputFile)
            }
        }
        outputFile
    }

    suspend fun insertPagesFromPdf(
        context: Context,
        baseFile: File,
        insertFile: File,
        insertAtZeroIndex: Int,
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        ensureInit(context)
        PDDocument.load(baseFile, MemoryUsageSetting.setupTempFileOnly()).use { baseDoc ->
            PDDocument.load(insertFile, MemoryUsageSetting.setupTempFileOnly()).use { extraDoc ->
                PDDocument().use { outDoc ->
                    val clampedIdx = insertAtZeroIndex.coerceIn(0, baseDoc.numberOfPages)
                    for (i in 0 until clampedIdx) {
                        outDoc.importPage(baseDoc.getPage(i))
                    }
                    for (j in 0 until extraDoc.numberOfPages) {
                        outDoc.importPage(extraDoc.getPage(j))
                    }
                    for (i in clampedIdx until baseDoc.numberOfPages) {
                        outDoc.importPage(baseDoc.getPage(i))
                    }
                    outDoc.save(outputFile)
                }
            }
        }
        outputFile
    }

    suspend fun compressPdf(
        context: Context,
        inputFile: File,
        level: CompressionLevel,
        customQualityPercent: Int = 55,
        outputFile: File
    ): CompressionResult = withContext(Dispatchers.IO) {
        ensureInit(context)
        val originalSize = inputFile.length()
        val quality = if (level == CompressionLevel.CUSTOM) {
            (customQualityPercent.coerceIn(15, 95) / 100f)
        } else {
            level.jpegQuality
        }
        val maxDim = level.maxDimensionPx

        // Attempt 1: Re-encode embedded XObject images cleanly inside PDFBox
        val imageAttempt = File.createTempFile("comp_img_", ".pdf", context.cacheDir)
        try {
            PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                for (page in doc.pages) {
                    val resources = page.resources ?: continue
                    for (name in resources.xObjectNames.toList()) {
                        val xobj = runCatching { resources.getXObject(name) }.getOrNull()
                        if (xobj is PDImageXObject) {
                            val bmp = runCatching { xobj.image }.getOrNull() ?: continue
                            val scaled = downscaleBitmap(bmp, maxDim)
                            val compressedImg = JPEGFactory.createFromImage(doc, scaled, quality)
                            resources.put(name, compressedImg)
                            if (scaled !== bmp) scaled.recycle()
                            bmp.recycle()
                        }
                    }
                }
                doc.save(imageAttempt)
            }

            // Attempt 2 (for HIGH or when imageAttempt didn't shrink): render pages at controlled DPI
            var bestFile = imageAttempt
            if (level == CompressionLevel.HIGH || imageAttempt.length() >= originalSize) {
                val rasterAttempt = File.createTempFile("comp_ras_", ".pdf", context.cacheDir)
                try {
                    rasterizeCompressPdf(inputFile, rasterAttempt, maxDim, (quality * 100).toInt().coerceIn(25, 85))
                    if (rasterAttempt.exists() && rasterAttempt.length() > 0 && rasterAttempt.length() < bestFile.length()) {
                        bestFile = rasterAttempt
                    }
                } catch (_: Exception) {
                } catch (_: LinkageError) {
                }
            }

            if (bestFile.length() < originalSize) {
                bestFile.copyTo(outputFile, overwrite = true)
            } else {
                inputFile.copyTo(outputFile, overwrite = true)
            }

            val finalSize = outputFile.length()
            val reduced = finalSize < originalSize
            val pct = if (reduced && originalSize > 0) {
                (((originalSize - finalSize) * 100.0) / originalSize).toInt().coerceIn(1, 99)
            } else 0
            CompressionResult(
                originalBytes = originalSize,
                compressedBytes = finalSize,
                savedPercent = pct,
                wasReduced = reduced,
                outputFile = outputFile
            )
        } finally {
            imageAttempt.delete()
        }
    }

    private fun rasterizeCompressPdf(inputFile: File, outputFile: File, maxWidth: Int, jpegQuality: Int) {
        ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                PDDocument().use { outDoc ->
                    for (i in 0 until renderer.pageCount) {
                        renderer.openPage(i).use { page ->
                            val w = maxWidth.coerceIn(600, 1400)
                            val h = ((page.height.toFloat() / page.width.toFloat().coerceAtLeast(1f)) * w).toInt()
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            val pdPage = PDPage(PDRectangle(page.width.toFloat(), page.height.toFloat()))
                            outDoc.addPage(pdPage)
                            val baos = ByteArrayOutputStream()
                            bmp.compress(Bitmap.CompressFormat.JPEG, jpegQuality, baos)
                            bmp.recycle()
                            val jpegBmp = BitmapFactory.decodeByteArray(baos.toByteArray(), 0, baos.size())
                            val pdImg = JPEGFactory.createFromImage(outDoc, jpegBmp, jpegQuality / 100f)
                            jpegBmp.recycle()
                            com.tom_roush.pdfbox.pdmodel.PDPageContentStream(outDoc, pdPage).use { cs ->
                                cs.drawImage(pdImg, 0f, 0f, pdPage.mediaBox.width, pdPage.mediaBox.height)
                            }
                        }
                    }
                    outDoc.save(outputFile)
                }
            }
        }
    }

    private fun downscaleBitmap(bmp: Bitmap, maxDim: Int): Bitmap {
        val maxSide = maxOf(bmp.width, bmp.height)
        if (maxSide <= maxDim) return bmp
        val scale = maxDim.toFloat() / maxSide.toFloat()
        return Bitmap.createScaledBitmap(
            bmp,
            (bmp.width * scale).toInt().coerceAtLeast(1),
            (bmp.height * scale).toInt().coerceAtLeast(1),
            true
        )
    }

    suspend fun protectPdf(
        context: Context,
        inputFile: File,
        userPassword: String,
        ownerPassword: String,
        allowPrint: Boolean,
        allowCopy: Boolean,
        allowModify: Boolean,
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        ensureInit(context)
        require(userPassword.isNotBlank()) { "Password cannot be empty." }
        PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
            val ap = AccessPermission().apply {
                setCanPrint(allowPrint)
                setCanExtractContent(allowCopy)
                setCanModify(allowModify)
                setCanModifyAnnotations(allowModify)
            }
            val spp = StandardProtectionPolicy(ownerPassword.ifBlank { userPassword }, userPassword, ap)
            spp.encryptionKeyLength = 128
            doc.protect(spp)
            doc.save(outputFile)
        }
        outputFile
    }

    suspend fun unlockPdf(
        context: Context,
        inputFile: File,
        password: String,
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        ensureInit(context)
        PDDocument.load(inputFile, password, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
            doc.isAllSecurityToBeRemoved = true
            doc.save(outputFile)
        }
        outputFile
    }

    suspend fun getMetadata(context: Context, file: File, password: String? = null): PdfMetadataInfo =
        withContext(Dispatchers.IO) {
            ensureInit(context)
            PDDocument.load(file, password.orEmpty(), MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                val info = doc.documentInformation ?: PDDocumentInformation()
                PdfMetadataInfo(
                    title = info.title.orEmpty(),
                    author = info.author.orEmpty(),
                    subject = info.subject.orEmpty(),
                    keywords = info.keywords.orEmpty(),
                    creator = info.creator.orEmpty(),
                    producer = info.producer.orEmpty(),
                    pageCount = doc.numberOfPages,
                    isEncrypted = doc.isEncrypted,
                    fileSizeBytes = file.length()
                )
            }
        }

    suspend fun updateMetadata(
        context: Context,
        inputFile: File,
        title: String,
        author: String,
        subject: String,
        keywords: String,
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        ensureInit(context)
        PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
            val info = doc.documentInformation ?: PDDocumentInformation()
            info.title = title
            info.author = author
            info.subject = subject
            info.keywords = keywords
            doc.documentInformation = info
            doc.save(outputFile)
        }
        outputFile
    }

    suspend fun getBookmarks(context: Context, file: File): List<PdfBookmarkItem> =
        withContext(Dispatchers.IO) {
            ensureInit(context)
            PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                val outline = doc.documentCatalog.documentOutline ?: return@use emptyList()
                val result = mutableListOf<PdfBookmarkItem>()
                fun walk(item: PDOutlineItem?) {
                    var curr = item
                    while (curr != null) {
                        val dest = runCatching { curr.findDestinationPage(doc) }.getOrNull()
                        val idx = if (dest != null) doc.pages.indexOf(dest).coerceAtLeast(0) else 0
                        result.add(PdfBookmarkItem(curr.title ?: "Bookmark", idx))
                        walk(curr.firstChild)
                        curr = curr.nextSibling
                    }
                }
                walk(outline.firstChild)
                result
            }
        }

    suspend fun addBookmark(
        context: Context,
        inputFile: File,
        title: String,
        pageIndex: Int,
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        ensureInit(context)
        PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
            val outline = doc.documentCatalog.documentOutline ?: PDDocumentOutline().also {
                doc.documentCatalog.documentOutline = it
            }
            val safeIdx = pageIndex.coerceIn(0, (doc.numberOfPages - 1).coerceAtLeast(0))
            val dest: PDPageDestination = PDPageFitWidthDestination().apply {
                page = doc.getPage(safeIdx)
            }
            val bookmark = PDOutlineItem().apply {
                this.title = title
                this.destination = dest
            }
            outline.addLast(bookmark)
            doc.save(outputFile)
        }
        outputFile
    }

    suspend fun extractImages(context: Context, inputFile: File, outputDir: File): List<File> =
        withContext(Dispatchers.IO) {
            ensureInit(context)
            outputDir.mkdirs()
            val extracted = mutableListOf<File>()
            PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                var imgIndex = 1
                for (page in doc.pages) {
                    val res = page.resources ?: continue
                    for (name in res.xObjectNames) {
                        val xObj = runCatching { res.getXObject(name) }.getOrNull()
                        if (xObj is PDImageXObject) {
                            val bmp = runCatching { xObj.image }.getOrNull() ?: continue
                            val outFile = File(outputDir, "extracted_image_${imgIndex++}.png")
                            FileOutputStream(outFile).use { fos ->
                                bmp.compress(Bitmap.CompressFormat.PNG, 95, fos)
                            }
                            bmp.recycle()
                            extracted.add(outFile)
                        }
                    }
                }
            }
            extracted
        }

    suspend fun listAttachments(context: Context, inputFile: File): List<Pair<String, Long>> =
        withContext(Dispatchers.IO) {
            ensureInit(context)
            PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                val result = mutableListOf<Pair<String, Long>>()
                val names = doc.documentCatalog.names?.embeddedFiles
                names?.names?.forEach { (key, spec) ->
                    val size = spec.embeddedFile?.size?.toLong() ?: 0L
                    result.add((spec.file ?: key) to size)
                }
                result
            }
        }

    suspend fun repairPdf(context: Context, inputFile: File, outputFile: File): File =
        withContext(Dispatchers.IO) {
            ensureInit(context)
            PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                doc.save(outputFile)
            }
            outputFile
        }

    suspend fun comparePdfs(context: Context, fileA: File, fileB: File): PdfComparisonResult =
        withContext(Dispatchers.IO) {
            val pagesA = extractAllText(context, fileA)
            val pagesB = extractAllText(context, fileB)
            val maxPages = maxOf(pagesA.size, pagesB.size)
            var same = 0
            val diffPages = mutableListOf<Int>()
            val notes = mutableListOf<String>()
            for (i in 0 until maxPages) {
                val tA = pagesA.getOrNull(i)?.trim().orEmpty()
                val tB = pagesB.getOrNull(i)?.trim().orEmpty()
                if (tA == tB) {
                    same++
                } else {
                    diffPages.add(i + 1)
                    val lenDelta = tB.length - tA.length
                    notes.add("Page ${i + 1}: Text differs (${if (lenDelta >= 0) "+$lenDelta" else "$lenDelta"} chars)")
                }
            }
            PdfComparisonResult(
                pageCountA = pagesA.size,
                pageCountB = pagesB.size,
                identicalPages = same,
                differingPages = diffPages,
                summaryNotes = notes
            )
        }
}
