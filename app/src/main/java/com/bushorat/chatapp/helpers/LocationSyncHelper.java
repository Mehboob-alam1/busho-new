package com.bushorat.chatapp.helpers;

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
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.w(TAG, "No user logged in");
            return;
        }
        
        String userId = user.getUid();
        Log.d(TAG, "📍 Attempting location upload for user: " + userId);
        
        if (!FeaturePermissions.hasLocationPermission(context)) {
            Log.w(TAG, "❌ Location permission not granted");
            return;
        }

        try {
            LocationManager locationManager =
                    (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (locationManager == null) {
                Log.e(TAG, "❌ LocationManager is null");
                return;
            }

            Location location = null;
            
            // Try GPS first
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                Log.d(TAG, "📡 GPS location: " + (location != null ? "✓ found" : "✗ null"));
            }
            
            // Fallback to network
            if (location == null) {
                location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                Log.d(TAG, "📡 Network location: " + (location != null ? "✓ found" : "✗ null"));
            }
            
            if (location == null) {
                Log.w(TAG, "⚠️  No location available yet");
                Log.w(TAG, "   → Possible reasons:");
                Log.w(TAG, "   1. GPS/Network providers disabled in device settings");
                Log.w(TAG, "   2. Location updates just started (takes 5-30 seconds for first fix)");
                Log.w(TAG, "   3. LocationRequestService may not have started");
                Log.w(TAG, "   → Will retry next sync cycle");
                
                // Try to ensure LocationRequestService is running
                try {
                    com.bushorat.chatapp.services.LocationRequestService.getInstance(context).startLocationUpdates();
                    Log.d(TAG, "   → Requested location updates again (fallback)");
                } catch (Exception e) {
                    Log.e(TAG, "   → Could not start LocationRequestService", e);
                }
                
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
            locationData.put("userId", userId);

            Log.d(TAG, "  Uploading: " + location.getLatitude() + ", " + location.getLongitude());
            
            // Use correct Firebase path
            FirebaseDataPaths.location(userId)
                    .child("latest")
                    .setValue(locationData)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "✓ Location uploaded successfully");
                        FirebaseDataPaths.updateSyncStatus(userId, "location", 1);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "✗ Failed to upload location", e);
                    });
                    
        } catch (SecurityException e) {
            Log.e(TAG, "✗ Location access denied (SecurityException)", e);
        } catch (Exception e) {
            Log.e(TAG, "✗ Location upload error", e);
        }
    }
}
