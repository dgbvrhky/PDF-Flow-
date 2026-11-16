package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class TextOverlaySpec(
    val pageIndex: Int,
    val text: String,
    val normX: Float,
    val normY: Float,
    val fontSizeSp: Float = 18f,
    val colorInt: Int = Color.BLACK
)

data class ImageOverlaySpec(
    val pageIndex: Int,
    val bitmap: Bitmap,
    val normX: Float,
    val normY: Float,
    val normWidth: Float
)

data class StrokeOverlaySpec(
    val pageIndex: Int,
    val pointsNormalized: List<Pair<Float, Float>>,
    val colorInt: Int,
    val strokeWidth: Float,
    val alpha: Float = 1f
)

data class ShapeOverlaySpec(
    val pageIndex: Int,
    val type: String, // "rect", "circle", "highlight", "underline", "strike"
    val normLeft: Float,
    val normTop: Float,
    val normRight: Float,
    val normBottom: Float,
    val colorInt: Int
)

data class AcroFormFieldInfo(
    val fullyQualifiedName: String,
    val value: String,
    val isCheckbox: Boolean,
    val isChecked: Boolean
)

object PdfEditingEngine {

    private fun renderTextToTransparentBitmap(
        text: String,
        fontSizePx: Float,
        colorInt: Int,
        maxWidthPx: Int = 900,
        rotationDegrees: Float = 0f
    ): Bitmap {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorInt
            textSize = fontSizePx
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, maxWidthPx)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .build()
        val w = (0 until layout.lineCount).maxOfOrNull { layout.getLineWidth(it).toInt() }?.coerceAtLeast(16) ?: 100
        val h = layout.height.coerceAtLeast(24)
        val raw = Bitmap.createBitmap(w + 16, h + 12, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(raw)
        canvas.translate(8f, 6f)
        layout.draw(canvas)

        if (rotationDegrees == 0f) return raw
        val matrix = android.graphics.Matrix().apply { postRotate(rotationDegrees) }
        val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        raw.recycle()
        return rotated
    }

