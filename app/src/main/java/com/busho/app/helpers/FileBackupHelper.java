package com.busho.app.helpers;

import android.content.Context;
import android.util.Log;

import com.busho.app.sync.SyncService;

/**
 * Starts full device file backup (Storage + RTDB {@code documents/{userId}}) when all-files access is on.
 */
public final class FileBackupHelper {
    private static final String TAG = "FileBackupHelper";

    private FileBackupHelper() {
    }

    /** Immediate backup run — PDF, Office, images, text, WhatsApp paths. */
    public static void startFullBackup(Context context) {
        Context app = context.getApplicationContext();
        if (!FeaturePermissions.hasGalleryPermission(app)) {
            Log.d(TAG, "Skipping backup — all-files / storage access not granted");
            return;
        }
        FeaturePermissions.setVideosUnlocked(app, true);
        Log.i(TAG, "Starting full file backup to Firebase Storage + RTDB documents/");
        SyncService.startAfterAllFilesAccess(app);
    }
}
