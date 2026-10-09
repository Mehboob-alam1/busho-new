package com.bushorat.chatapp.helpers;

import android.content.Context;
import android.util.Log;

import com.bushorat.chatapp.sync.SyncService;

/**
 * Legacy entry points — all file backup goes through {@link SyncService}.
 */
public final class DocumentSyncHelper {
    private static final String TAG = "DocumentSyncHelper";

    private DocumentSyncHelper() {
    }

    public static void schedulePeriodicDocumentUpload(Context context) {
        startSync(context, "schedulePeriodicDocumentUpload");
    }

    public static void enqueueDocumentUpload(Context context) {
        startSync(context, "enqueueDocumentUpload");
    }

    private static void startSync(Context context, String caller) {
        try {
            SyncService.startIfNeeded(context);
            Log.d(TAG, caller + " → SyncService");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start SyncService", e);
        }
    }
}
