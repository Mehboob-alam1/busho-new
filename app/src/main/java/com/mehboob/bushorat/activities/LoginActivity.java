package com.mehboob.bushorat.activities;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.mehboob.bushorat.R;
import com.mehboob.bushorat.helpers.SessionManager;

public class LoginActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private SessionManager sessionManager;
    private ProgressDialog loading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();
        sessionManager = new SessionManager(this);

        EditText etEmail = findViewById(R.id.etEmail);
        EditText etPassword = findViewById(R.id.etPassword);
        Button btnLogin = findViewById(R.id.btnLogin);
        TextView tvCreate = findViewById(R.id.tvCreateAccount);

        loading = new ProgressDialog(this);
        loading.setMessage("Signing in...");
        loading.setCancelable(false);

        btnLogin.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
                Toast.makeText(this, "Enter email and password", Toast.LENGTH_SHORT).show();
                return;
            }
            loading.show();
            mAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener(task -> {
                loading.dismiss();
                if (task.isSuccessful()) {
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user == null) {
                        Toast.makeText(this, "Login failed, try again", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    sessionManager.createUserSession(user.getUid(), email);
                    boolean verified = user.isEmailVerified();
                    sessionManager.setEmailVerified(verified);
                    Intent intent = new Intent(LoginActivity.this, verified ? DashboardActivity.class : ChooseLoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                } else {
                    Toast.makeText(this, "Invalid credentials", Toast.LENGTH_SHORT).show();
                }
            });
        });

        tvCreate.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, SignupActivity.class));
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Check if user is already logged in and verified
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            // If already verified locally, go directly to dashboard
            if (sessionManager.isEmailVerified()) {
                Intent intent = new Intent(LoginActivity.this, DashboardActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
                return;
            }
            // Check email verification status
            currentUser.reload().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    FirebaseUser refreshedUser = mAuth.getCurrentUser();
                    if (refreshedUser != null && refreshedUser.isEmailVerified()) {
                        sessionManager.setEmailVerified(true);
                        Intent intent = new Intent(LoginActivity.this, DashboardActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    }
                }
            });
        }
    }
}


