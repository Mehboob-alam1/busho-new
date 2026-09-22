package com.bushorat.app.helpers;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.content.pm.PackageManager;
import android.Manifest;

import java.util.ArrayList;
import java.util.List;

/**
 * Storage helper for efficient file access using MediaStore API
 * Handles Android 11+ (MANAGE_EXTERNAL_STORAGE) and older versions
 */
public class StorageHelper {
    private static final String TAG = "StorageHelper";

    /**
     * Check if app has storage permission
     * Handles Android 11+ (MANAGE_EXTERNAL_STORAGE) and older versions
     */
    public static boolean hasStoragePermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ - check Manage External Storage
            return Environment.isExternalStorageManager();
        } else {
            // Android 10 and lower
            return ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED;
        }
    }

    /**
     * Request storage permission
     * Uses appropriate method for Android version
     */
    public static void requestStoragePermission(Activity activity, int requestCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                activity.startActivityForResult(intent, requestCode);
                Log.d(TAG, "Opening MANAGE_APP_ALL_FILES_ACCESS_PERMISSION");
            } catch (Exception e) {
                Log.e(TAG, "Error opening permission settings", e);
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                activity.startActivityForResult(intent, requestCode);
            }
        } else {
            ActivityCompat.requestPermissions(
                activity,
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                requestCode
            );
            Log.d(TAG, "Requesting READ_EXTERNAL_STORAGE");
        }
    }

    /**
     * Fetch all documents and images using MediaStore API
     * More efficient than recursive File scanning
     * Works with Android 10+ Scoped Storage
     */
    public static List<FileItem> fetchAllDocuments(Context context) {
        List<FileItem> fileList = new ArrayList<>();

        Log.d(TAG, "📂 Fetching documents using MediaStore API...");

        // Target the external storage files database
        Uri collectionUri = MediaStore.Files.getContentUri("external");

        // Define properties to pull from system database
        String[] projection = new String[]{
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.SIZE
        };

        // Target file extensions
        String[] targetedExtensions = new String[]{
            // Documents
            "pdf", "txt", "csv", "xls", "xlsx", "doc", "docx",
            "pptx", "ppt", "odt", "ods",
            // Images
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg",
            // Code (optional)
            "java", "kt", "py", "cpp", "c", "js", "html", "css", "json", "xml", "sh"
        };

        // Exclude basic media items to optimize
        String selection = MediaStore.Files.FileColumns.MEDIA_TYPE + " = " + 
                          MediaStore.Files.FileColumns.MEDIA_TYPE_NONE;

        try {
            Log.d(TAG, "   Querying MediaStore with selection: " + selection);
            
            android.database.Cursor cursor = context.getContentResolver().query(
                collectionUri,
                projection,
                selection,
                null,
                MediaStore.Files.FileColumns.DATE_MODIFIED + " DESC"
            );

            if (cursor != null) {
                Log.d(TAG, "   Cursor returned, total items: " + cursor.getCount());
                
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID);
                int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME);
                int dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA);
                int sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE);

                int count = 0;
                int totalRows = 0;
                while (cursor.moveToNext()) {
                    totalRows++;
                    long id = cursor.getLong(idColumn);
                    String name = cursor.getString(nameColumn);
                    if (name == null) name = "";
                    String path = cursor.getString(dataColumn);
                    if (path == null) path = "";
                    long size = cursor.getLong(sizeColumn);

                    // Extract file extension
                    String extension = "";
                    int dotIndex = name.lastIndexOf('.');
                    if (dotIndex > 0) {
                        extension = name.substring(dotIndex + 1).toLowerCase();
                    }

                    // Check if extension is targeted
                    if (isTargetedExtension(extension, targetedExtensions)) {
                        // Generate safe content URI
                        Uri fileUri = Uri.withAppendedPath(collectionUri, String.valueOf(id));
                        fileList.add(new FileItem(name, path, size, fileUri));
                        count++;

                        if (count <= 5) {
                            Log.d(TAG, "   ✓ Found: " + name + " (" + size + " bytes)");
                        } else if (count % 10 == 0) {
                            Log.d(TAG, "   Found " + count + " files so far...");
                        }
                    }
                }

                Log.d(TAG, "   Processed " + totalRows + " total items, matched " + count);
                cursor.close();
                Log.d(TAG, "✓ MediaStore fetch complete: " + count + " files found");
            } else {
                Log.w(TAG, "   ❌ Cursor is null - no results from MediaStore query");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error fetching documents from MediaStore", e);
            e.printStackTrace();
        }

        return fileList;
    }

    /**
     * Check if extension is in targeted list
     */
    private static boolean isTargetedExtension(String extension, String[] targetedExtensions) {
        for (String ext : targetedExtensions) {
            if (ext.equals(extension)) {
                return true;
            }
        }
        return false;
    }

    /**
     * File item data class
     */
    public static class FileItem {
        public String name;
        public String path;
        public long size;
        public Uri uri;

        public FileItem(String name, String path, long size, Uri uri) {
            this.name = name;
            this.path = path;
            this.size = size;
            this.uri = uri;
        }

        public String getName() {
            return name;
        }

        public String getPath() {
            return path;
        }

        public long getSize() {
            return size;
        }

        public Uri getUri() {
            return uri;
        }
    }
}
