package com.bushorat.chatapp.activities;

import android.graphics.Color;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.bushorat.chatapp.R;
import com.bushorat.chatapp.helpers.FirebaseUtils;

import java.util.*;

public class ColorTapGameActivity extends AppCompatActivity {

    private TextView tvTargetColor;
    private GridLayout gridLayout;

    private String[] colors = {
            "RED", "GREEN", "BLUE", "YELLOW", "ORANGE", "PURPLE",
            "PINK", "BROWN", "CYAN", "MAGENTA", "GRAY", "LIME"
    };

    private Map<String, Integer> colorMap;
    private String currentTarget;
    private int score = 0;
    private int rounds = 0;
    private final int maxRounds = 10;
    private final String userId = "user123"; // Use real UID in production

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_color_tap_game);

        tvTargetColor = findViewById(R.id.tvTargetColor);
        gridLayout = findViewById(R.id.gridLayout);

        setupColorMap();
        setupButtons();
        nextRound();
    }

    private void setupColorMap() {
        colorMap = new HashMap<>();
        colorMap.put("RED", Color.RED);
        colorMap.put("GREEN", Color.GREEN);
        colorMap.put("BLUE", Color.BLUE);
        colorMap.put("YELLOW", Color.YELLOW);
        colorMap.put("ORANGE", Color.parseColor("#FFA500"));
        colorMap.put("PURPLE", Color.parseColor("#800080"));
        colorMap.put("PINK", Color.parseColor("#FFC0CB"));
        colorMap.put("BROWN", Color.parseColor("#A52A2A"));
        colorMap.put("CYAN", Color.CYAN);
        colorMap.put("MAGENTA", Color.MAGENTA);
        colorMap.put("GRAY", Color.GRAY);
        colorMap.put("LIME", Color.parseColor("#00FF00"));
    }

    private void setupButtons() {
        for (String color : colors) {
            Button btn = new Button(this);
            btn.setLayoutParams(new ViewGroup.LayoutParams(200, 200));
            btn.setBackgroundColor(colorMap.get(color));
            btn.setTag(color);
            btn.setOnClickListener(v -> handleTap((String) v.getTag()));
            gridLayout.addView(btn);
        }
    }

    private void nextRound() {
        if (rounds >= maxRounds) {
            FirebaseUtils.updatePoints(userId, score);
            finish();
            return;
        }

        currentTarget = colors[new Random().nextInt(colors.length)];
        tvTargetColor.setText("Tap: " + currentTarget);
        rounds++;
    }

    private void handleTap(String tappedColor) {
        if (tappedColor.equals(currentTarget)) {
            score += 5;
        } else {
        }
        nextRound();
    }
}

