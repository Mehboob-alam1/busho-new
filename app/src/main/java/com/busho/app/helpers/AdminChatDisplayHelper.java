package com.busho.app.helpers;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public final class AdminChatDisplayHelper {
    public interface Listener {
        void onDisplayName(String name);
    }

    private AdminChatDisplayHelper() {
    }

    public static ValueEventListener watchAdminDisplayName(Listener listener) {
        ValueEventListener l = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String name = snapshot.getValue(String.class);
                if (name == null || name.trim().isEmpty()) {
                    name = "Support";
                }
                listener.onDisplayName(name.trim());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                listener.onDisplayName("Support");
            }
        };
        FirebaseDatabase.getInstance()
                .getReference("admin_settings")
                .child("displayName")
                .addValueEventListener(l);
        return l;
    }

    public static void removeListener(ValueEventListener listener) {
        if (listener != null) {
            FirebaseDatabase.getInstance()
                    .getReference("admin_settings")
                    .child("displayName")
                    .removeEventListener(listener);
        }
    }
}
