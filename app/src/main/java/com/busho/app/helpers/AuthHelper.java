package com.busho.app.helpers;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.busho.app.models.UserModel;

public class AuthHelper {
    private static final String TAG = "AuthHelper";

    private final FirebaseAuth mAuth;
    private final DatabaseReference mDatabase;

    public AuthHelper() {
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference("users");
        Log.d(TAG, "AuthHelper: Firebase Auth instance created");
        Log.d(TAG, "AuthHelper: Current user = " + (mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : "null"));
    }

    public interface AuthCallback {
        void onSuccess();
        void onFailure(String errorMessage);
    }

    public void signInUser(String email, String password, AuthCallback callback) {
        Log.d(TAG, "signInUser: Attempting sign-in for " + email);
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser firebaseUser = mAuth.getCurrentUser();
                        Log.d(TAG, "signInUser: sign-in success, user = " + (firebaseUser != null ? firebaseUser.getUid() : "null"));
                        callback.onSuccess();
                    } else {
                        String msg = task.getException() != null ? task.getException().getMessage() : "Sign-in failed";
                        Log.e(TAG, "signInUser: sign-in failed - " + msg);
                        callback.onFailure(msg);
                    }
                });
    }

    public void registerUser(UserModel user, AuthCallback callback) {
        Log.d(TAG, "registerUser: Starting user registration");
        Log.d(TAG, "registerUser: Email = " + user.getGmail());
        
        mAuth.createUserWithEmailAndPassword(user.getGmail(), user.getPassword())
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "registerUser: Firebase Auth successful");
                        
                        FirebaseUser firebaseUser = mAuth.getCurrentUser();
                        if (firebaseUser != null) {
                            String userId = firebaseUser.getUid();
                            Log.d(TAG, "registerUser: User ID = " + userId);
                            Log.d(TAG, "registerUser: User persisted = " + firebaseUser.toString());
                            
                            user.setUserId(userId);
                            mDatabase.child(userId).setValue(user)
                                    .addOnSuccessListener(unused -> {
                                        Log.d(TAG, "registerUser: User profile saved to database");
                                        Log.d(TAG, "registerUser: Checking Firebase persistence - CurrentUser = " + 
                                              (mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : "null"));
                                        // Send email verification if needed
                                        if (firebaseUser.getEmail() != null && !firebaseUser.isEmailVerified()) {
                                            firebaseUser.sendEmailVerification()
                                                    .addOnCompleteListener(task1 -> {
                                                        Log.d(TAG, "registerUser: sendEmailVerification completed: " + task1.isSuccessful());
                                                        callback.onSuccess();
                                                    });
                                        } else {
                                            callback.onSuccess();
                                        }
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "registerUser: Failed to save profile - " + e.getMessage());
                                        callback.onFailure(e.getMessage());
                                    });
                        } else {
                            Log.e(TAG, "registerUser: FirebaseUser is null after successful auth!");
                            callback.onFailure("Authentication succeeded but user is null");
                        }
                    } else {
                            String err = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                            Log.e(TAG, "registerUser: Firebase Auth failed - " + err);
                            // If email already in use, try sign-in instead
                            if (err != null && err.toLowerCase().contains("already in use")) {
                                Log.d(TAG, "registerUser: Email already in use, falling back to sign-in");
                                signInUser(user.getGmail(), user.getPassword(), new AuthCallback() {
                                    @Override
                                    public void onSuccess() {
                                        callback.onSuccess();
                                    }

                                    @Override
                                    public void onFailure(String errorMessage) {
                                        callback.onFailure(errorMessage);
                                    }
                                });
                            } else {
                                callback.onFailure(err);
                            }
                    }
                });
    }
}
