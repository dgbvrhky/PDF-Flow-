package com.example.data.firebase

import android.content.Context
import com.example.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class CloudSyncRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = FirebaseAuth.getInstance()
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in before accessing cloud sync.")
    }

    private fun sanitizeId(raw: String): String {
        val cleaned = raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(100)
        return cleaned.ifBlank { UUID.randomUUID().toString() }
    }

    // --- Profile ---
    suspend fun upsertProfile(
        displayName: String,
        email: String?,
        phoneNumber: String?,
        photoUrl: String?,
        planTier: String = "free",
        authProvider: String = "google"
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("profile").document("main")
        val existing = docRef.get().await()
        val payload = buildMap<String, Any> {
            put("userId", uid)
            put("displayName", displayName.ifBlank { "PDF Flow User" }.take(100))
            if (!email.isNullOrBlank()) put("email", email.take(160))
            if (!phoneNumber.isNullOrBlank()) put("phoneNumber", phoneNumber.take(32))
            if (!photoUrl.isNullOrBlank()) put("photoUrl", photoUrl.take(500))
            put("planTier", if (planTier == "premium") "premium" else "free")
            put("authProvider", if (authProvider == "phone") "phone" else "google")
            if (!existing.exists()) {
                put("createdAt", FieldValue.serverTimestamp())
            }
            put("updatedAt", FieldValue.serverTimestamp())
        }
        if (existing.exists()) {
            docRef.update(payload.filterKeys { it != "userId" }).await()
        } else {
            docRef.set(payload).await()
        }
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "users/${auth.currentUser?.uid}/profile/main", auth)
    }

    suspend fun getProfile(userId: String = requireUserId()): Result<CloudUserProfile?> = runCatching {
        val docRef = db.collection("users").document(userId).collection("profile").document("main")
        docRef.get().await().toObject(CloudUserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.GET, "users/$userId/profile/main", auth)
    }

    // --- Settings ---
    suspend fun saveSettings(settings: CloudUserSettings): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("settings").document("preferences")
        val existing = docRef.get().await()
        val payload = buildMap<String, Any> {
            put("userId", uid)
            put("languageCode", settings.languageCode.take(10).ifBlank { "en" })
            put("themeMode", settings.themeMode)
            put("defaultViewerMode", settings.defaultViewerMode)
            put("defaultPageLayout", settings.defaultPageLayout)
            put("autoBackupEnabled", settings.autoBackupEnabled)
            put("appLockEnabled", settings.appLockEnabled)
            put("biometricEnabled", settings.biometricEnabled)
            put("notificationsEnabled", settings.notificationsEnabled)
            put("downloadSubfolder", settings.downloadSubfolder.take(120).ifBlank { "PDF Flow" })
            if (!existing.exists()) {
                put("createdAt", FieldValue.serverTimestamp())
            }
            put("updatedAt", FieldValue.serverTimestamp())
        }
        if (existing.exists()) {
            docRef.update(payload.filterKeys { it != "userId" }).await()
        } else {
            docRef.set(payload).await()
        }
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "users/${auth.currentUser?.uid}/settings/preferences", auth)
    }

    suspend fun getSettings(userId: String = requireUserId()): Result<CloudUserSettings?> = runCatching {
        val docRef = db.collection("users").document(userId).collection("settings").document("preferences")
        docRef.get().await().toObject(CloudUserSettings::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.GET, "users/$userId/settings/preferences", auth)
    }

    // --- Documents ---
    fun observeDocuments(userId: String): Flow<List<CloudDocumentRecord>> = flow {
        val path = "users/$userId/documents"
        emitAll(
            db.collection("users").document(userId).collection("documents")
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        CloudDocumentRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    val ex = fsError ?: (error as? Exception)
                    if (ex != null) {
                        handleFirestoreError(ex, OperationType.LIST, path, auth)
                    }
                    throw error
                }
        )
    }

    suspend fun upsertDocument(record: CloudDocumentRecord): Result<String> = runCatching {
        val uid = requireUserId()
        val docId = sanitizeId(record.id)
        val docRef = db.collection("users").document(uid).collection("documents").document(docId)
        val existing = docRef.get().await()
        val payload = buildMap<String, Any> {
            put("userId", uid)
            put("title", record.title.ifBlank { "Untitled" }.take(200))
            put("fileName", record.fileName.ifBlank { "document.pdf" }.take(200))
            put("mimeType", record.mimeType.ifBlank { "application/pdf" }.take(100))
            put("sizeBytes", record.sizeBytes.coerceAtLeast(0L))
            put("pageCount", record.pageCount.coerceAtLeast(0L))
            put("folderName", record.folderName.ifBlank { "Root" }.take(100))
            if (!record.localUri.isNullOrBlank()) put("localUri", record.localUri.take(500))
            if (!record.storagePath.isNullOrBlank()) put("storagePath", record.storagePath.take(500))
            put("isFavorite", record.isFavorite)
            put("isTrashed", record.isTrashed)
            put("isEncrypted", record.isEncrypted)
            if (!record.summary.isNullOrBlank()) put("summary", record.summary.take(4000))
            if (record.tags.isNotEmpty()) put("tags", record.tags.take(10))
            if (!existing.exists()) {
                put("createdAt", FieldValue.serverTimestamp())
            }
            put("updatedAt", FieldValue.serverTimestamp())
        }
        if (existing.exists()) {
            docRef.update(payload.filterKeys { it != "userId" }).await()
        } else {
            docRef.set(payload).await()
        }
        docId
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "users/${auth.currentUser?.uid}/documents/${record.id}", auth)
    }

    suspend fun getDocument(userId: String, docId: String): Result<CloudDocumentRecord?> = runCatching {
        val docRef = db.collection("users").document(userId).collection("documents").document(docId)
        docRef.get().await().toObject(CloudDocumentRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.GET, "users/$userId/documents/$docId", auth)
    }

    suspend fun deleteDocument(docId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("documents").document(docId)
        docRef.delete().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.DELETE, "users/${auth.currentUser?.uid}/documents/$docId", auth)
    }

    // --- Favorites ---
    fun observeFavorites(userId: String): Flow<List<CloudFavoriteDocument>> = flow {
        val path = "users/$userId/favorites"
        emitAll(
            db.collection("users").document(userId).collection("favorites")
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        CloudFavoriteDocument::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    val ex = fsError ?: (error as? Exception)
                    if (ex != null) {
                        handleFirestoreError(ex, OperationType.LIST, path, auth)
                    }
                    throw error
                }
        )
    }

    suspend fun setFavorite(fav: CloudFavoriteDocument): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docId = sanitizeId(fav.documentId)
        val docRef = db.collection("users").document(uid).collection("favorites").document(docId)
        val existing = docRef.get().await()
        val payload = buildMap<String, Any> {
            put("userId", uid)
            put("documentId", docId)
            put("title", fav.title.ifBlank { "Document" }.take(200))
            put("fileName", fav.fileName.ifBlank { "document.pdf" }.take(200))
            put("sizeBytes", fav.sizeBytes.coerceAtLeast(0L))
            put("pageCount", fav.pageCount.coerceAtLeast(0L))
            if (!existing.exists()) {
                put("createdAt", FieldValue.serverTimestamp())
            }
            put("updatedAt", FieldValue.serverTimestamp())
        }
        if (existing.exists()) {
            docRef.update(payload.filterKeys { it != "userId" && it != "documentId" }).await()
        } else {
            docRef.set(payload).await()
        }
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "users/${auth.currentUser?.uid}/favorites/${fav.documentId}", auth)
    }

    suspend fun removeFavorite(documentId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docId = sanitizeId(documentId)
        db.collection("users").document(uid).collection("favorites").document(docId).delete().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.DELETE, "users/${auth.currentUser?.uid}/favorites/$documentId", auth)
    }

    // --- Backups ---
    fun observeBackups(userId: String): Flow<List<CloudBackupRecord>> = flow {
        val path = "users/$userId/backups"
        emitAll(
            db.collection("users").document(userId).collection("backups")
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        CloudBackupRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    val ex = fsError ?: (error as? Exception)
                    if (ex != null) {
                        handleFirestoreError(ex, OperationType.LIST, path, auth)
                    }
                    throw error
                }
        )
    }

    suspend fun createBackup(
        backupName: String,
        documentCount: Long,
        totalBytes: Long,
        deviceName: String,
        summary: String?
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val backupId = "bak_" + UUID.randomUUID().toString().replace("-", "").take(16)
        val docRef = db.collection("users").document(uid).collection("backups").document(backupId)
        val payload = buildMap<String, Any> {
            put("userId", uid)
            put("backupName", backupName.take(150).ifBlank { "PDF Flow Backup" })
            put("documentCount", documentCount.coerceAtLeast(0L))
            put("totalBytes", totalBytes.coerceAtLeast(0L))
            put("status", "in_progress")
            put("deviceName", deviceName.take(100).ifBlank { "Android" })
            if (!summary.isNullOrBlank()) put("summary", summary.take(500))
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }
        docRef.set(payload).await()
        backupId
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "users/${auth.currentUser?.uid}/backups", auth)
    }

    suspend fun completeBackup(
        backupId: String,
        status: String,
        documentCount: Long,
        totalBytes: Long,
        summary: String?
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("backups").document(backupId)
        val updates = buildMap<String, Any> {
            put("status", if (status == "failed") "failed" else "completed")
            put("documentCount", documentCount.coerceAtLeast(0L))
            put("totalBytes", totalBytes.coerceAtLeast(0L))
            if (!summary.isNullOrBlank()) put("summary", summary.take(500))
            put("updatedAt", FieldValue.serverTimestamp())
        }
        docRef.update(updates).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "users/${auth.currentUser?.uid}/backups/$backupId", auth)
    }
}
