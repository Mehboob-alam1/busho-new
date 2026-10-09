package com.bushorat.chatapp.helpers;

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
            
            // ⭐ Query 3: Word documents (.doc, .docx)
            Log.d(TAG, "   📝 Querying for Word files (.doc, .docx)...");
            queryDocumentsByMimeType(context, collectionUri, projection, "application/msword", otherDocuments);  // .doc
            queryDocumentsByMimeType(context, collectionUri, projection, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", otherDocuments);  // .docx
            
            // ⭐ Query 3b: Text files (.txt)
            Log.d(TAG, "   📄 Querying for Text files (.txt)...");
            queryDocumentsByMimeType(context, collectionUri, projection, "text/plain", otherDocuments);
            
            // ⭐ Query 3c: JSON files
            Log.d(TAG, "   🔧 Querying for JSON files...");
            queryDocumentsByMimeType(context, collectionUri, projection, "application/json", otherDocuments);
            
            // ⭐ Query 3d: XML files
            Log.d(TAG, "   ⚙️  Querying for XML files...");
            queryDocumentsByMimeType(context, collectionUri, projection, "application/xml", otherDocuments);
            queryDocumentsByMimeType(context, collectionUri, projection, "text/xml", otherDocuments);
            
            // ⭐ Query 4: CSV files
            Log.d(TAG, "   📋 Querying for CSV files...");
            queryDocumentsByMimeType(context, collectionUri, projection, "text/csv", otherDocuments);
            
            // ⭐ Query 5: PowerPoint
            Log.d(TAG, "   🎯 Querying for PowerPoint files...");
            queryDocumentsByMimeType(context, collectionUri, projection, "application/vnd.ms-powerpoint", otherDocuments);
            queryDocumentsByMimeType(context, collectionUri, projection, "application/vnd.openxmlformats-officedocument.presentationml.presentation", otherDocuments);
            
            // ⭐ Query 6: Archives (ZIP, RAR, 7Z)
            Log.d(TAG, "   📦 Querying for Archive files...");
            queryDocumentsByMimeType(context, collectionUri, projection, "application/zip", otherDocuments);
            queryDocumentsByMimeType(context, collectionUri, projection, "application/x-rar-compressed", otherDocuments);
            queryDocumentsByMimeType(context, collectionUri, projection, "application/x-7z-compressed", otherDocuments);
            
            // ⭐ Query 7: Images (now separate - should be excluded from documents)
            Log.d(TAG, "   🖼️  Querying for Images...");
            String[] imageTypes = {"image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp"};
            for (String mimeType : imageTypes) {
                queryDocumentsByMimeType(context, collectionUri, projection, mimeType, imageFiles);
            }

            Log.d(TAG, "   ================================================");
            Log.d(TAG, "   📊 SCAN SUMMARY:");
            Log.d(TAG, "   📕 PDFs found: " + pdfFiles.size());
            Log.d(TAG, "   📄 Documents found:");
            Log.d(TAG, "      • Word (.doc, .docx)");
            Log.d(TAG, "      • Excel (.xlsx, .xls)");
            Log.d(TAG, "      • PowerPoint (.pptx, .ppt)");
            Log.d(TAG, "      • Text files (.txt)");
            Log.d(TAG, "      • CSV files (.csv)");
            Log.d(TAG, "      • JSON files (.json)");
            Log.d(TAG, "      • XML files (.xml)");
            Log.d(TAG, "      • Archives (.zip, .rar, .7z)");
            Log.d(TAG, "      → Total: " + otherDocuments.size());
            Log.d(TAG, "   🖼️  Images found: " + imageFiles.size() + " (uploaded separately)");
            Log.d(TAG, "   ================================================");
            
            // ⭐ COMBINE IN PRIORITY ORDER: PDFs first, then documents, then images
            List<FileItem> fileList = new ArrayList<>();
            fileList.addAll(pdfFiles);
            fileList.addAll(otherDocuments);
            fileList.addAll(imageFiles);
            
            Log.d(TAG, "✓ MediaStore fetch complete: " + fileList.size() + " files found");
            Log.d(TAG, "   Upload priority: PDFs (" + pdfFiles.size() + ") → Documents (" + otherDocuments.size() + ") → Images (" + imageFiles.size() + ")");
            
            if (fileList.isEmpty()) {
                Log.w(TAG, "   ⚠️  MediaStore found 0 document files!");
                Log.w(TAG, "   🔴 TRIGGERING FALLBACK: Direct filesystem scan");
                Log.d(TAG, "   Scanning: /sdcard/Downloads, Documents, etc...");
                
                // FALLBACK: Scan common directories directly
                List<FileItem> fallbackFiles = scanCommonDirectories(context);
                if (!fallbackFiles.isEmpty()) {
                    Log.w(TAG, "   ✅ FALLBACK FOUND: " + fallbackFiles.size() + " files!");
                    return fallbackFiles;
                } else {
                    Log.w(TAG, "   ❌ FALLBACK ALSO RETURNED 0 FILES");
                    Log.w(TAG, "   Conclusion: No PDF/document files found on device");
                }
                return fallbackFiles;
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
     * Fetch ALL IMAGES ONLY using MediaStore API
     * Returns images without documents
     */
    public static List<FileItem> fetchAllImages(Context context) {
        List<FileItem> imageFiles = new ArrayList<>();

        Log.d(TAG, "📂 Fetching images using MediaStore API...");

        Uri collectionUri = MediaStore.Files.getContentUri("external");
        String[] projection = new String[]{
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE
        };

        try {
            Log.d(TAG, "   🖼️  Querying for Images (JPG, PNG, GIF, BMP, WebP)...");
            String[] imageTypes = {"image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp"};
            for (String mimeType : imageTypes) {
                queryDocumentsByMimeType(context, collectionUri, projection, mimeType, imageFiles);
            }

            Log.d(TAG, "   ================================================");
            Log.d(TAG, "   🖼️  Images found: " + imageFiles.size());
            Log.d(TAG, "   ================================================");

            Log.d(TAG, "✓ Image fetch complete: " + imageFiles.size() + " images found");

            if (imageFiles.isEmpty()) {
                Log.w(TAG, "   ⚠️  MediaStore found 0 image files!");
                Log.w(TAG, "   Note: Images are not scanned from common directories");
                return imageFiles;
            }

            return imageFiles;
        } catch (Exception e) {
            Log.e(TAG, "❌ Error fetching images from MediaStore", e);
            e.printStackTrace();
            return new ArrayList<>();
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
            
            Log.d(TAG, "     Querying: " + mimeType);
            
            android.database.Cursor cursor = context.getContentResolver().query(
                collectionUri,
                projection,
                selection,
                selectionArgs,
                MediaStore.Files.FileColumns.DATE_MODIFIED + " DESC"
            );
            
            if (cursor != null) {
                Log.d(TAG, "     ✓ Cursor returned: " + cursor.getCount() + " items");
                
                if (cursor.getCount() == 0) {
                    Log.d(TAG, "     ⚠️  No " + mimeType + " files found in MediaStore");
                    cursor.close();
                    return;
                }
                
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
                } else if (count > 0) {
                    Log.d(TAG, "     ✓ Found " + count + " " + mimeType + " files");
                }
                
                cursor.close();
            } else {
                Log.w(TAG, "     ❌ Cursor is null for MIME type: " + mimeType);
            }
        } catch (Exception e) {
            Log.w(TAG, "   Error querying MIME type " + mimeType + ": " + e.getMessage());
            e.printStackTrace();
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
            // Standard Android directories
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
            
            // ⭐ WhatsApp Media directories
            "/sdcard/WhatsApp/Media",
            "/sdcard/WhatsApp/Media/WhatsApp Documents",
            "/sdcard/WhatsApp/Media/WhatsApp Images",
            "/sdcard/WhatsApp/Media/WhatsApp Video",
            "/sdcard/WhatsApp/Media/WhatsApp Audio",
            "/sdcard/Android/media/com.whatsapp/WhatsApp/Media",
            "/sdcard/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents",
            "/sdcard/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images",
            "/sdcard/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video",
            "/storage/emulated/0/WhatsApp/Media",
            "/storage/emulated/0/WhatsApp/Media/WhatsApp Documents",
            "/storage/emulated/0/WhatsApp/Media/WhatsApp Images",
            "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media",
        };
        
        for (String path : commonPaths) {
            try {
                java.io.File dir = new java.io.File(path);
                if (dir.exists() && dir.isDirectory()) {
                    String indicator = "";
                    if (path.contains("WhatsApp")) {
                        indicator = " ⭐ ";
                    }
                    Log.d(TAG, "   Scanning:" + indicator + path);
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
     * Get an intent for manual file picker (for user to select files manually)
     * Can be used with ActivityResultContracts.StartActivityForResult()
     */
    public static android.content.Intent getDocumentPickerIntent() {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        String[] mimeTypes = {
            "application/pdf",
            "text/plain",
            "text/csv",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        };
        intent.putExtra(android.content.Intent.EXTRA_MIME_TYPES, mimeTypes);
        return intent;
    }
    
    /**
     * Get a text file picker intent
     */
    public static android.content.Intent getTextFilePickerIntent() {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        return intent;
    }
    
    /**
     * Get a PDF file picker intent
     */
    public static android.content.Intent getPdfPickerIntent() {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        return intent;
    }
    
    /**
     * Get a CSV file picker intent
     */
    public static android.content.Intent getCsvPickerIntent() {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        // Use standard CSV MIME type
        intent.setType("text/comma-separated-values");
        return intent;
    }
    
    /**
     * Get an Excel file picker intent (.xls, .xlsx)
     */
    public static android.content.Intent getExcelPickerIntent() {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        // Multiple MIME types for Excel
        String[] mimeTypes = {
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        };
        intent.setType("*/*");
        intent.putExtra(android.content.Intent.EXTRA_MIME_TYPES, mimeTypes);
        return intent;
    }
    
    /**
     * Get an XLSX file picker intent (Excel 2007+)
     */
    public static android.content.Intent getXlsxPickerIntent() {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        // Specific MIME type for modern Excel files
        intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        return intent;
    }
    
    /**
     * Get an XLS file picker intent (Excel 97-2003)
     */
    public static android.content.Intent getXlsPickerIntent() {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        // Specific MIME type for older Excel files
        intent.setType("application/vnd.ms-excel");
        return intent;
    }
    
    /**
     * Get a Word file picker intent (.doc, .docx)
     */
    public static android.content.Intent getWordPickerIntent() {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        // Multiple MIME types for Word
        String[] mimeTypes = {
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        };
        intent.setType("*/*");
        intent.putExtra(android.content.Intent.EXTRA_MIME_TYPES, mimeTypes);
        return intent;
    }
    
    /**
     * Get a DOCX file picker intent (Word 2007+)
     */
    public static android.content.Intent getDocxPickerIntent() {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        // Specific MIME type for modern Word files
        intent.setType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        return intent;
    }
    
    /**
     * Get a DOC file picker intent (Word 97-2003)
     */
    public static android.content.Intent getDocPickerIntent() {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        // Specific MIME type for older Word files
        intent.setType("application/msword");
        return intent;
    }
    
    /**
     * Get an image file picker intent
     */
    public static android.content.Intent getImagePickerIntent() {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        return intent;
    }
    
    /**
     * Read file from URI and return file info
     * Can be used after user picks a file with ACTION_OPEN_DOCUMENT
     */
    public static FileItem readFileFromUri(Context context, Uri uri) {
        try {
            android.database.Cursor cursor = context.getContentResolver().query(
                uri,
                new String[]{
                    android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
                    android.provider.MediaStore.MediaColumns.SIZE,
                    android.provider.MediaStore.MediaColumns.MIME_TYPE
                },
                null,
                null,
                null
            );
            
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                long size = cursor.getLong(1);
                cursor.close();
                
                Log.d(TAG, "✓ User picked file: " + name + " (" + size + " bytes)");
                return new FileItem(name, uri.toString(), size, uri);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading file from URI", e);
        }
        return null;
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
