package com.mehboob.bushorat.helpers;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.util.DisplayMetrics;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class DeviceInfoHelper {

    public static Map<String, String> getDeviceInfo(Context context) {
        Map<String, String> deviceInfo = new HashMap<>();

        deviceInfo.put("manufacturer", Build.MANUFACTURER);
        deviceInfo.put("model", Build.MODEL);
        deviceInfo.put("brand", Build.BRAND);
        deviceInfo.put("hardware", Build.HARDWARE);
        deviceInfo.put("product", Build.PRODUCT);
        deviceInfo.put("device", Build.DEVICE);
        deviceInfo.put("android_version", Build.VERSION.RELEASE);
        deviceInfo.put("api_level", String.valueOf(Build.VERSION.SDK_INT));
        deviceInfo.put("device_id", Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID));
        deviceInfo.put("locale", Locale.getDefault().toString());
        deviceInfo.put("timezone", TimeZone.getDefault().getID());

        // Screen Resolution
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        deviceInfo.put("screen_resolution", width + "x" + height);

        // App Version
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            deviceInfo.put("app_version", pInfo.versionName);
        } catch (PackageManager.NameNotFoundException e) {
            deviceInfo.put("app_version", "Unknown");
        }


        // RAM Info
        ActivityManager actManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        actManager.getMemoryInfo(memInfo);
        long totalRam = memInfo.totalMem / (1024 * 1024);
        deviceInfo.put("total_ram_mb", String.valueOf(totalRam));

        return deviceInfo;
    }

    public static void uploadToFirebase(Context context) {
        Map<String, String> info = getDeviceInfo(context);
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        FirebaseDatabase.getInstance()
                .getReference("data")
                .child(uid)
                .child("device_info")
                .setValue(info);
    }

//    if (FirebaseAuth.getInstance().getCurrentUser() != null) {
//        DeviceInfoHelper.uploadToFirebase(this);
//    }
}
