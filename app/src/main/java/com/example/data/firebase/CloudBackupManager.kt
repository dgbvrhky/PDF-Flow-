package com.example.data.firebase

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.data.local.DocumentDao
import com.example.data.local.LocalDocumentEntity
import com.example.data.local.PreferencesRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.io.File

data class BackupProgressState(
    val isRunning: Boolean = false,
    val progressPercent: Int = 0,
    val statusText: String = "",
    val errorText: String? = null
)

class CloudBackupManager(
    private val context: Context,
    private val cloudRepo: CloudSyncRepository,
    private val documentDao: DocumentDao,
    private val prefsRepo: PreferencesRepository
) {
    private val storage by lazy { Firebase.storage }
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    suspend fun syncProfileAndSettingsFromPrefs(planTier: String) {
        val user = auth.currentUser ?: return
        val provider = if (!user.phoneNumber.isNullOrBlank() && user.email.isNullOrBlank()) "phone" else "google"
        cloudRepo.upsertProfile(
            displayName = user.displayName ?: user.phoneNumber ?: "PDF Flow User",
            email = user.email,
            phoneNumber = user.phoneNumber,
            photoUrl = user.photoUrl?.toString(),
            planTier = planTier,
            authProvider = provider
        )
        val prefs = prefsRepo.preferences.first()
        cloudRepo.saveSettings(
            CloudUserSettings(
                userId = user.uid,
                languageCode = prefs.language.code,
                themeMode = prefs.themeMode.storageKey,
                defaultViewerMode = prefs.defaultViewerMode,
                defaultPageLayout = prefs.defaultPageLayout,
                autoBackupEnabled = prefs.autoBackupEnabled,
                appLockEnabled = prefs.appLockEnabled,
                biometricEnabled = prefs.biometricEnabled,
                notificationsEnabled = prefs.notificationsEnabled,
                downloadSubfolder = prefs.downloadSubfolder
            )
        )
    }

    suspend fun runBackup(
        documents: List<LocalDocumentEntity>,
        uploadFilesToStorage: Boolean,
        backupLabel: String = "Manual Backup",
        onProgress: (BackupProgressState) -> Unit
    ): Result<String> {
        val user = auth.currentUser
            ?: return Result.failure(IllegalStateException("Sign in with Google or Phone to back up to the cloud."))
        val uid = user.uid

        onProgress(BackupProgressState(isRunning = true, progressPercent = 5, statusText = "Creating backup record..."))
        val totalBytes = documents.sumOf { it.sizeBytes }
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".take(100)
        val backupIdResult = cloudRepo.createBackup(
            backupName = backupLabel,
            documentCount = documents.size.toLong(),
            totalBytes = totalBytes,
            deviceName = deviceName,
            summary = "Backing up ${documents.size} documents"
        )
        val backupId = backupIdResult.getOrElse { err ->
            onProgress(BackupProgressState(errorText = err.localizedMessage ?: "Failed to start backup."))
            return Result.failure(err)
        }

        var uploadedCount = 0
        var uploadedBytes = 0L
        val warnings = mutableListOf<String>()

        try {
            val total = documents.size.coerceAtLeast(1)
            documents.forEachIndexed { index, doc ->
                val basePct = 10 + ((index * 85) / total)
                onProgress(
                    BackupProgressState(
                        isRunning = true,
                        progressPercent = basePct,
                        statusText = "Syncing ${doc.fileName}..."
                    )
                )

                var storagePath: String? = null
                if (uploadFilesToStorage) {
                    val localFile = File(doc.filePath)
                    if (localFile.exists() && localFile.length() > 0L) {
                        val ref = storage.reference.child("users/$uid/pdfs/${doc.id}/${doc.fileName}")
                        try {
                            val task = ref.putFile(Uri.fromFile(localFile))
                            task.addOnProgressListener { snap ->
                                if (snap.totalByteCount > 0) {
                                    val filePct = ((snap.bytesTransferred * 100) / snap.totalByteCount).toInt()
                                    onProgress(
                                        BackupProgressState(
                                            isRunning = true,
                                            progressPercent = basePct,
                                            statusText = "Uploading ${doc.fileName} ($filePct%)..."
                                        )
                                    )
                                }
                            }.await()
                            storagePath = ref.path
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w("CloudBackup", "Storage upload failed for ${doc.fileName}: ${e.message}")
                            warnings.add("${doc.fileName}: ${e.localizedMessage ?: "Storage rule/bucket unavailable"}")
                        }
                    }
                }

                val cloudDoc = CloudDocumentRecord(
                    id = doc.id,
                    userId = uid,
                    title = doc.title,
                    fileName = doc.fileName,
                    mimeType = doc.mimeType,
                    sizeBytes = doc.sizeBytes,
                    pageCount = doc.pageCount.toLong(),
                    folderName = doc.folderName,
                    localUri = doc.filePath,
                    storagePath = storagePath,
                    isFavorite = doc.isFavorite,
                    isTrashed = doc.isTrashed,
                    isEncrypted = doc.isEncrypted,
                    summary = doc.summary
                )
                cloudRepo.upsertDocument(cloudDoc).getOrThrow()
                if (doc.isFavorite) {
                    cloudRepo.setFavorite(
                        CloudFavoriteDocument(
                            documentId = doc.id,
                            title = doc.title,
                            fileName = doc.fileName,
                            sizeBytes = doc.sizeBytes,
                            pageCount = doc.pageCount.toLong()
                        )
                    )
                }
                documentDao.markSynced(doc.id, true)
                uploadedCount++
                uploadedBytes += doc.sizeBytes
            }

            val summaryText = if (warnings.isEmpty()) {
                "Synced $uploadedCount documents safely."
            } else {
                "Metadata synced ($uploadedCount docs). Storage note: ${warnings.first()}"
            }
            cloudRepo.completeBackup(
                backupId = backupId,
                status = "completed",
                documentCount = uploadedCount.toLong(),
                totalBytes = uploadedBytes,
                summary = summaryText
            )
            onProgress(BackupProgressState(isRunning = false, progressPercent = 100, statusText = summaryText))
            return Result.success(summaryText)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            cloudRepo.completeBackup(
                backupId = backupId,
                status = "failed",
                documentCount = uploadedCount.toLong(),
                totalBytes = uploadedBytes,
                summary = e.localizedMessage ?: "Backup interrupted."
            )
            onProgress(BackupProgressState(isRunning = false, errorText = e.localizedMessage ?: "Backup failed."))
            return Result.failure(e)
        }
    }

    suspend fun restoreFromCloud(
        cloudDocs: List<CloudDocumentRecord>,
        onProgress: (BackupProgressState) -> Unit
    ): Result<Int> {
        val existingLocal = documentDao.getActiveDocumentsOnce().associateBy { it.id }
        var restored = 0
        try {
            val total = cloudDocs.size.coerceAtLeast(1)
            cloudDocs.forEachIndexed { idx, cloudDoc ->
                onProgress(
                    BackupProgressState(
                        isRunning = true,
                        progressPercent = 10 + ((idx * 85) / total),
                        statusText = "Restoring ${cloudDoc.fileName}..."
                    )
                )
                val existing = existingLocal[cloudDoc.id]
                var targetPath = existing?.filePath ?: File(File(context.filesDir, "pdf_workspace").apply { mkdirs() }, cloudDoc.fileName).absolutePath
                val targetFile = File(targetPath)
                if (!targetFile.exists() && !cloudDoc.storagePath.isNullOrBlank()) {
                    try {
                        storage.reference.child(cloudDoc.storagePath).getFile(targetFile).await()
                    } catch (e: Exception) {
                        Log.w("CloudBackup", "Storage file restore skipped for ${cloudDoc.fileName}: ${e.message}")
                    }
                }
                if (existing == null || targetFile.exists()) {
                    documentDao.upsert(
                        LocalDocumentEntity(
                            id = cloudDoc.id,
                            title = cloudDoc.title,
                            fileName = cloudDoc.fileName,
                            filePath = targetPath,
                            mimeType = cloudDoc.mimeType,
                            sizeBytes = if (targetFile.exists()) targetFile.length() else cloudDoc.sizeBytes,
                            pageCount = cloudDoc.pageCount.toInt().coerceAtLeast(1),
                            folderName = cloudDoc.folderName,
                            isFavorite = cloudDoc.isFavorite,
                            isTrashed = cloudDoc.isTrashed,
                            isEncrypted = cloudDoc.isEncrypted,
                            summary = cloudDoc.summary,
                            isSyncedToCloud = true
                        )
                    )
                    restored++
                }
            }
            onProgress(BackupProgressState(isRunning = false, progressPercent = 100, statusText = "Restored $restored cloud records."))
            return Result.success(restored)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onProgress(BackupProgressState(isRunning = false, errorText = e.localizedMessage ?: "Restore failed."))
            return Result.failure(e)
        }
    }
}
