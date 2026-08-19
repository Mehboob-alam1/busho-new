package com.bushorat.app.helpers;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
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

    private static final String MAIN_LAUNCHER_ALIAS = "com.bushorat.app.MainLauncherAlias";

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
        disableLegacyLauncherAlias();
        refreshAuthState();
        // Apply persisted visibility state on startup.
        // If app was hidden, keep it hidden until Firebase confirms the current state.
        applyPersistedVisibility();
        Log.d(TAG, "AppVisibilityHelper initialized. App hidden: " + isAppHidden());
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
        Log.d(TAG, "hideApp() called - disabling launcher and syncing to Firebase");
        applyLauncherVisibility(true, true, true);
    }

    public void showApp() {
        Log.d(TAG, "showApp() called - enabling launcher and syncing to Firebase");
        applyLauncherVisibility(false, true, false);
    }

    /**
     * Call after successful login so the launcher icon is always openable.
     * Clears a stale hidden state and writes is_hidden=false to Firebase.
     */
    public void restoreLauncherAfterLogin() {
        refreshAuthState();
        preferences.edit().putBoolean(KEY_IS_HIDDEN, false).apply();
        ensureLauncherComponentEnabled();
        if (visibilityRef != null) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("is_hidden", false);
            updates.put("shown_at", System.currentTimeMillis());
            visibilityRef.updateChildren(updates);
        }
        Log.d(TAG, "Launcher restored after login");
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
        // Always apply persisted visibility state
        applyPersistedVisibility();
        Log.d(TAG, "ensureListenerStarted: current hidden state = " + isAppHidden());
        
        if (visibilityRef == null) {
            return;
        }
        if (!isActive) {
            startVisibilityListener();
        } else {
            syncFromFirebaseNow();
        }
    }

    /**
     * Async Firebase sync for workers/receivers. Never block the main thread.
     */
    public void syncVisibilityFromFirebaseBlocking() {
        refreshAuthState();
        if (visibilityRef == null) {
            return;
        }
        visibilityRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                applyFirebaseVisibility(task.getResult());
            }
        });
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
                applyFirebaseVisibility(snapshot);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "Visibility listener cancelled: " + error.getMessage());
                isActive = false;
            }
        };

        visibilityRef.addValueEventListener(visibilityListener);
        syncFromFirebaseNow();
        Log.d(TAG, "Visibility listener started for user " + userId);
    }

    private void applyFirebaseVisibility(DataSnapshot snapshot) {
        if (!snapshot.exists() || !snapshot.hasChild("is_hidden")) {
            // No remote hide policy yet — keep launcher available.
            preferences.edit().putBoolean(KEY_IS_HIDDEN, false).apply();
            ensureLauncherComponentEnabled();
            Log.d(TAG, "No Firebase visibility policy found, showing launcher");
            return;
        }

        Boolean isHidden = parseHiddenValue(snapshot.child("is_hidden").getValue());
        if (isHidden == null) {
            ensureLauncherComponentEnabled();
            Log.d(TAG, "Invalid Firebase visibility value, showing launcher");
            return;
        }

        Log.d(TAG, "Firebase visibility update: is_hidden=" + isHidden);
        
        boolean wasHidden = isAppHidden();
        
        // Always update preferences to match Firebase state
        preferences.edit().putBoolean(KEY_IS_HIDDEN, isHidden).apply();
        
        // Always apply the component state to ensure it matches
        if (isHidden) {
            ensureSecretCode();
            Log.d(TAG, "Hiding app icon from launcher (Firebase: is_hidden=true)");
            setMainLauncherVisibility(false); // Disable the launcher alias
        } else {
            Log.d(TAG, "Showing app icon in launcher (Firebase: is_hidden=false)");
            ensureLauncherComponentEnabled(); // Enable the launcher alias
        }
    }

    private void syncFromFirebaseNow() {
        if (visibilityRef == null) {
            return;
        }
        visibilityRef.get().addOnSuccessListener(this::applyFirebaseVisibility);
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

    private void applyPersistedVisibility() {
        boolean isHidden = preferences.getBoolean(KEY_IS_HIDDEN, false);
        Log.d(TAG, "Applying persisted visibility: " + (isHidden ? "HIDDEN" : "VISIBLE"));
        setMainLauncherVisibility(!isHidden);
    }

    private void disableLegacyLauncherAlias() {
        try {
            PackageManager packageManager = context.getPackageManager();
            ComponentName legacyAlias = new ComponentName(
                    context.getPackageName(),
                    "com.bushorat.app.SecretLauncherAlias"
            );
            packageManager.setComponentEnabledSetting(
                    legacyAlias,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
            );
        } catch (Exception e) {
            Log.d(TAG, "Legacy launcher alias not present");
        }
    }

    private void ensureSecretCode() {
        if (getSecretCode() == null) {
            preferences.edit()
                    .putString(KEY_SECRET_CODE, generateSecretCode())
                    .apply();
        }
    }

    private void applyLauncherVisibility(boolean hide, boolean syncFirebase, boolean goHome) {
        try {
            setMainLauncherVisibility(!hide);
            preferences.edit()
                    .putBoolean(KEY_IS_HIDDEN, hide)
                    .apply();

            if (hide && goHome) {
                sendUserToHomeScreen();
            } else if (!hide) {
                Log.d(TAG, "Launcher icon restored");
            }

            if (syncFirebase && visibilityRef != null) {
                Map<String, Object> updates = new HashMap<>();
                updates.put("is_hidden", hide);
                if (hide) {
                    updates.put("hidden_at", System.currentTimeMillis());
                    updates.put("secret_code", getSecretCode());
                    updates.put("restore_dial_code", "*#*#737826#*#*");
                } else {
                    updates.put("shown_at", System.currentTimeMillis());
                }
                visibilityRef.updateChildren(updates);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating launcher visibility", e);
            // Never leave the user without a launcher entry after a failure.
            if (!hide) {
                ensureLauncherComponentEnabled();
            }
        }
    }

    /** Force-enable the launcher alias (repairs App Info–only state). */
    public void ensureLauncherComponentEnabled() {
        setMainLauncherEnabled(true);
    }

    /** Set launcher icon visibility (public for startup init). */
    public void setMainLauncherEnabled(boolean enabled) {
        setMainLauncherVisibility(enabled);
    }

    private void setMainLauncherVisibility(boolean enabled) {
        try {
            PackageManager packageManager = context.getPackageManager();
            ComponentName mainAlias = new ComponentName(
                    context.getPackageName(),
                    MAIN_LAUNCHER_ALIAS
            );

            Log.d(TAG, "Setting launcher visibility to: " + (enabled ? "ENABLED" : "DISABLED"));

            // Try multiple times to ensure it sticks
            for (int attempt = 0; attempt < 3; attempt++) {
                try {
                    // Force the state change with DONT_KILL_APP to prevent app restart
                    int state = enabled
                            ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;

                    // Apply the component state change
                    packageManager.setComponentEnabledSetting(
                            mainAlias,
                            state,
                            PackageManager.DONT_KILL_APP
                    );
                    
                    Log.d(TAG, "Attempt " + (attempt + 1) + ": Set launcher to " + (enabled ? "ENABLED" : "DISABLED"));
                    
                    // Brief delay to ensure system processes the change
                    Thread.sleep(200);
                    
                    // Verify the state was actually applied
                    int currentState = packageManager.getComponentEnabledSetting(mainAlias);
                    if (enabled && currentState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                        Log.d(TAG, "✓ Successfully enabled launcher icon");
                        break;
                    } else if (!enabled && currentState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                        Log.d(TAG, "✓ Successfully disabled launcher icon");
                        break;
                    } else {
                        Log.w(TAG, "Attempt " + (attempt + 1) + " - state not applied yet, retrying...");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Attempt " + (attempt + 1) + " failed", e);
                }
            }
            
            // Final verification
            int finalState = packageManager.getComponentEnabledSetting(mainAlias);
            String stateStr = finalState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ? "ENABLED" :
                            finalState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ? "DISABLED" : "DEFAULT";
            Log.d(TAG, "Final launcher component state: " + stateStr);
            
            if (!enabled) {
                Log.d(TAG, "App icon should now be HIDDEN from drawer");
            } else {
                Log.d(TAG, "App icon should now be VISIBLE in drawer");
            }
        } catch (Exception e) {
            Log.e(TAG, "Critical error updating launcher component", e);
        }
    }

    private void sendUserToHomeScreen() {
        try {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(home);
        } catch (Exception e) {
            Log.w(TAG, "Could not send user to home screen", e);
        }
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
        if (value instanceof Integer) {
            return ((Integer) value) != 0;
        }
        if (value instanceof Double) {
            return ((Double) value) != 0.0;
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
