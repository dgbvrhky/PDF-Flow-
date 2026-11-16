package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StarRate
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.data.storage.PdfStorageManager
import com.example.localization.AppLanguage
import com.example.localization.LocalAppStrings
import com.example.ui.components.PdfFlowLogoImage
import com.example.ui.navigation.Routes
import com.example.ui.theme.FlowAmber
import com.example.ui.theme.FlowCrimson
import com.example.ui.theme.FlowEmerald
import com.example.ui.theme.FlowIndigo
import com.example.ui.theme.FlowViolet
import com.example.ui.theme.ThemeMode
import com.example.viewmodel.PdfFlowViewModel
import com.google.firebase.auth.FirebaseUser

@Composable
fun SettingsScreen(
    viewModel: PdfFlowViewModel,
    currentUser: FirebaseUser?,
    isGuest: Boolean,
    onNavigate: (String) -> Unit,
    onSignOutClick: () -> Unit,
    onSwitchToAccountLogin: () -> Unit
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()

    var showLangDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var showSubfolderDialog by remember { mutableStateOf(false) }
    var subfolderInput by remember(prefs.downloadSubfolder) { mutableStateOf(prefs.downloadSubfolder) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 800.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(strings.navSettings, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }

            // Account / Guest Card
            item {
                Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(if (currentUser != null) FlowIndigo else FlowAmber),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = Color.White)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentUser?.displayName
                                        ?: currentUser?.phoneNumber
                                        ?: currentUser?.email
                                        ?: "Guest Mode",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (currentUser != null) currentUser.email ?: currentUser.phoneNumber ?: "Signed In"
                                    else "Local storage only — sign in to sync",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (isGuest || currentUser == null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(onClick = onSwitchToAccountLogin, modifier = Modifier.weight(1f)) {
                                    Text("Sign In / Sync")
                                }
                                OutlinedButton(onClick = { onNavigate(Routes.GUEST_MIGRATION) }, modifier = Modifier.weight(1f)) {
                                    Text(strings.migrateGuestData)
                                }
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                FilledTonalButton(onClick = { onNavigate(Routes.BACKUP) }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(strings.cloudBackup)
                                }
                                OutlinedButton(onClick = onSignOutClick, modifier = Modifier.weight(1f).testTag("settings_signout_button")) {
                                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(strings.settingsSignOut)
                                }
                            }
                        }
                    }
                }
            }

            // Personalization
            item {
                Card(shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        SettingRow(
                            icon = Icons.Default.Language,
                            title = strings.settingsLanguage,
                            subtitle = prefs.language.nativeName,
                            onClick = { showLangDialog = true }
                        )
                        SettingRow(
                            icon = Icons.Default.Brightness6,
                            title = strings.settingsTheme,
                            subtitle = when (prefs.themeMode) {
                                ThemeMode.LIGHT -> strings.themeLight
                                ThemeMode.DARK -> strings.themeDark
                                ThemeMode.SYSTEM -> strings.themeSystem
                            },
                            onClick = { showThemeDialog = true }
                        )
                        SettingRow(
                            icon = Icons.Default.WorkspacePremium,
                            title = strings.premiumCardTitle,
                            subtitle = "View Free & Pro plans",
                            onClick = { onNavigate(Routes.PREMIUM) }
                        )
                    }
                }
            }

            // Security & Preferences
            item {
                Card(shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        SettingSwitchRow(
                            icon = Icons.Default.Lock,
                            title = strings.settingsAppLock,
                            subtitle = if (prefs.appLockEnabled) "PIN protection active" else "Require PIN on launch",
                            checked = prefs.appLockEnabled,
                            onCheckedChange = { enable ->
                                if (enable) showPinDialog = true else viewModel.setAppLock(false, "")
                            }
                        )
                        SettingSwitchRow(
                            icon = Icons.Default.Fingerprint,
                            title = strings.settingsBiometric,
                            subtitle = "Fingerprint / Face unlock",
                            checked = prefs.biometricEnabled,
                            onCheckedChange = { viewModel.setBiometric(it) }
                        )
                        SettingSwitchRow(
                            icon = Icons.Default.Notifications,
                            title = strings.settingsNotifications,
                            subtitle = "Alerts for saved downloads & backups",
                            checked = prefs.notificationsEnabled,
                            onCheckedChange = { viewModel.setNotifications(it) }
                        )
                        SettingRow(
                            icon = Icons.Default.Download,
                            title = strings.settingsDownloadLocation,
                            subtitle = "Downloads/${prefs.downloadSubfolder}",
                            onClick = { showSubfolderDialog = true }
                        )
                        SettingRow(
                            icon = Icons.Default.Description,
                            title = strings.settingsDefaultViewer,
                            subtitle = "Current: ${prefs.defaultViewerMode} (${prefs.defaultPageLayout})",
                            onClick = {
                                val next = when (prefs.defaultViewerMode) {
                                    "continuous" -> "single"
                                    "single" -> "horizontal"
                                    else -> "continuous"
                                }
                                viewModel.setViewerMode(next)
                            }
                        )
                        SettingRow(
                            icon = Icons.Default.CleaningServices,
                            title = strings.settingsClearCache,
                            subtitle = "Workspace used: ${PdfStorageManager.formatBytes(viewModel.storageManager.getWorkspaceStorageBytes())}",
                            onClick = { viewModel.clearTempCache() }
                        )
                    }
                }
            }

            // Policies & About
            item {
                Card(shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        SettingRow(Icons.Default.PrivacyTip, strings.settingsPrivacyPolicy, "How PDF Flow handles your files") {
                            onNavigate(Routes.infoPolicy("privacy"))
                        }
                        SettingRow(Icons.Default.Description, strings.settingsTerms, "Usage terms & document ownership") {
                            onNavigate(Routes.infoPolicy("terms"))
                        }
                        SettingRow(Icons.AutoMirrored.Filled.HelpOutline, strings.settingsHelp, "Guides & troubleshooting") {
                            onNavigate(Routes.infoPolicy("help"))
                        }
                        SettingRow(Icons.Default.Feedback, "Send Feedback", "Email feedback or bug report") {
                            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:")
                                putExtra(Intent.EXTRA_SUBJECT, "PDF Flow Feedback (v${BuildConfig.VERSION_NAME})")
                            }
                            runCatching { context.startActivity(Intent.createChooser(emailIntent, "Send Feedback")) }
                        }
                        SettingRow(Icons.Default.Share, "Share PDF Flow", "Invite friends to try PDF Flow") {
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Try PDF Flow — Everything PDF. One Flow.")
                            }
                            context.startActivity(Intent.createChooser(share, "Share PDF Flow"))
                        }
                        SettingRow(Icons.Default.StarRate, "Rate App", "Leave a review on Google Play") {
                            onNavigate(Routes.infoPolicy("about"))
                        }
                        SettingRow(Icons.Default.Info, strings.settingsAbout, "${strings.settingsVersion}: ${BuildConfig.VERSION_NAME}") {
                            onNavigate(Routes.infoPolicy("about"))
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (showLangDialog) {
        AlertDialog(
            onDismissRequest = { showLangDialog = false },
            title = { Text(strings.settingsLanguage) },
            text = {
                LazyColumn(modifier = Modifier.height(340.dp)) {
                    items(AppLanguage.entries) { lang ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setLanguage(lang, markSelected = true)
                                    showLangDialog = false
                                }
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${lang.nativeName} (${lang.englishName})")
                            if (lang == prefs.language) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLangDialog = false }) { Text(strings.cancelAction) } }
        )
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text(strings.settingsTheme) },
            text = {
                Column {
                    listOf(
                        ThemeMode.SYSTEM to strings.themeSystem,
                        ThemeMode.LIGHT to strings.themeLight,
                        ThemeMode.DARK to strings.themeDark
                    ).forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setThemeMode(mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(label)
                            if (prefs.themeMode == mode) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showThemeDialog = false }) { Text(strings.cancelAction) } }
        )
    }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("Set App Lock PIN") },
            text = {
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { pinInput = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("4-6 digit PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(enabled = pinInput.length >= 4, onClick = {
                    viewModel.setAppLock(true, pinInput)
                    pinInput = ""
                    showPinDialog = false
                }) { Text(strings.saveAction) }
            },
            dismissButton = { TextButton(onClick = { showPinDialog = false }) { Text(strings.cancelAction) } }
        )
    }

    if (showSubfolderDialog) {
        AlertDialog(
            onDismissRequest = { showSubfolderDialog = false },
            title = { Text(strings.settingsDownloadLocation) },
            text = {
                OutlinedTextField(
                    value = subfolderInput,
                    onValueChange = { subfolderInput = it },
                    label = { Text("Subfolder inside Android Downloads") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.setDownloadSubfolder(subfolderInput)
                    showSubfolderDialog = false
                }) { Text(strings.saveAction) }
            },
            dismissButton = { TextButton(onClick = { showSubfolderDialog = false }) { Text(strings.cancelAction) } }
        )
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudBackupScreen(
    viewModel: PdfFlowViewModel,
    currentUser: FirebaseUser?,
    onBack: () -> Unit,
    onSignInClick: () -> Unit
) {
    val strings = LocalAppStrings.current
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val progress by viewModel.backupProgress.collectAsStateWithLifecycle()
    val docs by viewModel.activeDocuments.collectAsStateWithLifecycle()
    val cloudDocs by viewModel.cloudDocuments.collectAsStateWithLifecycle()
    val backups by viewModel.cloudBackups.collectAsStateWithLifecycle()
    var uploadFiles by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.cloudBackup) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.backAction) } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (currentUser == null) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = FlowAmber.copy(alpha = 0.14f))) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Account Required for Cloud Backup", fontWeight = FontWeight.Bold)
                            Text("Sign in with Google or Mobile Number OTP to securely back up and sync your PDFs across devices.")
                            Button(onClick = onSignInClick, modifier = Modifier.fillMaxWidth()) {
                                Text("Sign In Now")
                            }
                        }
                    }
                }
            } else {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Auto-Backup Preference", fontWeight = FontWeight.SemiBold)
                                Switch(checked = prefs.autoBackupEnabled, onCheckedChange = { viewModel.setAutoBackup(it) })
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = uploadFiles, onCheckedChange = { uploadFiles = it })
                                Text("Also upload PDF binary files to Firebase Storage", style = MaterialTheme.typography.bodySmall)
                            }
                            if (progress.isRunning) {
                                LinearProgressIndicator(
                                    progress = { (progress.progressPercent / 100f).coerceIn(0.05f, 1f) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(progress.statusText, style = MaterialTheme.typography.bodySmall)
                            }
                            progress.errorText?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    enabled = !progress.isRunning && docs.isNotEmpty(),
                                    onClick = { viewModel.runManualBackup(docs, uploadFiles, "Manual Cloud Backup") },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(strings.manualBackup)
                                }
                                FilledTonalButton(
                                    enabled = !progress.isRunning && cloudDocs.isNotEmpty(),
                                    onClick = { viewModel.restoreCloudBackup() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(strings.restoreBackup)
                                }
                            }
                        }
                    }
                }

                item {
                    Text("${strings.backupHistory} (${backups.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                items(backups, key = { it.id }) { b ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(b.backupName, fontWeight = FontWeight.Bold)
                                Text(b.status.uppercase(), color = if (b.status == "completed") FlowEmerald else FlowAmber)
                            }
                            Text("${b.documentCount} files • ${PdfStorageManager.formatBytes(b.totalBytes)} • ${b.deviceName}", style = MaterialTheme.typography.bodySmall)
                            b.summary?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuestMigrationScreen(
    viewModel: PdfFlowViewModel,
    currentUser: FirebaseUser?,
    onBack: () -> Unit,
    onSignInClick: () -> Unit
) {
    val strings = LocalAppStrings.current
    val docs by viewModel.activeDocuments.collectAsStateWithLifecycle()
    val cloudDocs by viewModel.cloudDocuments.collectAsStateWithLifecycle()
    val progress by viewModel.backupProgress.collectAsStateWithLifecycle()
    val selectedIds = remember(docs) { mutableStateListOf<String>().apply { addAll(docs.map { it.id }) } }
    val cloudNames = remember(cloudDocs) { cloudDocs.map { it.fileName.lowercase() }.toSet() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.migrateGuestData) },
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
                Card(colors = CardDefaults.cardColors(containerColor = FlowIndigo.copy(alpha = 0.1f))) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.WarningAmber, contentDescription = null, tint = FlowIndigo)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Your local Guest PDFs stay safe on your device during migration. Select the files you want to sync to your cloud account.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (currentUser == null) {
                item {
                    Button(onClick = onSignInClick, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                        Text("Sign In with Google or Mobile Number First")
                    }
                }
            }

            items(docs, key = { it.id }) { doc ->
                val checked = doc.id in selectedIds
                val isDuplicate = doc.fileName.lowercase() in cloudNames
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { if (checked) selectedIds.remove(doc.id) else selectedIds.add(doc.id) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = checked, onCheckedChange = { if (checked) selectedIds.remove(doc.id) else selectedIds.add(doc.id) })
                        Column(modifier = Modifier.weight(1f)) {
                            Text(doc.title, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${PdfStorageManager.formatBytes(doc.sizeBytes)}" +
                                    if (isDuplicate) " • Already in cloud (will update safely)" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (currentUser != null && docs.isNotEmpty()) {
                item {
                    if (progress.isRunning) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                    }
                    Button(
                        enabled = !progress.isRunning && selectedIds.isNotEmpty(),
                        onClick = {
                            val chosen = docs.filter { it.id in selectedIds }
                            viewModel.runManualBackup(chosen, true, "Guest-to-Account Migration")
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Text("Migrate ${selectedIds.size} Document(s) to Cloud")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumPlansScreen(
    onBack: () -> Unit
) {
    val strings = LocalAppStrings.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PDF Flow Plans") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.backAction) } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(shape = MaterialTheme.shapes.large) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.linearGradient(listOf(FlowCrimson, FlowViolet)))
                            .padding(20.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("PDF Flow Free vs Pro", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
                            Text(
                                "All core PDF tools work locally on your device without fake paywalls.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }

            item {
                PlanTierCard(
                    title = "PDF Flow Free",
                    badge = "Active",
                    features = listOf(
                        "Full PDF Reader, Night Mode, Search & Print",
                        "Create, Merge, Split, Organize, Rotate & Crop PDFs",
                        "Document Scanner, Offline ML Kit OCR & PDF Security",
                        "Local Storage & Android Downloads Saving"
                    )
                )
            }

            item {
                PlanTierCard(
                    title = "PDF Flow Pro",
                    badge = "Play Billing",
                    features = listOf(
                        "Expanded Cloud Backup & Cross-Device Sync",
                        "Higher Batch Limits & Priority Cloud AI Vision OCR",
                        "Dedicated Multi-Language AI Document Studio"
                    )
                )
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Text(
                        "Billing Notice: Subscriptions and one-time Pro upgrades are processed exclusively through Google Play Billing once your Play Console product IDs are published. No simulated or dummy payments are enabled in this build.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanTierCard(title: String, badge: String, features: List<String>) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(badge, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
            features.forEach { f ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = FlowEmerald, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(f, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoPolicyScreen(
    topic: String,
    onBack: () -> Unit
) {
    val strings = LocalAppStrings.current
    val (title, body) = when (topic) {
        "privacy" -> strings.settingsPrivacyPolicy to """
            PDF Flow processes your PDF reading, merging, splitting, compression, editing, and local ML Kit OCR directly on your Android device.
            
            • Guest Mode keeps all documents strictly on-device in Room and local app storage.
            • Cloud Backup & Sync runs only when you sign in with Google or Mobile Number OTP and explicitly initiate or enable backup.
            • AI Document Assistant & AI Vision OCR send only the selected document text or page image to Firebase AI Logic (Gemini) over encrypted HTTPS when you trigger an AI action.
            • Media & File Import uses Android Photo Picker and Storage Access Framework (SAF) with least-privilege permissions.
        """.trimIndent()
        "terms" -> strings.settingsTerms to """
            You retain full ownership and copyright of all documents you create, edit, scan, or process in PDF Flow.
            
            • Unlock PDF should only be used on PDFs you own or have legal authorization to unlock.
            • Always verify critical legal or financial OCR/AI extractions against the original source document.
        """.trimIndent()
        "help" -> strings.settingsHelp to """
            Quick Guide:
            • Save to Phone: Use Save & Download inside any tool or document menu to save straight to Android Downloads/${strings.appName} or choose any folder via SAF.
            • RTL Languages: Selecting Arabic (العربية) or Urdu (اردو) automatically adapts layouts and navigation directions.
            • Scanned PDFs: If Search finds no text in an image-based PDF, open OCR first to extract editable text.
        """.trimIndent()
        else -> strings.settingsAbout to """
            PDF Flow — Everything PDF. One Flow.
            Version: ${BuildConfig.VERSION_NAME}
            Package: com.pdfflow.app
            
            A complete, multi-language mobile PDF workspace for reading, editing, scanning, converting, organizing, securing, OCR, and AI document intelligence.
        """.trimIndent()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.backAction) } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (topic == "about") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    PdfFlowLogoImage(size = 64.dp)
                    Column {
                        Text(strings.appName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(strings.tagline, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text(body, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
