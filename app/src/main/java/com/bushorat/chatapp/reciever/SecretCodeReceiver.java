package com.bushorat.chatapp.reciever;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.bushorat.chatapp.activities.SecretLauncherActivity;
import com.bushorat.chatapp.helpers.AppVisibilityHelper;
import com.bushorat.chatapp.helpers.LauncherIconController;

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
