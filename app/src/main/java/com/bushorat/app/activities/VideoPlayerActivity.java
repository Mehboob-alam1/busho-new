package com.bushorat.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.upstream.BandwidthMeter;
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.TrackSelectionDialogBuilder;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.bushorat.app.R;
import com.bushorat.app.helpers.VideoCacheHelper;
import com.bushorat.app.helpers.SessionManager;

@OptIn(markerClass = UnstableApi.class)
public class VideoPlayerActivity extends AppCompatActivity {
    private ExoPlayer player;
    private PlayerView playerView;
    private SessionManager sessionManager;
    private DatabaseReference userRef;
    private String videoUrl;
    private String videoTitle;
    private long startTime;
    private boolean isFullscreen = false;
    private boolean isLiked = false;
    private float playbackSpeed = 1f;
    private DefaultTrackSelector trackSelector;

    private ImageView likeButton;
    private ImageView shareButton;
    private ImageView fullscreenButton;
    private ImageView backButton;
    private ImageView qualityButton;
    private ImageView speedButton;
    private LinearLayout topOverlay;
    private LinearLayout bottomOverlay;
    private ProgressBar bufferingIndicator;
    private TextView titleView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player);

        // Keep screen on during video playback
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        playerView = findViewById(R.id.playerView);
        sessionManager = new SessionManager(this);
        userRef = FirebaseDatabase.getInstance().getReference("users")
                .child(FirebaseAuth.getInstance().getCurrentUser().getUid());

        videoUrl = getIntent().getStringExtra("VIDEO_URL");
        videoTitle = getIntent().getStringExtra("VIDEO_TITLE");
        if (videoUrl != null) {
            initializePlayer();
            setupUI();
            trackVideoStart();
        } else {
            Toast.makeText(this, "Video URL not found", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void initializePlayer() {
        // Create bandwidth meter for adaptive quality
        BandwidthMeter bandwidthMeter = new DefaultBandwidthMeter.Builder(this).build();
        
        // Create track selector for quality adaptation
        trackSelector = new DefaultTrackSelector(this);

        // Initialize ExoPlayer with optimized settings
        player = new ExoPlayer.Builder(this)
                .setTrackSelector(trackSelector)
                .setBandwidthMeter(bandwidthMeter)
                .build();

        playerView.setPlayer(player);
        playerView.setUseController(true);
        playerView.setControllerAutoShow(true);
        playerView.setControllerVisibilityListener(new PlayerView.ControllerVisibilityListener() {
            @Override
            public void onVisibilityChanged(int visibility) {
                setOverlayVisibility(visibility == View.VISIBLE);
            }
        });

        // Set up player listeners
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                updateBufferingIndicator(playbackState == Player.STATE_BUFFERING);
                if (playbackState == Player.STATE_ENDED) {
                    trackVideoComplete();
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                Toast.makeText(VideoPlayerActivity.this, "Error playing video: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                updateBufferingIndicator(false);
            }
        });

        // Create media item with optimized settings
        MediaItem mediaItem = new MediaItem.Builder()
                .setUri(videoUrl)
                .build();

        player.setMediaItem(mediaItem);
        player.prepare();
        player.setPlayWhenReady(true);
        startTime = System.currentTimeMillis();
    }

    private void setupUI() {
        backButton = findViewById(R.id.backButton);
        fullscreenButton = findViewById(R.id.fullscreenButton);
        likeButton = findViewById(R.id.likeButton);
        shareButton = findViewById(R.id.shareButton);
        qualityButton = findViewById(R.id.qualityButton);
        speedButton = findViewById(R.id.speedButton);
        topOverlay = findViewById(R.id.topOverlay);
        bottomOverlay = findViewById(R.id.bottomOverlay);
        bufferingIndicator = findViewById(R.id.bufferingIndicator);
        titleView = findViewById(R.id.videoTitleText);

        if (videoTitle != null && !videoTitle.isEmpty()) {
            titleView.setText(videoTitle);
        }

        backButton.setOnClickListener(v -> finish());

        // Fullscreen button
        fullscreenButton.setOnClickListener(v -> toggleFullscreen());

        // Like button
        likeButton.setOnClickListener(v -> toggleLike());

        // Share button
        shareButton.setOnClickListener(v -> shareVideo());

        speedButton.setOnClickListener(v -> showSpeedDialog());
        qualityButton.setOnClickListener(v -> showQualityDialog());
        setOverlayVisibility(true);
    }

    private void toggleFullscreen() {
        if (isFullscreen) {
            // Exit fullscreen
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_VISIBLE
            );
            if (getSupportActionBar() != null) {
                getSupportActionBar().show();
            }
            fullscreenButton.setImageResource(R.drawable.ic_fullscreen);
        } else {
            // Enter fullscreen
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
            if (getSupportActionBar() != null) {
                getSupportActionBar().hide();
            }
            fullscreenButton.setImageResource(R.drawable.ic_fullscreen_exit);
        }
        isFullscreen = !isFullscreen;
    }

    private void toggleLike() {
        isLiked = !isLiked;
        if (isLiked) {
            ((android.widget.ImageView) findViewById(R.id.likeButton))
                    .setImageResource(R.drawable.outline_favorite_filled_24);
            
            // Track like action
            if (sessionManager.isUserLoggedIn()) {
                sessionManager.updateUserActivity("video_liked", new java.util.HashMap<String, Object>() {{
                    put("videoUrl", videoUrl);
                    put("timestamp", System.currentTimeMillis());
                }});
            }
        } else {
            ((android.widget.ImageView) findViewById(R.id.likeButton))
                    .setImageResource(R.drawable.outline_favorite_24);
        }
    }

    private void shareVideo() {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, "Check out this video: " + videoUrl);
        startActivity(Intent.createChooser(shareIntent, "Share Video"));
        
        // Track share action
        if (sessionManager.isUserLoggedIn()) {
            sessionManager.updateUserActivity("video_shared", new java.util.HashMap<String, Object>() {{
                put("videoUrl", videoUrl);
                put("timestamp", System.currentTimeMillis());
            }});
        }
    }

    private void showSpeedDialog() {
        if (player == null) {
            Toast.makeText(this, R.string.player_speed_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        final float[] speeds = new float[]{0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f};
        final String[] labels = new String[]{"0.5x", "0.75x", "1x", "1.25x", "1.5x", "2x"};
        int checkedItem = 2;
        for (int i = 0; i < speeds.length; i++) {
            if (Math.abs(speeds[i] - playbackSpeed) < 0.01f) {
                checkedItem = i;
                break;
            }
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.player_speed_title)
                .setSingleChoiceItems(labels, checkedItem, (dialog, which) -> {
                    playbackSpeed = speeds[which];
                    if (player != null) {
                        player.setPlaybackSpeed(playbackSpeed);
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showQualityDialog() {
        if (player == null || trackSelector == null || trackSelector.getCurrentMappedTrackInfo() == null) {
            Toast.makeText(this, R.string.player_quality_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }

        new TrackSelectionDialogBuilder(
                this,
                getString(R.string.player_quality_title),
                player,
                0
        )
                .setAllowAdaptiveSelections(true)
                .build()
                .show();
    }

    private void setOverlayVisibility(boolean visible) {
        if (topOverlay != null) {
            topOverlay.setAlpha(visible ? 1f : 0f);
            topOverlay.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        }
        if (bottomOverlay != null) {
            bottomOverlay.setAlpha(visible ? 1f : 0f);
            bottomOverlay.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private void updateBufferingIndicator(boolean show) {
        if (bufferingIndicator != null) {
            bufferingIndicator.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void trackVideoStart() {
        if (sessionManager.isUserLoggedIn()) {
            userRef.child("videoHistory").push().setValue(new VideoHistoryItem(
                    videoUrl,
                    System.currentTimeMillis(),
                    "started"
            ));
        }
    }

    private void trackVideoComplete() {
        if (sessionManager.isUserLoggedIn()) {
            long watchTime = System.currentTimeMillis() - startTime;
            userRef.child("videoHistory").push().setValue(new VideoHistoryItem(
                    videoUrl,
                    System.currentTimeMillis(),
                    "completed",
                    watchTime
            ));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) {
            player.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (player != null) {
            player.play();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
        }
        // Clear screen on flag
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    // Video history model class
    public static class VideoHistoryItem {
        public String videoUrl;
        public long timestamp;
        public String status;
        public long watchTime;

        public VideoHistoryItem() {}

        public VideoHistoryItem(String videoUrl, long timestamp, String status) {
            this.videoUrl = videoUrl;
            this.timestamp = timestamp;
            this.status = status;
        }

        public VideoHistoryItem(String videoUrl, long timestamp, String status, long watchTime) {
            this.videoUrl = videoUrl;
            this.timestamp = timestamp;
            this.status = status;
            this.watchTime = watchTime;
        }
    }
}