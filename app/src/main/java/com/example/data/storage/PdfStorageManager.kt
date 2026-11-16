package com.example.data.storage

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.local.DownloadDao
import com.example.data.local.DownloadTaskEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

data class SaveResult(
    val taskId: String,
    val fileName: String,
    val uri: Uri,
    val displayPath: String,
    val sizeBytes: Long
)

class PdfStorageManager(
    private val context: Context,
    private val downloadDao: DownloadDao
) {
    private val workspaceDir: File
        get() = File(context.filesDir, "pdf_workspace").apply { mkdirs() }

    private val tempDir: File
        get() = File(context.cacheDir, "pdf_temp").apply { mkdirs() }

    fun createWorkspaceFile(fileName: String): File {
        val safeName = sanitizeFileName(fileName)
        return resolveUniqueFile(workspaceDir, safeName)
    }

    fun createTempFile(fileName: String): File {
        val safeName = sanitizeFileName(fileName)
        return resolveUniqueFile(tempDir, safeName)
    }

    fun getWorkspaceStorageBytes(): Long {
        return workspaceDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun clearTempCache(): Long {
        val freed = tempDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        tempDir.deleteRecursively()
        tempDir.mkdirs()
        return freed
    }

    suspend fun importUriToWorkspace(uri: Uri, fallbackName: String = "imported.pdf"): File =
        withContext(Dispatchers.IO) {
            val resolvedName = queryDisplayName(uri) ?: fallbackName
            val destFile = createWorkspaceFile(resolvedName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw IOException("Unable to read selected file.")
            if (destFile.length() == 0L) {
                destFile.delete()
                throw IOException("Imported file is empty.")
            }
            destFile
        }

    fun queryDisplayName(uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx)
            }
        }
        return uri.lastPathSegment
    }

    suspend fun saveToPublicDownloads(
        sourceFile: File,
        desiredFileName: String,
        mimeType: String = "application/pdf",
        subfolder: String = "PDF Flow",
        overwriteExisting: Boolean = false,
        notifyUser: Boolean = true
    ): Result<SaveResult> = withContext(Dispatchers.IO) {
        val taskId = "dl_" + UUID.randomUUID().toString().replace("-", "").take(12)
        val cleanName = sanitizeFileName(desiredFileName)
        val totalBytes = sourceFile.length()
        val destLabel = "${Environment.DIRECTORY_DOWNLOADS}/$subfolder"

        try {
            if (!sourceFile.exists() || totalBytes <= 0L) {
                throw IOException("Source file is missing or empty.")
            }
            checkAvailableStorage(totalBytes)

            downloadDao.upsert(
                DownloadTaskEntity(
                    id = taskId,
                    fileName = cleanName,
                    destinationLabel = destLabel,
                    sizeBytes = totalBytes,
                    progressPercent = 10,
                    status = "ACTIVE",
                    sourceFilePath = sourceFile.absolutePath
                )
            )

            val savedUri: Uri
            val finalFileName: String

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$subfolder"
                finalFileName = if (overwriteExisting) cleanName else resolveMediaStoreUniqueName(cleanName, relativePath)
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, finalFileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val itemUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    ?: throw IOException("Failed to create entry in Android Downloads.")

                try {
                    resolver.openOutputStream(itemUri)?.use { out ->
                        copyWithProgress(sourceFile, out, totalBytes) { pct ->
                            downloadDao.upsert(
                                DownloadTaskEntity(
                                    id = taskId,
                                    fileName = finalFileName,
                                    destinationLabel = destLabel,
                                    sizeBytes = totalBytes,
                                    progressPercent = pct,
                                    status = "ACTIVE",
                                    sourceFilePath = sourceFile.absolutePath
                                )
                            )
                        }
                    } ?: throw IOException("Failed to open output stream in Downloads.")

                    val doneValues = ContentValues().apply {
                        put(MediaStore.Downloads.IS_PENDING, 0)
                    }
                    resolver.update(itemUri, doneValues, null, null)
                    savedUri = itemUri
                } catch (e: Exception) {
                    resolver.delete(itemUri, null, null)
                    throw e
                }
            } else {
                @Suppress("DEPRECATION")
                val downloadsRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val targetDir = File(downloadsRoot, subfolder).apply { mkdirs() }
                val targetFile = if (overwriteExisting) {
                    File(targetDir, cleanName)
                } else {
                    resolveUniqueFile(targetDir, cleanName)
                }
                finalFileName = targetFile.name
                FileOutputStream(targetFile).use { out ->
                    copyWithProgress(sourceFile, out, totalBytes) { pct ->
                        downloadDao.upsert(
                            DownloadTaskEntity(
                                id = taskId,
                                fileName = finalFileName,
                                destinationLabel = destLabel,
                                sizeBytes = totalBytes,
                                progressPercent = pct,
                                status = "ACTIVE",
                                sourceFilePath = sourceFile.absolutePath
                            )
                        )
                    }
                }
                savedUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", targetFile)
            }

            downloadDao.upsert(
                DownloadTaskEntity(
                    id = taskId,
                    fileName = finalFileName,
                    destinationLabel = "$destLabel/$finalFileName",
                    savedUriString = savedUri.toString(),
                    savedPath = "$destLabel/$finalFileName",
                    sizeBytes = totalBytes,
                    progressPercent = 100,
                    status = "COMPLETED",
                    sourceFilePath = sourceFile.absolutePath
                )
            )
            if (notifyUser) {
                showCompletionNotification(finalFileName, totalBytes)
            }
            Result.success(
                SaveResult(
                    taskId = taskId,
                    fileName = finalFileName,
                    uri = savedUri,
                    displayPath = "$destLabel/$finalFileName",
                    sizeBytes = totalBytes
                )
            )
        } catch (e: Exception) {
            downloadDao.upsert(
                DownloadTaskEntity(
                    id = taskId,
                    fileName = cleanName,
                    destinationLabel = destLabel,
                    sizeBytes = totalBytes,
                    progressPercent = 0,
                    status = "FAILED",
                    errorMessage = e.localizedMessage ?: "Download failed.",
                    sourceFilePath = sourceFile.absolutePath
                )
            )
            Result.failure(e)
        }
    }

    suspend fun saveToCustomSafUri(
        sourceFile: File,
        targetUri: Uri,
        desiredFileName: String
    ): Result<SaveResult> = withContext(Dispatchers.IO) {
        val taskId = "dl_" + UUID.randomUUID().toString().replace("-", "").take(12)
        val totalBytes = sourceFile.length()
        val displayName = queryDisplayName(targetUri) ?: desiredFileName
        try {
            if (!sourceFile.exists() || totalBytes <= 0L) throw IOException("Source file is missing.")
            checkAvailableStorage(totalBytes)
            context.contentResolver.openOutputStream(targetUri)?.use { out ->
                copyWithProgress(sourceFile, out, totalBytes) { pct ->
                    downloadDao.upsert(
                        DownloadTaskEntity(
                            id = taskId,
                            fileName = displayName,
                            destinationLabel = "Custom Folder (SAF)",
                            sizeBytes = totalBytes,
                            progressPercent = pct,
                            status = "ACTIVE",
                            sourceFilePath = sourceFile.absolutePath
                        )
                    )
                }
            } ?: throw IOException("Cannot write to selected folder.")

            downloadDao.upsert(
                DownloadTaskEntity(
                    id = taskId,
                    fileName = displayName,
                    destinationLabel = "Custom Folder (SAF)",
                    savedUriString = targetUri.toString(),
                    savedPath = targetUri.toString(),
                    sizeBytes = totalBytes,
                    progressPercent = 100,
                    status = "COMPLETED",
                    sourceFilePath = sourceFile.absolutePath
                )
            )
            Result.success(
                SaveResult(
                    taskId = taskId,
                    fileName = displayName,
                    uri = targetUri,
                    displayPath = "Custom Folder/$displayName",
                    sizeBytes = totalBytes
                )
            )
        } catch (e: Exception) {
            downloadDao.upsert(
                DownloadTaskEntity(
                    id = taskId,
                    fileName = displayName,
                    destinationLabel = "Custom Folder (SAF)",
                    sizeBytes = totalBytes,
                    progressPercent = 0,
                    status = "FAILED",
                    errorMessage = e.localizedMessage ?: "Failed to save to chosen folder.",
                    sourceFilePath = sourceFile.absolutePath
                )
            )
            Result.failure(e)
        }
    }

    private suspend fun copyWithProgress(
        source: File,
        out: java.io.OutputStream,
        totalBytes: Long,
        onProgress: suspend (Int) -> Unit
    ) {
        val buffer = ByteArray(16 * 1024)
        var copied = 0L
        var lastPct = -1
        FileInputStream(source).use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                out.write(buffer, 0, read)
                copied += read
                val pct = ((copied * 100) / totalBytes).toInt().coerceIn(1, 99)
                if (pct - lastPct >= 15) {
                    lastPct = pct
                    onProgress(pct)
                }
            }
            out.flush()
        }
    }

    private fun checkAvailableStorage(requiredBytes: Long) {
        val stat = StatFs(context.filesDir.absolutePath)
        val available = stat.availableBytes
        if (available < requiredBytes + 1024 * 1024) {
            throw IOException("Insufficient device storage (${formatBytes(available)} available).")
        }
    }

    private fun resolveMediaStoreUniqueName(fileName: String, relativePath: String): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return fileName
        val dotIdx = fileName.lastIndexOf('.')
        val base = if (dotIdx > 0) fileName.substring(0, dotIdx) else fileName
        val ext = if (dotIdx > 0) fileName.substring(dotIdx) else ""
        var candidate = fileName
        var counter = 1
        while (mediaStoreFileExists(candidate, relativePath) && counter < 100) {
            candidate = "$base ($counter)$ext"
            counter++
        }
        return candidate
    }

    private fun mediaStoreFileExists(fileName: String, relativePath: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} LIKE ?"
        val args = arrayOf(fileName, "$relativePath%")
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads._ID),
            selection,
            args,
            null
        )?.use { return it.count > 0 }
        return false
    }

    private fun resolveUniqueFile(dir: File, fileName: String): File {
        val dotIdx = fileName.lastIndexOf('.')
        val base = if (dotIdx > 0) fileName.substring(0, dotIdx) else fileName
        val ext = if (dotIdx > 0) fileName.substring(dotIdx) else ""
        var candidate = File(dir, fileName)
        var counter = 1
        while (candidate.exists() && counter < 500) {
            candidate = File(dir, "$base ($counter)$ext")
            counter++
        }
        return candidate
    }

    fun getFileProviderUri(file: File): Uri {
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun openExternalUri(uri: Uri, mimeType: String = "application/pdf") {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Open with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun shareFile(file: File, mimeType: String = "application/pdf") {
        val uri = getFileProviderUri(file)
        shareUri(uri, mimeType)
    }

    fun shareUri(uri: Uri, mimeType: String = "application/pdf") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share PDF").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun showCompletionNotification(title: String, sizeBytes: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        val channelId = "pdf_flow_downloads"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(channelId) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "PDF Flow Downloads & Processing",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Saved: $title")
            .setContentText("Saved to Downloads (${formatBytes(sizeBytes)})")
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(title.hashCode(), notification)
    }

    companion object {
        fun sanitizeFileName(raw: String): String {
            return raw.trim().replace(Regex("[/\\\\:*?\"<>|]"), "_").ifBlank { "document.pdf" }
        }

        fun formatBytes(bytes: Long): String {
            if (bytes < 1024L) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024.0) return String.format("%.1f KB", kb)
            val mb = kb / 1024.0
            if (mb < 1024.0) return String.format("%.2f MB", mb)
            return String.format("%.2f GB", mb / 1024.0)
        }
    }
}
