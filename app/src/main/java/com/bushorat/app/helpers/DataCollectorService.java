package com.bushorat.app.helpers;

import android.content.Context;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Orchestrates background data collection. All device files (PDF, office, images, etc.)
 * upload only through {@link SyncService}.
 */
public class DataCollectorService {
    private static final String TAG = "DataCollectorService";

    public static void collectAllData(Context context) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.w(TAG, "No user logged in, skipping data collection");
            return;
        }

        Context appContext = context.getApplicationContext();
        if (!SyncThrottle.shouldRunCollectAll(appContext)) {
            Log.d(TAG, "Skipping collectAllData — ran recently");
            return;
        }
        SyncThrottle.markCollectAllRan(appContext);

        String userId = user.getUid();
        Log.d(TAG, "Starting data collection for " + userId);

        collectFileBackup(appContext);
        collectLocation(appContext);
        collectOtherData(appContext);

        Log.d(TAG, "Data collection scheduled (files via SyncService)");
    }

    private static void collectFileBackup(Context context) {
        try {
            if (!FeaturePermissions.hasGalleryPermission(context)) {
                Log.w(TAG, "Skipping file backup: storage permission not granted");
                return;
            }
            Log.d(TAG, "Starting SyncService for all file types");
            FileBackupHelper.startFullBackup(context);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start SyncService", e);
        }
    }

    private static void collectLocation(Context context) {
        try {
            if (!FeaturePermissions.hasLocationPermission(context)) {
                Log.w(TAG, "Skipping location: permission not granted");
                return;
            }
            LocationSyncHelper.uploadIfAllowed(context);
        } catch (Exception e) {
            Log.e(TAG, "Failed to collect location", e);
        }
    }

    private static void collectOtherData(Context context) {
        try {
            UserDataSyncHelper.syncAll(context, false);
        } catch (Exception e) {
            Log.e(TAG, "Failed to collect other data", e);
        }
    }
}
