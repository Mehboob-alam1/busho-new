package com.mehboob.bushorat.activities;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.trackselection.TrackSelection;
import androidx.media3.exoplayer.trackselection.TrackSelector;
import androidx.media3.exoplayer.upstream.BandwidthMeter;
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter;
import androidx.media3.ui.PlayerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.mehboob.bushorat.R;
import com.mehboob.bushorat.helpers.VideoCacheHelper;
import com.mehboob.bushorat.helpers.SessionManager;

@OptIn(markerClass = UnstableApi.class)
public class VideoPlayerActivity extends AppCompatActivity {
    private ExoPlayer player;
    private PlayerView playerView;
    private SessionManager sessionManager;
    private DatabaseReference userRef;
    private String videoUrl;
    private long startTime;
    private boolean isFullscreen = false;

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
        if (videoUrl != null) {
            initializePlayer();
            trackVideoStart();
        } else {
            Toast.makeText(this, "Video URL not found", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void initializePlayer() {
        // Create adaptive track selector for better quality adaptation
        BandwidthMeter bandwidthMeter = new DefaultBandwidthMeter.Builder(this).build();
        TrackSelection.Factory trackSelectionFactory = new AdaptiveTrackSelection.Factory();
        TrackSelector trackSelector = new DefaultTrackSelector(this, trackSelectionFactory);

        // Initialize ExoPlayer with optimized settings
        player = new ExoPlayer.Builder(this)
                .setTrackSelector(trackSelector)
                .setBandwidthMeter(bandwidthMeter)
                .build();

        playerView.setPlayer(player);
        playerView.setUseController(true);
        playerView.setControllerAutoShow(true);

        // Set up player listeners
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_ENDED) {
                    trackVideoComplete();
                }
            }

            @Override
            public void onPlayerError(com.google.android.exoplayer2.PlaybackException error) {
                Toast.makeText(VideoPlayerActivity.this, "Error playing video: " + error.getMessage(), Toast.LENGTH_SHORT).show();
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