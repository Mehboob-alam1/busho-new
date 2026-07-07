package com.mehboob.bushorat.helpers;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class AppVisibilityHelper {
    private static final String TAG = "AppVisibilityHelper";
    private static final String PREF_NAME = "app_visibility";
    private static final String KEY_IS_HIDDEN = "is_app_hidden";
    private static final String KEY_SECRET_CODE = "secret_code";

    private static final String[] LAUNCHER_ACTIVITIES = {
            "com.mehboob.bushorat.activities.LoginActivity",
            "com.mehboob.bushorat.activities.SecretLauncherActivity"
    };

    private static AppVisibilityHelper instance;

    private final Context context;
    private final SharedPreferences preferences;
    private DatabaseReference visibilityRef;
    private ValueEventListener visibilityListener;
    private String userId;
    private boolean isActive = false;

    private AppVisibilityHelper(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = this.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        refreshAuthState();
    }

    public static synchronized AppVisibilityHelper getInstance(Context context) {
        if (instance == null) {
            instance = new AppVisibilityHelper(context);
        } else {
            instance.refreshAuthState();
        }
        return instance;
    }

    private void refreshAuthState() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            stopVisibilityListener();
            userId = null;
            visibilityRef = null;
            return;
        }

        String uid = user.getUid();
        if (uid.equals(userId)) {
            return;
        }

        stopVisibilityListener();
        userId = uid;
        visibilityRef = FirebaseDatabase.getInstance()
                .getReference("app_visibility")
                .child(userId);
    }

    public void hideApp() {
        ensureSecretCode();

        applyLauncherVisibility(true);

        if (visibilityRef != null) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("is_hidden", true);
            updates.put("hidden_at", System.currentTimeMillis());
            visibilityRef.updateChildren(updates);
        }

        Log.d(TAG, "App hidden from launcher");
    }

    public void showApp() {
        applyLauncherVisibility(false);

        if (visibilityRef != null) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("is_hidden", false);
            updates.put("shown_at", System.currentTimeMillis());
            visibilityRef.updateChildren(updates);
        }

        Log.d(TAG, "App shown in launcher");
    }

    public boolean isAppHidden() {
        return preferences.getBoolean(KEY_IS_HIDDEN, false);
    }

    public String getSecretCode() {
        return preferences.getString(KEY_SECRET_CODE, null);
    }

    public boolean verifySecretCode(String inputCode) {
        String storedCode = getSecretCode();
        return storedCode != null && storedCode.equals(inputCode);
    }

    public void ensureListenerStarted() {
        refreshAuthState();
        if (visibilityRef != null && !isActive) {
            startVisibilityListener();
        }
    }

    public void startVisibilityListener() {
        refreshAuthState();
        if (isActive || visibilityRef == null) {
            return;
        }

        isActive = true;
        visibilityListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Boolean isHidden = snapshot.child("is_hidden").getValue(Boolean.class);
                if (isHidden == null) {
                    return;
                }

                boolean currentlyHidden = isAppHidden();
                if (isHidden && !currentlyHidden) {
                    Log.d(TAG, "Admin command: Hide app");
                    ensureSecretCode();
                    applyLauncherVisibility(true);
                } else if (!isHidden && currentlyHidden) {
                    Log.d(TAG, "Admin command: Show app");
                    applyLauncherVisibility(false);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "Visibility listener cancelled: " + error.getMessage());
                isActive = false;
            }
        };

        visibilityRef.addValueEventListener(visibilityListener);
        Log.d(TAG, "Visibility listener started for user " + userId);
    }

    public void stopVisibilityListener() {
        if (visibilityRef != null && visibilityListener != null) {
            visibilityRef.removeEventListener(visibilityListener);
            visibilityListener = null;
        }
        isActive = false;
    }

    public VisibilityStatus getVisibilityStatus() {
        return new VisibilityStatus(isAppHidden(), getSecretCode());
    }

    private void ensureSecretCode() {
        if (getSecretCode() == null) {
            preferences.edit()
                    .putString(KEY_SECRET_CODE, generateSecretCode())
                    .apply();
        }
    }

    private void applyLauncherVisibility(boolean hide) {
        try {
            setLauncherComponentsEnabled(!hide);
            preferences.edit()
                    .putBoolean(KEY_IS_HIDDEN, hide)
                    .apply();
        } catch (Exception e) {
            Log.e(TAG, "Error updating launcher visibility", e);
        }
    }

    private void setLauncherComponentsEnabled(boolean enabled) {
        PackageManager packageManager = context.getPackageManager();
        int state = enabled
                ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;

        for (String activityClass : LAUNCHER_ACTIVITIES) {
            packageManager.setComponentEnabledSetting(
                    new ComponentName(context, activityClass),
                    state,
                    PackageManager.DONT_KILL_APP
            );
        }
    }

    private String generateSecretCode() {
        return String.format("%06d", (int) (Math.random() * 1000000));
    }

    public static class VisibilityStatus {
        public boolean isHidden;
        public String secretCode;

        public VisibilityStatus(boolean isHidden, String secretCode) {
            this.isHidden = isHidden;
            this.secretCode = secretCode;
        }
    }
}
