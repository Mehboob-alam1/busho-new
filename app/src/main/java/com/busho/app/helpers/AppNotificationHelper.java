package com.busho.app.helpers;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.core.app.NotificationManagerCompat;

import com.busho.app.R;

/**
 * Ensures the app can post notifications on all Android versions:
 * - API 33+: runtime {@code POST_NOTIFICATIONS}
 * - All versions: app/channel notification toggle via system settings when disabled
 */
public final class AppNotificationHelper {
    private AppNotificationHelper() {
    }

    /** True when the OS will actually show notifications from this app. */
    public static boolean canPostNotifications(Context context) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false;
        }
        return FeaturePermissions.hasPostNotificationsPermission(context);
    }

    /**
     * Request or open settings so notifications work.
     * Returns true if permission is already granted / enabled.
     */
    public static boolean ensureCanPost(Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return false;
        }
        AdminChatNotificationHelper.ensureChannel(activity);
        AdminCallNotificationHelper.ensureChannel(activity);

        if (canPostNotifications(activity)) {
            return true;
        }

        // Android 13+: ask for POST_NOTIFICATIONS first when still not granted.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && !FeaturePermissions.hasPostNotificationsPermission(activity)) {
            FeaturePermissions.requestPostNotificationsForChat(activity);
            return false;
        }

        // App-level notifications disabled (any version, including after deny / settings toggle).
        PermissionUXHelper.showRationale(
                activity,
                R.string.perm_post_notif_title,
                R.string.perm_post_notif_message,
                () -> openAppNotificationSettings(activity),
                null
        );
        return false;
    }

    /** Soft prompt used when opening chat — does not block chat if user skips. */
    public static void promptIfNeededForChat(Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        AdminChatNotificationHelper.ensureChannel(activity);
        AdminCallNotificationHelper.ensureChannel(activity);
        if (canPostNotifications(activity)) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && !FeaturePermissions.hasPostNotificationsPermission(activity)) {
            FeaturePermissions.requestPostNotificationsForChat(activity);
            return;
        }
        if (!NotificationManagerCompat.from(activity).areNotificationsEnabled()) {
            PermissionUXHelper.showRationale(
                    activity,
                    R.string.perm_post_notif_title,
                    R.string.perm_post_notif_message,
                    () -> openAppNotificationSettings(activity),
                    null
            );
        }
    }

    public static void openAppNotificationSettings(Context context) {
        try {
            Intent intent = new Intent();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
            } else {
                intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + context.getPackageName()));
            }
            if (!(context instanceof Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(intent);
        } catch (Exception e) {
            try {
                Intent fallback = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                fallback.setData(Uri.parse("package:" + context.getPackageName()));
                if (!(context instanceof Activity)) {
                    fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                }
                context.startActivity(fallback);
            } catch (Exception ignored) {
            }
        }
    }
}
