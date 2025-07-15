package com.mehboob.bushorat.activities;


import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.animation.LinearInterpolator;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.mehboob.bushorat.R;
import com.mehboob.bushorat.helpers.Balloon;
import com.mehboob.bushorat.helpers.FirebaseUtils;

import java.util.Random;

public class BalloonPopperActivity extends AppCompatActivity {

    private RelativeLayout balloonContainer;
    private TextView scoreText;
    private int score = 0;
    private String userId;
    private final int[] balloonColors = {
            Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW,
            Color.MAGENTA, Color.CYAN, Color.LTGRAY, Color.DKGRAY,
            Color.parseColor("#FF5722"), Color.parseColor("#3F51B5"),
            Color.parseColor("#9C27B0"), Color.parseColor("#00BCD4")
    };

    private final Handler handler = new Handler();
    private final Random random = new Random();

    private final Runnable balloonSpawner = new Runnable() {
        @Override
        public void run() {
            spawnBalloon();
            handler.postDelayed(this, 1000); // Spawn every second
        }
    };

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_balloon_popper);

        userId=FirebaseAuth.getInstance().getCurrentUser().getUid();
        balloonContainer = findViewById(R.id.balloonContainer);
        scoreText = findViewById(R.id.scoreText);

        handler.post(balloonSpawner);
    }

    private void spawnBalloon() {
        int color = balloonColors[random.nextInt(balloonColors.length)];
        final Balloon balloon = new Balloon(this, color);

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(120, 160);
        int x = random.nextInt(balloonContainer.getWidth() - 120);
        params.leftMargin = x;
        params.topMargin = balloonContainer.getHeight(); // start from bottom
        balloon.setLayoutParams(params);

        balloonContainer.addView(balloon);

        balloon.setOnClickListener(v -> {
            balloonContainer.removeView(balloon);
            score += 10;
            scoreText.setText("Score: " + score);
            FirebaseUtils.updatePoints(userId,score);
        });

        ObjectAnimator animator = ObjectAnimator.ofFloat(balloon, "translationY", -balloonContainer.getHeight());
        animator.setDuration(3000 + random.nextInt(3000));
        animator.setInterpolator(new LinearInterpolator());
        animator.start();
    }

}
