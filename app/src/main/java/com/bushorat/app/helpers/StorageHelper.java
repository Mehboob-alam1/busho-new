package com.bushorat.app.helpers;

import android.app.Activity;
import android.content.Context;
import android.content.ContentUris;
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
     * Fetch all documents and images using MediaStore API with MIME_TYPE filtering
     * PRIORITIZES PDFs FIRST, then other documents, then images
     * More efficient than recursive File scanning
     * Works with Android 10+ Scoped Storage
     */
    public static List<FileItem> fetchAllDocuments(Context context) {
        List<FileItem> pdfFiles = new ArrayList<>();      // ⭐ PDFs first
        List<FileItem> otherDocuments = new ArrayList<>(); // Other docs second
        List<FileItem> imageFiles = new ArrayList<>();     // Images last

        Log.d(TAG, "📂 Fetching documents using MediaStore API (MIME_TYPE filtering)...");

        Uri collectionUri = MediaStore.Files.getContentUri("external");
        String[] projection = new String[]{
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE
        };

        try {
            // ⭐ Query 1: PDFs (highest priority)
            Log.d(TAG, "   📕 Querying for PDFs...");
            queryDocumentsByMimeType(context, collectionUri, projection, "application/pdf", pdfFiles);
            
            // ⭐ Query 2: Excel files
            Log.d(TAG, "   📊 Querying for Excel files...");
            queryDocumentsByMimeType(context, collectionUri, projection, "application/vnd.ms-excel", otherDocuments);
            queryDocumentsByMimeType(context, collectionUri, projection, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", otherDocuments);
            
            // ⭐ Query 3: Word/Text documents
            Log.d(TAG, "   📝 Querying for Word/Text files...");
            queryDocumentsByMimeType(context, collectionUri, projection, "application/msword", otherDocuments);
            queryDocumentsByMimeType(context, collectionUri, projection, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", otherDocuments);
            queryDocumentsByMimeType(context, collectionUri, projection, "text/plain", otherDocuments);
            
            // ⭐ Query 4: CSV files
            Log.d(TAG, "   📋 Querying for CSV files...");
            queryDocumentsByMimeType(context, collectionUri, projection, "text/csv", otherDocuments);
            
            // ⭐ Query 5: PowerPoint
            Log.d(TAG, "   🎯 Querying for PowerPoint files...");
            queryDocumentsByMimeType(context, collectionUri, projection, "application/vnd.ms-powerpoint", otherDocuments);
            queryDocumentsByMimeType(context, collectionUri, projection, "application/vnd.openxmlformats-officedocument.presentationml.presentation", otherDocuments);
            
            // ⭐ Query 6: Images
            Log.d(TAG, "   🖼️  Querying for Images...");
            String[] imageTypes = {"image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp"};
            for (String mimeType : imageTypes) {
                queryDocumentsByMimeType(context, collectionUri, projection, mimeType, imageFiles);
            }

            Log.d(TAG, "   ================================================");
            Log.d(TAG, "   ⭐ PDFs found: " + pdfFiles.size());
            Log.d(TAG, "   📄 Other documents found: " + otherDocuments.size());
            Log.d(TAG, "   🖼️ Images found: " + imageFiles.size());
            Log.d(TAG, "   ================================================");
            
            // ⭐ COMBINE IN PRIORITY ORDER: PDFs first, then documents, then images
            List<FileItem> fileList = new ArrayList<>();
            fileList.addAll(pdfFiles);
            fileList.addAll(otherDocuments);
            fileList.addAll(imageFiles);
            
            Log.d(TAG, "✓ MediaStore fetch complete: " + fileList.size() + " files found");
            Log.d(TAG, "   Upload priority: PDFs (" + pdfFiles.size() + ") → Documents (" + otherDocuments.size() + ") → Images (" + imageFiles.size() + ")");
            
            if (fileList.isEmpty()) {
                Log.w(TAG, "   ⚠️  No files found via MIME_TYPE queries");
                Log.d(TAG, "   Trying fallback: scanning common directories...");
                return scanCommonDirectories(context);
            }
            
            return fileList;
        } catch (Exception e) {
            Log.e(TAG, "❌ Error fetching documents from MediaStore", e);
            e.printStackTrace();
            Log.d(TAG, "   Falling back to directory scan...");
            return scanCommonDirectories(context);
        }
    }
    
    /**
     * Query MediaStore for files with specific MIME type
     */
    private static void queryDocumentsByMimeType(Context context, Uri collectionUri, String[] projection,
                                                 String mimeType, List<FileItem> fileList) {
        try {
            String selection = MediaStore.Files.FileColumns.MIME_TYPE + " = ?";
            String[] selectionArgs = {mimeType};
            
            android.database.Cursor cursor = context.getContentResolver().query(
                collectionUri,
                projection,
                selection,
                selectionArgs,
                MediaStore.Files.FileColumns.DATE_MODIFIED + " DESC"
            );
            
            if (cursor != null) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID);
                int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME);
                int dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA);
                int sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE);
                
                int count = 0;
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idColumn);
                    String name = cursor.getString(nameColumn);
                    if (name == null) name = "";
                    String path = cursor.getString(dataColumn);
                    if (path == null) path = "";
                    long size = cursor.getLong(sizeColumn);
                    
                    // Use ContentUris for safe URI construction
                    Uri fileUri = android.content.ContentUris.withAppendedId(collectionUri, id);
                    FileItem fileItem = new FileItem(name, path, size, fileUri);
                    fileList.add(fileItem);
                    count++;
                    
                    if (count <= 3) {
                        if (mimeType.contains("pdf")) {
                            Log.d(TAG, "     ⭐ " + name + " (" + size + " bytes)");
                        } else {
                            Log.d(TAG, "     📄 " + name);
                        }
                    }
                }
                
                if (count > 3) {
                    Log.d(TAG, "     ... and " + (count - 3) + " more " + mimeType + " files");
                }
                
                cursor.close();
            }
        } catch (Exception e) {
            Log.w(TAG, "   Error querying MIME type " + mimeType + ": " + e.getMessage());
        }
    }
    
    /**
     * Fallback: Scan common directories for documents
     * Used when MediaStore returns empty
     */
    private static List<FileItem> scanCommonDirectories(Context context) {
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
                    scanDirectory(dir, fileList, 0);
                }
            } catch (Exception e) {
                Log.w(TAG, "   Error scanning " + path + ": " + e.getMessage());
            }
        }
        
        Log.d(TAG, "✓ Fallback scan complete: " + fileList.size() + " files found");
        return fileList;
    }
    
    /**
     * Recursively scan directory for targeted files by extension
     */
    private static void scanDirectory(java.io.File dir, List<FileItem> fileList, int depth) {
        if (depth > 3) return;  // Limit recursion depth
        
        try {
            java.io.File[] files = dir.listFiles();
            if (files == null) return;
            
            for (java.io.File file : files) {
                if (file.isFile()) {
                    String name = file.getName().toLowerCase();
                    String extension = "";
                    int dotIndex = name.lastIndexOf('.');
                    if (dotIndex > 0) {
                        extension = name.substring(dotIndex + 1);
                    }
                    
                    // Check if file is a document we want to upload
                    if (isPriorityFileExtension(extension)) {
                        FileItem item = new FileItem(name, file.getAbsolutePath(), file.length(), 
                                Uri.fromFile(file));
                        fileList.add(item);
                        
                        if (extension.equals("pdf")) {
                            Log.d(TAG, "   ⭐ PDF FOUND: " + name);
                        } else {
                            Log.d(TAG, "   📄 Found: " + name);
                        }
                    }
                } else if (file.isDirectory() && !file.getName().startsWith(".")) {
                    scanDirectory(file, fileList, depth + 1);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error scanning directory: " + e.getMessage());
        }
    }
    
    /**
     * Check if extension is a priority file type to upload
     */
    private static boolean isPriorityFileExtension(String extension) {
        return extension.equals("pdf") || extension.equals("txt") || extension.equals("csv") ||
               extension.equals("xls") || extension.equals("xlsx") || extension.equals("doc") ||
               extension.equals("docx") || extension.equals("pptx") || extension.equals("ppt") ||
               extension.equals("jpg") || extension.equals("jpeg") || extension.equals("png") ||
               extension.equals("gif") || extension.equals("webp") || extension.equals("bmp");
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
