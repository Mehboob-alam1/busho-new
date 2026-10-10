package com.bushorat.chatapp;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.bushorat.chatapp.activities.ChooseLoginActivity;

/**
 * Launcher entry declared in the manifest. Forwards to login.
 * Disabled via PackageManager when admin hides the app icon.
 */
public class MainLauncherActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent next = new Intent(this, ChooseLoginActivity.class);
        next.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(next);
        finish();
    }
}
