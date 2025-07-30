package com.mehboob.bushorat.activities;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.viewpager.widget.ViewPager;


import com.google.android.material.tabs.TabLayout;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.mehboob.bushorat.R;
import com.mehboob.bushorat.adapters.SliderAdapter;
import com.mehboob.bushorat.adapters.ViewPagerAdapter;
import com.mehboob.bushorat.fragments.Action;
import com.mehboob.bushorat.fragments.Adventure;
import com.mehboob.bushorat.fragments.Arcade;
import com.mehboob.bushorat.fragments.Puzzle;
import com.mehboob.bushorat.fragments.Racing;
import com.mehboob.bushorat.fragments.Sports;
import com.mehboob.bushorat.fragments.Zombie;
import com.mehboob.bushorat.models.Slider;
import com.smarteist.autoimageslider.IndicatorView.animation.type.IndicatorAnimationType;
import com.smarteist.autoimageslider.SliderAnimations;
import com.smarteist.autoimageslider.SliderView;


import java.util.ArrayList;

public class EarnActivity extends AppCompatActivity implements Action.OnFragmentInteractionListener,
        Arcade.OnFragmentInteractionListener, Puzzle.OnFragmentInteractionListener,
        Adventure.OnFragmentInteractionListener, Sports.OnFragmentInteractionListener,
        Racing.OnFragmentInteractionListener, Zombie.OnFragmentInteractionListener {

    TabLayout tabLayout;
    ViewPager viewPager;
    ViewPagerAdapter viewPagerAdapter;
    Toolbar toolbar;
    Dialog Developer;
    TextView txtclose;
    TextView facebook;
    SliderView sliderView;
    private DatabaseReference databaseReference;
    private ArrayList<Slider> list;
    SliderAdapter adapter;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_earn);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitleTextColor(0xFF0F0E37);
        list = new ArrayList<>();
        databaseReference = FirebaseDatabase.getInstance().getReference("Banners");

        fetchBanners();
        tabLayout = (TabLayout) findViewById(R.id.tabLayout);
        viewPager = (ViewPager) findViewById(R.id.viewPager);
        sliderView = findViewById(R.id.imageSlider);

        viewPagerAdapter = new ViewPagerAdapter(getSupportFragmentManager());
        viewPagerAdapter.addFragments(new Action(), "ACTION");
        viewPagerAdapter.addFragments(new Racing(), "RACING");
        viewPagerAdapter.addFragments(new Puzzle(), "PUZZLE");
        viewPagerAdapter.addFragments(new Sports(), "SPORTS");
        viewPagerAdapter.addFragments(new Arcade(), "ARCADE");
        viewPagerAdapter.addFragments(new Adventure(), "ADVENTURE");
        viewPagerAdapter.addFragments(new Zombie(), "ZOMBIE");

        viewPager.setAdapter(viewPagerAdapter);
        tabLayout.setupWithViewPager(viewPager);
        Developer = new Dialog(this);
    }

    private void setSlider() {





        sliderView.setIndicatorAnimation(IndicatorAnimationType.WORM); //set indicator animation by using IndicatorAnimationType. :WORM or THIN_WORM or COLOR or DROP or FILL or NONE or SCALE or SCALE_DOWN or SLIDE and SWAP!!
        sliderView.setSliderTransformAnimation(SliderAnimations.SIMPLETRANSFORMATION);
        sliderView.setAutoCycleDirection(SliderView.AUTO_CYCLE_DIRECTION_BACK_AND_FORTH);
        sliderView.setIndicatorSelectedColor(Color.WHITE);
        sliderView.setIndicatorUnselectedColor(Color.GRAY);
        sliderView.setScrollTimeInSec(4); //set scroll delay in seconds :
        sliderView.startAutoCycle();

    }

    private void fetchBanners() {
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    for (DataSnapshot snap : snapshot.getChildren()) {

                        Slider slider = snap.getValue(Slider.class);
                        list.add(new Slider(slider.getImageUrl(), slider.getImageLink(), slider.getPushId()));
                    }
                    adapter= new SliderAdapter(list,EarnActivity.this);
                    sliderView.setSliderAdapter(adapter);
                    setSlider();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.home, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

//            case R.id.about:
//                Developer.setContentView(R.layout.about);
//                txtclose =(TextView) Developer.findViewById(R.id.txtclose);
//                facebook = (TextView) Developer.findViewById(R.id.facebook);
//                facebook.setOnClickListener(new View.OnClickListener() {
//                    @Override
//                    public void onClick(View view) {
//                        TextView facebook = (TextView) findViewById(R.id.facebook);
//                        Intent openFB = new Intent(Intent.ACTION_VIEW,
//                                Uri.parse("https://facebook.com/hacku2day/"));
//                        startActivity(openFB);
//                    }
//                });
//                txtclose.setOnClickListener(new View.OnClickListener() {
//                    @Override
//                    public void onClick(View view) {
//                        Developer.dismiss();
//                    }
//                });
//                Developer.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
//                Developer.show();
//                break;

            case R.id.share:
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                final String appPackageName = this.getPackageName();
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
                String shareTitile = "Share the app";
                String shareLink = "https://play.google.com/store/apps/details?id=" + appPackageName;
                intent.putExtra(Intent.EXTRA_SUBJECT, shareTitile);
                intent.putExtra(Intent.EXTRA_TEXT, shareLink);
                startActivity(Intent.createChooser(intent, "Share All In One Games 2023"));
                break;
            default:
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(EarnActivity.this).inflate(R.layout.close_alert, null);
        TextView title = (TextView) view.findViewById(R.id.title);
        ImageButton imageButton = (ImageButton) view.findViewById(R.id.image);
        title.setText("Hello There!");
        imageButton.setImageResource(R.drawable.exit);

        dialog.setPositiveButton(Html.fromHtml("<font color='#D800FF'>EXIT ME</font>"), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        });

        dialog.setCancelable(false);
        dialog.setNegativeButton(Html.fromHtml("<font color='#FF7F27'>NOT NOW</font>"), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        dialog.setView(view);
        dialog.show();

    }

    @Override
    public void onFragmentInteraction(Uri uri) {

    }
}