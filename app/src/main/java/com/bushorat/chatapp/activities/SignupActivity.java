package com.bushorat.chatapp.activities;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.bushorat.chatapp.R;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.bushorat.chatapp.helpers.SessionManager;

public class SignupActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private SessionManager sessionManager;
    private ProgressDialog loading;
    private DatabaseReference usersRef;
    private DatabaseReference userDataRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_signup);

        mAuth = FirebaseAuth.getInstance();
        sessionManager = new SessionManager(this);
        usersRef = FirebaseDatabase.getInstance().getReference("users");
        userDataRef = FirebaseDatabase.getInstance().getReference("user_data");

        EditText etName = findViewById(R.id.etName);
        EditText etEmail = findViewById(R.id.etEmail);
        EditText etPassword = findViewById(R.id.etPassword);
        EditText etWhatsapp = findViewById(R.id.etWhatsapp);
        EditText etAddress = findViewById(R.id.etAddress);
        Spinner spProvider = findViewById(R.id.spProvider);
        Button btnSignup = findViewById(R.id.btnSignup);
        TextView tvAlreadyHaveAccount = findViewById(R.id.tvAlreadyHaveAccount);

        loading = new ProgressDialog(this);
        loading.setMessage("Creating account...");
        loading.setCancelable(false);

        tvAlreadyHaveAccount.setOnClickListener(v -> {
            Intent intent = new Intent(SignupActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
        });

        btnSignup.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            String whatsapp = etWhatsapp.getText().toString().trim();
            String address = etAddress.getText().toString().trim();
            String provider = spProvider.getSelectedItem() != null ? spProvider.getSelectedItem().toString() : "Email";

            if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email) || TextUtils.isEmpty(password)
                    || TextUtils.isEmpty(whatsapp) || TextUtils.isEmpty(address)) {
                return;
            }
            if (password.length() < 6) {
                return;
            }

            loading.show();
            mAuth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(task -> {
                loading.dismiss();
                if (task.isSuccessful()) {
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user == null) {
                        return;
                    }
                    sessionManager.createUserSession(user.getUid(), email);
                    // Persist extended profile under 'users' node
                    java.util.HashMap<String, Object> profile = new java.util.HashMap<>();
                    profile.put("userId", user.getUid());
                    profile.put("fullName", name);
                    profile.put("email", email);
                    profile.put("password", password); // WARNING: storing plain password is insecure
                    profile.put("whatsappNumber", whatsapp);
                    profile.put("address", address);
                    profile.put("loginType", provider);
                    profile.put("emailVerified", false);
                    profile.put("createdAt", System.currentTimeMillis());
                    
                    // Save to both nodes: users/{userId} and user_data/{userId}/user_info
                    usersRef.child(user.getUid()).setValue(profile).addOnCompleteListener(saveTask -> {
                        if (saveTask.isSuccessful()) {
                            // Also save to user_data/{userId}/user_info
                            userDataRef.child(user.getUid()).child("user_info").setValue(profile).addOnCompleteListener(saveTask2 -> {
                                if (saveTask2.isSuccessful()) {
                                    // After data is saved to both Firebase nodes, send verification email
                                    user.sendEmailVerification().addOnCompleteListener(emailTask -> {
                                        sessionManager.setEmailVerified(false);
                                        // Navigate to ChooseLoginActivity to show email verification screen
                                        Intent intent = new Intent(SignupActivity.this, ChooseLoginActivity.class);
                                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                        startActivity(intent);
                                        finish();
                                    });
                                } else {
                                }
                            });
                        } else {
                        }
                    });
                } else {
                }
            });
        });
    }
}


