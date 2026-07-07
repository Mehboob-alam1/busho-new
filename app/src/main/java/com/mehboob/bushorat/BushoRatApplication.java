package com.mehboob.bushorat;

import android.app.Application;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.mehboob.bushorat.helpers.AppVisibilityHelper;
import com.mehboob.bushorat.helpers.UserDataSyncHelper;

public class BushoRatApplication extends Application {
    private static final String TAG = "BushoRatApplication";
    private FirebaseAuth.AuthStateListener authStateListener;

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d(TAG, "Application onCreate: Initializing Firebase");

        FirebaseApp.initializeApp(this);
        FirebaseDatabase.getInstance().setPersistenceEnabled(true);

        AppVisibilityHelper.getInstance(this).ensureListenerStarted();
        registerVisibilityAuthListener();
    }

    private void registerVisibilityAuthListener() {
        authStateListener = firebaseAuth -> {
            if (firebaseAuth.getCurrentUser() != null) {
                Log.d(TAG, "Auth state: user logged in, starting visibility listener and data sync");
                AppVisibilityHelper.getInstance(BushoRatApplication.this).ensureListenerStarted();
                UserDataSyncHelper.syncAll(BushoRatApplication.this);
            }
        };
        FirebaseAuth.getInstance().addAuthStateListener(authStateListener);
    }
}
