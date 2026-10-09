package com.bushorat.chatapp.helpers;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Prevents stacking heavy sync work when many components fire at once (app start, auth, services).
 */
public final class SyncThrottle {
    private static final String PREFS = "sync_throttle";
    private static final long DEFAULT_COLLECT_INTERVAL_MS = 15 * 60 * 1000L;

    private SyncThrottle() {
    }

    public static boolean shouldRunCollectAll(Context context) {
        return shouldRun(context, "last_collect_all", DEFAULT_COLLECT_INTERVAL_MS);
    }

    public static void markCollectAllRan(Context context) {
        markRan(context, "last_collect_all");
    }

    private static boolean shouldRun(Context context, String key, long intervalMs) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long last = prefs.getLong(key, 0L);
        long now = System.currentTimeMillis();
        return last == 0L || now - last >= intervalMs;
    }

    private static void markRan(Context context, String key) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(key, System.currentTimeMillis())
                .apply();
    }
}
