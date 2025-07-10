package com.mehboob.bushorat.activities;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.mehboob.bushorat.R;
import com.mehboob.bushorat.databinding.ActivityRegisterGoogleBinding;
import com.mehboob.bushorat.helpers.AuthHelper;
import com.mehboob.bushorat.models.UserModel;

public class RegisterGoogleActivity extends AppCompatActivity {

 private ActivityRegisterGoogleBinding binding;

    private AuthHelper authHelper;
    private ProgressDialog loadingDialog;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityRegisterGoogleBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        authHelper = new AuthHelper();

        // Initialize loading dialog
        loadingDialog = new ProgressDialog(this);
        loadingDialog.setMessage("Creating account...");
        loadingDialog.setCancelable(false);



        binding.btnSignupG.setOnClickListener(v -> {
            String name = binding.etNameG.getText().toString().trim();
            String email = binding.etGmailG.getText().toString().trim();
            String password = binding.etPasswordG.getText().toString().trim();
            String whatsapp = binding.etWhatsappG.getText().toString().trim();
            String address = binding.etAddressG.getText().toString().trim();

            if (name.isEmpty() || email.isEmpty() || password.isEmpty() || whatsapp.isEmpty() || address.isEmpty()) {
                Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show();
                return;
            }

            // Show loading
            loadingDialog.show();

            // Create user model
            UserModel user = new UserModel(name, email, password, whatsapp, address,"");

            authHelper.registerUser(user, new AuthHelper.AuthCallback() {
                @Override
                public void onSuccess() {
                    loadingDialog.dismiss();
                    Toast.makeText(RegisterGoogleActivity.this, "Signup Successful!", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onFailure(String errorMessage) {
                    loadingDialog.dismiss();
                    Toast.makeText(RegisterGoogleActivity.this, "Error: " + errorMessage, Toast.LENGTH_LONG).show();
                }
            });

        });





    }
}