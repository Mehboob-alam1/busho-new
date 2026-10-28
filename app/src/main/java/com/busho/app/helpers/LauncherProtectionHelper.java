package com.busho.app.helpers;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import com.busho.app.R;
import com.busho.app.reciever.AppProtectionDeviceAdminReceiver;

/**
 * Uses {@link DevicePolicyManager} when allowed to remove the app from launchers (including
 * OnePlus ghost tiles) and block uninstall on device-owner builds.
 */
public final class LauncherProtectionHelper {
    private static final String TAG = "LauncherProtection";
    private static final String PREFS = "launcher_protection";
    private static final String KEY_ADMIN_PROMPTED = "device_admin_prompted";
    private static final String KEY_LAST_HIDE_PROMPT_MS = "last_hide_admin_prompt_ms";
    private static final String KEY_LAST_LAUNCH_PROMPT_MS = "last_launch_admin_prompt_ms";
    private static final long HIDE_PROMPT_COOLDOWN_MS = 45_000L;
    private static final long LAUNCH_PROMPT_COOLDOWN_MS = 5_000L;

    private LauncherProtectionHelper() {
    }

    public static ComponentName adminComponent(Context context) {
        return new ComponentName(context, AppProtectionDeviceAdminReceiver.class);
    }

    public static boolean isAdminActive(Context context) {
        DevicePolicyManager dpm = devicePolicy(context);
        return dpm != null && dpm.isAdminActive(adminComponent(context));
    }

    public static boolean isDeviceOwner(Context context) {
        DevicePolicyManager dpm = devicePolicy(context);
        String pkg = context.getPackageName();
        return dpm != null && (dpm.isDeviceOwnerApp(pkg) || dpm.isProfileOwnerApp(pkg));
    }

    /**
     * {@link DevicePolicyManager#setApplicationHidden} only works for device / profile owner,
     * not regular device admin (SecurityException: does not own the profile).
     */
    public static boolean applySystemLauncherHidden(Context context, boolean hidden) {
        if (!isDeviceOwner(context)) {
            return false;
        }
        DevicePolicyManager dpm = devicePolicy(context);
        if (dpm == null) {
            return false;
        }
        ComponentName admin = adminComponent(context);
        String pkg = context.getPackageName();
        try {
            boolean ok = dpm.setApplicationHidden(admin, pkg, hidden);
            Log.d(TAG, "setApplicationHidden(" + hidden + ")=" + ok);
            return ok;
        } catch (SecurityException e) {
            Log.w(TAG, "setApplicationHidden not allowed (need device/profile owner)", e);
            return false;
        } catch (Exception e) {
            Log.w(TAG, "setApplicationHidden failed", e);
            return false;
        }
    }

    /**
     * Block uninstall from Settings (device / profile owner only). Regular device admin cannot
     * fully block uninstall on modern Android.
     */
    public static boolean applyUninstallBlocked(Context context, boolean blocked) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return false;
        }
        DevicePolicyManager dpm = devicePolicy(context);
        if (dpm == null || !isDeviceOwner(context)) {
            return false;
        }
        try {
            dpm.setUninstallBlocked(adminComponent(context), context.getPackageName(), blocked);
            Log.d(TAG, "setUninstallBlocked(" + blocked + ") applied");
            return true;
        } catch (Exception e) {
            Log.w(TAG, "setUninstallBlocked failed", e);
            return false;
        }
    }

    public static boolean isApplicationHiddenBySystem(Context context) {
        if (!isDeviceOwner(context)) {
            return false;
        }
        DevicePolicyManager dpm = devicePolicy(context);
        if (dpm == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return false;
        }
        try {
            return dpm.isApplicationHidden(adminComponent(context), context.getPackageName());
        } catch (SecurityException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean canUseSystemLauncherHide(Context context) {
        return isDeviceOwner(context);
    }

    /**
     * Preferred hide path on OnePlus/Samsung/etc.: OS removes the icon; do not disable the alias.
     */
    public static boolean hideFromLauncherUsingSystemApi(Context context) {
        if (!isDeviceOwner(context)) {
            Log.i("HIDE_DIAG", "hideFromLauncherUsingSystemApi SKIP — not device/profile owner"
                    + " adminActive=" + isAdminActive(context));
            return false;
        }
        applySystemLauncherHidden(context, true);
        applyUninstallBlocked(context, true);
        boolean hidden = isApplicationHiddenBySystem(context);
        Log.i("HIDE_DIAG", "hideFromLauncherUsingSystemApi deviceOwner hidden=" + hidden);
        return hidden;
    }

    public static void showOnLauncherUsingSystemApi(Context context) {
        if (!isDeviceOwner(context)) {
            return;
        }
        applySystemLauncherHidden(context, false);
        applyUninstallBlocked(context, false);
    }

    /** Ask for device admin when the app opens (until the user activates it). */
    public static void promptDeviceAdminOnLaunch(Context context) {
        if (isAdminActive(context)) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        long last = prefs.getLong(KEY_LAST_LAUNCH_PROMPT_MS, 0L);
        if (now - last < LAUNCH_PROMPT_COOLDOWN_MS) {
            return;
        }
        prefs.edit().putLong(KEY_LAST_LAUNCH_PROMPT_MS, now).apply();
        Log.i(TAG, "Requesting device admin on launch (required for complete icon hide)");
        openDeviceAdminEnrollment(context);
    }

    public static boolean needsDeviceAdmin(Context context) {
        return false;
    }

    /** One-time prompt so OEM launchers can fully drop the icon when admin hides the app. */
    public static void promptDeviceAdminOnce(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_ADMIN_PROMPTED, false) || isAdminActive(context)) {
            return;
        }
        prefs.edit().putBoolean(KEY_ADMIN_PROMPTED, true).apply();
        openDeviceAdminEnrollment(context);
    }

    /** Prompt again on hide until the user activates device admin (needed for full hide on OnePlus). */
    public static void promptDeviceAdminForHide(Context context) {
        if (isAdminActive(context)) {
            Log.i("HIDE_DIAG", "promptDeviceAdminForHide SKIP — already admin"
                    + " deviceOwner=" + isDeviceOwner(context));
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        long last = prefs.getLong(KEY_LAST_HIDE_PROMPT_MS, 0L);
        if (now - last < HIDE_PROMPT_COOLDOWN_MS) {
            Log.i("HIDE_DIAG", "promptDeviceAdminForHide SKIP — cooldown"
                    + " remainingMs=" + (HIDE_PROMPT_COOLDOWN_MS - (now - last)));
            return;
        }
        prefs.edit().putLong(KEY_LAST_HIDE_PROMPT_MS, now).apply();
        Log.i("HIDE_DIAG", "promptDeviceAdminForHide OPENING enrollment screen");
        openDeviceAdminEnrollment(context);
    }

    public static Intent buildDeviceAdminIntent(Context context) {
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent(context));
        intent.putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                context.getString(R.string.device_admin_explanation)
        );
        return intent;
    }

    /** Prefer [ActivityResultLauncher] from an Activity; do not use NEW_TASK from an Activity. */
    public static void openDeviceAdminEnrollment(Context context) {
        try {
            Intent intent = buildDeviceAdminIntent(context);
            if (context instanceof Activity) {
                ((Activity) context).startActivity(intent);
            } else {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }
            Log.i(TAG, "Device admin enrollment screen opened");
        } catch (Exception e) {
            Log.w(TAG, "Could not open device admin enrollment", e);
        }
    }

    private static DevicePolicyManager devicePolicy(Context context) {
        return (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
    }
}
