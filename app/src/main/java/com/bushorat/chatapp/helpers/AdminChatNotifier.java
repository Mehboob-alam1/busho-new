package com.bushorat.chatapp.helpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

/**
 * Shows a sound notification when admin sends a chat message while the app is in background.
 */
public final class AdminChatNotifier {
    private static final String TAG = "AdminChatNotifier";
    private static final String PREFS = "admin_chat_notifier";
    private static final String KEY_LAST_TS = "last_notified_ts";
    private static final String KEY_LAST_SEEN_TS = "last_seen_ts";

    private static ValueEventListener listener;
    private static String watchedUserId;

    private AdminChatNotifier() {
    }

    public static void start(Context context) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String uid = user != null ? user.getUid() : new SessionManager(context).getUserId();
        if (uid == null || uid.isEmpty()) {
            return;
        }
        if (uid.equals(watchedUserId) && listener != null) {
            return;
        }
        stop();
        watchedUserId = uid;
        Context app = context.getApplicationContext();
        listener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    return;
                }
                DataSnapshot latest = null;
                for (DataSnapshot child : snapshot.getChildren()) {
                    latest = child;
                }
                if (latest == null) {
                    return;
                }
                String sender = latest.child("sender").getValue(String.class);
                if (!AdminChatHelper.SENDER_ADMIN.equals(sender)) {
                    return;
                }
                Long ts = latest.child("timestamp").getValue(Long.class);
                long timestamp = ts != null ? ts : 0L;
                SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                long lastSeen = prefs.getLong(KEY_LAST_SEEN_TS, 0L);
                if (timestamp <= lastSeen) {
                    return;
                }
                if (isChatInForeground(app)) {
                    markSeen(app, timestamp);
                    return;
                }
                long lastNotified = prefs.getLong(KEY_LAST_TS, 0L);
                if (timestamp <= lastNotified) {
                    return;
                }
                prefs.edit().putLong(KEY_LAST_TS, timestamp).apply();
                String preview = latest.child("text").getValue(String.class);
                if (preview == null || preview.isEmpty()) {
                    preview = latest.child("fileName").getValue(String.class);
                }
                AdminChatNotificationHelper.showNewMessage(app, preview);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.w(TAG, "listener cancelled: " + error.getMessage());
            }
        };
        AdminChatHelper.messagesRef(uid).orderByChild("timestamp").addValueEventListener(listener);
    }

    public static void markSeen(Context context, long timestamp) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_SEEN_TS, timestamp)
                .apply();
        AdminChatNotificationHelper.cancel(context);
    }

    public static void markSeenFromSnapshot(Context context, DataSnapshot messagesSnap) {
        long max = 0L;
        for (DataSnapshot child : messagesSnap.getChildren()) {
            Long ts = child.child("timestamp").getValue(Long.class);
            if (ts != null && ts > max) {
                max = ts;
            }
        }
        if (max > 0) {
            markSeen(context, max);
        }
    }

    public static void stop() {
        if (watchedUserId != null && listener != null) {
            AdminChatHelper.removeMessagesListener(watchedUserId, listener);
        }
        listener = null;
        watchedUserId = null;
    }

    private static boolean isChatInForeground(Context context) {
        // Best-effort: ChatActivity sets a simple flag via Application would be ideal;
        // use class name from activity manager is heavy. ChatActivity calls markSeen on resume.
        return ChatActivityHolder.isVisible;
    }

    /** Updated by ChatActivity lifecycle. */
    public static final class ChatActivityHolder {
        public static volatile boolean isVisible;

        private ChatActivityHolder() {
        }
    }
}
