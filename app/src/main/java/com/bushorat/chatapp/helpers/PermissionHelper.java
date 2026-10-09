package com.bushorat.chatapp.helpers;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class PermissionHelper {

    private final Activity activity;
    private final int requestCode;

    public PermissionHelper(Activity activity, int requestCode) {
        this.activity = activity;
        this.requestCode = requestCode;
    }

    public boolean hasPermissions(String... permissions) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    public void requestPermissions(String... permissions) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            List<String> needed = new ArrayList<>();
            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                    needed.add(permission);
                }
            }

            if (!needed.isEmpty()) {
                ActivityCompat.requestPermissions(activity,
                        needed.toArray(new String[0]), requestCode);
            }
        }
    }

    public boolean handleResult(int requestCode, @NonNull int[] grantResults) {
        if (this.requestCode == requestCode) {
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    return false; // At least one permission was denied
                }
            }
            return true;
        }
        return false;
    }
}
