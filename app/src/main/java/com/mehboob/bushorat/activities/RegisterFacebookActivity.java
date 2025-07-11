package com.mehboob.bushorat.activities;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.mehboob.bushorat.helpers.AuthHelper;
import com.mehboob.bushorat.models.UserModel;
import com.mehboob.bushorat.databinding.ActivityRegisterFacebookBinding;

public class RegisterFacebookActivity extends AppCompatActivity {
    private ActivityRegisterFacebookBinding binding;
    private AuthHelper authHelper;
    private ProgressDialog loadingDialog;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterFacebookBinding.inflate(getLayoutInflater());

        EdgeToEdge.enable(this);
        authHelper = new AuthHelper();

        // Initialize loading dialog
        loadingDialog = new ProgressDialog(this);
        loadingDialog.setMessage("Creating account...");
        loadingDialog.setCancelable(false);

        setContentView(binding.getRoot());


        binding.btnSignup.setOnClickListener(v -> {
            String name = binding.etName.getText().toString().trim();
            String email = binding.etGmail.getText().toString().trim();
            String password = binding.etPassword.getText().toString().trim();
            String whatsapp = binding.etWhatsapp.getText().toString().trim();
            String address = binding.etAddress.getText().toString().trim();

            if (name.isEmpty() || email.isEmpty() || password.isEmpty() || whatsapp.isEmpty() || address.isEmpty()) {
                Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show();
                return;
            }

            // Show loading
            loadingDialog.show();

            // Create user model
            UserModel user = new UserModel(name, email, password, whatsapp, address,"");

            authHelper.registerUser(user, new AuthHelper.AuthCallback() {
                @Override
                public void onSuccess() {
                    loadingDialog.dismiss();
                    Toast.makeText(RegisterFacebookActivity.this, "Signup Successful!", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(RegisterFacebookActivity.this,DashboardActivity.class));

                }

                @Override
                public void onFailure(String errorMessage) {
                    loadingDialog.dismiss();
                    Toast.makeText(RegisterFacebookActivity.this, "Error: " + errorMessage, Toast.LENGTH_LONG).show();
                }
            });

        });
    }
}