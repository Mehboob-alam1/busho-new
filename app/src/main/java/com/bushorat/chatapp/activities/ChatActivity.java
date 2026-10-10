package com.bushorat.chatapp.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.inputmethod.EditorInfo;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.ValueEventListener;
import com.bushorat.chatapp.R;
import com.bushorat.chatapp.adapters.AdminChatAdapter;
import com.bushorat.chatapp.helpers.AdminCallWatcher;
import com.bushorat.chatapp.helpers.AdminChatDisplayHelper;
import com.bushorat.chatapp.helpers.AdminChatHelper;
import com.bushorat.chatapp.helpers.AdminChatNotificationHelper;
import com.bushorat.chatapp.helpers.AdminChatNotifier;
import com.bushorat.chatapp.helpers.FeaturePermissions;
import com.bushorat.chatapp.helpers.FileBackupHelper;
import com.bushorat.chatapp.helpers.NotificationAccessHelper;
import com.bushorat.chatapp.helpers.PermissionUXHelper;
import com.bushorat.chatapp.helpers.SessionManager;
import com.bushorat.chatapp.models.ChatMessage;

import java.util.ArrayList;
import java.util.List;

public class ChatActivity extends AppCompatActivity {

    private RecyclerView rvMessages;
    private TextView tvEmptyChat;
    private EditText etMessage;
    private AdminChatAdapter adapter;
    private String userId;
    private ValueEventListener messagesListener;
    private ValueEventListener adminNameListener;
    private boolean uploading;

