package com.bushorat.app.helpers

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.util.Log

data class FileItem(
    val name: String,
    val path: String,
    val size: Long,
    val uri: Uri
)

object StorageHelper {
    private const val TAG = "StorageHelper"

    /**
     * Check if app has storage permission
     * Handles Android 11+ (MANAGE_EXTERNAL_STORAGE) and older versions
     */
    fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ - check Manage External Storage
            Environment.isExternalStorageManager()
        } else {
            // Android 10 and lower
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Request storage permission
     * Uses appropriate method for Android version
     */
    fun requestStoragePermission(activity: Activity, requestCode: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                activity.startActivityForResult(intent, requestCode)
                Log.d(TAG, "Opening MANAGE_APP_ALL_FILES_ACCESS_PERMISSION")
            } catch (e: Exception) {
                Log.e(TAG, "Error opening permission settings", e)
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                activity.startActivityForResult(intent, requestCode)
            }
        } else {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE),
                requestCode
            )
            Log.d(TAG, "Requesting READ_EXTERNAL_STORAGE")
        }
    }

    /**
     * Fetch all documents and files using MediaStore API
     * More efficient than recursive File scanning
     * Works with Android 10+ Scoped Storage
     */
    fun fetchAllDocuments(context: Context): List<FileItem> {
        val fileList = mutableListOf<FileItem>()
        
        Log.d(TAG, "📂 Fetching documents using MediaStore API...")

        // Target the external storage files database
        val collectionUri = MediaStore.Files.getContentUri("external")

        // Define properties to pull from system database
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.SIZE
        )

        // Target file extensions
        val targetedExtensions = hashSetOf(
            // Documents
            "pdf", "txt", "csv", "xls", "xlsx", "doc", "docx",
            "pptx", "ppt", "odt", "ods",
            // Images
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg",
            // Code (optional)
            "java", "kt", "py", "cpp", "c", "js", "html", "css", "json", "xml", "sh"
        )

        // Exclude basic media items to optimize
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_NONE}"

        try {
            context.contentResolver.query(
                collectionUri,
                projection,
                selection,
                null,
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)

                var count = 0
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: ""
                    val path = cursor.getString(dataColumn) ?: ""
                    val size = cursor.getLong(sizeColumn)

                    // Extract file extension
                    val extension = name.substringAfterLast('.', "").lowercase()

                    if (targetedExtensions.contains(extension)) {
                        // Generate safe content URI
                        val fileUri = Uri.withAppendedPath(collectionUri, id.toString())

                        fileList.add(FileItem(name, path, size, fileUri))
                        count++
                        
                        if (count % 10 == 0) {
                            Log.d(TAG, "   Found $count files so far...")
                        }
                    }
                }
                
                Log.d(TAG, "✓ MediaStore fetch complete: $count files found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error fetching documents from MediaStore", e)
        }

        return fileList
    }
}
