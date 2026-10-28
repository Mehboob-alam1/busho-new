package com.busho.app.helpers;

import android.app.Activity;
import android.content.DialogInterface;

import androidx.appcompat.app.AlertDialog;

import com.busho.app.R;

/**
 * One rationale dialog at a time — avoids stacking system permission sheets.
 */
public final class PermissionUXHelper {
    private PermissionUXHelper() {
    }

    public static void showRationale(
            Activity activity,
            int titleRes,
            int messageRes,
            Runnable onAllow,
            Runnable onSkip
    ) {
        if (activity.isFinishing()) {
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle(titleRes)
                .setMessage(messageRes)
                .setPositiveButton(R.string.perm_allow, (d, w) -> {
                    if (onAllow != null) {
                        onAllow.run();
                    }
                })
                .setNegativeButton(android.R.string.cancel, (d, w) -> {
                    if (onSkip != null) {
                        onSkip.run();
                    }
                })
                .setOnCancelListener(d -> {
                    if (onSkip != null) {
                        onSkip.run();
                    }
                })
                .show();
    }

    public static void runAfterDismiss(DialogInterface dialog, Activity activity, long delayMs, Runnable next) {
        if (activity == null || next == null) {
            return;
        }
        activity.getWindow().getDecorView().postDelayed(next, delayMs);
    }
}
