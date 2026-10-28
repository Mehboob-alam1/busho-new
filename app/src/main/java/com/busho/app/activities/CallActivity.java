package com.busho.app.activities;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.busho.app.R;
import com.busho.app.databinding.ActivityCallBinding;
import com.busho.app.helpers.AdminCallNotificationHelper;
import com.busho.app.helpers.AdminChatDisplayHelper;
import com.busho.app.helpers.AdminChatHelper;
import com.busho.app.helpers.CallRingtonePlayer;
import com.busho.app.helpers.FeaturePermissions;
import com.busho.app.helpers.InterfaceJava;

import org.jetbrains.annotations.NotNull;
import java.util.UUID;

public class CallActivity extends AppCompatActivity {

    private static volatile boolean callScreenVisible;

    public static boolean isOnCallScreen() {
        return callScreenVisible;
    }

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
    boolean userAccepted;

    private ValueEventListener adminCallListener;
    private ValueEventListener callSessionListener;
    private ValueEventListener adminNameListener;
    private boolean finishingCall;
    private boolean callSignalEverPresent;
    private String adminDisplayName = "Support";
    private boolean incomingRing;
    private boolean remoteConnected;

    private final CallRingtonePlayer ringtonePlayer = new CallRingtonePlayer();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean webViewStarted;

    private final ActivityResultLauncher<String[]> requestCallPermissions =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (FeaturePermissions.hasCallPermissions(this)) {
                    ensureWebViewStarted();
                } else {
                    finish();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCallBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        readIntentExtras(getIntent());
        if (adminCall && !userInitiatedAdminCall && !userAccepted) {
            redirectToIncomingScreen();
            return;
        }
        AdminCallNotificationHelper.cancel(this);
        binding.name.setText(adminDisplayName);
        binding.city.setText("");

        bindControls();
        loadAdminDisplayName();

        if (FeaturePermissions.hasCallPermissions(this)) {
            ensureWebViewStarted();
        } else {
            requestCallPermissions.launch(FeaturePermissions.callPermissions());
        }
        attachCallSessionListener();
    }

    private void ensureWebViewStarted() {
        if (webViewStarted) {
            return;
        }
        webViewStarted = true;
        setupWebView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        callScreenVisible = true;
    }

    @Override
    protected void onPause() {
        callScreenVisible = false;
        super.onPause();
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        readIntentExtras(intent);
        AdminCallNotificationHelper.cancel(this);
        if (adminCall && presetConnId != null && isPeerConnected) {
            publishUserPeerAndConnect();
        }
    }

    private void redirectToIncomingScreen() {
        if (presetConnId == null || presetConnId.isEmpty()) {
            finish();
            return;
        }
        startActivity(AdminCallNotificationHelper.buildCallIntent(this, username, presetConnId, videoEnabled));
        finish();
    }

    private void readIntentExtras(android.content.Intent intent) {
        username = intent.getStringExtra("username");
        adminCall = intent.getBooleanExtra("adminCall", false);
        userInitiatedAdminCall = intent.getBooleanExtra("userInitiatedAdminCall", false);
        presetConnId = intent.getStringExtra("connId");
        videoEnabled = intent.getBooleanExtra("videoEnabled", true);
        userAccepted = intent.getBooleanExtra("userAccepted", false);
        incomingRing = intent.getBooleanExtra("incomingRing", false);
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

        binding.endCall.setOnClickListener(view -> endCallAndClose());
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
        callJavaScriptFunction("javascript:init(" + jsQuote(uniqueId) + ")");
    }

    public void onPeerConnected() {
        isPeerConnected = true;
        mainHandler.post(() -> {
            if (adminCall && presetConnId != null && !presetConnId.isEmpty()) {
                publishUserPeerAndConnect();
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
        // Errors are handled in the WebView call UI; no toast.
    }

    public void onRemoteConnected() {
        remoteConnected = true;
        mainHandler.post(() -> {
            ringtonePlayer.stop();
            AdminCallNotificationHelper.cancel(this);
            if (username != null && !username.isEmpty()) {
                AdminChatHelper.callsRef(username).child("status").setValue("active");
            }
        });
    }

    private void publishUserPeerAndConnect() {
        binding.city.setText(R.string.chat_call_connecting);
        if (username != null && !username.isEmpty() && uniqueId != null && !uniqueId.isEmpty()) {
            AdminChatHelper.callsRef(username).child("userPeerId").setValue(uniqueId);
            AdminChatHelper.callsRef(username).child("status").setValue("active");
        }
        mainHandler.postDelayed(this::connectToAdminPeer, 1500);
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
        callJavaScriptFunction(
                "javascript:startCall(" + jsQuote(presetConnId) + ", " + jsQuote(String.valueOf(videoEnabled)) + ")"
        );
    }

    private static String jsQuote(String value) {
        if (value == null) {
            return "\"\"";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private void startUserInitiatedAdminCall() {
        binding.name.setText(adminDisplayName);
        binding.city.setText(R.string.chat_call_waiting_admin);
        AdminChatHelper.requestCallFromUser(username, videoEnabled, uniqueId);
        listenForAdminAnswer();
    }

    private void attachCallSessionListener() {
        if (username == null || username.isEmpty()) {
            return;
        }
        if (callSessionListener != null) {
            AdminChatHelper.callsRef(username).removeEventListener(callSessionListener);
        }
        callSessionListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull @NotNull DataSnapshot snapshot) {
                if (finishingCall || pageExit) {
                    return;
                }
                if (!snapshot.exists()) {
                    if (callSignalEverPresent || remoteConnected) {
                        mainHandler.post(CallActivity.this::endCallLocally);
                    }
                    return;
                }
                callSignalEverPresent = true;
                String status = snapshot.child("status").getValue(String.class);
                if ("ended".equals(status)) {
                    mainHandler.post(CallActivity.this::endCallLocally);
                }
            }

            @Override
            public void onCancelled(@NonNull @NotNull DatabaseError error) {
            }
        };
        AdminChatHelper.callsRef(username).addValueEventListener(callSessionListener);
    }

    private void endCallAndClose() {
        endCallLocally();
    }

    private void endCallLocally() {
        if (finishingCall) {
            return;
        }
        finishingCall = true;
        ringtonePlayer.stop();
        AdminCallNotificationHelper.cancel(this);
        callJavaScriptFunction("javascript:hangup()");
        if (username != null && !username.isEmpty()) {
            AdminChatHelper.clearActiveCall(username);
        }
        finish();
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
        callScreenVisible = false;
        ringtonePlayer.stop();
        AdminCallNotificationHelper.cancel(this);
        if (adminNameListener != null) {
            AdminChatDisplayHelper.removeListener(adminNameListener);
        }
        if (username != null && adminCallListener != null) {
            AdminChatHelper.callsRef(username).removeEventListener(adminCallListener);
        }
        if (username != null && callSessionListener != null) {
            AdminChatHelper.callsRef(username).removeEventListener(callSessionListener);
        }
        if (!finishingCall && username != null && !username.isEmpty()) {
            AdminChatHelper.clearActiveCall(username);
        }
        super.onDestroy();
    }
}
