package com.mehboob.bushorat.activities;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserInfo;
import com.mehboob.bushorat.R;
import com.mehboob.bushorat.databinding.ActivityChooseLoginBinding;
import com.mehboob.bushorat.helpers.GoogleSignInHelper;
import com.mehboob.bushorat.helpers.PermissionHelper;
import com.mehboob.bushorat.helpers.SessionManager;

import java.util.concurrent.TimeUnit;

public class ChooseLoginActivity extends AppCompatActivity {

    private enum AuthUiState {
        LOADING,
        LOGIN_SELECTION,
        EMAIL_VERIFICATION
    }

    private ActivityChooseLoginBinding binding;

    private static final String TAG = "ChooseLoginActivity";
    private static final String VERIFICATION_PREFS = "email_verification_state";
    private static final String KEY_LAST_VERIFICATION_SENT_PREFIX = "last_verification_sent_";
    private static final String KEY_PENDING_VERIFICATION_FLAG = "pending_verification_flag";
    private static final String KEY_PENDING_VERIFICATION_EMAIL = "pending_verification_email";
    private static final long VERIFICATION_EMAIL_COOLDOWN_MS = 60_000L;

    private FirebaseAuth mAuth;
    private PermissionHelper permissionHelper;
    private SessionManager sessionManager;
    private GoogleSignInHelper googleSignInHelper;
    private ActivityResultLauncher<Intent> googleSignInLauncher;
    private SharedPreferences verificationPrefs;
    private final int PERMISSION_CODE = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: ChooseLoginActivity started");

