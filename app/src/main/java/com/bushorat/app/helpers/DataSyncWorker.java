package com.bushorat.app.helpers;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;

import java.util.concurrent.TimeUnit;

public class DataSyncWorker extends Worker {
    private static final String TAG = "DataSyncWorker";
    private static final String WORK_NAME = "data_sync_work";

    public DataSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        FirebaseApp.initializeApp(context);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Starting data sync...");

        try {
            FirebaseAuth auth = FirebaseAuth.getInstance();
            if (auth.getCurrentUser() == null) {
                Log.e(TAG, "User not logged in");
                return Result.failure();
            }

            Context context = getApplicationContext();
            AppVisibilityHelper.getInstance(context).syncVisibilityFromFirebaseBlocking();
            UserDataSyncHelper.syncAll(context);
            if (FeaturePermissions.hasGalleryPermission(context)) {
                FileBackupHelper.startFullBackup(context);
            }

            Log.d(TAG, "Data sync completed successfully");
            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "Error during data sync", e);
            return Result.retry();
        }
    }

    // Schedule periodic data sync
    public static void schedulePeriodicSync(Context context) {
        PeriodicWorkRequest syncWork = new PeriodicWorkRequest.Builder(
                DataSyncWorker.class,
                30, TimeUnit.MINUTES // Sync every 30 minutes (location sync)
        ).build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncWork
        );

        Log.d(TAG, "✓ Periodic data sync scheduled (every 30 minutes - includes location)");
    }

    // Cancel scheduled sync
    public static void cancelSync(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
        Log.d(TAG, "Data sync cancelled");
    }
}


