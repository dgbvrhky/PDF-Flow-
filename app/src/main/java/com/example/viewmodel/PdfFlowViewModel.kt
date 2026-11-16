package com.example.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.BackupProgressState
import com.example.data.firebase.CloudBackupManager
import com.example.data.firebase.CloudBackupRecord
import com.example.data.firebase.CloudDocumentRecord
import com.example.data.firebase.CloudSyncRepository
import com.example.data.local.AppPreferences
import com.example.data.local.DownloadTaskEntity
import com.example.data.local.FolderEntity
import com.example.data.local.LocalDocumentEntity
import com.example.data.local.PdfFlowDatabase
import com.example.data.local.PreferencesRepository
import com.example.data.storage.PdfStorageManager
import com.example.data.storage.SaveResult
import com.example.localization.AppLanguage
import com.example.pdf.PdfEngine
import com.example.ui.theme.ThemeMode
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

enum class FileSortOrder {
    DATE_DESC,
    NAME_ASC,
    SIZE_DESC
}

class PdfFlowViewModel(
    private val appContext: Context,
    private val db: PdfFlowDatabase,
    val prefsRepo: PreferencesRepository,
    val storageManager: PdfStorageManager,
    private val cloudRepo: CloudSyncRepository?,
    private val currentUserId: String?
) : ViewModel() {

    private val documentDao = db.documentDao()
    private val downloadDao = db.downloadDao()
    private val folderDao = db.folderDao()

    private val backupManager: CloudBackupManager? = cloudRepo?.let {
        CloudBackupManager(appContext, it, documentDao, prefsRepo)
    }

    val preferences: StateFlow<AppPreferences> = prefsRepo.preferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppPreferences())

    val activeDocuments: StateFlow<List<LocalDocumentEntity>> = documentDao.observeActiveDocuments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteDocuments: StateFlow<List<LocalDocumentEntity>> = documentDao.observeFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trashedDocuments: StateFlow<List<LocalDocumentEntity>> = documentDao.observeTrashedDocuments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloadTasks: StateFlow<List<DownloadTaskEntity>> = downloadDao.observeDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customFolders: StateFlow<List<FolderEntity>> = folderDao.observeFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cloudDocuments: StateFlow<List<CloudDocumentRecord>> =
        if (cloudRepo != null && currentUserId != null) {
            cloudRepo.observeDocuments(currentUserId)
                .catch { emit(emptyList()) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        } else {
            MutableStateFlow(emptyList())
        }

    val cloudBackups: StateFlow<List<CloudBackupRecord>> =
        if (cloudRepo != null && currentUserId != null) {
            cloudRepo.observeBackups(currentUserId)
                .catch { emit(emptyList()) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        } else {
            MutableStateFlow(emptyList())
        }

    private val _backupProgress = MutableStateFlow(BackupProgressState())
    val backupProgress: StateFlow<BackupProgressState> = _backupProgress.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        if (currentUserId != null) {
            viewModelScope.launch {
                backupManager?.syncProfileAndSettingsFromPrefs(preferences.value.planTier)
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun postStatus(message: String) {
        _statusMessage.value = message
    }

    fun setLanguage(language: AppLanguage, markSelected: Boolean = true) {
        viewModelScope.launch {
            prefsRepo.setLanguage(language, markSelected)
            if (currentUserId != null) backupManager?.syncProfileAndSettingsFromPrefs(preferences.value.planTier)
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch { prefsRepo.setOnboardingCompleted(true) }
    }

    fun enterGuestMode() {
        viewModelScope.launch { prefsRepo.setGuestMode(true) }
    }

    fun exitGuestMode() {
        viewModelScope.launch { prefsRepo.setGuestMode(false) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            prefsRepo.setThemeMode(mode)
            if (currentUserId != null) backupManager?.syncProfileAndSettingsFromPrefs(preferences.value.planTier)
        }
    }

    fun setViewerMode(mode: String) {
        viewModelScope.launch { prefsRepo.setDefaultViewerMode(mode) }
    }

    fun setPageLayout(layout: String) {
        viewModelScope.launch { prefsRepo.setDefaultPageLayout(layout) }
    }

    fun setAppLock(enabled: Boolean, pin: String? = null) {
        viewModelScope.launch { prefsRepo.setAppLock(enabled, pin) }
    }

    fun setBiometric(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setBiometricEnabled(enabled) }
    }

    fun setNotifications(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setNotificationsEnabled(enabled) }
    }

    fun setAutoBackup(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setAutoBackupEnabled(enabled) }
    }

    fun setDownloadSubfolder(subfolder: String) {
        viewModelScope.launch { prefsRepo.setDownloadSubfolder(subfolder) }
    }

    fun toggleQuickAccessTool(toolId: String) {
        viewModelScope.launch {
            val current = preferences.value.quickAccessToolIds
            val updated = if (toolId in current && current.size > 3) {
                current - toolId
            } else if (toolId !in current) {
                current + toolId
            } else current
            prefsRepo.updateQuickAccessTools(updated)
        }
    }

    fun recordToolUsage(toolId: String) {
        viewModelScope.launch { prefsRepo.recordToolUsage(toolId) }
    }

    suspend fun registerGeneratedPdf(
        file: File,
        title: String = file.nameWithoutExtension,
        folderName: String = "Root",
        isEncrypted: Boolean = false
    ): LocalDocumentEntity {
        val pageCount = runCatching { PdfEngine.getPageCount(appContext, file) }.getOrDefault(1)
        val entity = LocalDocumentEntity(
            id = "doc_" + UUID.randomUUID().toString().replace("-", "").take(14),
            title = title,
            fileName = file.name,
            filePath = file.absolutePath,
            sizeBytes = file.length(),
            pageCount = pageCount,
            folderName = folderName,
            isEncrypted = isEncrypted
        )
        documentDao.upsert(entity)
        return entity
    }

    fun importDocumentUri(uri: Uri, folderName: String = "Root", onImported: (LocalDocumentEntity) -> Unit = {}) {
        viewModelScope.launch {
            runCatching {
                val file = storageManager.importUriToWorkspace(uri, "imported_${System.currentTimeMillis()}.pdf")
                registerGeneratedPdf(file, file.nameWithoutExtension, folderName)
            }.onSuccess { entity ->
                _statusMessage.value = "Imported ${entity.fileName}"
                onImported(entity)
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Failed to import file."
            }
        }
    }

    fun toggleFavorite(doc: LocalDocumentEntity) {
        viewModelScope.launch {
            val next = !doc.isFavorite
            documentDao.setFavorite(doc.id, next)
            if (cloudRepo != null && currentUserId != null) {
                if (next) {
                    cloudRepo.setFavorite(
                        com.example.data.firebase.CloudFavoriteDocument(
                            documentId = doc.id,
                            title = doc.title,
                            fileName = doc.fileName,
                            sizeBytes = doc.sizeBytes,
                            pageCount = doc.pageCount.toLong()
                        )
                    )
                } else {
                    cloudRepo.removeFavorite(doc.id)
                }
            }
        }
    }

    fun markOpened(docId: String) {
        viewModelScope.launch { documentDao.markOpened(docId) }
    }

    fun moveToTrash(docId: String) {
        viewModelScope.launch { documentDao.setTrashed(docId, true) }
    }

    fun restoreFromTrash(docId: String) {
        viewModelScope.launch { documentDao.setTrashed(docId, false) }
    }

    fun deletePermanently(doc: LocalDocumentEntity) {
        viewModelScope.launch {
            runCatching { File(doc.filePath).delete() }
            documentDao.deletePermanently(doc.id)
            cloudRepo?.deleteDocument(doc.id)
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            trashedDocuments.value.forEach { runCatching { File(it.filePath).delete() } }
            documentDao.emptyTrash()
        }
    }

    fun renameDocument(doc: LocalDocumentEntity, newName: String) {
        viewModelScope.launch {
            val clean = PdfStorageManager.sanitizeFileName(newName)
            val finalFileName = if (clean.endsWith(".pdf", true)) clean else "$clean.pdf"
            val title = finalFileName.removeSuffix(".pdf")
            documentDao.rename(doc.id, title, finalFileName)
        }
    }

    fun duplicateDocument(doc: LocalDocumentEntity) {
        viewModelScope.launch {
            runCatching {
                val src = File(doc.filePath)
                if (!src.exists()) error("Source file missing on device.")
                val copyFile = storageManager.createWorkspaceFile("${doc.title}_copy.pdf")
                src.copyTo(copyFile, overwrite = true)
                registerGeneratedPdf(copyFile, "${doc.title} Copy", doc.folderName, doc.isEncrypted)
            }.onSuccess {
                _statusMessage.value = "Copied ${it.fileName}"
            }.onFailure {
                _statusMessage.value = it.localizedMessage ?: "Copy failed."
            }
        }
    }

    fun createFolder(name: String) {
        val clean = name.trim()
        if (clean.isNotBlank()) {
            viewModelScope.launch { folderDao.insert(FolderEntity(clean)) }
        }
    }

    fun moveDocumentToFolder(docId: String, folderName: String) {
        viewModelScope.launch { documentDao.moveToFolder(docId, folderName) }
    }

    fun saveSummaryForDocument(docId: String, summary: String) {
        viewModelScope.launch { documentDao.updateSummary(docId, summary) }
    }

    suspend fun saveFileToPublicDownloads(
        sourceFile: File,
        desiredFileName: String,
        mimeType: String = "application/pdf",
        overwriteExisting: Boolean = false
    ): Result<SaveResult> {
        val prefs = preferences.value
        return storageManager.saveToPublicDownloads(
            sourceFile = sourceFile,
            desiredFileName = desiredFileName,
            mimeType = mimeType,
            subfolder = prefs.downloadSubfolder,
            overwriteExisting = overwriteExisting,
            notifyUser = prefs.notificationsEnabled
        )
    }

    fun retryDownload(task: DownloadTaskEntity) {
        viewModelScope.launch {
            val src = File(task.sourceFilePath)
            if (!src.exists()) {
                _statusMessage.value = "Original source file is no longer available."
                return@launch
            }
            val res = saveFileToPublicDownloads(src, task.fileName)
            _statusMessage.value = if (res.isSuccess) "Saved ${task.fileName} to Downloads."
            else res.exceptionOrNull()?.localizedMessage ?: "Retry failed."
        }
    }

    fun clearTempCache() {
        val freed = storageManager.clearTempCache()
        _statusMessage.value = "Cleared ${PdfStorageManager.formatBytes(freed)} temporary cache."
    }

    fun runManualBackup(
        selectedDocs: List<LocalDocumentEntity> = activeDocuments.value,
        uploadToStorage: Boolean = true,
        label: String = "Cloud Backup"
    ) {
        val mgr = backupManager ?: run {
            _statusMessage.value = "Sign in with Google or Mobile Number to back up to the cloud."
            return
        }
        viewModelScope.launch {
            val res = mgr.runBackup(selectedDocs, uploadToStorage, label) { state ->
                _backupProgress.value = state
            }
            _statusMessage.value = res.getOrElse { it.localizedMessage ?: "Backup failed." }
        }
    }

    fun restoreCloudBackup() {
        val mgr = backupManager ?: run {
            _statusMessage.value = "Sign in to restore cloud backups."
            return
        }
        viewModelScope.launch {
            val res = mgr.restoreFromCloud(cloudDocuments.value) { state ->
                _backupProgress.value = state
            }
            _statusMessage.value = res.fold(
                onSuccess = { count -> "Restored $count document(s) from cloud." },
                onFailure = { it.localizedMessage ?: "Restore failed." }
            )
        }
    }

    companion object {
        fun factory(context: Context, user: FirebaseUser?): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val appCtx = context.applicationContext
                    val db = PdfFlowDatabase.getInstance(appCtx)
                    val prefs = PreferencesRepository(appCtx)
                    val storage = PdfStorageManager(appCtx, db.downloadDao())
                    val cloud = if (user != null) CloudSyncRepository(appCtx) else null
                    return PdfFlowViewModel(appCtx, db, prefs, storage, cloud, user?.uid) as T
                }
            }
    }
}
