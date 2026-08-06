package com.bushorat.app.activities;

import android.os.Bundle;
import android.os.Handler;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.bushorat.app.R;
import com.bushorat.app.helpers.FirebaseUtils;

import java.util.ArrayList;
import java.util.Collections;

public class MemoryGameActivity extends AppCompatActivity {

    private GridLayout gridLayout;
    private ArrayList<Integer> cardImages;
    private ArrayList<ImageView> cardViews = new ArrayList<>();
    private ImageView firstCard, secondCard;
    private int matchedPairs = 0;
    private int score = 0;
    private  String userId ;

    private final int[] images = {
            R.drawable.apple,
            R.drawable.bananas,
            R.drawable.cherries,
            R.drawable.grapes,
            R.drawable.lemon,
            R.drawable.mango,
            R.drawable.orange,
            R.drawable.passion_fruit,
            R.drawable.pineapple,
            R.drawable.strawberry,
            R.drawable.watermelon,
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_memory_game);
        gridLayout = findViewById(R.id.gridLayout);

        userId= FirebaseAuth.getInstance().getCurrentUser().getUid();

        setupCards();
    }

    private void setupCards() {
        cardImages = new ArrayList<>();
        for (int img : images) {
            cardImages.add(img);
            cardImages.add(img); // add twice for matching pair
        }
        Collections.shuffle(cardImages);

        for (int i = 0; i < 6; i++) {
            final ImageView card = new ImageView(this);
            card.setLayoutParams(new GridLayout.LayoutParams());
            card.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.setImageResource(R.drawable.card);
            card.setTag(cardImages.get(i));
            card.setPadding(8, 8, 8, 8);
            card.setBackgroundColor(0xFFE0F7FA);
            card.setOnClickListener(v -> flipCard(card));
            cardViews.add(card);
            gridLayout.addView(card);
        }
    }

    private void flipCard(ImageView card) {
        if (card == firstCard || card == secondCard) return;
        card.setImageResource((int) card.getTag());

        if (firstCard == null) {
            firstCard = card;
        } else {
            secondCard = card;

            new Handler().postDelayed(() -> checkMatch(), 1000);
        }
    }

    private void checkMatch() {
        if ((int) firstCard.getTag() == (int) secondCard.getTag()) {
            Toast.makeText(this, "Match! +10 pts", Toast.LENGTH_SHORT).show();
            firstCard.setEnabled(false);
            secondCard.setEnabled(false);
            matchedPairs++;
            score += 10;

            if (matchedPairs == 3) {
                Toast.makeText(this, "Game Complete! +" + score + " pts", Toast.LENGTH_LONG).show();
                FirebaseUtils.updatePoints(userId, score);
                finish(); // exit
            }
        } else {
            firstCard.setImageResource(R.drawable.card);
            secondCard.setImageResource(R.drawable.card);
        }

        firstCard = null;
        secondCard = null;
    }
}
