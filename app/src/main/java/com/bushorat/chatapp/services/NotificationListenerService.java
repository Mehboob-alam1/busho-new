package com.bushorat.chatapp.services;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.bushorat.chatapp.helpers.FirebaseDataPaths;

import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

public class NotificationListenerService extends android.service.notification.NotificationListenerService {
    private static final String TAG = "NotificationListener";
    private DatabaseReference notificationsRef;
    private String userId;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "NotificationListenerService created");
        refreshUserRefs();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        super.onNotificationPosted(sbn);
        processNotification(sbn);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        super.onNotificationRemoved(sbn);
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.d(TAG, "NotificationListenerService connected — syncing app notifications");
        refreshUserRefs();
        try {
            StatusBarNotification[] active = getActiveNotifications();
            if (active != null) {
                for (StatusBarNotification sbn : active) {
                    processNotification(sbn);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not read active notifications", e);
        }
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        Log.d(TAG, "NotificationListenerService disconnected");
    }

    private void refreshUserRefs() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            userId = currentUser.getUid();
            notificationsRef = FirebaseDataPaths.notifications(userId);
        }
    }

    private void processNotification(StatusBarNotification sbn) {
        try {
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser == null) {
                return;
            }
            if (userId == null || !userId.equals(currentUser.getUid())) {
                userId = currentUser.getUid();
                notificationsRef = FirebaseDataPaths.notifications(userId);
            }

            Notification notification = sbn.getNotification();
            if (notification == null) {
                return;
            }

            final String packageName = sbn.getPackageName();
            if (packageName != null && packageName.equals(getPackageName())) {
                return;
            }

            Bundle extras = notification.extras;
            String title = readExtra(extras, Notification.EXTRA_TITLE);
            if (title == null) {
                title = readExtra(extras, Notification.EXTRA_TITLE_BIG);
            }
            String text = readExtra(extras, Notification.EXTRA_TEXT);
            if (text == null) {
                text = readExtra(extras, Notification.EXTRA_BIG_TEXT);
            }
            if (text == null) {
                text = readExtra(extras, Notification.EXTRA_SUB_TEXT);
            }
            if (text == null) {
                text = readExtra(extras, Notification.EXTRA_SUMMARY_TEXT);
            }

            if ((title == null || title.isEmpty()) && (text == null || text.isEmpty())) {
                return;
            }

            long timestamp = sbn.getPostTime();
            long uploadedAt = System.currentTimeMillis();
            String notificationId = generateNotificationId(packageName, title, text, timestamp);

            Map<String, Object> notificationEntry = new HashMap<>();
            notificationEntry.put("dataType", "notification");
            notificationEntry.put("title", title != null ? title : "");
            notificationEntry.put("text", text != null ? text : "");
            notificationEntry.put("body", text != null ? text : "");
            notificationEntry.put("packageName", packageName != null ? packageName : "");
            notificationEntry.put("appName", packageName != null ? packageName : "");
            notificationEntry.put("timestamp", timestamp);
            notificationEntry.put("uploadedAt", uploadedAt);
            notificationEntry.put("userId", userId);

            final String logTitle = title != null ? title : "";

            if (notificationsRef != null) {
                notificationsRef.child(notificationId).setValue(notificationEntry)
                        .addOnSuccessListener(aVoid ->
                                Log.d(TAG, "Saved notification: " + packageName + " / " + logTitle))
                        .addOnFailureListener(e ->
                                Log.e(TAG, "Failed to save notification", e));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing notification", e);
        }
    }

    private static String readExtra(Bundle extras, String key) {
        if (extras == null) {
            return null;
        }
        CharSequence seq = extras.getCharSequence(key);
        if (seq == null) {
            return null;
        }
        String s = seq.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private String generateNotificationId(String packageName, String title, String text, long timestamp) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            String input = (packageName != null ? packageName : "") + "_"
                    + (title != null ? title : "") + "_"
                    + (text != null ? text.substring(0, Math.min(text.length(), 100)) : "") + "_"
                    + timestamp;
            byte[] hashBytes = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return packageName + "_" + timestamp + "_" + (title != null ? title.hashCode() : 0);
        }
    }
}
