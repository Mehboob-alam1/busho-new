package com.mehboob.bushorat.helpers;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.mehboob.bushorat.models.UserModel;

public class AuthHelper {

    private final FirebaseAuth mAuth;
    private final DatabaseReference mDatabase;

    public AuthHelper() {
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference("profiles");
    }

    public interface AuthCallback {
        void onSuccess();
        void onFailure(String errorMessage);
    }

    public void registerUser(UserModel user, AuthCallback callback) {
        mAuth.createUserWithEmailAndPassword(user.getGmail(), user.getPassword())
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        String userId = mAuth.getCurrentUser().getUid();
                        user.setUserId(userId);
                        mDatabase.child(userId).setValue(user)
                                .addOnSuccessListener(unused -> callback.onSuccess())
                                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
                    } else {
                        callback.onFailure(task.getException().getMessage());
                    }
                });
    }
}