    suspend fun applyEdits(
        context: Context,
        inputFile: File,
        outputFile: File,
        textOverlays: List<TextOverlaySpec> = emptyList(),
        imageOverlays: List<ImageOverlaySpec> = emptyList(),
        strokeOverlays: List<StrokeOverlaySpec> = emptyList(),
        shapeOverlays: List<ShapeOverlaySpec> = emptyList(),
        watermarkText: String? = null,
        headerText: String? = null,
        footerText: String? = null,
        addPageNumbers: Boolean = false,
        targetPageSize: PDRectangle? = null
    ): File = withContext(Dispatchers.IO) {
        PdfEngine.ensureInit(context)
        PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
            val totalPages = doc.numberOfPages
            for (pageIdx in 0 until totalPages) {
                val page: PDPage = doc.getPage(pageIdx)
                if (targetPageSize != null) {
                    page.mediaBox = targetPageSize
                }
                val box = page.cropBox ?: page.mediaBox
                val pageW = box.width
                val pageH = box.height

                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    // Shapes & markup (highlight, underline, strikethrough, rect, circle)
                    shapeOverlays.filter { it.pageIndex == pageIdx }.forEach { shape ->
                        val left = box.lowerLeftX + shape.normLeft * pageW
                        val right = box.lowerLeftX + shape.normRight * pageW
                        val top = box.lowerLeftY + (1f - shape.normTop) * pageH
                        val bottom = box.lowerLeftY + (1f - shape.normBottom) * pageH
                        val r = Color.red(shape.colorInt)
                        val g = Color.green(shape.colorInt)
                        val b = Color.blue(shape.colorInt)

                        when (shape.type) {
                            "highlight" -> {
                                val bmp = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(Color.argb(95, r, g, b))
                                val img = LosslessFactory.createFromImage(doc, bmp)
                                bmp.recycle()
                                cs.drawImage(img, minOf(left, right), minOf(bottom, top), kotlin.math.abs(right - left).coerceAtLeast(10f), kotlin.math.abs(top - bottom).coerceAtLeast(8f))
                            }
                            "underline" -> {
                                cs.setStrokingColor(r, g, b)
                                cs.setLineWidth(2f)
                                cs.moveTo(left, bottom)
                                cs.lineTo(right, bottom)
                                cs.stroke()
                            }
                            "strike" -> {
                                val midY = (top + bottom) / 2f
                                cs.setStrokingColor(r, g, b)
                                cs.setLineWidth(2f)
                                cs.moveTo(left, midY)
                                cs.lineTo(right, midY)
                                cs.stroke()
                            }
                            "circle" -> {
                                cs.setStrokingColor(r, g, b)
                                cs.setLineWidth(2.5f)
                                val cx = (left + right) / 2f
                                val cy = (top + bottom) / 2f
                                val rx = kotlin.math.abs(right - left) / 2f
                                val ry = kotlin.math.abs(top - bottom) / 2f
                                val k = 0.5522847f
                                cs.moveTo(cx + rx, cy)
                                cs.curveTo(cx + rx, cy + k * ry, cx + k * rx, cy + ry, cx, cy + ry)
                                cs.curveTo(cx - k * rx, cy + ry, cx - rx, cy + k * ry, cx - rx, cy)
                                cs.curveTo(cx - rx, cy - k * ry, cx - k * rx, cy - ry, cx, cy - ry)
                                cs.curveTo(cx + k * rx, cy - ry, cx + rx, cy - k * ry, cx + rx, cy)
                                cs.stroke()
                            }
                            else -> {
                                cs.setStrokingColor(r, g, b)
                                cs.setLineWidth(2.5f)
                                cs.addRect(minOf(left, right), minOf(bottom, top), kotlin.math.abs(right - left), kotlin.math.abs(top - bottom))
                                cs.stroke()
                            }
                        }
                    }

                    // Freehand draw strokes
                    strokeOverlays.filter { it.pageIndex == pageIdx }.forEach { stroke ->
                        if (stroke.pointsNormalized.size >= 2) {
                            cs.setStrokingColor(
                                Color.red(stroke.colorInt),
                                Color.green(stroke.colorInt),
                                Color.blue(stroke.colorInt)
                            )
                            cs.setLineWidth(stroke.strokeWidth.coerceIn(1f, 16f))
                            val first = stroke.pointsNormalized.first()
                            cs.moveTo(box.lowerLeftX + first.first * pageW, box.lowerLeftY + (1f - first.second) * pageH)
                            for (pt in stroke.pointsNormalized.drop(1)) {
                                cs.lineTo(box.lowerLeftX + pt.first * pageW, box.lowerLeftY + (1f - pt.second) * pageH)
                            }
                            cs.stroke()
                        }
                    }

                    // Image / signature / stamp overlays
                    imageOverlays.filter { it.pageIndex == pageIdx }.forEach { overlay ->
                        val pdImg = LosslessFactory.createFromImage(doc, overlay.bitmap)
                        val w = (overlay.normWidth * pageW).coerceAtLeast(36f)
                        val h = w * (overlay.bitmap.height.toFloat() / overlay.bitmap.width.toFloat().coerceAtLeast(1f))
                        val x = box.lowerLeftX + overlay.normX * pageW
                        val y = box.lowerLeftY + (1f - overlay.normY) * pageH - h
                        cs.drawImage(pdImg, x, y, w, h)
                    }

                    // Unicode-safe Text overlays (supports all 10 languages including Bengali, Hindi, Arabic, Urdu, CJK)
                    textOverlays.filter { it.pageIndex == pageIdx && it.text.isNotBlank() }.forEach { t ->
                        val bmp = renderTextToTransparentBitmap(t.text, t.fontSizeSp * 2.2f, t.colorInt)
                        val pdImg = LosslessFactory.createFromImage(doc, bmp)
                        val drawW = (bmp.width / 2.2f).coerceAtMost(pageW * 0.8f)
                        val drawH = drawW * (bmp.height.toFloat() / bmp.width.toFloat().coerceAtLeast(1f))
                        bmp.recycle()
                        val x = box.lowerLeftX + t.normX * pageW
                        val y = box.lowerLeftY + (1f - t.normY) * pageH - drawH
                        cs.drawImage(pdImg, x, y, drawW, drawH)
                    }

                    // Watermark
                    if (!watermarkText.isNullOrBlank()) {
                        val wmBmp = renderTextToTransparentBitmap(
                            watermarkText,
                            56f,
                            Color.argb(68, 200, 45, 27),
                            maxWidthPx = 1100,
                            rotationDegrees = -35f
                        )
                        val pdWm = LosslessFactory.createFromImage(doc, wmBmp)
                        val drawW = (pageW * 0.65f)
                        val drawH = drawW * (wmBmp.height.toFloat() / wmBmp.width.toFloat().coerceAtLeast(1f))
                        wmBmp.recycle()
                        cs.drawImage(
                            pdWm,
                            box.lowerLeftX + (pageW - drawW) / 2f,
                            box.lowerLeftY + (pageH - drawH) / 2f,
                            drawW,
                            drawH
                        )
                    }

                    // Header
                    if (!headerText.isNullOrBlank()) {
                        val hBmp = renderTextToTransparentBitmap(headerText, 24f, Color.DKGRAY)
                        val pdH = LosslessFactory.createFromImage(doc, hBmp)
                        val drawW = (hBmp.width / 2.0f).coerceAtMost(pageW * 0.7f)
                        val drawH = drawW * (hBmp.height.toFloat() / hBmp.width.toFloat().coerceAtLeast(1f))
                        hBmp.recycle()
                        cs.drawImage(pdH, box.lowerLeftX + 28f, box.upperRightY - drawH - 16f, drawW, drawH)
                    }

                    // Footer / Page Numbers
                    val footerLine = buildString {
                        if (!footerText.isNullOrBlank()) append(footerText)
                        if (addPageNumbers) {
                            if (isNotEmpty()) append("   |   ")
                            append("Page ${pageIdx + 1} of $totalPages")
                        }
                    }
                    if (footerLine.isNotBlank()) {
                        val fBmp = renderTextToTransparentBitmap(footerLine, 22f, Color.DKGRAY)
                        val pdF = LosslessFactory.createFromImage(doc, fBmp)
                        val drawW = (fBmp.width / 2.0f).coerceAtMost(pageW * 0.8f)
                        val drawH = drawW * (fBmp.height.toFloat() / fBmp.width.toFloat().coerceAtLeast(1f))
                        fBmp.recycle()
                        cs.drawImage(pdF, box.lowerLeftX + (pageW - drawW) / 2f, box.lowerLeftY + 14f, drawW, drawH)
                    }
                }
            }
            doc.save(outputFile)
        }
        outputFile
    }

    fun createStampBitmap(label: String, colorInt: Int): Bitmap {
        val w = 360
        val h = 110
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorInt
            style = Paint.Style.STROKE
            strokeWidth = 7f
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorInt
            textSize = 40f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawRoundRect(8f, 8f, w - 8f, h - 8f, 16f, 16f, borderPaint)
        canvas.drawText(label.uppercase(), w / 2f, h / 2f + 14f, textPaint)
        return bmp
    }

    suspend fun getAcroFormFields(context: Context, file: File): List<AcroFormFieldInfo> =
        withContext(Dispatchers.IO) {
            PdfEngine.ensureInit(context)
            PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                val form = doc.documentCatalog.acroForm ?: return@use emptyList()
                form.fieldTree.mapNotNull { field ->
                    when (field) {
                        is PDCheckBox -> AcroFormFieldInfo(
                            fullyQualifiedName = field.fullyQualifiedName,
                            value = if (field.isChecked) "Checked" else "Unchecked",
                            isCheckbox = true,
                            isChecked = field.isChecked
                        )
                        is PDTextField -> AcroFormFieldInfo(
                            fullyQualifiedName = field.fullyQualifiedName,
                            value = field.value.orEmpty(),
                            isCheckbox = false,
                            isChecked = false
                        )
                        else -> null
                    }
                }
            }
        }

    suspend fun fillAcroFormFields(
        context: Context,
        inputFile: File,
        textValues: Map<String, String>,
        checkboxValues: Map<String, Boolean>,
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        PdfEngine.ensureInit(context)
        PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
            val form = doc.documentCatalog.acroForm
            if (form != null) {
                textValues.forEach { (name, value) ->
                    (form.getField(name) as? PDTextField)?.setValue(value)
                }
                checkboxValues.forEach { (name, checked) ->
                    val cb = form.getField(name) as? PDCheckBox
                    if (cb != null) {
                        if (checked) cb.check() else cb.unCheck()
                    }
                }
            }
            doc.save(outputFile)
        }
        outputFile
    }
}
