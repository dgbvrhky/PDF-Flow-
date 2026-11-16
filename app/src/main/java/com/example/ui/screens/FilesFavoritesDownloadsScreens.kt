package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.DownloadTaskEntity
import com.example.data.local.LocalDocumentEntity
import com.example.data.storage.PdfStorageManager
import com.example.localization.LocalAppStrings
import com.example.pdf.PdfConverterEngine
import com.example.pdf.PdfEngine
import com.example.ui.components.PdfDocumentCard
import com.example.ui.components.SaveAndDownloadDialog
import com.example.ui.navigation.Routes
import com.example.ui.theme.FlowAmber
import com.example.ui.theme.FlowCrimson
import com.example.ui.theme.FlowEmerald
import com.example.ui.theme.FlowIndigo
import com.example.viewmodel.FileSortOrder
import com.example.viewmodel.PdfFlowViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FilesScreen(
    viewModel: PdfFlowViewModel,
    onNavigate: (String) -> Unit
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val activeDocs by viewModel.activeDocuments.collectAsStateWithLifecycle()
    val trashedDocs by viewModel.trashedDocuments.collectAsStateWithLifecycle()
    val folders by viewModel.customFolders.collectAsStateWithLifecycle()

    var sectionTab by remember { mutableStateOf("all") } // all, folders, trash
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(FileSortOrder.DATE_DESC) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var isGridView by remember { mutableStateOf(false) }

    var multiSelectMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<String>() }

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    var actionDoc by remember { mutableStateOf<LocalDocumentEntity?>(null) }
    var renameDoc by remember { mutableStateOf<LocalDocumentEntity?>(null) }
    var renameText by remember { mutableStateOf("") }
    var moveDoc by remember { mutableStateOf<LocalDocumentEntity?>(null) }
    var infoDoc by remember { mutableStateOf<LocalDocumentEntity?>(null) }
    var saveDialogDoc by remember { mutableStateOf<LocalDocumentEntity?>(null) }

    val importPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importDocumentUri(uri, selectedFolder ?: "Root")
        }
    }

    val importImagesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 25)
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                runCatching {
                    val out = viewModel.storageManager.createWorkspaceFile("Images_${System.currentTimeMillis()}.pdf")
                    PdfConverterEngine.imagesToPdf(context, uris, out)
                    viewModel.registerGeneratedPdf(out, out.nameWithoutExtension, selectedFolder ?: "Root")
                }.onSuccess {
                    viewModel.postStatus("Created ${it.fileName} from ${uris.size} image(s)")
                }.onFailure {
                    viewModel.postStatus(it.localizedMessage ?: "Failed to import images")
                }
            }
        }
    }

    val displayedDocs = remember(activeDocs, trashedDocs, sectionTab, selectedFolder, searchQuery, sortOrder) {
        val base = when (sectionTab) {
            "trash" -> trashedDocs
            "folders" -> if (selectedFolder == null) activeDocs else activeDocs.filter { it.folderName == selectedFolder }
            else -> activeDocs
        }
        val filtered = if (searchQuery.isBlank()) base else base.filter {
            it.title.contains(searchQuery, true) || it.fileName.contains(searchQuery, true)
        }
        when (sortOrder) {
            FileSortOrder.DATE_DESC -> filtered.sortedByDescending { it.updatedAt }
            FileSortOrder.NAME_ASC -> filtered.sortedBy { it.title.lowercase() }
            FileSortOrder.SIZE_DESC -> filtered.sortedByDescending { it.sizeBytes }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize()
        ) {
            // Top Controls
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(strings.navFiles, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = { isGridView = !isGridView }, modifier = Modifier.testTag("files_toggle_grid")) {
                            Icon(if (isGridView) Icons.Default.ViewList else Icons.Default.GridView, contentDescription = "Toggle View")
                        }
                        Box {
                            IconButton(onClick = { sortMenuExpanded = true }, modifier = Modifier.testTag("files_sort_button")) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                            }
                            DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text(strings.sortByDate) },
                                    onClick = { sortOrder = FileSortOrder.DATE_DESC; sortMenuExpanded = false }
                                )
                                DropdownMenuItem(
                                    text = { Text(strings.sortByName) },
                                    onClick = { sortOrder = FileSortOrder.NAME_ASC; sortMenuExpanded = false }
                                )
                                DropdownMenuItem(
                                    text = { Text(strings.sortBySize) },
                                    onClick = { sortOrder = FileSortOrder.SIZE_DESC; sortMenuExpanded = false }
                                )
                            }
                        }
                        IconButton(onClick = {
                            multiSelectMode = !multiSelectMode
                            selectedIds.clear()
                        }, modifier = Modifier.testTag("files_multiselect_button")) {
                            Icon(
                                if (multiSelectMode) Icons.Default.Close else Icons.Default.CheckCircle,
                                contentDescription = "Multi-select"
                            )
                        }
                    }
                }

                // Import & Download shortcuts
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilledTonalButton(
                            onClick = { importPdfLauncher.launch(arrayOf("application/pdf")) },
                            modifier = Modifier.testTag("files_import_pdf_button")
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.browseDeviceStorage)
                        }
                    }
                    item {
                        OutlinedButton(
                            onClick = {
                                importImagesLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Photos -> PDF")
                        }
                    }
                    item {
                        OutlinedButton(onClick = { onNavigate(Routes.DOWNLOADS) }) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.downloadsFolder)
                        }
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(strings.searchPlaceholder) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().testTag("files_search_input")
                )

                // Section Chips
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val tabs = listOf(
                        "all" to strings.allFiles,
                        "folders" to strings.customFolders,
                        "trash" to strings.recentlyDeleted
                    )
                    items(tabs) { (key, label) ->
                        val active = sectionTab == key
                        Surface(
                            shape = CircleShape,
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable {
                                sectionTab = key
                                if (key != "folders") selectedFolder = null
                            }
                        ) {
                            Text(
                                text = label,
                                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                // Multi-select Batch Bar
                if (multiSelectMode && selectedIds.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${selectedIds.size} selected", fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (selectedIds.size >= 2) {
                                    TextButton(onClick = {
                                        val selectedFiles = activeDocs.filter { it.id in selectedIds }.map { File(it.filePath) }
                                        scope.launch {
                                            runCatching {
                                                val out = viewModel.storageManager.createWorkspaceFile("Merged_${System.currentTimeMillis()}.pdf")
                                                PdfEngine.mergePdfs(context, selectedFiles, out)
                                                viewModel.registerGeneratedPdf(out, out.nameWithoutExtension)
                                            }.onSuccess {
                                                multiSelectMode = false
                                                selectedIds.clear()
                                                viewModel.postStatus("Merged into ${it.fileName}")
                                            }.onFailure {
                                                viewModel.postStatus(it.localizedMessage ?: "Merge failed")
                                            }
                                        }
                                    }) {
                                        Icon(Icons.Default.MergeType, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Merge")
                                    }
                                }
                                TextButton(onClick = {
                                    val docsToBackup = activeDocs.filter { it.id in selectedIds }
                                    viewModel.runManualBackup(docsToBackup, true, "Selected Docs Backup")
                                    multiSelectMode = false
                                    selectedIds.clear()
                                }) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Backup")
                                }
                                TextButton(onClick = {
                                    selectedIds.forEach { viewModel.moveToTrash(it) }
                                    multiSelectMode = false
                                    selectedIds.clear()
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Trash", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            // Content List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (sectionTab == "folders") {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                                item {
                                    FolderChip("Root", selectedFolder == null || selectedFolder == "Root") {
                                        selectedFolder = "Root"
                                    }
                                }
                                items(folders, key = { it.name }) { f ->
                                    FolderChip(f.name, selectedFolder == f.name) { selectedFolder = f.name }
                                }
                            }
                            IconButton(onClick = { showCreateFolderDialog = true }) {
                                Icon(Icons.Default.CreateNewFolder, contentDescription = "Create Folder")
                            }
                        }
                    }
                }

                if (sectionTab == "trash" && trashedDocs.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { viewModel.emptyTrash() }) {
                                Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(6.dp))
                                Text("Empty Trash", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                if (displayedDocs.isEmpty()) {
                    item {
                        EmptyDocumentsHeroCard(
                            title = if (sectionTab == "trash") "Trash is empty" else strings.emptyFilesTitle,
                            subtitle = if (sectionTab == "trash") "Deleted PDFs appear here before permanent removal." else strings.emptyFilesSubtitle,
                            onImportClick = { importPdfLauncher.launch(arrayOf("application/pdf")) },
                            onCreateClick = { onNavigate(Routes.converter("create")) }
                        )
                    }
                } else if (isGridView) {
                    items(displayedDocs.chunked(2)) { pair ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            pair.forEach { doc ->
                                val selected = doc.id in selectedIds
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .border(
                                            width = if (selected) 2.dp else 0.dp,
                                            color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            shape = MaterialTheme.shapes.medium
                                        )
                                        .clickable {
                                            if (multiSelectMode) {
                                                if (selected) selectedIds.remove(doc.id) else selectedIds.add(doc.id)
                                            } else if (sectionTab == "trash") {
                                                actionDoc = doc
                                            } else {
                                                viewModel.markOpened(doc.id)
                                                onNavigate(Routes.reader(doc.id))
                                            }
                                        },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = FlowCrimson, modifier = Modifier.size(30.dp))
                                            IconButton(onClick = { actionDoc = doc }, modifier = Modifier.size(28.dp)) {
                                                Icon(Icons.Default.Info, contentDescription = "Options", modifier = Modifier.size(18.dp))
                                            }
                                        }
                                        Text(doc.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "${doc.pageCount}p • ${PdfStorageManager.formatBytes(doc.sizeBytes)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                } else {
                    items(displayedDocs, key = { it.id }) { doc ->
                        val selected = doc.id in selectedIds
                        Box(
                            modifier = Modifier.border(
                                width = if (selected) 2.dp else 0.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = MaterialTheme.shapes.medium
                            )
                        ) {
                            PdfDocumentCard(
                                doc = doc,
                                onOpen = {
                                    if (multiSelectMode) {
                                        if (selected) selectedIds.remove(doc.id) else selectedIds.add(doc.id)
                                    } else if (sectionTab == "trash") {
                                        actionDoc = doc
                                    } else {
                                        viewModel.markOpened(doc.id)
                                        onNavigate(Routes.reader(doc.id))
                                    }
                                },
                                onToggleFavorite = { viewModel.toggleFavorite(doc) },
                                onMoreClick = { actionDoc = doc }
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("Create Folder") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.createFolder(newFolderName)
                    selectedFolder = newFolderName.trim().ifBlank { null }
                    newFolderName = ""
                    showCreateFolderDialog = false
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) { Text(strings.cancelAction) }
            }
        )
    }

    actionDoc?.let { doc ->
        AlertDialog(
            onDismissRequest = { actionDoc = null },
            title = { Text(doc.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (doc.isTrashed) {
                        FileMenuRow(Icons.Default.RestoreFromTrash, "Restore Document") {
                            viewModel.restoreFromTrash(doc.id)
                            actionDoc = null
                        }
                        FileMenuRow(Icons.Default.DeleteForever, "Delete Permanently", MaterialTheme.colorScheme.error) {
                            viewModel.deletePermanently(doc)
                            actionDoc = null
                        }
                    } else {
                        FileMenuRow(Icons.Default.FileOpen, "Open in Reader") {
                            actionDoc = null
                            onNavigate(Routes.reader(doc.id))
                        }
                        FileMenuRow(Icons.Default.Edit, "Edit PDF") {
                            actionDoc = null
                            onNavigate(Routes.editor(doc.id))
                        }
                        FileMenuRow(Icons.Default.Download, strings.saveAndDownload) {
                            actionDoc = null
                            saveDialogDoc = doc
                        }
                        FileMenuRow(Icons.Default.Share, strings.shareAction) {
                            actionDoc = null
                            viewModel.storageManager.shareFile(File(doc.filePath))
                        }
                        FileMenuRow(Icons.Default.Edit, "Rename") {
                            renameText = doc.title
                            renameDoc = doc
                            actionDoc = null
                        }
                        FileMenuRow(Icons.Default.ContentCopy, "Copy / Duplicate") {
                            viewModel.duplicateDocument(doc)
                            actionDoc = null
                        }
                        FileMenuRow(Icons.Default.DriveFileMove, "Move to Folder") {
                            moveDoc = doc
                            actionDoc = null
                        }
                        FileMenuRow(Icons.Default.Info, "File Info") {
                            infoDoc = doc
                            actionDoc = null
                        }
                        FileMenuRow(Icons.Default.Delete, "Move to Trash", MaterialTheme.colorScheme.error) {
                            viewModel.moveToTrash(doc.id)
                            actionDoc = null
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { actionDoc = null }) { Text("Close") } }
        )
    }

    renameDoc?.let { doc ->
        AlertDialog(
            onDismissRequest = { renameDoc = null },
            title = { Text("Rename PDF") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("New Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (renameText.isNotBlank()) viewModel.renameDocument(doc, renameText)
                    renameDoc = null
                }) { Text(strings.saveAction) }
            },
            dismissButton = { TextButton(onClick = { renameDoc = null }) { Text(strings.cancelAction) } }
        )
    }

    moveDoc?.let { doc ->
        val folderChoices = listOf("Root") + folders.map { it.name }
        AlertDialog(
            onDismissRequest = { moveDoc = null },
            title = { Text("Move to Folder") },
            text = {
                Column {
                    folderChoices.distinct().forEach { folder ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.moveDocumentToFolder(doc.id, folder)
                                    moveDoc = null
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = FlowAmber)
                            Spacer(Modifier.width(10.dp))
                            Text(folder)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { moveDoc = null }) { Text(strings.cancelAction) } }
        )
    }

    infoDoc?.let { doc ->
        val date = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(doc.updatedAt))
        AlertDialog(
            onDismissRequest = { infoDoc = null },
            title = { Text("Document Details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Title: ${doc.title}")
                    Text("File: ${doc.fileName}")
                    Text("Pages: ${doc.pageCount}")
                    Text("Size: ${PdfStorageManager.formatBytes(doc.sizeBytes)}")
                    Text("Folder: ${doc.folderName}")
                    Text("Encrypted: ${if (doc.isEncrypted) "Yes" else "No"}")
                    Text("Cloud Synced: ${if (doc.isSyncedToCloud) "Yes" else "No"}")
                    Text("Modified: $date")
                }
            },
            confirmButton = { TextButton(onClick = { infoDoc = null }) { Text("OK") } }
        )
    }

    saveDialogDoc?.let { doc ->
        SaveAndDownloadDialog(
            sourceFile = File(doc.filePath),
            defaultFileName = doc.fileName,
            storageManager = viewModel.storageManager,
            saveToDownloadsAction = { src, name, mime, overwrite ->
                viewModel.saveFileToPublicDownloads(src, name, mime, overwrite)
            },
            onDismiss = { saveDialogDoc = null }
        )
    }
}

