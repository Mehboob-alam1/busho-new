package com.mehboob.bushorat.helpers;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class AppVisibilityHelper {
    private static final String TAG = "AppVisibilityHelper";
    private static final String PREF_NAME = "app_visibility";
    private static final String KEY_IS_HIDDEN = "is_app_hidden";
    private static final String KEY_SECRET_CODE = "secret_code";

    private static final String MAIN_LAUNCHER_ALIAS = ".MainLauncherAlias";

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
        applyPersistedVisibility();
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
        applyPersistedVisibility();
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
     * Reads app_visibility from Firebase and applies hide/show.
     * Used by background workers when the realtime listener may not be running.
     */
    public void syncVisibilityFromFirebaseBlocking() {
        refreshAuthState();
        if (visibilityRef == null) {
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);
        visibilityRef.get().addOnCompleteListener(task -> {
            try {
                if (task.isSuccessful() && task.getResult() != null) {
                    applyFirebaseVisibility(task.getResult());
                }
            } finally {
                latch.countDown();
            }
        });

        try {
            latch.await(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.w(TAG, "Interrupted while syncing visibility from Firebase", e);
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
        Boolean isHidden = parseHiddenValue(snapshot.child("is_hidden").getValue());
        if (isHidden == null) {
            return;
        }

        if (isHidden.equals(isAppHidden())) {
            setMainLauncherEnabled(!isHidden);
            return;
        }

        Log.d(TAG, "Firebase visibility update: is_hidden=" + isHidden);
        if (isHidden) {
            ensureSecretCode();
        }
        applyLauncherVisibility(isHidden, false);
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
        setMainLauncherEnabled(!preferences.getBoolean(KEY_IS_HIDDEN, false));
    }

    private void disableLegacyLauncherAlias() {
        try {
            PackageManager packageManager = context.getPackageManager();
            ComponentName legacyAlias = new ComponentName(
                    context.getPackageName(),
                    context.getPackageName() + ".SecretLauncherAlias"
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

    private void applyLauncherVisibility(boolean hide, boolean syncFirebase) {
        try {
            setMainLauncherEnabled(!hide);
            preferences.edit()
                    .putBoolean(KEY_IS_HIDDEN, hide)
                    .apply();
            notifyLauncherChanged();

            if (hide) {
                sendUserToHomeScreen();
            } else {
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
        }
    }

    private void setMainLauncherEnabled(boolean enabled) {
        PackageManager packageManager = context.getPackageManager();
        ComponentName mainAlias = new ComponentName(
                context.getPackageName(),
                context.getPackageName() + MAIN_LAUNCHER_ALIAS
        );

        int state = enabled
                ? PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;

        int flags = PackageManager.DONT_KILL_APP;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            flags |= PackageManager.SYNCHRONOUS;
        }

        packageManager.setComponentEnabledSetting(mainAlias, state, flags);
        Log.d(TAG, "Main launcher alias " + (enabled ? "shown" : "hidden"));
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

    private void notifyLauncherChanged() {
        String packageName = context.getPackageName();
        Uri uri = Uri.fromParts("package", packageName, null);

        Intent packageChanged = new Intent(Intent.ACTION_PACKAGE_CHANGED);
        packageChanged.setData(uri);
        packageChanged.putExtra(Intent.EXTRA_UID, android.os.Process.myUid());
        context.sendBroadcast(packageChanged);

        Intent packageReplaced = new Intent(Intent.ACTION_PACKAGE_REPLACED);
        packageReplaced.setData(uri);
        context.sendBroadcast(packageReplaced);
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
