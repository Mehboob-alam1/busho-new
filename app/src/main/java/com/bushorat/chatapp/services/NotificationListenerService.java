package com.bushorat.chatapp.services;

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
        
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = auth.getCurrentUser();
        
        if (currentUser != null) {
            userId = currentUser.getUid();
            notificationsRef = FirebaseDataPaths.notifications(userId);
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        super.onNotificationPosted(sbn);
        
        try {
            // Check if user is logged in
            FirebaseAuth auth = FirebaseAuth.getInstance();
            FirebaseUser currentUser = auth.getCurrentUser();
            
            if (currentUser == null) {
                Log.d(TAG, "User not logged in, skipping notification");
                return;
            }

            // Update userId if needed
            if (userId == null || !userId.equals(currentUser.getUid())) {
                userId = currentUser.getUid();
                notificationsRef = FirebaseDataPaths.notifications(userId);
                Log.d(TAG, "Updated userId to: " + userId);
            }

            // Extract notification data
            android.app.Notification notification = sbn.getNotification();
            if (notification == null) {
                Log.d(TAG, "Notification object is null");
                return;
            }

            final String packageName = sbn.getPackageName();
            if (packageName != null && packageName.equals(getPackageName())) {
                return;
            }

            String titleTemp = null;
            String textTemp = null;
            
            // Extract title and text from notification
            CharSequence titleSeq = notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE);
            if (titleSeq != null) {
                titleTemp = titleSeq.toString().trim();
            }
            
            CharSequence textSeq = notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT);
            if (textSeq == null) {
                textSeq = notification.extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT);
            }
            if (textSeq != null) {
                textTemp = textSeq.toString().trim();
            }

            final String title = titleTemp;
            final String text = textTemp;

            // Skip if no meaningful content
            if ((title == null || title.isEmpty()) && (text == null || text.isEmpty())) {
                Log.d(TAG, "Skipping empty notification from: " + packageName);
                return;
            }

            long timestamp = sbn.getPostTime();
            long uploadedAt = System.currentTimeMillis();

            // Generate unique notification ID (using hash of packageName + title + text + timestamp)
            String notificationId = generateNotificationId(packageName, title, text, timestamp);

            // Create notification entry with required format
            Map<String, Object> notificationEntry = new HashMap<>();
            notificationEntry.put("dataType", "notification");
            notificationEntry.put("title", title != null ? title : "");
            notificationEntry.put("text", text != null ? text : "");
            notificationEntry.put("packageName", packageName != null ? packageName : "");
            notificationEntry.put("timestamp", timestamp);
            notificationEntry.put("uploadedAt", uploadedAt);
            notificationEntry.put("userId", userId);

            Log.d(TAG, "Processing notification - Package: " + packageName + ", Title: " + title);

            // Save to Firebase Database with proper error handling
            if (notificationsRef != null) {
                notificationsRef.child(notificationId).setValue(notificationEntry)
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "Notification saved to Firebase: " + packageName + " - " + title);
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to save notification to Firebase: " + packageName, e);
                        });
            } else {
                Log.e(TAG, "notificationsRef is null, cannot save notification");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error processing notification", e);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        super.onNotificationRemoved(sbn);
        // Optional: handle notification removal if needed
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.d(TAG, "NotificationListenerService connected — notifications will sync to user_data/…/notifications");
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            userId = currentUser.getUid();
            notificationsRef = FirebaseDataPaths.notifications(userId);
        }
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        Log.d(TAG, "NotificationListenerService disconnected");
    }

    private String generateNotificationId(String packageName, String title, String text, long timestamp) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            String input = (packageName != null ? packageName : "") + "_" +
                          (title != null ? title : "") + "_" +
                          (text != null ? text.substring(0, Math.min(text.length(), 100)) : "") + "_" +
                          timestamp;
            byte[] hashBytes = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            // Fallback to simple hash
            return String.valueOf((packageName != null ? packageName.hashCode() : 0) + "_" +
                                 timestamp + "_" +
                                 (title != null ? title.hashCode() : 0));
        }
    }
}