@Composable
private fun FolderChip(label: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Folder, contentDescription = null, tint = FlowAmber, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun FileMenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = if (tint == MaterialTheme.colorScheme.error) tint else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun FavoritesScreen(
    viewModel: PdfFlowViewModel,
    onNavigate: (String) -> Unit
) {
    val strings = LocalAppStrings.current
    val favorites by viewModel.favoriteDocuments.collectAsStateWithLifecycle()
    var saveDoc by remember { mutableStateOf<LocalDocumentEntity?>(null) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(strings.navFavorites, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
            if (favorites.isEmpty()) {
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = FlowAmber, modifier = Modifier.size(48.dp))
                            Text(strings.emptyFavoritesTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                strings.emptyFavoritesSubtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(favorites, key = { it.id }) { doc ->
                    PdfDocumentCard(
                        doc = doc,
                        onOpen = {
                            viewModel.markOpened(doc.id)
                            onNavigate(Routes.reader(doc.id))
                        },
                        onToggleFavorite = { viewModel.toggleFavorite(doc) },
                        onMoreClick = { saveDoc = doc }
                    )
                }
            }
        }
    }

    saveDoc?.let { doc ->
        SaveAndDownloadDialog(
            sourceFile = File(doc.filePath),
            defaultFileName = doc.fileName,
            storageManager = viewModel.storageManager,
            saveToDownloadsAction = { src, name, mime, overwrite ->
                viewModel.saveFileToPublicDownloads(src, name, mime, overwrite)
            },
            onDismiss = { saveDoc = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadManagerScreen(
    viewModel: PdfFlowViewModel,
    onBack: () -> Unit
) {
    val strings = LocalAppStrings.current
    val tasks by viewModel.downloadTasks.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf("ALL") }

    val filtered = remember(tasks, filter) {
        when (filter) {
            "ACTIVE" -> tasks.filter { it.status == "ACTIVE" }
            "COMPLETED" -> tasks.filter { it.status == "COMPLETED" }
            "FAILED" -> tasks.filter { it.status == "FAILED" }
            else -> tasks
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.toolDownloadManager) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.backAction)
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 800.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Default Device Location", fontWeight = FontWeight.Bold)
                            Text("Downloads/${prefs.downloadSubfolder}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val chips = listOf(
                            "ALL" to "All (${tasks.size})",
                            "ACTIVE" to strings.activeDownloads,
                            "COMPLETED" to strings.completedDownloads,
                            "FAILED" to strings.failedDownloads
                        )
                        items(chips) { (key, label) ->
                            val active = filter == key
                            Surface(
                                shape = CircleShape,
                                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable { filter = key }
                            ) {
                                Text(
                                    text = label,
                                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

                if (filtered.isEmpty()) {
                    item {
                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, tint = FlowIndigo, modifier = Modifier.size(44.dp))
                                Text("No downloads yet", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Use Save & Download inside any PDF tool to save files directly into your Android Downloads folder.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(filtered, key = { it.id }) { task ->
                        DownloadTaskCard(
                            task = task,
                            onOpen = {
                                if (task.savedUriString.isNotBlank()) {
                                    viewModel.storageManager.openExternalUri(Uri.parse(task.savedUriString))
                                }
                            },
                            onShare = {
                                if (task.savedUriString.isNotBlank()) {
                                    viewModel.storageManager.shareUri(Uri.parse(task.savedUriString))
                                }
                            },
                            onRetry = { viewModel.retryDownload(task) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadTaskCard(
    task: DownloadTaskEntity,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRetry: () -> Unit
) {
    val strings = LocalAppStrings.current
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(task.fileName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Surface(
                    shape = CircleShape,
                    color = when (task.status) {
                        "COMPLETED" -> FlowEmerald.copy(alpha = 0.16f)
                        "FAILED" -> MaterialTheme.colorScheme.errorContainer
                        else -> FlowIndigo.copy(alpha = 0.16f)
                    }
                ) {
                    Text(
                        text = task.status,
                        style = MaterialTheme.typography.labelSmall,
                        color = when (task.status) {
                            "COMPLETED" -> FlowEmerald
                            "FAILED" -> MaterialTheme.colorScheme.error
                            else -> FlowIndigo
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            Text(
                "${PdfStorageManager.formatBytes(task.sizeBytes)} • ${task.destinationLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (task.status == "ACTIVE") {
                LinearProgressIndicator(
                    progress = { (task.progressPercent / 100f).coerceIn(0.05f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            task.errorMessage?.let { err ->
                Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (task.status == "COMPLETED" && task.savedUriString.isNotBlank()) {
                    FilledTonalButton(onClick = onOpen) { Text(strings.openAction) }
                    OutlinedButton(onClick = onShare) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.shareAction)
                    }
                } else if (task.status == "FAILED") {
                    Button(onClick = onRetry) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.retryAction)
                    }
                }
            }
        }
    }
}
