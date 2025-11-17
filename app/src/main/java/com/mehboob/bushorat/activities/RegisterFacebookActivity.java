package com.mehboob.bushorat.activities;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.mehboob.bushorat.helpers.AuthHelper;
import com.mehboob.bushorat.helpers.SessionManager;
import com.mehboob.bushorat.models.UserModel;
import com.mehboob.bushorat.databinding.ActivityRegisterFacebookBinding;

public class RegisterFacebookActivity extends AppCompatActivity {
    private static final String TAG = "RegisterFacebookActivity";
    
    private ActivityRegisterFacebookBinding binding;
    private AuthHelper authHelper;
    private ProgressDialog loadingDialog;
    private SessionManager sessionManager;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterFacebookBinding.inflate(getLayoutInflater());

        EdgeToEdge.enable(this);
        authHelper = new AuthHelper();
        sessionManager = new SessionManager(this);

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
            UserModel user = new UserModel(name, email, password, whatsapp, address,"",200);

            authHelper.registerUser(user, new AuthHelper.AuthCallback() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "onSuccess: User registration successful");
                    loadingDialog.dismiss();
                    
                    // Create user session after successful registration
                    String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                    Log.d(TAG, "onSuccess: User ID = " + userId);
                    
                    sessionManager.createUserSession(userId, email);
                    Log.d(TAG, "onSuccess: Session created for user");
                    
                    Toast.makeText(RegisterFacebookActivity.this, "Signup Successful!", Toast.LENGTH_SHORT).show();
                    
                    // Route based on email verification status
                    boolean verified = FirebaseAuth.getInstance().getCurrentUser().isEmailVerified();
                    sessionManager.setEmailVerified(verified);
                    Intent intent = new Intent(RegisterFacebookActivity.this, verified ? DashboardActivity.class : LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    Log.d(TAG, "onSuccess: Finishing RegisterFacebookActivity");
                    finish();
                }

                @Override
                public void onFailure(String errorMessage) {
                    Log.e(TAG, "onFailure: Registration failed - " + errorMessage);
                    Log.d(TAG, "onFailure: Trying sign-in fallback");
                    authHelper.signInUser(email, password, new AuthHelper.AuthCallback() {
                        @Override
                        public void onSuccess() {
                            loadingDialog.dismiss();
                            String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                            sessionManager.createUserSession(userId, email);
                            boolean verified = FirebaseAuth.getInstance().getCurrentUser().isEmailVerified();
                            sessionManager.setEmailVerified(verified);
                            Intent intent = new Intent(RegisterFacebookActivity.this, verified ? DashboardActivity.class : LoginActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        }

                        @Override
                        public void onFailure(String errorMessage2) {
                            loadingDialog.dismiss();
                            Toast.makeText(RegisterFacebookActivity.this, "Error: " + errorMessage2, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            });

        });
    }
}