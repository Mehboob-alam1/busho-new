package com.bushorat.chatapp.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.bushorat.chatapp.R;
import com.bushorat.chatapp.databinding.ActivitySecretLauncherBinding;
import com.bushorat.chatapp.helpers.AppVisibilityHelper;

public class SecretLauncherActivity extends AppCompatActivity {

    private ActivitySecretLauncherBinding binding;
    private AppVisibilityHelper visibilityHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivitySecretLauncherBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        visibilityHelper = AppVisibilityHelper.getInstance(this);

        // Check if user is authenticated
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            // Not logged in, redirect to login
            startActivity(new Intent(this, ChooseLoginActivity.class));
            finish();
            return;
        }

        setupUI();
    }

    private void setupUI() {
        binding.btnVerify.setOnClickListener(v -> verifySecretCode());
        
        // Enable verify button when code is entered
        binding.etSecretCode.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                binding.btnVerify.setEnabled(s.length() == 6);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Show current visibility status
        updateVisibilityStatus();
    }

    private void verifySecretCode() {
        String inputCode = binding.etSecretCode.getText().toString().trim();
        
        if (inputCode.length() != 6) {
            Toast.makeText(this, "Please enter a 6-digit code", Toast.LENGTH_SHORT).show();
            return;
        }

        if (visibilityHelper.verifySecretCode(inputCode)) {
            // Code verified - show app and navigate to dashboard
            visibilityHelper.showApp();
            Toast.makeText(this, "App restored! Opening dashboard...", Toast.LENGTH_SHORT).show();
            
            Intent intent = new Intent(this, DashboardActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } else {
            Toast.makeText(this, "Invalid secret code", Toast.LENGTH_SHORT).show();
            binding.etSecretCode.setText("");
            binding.etSecretCode.requestFocus();
        }
    }

    private void updateVisibilityStatus() {
        AppVisibilityHelper.VisibilityStatus status = visibilityHelper.getVisibilityStatus();

        if (status.isHidden) {
            binding.tvStatus.setText("App is hidden. Dial *#*#737826#*#* and enter your secret code to restore.");
            binding.tvStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_dark, getTheme()));
        } else {
            binding.tvStatus.setText("App is visible in the launcher.");
            binding.tvStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark, getTheme()));
        }

        if (status.secretCode != null) {
            binding.tvSecretCodeHint.setText("Your secret code: " + status.secretCode);
            binding.tvSecretCodeHint.setVisibility(View.VISIBLE);
        } else {
            binding.tvSecretCodeHint.setVisibility(View.GONE);
        }
    }
}
