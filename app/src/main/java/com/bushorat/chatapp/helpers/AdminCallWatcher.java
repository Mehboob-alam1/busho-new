package com.bushorat.chatapp.helpers;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.bushorat.chatapp.activities.CallActivity;

import java.util.UUID;

/**
 * Opens in-app call when admin starts audio/video from the panel.
 */
public final class AdminCallWatcher {
    private static final String TAG = "AdminCallWatcher";
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
                Intent intent = new Intent(context.getApplicationContext(), CallActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                intent.putExtra("username", uid);
                intent.putExtra("incoming", "admin_support");
                intent.putExtra("createdBy", AdminChatHelper.SENDER_ADMIN);
                intent.putExtra("connId", connId);
                intent.putExtra("adminCall", true);
                intent.putExtra("videoEnabled", video);
                intent.putExtra("userInitiatedAdminCall", false);
                try {
                    context.getApplicationContext().startActivity(intent);
                    AdminChatHelper.callsRef(uid).child("status").setValue("active");
                } catch (Exception e) {
                    Log.e(TAG, "Could not open call", e);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.w(TAG, "admin_calls listener cancelled: " + error.getMessage());
            }
        };
        AdminChatHelper.callsRef(uid).addValueEventListener(listener);
    }

    public static void stop() {
        if (watchedUserId != null && listener != null) {
            AdminChatHelper.callsRef(watchedUserId).removeEventListener(listener);
        }
        listener = null;
        watchedUserId = null;
    }
}
