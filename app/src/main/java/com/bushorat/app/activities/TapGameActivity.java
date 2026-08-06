package com.bushorat.app.activities;

import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.bushorat.app.R;
import com.bushorat.app.helpers.FirebaseUtils;

public class TapGameActivity extends AppCompatActivity {
    private int score = 0;
    private Button tapButton;
    private TextView scoreText;
    private Handler handler = new Handler();
    private String userId ;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tap_game);
        userId= FirebaseAuth.getInstance().getCurrentUser().getUid();

        tapButton = findViewById(R.id.tapButton);
        scoreText = findViewById(R.id.scoreText);

        startGame();
    }

    private void startGame() {
        tapButton.setOnClickListener(v -> {
            score++;
            scoreText.setText("Score: " + score);
        });

        handler.postDelayed(() -> {
            Toast.makeText(this, "Time's up!", Toast.LENGTH_SHORT).show();
            FirebaseUtils.updatePoints(userId, score); // Add points
            finish(); // End game
        }, 10000); // 10 seconds
    }
}
