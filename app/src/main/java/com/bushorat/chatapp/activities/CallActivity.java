package com.bushorat.chatapp.activities;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.bushorat.chatapp.R;
import com.bushorat.chatapp.databinding.ActivityCallBinding;
import com.bushorat.chatapp.helpers.AdminChatDisplayHelper;
import com.bushorat.chatapp.helpers.AdminChatHelper;
import com.bushorat.chatapp.helpers.InterfaceJava;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CallActivity extends AppCompatActivity {

    ActivityCallBinding binding;
    String uniqueId = "";
    String username = "";
    boolean isPeerConnected = false;
    boolean isAudio = true;
    boolean isVideo = true;
    boolean pageExit = false;

    boolean adminCall = false;
    boolean userInitiatedAdminCall = false;
    String presetConnId;
    boolean videoEnabled = true;

    private ValueEventListener adminCallListener;
    private ValueEventListener adminNameListener;
    private String adminDisplayName = "Support";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCallBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        readIntentExtras(getIntent());
        binding.name.setText(adminDisplayName);
        binding.city.setText("");

        setupWebView();
        bindControls();
        loadAdminDisplayName();
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        readIntentExtras(intent);
        if (adminCall && presetConnId != null && isPeerConnected) {
            connectToAdminPeer();
        }
    }

    private void readIntentExtras(android.content.Intent intent) {
        username = intent.getStringExtra("username");
        adminCall = intent.getBooleanExtra("adminCall", false);
        userInitiatedAdminCall = intent.getBooleanExtra("userInitiatedAdminCall", false);
        presetConnId = intent.getStringExtra("connId");
        videoEnabled = intent.getBooleanExtra("videoEnabled", true);
        if (username == null || username.isEmpty()) {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            username = user != null ? user.getUid() : "";
        }
        isVideo = videoEnabled;
    }

    private void bindControls() {
        binding.micBtn.setOnClickListener(view -> {
            isAudio = !isAudio;
            callJavaScriptFunction("javascript:toggleAudio(\"" + isAudio + "\")");
            binding.micBtn.setImageResource(isAudio ? R.drawable.btn_unmute_normal : R.drawable.btn_mute_normal);
        });

        binding.videoBtn.setOnClickListener(view -> {
            isVideo = !isVideo;
            callJavaScriptFunction("javascript:toggleVideo(\"" + isVideo + "\")");
            binding.videoBtn.setImageResource(isVideo ? R.drawable.btn_video_normal : R.drawable.btn_video_muted);
        });

        binding.endCall.setOnClickListener(view -> finish());
        binding.videoSizeBtn.setOnClickListener(v -> callJavaScriptFunction("javascript:swapVideos()"));
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
        binding.webView.getSettings().setDomStorageEnabled(true);
        binding.webView.addJavascriptInterface(new InterfaceJava(this), "Android");

        binding.webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                initializePeer();
            }
        });
        binding.webView.loadUrl("file:///android_asset/call.html");
    }

    void initializePeer() {
        uniqueId = UUID.randomUUID().toString();
        callJavaScriptFunction("javascript:init(\"" + uniqueId + "\")");
    }

    public void onPeerConnected() {
        isPeerConnected = true;
        mainHandler.post(() -> {
            if (adminCall && presetConnId != null && !presetConnId.isEmpty()) {
                connectToAdminPeer();
                return;
            }
            if (userInitiatedAdminCall) {
                startUserInitiatedAdminCall();
                return;
            }
            binding.loadingGroup.setVisibility(View.GONE);
            binding.controls.setVisibility(View.VISIBLE);
        });
    }

    public void onPeerError(String message) {
        mainHandler.post(() ->
                Toast.makeText(this, message != null ? message : "Call error", Toast.LENGTH_SHORT).show()
        );
    }

    private void connectToAdminPeer() {
        binding.name.setText(adminDisplayName);
        binding.city.setText(getString(R.string.chat_toolbar_subtitle));
        if (!videoEnabled) {
            isVideo = false;
            callJavaScriptFunction("javascript:toggleVideo(\"false\")");
            binding.videoBtn.setImageResource(R.drawable.btn_video_muted);
        }
        binding.loadingGroup.setVisibility(View.GONE);
        binding.controls.setVisibility(View.VISIBLE);
        callJavaScriptFunction("javascript:startCall(\"" + presetConnId + "\", \"" + videoEnabled + "\")");
    }

    private void startUserInitiatedAdminCall() {
        binding.name.setText(adminDisplayName);
        binding.city.setText(R.string.chat_call_waiting_admin);
        AdminChatHelper.requestCallFromUser(username, videoEnabled, uniqueId);
        listenForAdminAnswer();
    }

    private void listenForAdminAnswer() {
        if (adminCallListener != null) {
            AdminChatHelper.callsRef(username).removeEventListener(adminCallListener);
        }
        adminCallListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull @NotNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    return;
                }
                String connId = snapshot.child("connId").getValue(String.class);
                String from = snapshot.child("from").getValue(String.class);
                String status = snapshot.child("status").getValue(String.class);
                if (connId == null || connId.isEmpty()) {
                    return;
                }
                if (!AdminChatHelper.SENDER_ADMIN.equals(from)) {
                    return;
                }
                if ("incoming".equals(status) || "active".equals(status)) {
                    presetConnId = connId;
                    adminCall = true;
                    String callType = snapshot.child("callType").getValue(String.class);
                    videoEnabled = !"audio".equals(callType);
                    if (isPeerConnected) {
                        connectToAdminPeer();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull @NotNull DatabaseError error) {
            }
        };
        AdminChatHelper.callsRef(username).addValueEventListener(adminCallListener);
    }

    private void loadAdminDisplayName() {
        adminNameListener = AdminChatDisplayHelper.watchAdminDisplayName(name -> {
            adminDisplayName = name;
            mainHandler.post(() -> binding.name.setText(name));
        });
    }

    void callJavaScriptFunction(String function) {
        binding.webView.post(() -> binding.webView.evaluateJavascript(function, null));
    }

    @Override
    protected void onDestroy() {
        pageExit = true;
        if (adminNameListener != null) {
            AdminChatDisplayHelper.removeListener(adminNameListener);
        }
        if (username != null && adminCallListener != null) {
            AdminChatHelper.callsRef(username).removeEventListener(adminCallListener);
        }
        if (username != null && !username.isEmpty()) {
            if (userInitiatedAdminCall && !adminCall) {
                AdminChatHelper.callsRef(username).removeValue();
            } else {
                Map<String, Object> clear = new HashMap<>();
                clear.put("status", "ended");
                AdminChatHelper.callsRef(username).updateChildren(clear);
            }
        }
        super.onDestroy();
    }
}
