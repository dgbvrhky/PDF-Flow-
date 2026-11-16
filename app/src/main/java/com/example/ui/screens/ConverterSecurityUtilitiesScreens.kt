package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.LocalDocumentEntity
import com.example.data.storage.PdfStorageManager
import com.example.localization.LocalAppStrings
import com.example.pdf.CompressionLevel
import com.example.pdf.CompressionResult
import com.example.pdf.PdfComparisonResult
import com.example.pdf.PdfConverterEngine
import com.example.pdf.PdfEditingEngine
import com.example.pdf.PdfEngine
import com.example.pdf.PdfMetadataInfo
import com.example.ui.components.SaveAndDownloadDialog
import com.example.ui.theme.FlowAmber
import com.example.ui.theme.FlowEmerald
import com.example.ui.theme.FlowIndigo
import com.example.viewmodel.PdfFlowViewModel
import kotlinx.coroutines.launch
import java.io.File

private enum class ConverterMode(val key: String, val label: String) {
    CREATE("create", "Create PDF"),
    IMG_TO_PDF("img_to_pdf", "Images -> PDF"),
    PDF_TO_IMG("pdf_to_img", "PDF -> JPG/PNG"),
    PDF_TO_DOCX("pdf_to_docx", "PDF -> DOCX"),
    DOCX_TO_PDF("docx_to_pdf", "DOCX -> PDF"),
    PDF_TO_CSV("pdf_to_csv", "PDF -> CSV"),
    CSV_TO_PDF("csv_to_pdf", "CSV -> PDF"),
    PDF_TO_TXT("pdf_to_txt", "PDF -> TXT"),
    HTML_TO_PDF("html_to_pdf", "HTML -> PDF")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConverterScreen(
    initialMode: String,
    viewModel: PdfFlowViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val scope = rememberCoroutineScope()
    val docs by viewModel.activeDocuments.collectAsStateWithLifecycle()

    var mode by remember(initialMode) {
        mutableStateOf(ConverterMode.entries.firstOrNull { it.key == initialMode } ?: ConverterMode.CREATE)
    }
    var selectedDoc by remember(docs) { mutableStateOf(docs.firstOrNull()) }

    var createTitle by remember { mutableStateOf("Untitled Document") }
    var createBody by remember { mutableStateOf("") }
    var htmlInput by remember { mutableStateOf("<h1>PDF Flow Report</h1><p>Write or paste HTML here to convert cleanly to PDF.</p>") }
    var exportPng by remember { mutableStateOf(false) }

    var isWorking by remember { mutableStateOf(false) }
    var noteBanner by remember { mutableStateOf<String?>(null) }
    var outputFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var outputMime by remember { mutableStateOf("application/pdf") }
    var saveDialogFile by remember { mutableStateOf<File?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30)
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                isWorking = true
                runCatching {
                    val out = viewModel.storageManager.createWorkspaceFile("Images_${System.currentTimeMillis()}.pdf")
                    PdfConverterEngine.imagesToPdf(context, uris, out)
                    viewModel.registerGeneratedPdf(out, "Images PDF")
                    out
                }.onSuccess {
                    outputFiles = listOf(it)
                    outputMime = "application/pdf"
                    noteBanner = "Converted ${uris.size} image(s) into ${it.name}."
                    saveDialogFile = it
                }.onFailure { viewModel.postStatus(it.localizedMessage ?: "Conversion failed.") }
                isWorking = false
            }
        }
    }

    val docxPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isWorking = true
                runCatching {
                    val out = viewModel.storageManager.createWorkspaceFile("Word_${System.currentTimeMillis()}.pdf")
                    val report = PdfConverterEngine.docxToPdf(context, uri, "Converted Word Document", out)
                    viewModel.registerGeneratedPdf(out, "Word PDF")
                    report
                }.onSuccess { report ->
                    outputFiles = report.outputFiles
                    outputMime = "application/pdf"
                    noteBanner = report.note
                    saveDialogFile = report.outputFiles.firstOrNull()
                }.onFailure { viewModel.postStatus(it.localizedMessage ?: "DOCX conversion failed.") }
                isWorking = false
            }
        }
    }

    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isWorking = true
                runCatching {
                    val out = viewModel.storageManager.createWorkspaceFile("Sheet_${System.currentTimeMillis()}.pdf")
                    val report = PdfConverterEngine.csvOrSpreadsheetTextToPdf(context, uri, "Spreadsheet Export", out)
                    viewModel.registerGeneratedPdf(out, "Spreadsheet PDF")
                    report
                }.onSuccess { report ->
                    outputFiles = report.outputFiles
                    outputMime = "application/pdf"
                    noteBanner = report.note
                    saveDialogFile = report.outputFiles.firstOrNull()
                }.onFailure { viewModel.postStatus(it.localizedMessage ?: "CSV conversion failed.") }
                isWorking = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (mode == ConverterMode.CREATE) strings.toolCreatePdf else strings.toolConvertPdf) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.backAction) } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ConverterMode.entries) { m ->
                        val active = m == mode
                        Surface(
                            shape = CircleShape,
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable {
                                mode = m
                                outputFiles = emptyList()
                                noteBanner = null
                            }
                        ) {
                            Text(
                                m.label,
                                color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            if (mode in listOf(ConverterMode.PDF_TO_IMG, ConverterMode.PDF_TO_DOCX, ConverterMode.PDF_TO_CSV, ConverterMode.PDF_TO_TXT)) {
                item {
                    Text("Select Source PDF", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

            when (mode) {
                ConverterMode.CREATE -> {
                    item {
                        OutlinedTextField(
                            value = createTitle,
                            onValueChange = { createTitle = it },
                            label = { Text("Document Title") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("create_pdf_title_input")
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = createBody,
                            onValueChange = { createBody = it },
                            label = { Text("Content (Supports Unicode & Multi-page)") },
                            minLines = 8,
                            modifier = Modifier.fillMaxWidth().testTag("create_pdf_body_input")
                        )
                    }
                    item {
                        Button(
                            enabled = !isWorking && (createTitle.isNotBlank() || createBody.isNotBlank()),
                            onClick = {
                                scope.launch {
                                    isWorking = true
                                    runCatching {
                                        val safeName = PdfStorageManager.sanitizeFileName(createTitle).removeSuffix(".pdf") + ".pdf"
                                        val out = viewModel.storageManager.createWorkspaceFile(safeName)
                                        PdfConverterEngine.textToPdf(createTitle, createBody, out)
                                        viewModel.registerGeneratedPdf(out, createTitle.ifBlank { out.nameWithoutExtension })
                                        out
                                    }.onSuccess {
                                        outputFiles = listOf(it)
                                        outputMime = "application/pdf"
                                        noteBanner = "Created ${it.name} (${PdfStorageManager.formatBytes(it.length())})."
                                        saveDialogFile = it
                                    }.onFailure { viewModel.postStatus(it.localizedMessage ?: "Failed to create PDF.") }
                                    isWorking = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("create_pdf_generate_button")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (isWorking) "Generating..." else "Generate PDF")
                        }
                    }
                }

                ConverterMode.IMG_TO_PDF -> {
                    item {
                        Button(
                            enabled = !isWorking,
                            onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Select Photos to Convert to PDF")
                        }
                    }
                }

                ConverterMode.DOCX_TO_PDF -> {
                    item {
                        Button(
                            enabled = !isWorking,
                            onClick = {
                                docxPicker.launch(arrayOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "*/*"))
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Pick .docx File to Convert")
                        }
                    }
                }

                ConverterMode.CSV_TO_PDF -> {
                    item {
                        Button(
                            enabled = !isWorking,
                            onClick = { csvPicker.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain")) },
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Pick .csv / Table Text File")
                        }
                    }
                }

                ConverterMode.HTML_TO_PDF -> {
                    item {
                        OutlinedTextField(
                            value = htmlInput,
                            onValueChange = { htmlInput = it },
                            label = { Text("HTML Markup") },
                            minLines = 6,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        Button(
                            enabled = !isWorking && htmlInput.isNotBlank(),
                            onClick = {
                                scope.launch {
                                    isWorking = true
                                    runCatching {
                                        val out = viewModel.storageManager.createWorkspaceFile("HtmlExport_${System.currentTimeMillis()}.pdf")
                                        PdfConverterEngine.htmlToPdf(htmlInput, "HTML Export", out)
                                        viewModel.registerGeneratedPdf(out, "HTML Export")
                                        out
                                    }.onSuccess {
                                        outputFiles = listOf(it)
                                        outputMime = "application/pdf"
                                        saveDialogFile = it
                                    }.onFailure { viewModel.postStatus(it.localizedMessage ?: "HTML conversion failed.") }
                                    isWorking = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) { Text("Convert HTML to PDF") }
                    }
                }

                ConverterMode.PDF_TO_IMG -> {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = exportPng, onCheckedChange = { exportPng = it })
                            Text("Export as PNG (uncheck for JPG)")
                        }
                        Button(
                            enabled = !isWorking && selectedDoc != null,
                            onClick = {
                                val doc = selectedDoc ?: return@Button
                                scope.launch {
                                    isWorking = true
                                    runCatching {
                                        val outDir = File(context.cacheDir, "pdf_temp/img_${System.currentTimeMillis()}")
                                        PdfConverterEngine.pdfToImages(context, File(doc.filePath), outDir, doc.title, exportPng)
                                    }.onSuccess { imgs ->
                                        outputFiles = imgs
                                        outputMime = if (exportPng) "image/png" else "image/jpeg"
                                        noteBanner = "Exported ${imgs.size} page image(s)."
                                    }.onFailure { viewModel.postStatus(it.localizedMessage ?: "Export failed.") }
                                    isWorking = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) { Text(if (isWorking) "Exporting..." else "Export Pages to Images") }
                    }
                }

                ConverterMode.PDF_TO_DOCX, ConverterMode.PDF_TO_CSV, ConverterMode.PDF_TO_TXT -> {
                    item {
                        Button(
                            enabled = !isWorking && selectedDoc != null,
                            onClick = {
                                val doc = selectedDoc ?: return@Button
                                scope.launch {
                                    isWorking = true
                                    runCatching {
                                        when (mode) {
                                            ConverterMode.PDF_TO_DOCX -> {
                                                val out = viewModel.storageManager.createWorkspaceFile("${doc.title}.docx")
                                                outputMime = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                                PdfConverterEngine.pdfToDocx(context, File(doc.filePath), out)
                                            }
                                            ConverterMode.PDF_TO_CSV -> {
                                                val out = viewModel.storageManager.createWorkspaceFile("${doc.title}.csv")
                                                outputMime = "text/csv"
                                                PdfConverterEngine.pdfToCsv(context, File(doc.filePath), out)
                                            }
                                            else -> {
                                                val out = viewModel.storageManager.createWorkspaceFile("${doc.title}.txt")
                                                outputMime = "text/plain"
                                                PdfConverterEngine.pdfToTxt(context, File(doc.filePath), out)
                                                com.example.pdf.ConversionReport(listOf(out), "Extracted selectable text to .txt.")
                                            }
                                        }
                                    }.onSuccess { rep ->
                                        outputFiles = rep.outputFiles
                                        noteBanner = rep.note
                                        saveDialogFile = rep.outputFiles.firstOrNull()
                                    }.onFailure { viewModel.postStatus(it.localizedMessage ?: "Export failed.") }
                                    isWorking = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) { Text(if (isWorking) "Converting..." else "Run ${mode.label}") }
                    }
                }
            }

            noteBanner?.let { note ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))) {
                        Text(note, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                    }
                }
            }

            if (outputFiles.isNotEmpty()) {
                items(outputFiles) { f ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(f.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(PdfStorageManager.formatBytes(f.length()), style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { saveDialogFile = f }) {
                                Icon(Icons.Default.Download, contentDescription = strings.saveAndDownload)
                            }
                        }
                    }
                }
            }
        }
    }

    saveDialogFile?.let { file ->
        SaveAndDownloadDialog(
            sourceFile = file,
            defaultFileName = file.name,
            mimeType = outputMime,
            storageManager = viewModel.storageManager,
            saveToDownloadsAction = { src, name, mime, overwrite ->
                viewModel.saveFileToPublicDownloads(src, name, mime, overwrite)
            },
            onDismiss = { saveDialogFile = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressorScreen(
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
    var level by remember { mutableStateOf(CompressionLevel.MEDIUM) }
    var customQuality by remember { mutableFloatStateOf(55f) }
    var isRunning by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<CompressionResult?>(null) }
    var showSaveDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.toolCompressPdf) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.backAction) } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Choose PDF to Compress", fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(docs, key = { it.id }) { doc ->
                    val active = selectedDoc?.id == doc.id
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        modifier = Modifier.clickable { selectedDoc = doc; result = null }
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(doc.title, color = if (active) Color.White else MaterialTheme.colorScheme.onSurface, maxLines = 1)
                            Text(
                                PdfStorageManager.formatBytes(doc.sizeBytes),
                                color = if (active) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            Text("Compression Level", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CompressionLevel.entries.forEach { lvl ->
                    val active = level == lvl
                    Surface(
                        shape = CircleShape,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.weight(1f).clickable { level = lvl }
                    ) {
                        Text(
                            lvl.label,
                            color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp)
                        )
                    }
                }
            }

            if (level == CompressionLevel.CUSTOM) {
                Text("Image Quality: ${customQuality.toInt()}%")
                Slider(value = customQuality, onValueChange = { customQuality = it }, valueRange = 20f..90f)
            }

            Button(
                enabled = selectedDoc != null && !isRunning,
                onClick = {
                    val doc = selectedDoc ?: return@Button
                    scope.launch {
                        isRunning = true
                        runCatching {
                            val out = viewModel.storageManager.createWorkspaceFile("Compressed_${doc.fileName}")
                            val res = PdfEngine.compressPdf(context, File(doc.filePath), level, customQuality.toInt(), out)
                            viewModel.registerGeneratedPdf(out, "${doc.title} Compressed", doc.folderName)
                            res
                        }.onSuccess { result = it }
                            .onFailure { viewModel.postStatus(it.localizedMessage ?: "Compression failed.") }
                        isRunning = false
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("compress_execute_button")
            ) {
                Icon(Icons.Default.Compress, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (isRunning) "Compressing..." else "Compress PDF")
            }

            result?.let { res ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (res.wasReduced) "Size Reduced by ${res.savedPercent}%" else "Already Optimized (Original Retained)",
                            fontWeight = FontWeight.Bold,
                            color = if (res.wasReduced) FlowEmerald else MaterialTheme.colorScheme.primary
                        )
                        Text("Original: ${PdfStorageManager.formatBytes(res.originalBytes)}")
                        Text("Output: ${PdfStorageManager.formatBytes(res.compressedBytes)}")
                        Button(onClick = { showSaveDialog = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(strings.saveAndDownload)
                        }
                    }
                }
            }
        }
    }

    result?.let { res ->
        if (showSaveDialog) {
            SaveAndDownloadDialog(
                sourceFile = res.outputFile,
                defaultFileName = res.outputFile.name,
                storageManager = viewModel.storageManager,
                saveToDownloadsAction = { src, name, mime, overwrite ->
                    viewModel.saveFileToPublicDownloads(src, name, mime, overwrite)
                },
                onDismiss = { showSaveDialog = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScreen(
    initialMode: String,
    initialDocId: String?,
    viewModel: PdfFlowViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val scope = rememberCoroutineScope()
    val docs by viewModel.activeDocuments.collectAsStateWithLifecycle()

    var mode by remember(initialMode) { mutableStateOf(initialMode) } // protect, unlock
    var selectedDoc by remember(docs, initialDocId) {
        mutableStateOf(docs.firstOrNull { it.id == initialDocId } ?: docs.firstOrNull())
    }
    var userPassword by remember { mutableStateOf("") }
    var ownerPassword by remember { mutableStateOf("") }
    var allowPrint by remember { mutableStateOf(true) }
    var allowCopy by remember { mutableStateOf(false) }
    var allowModify by remember { mutableStateOf(false) }
    var isWorking by remember { mutableStateOf(false) }
    var outputFile by remember { mutableStateOf<File?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (mode == "protect") strings.toolProtectPdf else strings.toolUnlockPdf) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.backAction) } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    FilledTonalButton(onClick = { mode = "protect" }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.toolProtectPdf)
                    }
                    FilledTonalButton(onClick = { mode = "unlock" }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.toolUnlockPdf)
                    }
                }
            }

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
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = userPassword,
                    onValueChange = { userPassword = it },
                    label = { Text(if (mode == "protect") "Open Password (Required)" else "Current Authorized Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("security_password_input")
                )
            }

            if (mode == "protect") {
                item {
                    OutlinedTextField(
                        value = ownerPassword,
                        onValueChange = { ownerPassword = it },
                        label = { Text("Owner / Permissions Password (Optional)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = allowPrint, onCheckedChange = { allowPrint = it })
                        Text("Allow Printing")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = allowCopy, onCheckedChange = { allowCopy = it })
                        Text("Allow Text & Image Copy")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = allowModify, onCheckedChange = { allowModify = it })
                        Text("Allow Modifying Annotations")
                    }
                }
            }

            item {
                Button(
                    enabled = selectedDoc != null && userPassword.isNotBlank() && !isWorking,
                    onClick = {
                        val doc = selectedDoc ?: return@Button
                        scope.launch {
                            isWorking = true
                            runCatching {
                                if (mode == "protect") {
                                    val out = viewModel.storageManager.createWorkspaceFile("Protected_${doc.fileName}")
                                    PdfEngine.protectPdf(context, File(doc.filePath), userPassword, ownerPassword, allowPrint, allowCopy, allowModify, out)
                                    viewModel.registerGeneratedPdf(out, "${doc.title} Protected", doc.folderName, isEncrypted = true)
                                    out
                                } else {
                                    val out = viewModel.storageManager.createWorkspaceFile("Unlocked_${doc.fileName}")
                                    PdfEngine.unlockPdf(context, File(doc.filePath), userPassword, out)
                                    viewModel.registerGeneratedPdf(out, "${doc.title} Unlocked", doc.folderName, isEncrypted = false)
                                    out
                                }
                            }.onSuccess {
                                outputFile = it
                                viewModel.postStatus(if (mode == "protect") "PDF password protected." else "PDF unlocked.")
                            }.onFailure {
                                viewModel.postStatus(it.localizedMessage ?: "Security operation failed. Check password.")
                            }
                            isWorking = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("security_execute_button")
                ) {
                    Text(if (isWorking) "Processing..." else if (mode == "protect") "Encrypt & Protect PDF" else "Unlock PDF")
                }
            }
        }
    }

    outputFile?.let { file ->
        SaveAndDownloadDialog(
            sourceFile = file,
            defaultFileName = file.name,
            storageManager = viewModel.storageManager,
            saveToDownloadsAction = { src, name, mime, overwrite ->
                viewModel.saveFileToPublicDownloads(src, name, mime, overwrite)
            },
            onDismiss = { outputFile = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UtilitiesScreen(
    initialTab: String,
    initialDocId: String?,
    viewModel: PdfFlowViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val scope = rememberCoroutineScope()
    val docs by viewModel.activeDocuments.collectAsStateWithLifecycle()

    var tab by remember(initialTab) { mutableStateOf(initialTab) } // compare, metadata, images, attachments, repair, batch
    var docA by remember(docs, initialDocId) { mutableStateOf(docs.firstOrNull { it.id == initialDocId } ?: docs.firstOrNull()) }
    var docB by remember(docs) { mutableStateOf(docs.getOrNull(1) ?: docs.firstOrNull()) }

    var comparisonResult by remember { mutableStateOf<PdfComparisonResult?>(null) }
    var metaInfo by remember { mutableStateOf<PdfMetadataInfo?>(null) }
    var editTitle by remember { mutableStateOf("") }
    var editAuthor by remember { mutableStateOf("") }
    var editSubject by remember { mutableStateOf("") }
    var editKeywords by remember { mutableStateOf("") }
    var extractedImages by remember { mutableStateOf<List<File>>(emptyList()) }
    var attachments by remember { mutableStateOf<List<Pair<String, Long>>>(emptyList()) }
    val batchSelected = remember { mutableStateListOf<String>() }
    var batchWatermark by remember { mutableStateOf("CONFIDENTIAL") }
    var saveFile by remember { mutableStateOf<File?>(null) }
    var saveMime by remember { mutableStateOf("application/pdf") }

    LaunchedEffect(docA, tab) {
        val f = docA?.let { File(it.filePath) } ?: return@LaunchedEffect
        if (!f.exists()) return@LaunchedEffect
        if (tab == "metadata") {
            runCatching { PdfEngine.getMetadata(context, f) }.getOrNull()?.let { info ->
                metaInfo = info
                editTitle = info.title
                editAuthor = info.author
                editSubject = info.subject
                editKeywords = info.keywords
            }
        } else if (tab == "attachments") {
            attachments = runCatching { PdfEngine.listAttachments(context, f) }.getOrDefault(emptyList())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PDF Utilities") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.backAction) } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                val tabs = listOf(
                    "compare" to "Compare",
                    "metadata" to "Metadata",
                    "images" to "Images",
                    "attachments" to "Attachments",
                    "repair" to "Repair",
                    "batch" to "Batch"
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(tabs) { (key, label) ->
                        val active = tab == key
                        Surface(
                            shape = CircleShape,
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { tab = key }
                        ) {
                            Text(
                                label,
                                color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            if (tab != "batch") {
                item {
                    Text(if (tab == "compare") "Primary PDF (A)" else "Select PDF", fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        items(docs, key = { it.id }) { d ->
                            val active = docA?.id == d.id
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                modifier = Modifier.clickable { docA = d }
                            ) { Text(d.title, modifier = Modifier.padding(10.dp)) }
                        }
                    }
                }
            }

            when (tab) {
                "compare" -> {
                    item {
                        Text("Comparison PDF (B)", fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                            items(docs, key = { "b_${it.id}" }) { d ->
                                val active = docB?.id == d.id
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.clickable { docB = d }
                                ) { Text(d.title, modifier = Modifier.padding(10.dp)) }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Button(
                            enabled = docA != null && docB != null,
                            onClick = {
                                scope.launch {
                                    runCatching {
                                        PdfEngine.comparePdfs(context, File(docA!!.filePath), File(docB!!.filePath))
                                    }.onSuccess { comparisonResult = it }
                                        .onFailure { viewModel.postStatus(it.localizedMessage ?: "Compare failed.") }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Compare, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Compare PDFs")
                        }
                    }
                    comparisonResult?.let { cmp ->
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Pages: ${cmp.pageCountA} vs ${cmp.pageCountB}", fontWeight = FontWeight.Bold)
                                    Text("Identical text pages: ${cmp.identicalPages}")
                                    if (cmp.differingPages.isEmpty()) {
                                        Text("All extracted page text matches.", color = FlowEmerald)
                                    } else {
                                        cmp.summaryNotes.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                                    }
                                }
                            }
                        }
                    }
                }

                "metadata" -> {
                    item {
                        metaInfo?.let { info ->
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Pages: ${info.pageCount} • Size: ${PdfStorageManager.formatBytes(info.fileSizeBytes)}")
                                    Text("Creator: ${info.creator.ifBlank { "—" }} • Producer: ${info.producer.ifBlank { "—" }}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = editTitle, onValueChange = { editTitle = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = editAuthor, onValueChange = { editAuthor = it }, label = { Text("Author") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = editSubject, onValueChange = { editSubject = it }, label = { Text("Subject") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = editKeywords, onValueChange = { editKeywords = it }, label = { Text("Keywords") }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Button(
                            enabled = docA != null,
                            onClick = {
                                val doc = docA ?: return@Button
                                scope.launch {
                                    val out = viewModel.storageManager.createWorkspaceFile("Meta_${doc.fileName}")
                                    PdfEngine.updateMetadata(context, File(doc.filePath), editTitle, editAuthor, editSubject, editKeywords, out)
                                    viewModel.registerGeneratedPdf(out, editTitle.ifBlank { doc.title }, doc.folderName)
                                    saveMime = "application/pdf"
                                    saveFile = out
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Save Updated Metadata PDF") }
                    }
                }

                "images" -> {
                    item {
                        Button(
                            enabled = docA != null,
                            onClick = {
                                val doc = docA ?: return@Button
                                scope.launch {
                                    val dir = File(context.cacheDir, "pdf_temp/extract_${System.currentTimeMillis()}")
                                    extractedImages = runCatching { PdfEngine.extractImages(context, File(doc.filePath), dir) }.getOrDefault(emptyList())
                                    if (extractedImages.isEmpty()) viewModel.postStatus("No embedded bitmap images found in this PDF.")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Extract Embedded Images")
                        }
                    }
                    items(extractedImages) { imgFile ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${imgFile.name} (${PdfStorageManager.formatBytes(imgFile.length())})", modifier = Modifier.weight(1f))
                                IconButton(onClick = { saveMime = "image/png"; saveFile = imgFile }) {
                                    Icon(Icons.Default.Download, contentDescription = strings.saveAndDownload)
                                }
                            }
                        }
                    }
                }

                "attachments" -> {
                    item {
                        if (attachments.isEmpty()) {
                            Text("No embedded file attachments found in this PDF.")
                        } else {
                            attachments.forEach { (name, size) ->
                                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(name, fontWeight = FontWeight.SemiBold)
                                        Text(PdfStorageManager.formatBytes(size))
                                    }
                                }
                            }
                        }
                    }
                }

                "repair" -> {
                    item {
                        Button(
                            enabled = docA != null,
                            onClick = {
                                val doc = docA ?: return@Button
                                scope.launch {
                                    runCatching {
                                        val out = viewModel.storageManager.createWorkspaceFile("Repaired_${doc.fileName}")
                                        PdfEngine.repairPdf(context, File(doc.filePath), out)
                                        viewModel.registerGeneratedPdf(out, "${doc.title} Repaired", doc.folderName)
                                        out
                                    }.onSuccess {
                                        saveMime = "application/pdf"
                                        saveFile = it
                                        viewModel.postStatus("PDF structure rebuilt cleanly.")
                                    }.onFailure { viewModel.postStatus(it.localizedMessage ?: "PDF could not be repaired.") }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Build, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Rebuild & Repair PDF")
                        }
                    }
                }

                "batch" -> {
                    item {
                        Text("Select PDFs for Batch Processing (${batchSelected.size} selected)", fontWeight = FontWeight.Bold)
                    }
                    items(docs, key = { "batch_${it.id}" }) { doc ->
                        val checked = doc.id in batchSelected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { if (checked) batchSelected.remove(doc.id) else batchSelected.add(doc.id) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = checked, onCheckedChange = { if (checked) batchSelected.remove(doc.id) else batchSelected.add(doc.id) })
                            Text(doc.title, modifier = Modifier.weight(1f))
                            Text(PdfStorageManager.formatBytes(doc.sizeBytes), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = batchWatermark,
                            onValueChange = { batchWatermark = it },
                            label = { Text("Batch Watermark Text") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                enabled = batchSelected.isNotEmpty(),
                                onClick = {
                                    scope.launch {
                                        var count = 0
                                        docs.filter { it.id in batchSelected }.forEach { doc ->
                                            val out = viewModel.storageManager.createWorkspaceFile("BatchComp_${doc.fileName}")
                                            runCatching {
                                                PdfEngine.compressPdf(context, File(doc.filePath), CompressionLevel.MEDIUM, 62, out)
                                                viewModel.registerGeneratedPdf(out, "${doc.title} Batch Compressed", doc.folderName)
                                                count++
                                            }
                                        }
                                        viewModel.postStatus("Batch compressed $count PDF(s).")
                                        batchSelected.clear()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Batch Compress") }
                            FilledTonalButton(
                                enabled = batchSelected.isNotEmpty() && batchWatermark.isNotBlank(),
                                onClick = {
                                    scope.launch {
                                        var count = 0
                                        docs.filter { it.id in batchSelected }.forEach { doc ->
                                            val out = viewModel.storageManager.createWorkspaceFile("BatchWM_${doc.fileName}")
                                            runCatching {
                                                PdfEditingEngine.applyEdits(context, File(doc.filePath), out, watermarkText = batchWatermark)
                                                viewModel.registerGeneratedPdf(out, "${doc.title} Watermarked", doc.folderName)
                                                count++
                                            }
                                        }
                                        viewModel.postStatus("Added watermark to $count PDF(s).")
                                        batchSelected.clear()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Batch Watermark") }
                        }
                    }
                }
            }
        }
    }

    saveFile?.let { file ->
        SaveAndDownloadDialog(
            sourceFile = file,
            defaultFileName = file.name,
            mimeType = saveMime,
            storageManager = viewModel.storageManager,
            saveToDownloadsAction = { src, name, mime, overwrite ->
                viewModel.saveFileToPublicDownloads(src, name, mime, overwrite)
            },
            onDismiss = { saveFile = null }
        )
    }
}
