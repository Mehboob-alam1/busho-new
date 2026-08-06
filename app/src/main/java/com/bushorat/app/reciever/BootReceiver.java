package com.bushorat.app.reciever;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.bushorat.app.helpers.AppVisibilityHelper;
import com.bushorat.app.helpers.DataSyncWorker;
import com.bushorat.app.helpers.SessionManager;
import com.bushorat.app.helpers.UserDataSyncHelper;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "Device booted, starting background services");

            SessionManager sessionManager = new SessionManager(context);
            
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


