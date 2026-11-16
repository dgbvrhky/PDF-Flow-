package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.localization.LocalAppStrings
import com.example.pdf.PdfBookmarkItem
import com.example.pdf.PdfEngine
import com.example.pdf.PdfSearchHit
import com.example.ui.components.SaveAndDownloadDialog
import com.example.ui.navigation.Routes
import com.example.viewmodel.PdfFlowViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(
    docId: String,
    viewModel: PdfFlowViewModel,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val scope = rememberCoroutineScope()
    val docs by viewModel.activeDocuments.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val doc = remember(docs, docId) { docs.firstOrNull { it.id == docId } }

    var viewerMode by remember(prefs.defaultViewerMode) { mutableStateOf(prefs.defaultViewerMode) } // continuous, single, horizontal
    var fitMode by remember(prefs.defaultPageLayout) { mutableStateOf(prefs.defaultPageLayout) } // fit_width, fit_page
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var isFullscreen by remember { mutableStateOf(false) }
    var nightReading by remember { mutableStateOf(false) }
    var currentPage by remember { mutableIntStateOf(0) }
    var pageCount by remember { mutableIntStateOf(0) }
    var password by remember { mutableStateOf<String?>(null) }
    var needsPassword by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var loadError by remember { mutableStateOf<String?>(null) }

    val pageCache = remember { mutableStateMapOf<Int, Bitmap>() }
    var showThumbnails by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchHits by remember { mutableStateOf<List<PdfSearchHit>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    var showTextCopyDialog by remember { mutableStateOf(false) }
    var extractedPageText by remember { mutableStateOf("") }
    var showBookmarksDialog by remember { mutableStateOf(false) }
    var bookmarks by remember { mutableStateOf<List<PdfBookmarkItem>>(emptyList()) }
    var newBookmarkTitle by remember { mutableStateOf("") }
    var showSaveDialog by remember { mutableStateOf(false) }

    val pdfFile = remember(doc) { doc?.let { File(it.filePath) } }

    LaunchedEffect(pdfFile, password) {
        if (pdfFile != null && pdfFile.exists()) {
            pageCache.clear()
            runCatching {
                PdfEngine.getPageCount(context, pdfFile, password)
            }.onSuccess { count ->
                pageCount = count
                needsPassword = false
                loadError = null
            }.onFailure { err ->
                if (err.message?.contains("password", true) == true || doc?.isEncrypted == true) {
                    needsPassword = true
                } else {
                    loadError = err.localizedMessage ?: "Unable to open PDF."
                }
            }
        }
    }

    val nightFilter = remember(nightReading) {
        if (!nightReading) null
        else ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    -0.88f, 0f, 0f, 0f, 235f,
                    0f, -0.88f, 0f, 0f, 230f,
                    0f, 0f, -0.88f, 0f, 220f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }

    if (doc == null || pdfFile == null || !pdfFile.exists()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("PDF Reader") },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.backAction) }
                    }
                )
            }
        ) { p ->
            Box(Modifier.fillMaxSize().padding(p), contentAlignment = Alignment.Center) {
                Text("Document file not found on device.")
            }
        }
        return
    }

    Scaffold(
        topBar = {
            if (!isFullscreen) {
                TopAppBar(
                    title = {
                        Column {
                            Text(doc.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (pageCount > 0) {
                                Text(
                                    "Page ${currentPage + 1} / $pageCount • ${(zoomScale * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.backAction)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSearchDialog = true }, modifier = Modifier.testTag("reader_search_button")) {
                            Icon(Icons.Default.Search, contentDescription = "Search inside PDF")
                        }
                        IconButton(onClick = {
                            scope.launch {
                                val pages = runCatching { PdfEngine.extractAllText(context, pdfFile, password) }.getOrDefault(emptyList())
                                extractedPageText = pages.getOrNull(currentPage)?.ifBlank { "No selectable text on this page. Use OCR for scanned PDFs." }
                                    ?: "No text extracted."
                                showTextCopyDialog = true
                            }
                        }, modifier = Modifier.testTag("reader_copy_text_button")) {
                            Icon(Icons.Default.TextFields, contentDescription = "Select & Copy Text")
                        }
                        IconButton(onClick = {
                            scope.launch {
                                bookmarks = runCatching { PdfEngine.getBookmarks(context, pdfFile) }.getOrDefault(emptyList())
                                showBookmarksDialog = true
                            }
                        }) {
                            Icon(Icons.Default.Bookmark, contentDescription = "Bookmarks")
                        }
                        IconButton(onClick = { showSaveDialog = true }, modifier = Modifier.testTag("reader_download_button")) {
                            Icon(Icons.Default.Download, contentDescription = strings.saveAndDownload)
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (!isFullscreen) {
                Surface(tonalElevation = 4.dp) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item {
                            IconButton(onClick = {
                                viewerMode = when (viewerMode) {
                                    "continuous" -> "single"
                                    "single" -> "horizontal"
                                    else -> "continuous"
                                }
                            }) {
                                Icon(
                                    when (viewerMode) {
                                        "horizontal" -> Icons.Default.ViewCarousel
                                        "single" -> Icons.Default.ViewDay
                                        else -> Icons.Default.GridView
                                    },
                                    contentDescription = "Scroll Mode: $viewerMode"
                                )
                            }
                        }
                        item {
                            IconButton(onClick = { zoomScale = (zoomScale - 0.25f).coerceAtLeast(0.75f) }) {
                                Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out")
                            }
                        }
                        item {
                            IconButton(onClick = { zoomScale = (zoomScale + 0.25f).coerceAtMost(3.0f) }) {
                                Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In")
                            }
                        }
                        item {
                            IconButton(onClick = {
                                fitMode = if (fitMode == "fit_width") "fit_page" else "fit_width"
                                zoomScale = 1f
                            }) {
                                Icon(Icons.Default.FitScreen, contentDescription = "Fit Mode")
                            }
                        }
                        item {
                            IconButton(onClick = { nightReading = !nightReading }) {
                                Icon(
                                    Icons.Default.DarkMode,
                                    contentDescription = "Night Reading Mode",
                                    tint = if (nightReading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        item {
                            IconButton(onClick = { showThumbnails = !showThumbnails }) {
                                Icon(Icons.Default.GridView, contentDescription = "Page Thumbnails")
                            }
                        }
                        item {
                            IconButton(onClick = { isFullscreen = true }) {
                                Icon(Icons.Default.Fullscreen, contentDescription = "Fullscreen")
                            }
                        }
                        item {
                            IconButton(onClick = { onNavigate(Routes.editor(doc.id)) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Annotate & Edit PDF")
                            }
                        }
                        item {
                            IconButton(onClick = { printPdfFile(context, pdfFile, doc.fileName) }) {
                                Icon(Icons.Default.Print, contentDescription = "Print PDF")
                            }
                        }
                        item {
                            IconButton(onClick = { viewModel.storageManager.shareFile(pdfFile) }) {
                                Icon(Icons.Default.Share, contentDescription = strings.shareAction)
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(if (nightReading) Color(0xFF0A0C10) else MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (zoom != 1f) {
                            zoomScale = (zoomScale * zoom).coerceIn(0.75f, 3.0f)
                        }
                    }
                }
        ) {
            when {
                needsPassword -> {
                    Card(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("Protected PDF", style = MaterialTheme.typography.titleLarge)
                            }
                            Text("Enter the PDF password to view its pages.")
                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = { passwordInput = it },
                                label = { Text("Password") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = { password = passwordInput },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Unlock & Read")
                            }
                        }
                    }
                }
                loadError != null -> {
                    Text(
                        text = loadError!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp)
                    )
                }
                pageCount == 0 -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                viewerMode == "horizontal" -> {
                    val pagerState = rememberPagerState(initialPage = currentPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))) { pageCount }
                    LaunchedEffect(pagerState.currentPage) { currentPage = pagerState.currentPage }
                    LaunchedEffect(currentPage) {
                        if (pagerState.currentPage != currentPage) pagerState.scrollToPage(currentPage)
                    }
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { idx ->
                        PdfPageBitmapView(
                            pdfFile = pdfFile,
                            pageIndex = idx,
                            password = password,
                            cached = pageCache[idx],
                            onRendered = { pageCache[idx] = it },
                            zoomScale = zoomScale,
                            fitPage = fitMode == "fit_page",
                            colorFilter = nightFilter
                        )
                    }
                }
                viewerMode == "single" -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        PdfPageBitmapView(
                            pdfFile = pdfFile,
                            pageIndex = currentPage,
                            password = password,
                            cached = pageCache[currentPage],
                            onRendered = { pageCache[currentPage] = it },
                            zoomScale = zoomScale,
                            fitPage = fitMode == "fit_page",
                            colorFilter = nightFilter
                        )
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            FilledTonalIconButton(
                                enabled = currentPage > 0,
                                onClick = { currentPage-- }
                            ) { Icon(Icons.Default.ChevronLeft, contentDescription = "Previous page") }
                            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)) {
                                Text("${currentPage + 1} / $pageCount", modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                            }
                            FilledTonalIconButton(
                                enabled = currentPage < pageCount - 1,
                                onClick = { currentPage++ }
                            ) { Icon(Icons.Default.ChevronRight, contentDescription = "Next page") }
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(pageCount) { idx ->
                            LaunchedEffect(idx) { currentPage = idx }
                            PdfPageBitmapView(
                                pdfFile = pdfFile,
                                pageIndex = idx,
                                password = password,
                                cached = pageCache[idx],
                                onRendered = { pageCache[idx] = it },
                                zoomScale = zoomScale,
                                fitPage = fitMode == "fit_page",
                                colorFilter = nightFilter
                            )
                        }
                    }
                }
            }

            // Thumbnail strip drawer
            if (showThumbnails && pageCount > 0) {
                Surface(
                    tonalElevation = 8.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                ) {
                    LazyRow(
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(pageCount) { idx ->
                            val isCurr = idx == currentPage
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(72.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        width = if (isCurr) 2.dp else 1.dp,
                                        color = if (isCurr) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        currentPage = idx
                                        if (viewerMode == "continuous") viewerMode = "single"
                                    }
                                    .padding(6.dp)
                            ) {
                                Text("P${idx + 1}", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            if (isFullscreen) {
                FloatingActionButton(
                    onClick = { isFullscreen = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Default.FullscreenExit, contentDescription = "Exit Fullscreen")
                }
            }
        }
    }

    if (showSearchDialog) {
        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            title = { Text("Search Inside PDF") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Keyword") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        enabled = searchQuery.isNotBlank() && !isSearching,
                        onClick = {
                            scope.launch {
                                isSearching = true
                                searchHits = runCatching { PdfEngine.searchInPdf(context, pdfFile, searchQuery, password) }.getOrDefault(emptyList())
                                isSearching = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isSearching) "Searching..." else "Search")
                    }
                    LazyColumn(modifier = Modifier.height(180.dp)) {
                        items(searchHits) { hit ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        currentPage = hit.pageIndex
                                        viewerMode = "single"
                                        showSearchDialog = false
                                    }
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("Page ${hit.pageIndex + 1}", fontWeight = FontWeight.Bold)
                                    Text(hit.snippet, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSearchDialog = false }) { Text("Close") } }
        )
    }

    if (showTextCopyDialog) {
        AlertDialog(
            onDismissRequest = { showTextCopyDialog = false },
            title = { Text("Page ${currentPage + 1} Text") },
            text = {
                Column(modifier = Modifier.height(260.dp).verticalScroll(rememberScrollState())) {
                    Text(extractedPageText, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("PDF Text", extractedPageText))
                    viewModel.postStatus("Copied page text to clipboard.")
                    showTextCopyDialog = false
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy All")
                }
            },
            dismissButton = { TextButton(onClick = { showTextCopyDialog = false }) { Text("Close") } }
        )
    }

    if (showBookmarksDialog) {
        AlertDialog(
            onDismissRequest = { showBookmarksDialog = false },
            title = { Text("Bookmarks") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = newBookmarkTitle,
                            onValueChange = { newBookmarkTitle = it },
                            label = { Text("Bookmark Page ${currentPage + 1}") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            if (newBookmarkTitle.isNotBlank()) {
                                scope.launch {
                                    val out = viewModel.storageManager.createWorkspaceFile("${doc.title}_bookmarked.pdf")
                                    runCatching {
                                        PdfEngine.addBookmark(context, pdfFile, newBookmarkTitle, currentPage, out)
                                        viewModel.registerGeneratedPdf(out, "${doc.title} Bookmarked", doc.folderName)
                                        bookmarks = PdfEngine.getBookmarks(context, out)
                                    }
                                    newBookmarkTitle = ""
                                }
                            }
                        }) {
                            Icon(Icons.Default.BookmarkAdd, contentDescription = "Add Bookmark")
                        }
                    }
                    if (bookmarks.isEmpty()) {
                        Text("No outline bookmarks in this PDF yet.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        LazyColumn(modifier = Modifier.height(180.dp)) {
                            itemsIndexed(bookmarks) { _, item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            currentPage = item.pageIndex
                                            viewerMode = "single"
                                            showBookmarksDialog = false
                                        }
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text("P${item.pageIndex + 1}", color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showBookmarksDialog = false }) { Text("Close") } }
        )
    }

    if (showSaveDialog) {
        SaveAndDownloadDialog(
            sourceFile = pdfFile,
            defaultFileName = doc.fileName,
            storageManager = viewModel.storageManager,
            saveToDownloadsAction = { src, name, mime, overwrite ->
                viewModel.saveFileToPublicDownloads(src, name, mime, overwrite)
            },
            onDismiss = { showSaveDialog = false }
        )
    }
}

@Composable
private fun PdfPageBitmapView(
    pdfFile: File,
    pageIndex: Int,
    password: String?,
    cached: Bitmap?,
    onRendered: (Bitmap) -> Unit,
    zoomScale: Float,
    fitPage: Boolean,
    colorFilter: ColorFilter?
) {
    val context = LocalContext.current
    var bitmap by remember(pdfFile, pageIndex, cached) { mutableStateOf(cached) }

    LaunchedEffect(pdfFile, pageIndex, password) {
        if (bitmap == null) {
            runCatching { PdfEngine.renderPageBitmap(context, pdfFile, pageIndex, 1080, password) }
                .getOrNull()?.let { bmp ->
                    bitmap = bmp
                    onRendered(bmp)
                }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (fitPage) Modifier.aspectRatio(0.71f) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .background(Color.White, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "PDF Page ${pageIndex + 1}",
                contentScale = if (fitPage) ContentScale.Fit else ContentScale.FillWidth,
                colorFilter = colorFilter,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer(scaleX = zoomScale, scaleY = zoomScale)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
            )
        }
    }
}

private fun printPdfFile(context: Context, file: File, jobName: String) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    val adapter = object : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes?,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback,
            extras: Bundle?
        ) {
            if (cancellationSignal?.isCanceled == true) {
                callback.onLayoutCancelled()
                return
            }
            val info = PrintDocumentInfo.Builder(jobName)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .build()
            callback.onLayoutFinished(info, true)
        }

        override fun onWrite(
            pages: Array<out PageRange>?,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback
        ) {
            try {
                FileInputStream(file).use { input ->
                    FileOutputStream(destination.fileDescriptor).use { output ->
                        input.copyTo(output)
                    }
                }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback.onWriteFailed(e.localizedMessage)
            }
        }
    }
    printManager.print(jobName, adapter, PrintAttributes.Builder().build())
}
