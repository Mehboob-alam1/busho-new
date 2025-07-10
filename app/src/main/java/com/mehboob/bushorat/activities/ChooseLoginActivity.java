package com.mehboob.bushorat.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.mehboob.bushorat.R;
import com.mehboob.bushorat.databinding.ActivityChooseLoginBinding;

public class ChooseLoginActivity extends AppCompatActivity {

    private ActivityChooseLoginBinding binding;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding= ActivityChooseLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());



        binding.btnFacebookSignIn.setOnClickListener(v -> startActivity(new Intent(ChooseLoginActivity.this,RegisterFacebookActivity.class)));


        binding.btnGoogleSignIn.setOnClickListener(v -> startActivity(new Intent(ChooseLoginActivity.this,RegisterGoogleActivity.class)));


    }
}