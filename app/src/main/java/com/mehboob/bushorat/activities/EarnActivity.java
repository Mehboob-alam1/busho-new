package com.mehboob.bushorat.activities;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mehboob.bushorat.R;
import com.mehboob.bushorat.adapters.GameAdapter;
import com.mehboob.bushorat.models.Game;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EarnActivity extends AppCompatActivity {

    RecyclerView recyclerView;
    List<Game> gameList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_earn);
        recyclerView = findViewById(R.id.recyclerView);

        gameList = Arrays.asList(
                new Game("Marble Shooter", "https://cdn.htmlgames.com/MarbleShooter/", R.drawable.marble_shooter),
                new Game("Rotate Puzzle", "https://cdn.htmlgames.com/RotatePuzzleSummerBeach/", R.drawable.rotate_puzzle),
                new Game("Hidden Objects", "https://cdn.htmlgames.com/StreetHiddenObjects/", R.drawable.hidden_objects),
                new Game("Higher or Lower", "https://cdn.htmlgames.com/HigherOrLower/", R.drawable.higher_lower),
                new Game("Flower World", "https://cdn.htmlgames.com/FlowerWorld2/", R.drawable.flower_world),
                new Game("Japanese Garden", "https://cdn.htmlgames.com/JapaneseGarden-HiddenSecrets/", R.drawable.japanese_garden),
                new Game("Love Bubbles", "https://cdn.htmlgames.com/LoveBubbles/", R.drawable.love_bubbles),
                new Game("Triple Ice Age", "https://cdn.htmlgames.com/TripleDimensionsIceAge/", R.drawable.triple_ice),
                new Game("Ninja Breakout", "https://cdn.htmlgames.com/NinjaBreakout/", R.drawable.ninja_breakout),
                new Game("Red & Green 2", "https://cdn.htmlgames.com/RedAndGreen2/", R.drawable.red_green),
                new Game("Odd One Out", "https://cdn.htmlgames.com/FindTheOddOneOut/", R.drawable.odd_one),
                new Game("Jewels of Medici", "https://cdn.htmlgames.com/JewelsOfTheMedici/", R.drawable.jewels_medici),
                new Game("Delicious Duos", "https://cdn.htmlgames.com/DeliciousDuos/", R.drawable.delicious_duos),
                new Game("Hidden Spots City", "https://cdn.htmlgames.com/HiddenSpotsCity/", R.drawable.hidden_spots),
                new Game("Christmas Match 3", "https://cdn.htmlgames.com/ChristmasMatch3/", R.drawable.christmas_match),
                new Game("Car Park Sort", "https://cdn.htmlgames.com/CarParkSort/", R.drawable.car_park),
                new Game("Connect the Bubbles", "https://cdn.htmlgames.com/ConnectTheBubbles/", R.drawable.connect_bubbles),
                new Game("Mahjongg Pyramids", "https://cdn.htmlgames.com/MahjonggPyramids/", R.drawable.mahjong),
                new Game("Bottle Shooter", "https://cdn.htmlgames.com/BottleShooter/", R.drawable.bottle_shooter),
                new Game("Hexa Merge", "https://cdn.htmlgames.com/HexaMerge/", R.drawable.hexa_merge),
                new Game("1010 Halloween", "https://cdn.htmlgames.com/1010Halloween/", R.drawable.halloween),
                new Game("Bounce Ball 2", "https://cdn.htmlgames.com/BounceBall2/", R.drawable.bounce_ball),
                new Game("Candy House", "https://cdn.htmlgames.com/CandyHouse/", R.drawable.candy_house),
                new Game("Carrom Pool", "https://cdn.htmlgames.com/CarromPool/", R.drawable.carrom_pool)
        );

        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        recyclerView.setAdapter(new GameAdapter(gameList, this));
    }
}
