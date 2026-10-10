package com.bushorat.chatapp.helpers;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class AdminChatHelper {
    private static final String TAG = "AdminChatHelper";
    public static final String SENDER_USER = "user";
    public static final String SENDER_ADMIN = "admin";
    public static final String TYPE_TEXT = "text";
    public static final String TYPE_IMAGE = "image";
    public static final String TYPE_VIDEO = "video";
    public static final String TYPE_FILE = "file";

    public interface MessagesListener {
        void onMessagesChanged(DataSnapshot snapshot);

        void onError(String message);
    }

    public interface UploadCallback {
        void onSuccess(String downloadUrl);

        void onFailure(String error);
    }

    private AdminChatHelper() {
    }

    public static DatabaseReference messagesRef(String userId) {
        return FirebaseDatabase.getInstance()
                .getReference("admin_chat")
                .child(userId)
                .child("messages");
    }

    public static DatabaseReference metaRef(String userId) {
        return FirebaseDatabase.getInstance()
                .getReference("admin_chat")
                .child(userId)
                .child("meta");
    }

    public static DatabaseReference callsRef(String userId) {
        return FirebaseDatabase.getInstance()
                .getReference("admin_calls")
                .child(userId);
    }

    @Nullable
    public static String currentUserId() {
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            return FirebaseAuth.getInstance().getCurrentUser().getUid();
        }
        return new SessionManager(
                FirebaseDatabase.getInstance().getApp().getApplicationContext()
        ).getUserId();
    }

    public static ValueEventListener listenMessages(String userId, MessagesListener listener) {
        ValueEventListener l = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                listener.onMessagesChanged(snapshot);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                listener.onError(error.getMessage());
            }
        };
        messagesRef(userId).orderByChild("timestamp").addValueEventListener(l);
        return l;
    }

    public static void removeMessagesListener(String userId, ValueEventListener listener) {
        if (listener != null) {
            messagesRef(userId).orderByChild("timestamp").removeEventListener(listener);
        }
    }

    public static void sendText(String userId, String text, String sender) {
        pushMessage(userId, sender, TYPE_TEXT, text, null, null, null);
    }

    public static void pushMessage(
            String userId,
            String sender,
            String type,
            @Nullable String text,
            @Nullable String fileUrl,
            @Nullable String fileName,
            @Nullable String mimeType
    ) {
        String id = messagesRef(userId).push().getKey();
        if (id == null) {
            return;
        }
        long ts = System.currentTimeMillis();
        Map<String, Object> msg = new HashMap<>();
        msg.put("sender", sender);
        msg.put("type", type);
        msg.put("timestamp", ts);
        if (text != null) {
            msg.put("text", text);
        }
        if (fileUrl != null) {
            msg.put("fileUrl", fileUrl);
        }
        if (fileName != null) {
            msg.put("fileName", fileName);
        }
        if (mimeType != null) {
            msg.put("mimeType", mimeType);
        }

        messagesRef(userId).child(id).setValue(msg);
        Map<String, Object> meta = new HashMap<>();
        meta.put("lastMessageAt", ts);
        meta.put("lastPreview", text != null ? text : (fileName != null ? fileName : type));
        if (SENDER_ADMIN.equals(sender)) {
            meta.put("unreadForUser", true);
        } else {
            meta.put("unreadForAdmin", true);
        }
        metaRef(userId).updateChildren(meta);
    }

    public static void uploadAttachment(
            Context context,
            String userId,
            Uri uri,
            String mimeType,
            String fileName,
            UploadCallback callback
    ) {
        String extId = UUID.randomUUID().toString();
        String safeName = fileName != null ? fileName.replaceAll("[^a-zA-Z0-9._-]", "_") : "file";
        String storagePath = "admin_chat_files/" + userId + "/" + extId + "_" + safeName;
        StorageReference ref = FirebaseStorage.getInstance().getReference(storagePath);

        ref.putFile(uri)
                .continueWithTask(task -> {
                    if (!task.isSuccessful() || task.getException() != null) {
                        throw task.getException() != null
                                ? task.getException()
                                : new Exception("Upload failed");
                    }
                    return ref.getDownloadUrl();
                })
                .addOnSuccessListener(uri1 -> {
                    DocumentRegistryHelper.registerChatAttachment(
                            userId,
                            fileName != null ? fileName : safeName,
                            uri1.toString(),
                            mimeType,
                            storagePath
                    );
                    callback.onSuccess(uri1.toString());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Upload failed", e);
                    callback.onFailure(e.getMessage() != null ? e.getMessage() : "Upload failed");
                });
    }

    public static void requestCallFromUser(String userId, boolean video) {
        requestCallFromUser(userId, video, null);
    }

    public static void requestCallFromUser(String userId, boolean video, @Nullable String userPeerId) {
        Map<String, Object> call = new HashMap<>();
        call.put("status", "requested");
        call.put("from", SENDER_USER);
        call.put("callType", video ? "video" : "audio");
        call.put("timestamp", System.currentTimeMillis());
        if (userPeerId != null && !userPeerId.isEmpty()) {
            call.put("userPeerId", userPeerId);
        }
        callsRef(userId).setValue(call);
    }

    public static void clearUnreadForUser(String userId) {
        metaRef(userId).child("unreadForUser").setValue(false);
    }
}
