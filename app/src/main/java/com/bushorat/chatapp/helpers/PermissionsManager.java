package com.bushorat.chatapp.helpers;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class PermissionsManager {
    private Context context;
    private Activity activity;

    public static final String[] ALL_PERMISSIONS = {
            // Storage
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            
            // Camera & Audio
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            
            // Contacts
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.GET_ACCOUNTS,
            
            // Phone
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.CALL_PHONE,
            
            // SMS
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            
            // Location
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            
            // Calendar
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
    };

    public PermissionsManager(Context context, Activity activity) {
        this.context = context;
        this.activity = activity;
    }

    // Check if all permissions are granted
    public boolean areAllPermissionsGranted() {
        for (String permission : getEssentialSyncPermissions()) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public static String[] getEssentialSyncPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return new String[]{
                    Manifest.permission.READ_SMS,
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.READ_CALL_LOG,
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.READ_MEDIA_IMAGES
            };
        }
        return new String[]{
                Manifest.permission.READ_SMS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.READ_EXTERNAL_STORAGE
        };
    }

    /** True when the app can read device files for backup (PDFs, docs, images, WhatsApp). */
    public static boolean hasGalleryPermission(Context context) {
        return StorageHelper.hasStoragePermission(context);
    }

    public List<String> getDeniedEssentialPermissions() {
        List<String> deniedPermissions = new ArrayList<>();
        for (String permission : getEssentialSyncPermissions()) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                deniedPermissions.add(permission);
            }
        }
        return deniedPermissions;
    }

    public void requestEssentialSyncPermissions(int requestCode) {
        List<String> deniedPermissions = getDeniedEssentialPermissions();
        if (!deniedPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(
                    activity,
                    deniedPermissions.toArray(new String[0]),
                    requestCode
            );
        }
    }

    // Get list of denied permissions
    public List<String> getDeniedPermissions() {
        List<String> deniedPermissions = new ArrayList<>();
        for (String permission : ALL_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                deniedPermissions.add(permission);
            }
        }
        return deniedPermissions;
    }

    // Request all permissions
    public void requestAllPermissions(int requestCode) {
        List<String> deniedPermissions = getDeniedPermissions();
        if (!deniedPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(
                    activity,
                    deniedPermissions.toArray(new String[0]),
                    requestCode
            );
        }
    }

    // Request specific permission group
    public void requestStoragePermissions(int requestCode) {
        String[] permissions = {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };
        ActivityCompat.requestPermissions(activity, permissions, requestCode);
    }

    public void requestContactsPermissions(int requestCode) {
        String[] permissions = {
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.WRITE_CONTACTS,
                Manifest.permission.GET_ACCOUNTS
        };
        ActivityCompat.requestPermissions(activity, permissions, requestCode);
    }

    public void requestPhonePermissions(int requestCode) {
        String[] permissions = {
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.CALL_PHONE
        };
        ActivityCompat.requestPermissions(activity, permissions, requestCode);
    }

    public void requestSmsPermissions(int requestCode) {
        String[] permissions = {
                Manifest.permission.READ_SMS,
                Manifest.permission.SEND_SMS,
                Manifest.permission.RECEIVE_SMS
        };
        ActivityCompat.requestPermissions(activity, permissions, requestCode);
    }

    public void requestLocationPermissions(int requestCode) {
        String[] permissions = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        };
        ActivityCompat.requestPermissions(activity, permissions, requestCode);
    }

    public void requestCameraPermissions(int requestCode) {
        String[] permissions = {
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
        };
        ActivityCompat.requestPermissions(activity, permissions, requestCode);
    }

    // Check specific permission groups
    public boolean hasStoragePermissions() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    public boolean hasContactsPermissions() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
    }

    public boolean hasPhonePermissions() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
    }

    public boolean hasSmsPermissions() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    public boolean hasLocationPermissions() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public boolean hasCameraPermissions() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    // Get permission status summary
    public String getPermissionsSummary() {
        int granted = 0;
        int total = ALL_PERMISSIONS.length;
        
        for (String permission : ALL_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                granted++;
            }
        }
        
        return granted + "/" + total + " permissions granted";
    }
}


