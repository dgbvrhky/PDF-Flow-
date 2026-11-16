package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.LocalDocumentEntity
import com.example.localization.LocalAppStrings
import com.example.pdf.AcroFormFieldInfo
import com.example.pdf.ImageOverlaySpec
import com.example.pdf.PdfEditingEngine
import com.example.pdf.PdfEngine
import com.example.pdf.PdfPageSpec
import com.example.pdf.ShapeOverlaySpec
import com.example.pdf.StrokeOverlaySpec
import com.example.pdf.TextOverlaySpec
import com.example.ui.components.SaveAndDownloadDialog
import com.example.ui.theme.FlowAmber
import com.example.ui.theme.FlowCrimson
import com.example.ui.theme.FlowEmerald
import com.example.ui.theme.FlowIndigo
import com.example.viewmodel.PdfFlowViewModel
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

private enum class EditorToolMode(val label: String) {
    TEXT("Text"),
    DRAW("Draw"),
    SIGN("Signature"),
    HIGHLIGHT("Highlight"),
    UNDERLINE("Underline"),
    STRIKE("Strike"),
    RECT("Box"),
    CIRCLE("Circle"),
    IMAGE("Image"),
    STAMP("Stamp")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfEditorScreen(
    initialDocId: String?,
    viewModel: PdfFlowViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val scope = rememberCoroutineScope()
    val docs by viewModel.activeDocuments.collectAsStateWithLifecycle()

    var selectedDoc by remember(docs, initialDocId) {
        mutableStateOf(
            docs.firstOrNull { it.id == initialDocId } ?: docs.firstOrNull()
        )
    }
    var currentPage by remember { mutableIntStateOf(0) }
    var pageCount by remember { mutableIntStateOf(0) }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var activeMode by remember { mutableStateOf(EditorToolMode.TEXT) }
    var activeColor by remember { mutableStateOf(FlowIndigo) }
    var strokeWidthPx by remember { mutableFloatStateOf(5f) }

    val textOverlays = remember { mutableStateListOf<TextOverlaySpec>() }
    val imageOverlays = remember { mutableStateListOf<ImageOverlaySpec>() }
    val strokeOverlays = remember { mutableStateListOf<StrokeOverlaySpec>() }
    val shapeOverlays = remember { mutableStateListOf<ShapeOverlaySpec>() }
    val activeDragPoints = remember { mutableStateListOf<Pair<Float, Float>>() }
    var dragStartNorm by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var dragEndNorm by remember { mutableStateOf<Pair<Float, Float>?>(null) }

    var pendingTapNorm by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var showTextDialog by remember { mutableStateOf(false) }
    var textDialogInput by remember { mutableStateOf("") }
    var showSignaturePad by remember { mutableStateOf(false) }
    var showStampPicker by remember { mutableStateOf(false) }
    var showPageSetupDialog by remember { mutableStateOf(false) }

    var watermarkText by remember { mutableStateOf("") }
    var headerText by remember { mutableStateOf("") }
    var footerText by remember { mutableStateOf("") }
    var addPageNumbers by remember { mutableStateOf(false) }
    var pageSizeChoice by remember { mutableStateOf("Original") }

    var showFormsDialog by remember { mutableStateOf(false) }
    var acroFields by remember { mutableStateOf<List<AcroFormFieldInfo>>(emptyList()) }
    val formTexts = remember { mutableStateOf(mapOf<String, String>()) }
    val formChecks = remember { mutableStateOf(mapOf<String, Boolean>()) }

    var isSaving by remember { mutableStateOf(false) }
    var savedOutputFile by remember { mutableStateOf<File?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        val tap = pendingTapNorm
        if (uri != null && tap != null) {
            scope.launch {
                val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                if (bmp != null) {
                    imageOverlays.add(
                        ImageOverlaySpec(
                            pageIndex = currentPage,
                            bitmap = bmp,
                            normX = tap.first.coerceIn(0.05f, 0.7f),
                            normY = tap.second.coerceIn(0.05f, 0.75f),
                            normWidth = 0.28f
                        )
                    )
                }
            }
        }
    }

    LaunchedEffect(selectedDoc, currentPage) {
        val doc = selectedDoc ?: return@LaunchedEffect
        val file = File(doc.filePath)
        if (file.exists()) {
            pageCount = runCatching { PdfEngine.getPageCount(context, file) }.getOrDefault(1)
            pageBitmap = runCatching { PdfEngine.renderPageBitmap(context, file, currentPage, 1080) }.getOrNull()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selectedDoc?.title ?: strings.toolEditPdf, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.backAction) }
                },
                actions = {
                    IconButton(onClick = {
                        when {
                            strokeOverlays.isNotEmpty() -> strokeOverlays.removeAt(strokeOverlays.lastIndex)
                            shapeOverlays.isNotEmpty() -> shapeOverlays.removeAt(shapeOverlays.lastIndex)
                            textOverlays.isNotEmpty() -> textOverlays.removeAt(textOverlays.lastIndex)
                            imageOverlays.isNotEmpty() -> imageOverlays.removeAt(imageOverlays.lastIndex)
                        }
                    }) {
                        Icon(Icons.Default.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = { showPageSetupDialog = true }) {
                        Icon(Icons.Default.Tune, contentDescription = "Watermark & Header/Footer")
                    }
                    IconButton(onClick = {
                        val f = selectedDoc?.let { File(it.filePath) } ?: return@IconButton
                        scope.launch {
                            acroFields = runCatching { PdfEditingEngine.getAcroFormFields(context, f) }.getOrDefault(emptyList())
                            formTexts.value = acroFields.filter { !it.isCheckbox }.associate { it.fullyQualifiedName to it.value }
                            formChecks.value = acroFields.filter { it.isCheckbox }.associate { it.fullyQualifiedName to it.isChecked }
                            showFormsDialog = true
                        }
                    }) {
                        Icon(Icons.Default.CheckBox, contentDescription = "Fill PDF Forms")
                    }
                    IconButton(
                        enabled = !isSaving && selectedDoc != null,
                        onClick = {
                            val doc = selectedDoc ?: return@IconButton
                            scope.launch {
                                isSaving = true
                                runCatching {
                                    val out = viewModel.storageManager.createWorkspaceFile("Edited_${doc.fileName}")
                                    val sizeRect = when (pageSizeChoice) {
                                        "A4" -> PDRectangle.A4
                                        "Letter" -> PDRectangle.LETTER
                                        "Legal" -> PDRectangle.LEGAL
                                        else -> null
                                    }
                                    PdfEditingEngine.applyEdits(
                                        context = context,
                                        inputFile = File(doc.filePath),
                                        outputFile = out,
                                        textOverlays = textOverlays.toList(),
                                        imageOverlays = imageOverlays.toList(),
                                        strokeOverlays = strokeOverlays.toList(),
                                        shapeOverlays = shapeOverlays.toList(),
                                        watermarkText = watermarkText.ifBlank { null },
                                        headerText = headerText.ifBlank { null },
                                        footerText = footerText.ifBlank { null },
                                        addPageNumbers = addPageNumbers,
                                        targetPageSize = sizeRect
                                    )
                                    viewModel.registerGeneratedPdf(out, "${doc.title} Edited", doc.folderName)
                                    out
                                }.onSuccess {
                                    savedOutputFile = it
                                    viewModel.postStatus("Saved edited PDF copy.")
                                }.onFailure {
                                    viewModel.postStatus(it.localizedMessage ?: "Failed to save edits.")
                                }
                                isSaving = false
                            }
                        },
                        modifier = Modifier.testTag("editor_save_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = strings.saveAction)
                    }
                }
            )
        }
    ) { padding ->
        if (selectedDoc == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Import or create a PDF first to open the Editor.")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Document & Page Switcher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Tap or drag on page using ${activeMode.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(enabled = currentPage > 0, onClick = { currentPage-- }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Prev")
                    }
                    Text("${currentPage + 1}/$pageCount", fontWeight = FontWeight.SemiBold)
                    IconButton(enabled = currentPage < pageCount - 1, onClick = { currentPage++ }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next")
                    }
                }
            }

            // Color & Tool Strip
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val colors = listOf(Color.Black, FlowCrimson, FlowIndigo, FlowEmerald, FlowAmber)
                items(colors) { col ->
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(col)
                            .border(if (activeColor == col) 3.dp else 1.dp, Color.White, CircleShape)
                            .clickable { activeColor = col }
                    )
                }
                items(EditorToolMode.entries) { mode ->
                    val selected = activeMode == mode
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable {
                            activeMode = mode
                            if (mode == EditorToolMode.SIGN) showSignaturePad = true
                            if (mode == EditorToolMode.STAMP) {
                                pendingTapNorm = 0.25f to 0.35f
                                showStampPicker = true
                            }
                        }
                    ) {
                        Text(
                            text = mode.label,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // Interactive Page Canvas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                val bmp = pageBitmap
                if (bmp == null) {
                    CircularProgressIndicator()
                } else {
                    BoxWithConstraints(
                        modifier = Modifier
                            .aspectRatio(bmp.width.toFloat() / bmp.height.toFloat().coerceAtLeast(1f))
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                    ) {
                        val boxW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                        val boxH = constraints.maxHeight.toFloat().coerceAtLeast(1f)

                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Editable PDF Page",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Interactive gesture surface
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(activeMode, currentPage) {
                                    detectTapGestures { offset ->
                                        val nx = (offset.x / boxW).coerceIn(0f, 0.95f)
                                        val ny = (offset.y / boxH).coerceIn(0f, 0.95f)
                                        pendingTapNorm = nx to ny
                                        when (activeMode) {
                                            EditorToolMode.TEXT -> {
                                                textDialogInput = ""
                                                showTextDialog = true
                                            }
                                            EditorToolMode.IMAGE -> {
                                                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                            }
                                            EditorToolMode.SIGN -> showSignaturePad = true
                                            EditorToolMode.STAMP -> showStampPicker = true
                                            else -> Unit
                                        }
                                    }
                                }
                                .pointerInput(activeMode, activeColor, currentPage) {
                                    detectDragGestures(
                                        onDragStart = { start ->
                                            val nx = (start.x / boxW).coerceIn(0f, 1f)
                                            val ny = (start.y / boxH).coerceIn(0f, 1f)
                                            if (activeMode == EditorToolMode.DRAW) {
                                                activeDragPoints.clear()
                                                activeDragPoints.add(nx to ny)
                                            } else if (activeMode in listOf(
                                                    EditorToolMode.HIGHLIGHT,
                                                    EditorToolMode.UNDERLINE,
                                                    EditorToolMode.STRIKE,
                                                    EditorToolMode.RECT,
                                                    EditorToolMode.CIRCLE
                                                )
                                            ) {
                                                dragStartNorm = nx to ny
                                                dragEndNorm = nx to ny
                                            }
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            val nx = (change.position.x / boxW).coerceIn(0f, 1f)
                                            val ny = (change.position.y / boxH).coerceIn(0f, 1f)
                                            if (activeMode == EditorToolMode.DRAW) {
                                                activeDragPoints.add(nx to ny)
                                            } else if (dragStartNorm != null) {
                                                dragEndNorm = nx to ny
                                            }
                                        },
                                        onDragEnd = {
                                            if (activeMode == EditorToolMode.DRAW && activeDragPoints.size >= 2) {
                                                strokeOverlays.add(
                                                    StrokeOverlaySpec(
                                                        pageIndex = currentPage,
                                                        pointsNormalized = activeDragPoints.toList(),
                                                        colorInt = activeColor.toArgb(),
                                                        strokeWidth = strokeWidthPx
                                                    )
                                                )
                                                activeDragPoints.clear()
                                            } else if (dragStartNorm != null && dragEndNorm != null) {
                                                val s = dragStartNorm!!
                                                val e = dragEndNorm!!
                                                val typeKey = when (activeMode) {
                                                    EditorToolMode.HIGHLIGHT -> "highlight"
                                                    EditorToolMode.UNDERLINE -> "underline"
                                                    EditorToolMode.STRIKE -> "strike"
                                                    EditorToolMode.CIRCLE -> "circle"
                                                    else -> "rect"
                                                }
                                                shapeOverlays.add(
                                                    ShapeOverlaySpec(
                                                        pageIndex = currentPage,
                                                        type = typeKey,
                                                        normLeft = minOf(s.first, e.first),
                                                        normTop = minOf(s.second, e.second),
                                                        normRight = maxOf(s.first, e.first),
                                                        normBottom = maxOf(s.second, e.second),
                                                        colorInt = activeColor.toArgb()
                                                    )
                                                )
                                                dragStartNorm = null
                                                dragEndNorm = null
                                            }
                                        }
                                    )
                                }
                        ) {
                            // Draw committed shapes
                            shapeOverlays.filter { it.pageIndex == currentPage }.forEach { s ->
                                val l = s.normLeft * size.width
                                val t = s.normTop * size.height
                                val r = s.normRight * size.width
                                val b = s.normBottom * size.height
                                val c = Color(s.colorInt)
                                when (s.type) {
                                    "highlight" -> drawRect(c.copy(alpha = 0.35f), Offset(l, t), androidx.compose.ui.geometry.Size(r - l, (b - t).coerceAtLeast(18f)))
                                    "underline" -> drawLine(c, Offset(l, b), Offset(r, b), strokeWidth = 4f)
                                    "strike" -> drawLine(c, Offset(l, (t + b) / 2f), Offset(r, (t + b) / 2f), strokeWidth = 4f)
                                    "circle" -> drawOval(c, Offset(l, t), androidx.compose.ui.geometry.Size(r - l, b - t), style = androidx.compose.ui.graphics.drawscope.Stroke(4f))
                                    else -> drawRect(c, Offset(l, t), androidx.compose.ui.geometry.Size(r - l, b - t), style = androidx.compose.ui.graphics.drawscope.Stroke(4f))
                                }
                            }
                            // Draw committed freehand strokes
                            strokeOverlays.filter { it.pageIndex == currentPage }.forEach { st ->
                                val c = Color(st.colorInt)
                                for (i in 0 until st.pointsNormalized.lastIndex) {
                                    val p1 = st.pointsNormalized[i]
                                    val p2 = st.pointsNormalized[i + 1]
                                    drawLine(
                                        color = c,
                                        start = Offset(p1.first * size.width, p1.second * size.height),
                                        end = Offset(p2.first * size.width, p2.second * size.height),
                                        strokeWidth = st.strokeWidth * 2f,
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                            // Active stroke preview
                            for (i in 0 until activeDragPoints.lastIndex) {
                                val p1 = activeDragPoints[i]
                                val p2 = activeDragPoints[i + 1]
                                drawLine(
                                    color = activeColor,
                                    start = Offset(p1.first * size.width, p1.second * size.height),
                                    end = Offset(p2.first * size.width, p2.second * size.height),
                                    strokeWidth = strokeWidthPx * 2f,
                                    cap = StrokeCap.Round
                                )
                            }
                        }

                        // Preview placed text overlays
                        textOverlays.filter { it.pageIndex == currentPage }.forEach { txt ->
                            Text(
                                text = txt.text,
                                color = Color(txt.colorInt),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.offset {
                                    IntOffset((txt.normX * boxW).roundToInt(), (txt.normY * boxH).roundToInt())
                                }
                            )
                        }

                        // Preview placed image/signature/stamp overlays
                        imageOverlays.filter { it.pageIndex == currentPage }.forEach { img ->
                            Image(
                                bitmap = img.bitmap.asImageBitmap(),
                                contentDescription = "Overlay",
                                modifier = Modifier
                                    .offset {
                                        IntOffset((img.normX * boxW).roundToInt(), (img.normY * boxH).roundToInt())
                                    }
                                    .width(100.dp)
                            )
                        }
                    }
                }
            }

            if (isSaving) {
                Surface(tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
                    Text("Applying edits and writing new PDF...", modifier = Modifier.padding(12.dp))
                }
            }
        }
    }

    if (showTextDialog) {
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            title = { Text("Add Text / Comment") },
            text = {
                OutlinedTextField(
                    value = textDialogInput,
                    onValueChange = { textDialogInput = it },
                    label = { Text("Supports all languages (বাংলা, العربية, हिन्दी, CJK)") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    val tap = pendingTapNorm ?: (0.2f to 0.2f)
                    if (textDialogInput.isNotBlank()) {
                        textOverlays.add(
                            TextOverlaySpec(
                                pageIndex = currentPage,
                                text = textDialogInput,
                                normX = tap.first,
                                normY = tap.second,
                                colorInt = activeColor.toArgb()
                            )
                        )
                    }
                    showTextDialog = false
                }) { Text("Place") }
            },
            dismissButton = { TextButton(onClick = { showTextDialog = false }) { Text(strings.cancelAction) } }
        )
    }

    if (showSignaturePad) {
        SignaturePadDialog(
            inkColor = activeColor,
            onDismiss = { showSignaturePad = false },
            onConfirmBitmap = { sigBmp ->
                val tap = pendingTapNorm ?: (0.25f to 0.65f)
                imageOverlays.add(
                    ImageOverlaySpec(
                        pageIndex = currentPage,
                        bitmap = sigBmp,
                        normX = tap.first,
                        normY = tap.second,
                        normWidth = 0.28f
                    )
                )
                showSignaturePad = false
            }
        )
    }

    if (showStampPicker) {
        val stamps = listOf("APPROVED", "CONFIDENTIAL", "PAID", "DRAFT", "REVIEWED")
        AlertDialog(
            onDismissRequest = { showStampPicker = false },
            title = { Text("Select Stamp") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    stamps.forEach { label ->
                        OutlinedButton(
                            onClick = {
                                val bmp = PdfEditingEngine.createStampBitmap(label, activeColor.toArgb())
                                val tap = pendingTapNorm ?: (0.25f to 0.3f)
                                imageOverlays.add(ImageOverlaySpec(currentPage, bmp, tap.first, tap.second, 0.30f))
                                showStampPicker = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(label) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showStampPicker = false }) { Text(strings.cancelAction) } }
        )
    }

    if (showPageSetupDialog) {
        AlertDialog(
            onDismissRequest = { showPageSetupDialog = false },
            title = { Text("Watermark, Headers & Page Size") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = watermarkText, onValueChange = { watermarkText = it }, label = { Text("Watermark Text") }, singleLine = true)
                    OutlinedTextField(value = headerText, onValueChange = { headerText = it }, label = { Text("Header Text") }, singleLine = true)
                    OutlinedTextField(value = footerText, onValueChange = { footerText = it }, label = { Text("Footer Text") }, singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Add Page Numbers")
                        Switch(checked = addPageNumbers, onCheckedChange = { addPageNumbers = it })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Original", "A4", "Letter").forEach { sz ->
                            Surface(
                                shape = CircleShape,
                                color = if (pageSizeChoice == sz) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable { pageSizeChoice = sz }
                            ) {
                                Text(
                                    sz,
                                    color = if (pageSizeChoice == sz) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPageSetupDialog = false }) { Text("Apply") } }
        )
    }

    if (showFormsDialog) {
        AlertDialog(
            onDismissRequest = { showFormsDialog = false },
            title = { Text("Fill PDF Form Fields") },
            text = {
                if (acroFields.isEmpty()) {
                    Text("This PDF has no interactive AcroForm fields. You can still place text or checkmarks directly using the Text/Draw tools.")
                } else {
                    LazyColumn(modifier = Modifier.height(260.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(acroFields) { field ->
                            if (field.isCheckbox) {
                                val checked = formChecks.value[field.fullyQualifiedName] ?: field.isChecked
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { c ->
                                            formChecks.value = formChecks.value.toMutableMap().apply { put(field.fullyQualifiedName, c) }
                                        }
                                    )
                                    Text(field.fullyQualifiedName)
                                }
                            } else {
                                OutlinedTextField(
                                    value = formTexts.value[field.fullyQualifiedName] ?: "",
                                    onValueChange = { v ->
                                        formTexts.value = formTexts.value.toMutableMap().apply { put(field.fullyQualifiedName, v) }
                                    },
                                    label = { Text(field.fullyQualifiedName) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (acroFields.isNotEmpty()) {
                    Button(onClick = {
                        val doc = selectedDoc ?: return@Button
                        scope.launch {
                            val out = viewModel.storageManager.createWorkspaceFile("Filled_${doc.fileName}")
                            PdfEditingEngine.fillAcroFormFields(context, File(doc.filePath), formTexts.value, formChecks.value, out)
                            viewModel.registerGeneratedPdf(out, "${doc.title} Filled", doc.folderName)
                            savedOutputFile = out
                            showFormsDialog = false
                        }
                    }) { Text("Save Form") }
                } else {
                    TextButton(onClick = { showFormsDialog = false }) { Text("OK") }
                }
            },
            dismissButton = { TextButton(onClick = { showFormsDialog = false }) { Text("Close") } }
        )
    }

    savedOutputFile?.let { file ->
        SaveAndDownloadDialog(
            sourceFile = file,
            defaultFileName = file.name,
            storageManager = viewModel.storageManager,
            saveToDownloadsAction = { src, name, mime, overwrite ->
                viewModel.saveFileToPublicDownloads(src, name, mime, overwrite)
            },
            onDismiss = { savedOutputFile = null }
        )
    }
}

@Composable
private fun SignaturePadDialog(
    inkColor: Color,
    onDismiss: () -> Unit,
    onConfirmBitmap: (Bitmap) -> Unit
) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    val currentStroke = remember { mutableStateListOf<Offset>() }
    var canvasWidth by remember { mutableFloatStateOf(600f) }
    var canvasHeight by remember { mutableFloatStateOf(260f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Draw Signature") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sign inside the box below:", style = MaterialTheme.typography.bodySmall)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { pos ->
                                        canvasWidth = size.width.toFloat().coerceAtLeast(1f)
                                        canvasHeight = size.height.toFloat().coerceAtLeast(1f)
                                        currentStroke.clear()
                                        currentStroke.add(pos)
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        currentStroke.add(change.position)
                                    },
                                    onDragEnd = {
                                        if (currentStroke.size >= 2) {
                                            strokes.add(currentStroke.toList())
                                        }
                                        currentStroke.clear()
                                    }
                                )
                            }
                    ) {
                        val all = strokes + listOf(currentStroke.toList())
                        all.forEach { pts ->
                            for (i in 0 until pts.lastIndex) {
                                drawLine(inkColor, pts[i], pts[i + 1], strokeWidth = 7f, cap = StrokeCap.Round)
                            }
                        }
                    }
                }
                TextButton(onClick = { strokes.clear(); currentStroke.clear() }) { Text("Clear Pad") }
            }
        },
        confirmButton = {
            Button(
                enabled = strokes.isNotEmpty(),
                onClick = {
                    val bmp = Bitmap.createBitmap(canvasWidth.toInt().coerceAtLeast(300), canvasHeight.toInt().coerceAtLeast(140), Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bmp)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = inkColor.toArgb()
                        style = Paint.Style.STROKE
                        strokeWidth = 7f
                        strokeCap = Paint.Cap.ROUND
                    }
                    strokes.forEach { pts ->
                        val p = Path()
                        pts.forEachIndexed { idx, offset ->
                            if (idx == 0) p.moveTo(offset.x, offset.y) else p.lineTo(offset.x, offset.y)
                        }
                        canvas.drawPath(p, paint)
                    }
                    onConfirmBitmap(bmp)
                }
            ) { Text("Insert Signature") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfOrganizerScreen(
    initialDocId: String?,
    viewModel: PdfFlowViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val scope = rememberCoroutineScope()
    val docs by viewModel.activeDocuments.collectAsStateWithLifecycle()

    var selectedDoc by remember(docs, initialDocId) {
        mutableStateOf(docs.firstOrNull { it.id == initialDocId } ?: docs.firstOrNull())
    }
    val pageSpecs = remember { mutableStateListOf<PdfPageSpec>() }
    val selectedPageIndices = remember { mutableStateListOf<Int>() }
    val thumbs = remember { mutableStateOf<Map<Int, Bitmap>>(emptyMap()) }
    var cropMode by remember { mutableStateOf("None") }
    var isProcessing by remember { mutableStateOf(false) }
    var savedFile by remember { mutableStateOf<File?>(null) }

    val insertPdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val doc = selectedDoc ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            scope.launch {
                isProcessing = true
                runCatching {
                    val extra = viewModel.storageManager.importUriToWorkspace(uri, "insert_source.pdf")
                    val out = viewModel.storageManager.createWorkspaceFile("Inserted_${doc.fileName}")
                    PdfEngine.insertPagesFromPdf(context, File(doc.filePath), extra, pageSpecs.size, out)
                    viewModel.registerGeneratedPdf(out, "${doc.title} Merged", doc.folderName)
                    out
                }.onSuccess { savedFile = it }
                    .onFailure { viewModel.postStatus(it.localizedMessage ?: "Insert failed.") }
                isProcessing = false
            }
        }
    }

    LaunchedEffect(selectedDoc) {
        val doc = selectedDoc ?: return@LaunchedEffect
        val file = File(doc.filePath)
        if (file.exists()) {
            val count = runCatching { PdfEngine.getPageCount(context, file) }.getOrDefault(0)
            pageSpecs.clear()
            selectedPageIndices.clear()
            for (i in 0 until count) pageSpecs.add(PdfPageSpec(i))
            val map = mutableMapOf<Int, Bitmap>()
            for (i in 0 until minOf(count, 40)) {
                PdfEngine.renderPageBitmap(context, file, i, 260)?.let { map[i] = it }
            }
            thumbs.value = map
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Organize Pages") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.backAction) } },
                actions = {
                    IconButton(onClick = { insertPdfPicker.launch(arrayOf("application/pdf")) }) {
                        Icon(Icons.Default.Add, contentDescription = "Insert PDF")
                    }
                    IconButton(
                        enabled = !isProcessing && selectedDoc != null && pageSpecs.isNotEmpty(),
                        onClick = {
                            val doc = selectedDoc ?: return@IconButton
                            scope.launch {
                                isProcessing = true
                                runCatching {
                                    val cropRect = when (cropMode) {
                                        "Tight" -> RectF(0.08f, 0.08f, 0.92f, 0.92f)
                                        "Focus" -> RectF(0.15f, 0.15f, 0.85f, 0.85f)
                                        else -> null
                                    }
                                    val finalSpecs = pageSpecs.map { it.copy(cropNormalized = cropRect) }
                                    val out = viewModel.storageManager.createWorkspaceFile("Organized_${doc.fileName}")
                                    PdfEngine.applyPageOperations(context, File(doc.filePath), finalSpecs, out)
                                    viewModel.registerGeneratedPdf(out, "${doc.title} Organized", doc.folderName)
                                    out
                                }.onSuccess { savedFile = it }
                                    .onFailure { viewModel.postStatus(it.localizedMessage ?: "Failed to organize.") }
                                isProcessing = false
                            }
                        }
                    ) {
                        Icon(Icons.Default.Save, contentDescription = strings.saveAction)
                    }
                }
            )
        }
    ) { padding ->
        if (selectedDoc == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Select or import a PDF first.")
            }
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("None", "Tight", "Focus").forEach { cm ->
                        Surface(
                            shape = CircleShape,
                            color = if (cropMode == cm) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { cropMode = cm }
                        ) {
                            Text(
                                "Crop: $cm",
                                color = if (cropMode == cm) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
                if (selectedPageIndices.isNotEmpty()) {
                    FilledTonalButton(onClick = {
                        val doc = selectedDoc ?: return@FilledTonalButton
                        scope.launch {
                            val specsToExtract = selectedPageIndices.mapNotNull { pageSpecs.getOrNull(it) }
                            if (specsToExtract.isNotEmpty()) {
                                val out = viewModel.storageManager.createWorkspaceFile("Extracted_${doc.fileName}")
                                PdfEngine.applyPageOperations(context, File(doc.filePath), specsToExtract, out)
                                viewModel.registerGeneratedPdf(out, "${doc.title} Extract", doc.folderName)
                                savedFile = out
                            }
                        }
                    }) {
                        Text("Extract (${selectedPageIndices.size})")
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 148.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(pageSpecs) { idx, spec ->
                    val isSel = idx in selectedPageIndices
                    Card(
                        modifier = Modifier
                            .border(if (isSel) 2.dp else 0.dp, if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent, MaterialTheme.shapes.medium)
                            .clickable {
                                if (isSel) selectedPageIndices.remove(idx) else selectedPageIndices.add(idx)
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            val bmp = thumbs.value[spec.sourcePageIndex]
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Page ${idx + 1}",
                                    modifier = Modifier.height(140.dp).clip(RoundedCornerShape(6.dp))
                                )
                            } else {
                                Box(Modifier.height(140.dp), contentAlignment = Alignment.Center) {
                                    Text("Page ${spec.sourcePageIndex + 1}")
                                }
                            }
                            Text("Pos ${idx + 1} (Src ${spec.sourcePageIndex + 1}) ${spec.rotationDeltaDegrees}°", style = MaterialTheme.typography.labelSmall)
                            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                                IconButton(enabled = idx > 0, onClick = {
                                    val item = pageSpecs.removeAt(idx)
                                    pageSpecs.add(idx - 1, item)
                                }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Move Left", modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = {
                                    pageSpecs[idx] = spec.copy(rotationDeltaDegrees = (spec.rotationDeltaDegrees + 90) % 360)
                                }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.RotateRight, contentDescription = "Rotate", modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = {
                                    pageSpecs.add(idx + 1, spec.copy())
                                }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate", modifier = Modifier.size(16.dp))
                                }
                                IconButton(enabled = pageSpecs.size > 1, onClick = {
                                    pageSpecs.removeAt(idx)
                                    selectedPageIndices.clear()
                                }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                }
                                IconButton(enabled = idx < pageSpecs.lastIndex, onClick = {
                                    val item = pageSpecs.removeAt(idx)
                                    pageSpecs.add(idx + 1, item)
                                }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Move Right", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    savedFile?.let { file ->
        SaveAndDownloadDialog(
            sourceFile = file,
            defaultFileName = file.name,
            storageManager = viewModel.storageManager,
            saveToDownloadsAction = { src, name, mime, overwrite ->
                viewModel.saveFileToPublicDownloads(src, name, mime, overwrite)
            },
            onDismiss = { savedFile = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergeSplitScreen(
    initialMode: String,
    viewModel: PdfFlowViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val scope = rememberCoroutineScope()
    val docs by viewModel.activeDocuments.collectAsStateWithLifecycle()

    var mode by remember(initialMode) { mutableStateOf(initialMode) } // merge, split
    val mergeQueue = remember { mutableStateListOf<LocalDocumentEntity>() }
    var splitDoc by remember(docs) { mutableStateOf(docs.firstOrNull()) }
    var splitEveryN by remember { mutableStateOf("1") }
    var isRunning by remember { mutableStateOf(false) }
    var outputFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var activeSaveFile by remember { mutableStateOf<File?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (mode == "merge") strings.toolMergePdf else strings.toolSplitPdf) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.backAction) } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(
                    onClick = { mode = "merge"; outputFiles = emptyList() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.MergeType, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.toolMergePdf)
                }
                FilledTonalButton(
                    onClick = { mode = "split"; outputFiles = emptyList() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CallSplit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.toolSplitPdf)
                }
            }

            if (mode == "merge") {
                Text("Tap documents below to add them to the merge queue (${mergeQueue.size} selected):", style = MaterialTheme.typography.titleSmall)
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (mergeQueue.isNotEmpty()) {
                        item { Text("Merge Order", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                        itemsIndexed(mergeQueue) { idx, item ->
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("${idx + 1}. ${item.title}", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Row {
                                        IconButton(enabled = idx > 0, onClick = {
                                            val moved = mergeQueue.removeAt(idx)
                                            mergeQueue.add(idx - 1, moved)
                                        }) { Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up") }
                                        IconButton(enabled = idx < mergeQueue.lastIndex, onClick = {
                                            val moved = mergeQueue.removeAt(idx)
                                            mergeQueue.add(idx + 1, moved)
                                        }) { Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down") }
                                        IconButton(onClick = { mergeQueue.removeAt(idx) }) { Icon(Icons.Default.Delete, contentDescription = "Remove") }
                                    }
                                }
                            }
                        }
                    }
                    item { Text("Available PDFs", fontWeight = FontWeight.Bold) }
                    items(docs, key = { it.id }) { doc ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                if (doc !in mergeQueue) mergeQueue.add(doc)
                            },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(doc.title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${doc.pageCount}p", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Button(
                    enabled = mergeQueue.size >= 2 && !isRunning,
                    onClick = {
                        scope.launch {
                            isRunning = true
                            runCatching {
                                val out = viewModel.storageManager.createWorkspaceFile("Merged_${System.currentTimeMillis()}.pdf")
                                PdfEngine.mergePdfs(context, mergeQueue.map { File(it.filePath) }, out)
                                viewModel.registerGeneratedPdf(out, "Merged PDF")
                                out
                            }.onSuccess {
                                outputFiles = listOf(it)
                                activeSaveFile = it
                            }.onFailure {
                                viewModel.postStatus(it.localizedMessage ?: "Merge failed.")
                            }
                            isRunning = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("merge_execute_button")
                ) {
                    Text(if (isRunning) "Merging..." else "Merge ${mergeQueue.size} PDFs")
                }
            } else {
                Text("Select a PDF and pages per split file:", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = splitEveryN,
                    onValueChange = { splitEveryN = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Pages per split PDF") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(docs, key = { it.id }) { doc ->
                        val active = splitDoc?.id == doc.id
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(if (active) 2.dp else 0.dp, if (active) MaterialTheme.colorScheme.primary else Color.Transparent, MaterialTheme.shapes.medium)
                                .clickable { splitDoc = doc },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(doc.title, modifier = Modifier.weight(1f))
                                Text("${doc.pageCount}p")
                            }
                        }
                    }
                    if (outputFiles.isNotEmpty()) {
                        item { Text("Created Split Files", fontWeight = FontWeight.Bold, color = FlowEmerald) }
                        items(outputFiles) { f ->
                            Card(colors = CardDefaults.cardColors(containerColor = FlowEmerald.copy(alpha = 0.1f))) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(f.name, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { activeSaveFile = f }) {
                                        Icon(Icons.Default.Download, contentDescription = strings.saveAndDownload)
                                    }
                                }
                            }
                        }
                    }
                }
                Button(
                    enabled = splitDoc != null && !isRunning,
                    onClick = {
                        val doc = splitDoc ?: return@Button
                        scope.launch {
                            isRunning = true
                            val n = splitEveryN.toIntOrNull()?.coerceAtLeast(1) ?: 1
                            runCatching {
                                val outDir = File(context.filesDir, "pdf_workspace/splits_${System.currentTimeMillis()}")
                                val parts = PdfEngine.splitPdfEveryNPages(context, File(doc.filePath), n, outDir, doc.title)
                                parts.forEach { part -> viewModel.registerGeneratedPdf(part, part.nameWithoutExtension, doc.folderName) }
                                parts
                            }.onSuccess {
                                outputFiles = it
                                viewModel.postStatus("Split into ${it.size} PDF files.")
                            }.onFailure {
                                viewModel.postStatus(it.localizedMessage ?: "Split failed.")
                            }
                            isRunning = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("split_execute_button")
                ) {
                    Text(if (isRunning) "Splitting..." else "Split PDF")
                }
            }
        }
    }

    activeSaveFile?.let { file ->
        SaveAndDownloadDialog(
            sourceFile = file,
            defaultFileName = file.name,
            storageManager = viewModel.storageManager,
            saveToDownloadsAction = { src, name, mime, overwrite ->
                viewModel.saveFileToPublicDownloads(src, name, mime, overwrite)
            },
            onDismiss = { activeSaveFile = null }
        )
    }
}
