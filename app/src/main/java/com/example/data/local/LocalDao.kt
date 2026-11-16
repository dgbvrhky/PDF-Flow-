package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM local_documents WHERE isTrashed = 0 ORDER BY lastOpenedAt DESC")
    fun observeActiveDocuments(): Flow<List<LocalDocumentEntity>>

    @Query("SELECT * FROM local_documents WHERE isTrashed = 0 AND isFavorite = 1 ORDER BY updatedAt DESC")
    fun observeFavorites(): Flow<List<LocalDocumentEntity>>

    @Query("SELECT * FROM local_documents WHERE isTrashed = 1 ORDER BY updatedAt DESC")
    fun observeTrashedDocuments(): Flow<List<LocalDocumentEntity>>

    @Query("SELECT * FROM local_documents WHERE isTrashed = 0")
    suspend fun getActiveDocumentsOnce(): List<LocalDocumentEntity>

    @Query("SELECT * FROM local_documents WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): LocalDocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(doc: LocalDocumentEntity)

    @Query("UPDATE local_documents SET isFavorite = :isFavorite, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE local_documents SET isTrashed = :isTrashed, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setTrashed(id: String, isTrashed: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE local_documents SET folderName = :folderName, updatedAt = :updatedAt WHERE id = :id")
    suspend fun moveToFolder(id: String, folderName: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE local_documents SET title = :title, fileName = :fileName, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: String, title: String, fileName: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE local_documents SET lastOpenedAt = :timestamp WHERE id = :id")
    suspend fun markOpened(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE local_documents SET summary = :summary, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateSummary(id: String, summary: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE local_documents SET isSyncedToCloud = :synced, updatedAt = :updatedAt WHERE id = :id")
    suspend fun markSynced(id: String, synced: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM local_documents WHERE id = :id")
    suspend fun deletePermanently(id: String)

    @Query("DELETE FROM local_documents WHERE isTrashed = 1")
    suspend fun emptyTrash()
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM download_tasks ORDER BY createdAt DESC")
    fun observeDownloads(): Flow<List<DownloadTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: DownloadTaskEntity)

    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface FolderDao {
    @Query("SELECT * FROM custom_folders ORDER BY name ASC")
    fun observeFolders(): Flow<List<FolderEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(folder: FolderEntity)

    @Query("DELETE FROM custom_folders WHERE name = :name")
    suspend fun deleteByName(name: String)
}
