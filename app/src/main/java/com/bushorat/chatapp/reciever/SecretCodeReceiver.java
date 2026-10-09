package com.bushorat.chatapp.reciever;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.bushorat.chatapp.activities.SecretLauncherActivity;
import com.bushorat.chatapp.helpers.AppVisibilityHelper;

/**
 * Opens the secret restore screen when the user dials *#*#737826#*#* (RESTORE).
 * No launcher icon is required.
 */
public class SecretCodeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        AppVisibilityHelper.getInstance(context).prepareForHiddenLaunch();
        Intent launch = new Intent(context, SecretLauncherActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(launch);
    }
}
