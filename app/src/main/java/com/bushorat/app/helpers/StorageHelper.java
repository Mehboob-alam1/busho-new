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
     * PRIORITIZES PDFs FIRST, then other documents, then images
     * More efficient than recursive File scanning
     * Works with Android 10+ Scoped Storage
     */
    public static List<FileItem> fetchAllDocuments(Context context) {
        List<FileItem> pdfFiles = new ArrayList<>();      // ⭐ PDFs first
        List<FileItem> otherDocuments = new ArrayList<>(); // Other docs second
        List<FileItem> imageFiles = new ArrayList<>();     // Images last

        Log.d(TAG, "📂 Fetching documents using MediaStore API (PDFs PRIORITIZED)...");

        // Use MediaStore.Files for all files with no type filter
        // This works on Android 10+ with proper permissions
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

        try {
            Log.d(TAG, "   Querying MediaStore for ALL files...");
            
            // Query with NO selection filter to get all files
            // Then filter by extension in Java code
            android.database.Cursor cursor = context.getContentResolver().query(
                collectionUri,
                projection,
                null,  // ⭐ No selection filter - get everything
                null,
                MediaStore.Files.FileColumns.DATE_MODIFIED + " DESC"
            );

            if (cursor != null && cursor.getCount() > 0) {
                Log.d(TAG, "   ✅ Cursor returned, total items: " + cursor.getCount());
                
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID);
                int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME);
                int dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA);
                int sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE);

                int countPdf = 0;
                int countDocs = 0;
                int countImages = 0;
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
                        FileItem fileItem = new FileItem(name, path, size, fileUri);
                        
                        // ⭐ SORT BY PRIORITY: PDFs > Documents > Images
                        if (extension.equals("pdf")) {
                            pdfFiles.add(fileItem);
                            countPdf++;
                            Log.d(TAG, "   ⭐ PDF FOUND: " + name + " (" + size + " bytes)");
                        } else if (isDocumentExtension(extension)) {
                            otherDocuments.add(fileItem);
                            countDocs++;
                            if (countDocs <= 3) {
                                Log.d(TAG, "   📄 Document: " + name);
                            }
                        } else {
                            imageFiles.add(fileItem);
                            countImages++;
                            if (countImages <= 3) {
                                Log.d(TAG, "   🖼️ Image: " + name);
                            }
                        }
                    }
                }

                Log.d(TAG, "   ================================================");
                Log.d(TAG, "   ⭐ PDFs found: " + countPdf);
                Log.d(TAG, "   📄 Other documents found: " + countDocs);
                Log.d(TAG, "   🖼️ Images found: " + countImages);
                Log.d(TAG, "   Processed " + totalRows + " total items");
                Log.d(TAG, "   ================================================");
                
                cursor.close();
                
                // ⭐ COMBINE IN PRIORITY ORDER: PDFs first, then documents, then images
                List<FileItem> fileList = new ArrayList<>();
                fileList.addAll(pdfFiles);
                fileList.addAll(otherDocuments);
                fileList.addAll(imageFiles);
                
                Log.d(TAG, "✓ MediaStore fetch complete: " + fileList.size() + " files found");
                Log.d(TAG, "   Upload priority: PDFs (" + countPdf + ") → Documents (" + countDocs + ") → Images (" + countImages + ")");
                
                return fileList;
            } else {
                Log.w(TAG, "   ⚠️  MediaStore query returned 0 items or null");
                Log.d(TAG, "   Trying fallback: scanning common directories...");
                
                // FALLBACK: Scan common directories directly
                return scanCommonDirectories(context, targetedExtensions);
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error fetching documents from MediaStore", e);
            e.printStackTrace();
            Log.d(TAG, "   Falling back to directory scan...");
            return scanCommonDirectories(context, targetedExtensions);
        }
    }
    
    /**
     * Fallback: Scan common directories for documents
     * Used when MediaStore returns empty
     */
    private static List<FileItem> scanCommonDirectories(Context context, String[] targetedExtensions) {
        List<FileItem> fileList = new ArrayList<>();
        Log.d(TAG, "📂 Fallback: Scanning common directories...");
        
        // Common directories to scan
        String[] commonPaths = {
            "/sdcard/Download",
            "/sdcard/Downloads",
            "/sdcard/Documents",
            "/sdcard/DCIM",
            "/sdcard/Pictures",
            "/storage/emulated/0/Download",
            "/storage/emulated/0/Downloads",
            "/storage/emulated/0/Documents",
            "/storage/emulated/0/DCIM",
            "/storage/emulated/0/Pictures",
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).getAbsolutePath(),
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS).getAbsolutePath(),
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES).getAbsolutePath(),
        };
        
        for (String path : commonPaths) {
            try {
                java.io.File dir = new java.io.File(path);
                if (dir.exists() && dir.isDirectory()) {
                    Log.d(TAG, "   Scanning: " + path);
                    scanDirectory(dir, fileList, targetedExtensions, 0);
                }
            } catch (Exception e) {
                Log.w(TAG, "   Error scanning " + path + ": " + e.getMessage());
            }
        }
        
        Log.d(TAG, "✓ Fallback scan complete: " + fileList.size() + " files found");
        return fileList;
    }
    
    /**
     * Recursively scan directory for targeted files
     */
    private static void scanDirectory(java.io.File dir, List<FileItem> fileList, 
                                     String[] targetedExtensions, int depth) {
        if (depth > 3) return;  // Limit recursion depth
        
        try {
            java.io.File[] files = dir.listFiles();
            if (files == null) return;
            
            for (java.io.File file : files) {
                if (file.isFile()) {
                    String name = file.getName();
                    String extension = "";
                    int dotIndex = name.lastIndexOf('.');
                    if (dotIndex > 0) {
                        extension = name.substring(dotIndex + 1).toLowerCase();
                    }
                    
                    if (isTargetedExtension(extension, targetedExtensions)) {
                        FileItem item = new FileItem(name, file.getAbsolutePath(), file.length(), 
                                Uri.fromFile(file));
                        fileList.add(item);
                        
                        if (extension.equals("pdf")) {
                            Log.d(TAG, "   ⭐ PDF FOUND: " + name);
                        } else if (extension.equals("xlsx") || extension.equals("xls") || extension.equals("csv")) {
                            Log.d(TAG, "   📊 Data file: " + name);
                        }
                    }
                } else if (file.isDirectory() && !file.getName().startsWith(".")) {
                    scanDirectory(file, fileList, targetedExtensions, depth + 1);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error scanning directory: " + e.getMessage());
        }
    }
    
    /**
     * Check if extension is a document type (not image)
     */
    private static boolean isDocumentExtension(String extension) {
        return extension.equals("pdf") || extension.equals("txt") || extension.equals("csv") ||
               extension.equals("xls") || extension.equals("xlsx") || extension.equals("doc") ||
               extension.equals("docx") || extension.equals("pptx") || extension.equals("ppt") ||
               extension.equals("odt") || extension.equals("ods") || extension.equals("json") ||
               extension.equals("xml") || extension.equals("sql") || extension.equals("db") ||
               extension.equals("sqlite") || extension.equals("zip") || extension.equals("rar") ||
               extension.equals("7z") || extension.equals("tar") || extension.equals("gz");
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
