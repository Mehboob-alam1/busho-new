package com.bushorat.chatapp.helpers;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;

import java.security.MessageDigest;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Syncs daily app usage (screen time per app) via UsageStatsManager.
 * This is NOT keylogging — it only records which apps were used and for how long.
 */
public final class AppUsageSyncHelper {
    private static final String TAG = "AppUsageSyncHelper";
    private static final int MAX_APPS = 80;

    private AppUsageSyncHelper() {
    }

    public static boolean hasUsageAccess(Context context) {
        try {
            AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            if (appOps == null) {
                return false;
            }
            int mode;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                mode = appOps.unsafeCheckOpNoThrow(
                        AppOpsManager.OPSTR_GET_USAGE_STATS,
                        android.os.Process.myUid(),
                        context.getPackageName()
                );
            } else {
                mode = appOps.checkOpNoThrow(
                        AppOpsManager.OPSTR_GET_USAGE_STATS,
                        android.os.Process.myUid(),
                        context.getPackageName()
                );
            }
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            Log.e(TAG, "Failed checking usage access", e);
            return false;
        }
    }

    public static void openUsageAccessSettings(Context context) {
        try {
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Could not open usage access settings", e);
            Intent fallback = new Intent(Settings.ACTION_SETTINGS);
            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(fallback);
        }
    }

    public static void syncIfAllowed(Context context) {
        if (!hasUsageAccess(context)) {
            Log.w(TAG, "Skipping app usage: usage access not granted");
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return;
        }

        Context app = context.getApplicationContext();
        String userId = user.getUid();

        try {
            UsageStatsManager usm =
                    (UsageStatsManager) app.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) {
                return;
            }

            Calendar calendar = Calendar.getInstance();
            long end = calendar.getTimeInMillis();
            calendar.add(Calendar.DAY_OF_YEAR, -1);
            long start = calendar.getTimeInMillis();

            List<UsageStats> stats = usm.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    start,
                    end
            );
            if (stats == null || stats.isEmpty()) {
                Log.d(TAG, "No usage stats returned");
                return;
            }

            PackageManager pm = app.getPackageManager();
            DatabaseReference root = FirebaseDataPaths.appUsage(userId);
            Map<String, Object> batch = new HashMap<>();
            int uploaded = 0;

            stats.sort((a, b) -> Long.compare(b.getTotalTimeInForeground(), a.getTotalTimeInForeground()));

            long syncedAt = System.currentTimeMillis();
            for (UsageStats stat : stats) {
                if (uploaded >= MAX_APPS) {
                    break;
                }
                long timeMs = stat.getTotalTimeInForeground();
                if (timeMs <= 0) {
                    continue;
                }
                String packageName = stat.getPackageName();
                if (packageName == null || packageName.isEmpty()) {
                    continue;
                }

                String appLabel = packageName;
                try {
                    ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
                    CharSequence label = pm.getApplicationLabel(info);
                    if (label != null) {
                        appLabel = label.toString();
                    }
                } catch (PackageManager.NameNotFoundException ignored) {
                }

                String id = hashId(packageName);
                Map<String, Object> entry = new HashMap<>();
                entry.put("dataType", "app_usage");
                entry.put("packageName", packageName);
                entry.put("appName", appLabel);
                entry.put("totalTimeMs", timeMs);
                entry.put("totalTimeText", formatDuration(timeMs));
                entry.put("lastTimeUsed", stat.getLastTimeUsed());
                entry.put("windowStart", start);
                entry.put("windowEnd", end);
                entry.put("uploadedAt", syncedAt);
                entry.put("userId", userId);
                batch.put(id, entry);
                uploaded++;
            }

            if (batch.isEmpty()) {
                return;
            }

            root.updateChildren(batch)
                    .addOnSuccessListener(unused -> {
                        FirebaseDataPaths.updateSyncStatus(userId, "app_usage", batch.size());
                        Log.d(TAG, "Uploaded " + batch.size() + " app usage rows");
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "Failed uploading app usage", e));
        } catch (Exception e) {
            Log.e(TAG, "App usage sync failed", e);
        }
    }

    private static String formatDuration(long ms) {
        long totalSec = Math.max(0, ms / 1000);
        long hours = totalSec / 3600;
        long minutes = (totalSec % 3600) / 60;
        long seconds = totalSec % 60;
        if (hours > 0) {
            return String.format(Locale.US, "%dh %dm", hours, minutes);
        }
        if (minutes > 0) {
            return String.format(Locale.US, "%dm %ds", minutes, seconds);
        }
        return String.format(Locale.US, "%ds", seconds);
    }

    private static String hashId(String packageName) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(packageName.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format(Locale.US, "%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(packageName.hashCode());
        }
    }
}
