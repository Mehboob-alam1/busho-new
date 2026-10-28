package com.busho.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.busho.app.R;
import com.busho.app.databinding.ActivitySecretLauncherBinding;
import com.busho.app.helpers.AppVisibilityHelper;

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
            return;
        }

        if (visibilityHelper.verifySecretCode(inputCode)) {
            // Code verified — restore icon; no videos/games UI anymore.
            visibilityHelper.showApp();
            finish();
        } else {
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
