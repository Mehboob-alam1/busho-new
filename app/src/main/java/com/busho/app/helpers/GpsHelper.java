package com.busho.app.helpers;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

/**
 * Helper to check GPS status and prompt user to enable it
 */
public class GpsHelper {
    private static final String TAG = "GpsHelper";

    /**
     * Check if GPS is enabled on the device
     */
    public static boolean isGpsEnabled(Context context) {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        return locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    /**
     * Check if Network location is enabled on the device
     */
    public static boolean isNetworkLocationEnabled(Context context) {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        return locationManager != null && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    /**
     * Check if any location provider is enabled
     */
    public static boolean isAnyLocationProviderEnabled(Context context) {
        return isGpsEnabled(context) || isNetworkLocationEnabled(context);
    }

    /**
     * Show GPS enable dialog for Android 11+
     * Called after location permission is granted but GPS is off
     */
    public static void showGpsEnableDialog(Context context) {
        // Only show for Android 11+
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.d(TAG, "Android 10 or lower - skipping GPS prompt");
            return;
        }

        if (isAnyLocationProviderEnabled(context)) {
            Log.d(TAG, "✓ Location providers already enabled");
            return;
        }

        Log.d(TAG, "⚠️  Showing GPS enable dialog for Android 11+");

        new AlertDialog.Builder(context)
                .setTitle("📍 Enable Location Services")
                .setMessage("To collect accurate location data, please enable GPS or Network Location in your device settings.\n\nYou can enable this in Settings → Location")
                .setIcon(android.R.drawable.ic_dialog_info)
                .setPositiveButton("Open Settings", (dialog, which) -> {
                    Log.d(TAG, "User clicked 'Open Settings'");
                    Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                })
                .setNegativeButton("Later", (dialog, which) -> {
                    Log.d(TAG, "User clicked 'Later'");
                    dialog.dismiss();
                })
                .setCancelable(true)
                .show();
    }

    /**
     * Log location provider status
     */
    public static void logLocationProviderStatus(Context context) {
        boolean gpsEnabled = isGpsEnabled(context);
        boolean networkEnabled = isNetworkLocationEnabled(context);
        
        Log.d(TAG, "📊 Location Provider Status:");
        Log.d(TAG, "  GPS Enabled: " + (gpsEnabled ? "✓ YES" : "✗ NO"));
        Log.d(TAG, "  Network Location Enabled: " + (networkEnabled ? "✓ YES" : "✗ NO"));
        Log.d(TAG, "  Any Provider Enabled: " + ((gpsEnabled || networkEnabled) ? "✓ YES" : "✗ NO"));
    }
}
