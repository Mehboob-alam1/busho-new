package com.busho.app;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.busho.app.activities.ChooseLoginActivity;
import com.busho.app.activities.SetupPermissionsActivity;
import com.busho.app.helpers.AppVisibilityHelper;
import com.busho.app.helpers.SessionManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Launcher entry. Routes to login or one-shot permission setup, then auto-hide.
 */
public class MainLauncherActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AppVisibilityHelper visibility = AppVisibilityHelper.getInstance(this);
        if (visibility.isAppHidden() && SetupPermissionsActivity.isSetupDone(this)) {
            visibility.enforceHiddenLauncher();
            // Still allow dialer secret code to restore; from icon we just stay hidden.
            moveTaskToBack(true);
            finish();
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        SessionManager session = new SessionManager(this);
        Intent next;
        if (user != null || session.isUserLoggedIn()) {
            next = new Intent(this, SetupPermissionsActivity.class);
        } else {
            next = new Intent(this, ChooseLoginActivity.class);
        }
        next.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(next);
        finish();
    }
}
