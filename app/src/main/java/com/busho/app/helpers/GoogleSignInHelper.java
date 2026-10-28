package com.busho.app.helpers;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.busho.app.R;

import java.util.HashMap;
import java.util.Map;

public class GoogleSignInHelper {
    private static final String TAG = "GoogleSignInHelper";

    private final FirebaseAuth firebaseAuth;
    private final GoogleSignInClient googleSignInClient;

    public GoogleSignInHelper(Context context) {
        firebaseAuth = FirebaseAuth.getInstance();
        googleSignInClient = GoogleSignIn.getClient(context, buildSignInOptions(context));
    }

    public Intent getSignInIntent() {
        return googleSignInClient.getSignInIntent();
    }

    public boolean isConfigured(Context context) {
        return !TextUtils.isEmpty(getWebClientId(context));
    }

    public void handleSignInResult(Intent data, SignInCallback callback) {
        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            if (account == null || TextUtils.isEmpty(account.getIdToken())) {
                callback.onFailure("Google sign-in did not return a valid account");
                return;
            }
            firebaseAuthWithGoogle(account, callback);
        } catch (ApiException e) {
            Log.e(TAG, "Google sign-in failed", e);
            callback.onFailure("Google sign-in failed: " + e.getStatusCode());
        }
    }

    private void firebaseAuthWithGoogle(@NonNull GoogleSignInAccount account, SignInCallback callback) {
        AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
        firebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        String message = task.getException() != null
                                ? task.getException().getMessage()
                                : "Firebase authentication failed";
                        callback.onFailure(message);
                        return;
                    }

                    FirebaseUser user = firebaseAuth.getCurrentUser();
                    if (user == null) {
                        callback.onFailure("Signed in but user is null");
                        return;
                    }

                    saveGoogleUserProfile(user, account);
                    callback.onSuccess(user);
                });
    }

    private void saveGoogleUserProfile(FirebaseUser user, GoogleSignInAccount account) {
        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users").child(user.getUid());

        Map<String, Object> profile = new HashMap<>();
        profile.put("userId", user.getUid());
        profile.put("email", user.getEmail());
        profile.put("fullName", account.getDisplayName() != null ? account.getDisplayName() : "");
        profile.put("displayName", account.getDisplayName() != null ? account.getDisplayName() : "");
        profile.put("loginType", "Google");
        profile.put("emailVerified", true);
        profile.put("lastLogin", System.currentTimeMillis());
        profile.put("lastSeen", System.currentTimeMillis());

        usersRef.get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()) {
                profile.put("createdAt", System.currentTimeMillis());
            }
            usersRef.updateChildren(profile);
            FirebaseDataPaths.profile(user.getUid()).updateChildren(profile);
        });
    }

    private GoogleSignInOptions buildSignInOptions(Context context) {
        GoogleSignInOptions.Builder builder = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail();

        String webClientId = getWebClientId(context);
        if (!TextUtils.isEmpty(webClientId)) {
            builder.requestIdToken(webClientId);
        }

        return builder.build();
    }

    private String getWebClientId(Context context) {
        int resId = context.getResources().getIdentifier("default_web_client_id", "string", context.getPackageName());
        if (resId == 0) {
            return null;
        }
        String value = context.getString(resId);
        return TextUtils.isEmpty(value) ? null : value;
    }

    public interface SignInCallback {
        void onSuccess(FirebaseUser user);
        void onFailure(String errorMessage);
    }
}
