package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.LocalDocumentEntity
import com.example.data.storage.PdfStorageManager
import com.example.localization.LocalAppStrings
import com.example.ui.components.PdfDocumentCard
import com.example.ui.components.PdfFlowLogoImage
import com.example.ui.components.SaveAndDownloadDialog
import com.example.ui.navigation.Routes
import com.example.ui.navigation.ToolDescriptor
import com.example.ui.navigation.buildToolCatalog
import com.example.ui.theme.FlowAmber
import com.example.ui.theme.FlowCrimson
import com.example.ui.theme.FlowEmerald
import com.example.ui.theme.FlowIndigo
import com.example.ui.theme.FlowIndigoDeep
import com.example.ui.theme.FlowRoyalBlue
import com.example.ui.theme.FlowViolet
import com.example.viewmodel.PdfFlowViewModel
import java.io.File

@Composable
fun HomeDashboardScreen(
    viewModel: PdfFlowViewModel,
    userDisplayName: String?,
    isGuest: Boolean,
    onNavigate: (String) -> Unit,
    onSwitchTab: (String) -> Unit
) {
    val strings = LocalAppStrings.current
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val docs by viewModel.activeDocuments.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteDocuments.collectAsStateWithLifecycle()
    val catalog = remember(strings) { buildToolCatalog(strings) }

    var searchQuery by remember { mutableStateOf("") }
    var showCustomizeQuick by remember { mutableStateOf(false) }
    var actionDoc by remember { mutableStateOf<LocalDocumentEntity?>(null) }
    var saveDialogDoc by remember { mutableStateOf<LocalDocumentEntity?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importDocumentUri(uri) { imported ->
                onNavigate(Routes.reader(imported.id))
            }
        }
    }

    val quickTools = remember(catalog, prefs.quickAccessToolIds) {
        prefs.quickAccessToolIds.mapNotNull { id -> catalog.firstOrNull { it.id == id } }
            .ifEmpty { catalog.take(6) }
    }
    val recentTools = remember(catalog, prefs.recentToolIds) {
        prefs.recentToolIds.mapNotNull { id -> catalog.firstOrNull { it.id == id } }
    }
    val filteredDocs = remember(docs, searchQuery) {
        if (searchQuery.isBlank()) docs.take(6)
        else docs.filter {
            it.title.contains(searchQuery, true) || it.fileName.contains(searchQuery, true)
        }.take(10)
    }

    val usedBytes = remember(docs) { viewModel.storageManager.getWorkspaceStorageBytes() }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Welcome Banner
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(FlowIndigoDeep, FlowIndigo, FlowRoyalBlue)
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (userDisplayName.isNullOrBlank()) strings.welcomeMorning
                                        else "${strings.welcomeMorning}, $userDisplayName",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = strings.tagline,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.85f)
                                    )
                                }
                                PdfFlowLogoImage(size = 52.dp)
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { importLauncher.launch(arrayOf("application/pdf")) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = FlowIndigoDeep
                                    ),
                                    modifier = Modifier.testTag("home_import_pdf_button")
                                ) {
                                    Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(strings.toolImportPdf)
                                }
                                FilledTonalButton(
                                    onClick = {
                                        viewModel.recordToolUsage("scan")
                                        onNavigate(Routes.SCANNER)
                                    },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = Color.White.copy(alpha = 0.18f),
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier.testTag("home_scan_button")
                                ) {
                                    Text(strings.toolScanDoc)
                                }
                            }
                        }
                    }
                }
            }

            // Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(strings.searchPlaceholder) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_search_input")
                )
            }

            // Quick Access Section
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(strings.quickAccess, style = MaterialTheme.typography.titleLarge)
                        TextButton(
                            onClick = { showCustomizeQuick = true },
                            modifier = Modifier.testTag("customize_quick_access_button")
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.customizeQuickAccess)
                        }
                    }

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(quickTools, key = { it.id }) { tool ->
                            QuickToolTile(tool = tool) {
                                viewModel.recordToolUsage(tool.id)
                                onNavigate(tool.route)
                            }
                        }
                    }
                }
            }

            // Featured Tools Strip
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(strings.featuredTools, style = MaterialTheme.typography.titleLarge)
                        TextButton(onClick = { onSwitchTab(Routes.TOOLS) }) {
                            Text("All Tools")
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                    }
                    val featured = remember(catalog) {
                        listOf("create", "scan", "merge", "compress", "convert", "edit", "ai")
                            .mapNotNull { id -> catalog.firstOrNull { it.id == id } }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(featured, key = { "feat_${it.id}" }) { tool ->
                            FeaturedToolCard(tool = tool) {
                                viewModel.recordToolUsage(tool.id)
                                onNavigate(tool.route)
                            }
                        }
                    }
                }
            }

            // Recently Used Tools
            if (recentTools.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(strings.recentlyUsedTools, style = MaterialTheme.typography.titleMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(recentTools, key = { "rec_${it.id}" }) { tool ->
                                AssistChip(
                                    onClick = {
                                        viewModel.recordToolUsage(tool.id)
                                        onNavigate(tool.route)
                                    },
                                    label = { Text(tool.title) },
                                    leadingIcon = {
                                        Icon(tool.icon, contentDescription = null, tint = tool.color, modifier = Modifier.size(18.dp))
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Recent Documents
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(strings.recentDocuments, style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { onSwitchTab(Routes.FILES) }) {
                        Text(strings.allFiles)
                    }
                }
            }

            if (filteredDocs.isEmpty()) {
                item {
                    EmptyDocumentsHeroCard(
                        title = strings.emptyFilesTitle,
                        subtitle = strings.emptyFilesSubtitle,
                        onImportClick = { importLauncher.launch(arrayOf("application/pdf")) },
                        onCreateClick = { onNavigate(Routes.converter("create")) }
                    )
                }
            } else {
                items(filteredDocs, key = { it.id }) { doc ->
                    PdfDocumentCard(
                        doc = doc,
                        onOpen = {
                            viewModel.markOpened(doc.id)
                            onNavigate(Routes.reader(doc.id))
                        },
                        onToggleFavorite = { viewModel.toggleFavorite(doc) },
                        onMoreClick = { actionDoc = doc }
                    )
                }
            }

            // Favorites Strip
            if (favorites.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(strings.favoriteDocuments, style = MaterialTheme.typography.titleLarge)
                            TextButton(onClick = { onSwitchTab(Routes.FAVORITES) }) {
                                Text(strings.navFavorites)
                            }
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(favorites.take(6), key = { "fav_${it.id}" }) { doc ->
                                Card(
                                    modifier = Modifier
                                        .width(190.dp)
                                        .clickable {
                                            viewModel.markOpened(doc.id)
                                            onNavigate(Routes.reader(doc.id))
                                        },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Star, contentDescription = null, tint = FlowAmber, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(doc.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                        }
                                        Text(
                                            "${doc.pageCount}p • ${PdfStorageManager.formatBytes(doc.sizeBytes)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Storage Usage Card
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(strings.storageUsage, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${docs.size} PDFs • ${PdfStorageManager.formatBytes(usedBytes)}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        val quotaMb = if (prefs.planTier == "pro") 5000f else 500f
                        val ratio = ((usedBytes / (1024f * 1024f)) / quotaMb).coerceIn(0.03f, 1f)
                        LinearProgressIndicator(
                            progress = { ratio },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                if (isGuest) "Local device storage (Guest Mode)" else "Synced workspace ready",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(
                                onClick = {
                                    onNavigate(if (isGuest) Routes.GUEST_MIGRATION else Routes.BACKUP)
                                },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (isGuest) strings.migrateGuestData else strings.cloudBackup)
                            }
                        }
                    }
                }
            }

            // Premium Upgrade Card
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigate(Routes.PREMIUM) }
                        .testTag("home_premium_card")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(listOf(FlowCrimson, FlowViolet))
                            )
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = Color(0xFFFDE68A))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        strings.premiumCardTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    strings.premiumCardSubtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            FilledTonalButton(
                                onClick = { onNavigate(Routes.PREMIUM) },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color.White,
                                    contentColor = FlowCrimson
                                )
                            ) {
                                Text(strings.upgradeNow)
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (showCustomizeQuick) {
        AlertDialog(
            onDismissRequest = { showCustomizeQuick = false },
            title = { Text(strings.customizeQuickAccess) },
            text = {
                LazyColumn(modifier = Modifier.height(340.dp)) {
                    items(catalog, key = { it.id }) { tool ->
                        val checked = tool.id in prefs.quickAccessToolIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggleQuickAccessTool(tool.id) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = checked, onCheckedChange = { viewModel.toggleQuickAccessTool(tool.id) })
                            Icon(tool.icon, contentDescription = null, tint = tool.color, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(tool.title, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCustomizeQuick = false }) { Text("Done") }
            }
        )
    }

    actionDoc?.let { doc ->
        DocumentQuickActionSheet(
            doc = doc,
            onDismiss = { actionDoc = null },
            onOpen = {
                actionDoc = null
                onNavigate(Routes.reader(doc.id))
            },
            onEdit = {
                actionDoc = null
                onNavigate(Routes.editor(doc.id))
            },
            onOrganize = {
                actionDoc = null
                onNavigate(Routes.organizer(doc.id))
            },
            onCompress = {
                actionDoc = null
                onNavigate(Routes.compressor(doc.id))
            },
            onProtect = {
                actionDoc = null
                onNavigate(Routes.security("protect", doc.id))
            },
            onAi = {
                actionDoc = null
                onNavigate(Routes.aiStudio(doc.id))
            },
            onDownload = {
                actionDoc = null
                saveDialogDoc = doc
            },
            onShare = {
                actionDoc = null
                viewModel.storageManager.shareFile(File(doc.filePath))
            },
            onTrash = {
                actionDoc = null
                viewModel.moveToTrash(doc.id)
            }
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
private fun QuickToolTile(tool: ToolDescriptor, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(108.dp)
            .clickable(onClick = onClick)
            .testTag("quick_tool_${tool.id}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(tool.color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(tool.icon, contentDescription = tool.title, tint = tool.color)
            }
            Text(
                text = tool.title,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FeaturedToolCard(tool: ToolDescriptor, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(178.dp)
            .clickable(onClick = onClick)
            .testTag("featured_tool_${tool.id}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tool.color),
                contentAlignment = Alignment.Center
            ) {
                Icon(tool.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Text(tool.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                tool.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun EmptyDocumentsHeroCard(
    title: String,
    subtitle: String,
    onImportClick: () -> Unit,
    onCreateClick: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PdfFlowLogoImage(size = 54.dp)
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onImportClick) {
                    Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Import PDF")
                }
                FilledTonalButton(onClick = onCreateClick) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Create PDF")
                }
            }
        }
    }
}

@Composable
fun DocumentQuickActionSheet(
    doc: LocalDocumentEntity,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onOrganize: () -> Unit,
    onCompress: () -> Unit,
    onProtect: () -> Unit,
    onAi: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onTrash: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(doc.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ActionRowItem(Icons.Default.FileOpen, "Open in PDF Reader", onOpen)
                ActionRowItem(Icons.Default.Edit, "Edit & Annotate PDF", onEdit)
                ActionRowItem(Icons.Default.Tune, "Organize / Split / Rotate Pages", onOrganize)
                ActionRowItem(Icons.Default.Compress, "Compress PDF", onCompress)
                ActionRowItem(Icons.Default.Lock, "Protect with Password", onProtect)
                ActionRowItem(Icons.Default.AutoAwesome, "Smart AI Assistant", onAi)
                ActionRowItem(Icons.Default.Download, "Save & Download to Device", onDownload)
                ActionRowItem(Icons.Default.Share, "Share PDF", onShare)
                TextButton(onClick = onTrash, modifier = Modifier.fillMaxWidth()) {
                    Text("Move to Trash", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun ActionRowItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ToolsHubScreen(
    viewModel: PdfFlowViewModel,
    onNavigate: (String) -> Unit
) {
    val strings = LocalAppStrings.current
    val catalog = remember(strings) { buildToolCatalog(strings) }
    var filterQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    val categories = listOf("All", "Create", "Edit & Organize", "Convert", "Scan & AI", "Optimize", "Security", "Utilities")

    val filtered = remember(catalog, filterQuery, selectedCategory) {
        catalog.filter { tool ->
            (selectedCategory == "All" || tool.category == selectedCategory) &&
                (filterQuery.isBlank() || tool.title.contains(filterQuery, true) || tool.subtitle.contains(filterQuery, true))
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(strings.navTools, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
            item {
                OutlinedTextField(
                    value = filterQuery,
                    onValueChange = { filterQuery = it },
                    placeholder = { Text(strings.searchPlaceholder) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().testTag("tools_search_input")
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { cat ->
                        val active = cat == selectedCategory
                        Surface(
                            shape = CircleShape,
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { selectedCategory = cat }
                        ) {
                            Text(
                                text = cat,
                                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            items(filtered.chunked(2)) { rowTools ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowTools.forEach { tool ->
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    viewModel.recordToolUsage(tool.id)
                                    onNavigate(tool.route)
                                }
                                .testTag("tool_hub_card_${tool.id}"),
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(tool.color.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(tool.icon, contentDescription = tool.title, tint = tool.color)
                                }
                                Text(tool.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    tool.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    if (rowTools.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
