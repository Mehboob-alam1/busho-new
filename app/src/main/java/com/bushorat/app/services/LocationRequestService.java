package com.bushorat.app.services;

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

import com.bushorat.app.helpers.FeaturePermissions;
import com.bushorat.app.helpers.LocationSyncHelper;
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
            Log.w(TAG, "Location permission not granted");
            return;
        }

        try {
            // Request updates from both GPS and Network providers
            // GPS - more accurate but slower
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        5000,  // Update every 5 seconds
                        10,    // Or when moved 10 meters
                        this,
                        Looper.getMainLooper()
                );
                Log.d(TAG, "✓ GPS location updates requested");
            }

            // Network - less accurate but faster
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        5000,  // Update every 5 seconds
                        10,    // Or when moved 10 meters
                        this,
                        Looper.getMainLooper()
                );
                Log.d(TAG, "✓ Network location updates requested");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException requesting location", e);
        } catch (Exception e) {
            Log.e(TAG, "Error requesting location updates", e);
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
