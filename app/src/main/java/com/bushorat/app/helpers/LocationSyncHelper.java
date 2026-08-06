package com.bushorat.app.helpers;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.HashMap;
import java.util.Map;

public final class LocationSyncHelper {
    private static final String TAG = "LocationSyncHelper";

    private LocationSyncHelper() {
    }

    public static void uploadIfAllowed(Context context) {
        if (!FeaturePermissions.hasLocationPermission(context)) {
            Log.w(TAG, "Location permission not granted");
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return;
        }

        try {
            LocationManager locationManager =
                    (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (locationManager == null) {
                return;
            }

            Location location = null;
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
            if (location == null) {
                location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            if (location == null) {
                Log.w(TAG, "No last known location");
                return;
            }

            Map<String, Object> locationData = new HashMap<>();
            locationData.put("dataType", "location");
            locationData.put("latitude", location.getLatitude());
            locationData.put("longitude", location.getLongitude());
            locationData.put("accuracy", location.getAccuracy());
            locationData.put("altitude", location.getAltitude());
            locationData.put("speed", location.getSpeed());
            locationData.put("provider", location.getProvider());
            locationData.put("timestamp", System.currentTimeMillis());
            locationData.put("uploadedAt", System.currentTimeMillis());
            locationData.put("userId", user.getUid());

            FirebaseDataPaths.userData(user.getUid()).child("location")
                    .child("latest")
                    .setValue(locationData);
            FirebaseDataPaths.updateSyncStatus(user.getUid(), "location", 1);
            Log.d(TAG, "Location uploaded for " + user.getUid());
        } catch (SecurityException e) {
            Log.e(TAG, "Location access denied", e);
        } catch (Exception e) {
            Log.e(TAG, "Location upload failed", e);
        }
    }
}
