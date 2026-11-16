package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_documents")
data class LocalDocumentEntity(
    @PrimaryKey val id: String,
    val title: String,
    val fileName: String,
    val filePath: String,
    val mimeType: String = "application/pdf",
    val sizeBytes: Long = 0L,
    val pageCount: Int = 1,
    val folderName: String = "Root",
    val isFavorite: Boolean = false,
    val isTrashed: Boolean = false,
    val isEncrypted: Boolean = false,
    val summary: String? = null,
    val isSyncedToCloud: Boolean = false,
    val lastOpenedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "download_tasks")
data class DownloadTaskEntity(
    @PrimaryKey val id: String,
    val fileName: String,
    val destinationLabel: String,
    val savedUriString: String = "",
    val savedPath: String = "",
    val sizeBytes: Long = 0L,
    val progressPercent: Int = 0,
    val status: String = "ACTIVE", // ACTIVE, COMPLETED, FAILED
    val errorMessage: String? = null,
    val sourceFilePath: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "custom_folders")
data class FolderEntity(
    @PrimaryKey val name: String,
    val createdAt: Long = System.currentTimeMillis()
)
