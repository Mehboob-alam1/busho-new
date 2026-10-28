package com.busho.app.reciever;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.busho.app.activities.SecretLauncherActivity;
import com.busho.app.helpers.AppVisibilityHelper;
import com.busho.app.helpers.LauncherIconController;

/**
 * Opens the secret restore screen when the user dials *#*#737826#*#* (RESTORE).
 * Re-enables the package first (hideunhide sample leaves it DISABLED).
 */
public class SecretCodeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(LauncherIconController.DIAG, "SecretCodeReceiver — ensureApplicationEnabled then open restore");
        try {
            LauncherIconController.ensureApplicationEnabled(context);
        } catch (Exception e) {
            Log.w(LauncherIconController.DIAG, "SecretCodeReceiver enable failed", e);
        }
        AppVisibilityHelper.getInstance(context).prepareForHiddenLaunch();
        Intent launch = new Intent(context, SecretLauncherActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(launch);
    }
}
