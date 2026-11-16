package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.localization.AppLanguage
import com.example.localization.LocalAppStrings
import com.example.pdf.OcrAndAiEngine
import com.example.pdf.OcrScriptOption
import com.example.pdf.PdfConverterEngine
import com.example.pdf.PdfEngine
import com.example.pdf.ScanFilterMode
import com.example.ui.components.SaveAndDownloadDialog
import com.example.ui.theme.FlowAmber
import com.example.ui.theme.FlowCrimson
import com.example.ui.theme.FlowIndigo
import com.example.ui.theme.FlowViolet
import com.example.viewmodel.PdfFlowViewModel
import kotlinx.coroutines.launch
import java.io.File

private data class ScannedPageItem(
    val original: Bitmap,
    val filter: ScanFilterMode = ScanFilterMode.ORIGINAL,
    val rotation: Int = 0
) {
    fun processedBitmap(): Bitmap {
        val rotated = if (rotation != 0) {
            val m = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(original, 0, 0, original.width, original.height, m, true)
        } else original
        return PdfConverterEngine.applyScanFilter(rotated, filter)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScannerScreen(
    viewModel: PdfFlowViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val strings = LocalAppStrings.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
    }

    val scannedPages = remember { mutableStateListOf<ScannedPageItem>() }
    var activeIndex by remember { mutableStateOf(0) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var savedPdfFile by remember { mutableStateOf<File?>(null) }

    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
    ) { uris ->
        uris.forEach { uri ->
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)?.let { bmp ->
                    scannedPages.add(ScannedPageItem(bmp))
                }
            }
        }
        if (scannedPages.isNotEmpty()) activeIndex = scannedPages.lastIndex
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.toolScanDoc) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.backAction) }
                },
                actions = {
                    IconButton(onClick = {
                        galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = "Import Photos")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (scannedPages.isEmpty()) {
                if (hasCameraPermission) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .background(Color.Black)
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                val previewView = PreviewView(ctx)
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                cameraProviderFuture.addListener({
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.surfaceProvider = previewView.surfaceProvider
                                    }
                                    val capture = ImageCapture.Builder()
                                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                        .build()
                                    imageCapture = capture
                                    runCatching {
                                        cameraProvider.unbindAll()
                                        cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview,
                                            capture
                                        )
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                                previewView
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                val cap = imageCapture ?: return@Button
                                val outFile = viewModel.storageManager.createTempFile("scan_${System.currentTimeMillis()}.jpg")
                                val opts = ImageCapture.OutputFileOptions.Builder(outFile).build()
                                cap.takePicture(
                                    opts,
                                    ContextCompat.getMainExecutor(context),
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                            BitmapFactory.decodeFile(outFile.absolutePath)?.let { bmp ->
                                                scannedPages.add(ScannedPageItem(bmp))
                                                activeIndex = scannedPages.lastIndex
                                            }
                                        }
                                        override fun onError(exception: ImageCaptureException) {
                                            viewModel.postStatus(exception.localizedMessage ?: "Capture failed.")
                                        }
                                    }
                                )
                            },
                            modifier = Modifier.weight(1f).height(52.dp).testTag("scanner_capture_button")
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Capture Page")
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = FlowCrimson, modifier = Modifier.size(56.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("Camera Permission Needed", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Allow Camera access to scan physical documents, or import existing photos using the Photo Picker.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                                Text("Enable Camera")
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = {
                                galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }) {
                                Text("Import from Photos")
                            }
                        }
                    }
                }
            } else {
                val safeIdx = activeIndex.coerceIn(0, scannedPages.lastIndex)
                val currentItem = scannedPages[safeIdx]
                val previewBmp = remember(currentItem) { currentItem.processedBitmap() }

                Image(
                    bitmap = previewBmp.asImageBitmap(),
                    contentDescription = "Scanned Page ${safeIdx + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(Color.Black)
                )

                // Filter & Page Controls
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ScanFilterMode.entries) { mode ->
                        val active = currentItem.filter == mode
                        Surface(
                            shape = CircleShape,
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable {
                                scannedPages[safeIdx] = currentItem.copy(filter = mode)
                            }
                        ) {
                            Text(
                                mode.name.replace('_', ' '),
                                color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(enabled = safeIdx > 0, onClick = {
                        val item = scannedPages.removeAt(safeIdx)
                        scannedPages.add(safeIdx - 1, item)
                        activeIndex = safeIdx - 1
                    }) { Icon(Icons.Default.ChevronLeft, contentDescription = "Move Left") }
                    IconButton(onClick = {
                        scannedPages[safeIdx] = currentItem.copy(rotation = (currentItem.rotation + 90) % 360)
                    }) { Icon(Icons.Default.RotateRight, contentDescription = "Rotate 90°") }
                    IconButton(onClick = {
                        // Trim 8% outer margin crop
                        val src = currentItem.original
                        val cw = (src.width * 0.84f).toInt().coerceAtLeast(100)
                        val ch = (src.height * 0.84f).toInt().coerceAtLeast(100)
                        val cropped = Bitmap.createBitmap(src, (src.width - cw) / 2, (src.height - ch) / 2, cw, ch)
                        scannedPages[safeIdx] = currentItem.copy(original = cropped)
                    }) { Icon(Icons.Default.Crop, contentDescription = "Trim Edges") }
                    IconButton(onClick = {
                        scannedPages.removeAt(safeIdx)
                        activeIndex = (safeIdx - 1).coerceAtLeast(0)
                    }) { Icon(Icons.Default.Delete, contentDescription = "Remove Page", tint = MaterialTheme.colorScheme.error) }
                    IconButton(enabled = safeIdx < scannedPages.lastIndex, onClick = {
                        val item = scannedPages.removeAt(safeIdx)
                        scannedPages.add(safeIdx + 1, item)
                        activeIndex = safeIdx + 1
                    }) { Icon(Icons.Default.ChevronRight, contentDescription = "Move Right") }
                }

                // Multi-page thumbnail strip
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(scannedPages) { idx, _ ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (idx == safeIdx) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { activeIndex = idx }
                        ) {
                            Text(
                                "Page ${idx + 1}",
                                color = if (idx == safeIdx) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = {
                        galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }, modifier = Modifier.weight(1f)) {
                        Text("+ Add Page")
                    }
                    Button(
                        enabled = !isSaving,
                        onClick = {
                            scope.launch {
                                isSaving = true
                                runCatching {
                                    val bmps = scannedPages.map { it.processedBitmap() }
                                    val out = viewModel.storageManager.createWorkspaceFile("Scan_${System.currentTimeMillis()}.pdf")
                                    PdfConverterEngine.bitmapsToPdf(context, bmps, out)
                                    viewModel.registerGeneratedPdf(out, "Scanned Document")
                                    out
                                }.onSuccess {
                                    savedPdfFile = it
                                    viewModel.postStatus("Scanned PDF saved (${it.name}).")
                                }.onFailure {
                                    viewModel.postStatus(it.localizedMessage ?: "Failed to create scanned PDF.")
                                }
                                isSaving = false
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("scanner_save_pdf_button")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isSaving) "Saving..." else "Save as PDF")
                    }
                }
            }
        }
    }

    savedPdfFile?.let { file ->
        SaveAndDownloadDialog(
            sourceFile = file,
            defaultFileName = file.name,
            storageManager = viewModel.storageManager,
            saveToDownloadsAction = { src, name, mime, overwrite ->
                viewModel.saveFileToPublicDownloads(src, name, mime, overwrite)
            },
            onDismiss = { savedPdfFile = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrScreen(
    viewModel: PdfFlowViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val scope = rememberCoroutineScope()
    val docs by viewModel.activeDocuments.collectAsStateWithLifecycle()

    var selectedDoc by remember(docs) { mutableStateOf(docs.firstOrNull()) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedScript by remember { mutableStateOf(OcrScriptOption.LATIN) }
    var extractedText by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }
    var exportedPdf by remember { mutableStateOf<File?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) selectedImageUri = uri
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.toolOcr) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.backAction) } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = FlowAmber.copy(alpha = 0.12f))) {
                    Text(
                        selectedScript.accuracyNote,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            item {
                Text("1. Choose OCR Script / Language", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(OcrScriptOption.entries) { opt ->
                        val active = opt == selectedScript
                        Surface(
                            shape = CircleShape,
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { selectedScript = opt }
                        ) {
                            Text(
                                opt.label,
                                color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            item {
                Text("2. Select Source PDF or Image", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (selectedImageUri != null) "Image Selected" else "Pick Image")
                    }
                    if (selectedImageUri != null) {
                        TextButton(onClick = { selectedImageUri = null }) { Text("Use PDF Instead") }
                    }
                }
                if (selectedImageUri == null && docs.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        items(docs, key = { it.id }) { doc ->
                            val active = selectedDoc?.id == doc.id
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                modifier = Modifier.clickable { selectedDoc = doc }
                            ) {
                                Text(doc.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(10.dp))
                            }
                        }
                    }
                }
            }

            item {
                Button(
                    enabled = !isRunning && (selectedImageUri != null || selectedDoc != null),
                    onClick = {
                        scope.launch {
                            isRunning = true
                            val result = if (selectedImageUri != null) {
                                val bmp = context.contentResolver.openInputStream(selectedImageUri!!)?.use { BitmapFactory.decodeStream(it) }
                                if (bmp != null) OcrAndAiEngine.recognizeBitmapText(bmp, selectedScript)
                                else Result.failure(IllegalStateException("Could not decode image."))
                            } else {
                                OcrAndAiEngine.runOcrOnPdf(context, File(selectedDoc!!.filePath), selectedScript)
                            }
                            result.onSuccess { extractedText = it }
                                .onFailure { viewModel.postStatus(it.localizedMessage ?: "OCR failed.") }
                            isRunning = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("ocr_run_button")
                ) {
                    Icon(Icons.Default.TextSnippet, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isRunning) "Recognizing Text..." else "Run OCR")
                }
            }

            if (extractedText.isNotBlank()) {
                item {
                    OutlinedTextField(
                        value = extractedText,
                        onValueChange = { extractedText = it },
                        label = { Text("Extracted Text (Editable)") },
                        minLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("OCR Text", extractedText))
                                viewModel.postStatus("Copied OCR text.")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copy")
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    val out = viewModel.storageManager.createWorkspaceFile("OCR_${System.currentTimeMillis()}.pdf")
                                    PdfConverterEngine.textToPdf("OCR Extracted Document", extractedText, out)
                                    viewModel.registerGeneratedPdf(out, "OCR Export")
                                    exportedPdf = out
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Export PDF")
                        }
                    }
                }
            }
        }
    }

    exportedPdf?.let { file ->
        SaveAndDownloadDialog(
            sourceFile = file,
            defaultFileName = file.name,
            storageManager = viewModel.storageManager,
            saveToDownloadsAction = { src, name, mime, overwrite ->
                viewModel.saveFileToPublicDownloads(src, name, mime, overwrite)
            },
            onDismiss = { exportedPdf = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiStudioScreen(
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
    var customInputText by remember { mutableStateOf("") }
    var targetTranslateLang by remember { mutableStateOf(AppLanguage.ENGLISH) }
    var qaQuestion by remember { mutableStateOf("") }
    var aiOutput by remember { mutableStateOf("") }
    var aiError by remember { mutableStateOf<String?>(null) }
    var isStreaming by remember { mutableStateOf(false) }
    var savedPdf by remember { mutableStateOf<File?>(null) }

    fun runAiTask(instruction: String, saveAsSummary: Boolean = false) {
        scope.launch {
            isStreaming = true
            aiError = null
            aiOutput = ""
            val sourceTitle = selectedDoc?.title ?: "Custom Document"
            val docText = if (selectedDoc != null) {
                runCatching {
                    PdfEngine.extractAllText(context, File(selectedDoc!!.filePath)).joinToString("\n")
                }.getOrDefault("").ifBlank { customInputText }
            } else {
                customInputText
            }
            if (docText.isBlank()) {
                aiError = "No selectable text found in the PDF. Paste text or run OCR first for scanned PDFs."
                isStreaming = false
                return@launch
            }
            OcrAndAiEngine.streamPdfAiTask(sourceTitle, docText, instruction).collect { res ->
                res.onSuccess {
                    aiOutput = it
                    if (saveAsSummary && selectedDoc != null) {
                        viewModel.saveSummaryForDocument(selectedDoc!!.id, it.take(400))
                    }
                }.onFailure { err ->
                    aiError = err.localizedMessage ?: "AI generation failed."
                }
            }
            isStreaming = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.toolAiAssistant) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.backAction) } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Select Document", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(docs, key = { it.id }) { doc ->
                        val active = selectedDoc?.id == doc.id
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            modifier = Modifier.clickable { selectedDoc = doc }
                        ) {
                            Text(
                                doc.title,
                                color = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }

            if (selectedDoc == null) {
                item {
                    OutlinedTextField(
                        value = customInputText,
                        onValueChange = { customInputText = it },
                        label = { Text("Paste document text for AI analysis") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    FilledTonalButton(
                        enabled = !isStreaming,
                        onClick = { runAiTask("Write a clear, structured executive summary with key takeaways and action items.", saveAsSummary = true) },
                        modifier = Modifier.weight(1f).testTag("ai_summarize_button")
                    ) { Text(strings.aiSummary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    FilledTonalButton(
                        enabled = !isStreaming,
                        onClick = { runAiTask("Extract dates, names, amounts, action points, and key structured facts.") },
                        modifier = Modifier.weight(1f).testTag("ai_extract_button")
                    ) { Text(strings.aiExtractInfo, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    FilledTonalButton(
                        enabled = !isStreaming,
                        onClick = { runAiTask("Generate concise study notes, bullet flashcards, and revision highlights from this content.") },
                        modifier = Modifier.weight(1f).testTag("ai_notes_button")
                    ) { Text(strings.aiGenerateNotes, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    FilledTonalButton(
                        enabled = !isStreaming,
                        onClick = { runAiTask("Translate the essential document content accurately into ${targetTranslateLang.englishName} (${targetTranslateLang.nativeName}).") },
                        modifier = Modifier.weight(1f).testTag("ai_translate_button")
                    ) { Text("${strings.aiTranslateText} (${targetTranslateLang.nativeName})", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(AppLanguage.entries) { lang ->
                        val active = lang == targetTranslateLang
                        Surface(
                            shape = CircleShape,
                            color = if (active) FlowViolet else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { targetTranslateLang = lang }
                        ) {
                            Text(
                                lang.nativeName,
                                color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = qaQuestion,
                        onValueChange = { qaQuestion = it },
                        label = { Text(strings.aiAskQuestions) },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("ai_question_input")
                    )
                    Button(
                        enabled = qaQuestion.isNotBlank() && !isStreaming,
                        onClick = {
                            val q = qaQuestion
                            runAiTask("Answer this question based strictly on the document content: $q")
                        },
                        modifier = Modifier.testTag("ai_ask_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Ask AI")
                    }
                }
            }

            if (isStreaming) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Generating with PDF Flow AI...")
                    }
                }
            }

            aiError?.let { err ->
                item {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
                        Text(err, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(12.dp))
                    }
                }
            }

            if (aiOutput.isNotBlank()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = FlowIndigo)
                                Spacer(Modifier.width(8.dp))
                                Text("AI Result", fontWeight = FontWeight.Bold)
                            }
                            Text(aiOutput, style = MaterialTheme.typography.bodyMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("AI Result", aiOutput))
                                    viewModel.postStatus("Copied AI response.")
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Copy")
                                }
                                Button(onClick = {
                                    scope.launch {
                                        val out = viewModel.storageManager.createWorkspaceFile("AI_Summary_${System.currentTimeMillis()}.pdf")
                                        PdfConverterEngine.textToPdf(selectedDoc?.title ?: "AI Notes", aiOutput, out)
                                        viewModel.registerGeneratedPdf(out, "AI Result PDF")
                                        savedPdf = out
                                    }
                                }) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Save as PDF")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    savedPdf?.let { file ->
        SaveAndDownloadDialog(
            sourceFile = file,
            defaultFileName = file.name,
            storageManager = viewModel.storageManager,
            saveToDownloadsAction = { src, name, mime, overwrite ->
                viewModel.saveFileToPublicDownloads(src, name, mime, overwrite)
            },
            onDismiss = { savedPdf = null }
        )
    }
}
