package com.bushorat.chatapp.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ValueEventListener;
import com.bushorat.chatapp.R;
import com.bushorat.chatapp.databinding.ActivityIncomingCallBinding;
import com.bushorat.chatapp.helpers.AdminCallNotificationHelper;
import com.bushorat.chatapp.helpers.AdminCallWatcher;
import com.bushorat.chatapp.helpers.AdminChatDisplayHelper;
import com.bushorat.chatapp.helpers.AdminChatHelper;
import com.bushorat.chatapp.helpers.CallRingtonePlayer;
import com.bushorat.chatapp.helpers.SessionManager;

/**
 * Full-screen incoming call from admin — user taps Answer to open the live call room.
 */
public class IncomingCallActivity extends AppCompatActivity {

    private static volatile boolean visible;

    public static boolean isVisible() {
        return visible;
    }

    private ActivityIncomingCallBinding binding;
    private final CallRingtonePlayer ringtonePlayer = new CallRingtonePlayer();
    private ValueEventListener adminNameListener;

    private String userId;
    private String connId;
    private boolean videoEnabled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        );

        binding = ActivityIncomingCallBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        readExtras();
        if (userId == null || userId.isEmpty() || connId == null || connId.isEmpty()) {
            finish();
            return;
        }

        AdminCallNotificationHelper.cancel(this);
        binding.tvCallType.setText(
                videoEnabled ? R.string.chat_incoming_video : R.string.chat_incoming_audio
        );

        adminNameListener = AdminChatDisplayHelper.watchAdminDisplayName(name ->
                runOnUiThread(() -> binding.tvCallerName.setText(name))
        );

        binding.btnAnswer.setOnClickListener(v -> answerCall());
        binding.btnDecline.setOnClickListener(v -> declineCall());

        ringtonePlayer.start(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
    }

    @Override
    protected void onPause() {
        visible = false;
        super.onPause();
    }

    private void readExtras() {
        Intent intent = getIntent();
        userId = intent.getStringExtra("username");
        connId = intent.getStringExtra("connId");
        videoEnabled = intent.getBooleanExtra("videoEnabled", true);
        if (userId == null || userId.isEmpty()) {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            userId = user != null ? user.getUid() : new SessionManager(this).getUserId();
        }
    }

    private void answerCall() {
        ringtonePlayer.stop();
        AdminCallNotificationHelper.cancel(this);
        Intent call = new Intent(this, CallActivity.class);
        call.putExtra("username", userId);
        call.putExtra("adminCall", true);
        call.putExtra("userInitiatedAdminCall", false);
        call.putExtra("userAccepted", true);
        call.putExtra("connId", connId);
        call.putExtra("videoEnabled", videoEnabled);
        call.putExtra("incomingRing", false);
        startActivity(call);
        finish();
    }

    private void declineCall() {
        ringtonePlayer.stop();
        AdminCallNotificationHelper.cancel(this);
        AdminCallWatcher.clearLastHandled(this);
        AdminChatHelper.clearActiveCall(userId);
        finish();
    }

    @Override
    protected void onDestroy() {
        ringtonePlayer.stop();
        if (adminNameListener != null) {
            AdminChatDisplayHelper.removeListener(adminNameListener);
        }
        super.onDestroy();
    }
}
