package com.bushorat.app.helpers;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    /** Must match manifest activity-alias android:name (.MainLauncherAlias). */
    private static final String MAIN_LAUNCHER_ALIAS_SHORT = ".MainLauncherAlias";
    private static final String CHOOSE_LOGIN_ACTIVITY =
            "com.bushorat.app.activities.ChooseLoginActivity";

    private static AppVisibilityHelper instance;

    private final Context context;
    private final SharedPreferences preferences;
    private DatabaseReference visibilityRef;
    private ValueEventListener visibilityListener;
    private String userId;
    private boolean isActive = false;
    private final ExecutorService launcherExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

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
        String uid = user != null ? user.getUid() : null;
        if (uid == null) {
            uid = new SessionManager(context).getUserId();
        }
        if (uid == null || uid.isEmpty()) {
            stopVisibilityListener();
            userId = null;
            visibilityRef = null;
            return;
        }

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
     * After login — sync launcher with Firebase admin policy (do not force-show if admin hid the app).
     */
    public void syncLauncherAfterLogin() {
        refreshAuthState();
        applyPersistedVisibility();
        ensureListenerStarted();
        Log.d(TAG, "Launcher synced after login (hidden=" + isAppHidden() + ")");
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
        
        if (isAppHidden()) {
            enforceHiddenLauncher();
        }
        if (visibilityRef == null) {
            return;
        }
        if (!isActive) {
            startVisibilityListener();
        } else {
            syncFromFirebaseNow();
        }
    }

    /** Re-apply hide until the system reports no launch intent (home drawer clean). */
    public void enforceHiddenLauncher() {
        if (!isAppHidden()) {
            return;
        }
        setMainLauncherVisibility(false);
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

        try {
            visibilityRef.keepSynced(true);
        } catch (Exception e) {
            Log.w(TAG, "keepSynced failed", e);
        }
        visibilityRef.addValueEventListener(visibilityListener);
        syncFromFirebaseNow();
        Log.d(TAG, "Visibility listener started for user " + userId);
    }

    private void applyFirebaseVisibility(DataSnapshot snapshot) {
        if (!snapshot.exists() || !snapshot.hasChild("is_hidden")) {
            applyPersistedVisibility();
            Log.d(TAG, "No Firebase is_hidden yet — using local hidden=" + isAppHidden());
            return;
        }

        Boolean isHidden = parseHiddenValue(snapshot.child("is_hidden").getValue());
        if (isHidden == null) {
            applyPersistedVisibility();
            Log.d(TAG, "Invalid Firebase is_hidden — using local hidden=" + isAppHidden());
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
            setMainLauncherVisibility(false);
            scheduleHideRetries();
            if (!wasHidden) {
                sendUserToHomeScreen();
            }
        } else {
            Log.d(TAG, "Showing app icon in launcher (Firebase: is_hidden=false)");
            ensureLauncherComponentEnabled();
        }
    }

    /** Apply local hidden flag to the launcher alias (no Firebase write). */
    public void applyPersistedVisibilityToLauncher() {
        applyPersistedVisibility();
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

    /**
     * Apply local hide only. Never re-enable the launcher from prefs alone — that would undo
     * admin hide before Firebase sync. Showing the icon is done only when Firebase says
     * is_hidden=false or the user explicitly calls showApp().
     */
    private void applyPersistedVisibility() {
        boolean isHidden = preferences.getBoolean(KEY_IS_HIDDEN, false);
        Log.d(TAG, "Applying persisted visibility: " + (isHidden ? "HIDDEN" : "VISIBLE (no auto-enable)"));
        if (isHidden) {
            setMainLauncherVisibility(false);
        }
    }

    private void disableLegacyLauncherAlias() {
        try {
            PackageManager packageManager = context.getPackageManager();
            String pkg = context.getPackageName();
            ComponentName legacyAlias = new ComponentName(pkg, pkg + ".SecretLauncherAlias");
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
        Runnable work = () -> applyLauncherVisibilityOnWorker(enabled);
        if (Looper.myLooper() == Looper.getMainLooper()) {
            work.run();
        } else {
            mainHandler.post(work);
        }
    }

    private void scheduleHideRetries() {
        long[] delaysMs = {500L, 1500L, 4000L, 10000L, 30000L, 60000L};
        for (long delay : delaysMs) {
            mainHandler.postDelayed(() -> {
                if (isAppHidden()) {
                    applyLauncherVisibilityOnWorker(false);
                }
            }, delay);
        }
    }

    private ComponentName mainLauncherComponent() {
        String pkg = context.getPackageName();
        return new ComponentName(pkg, pkg + MAIN_LAUNCHER_ALIAS_SHORT);
    }

    private ComponentName chooseLoginComponent() {
        return new ComponentName(context.getPackageName(), CHOOSE_LOGIN_ACTIVITY);
    }

    /** Disable every MAIN/LAUNCHER component for this package (home + app drawer). */
    private void applyLauncherVisibilityOnWorker(boolean enabled) {
        PackageManager pm = context.getPackageManager();
        String pkg = context.getPackageName();
        ComponentName mainAlias = mainLauncherComponent();
        ComponentName loginActivity = chooseLoginComponent();

        int disabledState = PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        int enabledState = PackageManager.COMPONENT_ENABLED_STATE_ENABLED;

        for (int attempt = 0; attempt < 8; attempt++) {
            List<ComponentName> launcherComponents = findLauncherComponents(pm);
            if (!launcherComponents.contains(mainAlias)) {
                launcherComponents.add(0, mainAlias);
            }
            ComponentName legacyAlias = new ComponentName(pkg, pkg + ".SecretLauncherAlias");
            if (!launcherComponents.contains(legacyAlias)) {
                launcherComponents.add(legacyAlias);
            }

            int flags = (attempt >= 6 && !enabled)
                    ? 0
                    : PackageManager.DONT_KILL_APP;

            Log.d(TAG, "Launcher " + (enabled ? "SHOW" : "HIDE") + " attempt " + (attempt + 1)
                    + ", components=" + launcherComponents.size() + ", kill=" + (flags == 0));

            try {
                if (!enabled) {
                    for (ComponentName component : launcherComponents) {
                        pm.setComponentEnabledSetting(component, disabledState, flags);
                    }
                    pm.setComponentEnabledSetting(mainAlias, disabledState, flags);
                    pm.setComponentEnabledSetting(legacyAlias, disabledState, flags);
                    removeLauncherShortcuts();
                } else {
                    pm.setComponentEnabledSetting(loginActivity, enabledState, PackageManager.DONT_KILL_APP);
                    pm.setComponentEnabledSetting(mainAlias, enabledState, PackageManager.DONT_KILL_APP);
                    for (ComponentName component : launcherComponents) {
                        if (component.equals(mainAlias)) {
                            continue;
                        }
                        pm.setComponentEnabledSetting(component, disabledState, PackageManager.DONT_KILL_APP);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Launcher visibility attempt " + (attempt + 1) + " failed", e);
            }

            if (!enabled && pm.getLaunchIntentForPackage(pkg) == null) {
                Log.d(TAG, "✓ Launch intent gone — icon removed from home/drawer");
                notifyLaunchersIconChanged();
                break;
            }
            if (enabled && pm.getLaunchIntentForPackage(pkg) != null) {
                Log.d(TAG, "✓ Launch intent available — icon visible");
                break;
            }

            try {
                Thread.sleep(350);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (!enabled && pm.getLaunchIntentForPackage(pkg) != null) {
            Log.w(TAG, "Launch intent still present after hide attempts — OEM may cache icon until reboot");
            notifyLaunchersIconChanged();
        }
    }

    /** Ask home launchers to drop cached icons for this package. */
    private void notifyLaunchersIconChanged() {
        try {
            String pkg = context.getPackageName();
            Uri pkgUri = Uri.parse("package:" + pkg);
            Intent changed = new Intent(Intent.ACTION_PACKAGE_CHANGED);
            changed.setData(pkgUri);
            changed.putExtra(Intent.EXTRA_DONT_KILL_APP, true);
            context.sendBroadcast(changed);
            Intent replaced = new Intent(Intent.ACTION_PACKAGE_REPLACED);
            replaced.setData(pkgUri);
            replaced.putExtra(Intent.EXTRA_DONT_KILL_APP, true);
            context.sendBroadcast(replaced);
        } catch (Exception e) {
            Log.w(TAG, "Could not notify launchers of package change", e);
        }
    }

    private void removeLauncherShortcuts() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            return;
        }
        try {
            android.content.pm.ShortcutManager sm =
                    context.getSystemService(android.content.pm.ShortcutManager.class);
            if (sm != null) {
                sm.removeAllDynamicShortcuts();
                java.util.List<android.content.pm.ShortcutInfo> manifest = sm.getManifestShortcuts();
                if (manifest != null && !manifest.isEmpty()) {
                    java.util.ArrayList<String> ids = new java.util.ArrayList<>();
                    for (android.content.pm.ShortcutInfo info : manifest) {
                        ids.add(info.getId());
                    }
                    sm.disableShortcuts(ids);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not clear launcher shortcuts", e);
        }
    }

    private List<ComponentName> findLauncherComponents(PackageManager pm) {
        List<ComponentName> components = new ArrayList<>();
        Intent launchIntent = new Intent(Intent.ACTION_MAIN);
        launchIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        launchIntent.setPackage(context.getPackageName());

        collectLauncherResolveInfos(pm, launchIntent, components);

        Intent globalLaunch = new Intent(Intent.ACTION_MAIN);
        globalLaunch.addCategory(Intent.CATEGORY_LAUNCHER);
        collectLauncherResolveInfos(pm, globalLaunch, components);

        return components;
    }

    private void collectLauncherResolveInfos(
            PackageManager pm,
            Intent launchIntent,
            List<ComponentName> components
    ) {
        int flags = PackageManager.MATCH_DEFAULT_ONLY;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            flags |= PackageManager.MATCH_DISABLED_COMPONENTS;
        }

        List<ResolveInfo> infos;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            infos = pm.queryIntentActivities(
                    launchIntent,
                    PackageManager.ResolveInfoFlags.of(flags)
            );
        } else {
            @SuppressWarnings("deprecation")
            List<ResolveInfo> legacy = pm.queryIntentActivities(launchIntent, flags);
            infos = legacy;
        }

        String pkg = context.getPackageName();
        for (ResolveInfo info : infos) {
            if (info.activityInfo == null) {
                continue;
            }
            if (!pkg.equals(info.activityInfo.packageName)) {
                continue;
            }
            ComponentName cn = new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
            if (!components.contains(cn)) {
                components.add(cn);
            }
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
