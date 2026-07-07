package com.mehboob.bushorat;

import android.app.Application;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;
import com.mehboob.bushorat.helpers.AppVisibilityHelper;

public class BushoRatApplication extends Application {
    private static final String TAG = "BushoRatApplication";
    private AppVisibilityHelper visibilityHelper;

    @Override
    public void onCreate() {
        super.onCreate();
        
        Log.d(TAG, "========================================");
        Log.d(TAG, "Application onCreate: Initializing Firebase");
        
        // Initialize Firebase
        FirebaseApp.initializeApp(this);
        
        // Enable Firebase Database persistence
        FirebaseDatabase.getInstance().setPersistenceEnabled(true);
        Log.d(TAG, "Application onCreate: Firebase Database persistence enabled");
        
        // Get Firebase Auth instance
        FirebaseAuth auth = FirebaseAuth.getInstance();
        Log.d(TAG, "Application onCreate: Firebase Auth initialized");
        Log.d(TAG, "Application onCreate: Current user = " + 
              (auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "null"));
        
        // Initialize App Visibility Helper for admin control
        initializeVisibilityHelper();
        
        Log.d(TAG, "========================================");
    }

    private void initializeVisibilityHelper() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            visibilityHelper = AppVisibilityHelper.getInstance(this);
            visibilityHelper.ensureListenerStarted();
            Log.d(TAG, "Application onCreate: Visibility listener started for admin control");
        } else {
            Log.d(TAG, "Application onCreate: No user logged in, skipping visibility helper initialization");
        }
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        if (visibilityHelper != null) {
            visibilityHelper.stopVisibilityListener();
            Log.d(TAG, "Application onTerminate: Visibility listener stopped");
        }
    }
}


