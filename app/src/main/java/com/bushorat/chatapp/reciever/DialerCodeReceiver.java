package com.bushorat.chatapp.reciever;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import com.bushorat.chatapp.activities.ChooseLoginActivity;
import com.bushorat.chatapp.activities.DashboardActivity;
import com.bushorat.chatapp.helpers.AppVisibilityHelper;

/**
 * Receiver for dialer secret codes (USSD codes)
 * Launch hidden app by dialing: *#737826#
 * Or use the secret code stored in SharedPreferences
 */
public class DialerCodeReceiver extends BroadcastReceiver {
    private static final String TAG = "DialerCodeReceiver";
    private static final String SECRET_CODE_ACTION = "android.provider.Telephony.SECRET_CODE";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        String action = intent.getAction();
        String data = intent.getDataString();

        Log.d(TAG, "Received action: " + action + ", data: " + data);

        // Handle secret code dialing
        if (SECRET_CODE_ACTION.equals(action) && data != null) {
            String code = data.replace("android_secret_code://", "");
            Log.d(TAG, "Secret code detected: " + code);

            // Check for default code or stored secret code
            if ("737826".equals(code) || isValidSecretCode(context, code)) {
                Log.d(TAG, "Valid secret code entered - launching app");
                launchApp(context);
            }
        }
    }

    /**
     * Check if the entered code matches the stored secret code
     */
    private boolean isValidSecretCode(Context context, String enteredCode) {
        try {
            AppVisibilityHelper helper = AppVisibilityHelper.getInstance(context);
            String storedCode = helper.getSecretCode();
            if (storedCode != null && storedCode.equals(enteredCode)) {
                Log.d(TAG, "Entered code matches stored secret code");
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking secret code", e);
        }
        return false;
    }

    /**
     * Launch the main app activity
     */
    private void launchApp(Context context) {
        try {
            AppVisibilityHelper.getInstance(context).prepareForHiddenLaunch();
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            Class<?> target = user != null ? DashboardActivity.class : ChooseLoginActivity.class;
            Intent intent = new Intent(context, target);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(intent);
            Log.d(TAG, "App launched via dialer code → " + target.getSimpleName());
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch app", e);
        }
    }
}
