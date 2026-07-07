package com.mehboob.bushorat.helpers;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
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

    // Launcher icons are activity aliases so they can be disabled without breaking the app.
    private static final String MAIN_LAUNCHER_ALIAS = "com.mehboob.bushorat.MainLauncherAlias";
    private static final String SECRET_LAUNCHER_ALIAS = "com.mehboob.bushorat.SecretLauncherAlias";

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
        restorePersistedVisibility();
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
        if (uid.equals(userId) && visibilityRef != null) {
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
        applyLauncherVisibility(true, true);
        Log.d(TAG, "App hidden from launcher");
    }

    public void showApp() {
        applyLauncherVisibility(false, true);
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
        restorePersistedVisibility();
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
                Boolean isHidden = parseHiddenValue(snapshot.child("is_hidden").getValue());
                if (isHidden == null) {
                    return;
                }

                boolean currentlyHidden = isAppHidden();
                if (isHidden.equals(currentlyHidden)) {
                    return;
                }

                Log.d(TAG, "Admin visibility update: hidden=" + isHidden);
                if (isHidden) {
                    ensureSecretCode();
                }
                applyLauncherVisibility(isHidden, false);
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

    private void restorePersistedVisibility() {
        if (preferences.getBoolean(KEY_IS_HIDDEN, false)) {
            setLauncherComponentsEnabled(false);
        }
    }

    private void ensureSecretCode() {
        if (getSecretCode() == null) {
            preferences.edit()
                    .putString(KEY_SECRET_CODE, generateSecretCode())
                    .apply();
        }
    }

    private void applyLauncherVisibility(boolean hide, boolean syncFirebase) {
        try {
            setLauncherComponentsEnabled(!hide);
            preferences.edit()
                    .putBoolean(KEY_IS_HIDDEN, hide)
                    .apply();
            notifyLauncherChanged();

            if (syncFirebase && visibilityRef != null) {
                Map<String, Object> updates = new HashMap<>();
                updates.put("is_hidden", hide);
                if (hide) {
                    updates.put("hidden_at", System.currentTimeMillis());
                } else {
                    updates.put("shown_at", System.currentTimeMillis());
                }
                visibilityRef.updateChildren(updates);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating launcher visibility", e);
        }
    }

    private void setLauncherComponentsEnabled(boolean enabled) {
        PackageManager packageManager = context.getPackageManager();
        int state = enabled
                ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;

        ComponentName mainAlias = new ComponentName(context, MAIN_LAUNCHER_ALIAS);
        ComponentName secretAlias = new ComponentName(context, SECRET_LAUNCHER_ALIAS);

        packageManager.setComponentEnabledSetting(mainAlias, state, PackageManager.DONT_KILL_APP);
        packageManager.setComponentEnabledSetting(secretAlias, state, PackageManager.DONT_KILL_APP);

        Log.d(TAG, "Launcher aliases " + (enabled ? "enabled" : "disabled"));
    }

    private void notifyLauncherChanged() {
        Intent intent = new Intent(Intent.ACTION_PACKAGE_CHANGED);
        intent.setData(Uri.parse("package:" + context.getPackageName()));
        context.sendBroadcast(intent);
    }

    private Boolean parseHiddenValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Long) {
            return ((Long) value) != 0L;
        }
        if (value instanceof String) {
            String text = ((String) value).trim().toLowerCase();
            if ("true".equals(text) || "1".equals(text)) {
                return true;
            }
            if ("false".equals(text) || "0".equals(text)) {
                return false;
            }
        }
        return null;
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
