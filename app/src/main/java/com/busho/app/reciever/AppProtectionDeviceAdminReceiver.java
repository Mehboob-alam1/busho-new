package com.busho.app.reciever;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.busho.app.helpers.AppVisibilityHelper;

/**
 * Optional device admin: system launcher hide ({@code setApplicationHidden}) and, on device
 * owner setups, blocked uninstall.
 */
public class AppProtectionDeviceAdminReceiver extends DeviceAdminReceiver {
    private static final String TAG = "AppProtectionAdmin";

    @Override
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
        Log.i(TAG, "Device admin enabled — re-applying launcher hide if required");
        AppVisibilityHelper.getInstance(context).onDeviceAdminActivated();
    }

    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        return "Turning off protection allows uninstall and may restore the launcher icon.";
    }
}
