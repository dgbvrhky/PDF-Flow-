package com.example.data.firebase

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class CloudUserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val phoneNumber: String? = null,
    val photoUrl: String? = null,
    val planTier: String = "free",
    val authProvider: String = "google",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudUserSettings(
    @DocumentId val id: String = "",
    val userId: String = "",
    val languageCode: String = "en",
    val themeMode: String = "system",
    val defaultViewerMode: String = "continuous",
    val defaultPageLayout: String = "fit_width",
    val autoBackupEnabled: Boolean = false,
    val appLockEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val downloadSubfolder: String = "PDF Flow",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudDocumentRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val fileName: String = "",
    val mimeType: String = "application/pdf",
    val sizeBytes: Long = 0L,
    val pageCount: Long = 0L,
    val folderName: String = "Root",
    val localUri: String? = null,
    val storagePath: String? = null,
    val isFavorite: Boolean = false,
    val isTrashed: Boolean = false,
    val isEncrypted: Boolean = false,
    val summary: String? = null,
    val tags: List<String> = emptyList(),
    val lastOpenedAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudFavoriteDocument(
    @DocumentId val id: String = "",
    val userId: String = "",
    val documentId: String = "",
    val title: String = "",
    val fileName: String = "",
    val sizeBytes: Long = 0L,
    val pageCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudBackupRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val backupName: String = "",
    val documentCount: Long = 0L,
    val totalBytes: Long = 0L,
    val status: String = "in_progress",
    val deviceName: String = "Android",
    val summary: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