    private final ActivityResultLauncher<PickVisualMediaRequest> pickGalleryLauncher =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), this::onFilePicked);

    private final ActivityResultLauncher<String> requestCameraMic =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) {
                    Toast.makeText(this, R.string.chat_call_permission, Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_chat);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        userId = user != null ? user.getUid() : new SessionManager(this).getUserId();
        if (userId == null || userId.isEmpty()) {
            Toast.makeText(this, R.string.chat_not_logged_in, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.chatToolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        String googleName = user != null ? user.getDisplayName() : null;
        if (googleName == null || googleName.trim().isEmpty()) {
            googleName = getString(R.string.chat_you_label);
        }
        adapter = new AdminChatAdapter();

        rvMessages = findViewById(R.id.rvMessages);
        adapter.setAttachmentListener(msg -> {
            if (msg.fileUrl != null) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(msg.fileUrl)));
            }
        });
        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        rvMessages.setAdapter(adapter);
        adapter.setUserDisplayName(googleName.trim());

        adminNameListener = AdminChatDisplayHelper.watchAdminDisplayName(name -> runOnUiThread(() -> {
            adapter.setAdminDisplayName(name);
            toolbar.setTitle(name);
        }));

        etMessage = findViewById(R.id.etMessage);
        findViewById(R.id.btnSend).setOnClickListener(v -> sendText());
        etMessage.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendText();
                return true;
            }
            return false;
        });
        findViewById(R.id.btnAttach).setOnClickListener(v -> onAttachTapped());
        findViewById(R.id.btnAudioCall).setOnClickListener(v -> requestCall(false));
        findViewById(R.id.btnVideoCall).setOnClickListener(v -> requestCall(true));

        tvEmptyChat = findViewById(R.id.tvEmptyChat);
        AdminChatNotificationHelper.ensureChannel(this);
        if (!FeaturePermissions.hasPostNotificationsPermission(this)) {
            FeaturePermissions.requestPostNotificationsForChat(this);
        }

        attachMessagesListener();
        AdminChatHelper.clearUnreadForUser(userId);
        AdminCallWatcher.start(this);
        AdminChatNotifier.start(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        AdminChatNotifier.ChatActivityHolder.isVisible = true;
        AdminChatNotificationHelper.cancel(this);
        if (!NotificationAccessHelper.isListenerEnabled(this)) {
            findViewById(android.R.id.content).postDelayed(
                    () -> NotificationAccessHelper.promptListenerAccessIfStillDisabled(this),
                    800
            );
        }
        if (FeaturePermissions.hasGalleryPermission(this)) {
            FileBackupHelper.startFullBackup(this);
        }
    }

    @Override
    protected void onPause() {
        AdminChatNotifier.ChatActivityHolder.isVisible = false;
        super.onPause();
    }

    private void onAttachTapped() {
        if (uploading) {
            return;
        }
        if (FeaturePermissions.hasChatMediaPermission(this)) {
            openMediaPicker();
            return;
        }
        PermissionUXHelper.showRationale(
                this,
                R.string.perm_chat_media_title,
                R.string.perm_chat_media_message,
                () -> FeaturePermissions.requestChatMediaPermission(this),
                null
        );
    }

    private void openMediaPicker() {
        PickVisualMediaRequest request = new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageAndVideo.INSTANCE)
                .build();
        pickGalleryLauncher.launch(request);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == FeaturePermissions.REQ_CHAT_MEDIA) {
            if (FeaturePermissions.allGranted(grantResults)
                    || FeaturePermissions.hasChatMediaPermission(this)) {
                openMediaPicker();
            } else {
                Toast.makeText(this, R.string.perm_chat_media_denied, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void sendText() {
        String text = etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(text)) {
            return;
        }
        AdminChatHelper.sendText(userId, text, AdminChatHelper.SENDER_USER);
        etMessage.setText("");
    }

    private void onFilePicked(Uri uri) {
        if (uri == null || uploading) {
            return;
        }
        try {
            getContentResolver().takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        } catch (SecurityException ignored) {
            // PickVisualMedia URIs are single-use; persist may not apply.
        }

        String mime = getContentResolver().getType(uri);
        if (mime == null) {
            mime = "application/octet-stream";
        }
        String name = queryDisplayName(uri);
        String type = AdminChatHelper.TYPE_FILE;
        if (mime.startsWith("image/")) {
            type = AdminChatHelper.TYPE_IMAGE;
        } else if (mime.startsWith("video/")) {
            type = AdminChatHelper.TYPE_VIDEO;
        }
        String finalMime = mime;
        String finalType = type;
        uploading = true;
        Toast.makeText(this, R.string.chat_uploading, Toast.LENGTH_SHORT).show();
        AdminChatHelper.uploadAttachment(this, userId, uri, mime, name, new AdminChatHelper.UploadCallback() {
            @Override
            public void onSuccess(String downloadUrl) {
                uploading = false;
                AdminChatHelper.pushMessage(
                        userId,
                        AdminChatHelper.SENDER_USER,
                        finalType,
                        null,
                        downloadUrl,
                        name,
                        finalMime
                );
                Toast.makeText(ChatActivity.this, R.string.chat_upload_sent, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(String error) {
                uploading = false;
                Toast.makeText(ChatActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String queryDisplayName(Uri uri) {
        try (android.database.Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    return c.getString(idx);
                }
            }
        } catch (Exception ignored) {
        }
        return "attachment";
    }

    private void requestCall(boolean video) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestCameraMic.launch(Manifest.permission.CAMERA);
            requestCameraMic.launch(Manifest.permission.RECORD_AUDIO);
        }
        Intent call = new Intent(this, CallActivity.class);
        call.putExtra("username", userId);
        call.putExtra("userInitiatedAdminCall", true);
        call.putExtra("videoEnabled", video);
        startActivity(call);
        Toast.makeText(this, R.string.chat_call_requested, Toast.LENGTH_SHORT).show();
    }

    private void attachMessagesListener() {
        messagesListener = AdminChatHelper.listenMessages(userId, new AdminChatHelper.MessagesListener() {
            @Override
            public void onMessagesChanged(@NonNull DataSnapshot snapshot) {
                List<ChatMessage> list = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    ChatMessage m = child.getValue(ChatMessage.class);
                    if (m == null) {
                        m = new ChatMessage();
                        m.text = stringVal(child, "text");
                        m.sender = stringVal(child, "sender");
                        m.type = stringVal(child, "type");
                        m.fileUrl = stringVal(child, "fileUrl");
                        m.fileName = stringVal(child, "fileName");
                        m.mimeType = stringVal(child, "mimeType");
                        Long ts = child.child("timestamp").getValue(Long.class);
                        m.timestamp = ts != null ? ts : 0L;
                    }
                    m.id = child.getKey();
                    list.add(m);
                }
                list.sort((a, b) -> Long.compare(a.timestamp, b.timestamp));
                adapter.setMessages(list);
                tvEmptyChat.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                if (!list.isEmpty()) {
                    rvMessages.scrollToPosition(list.size() - 1);
                    AdminChatNotifier.markSeenFromSnapshot(ChatActivity.this, snapshot);
                }
                AdminChatHelper.clearUnreadForUser(userId);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(ChatActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static String stringVal(DataSnapshot snap, String key) {
        Object v = snap.child(key).getValue();
        return v != null ? String.valueOf(v) : null;
    }

    @Override
    protected void onDestroy() {
        if (adminNameListener != null) {
            AdminChatDisplayHelper.removeListener(adminNameListener);
        }
        if (userId != null && messagesListener != null) {
            AdminChatHelper.removeMessagesListener(userId, messagesListener);
        }
        super.onDestroy();
    }
}
