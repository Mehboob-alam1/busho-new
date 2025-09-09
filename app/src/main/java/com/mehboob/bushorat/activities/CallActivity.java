package com.mehboob.bushorat.activities;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.mehboob.bushorat.R;


import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.service.autofill.UserData;
import android.view.View;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;


import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.mehboob.bushorat.databinding.ActivityCallBinding;
import com.mehboob.bushorat.helpers.InterfaceJava;
import com.mehboob.bushorat.models.UserModel;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class CallActivity extends AppCompatActivity {

    ActivityCallBinding binding;
    String uniqueId = "";
    FirebaseAuth auth;
    String username = "";
    String friendsUsername = "";

    boolean isPeerConnected = false;

    DatabaseReference firebaseRef;

    boolean isAudio = true;
    boolean isVideo = true;
    String createdBy;

    boolean pageExit = false;

    boolean isFullScreen = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCallBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());


        auth = FirebaseAuth.getInstance();
        firebaseRef = FirebaseDatabase.getInstance().getReference().child("users");

        username = getIntent().getStringExtra("username");
        String incoming = getIntent().getStringExtra("incoming");
        createdBy = getIntent().getStringExtra("createdBy");

//        friendsUsername = "";
//        if(incoming.equalsIgnoreCase(friendsUsername))
//            friendsUsername = incoming;

        friendsUsername = incoming;

        setupWebView();

        binding.micBtn.setOnClickListener(view -> {
            isAudio = !isAudio;
            callJavaScriptFunction("javascript:toggleAudio(\""+isAudio+"\")");
            if(isAudio){
                binding.micBtn.setImageResource(R.drawable.btn_unmute_normal);
            } else {
                binding.micBtn.setImageResource(R.drawable.btn_mute_normal);
            }
        });

        binding.videoBtn.setOnClickListener(view -> {
            isVideo = !isVideo;
            callJavaScriptFunction("javascript:toggleVideo(\""+isVideo+"\")");
            if(isVideo){
                binding.videoBtn.setImageResource(R.drawable.btn_video_normal);
            } else {
                binding.videoBtn.setImageResource(R.drawable.btn_video_muted);
            }
        });

        binding.endCall.setOnClickListener(view -> finish());


        binding.videoSizeBtn.setOnClickListener(v -> {
            callJavaScriptFunction("javascript:swapVideos()");
        });
    }

    private void toggleVideoSize() {
        if (isFullScreen) {
            // Switch to small floating size
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    500, 800 // Width x Height for small mode
            );
            params.topMargin = 50;
            params.leftMargin = 50;
            binding.webView.setLayoutParams(params);
            binding.videoSizeBtn.setImageResource(R.drawable.ic_fullscreen);
        } else {
            // Switch to fullscreen
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            );
            binding.webView.setLayoutParams(params);
            binding.videoSizeBtn.setImageResource(R.drawable.ic_fullscreen_exit);
        }
        isFullScreen = !isFullScreen;
    }


    void setupWebView() {
        binding.webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    request.grant(request.getResources());
                }
            }
        });

        binding.webView.getSettings().setJavaScriptEnabled(true);
        binding.webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        binding.webView.addJavascriptInterface(new InterfaceJava(this), "Android");

        loadVideoCall();
    }

    public void loadVideoCall() {
        String filePath = "file:android_asset/call.html";
        binding.webView.loadUrl(filePath);

        binding.webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                initializePeer();
            }
        });
    }


    void initializePeer() {
        uniqueId = getUniqueId();

        callJavaScriptFunction("javascript:init(\"" + uniqueId + "\")");

        if(createdBy.equalsIgnoreCase(username)) {
            if(pageExit)
                return;
            firebaseRef.child(username).child("connId").setValue(uniqueId);
            firebaseRef.child(username).child("isAvailable").setValue(true);

            binding.loadingGroup.setVisibility(View.GONE);
            binding.controls.setVisibility(View.VISIBLE);

            FirebaseDatabase.getInstance().getReference()
                    .child("profiles")
                    .child(friendsUsername)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull @NotNull DataSnapshot snapshot) {
                            UserModel user = snapshot.getValue(UserModel.class);


                            binding.name.setText(user.getName());
                            binding.city.setText(user.getWhatsappNumber());

                        }

                        @Override
                        public void onCancelled(@NonNull @NotNull DatabaseError error) {

                        }
                    });

        } else {
            new Handler().postDelayed(() -> {
                friendsUsername = createdBy;
                FirebaseDatabase.getInstance().getReference()
                        .child("profiles")
                        .child(friendsUsername)
                        .addListenerForSingleValueEvent(new ValueEventListener() {
                            @RequiresApi(api = Build.VERSION_CODES.P)
                            @Override
                            public void onDataChange(@NonNull @NotNull DataSnapshot snapshot) {
                              UserModel user=  snapshot.getValue(UserModel.class);

                                binding.name.setText(user.getName());
                                binding.city.setText(user.getWhatsappNumber());

                            }

                            @Override
                            public void onCancelled(@NonNull @NotNull DatabaseError error) {

                            }
                        });
                FirebaseDatabase.getInstance().getReference()
                        .child("users")
                        .child(friendsUsername)
                        .child("connId")
                        .addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull @NotNull DataSnapshot snapshot) {
                                if(snapshot.getValue() != null) {
                                    sendCallRequest();
                                }
                            }

                            @Override
                            public void onCancelled(@NonNull @NotNull DatabaseError error) {

                            }
                        });
            }, 3000);
        }

    }

    public void onPeerConnected(){
        isPeerConnected = true;
    }

    public void onConnectionStatus(String status) {
        runOnUiThread(() -> {
            // Update UI based on connection status
            switch (status) {
                case "connected":
                    // Peer connected but no stream yet
                    binding.connectionStatus.setBackgroundResource(R.drawable.status_indicator_connected);
                    break;
                case "streaming":
                    // Video stream is active
                    binding.connectionStatus.setBackgroundResource(R.drawable.status_indicator_connected);
                    break;
                case "disconnected":
                case "closed":
                case "call_ended":
                    // Connection lost or call ended
                    binding.connectionStatus.setBackgroundResource(R.drawable.status_indicator_disconnected);
                    break;
            }
        });
    }

    public void onError(String error) {
        runOnUiThread(() -> {
            // Show error message to user
            Toast.makeText(CallActivity.this, "Connection Error: " + error, Toast.LENGTH_LONG).show();
        });
    }

    public void onQualityUpdate(String audioQuality, String videoQuality, String connectionType) {
        runOnUiThread(() -> {
            // Update quality indicators in UI
            // You can add quality indicators to the layout and update them here
            android.util.Log.d("CallQuality", "Audio: " + audioQuality + ", Video: " + videoQuality + ", Connection: " + connectionType);
        });
    }

    void sendCallRequest(){
        if(!isPeerConnected) {
            Toast.makeText(this, "You are not connected. Please check your internet.", Toast.LENGTH_SHORT).show();
            return;
        }

        listenConnId();
    }

    void listenConnId() {
        firebaseRef.child(friendsUsername).child("connId").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull @NotNull DataSnapshot snapshot) {
                if(snapshot.getValue() == null)
                    return;
                binding.loadingGroup.setVisibility(View.GONE);
                binding.controls.setVisibility(View.VISIBLE);
                String connId = snapshot.getValue(String.class);
                callJavaScriptFunction("javascript:startCall(\""+connId+"\")");
            }

            @Override
            public void onCancelled(@NonNull @NotNull DatabaseError error) {

            }
        });
    }

    void callJavaScriptFunction(String function){
        binding.webView.post(() -> binding.webView.evaluateJavascript(function, null));
    }




    String getUniqueId(){
        return UUID.randomUUID().toString();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        pageExit = true;
        firebaseRef.child(createdBy).setValue(null);
        finish();
    }
}