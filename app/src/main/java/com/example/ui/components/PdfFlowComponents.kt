package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.LocalDocumentEntity
import com.example.data.storage.PdfStorageManager
import com.example.data.storage.SaveResult
import com.example.localization.LocalAppStrings
import com.example.ui.theme.FlowCrimson
import com.example.ui.theme.FlowEmerald
import com.example.ui.theme.FlowIndigo
import com.example.ui.theme.FlowRoyalBlue
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PdfFlowLogoImage(
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(id = R.drawable.pdf_flow_logo),
        contentDescription = "PDF Flow Logo",
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(size * 0.24f))
            .clip(RoundedCornerShape(size * 0.24f))
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfFlowTopBar(
    title: String? = null,
    activeDownloadCount: Int = 0,
    isGuest: Boolean = false,
    onSearchClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    val strings = LocalAppStrings.current
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
        ),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PdfFlowLogoImage(size = 34.dp)
                Column {
                    Text(
                        text = title ?: strings.appName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (title == null) {
                        Text(
                            text = strings.tagline,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        actions = {
            IconButton(
                onClick = onSearchClick,
                modifier = Modifier.testTag("topbar_search_button")
            ) {
                Icon(Icons.Default.Search, contentDescription = strings.searchPlaceholder)
            }
            IconButton(
                onClick = onDownloadsClick,
                modifier = Modifier.testTag("topbar_downloads_button")
            ) {
                BadgedBox(
                    badge = {
                        if (activeDownloadCount > 0) {
                            Badge { Text(activeDownloadCount.toString()) }
                        }
                    }
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = strings.toolDownloadManager)
                }
            }
            IconButton(
                onClick = onProfileClick,
                modifier = Modifier.testTag("topbar_profile_button")
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    if (isGuest) FlowCrimson else FlowIndigo,
                                    FlowRoyalBlue
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = strings.settingsAccount,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    )
}

@Composable
fun PdfDocumentCard(
    doc: LocalDocumentEntity,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateStr = remember(doc.updatedAt) {
        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(doc.updatedAt))
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .testTag("doc_card_${doc.id}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(listOf(FlowCrimson, Color(0xFFE04F33)))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (doc.isEncrypted) Icons.Default.Lock else Icons.Default.PictureAsPdf,
                    contentDescription = doc.title,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = doc.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (doc.isSyncedToCloud) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = "Synced",
                            tint = FlowEmerald,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "${doc.pageCount}p • ${PdfStorageManager.formatBytes(doc.sizeBytes)} • $dateStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("fav_toggle_${doc.id}")
            ) {
                Icon(
                    imageVector = if (doc.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = "Favorite",
                    tint = if (doc.isFavorite) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onMoreClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("doc_more_${doc.id}")
            ) {
                Icon(Icons.Default.MoreVert, contentDescription = "More options")
            }
        }
    }
}

@Composable
fun SaveAndDownloadDialog(
    sourceFile: File,
    defaultFileName: String,
    mimeType: String = "application/pdf",
    storageManager: PdfStorageManager,
    saveToDownloadsAction: suspend (File, String, String, Boolean) -> Result<SaveResult>,
    onDismiss: () -> Unit
) {
    val strings = LocalAppStrings.current
    val scope = rememberCoroutineScope()
    var fileName by remember { mutableStateOf(defaultFileName) }
    var overwrite by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var saveResult by remember { mutableStateOf<SaveResult?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(mimeType)
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isSaving = true
                errorMsg = null
                val res = storageManager.saveToCustomSafUri(sourceFile, uri, fileName)
                isSaving = false
                res.onSuccess { saveResult = it }.onFailure { errorMsg = it.localizedMessage }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(strings.saveAndDownload, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "File Size: ${PdfStorageManager.formatBytes(sourceFile.length())}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (saveResult == null) {
                    OutlinedTextField(
                        value = fileName,
                        onValueChange = { fileName = it },
                        label = { Text("File Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_filename_input")
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { overwrite = !overwrite }
                    ) {
                        Checkbox(checked = overwrite, onCheckedChange = { overwrite = it })
                        Text(
                            text = "Replace existing file if same name exists",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                if (isSaving) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                errorMsg?.let { err ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                saveResult?.let { ok ->
                    Surface(
                        color = FlowEmerald.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Saved Successfully",
                                fontWeight = FontWeight.Bold,
                                color = FlowEmerald
                            )
                            Text(
                                text = "${ok.fileName} (${PdfStorageManager.formatBytes(ok.sizeBytes)})",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "Location: ${ok.displayPath}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (saveResult != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { storageManager.shareUri(saveResult!!.uri, mimeType) },
                        modifier = Modifier.testTag("saved_share_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.shareAction)
                    }
                    Button(
                        onClick = { storageManager.openExternalUri(saveResult!!.uri, mimeType) },
                        modifier = Modifier.testTag("saved_open_button")
                    ) {
                        Text(strings.openAction)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !isSaving && fileName.isNotBlank(),
                        onClick = {
                            scope.launch {
                                isSaving = true
                                errorMsg = null
                                val res = saveToDownloadsAction(sourceFile, fileName, mimeType, overwrite)
                                isSaving = false
                                res.onSuccess { saveResult = it }.onFailure { errorMsg = it.localizedMessage }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_to_downloads_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.saveToDownloads)
                    }
                    OutlinedButton(
                        enabled = !isSaving && fileName.isNotBlank(),
                        onClick = { safLauncher.launch(fileName) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_choose_folder_button")
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.chooseFolder)
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text(if (saveResult != null) "Done" else strings.cancelAction)
            }
        }
    )
}
