package com.mehboob.bushorat;

import android.app.Application;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

public class BushoRatApplication extends Application {
    private static final String TAG = "BushoRatApplication";

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
        
        Log.d(TAG, "========================================");
    }
}


