package com.busho.app.services;

import android.Manifest;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.busho.app.helpers.FeaturePermissions;
import com.busho.app.helpers.GpsHelper;
import com.busho.app.helpers.LocationSyncHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Continuously requests location updates so getLastKnownLocation() has fresh data.
 * This must run in the foreground or background to keep location flowing.
 */
public class LocationRequestService implements LocationListener {
    private static final String TAG = "LocationRequestService";
    private static LocationRequestService instance = null;
    private LocationManager locationManager;
    private Context context;
    private Handler handler;

    private LocationRequestService(Context context) {
        this.context = context.getApplicationContext();
        this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        this.handler = new Handler(Looper.getMainLooper());
    }

    public static synchronized LocationRequestService getInstance(Context context) {
        if (instance == null) {
            instance = new LocationRequestService(context);
        }
        return instance;
    }

    /**
     * Start requesting location updates
     */
    public void startLocationUpdates() {
        Log.d(TAG, "📍 Starting location update requests");

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.w(TAG, "No user logged in, cannot start location updates");
            return;
        }

        if (!FeaturePermissions.hasLocationPermission(context)) {
            Log.w(TAG, "❌ Location permission not granted - cannot request updates");
            // Log individual permission checks for debugging
            boolean hasFineLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
            boolean hasCoarseLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
            Log.d(TAG, "  ACCESS_FINE_LOCATION: " + hasFineLocation);
            Log.d(TAG, "  ACCESS_COARSE_LOCATION: " + hasCoarseLocation);
            return;
        }

        if (locationManager == null) {
            Log.e(TAG, "❌ LocationManager is null");
            return;
        }

        try {
            boolean hasGPS = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean hasNetwork = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            
            Log.d(TAG, "📡 Provider status: GPS=" + hasGPS + ", Network=" + hasNetwork);
            
            // For Android 11+, show GPS enable prompt if no providers are enabled
            if (!hasGPS && !hasNetwork) {
                Log.w(TAG, "⚠️  No location providers enabled! Prompting user to enable GPS (Android 11+)");
                GpsHelper.showGpsEnableDialog(context);
            }
            
            GpsHelper.logLocationProviderStatus(context);
            
            // Request updates from both GPS and Network providers
            // GPS - more accurate but slower
            if (hasGPS) {
                try {
                    locationManager.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER,
                            5000,  // Update every 5 seconds
                            10,    // Or when moved 10 meters
                            this,
                            Looper.getMainLooper()
                    );
                    Log.d(TAG, "✓ GPS location updates requested (5s interval, 10m threshold)");
                } catch (SecurityException e) {
                    Log.e(TAG, "❌ GPS permission denied", e);
                }
            } else {
                Log.d(TAG, "⚠️  GPS provider not enabled on device");
            }

            // Network - less accurate but faster
            if (hasNetwork) {
                try {
                    locationManager.requestLocationUpdates(
                            LocationManager.NETWORK_PROVIDER,
                            5000,  // Update every 5 seconds
                            10,    // Or when moved 10 meters
                            this,
                            Looper.getMainLooper()
                    );
                    Log.d(TAG, "✓ Network location updates requested (5s interval, 10m threshold)");
                } catch (SecurityException e) {
                    Log.e(TAG, "❌ Network permission denied", e);
                }
            } else {
                Log.d(TAG, "⚠️  Network provider not enabled on device");
            }
            
            if (!hasGPS && !hasNetwork) {
                Log.w(TAG, "⚠️  No location providers enabled! Enable GPS or Network location in Settings");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Unexpected error requesting location updates", e);
        }
    }

    /**
     * Stop requesting location updates
     */
    public void stopLocationUpdates() {
        try {
            locationManager.removeUpdates(this);
            Log.d(TAG, "Location update requests stopped");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping location updates", e);
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location != null) {
            Log.d(TAG, "📍 Location received: " + location.getLatitude() + ", " + location.getLongitude());
            // Location is now available for getLastKnownLocation() calls
        }
    }

    @Override
    public void onProviderEnabled(String provider) {
        Log.d(TAG, "Provider enabled: " + provider);
    }

    @Override
    public void onProviderDisabled(String provider) {
        Log.d(TAG, "Provider disabled: " + provider);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // Deprecated but needed for older API levels
    }
}
