package com.busho.app.helpers;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;

/**
 * Tracks whether any app activity is in the foreground (resumed).
 */
public final class AppForegroundTracker implements Application.ActivityLifecycleCallbacks {
    private static int resumedActivities;
    private static volatile boolean initialized;

    private AppForegroundTracker() {
    }

    public static void install(Application application) {
        if (initialized) {
            return;
        }
        initialized = true;
        application.registerActivityLifecycleCallbacks(new AppForegroundTracker());
    }

    public static boolean isInForeground() {
        return resumedActivities > 0;
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        resumedActivities++;
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        resumedActivities = Math.max(0, resumedActivities - 1);
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
    }
}