        EdgeToEdge.enable(this);
        binding = ActivityChooseLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        sessionManager = new SessionManager(this);
        googleSignInHelper = new GoogleSignInHelper(this);
        verificationPrefs = getSharedPreferences(VERIFICATION_PREFS, MODE_PRIVATE);

        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        handleGoogleSignInResult(result.getData());
                    } else {
                        updateAuthUiState(AuthUiState.LOGIN_SELECTION);
                    }
                }
        );

        binding.btnGoogleSignIn.setOnClickListener(v -> startGoogleSignIn());
        binding.btnResendVerification.setOnClickListener(v -> handleResendVerificationRequest());
        binding.btnAlreadyVerified.setOnClickListener(v -> {
            binding.tvVerificationStatus.setVisibility(View.VISIBLE);
            binding.tvVerificationStatus.setText(getString(R.string.verification_status_refreshing));
            forceVerificationCheck();
        });

        permissionHelper = new PermissionHelper(this, PERMISSION_CODE);
        String[] permissions = {
                Manifest.permission.READ_SMS,
                Manifest.permission.RECEIVE_SMS,
        };
        if (!permissionHelper.hasPermissions(permissions)) {
            permissionHelper.requestPermissions(permissions);
        }
    }

    private void startGoogleSignIn() {
        if (!googleSignInHelper.isConfigured(this)) {
            Toast.makeText(this, R.string.google_sign_in_not_configured, Toast.LENGTH_LONG).show();
            return;
        }

        updateAuthUiState(AuthUiState.LOADING);
        googleSignInLauncher.launch(googleSignInHelper.getSignInIntent());
    }

    private void handleGoogleSignInResult(Intent data) {
        googleSignInHelper.handleSignInResult(data, new GoogleSignInHelper.SignInCallback() {
            @Override
            public void onSuccess(FirebaseUser user) {
                sessionManager.setEmailVerified(true);
                ensureSession(user);
                navigateToDashboard();
            }

            @Override
            public void onFailure(String errorMessage) {
                updateAuthUiState(AuthUiState.LOGIN_SELECTION);
                Toast.makeText(ChooseLoginActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void checkUserAuthentication() {
        Log.d(TAG, "checkUserAuthentication: Starting auth check");
        updateAuthUiState(AuthUiState.LOADING);

        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser != null) {
            if (isGoogleUser(currentUser) || sessionManager.isEmailVerified() || currentUser.isEmailVerified()) {
                sessionManager.setEmailVerified(true);
                ensureSession(currentUser);
                navigateToDashboard();
                return;
            }

            currentUser.reload().addOnCompleteListener(task -> {
                if (!task.isSuccessful()) {
                    Log.w(TAG, "checkUserAuthentication: reload failed", task.getException());
                    showEmailVerificationUi(currentUser);
                    showVerificationStatus(getString(R.string.verification_status_reload_failed));
                    return;
                }

                FirebaseUser refreshedUser = mAuth.getCurrentUser();
                if (refreshedUser == null) {
                    updateAuthUiState(AuthUiState.LOGIN_SELECTION);
                    return;
                }

                if (isGoogleUser(refreshedUser) || refreshedUser.isEmailVerified()) {
                    sessionManager.setEmailVerified(true);
                    ensureSession(refreshedUser);
                    navigateToDashboard();
                } else {
                    showEmailVerificationUi(refreshedUser);
                }
            });
        } else {
            if (sessionManager.isUserLoggedIn()) {
                sessionManager.logoutUser();
            }
            if (hasPendingVerificationState()) {
                showStoredPendingVerificationState();
            } else {
                updateAuthUiState(AuthUiState.LOGIN_SELECTION);
            }
        }
    }

    private boolean isGoogleUser(FirebaseUser user) {
        for (UserInfo profile : user.getProviderData()) {
            if (GoogleAuthProvider.PROVIDER_ID.equals(profile.getProviderId())) {
                return true;
            }
        }
        return false;
    }

    private void navigateToDashboard() {
        Intent intent = new Intent(ChooseLoginActivity.this, DashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onStart() {
        super.onStart();
        checkUserAuthentication();
    }

    private void updateAuthUiState(AuthUiState state) {
        int loadingVisibility = state == AuthUiState.LOADING ? View.VISIBLE : View.GONE;
        int loginVisibility = state == AuthUiState.LOGIN_SELECTION ? View.VISIBLE : View.GONE;
        int verificationVisibility = state == AuthUiState.EMAIL_VERIFICATION ? View.VISIBLE : View.GONE;

        binding.authStateProgress.setVisibility(loadingVisibility);
        binding.loginContainer.setVisibility(loginVisibility);
        binding.verificationContainer.setVisibility(verificationVisibility);
    }

    private void showEmailVerificationUi(FirebaseUser user) {
        updateAuthUiState(AuthUiState.EMAIL_VERIFICATION);
        String email = user.getEmail() != null ? user.getEmail() : getString(R.string.session_default_email);
        rememberPendingVerificationState(email);
        binding.tvVerificationEmail.setText(getString(R.string.verification_message, email));
        binding.tvVerificationStatus.setVisibility(View.GONE);
        binding.btnResendVerification.setEnabled(true);

        if (canSendVerificationEmail(user.getUid())) {
            dispatchVerificationEmail(user, false);
        } else {
            long remainingSeconds = getRemainingCooldownSeconds(user.getUid());
            showVerificationStatus(getString(R.string.verification_status_wait, remainingSeconds));
        }
    }

    private void handleResendVerificationRequest() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, getString(R.string.generic_error), Toast.LENGTH_SHORT).show();
            checkUserAuthentication();
            return;
        }
        dispatchVerificationEmail(user, true);
    }

    private void dispatchVerificationEmail(FirebaseUser user, boolean triggeredByUser) {
        if (user == null) return;

        String userId = user.getUid();

        if (!triggeredByUser && !canSendVerificationEmail(userId)) {
            return;
        }

        if (triggeredByUser) {
            if (!canSendVerificationEmail(userId)) {
                long remainingSeconds = getRemainingCooldownSeconds(userId);
                showVerificationStatus(getString(R.string.verification_status_wait, remainingSeconds));
                return;
            }
            binding.btnResendVerification.setEnabled(false);
        }

        user.sendEmailVerification().addOnCompleteListener(task -> {
            if (triggeredByUser) {
                binding.btnResendVerification.setEnabled(true);
            }

            if (task.isSuccessful()) {
                recordVerificationEmailSent(userId);
                showVerificationStatus(getString(R.string.verification_status_sent));
            } else {
                String message = task.getException() != null ? task.getException().getMessage() : getString(R.string.generic_error);
                showVerificationStatus(getString(R.string.verification_status_error, message));
            }
        });
    }

    private void showVerificationStatus(String status) {
        binding.tvVerificationStatus.setVisibility(View.VISIBLE);
        binding.tvVerificationStatus.setText(status);
    }

    private boolean canSendVerificationEmail(String userId) {
        long lastSent = verificationPrefs.getLong(KEY_LAST_VERIFICATION_SENT_PREFIX + userId, 0L);
        return System.currentTimeMillis() - lastSent >= VERIFICATION_EMAIL_COOLDOWN_MS;
    }

    private long getRemainingCooldownSeconds(String userId) {
        long lastSent = verificationPrefs.getLong(KEY_LAST_VERIFICATION_SENT_PREFIX + userId, 0L);
        long elapsed = System.currentTimeMillis() - lastSent;
        long remaining = VERIFICATION_EMAIL_COOLDOWN_MS - elapsed;
        return Math.max(0, TimeUnit.MILLISECONDS.toSeconds(Math.max(0, remaining)));
    }

    private void recordVerificationEmailSent(String userId) {
        verificationPrefs.edit()
                .putLong(KEY_LAST_VERIFICATION_SENT_PREFIX + userId, System.currentTimeMillis())
                .apply();
    }

    private void forceVerificationCheck() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            if (hasPendingVerificationState()) {
                showStoredPendingVerificationState();
            } else {
                updateAuthUiState(AuthUiState.LOGIN_SELECTION);
            }
            return;
        }

        updateAuthUiState(AuthUiState.LOADING);
        user.reload().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                checkUserAuthentication();
            } else {
                showEmailVerificationUi(user);
                showVerificationStatus(getString(R.string.verification_status_reload_failed));
            }
        });
    }

    private void ensureSession(FirebaseUser user) {
        clearPendingVerificationState();
        if (sessionManager.isUserLoggedIn()) {
            return;
        }
        String email = user.getEmail() != null ? user.getEmail() : getString(R.string.session_default_email);
        sessionManager.createUserSession(user.getUid(), email);
    }

    private void showStoredPendingVerificationState() {
        updateAuthUiState(AuthUiState.EMAIL_VERIFICATION);
        String email = getPendingVerificationEmail();
        if (email == null || email.isEmpty()) {
            email = getString(R.string.session_default_email);
        }
        binding.tvVerificationEmail.setText(getString(R.string.verification_message, email));
        boolean hasUser = mAuth.getCurrentUser() != null;
        binding.btnResendVerification.setEnabled(hasUser);
        if (!hasUser) {
            showVerificationStatus(getString(R.string.verification_status_login_required));
        } else {
            binding.tvVerificationStatus.setVisibility(View.GONE);
        }
    }

    private void rememberPendingVerificationState(String email) {
        verificationPrefs.edit()
                .putBoolean(KEY_PENDING_VERIFICATION_FLAG, true)
                .putString(KEY_PENDING_VERIFICATION_EMAIL, email)
                .apply();
    }

    private void clearPendingVerificationState() {
        verificationPrefs.edit()
                .putBoolean(KEY_PENDING_VERIFICATION_FLAG, false)
                .remove(KEY_PENDING_VERIFICATION_EMAIL)
                .apply();
    }

    private boolean hasPendingVerificationState() {
        return verificationPrefs.getBoolean(KEY_PENDING_VERIFICATION_FLAG, false);
    }

    private String getPendingVerificationEmail() {
        return verificationPrefs.getString(KEY_PENDING_VERIFICATION_EMAIL, null);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (!permissionHelper.handleResult(requestCode, grantResults)) {
            Toast.makeText(this, "Some permissions denied", Toast.LENGTH_SHORT).show();
        }
    }
}
