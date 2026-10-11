package com.bushorat.chatapp.helpers;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.bushorat.chatapp.activities.CallActivity;
import com.bushorat.chatapp.activities.IncomingCallActivity;

import java.util.UUID;

/**
 * Incoming admin audio/video calls: notification when background, CallActivity when foreground.
 */
public final class AdminCallWatcher {
    private static final String TAG = "AdminCallWatcher";
    private static final String PREFS = "admin_call_watcher";
    private static final String KEY_LAST_CONN = "last_handled_conn_id";

    private static ValueEventListener listener;
    private static String watchedUserId;

    private AdminCallWatcher() {
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
        AdminCallNotificationHelper.ensureChannel(app);

        listener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    return;
                }
                String status = snapshot.child("status").getValue(String.class);
                String from = snapshot.child("from").getValue(String.class);
                if (!"incoming".equals(status) || !AdminChatHelper.SENDER_ADMIN.equals(from)) {
                    return;
                }
                String callType = snapshot.child("callType").getValue(String.class);
                boolean video = !"audio".equals(callType);
                String connId = snapshot.child("connId").getValue(String.class);
                if (connId == null || connId.isEmpty()) {
                    connId = UUID.randomUUID().toString();
                }
                if (alreadyHandled(app, connId)) {
                    return;
                }
                markHandled(app, connId);

                String callerName = "Support";
                if (CallActivity.isOnCallScreen() || IncomingCallActivity.isVisible()) {
                    Log.d(TAG, "Call screen already open, skip duplicate launch");
                    return;
                }

                if (AppForegroundTracker.isInForeground()) {
                    launchCallActivity(app, uid, connId, video, true);
                } else {
                    AdminCallNotificationHelper.showIncomingCall(app, uid, connId, video, callerName);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.w(TAG, "admin_calls listener cancelled: " + error.getMessage());
            }
        };
        AdminChatHelper.callsRef(uid).addValueEventListener(listener);
    }

    private static boolean alreadyHandled(Context app, String connId) {
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return connId.equals(prefs.getString(KEY_LAST_CONN, ""));
    }

    private static void markHandled(Context app, String connId) {
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_CONN, connId)
                .apply();
    }

    static void launchCallActivity(Context context, String uid, String connId, boolean video, boolean ring) {
        Intent intent = AdminCallNotificationHelper.buildCallIntent(context, uid, connId, video);
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Could not open call activity", e);
            AdminCallNotificationHelper.showIncomingCall(context, uid, connId, video, "Support");
        }
    }

    public static void clearLastHandled(Context context) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_LAST_CONN)
                .apply();
    }

    public static void stop() {
        if (watchedUserId != null && listener != null) {
            AdminChatHelper.callsRef(watchedUserId).removeEventListener(listener);
        }
        listener = null;
        watchedUserId = null;
    }
}
