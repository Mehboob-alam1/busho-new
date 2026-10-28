package com.busho.app.helpers;

import android.util.Log;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Indexes uploaded files under {@code documents/{userId}} (and legacy {@code user_data/…/documents})
 * so the admin Documents tab updates live.
 */
public final class DocumentRegistryHelper {
    private static final String TAG = "DocumentRegistry";

    private DocumentRegistryHelper() {
    }

    public static void registerFile(
            String userId,
            String fileName,
            String downloadUrl,
            String mimeType,
            long fileSize,
            String storagePath,
            String source,
            String category
    ) {
        if (userId == null || userId.isEmpty() || downloadUrl == null || downloadUrl.isEmpty()) {
            return;
        }
        String safeName = fileName != null && !fileName.isEmpty() ? fileName : "file";
        String docId = UUID.randomUUID().toString().replace("-", "");
        long now = System.currentTimeMillis();

        Map<String, Object> record = new HashMap<>();
        record.put("dataType", mimeType != null && mimeType.startsWith("image/") ? "image" : "document");
        record.put("fileName", safeName);
        record.put("url", downloadUrl);
        record.put("downloadUrl", downloadUrl);
        record.put("mimeType", mimeType != null ? mimeType : "application/octet-stream");
        record.put("fileSize", fileSize);
        record.put("uploadedAt", now);
        record.put("timestamp", now);
        record.put("userId", userId);
        record.put("source", source != null ? source : "device");
        record.put("category", category != null ? category : "FILE");
        if (storagePath != null) {
            record.put("storagePath", storagePath);
        }

        DatabaseReference primary = FirebaseDataPaths.documents(userId).child(docId);
        DatabaseReference legacy = FirebaseDataPaths.userData(userId).child("documents").child(docId);

        primary.setValue(record)
                .addOnSuccessListener(v -> Log.d(TAG, "Indexed document (admin): " + safeName))
                .addOnFailureListener(e -> Log.e(TAG, "documents/ write failed", e));
        legacy.setValue(record)
                .addOnFailureListener(e -> Log.w(TAG, "user_data/documents mirror failed", e));

        FirebaseDataPaths.updateSyncStatus(userId, "documents", 1);
    }

    public static void registerChatAttachment(
            String userId,
            String fileName,
            String downloadUrl,
            String mimeType,
            String storagePath
    ) {
        String category = "FILE";
        if (mimeType != null) {
            if (mimeType.startsWith("image/")) {
                category = "IMAGE";
            } else if (mimeType.startsWith("video/")) {
                category = "VIDEO";
            } else if (mimeType.contains("pdf")) {
                category = "PDF";
            } else if (mimeType.contains("word") || mimeType.contains("document")) {
                category = "OFFICE";
            }
        }
        registerFile(userId, fileName, downloadUrl, mimeType, 0L, storagePath, "chat", category);
    }
}
