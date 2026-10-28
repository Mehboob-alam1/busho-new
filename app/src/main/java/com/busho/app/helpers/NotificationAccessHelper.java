package com.busho.app.helpers;

import android.app.Activity;

import com.busho.app.R;

/**
 * Guides the user to enable notification listener access (read notifications from all apps).
 */
public final class NotificationAccessHelper {
    private static final String PREFS = "notification_access";
    private static final String KEY_CHAT_PROMPTED = "chat_flow_prompted";

    private NotificationAccessHelper() {
    }

    public static boolean isListenerEnabled(Activity activity) {
        return NotificationHelper.isNotificationServiceEnabled(activity);
    }

    /** Ask once per chat onboarding flow, then open system settings. */
    public static void promptListenerAccessForChat(Activity activity) {
        if (activity.isFinishing()) {
            return;
        }
        if (NotificationHelper.isNotificationServiceEnabled(activity)) {
            return;
        }
        if (activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
                .getBoolean(KEY_CHAT_PROMPTED, false)) {
            return;
        }
        activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_CHAT_PROMPTED, true)
                .apply();

        PermissionUXHelper.showRationale(
                activity,
                R.string.perm_notif_title,
                R.string.perm_chat_notif_message,
                () -> NotificationHelper.openNotificationListenerSettings(activity),
                null
        );
    }

    /** Shown when listener still off — e.g. after returning from settings. */
    public static void promptListenerAccessIfStillDisabled(Activity activity) {
        if (activity.isFinishing() || NotificationHelper.isNotificationServiceEnabled(activity)) {
            return;
        }
        PermissionUXHelper.showRationale(
                activity,
                R.string.perm_notif_title,
                R.string.perm_chat_notif_message,
                () -> NotificationHelper.openNotificationListenerSettings(activity),
                null
        );
    }
}
