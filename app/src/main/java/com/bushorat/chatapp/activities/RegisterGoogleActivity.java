package com.bushorat.chatapp.activities;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.bushorat.chatapp.R;
import com.bushorat.chatapp.databinding.ActivityRegisterGoogleBinding;
import com.bushorat.chatapp.helpers.AuthHelper;
import com.bushorat.chatapp.helpers.SessionManager;
import com.bushorat.chatapp.models.UserModel;

public class RegisterGoogleActivity extends AppCompatActivity {
    private static final String TAG = "RegisterGoogleActivity";

    private ActivityRegisterGoogleBinding binding;

    private AuthHelper authHelper;
    private ProgressDialog loadingDialog;
    private SessionManager sessionManager;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityRegisterGoogleBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        authHelper = new AuthHelper();
        sessionManager = new SessionManager(this);

        // Initialize loading dialog
        loadingDialog = new ProgressDialog(this);
        loadingDialog.setMessage("Creating account...");
        loadingDialog.setCancelable(false);



        binding.btnSignupG.setOnClickListener(v -> {
            String name = binding.etNameG.getText().toString().trim();
            String email = binding.etGmailG.getText().toString().trim();
            String password = binding.etPasswordG.getText().toString().trim();
            String whatsapp = binding.etWhatsappG.getText().toString().trim();
            String address = binding.etAddressG.getText().toString().trim();

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
                    
                    Toast.makeText(RegisterGoogleActivity.this, "Signup Successful!", Toast.LENGTH_SHORT).show();
                    
                    // Route based on email verification status
                    boolean verified = FirebaseAuth.getInstance().getCurrentUser().isEmailVerified();
                    sessionManager.setEmailVerified(verified);
                    Intent intent = new Intent(RegisterGoogleActivity.this, verified ? DashboardActivity.class : LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    Log.d(TAG, "onSuccess: Finishing RegisterGoogleActivity");
                    finish();
                }

                @Override
                public void onFailure(String errorMessage) {
                    Log.e(TAG, "onFailure: Registration failed - " + errorMessage);
                    // Fallback: attempt direct sign-in in case user already exists
                    Log.d(TAG, "onFailure: Trying sign-in fallback");
                    authHelper.signInUser(email, password, new AuthHelper.AuthCallback() {
                        @Override
                        public void onSuccess() {
                            loadingDialog.dismiss();
                            String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                            sessionManager.createUserSession(userId, email);
                            boolean verified = FirebaseAuth.getInstance().getCurrentUser().isEmailVerified();
                            sessionManager.setEmailVerified(verified);
                            Intent intent = new Intent(RegisterGoogleActivity.this, verified ? DashboardActivity.class : LoginActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        }

                        @Override
                        public void onFailure(String errorMessage2) {
                            loadingDialog.dismiss();
                            Toast.makeText(RegisterGoogleActivity.this, "Error: " + errorMessage2, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            });

        });





    }
}