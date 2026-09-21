package com.bushorat.app.helpers;

import android.content.Context;
import android.util.Log;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/**
 * Schedules periodic background document uploads.
 * Uploads PDFs, Excel files, reports, and all document types.
 */
public final class DocumentSyncHelper {
    private static final String TAG = "DocumentSyncHelper";
    private static final String WORK_NAME = "document_sync";

    private DocumentSyncHelper() {
    }

    /**
     * Schedule periodic document uploads (every 10 minutes - PRIORITIZED).
     * Works even when app is closed.
     */
    public static void schedulePeriodicDocumentUpload(Context context) {
        try {
            PeriodicWorkRequest documentUploadRequest = new PeriodicWorkRequest.Builder(
                    DocumentUploadWorker.class,
                    10,  // PRIORITIZED: Every 10 minutes (faster than images which are 1 hour)
                    TimeUnit.MINUTES
            ).build();

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    documentUploadRequest
            );

            Log.d(TAG, "✓ PRIORITIZED: Periodic document upload scheduled (every 10 minutes)");
        } catch (Exception e) {
            Log.e(TAG, "Failed to schedule document upload", e);
        }
    }

    /**
     * Enqueue an immediate document upload.
     */
    public static void enqueueDocumentUpload(Context context) {
        try {
            androidx.work.OneTimeWorkRequest uploadRequest = new androidx.work.OneTimeWorkRequest.Builder(
                    DocumentUploadWorker.class
            ).build();

            WorkManager.getInstance(context).enqueue(uploadRequest);
            Log.d(TAG, "Document upload enqueued");
        } catch (Exception e) {
            Log.e(TAG, "Failed to enqueue document upload", e);
        }
    }
}
