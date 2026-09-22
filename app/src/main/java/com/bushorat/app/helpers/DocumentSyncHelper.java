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
     * ⭐ IMMEDIATE document upload on app start.
     * No delays, no intervals - uploads immediately when called.
     * Works even when app is closed via WorkManager.
     */
    public static void schedulePeriodicDocumentUpload(Context context) {
        try {
            // ⭐ IMMEDIATE: Use OneTimeWorkRequest instead of periodic
            // This uploads documents RIGHT NOW, not on a schedule
            androidx.work.OneTimeWorkRequest documentUploadRequest = 
                new androidx.work.OneTimeWorkRequest.Builder(
                    DocumentUploadWorker.class
                ).build();

            WorkManager.getInstance(context).enqueueUniqueWork(
                    WORK_NAME,
                    androidx.work.ExistingWorkPolicy.REPLACE,  // ⭐ REPLACE - upload now, don't wait
                    documentUploadRequest
            );

            Log.d(TAG, "⭐ IMMEDIATE: Document upload started NOW (no delays, no intervals)");
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
