package com.busho.app.helpers;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class FirebaseUtils {
    private static final DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users");

    public static void updatePoints(String userId, int pointsToAdd) {
        userRef.child(userId).child("points").get().addOnSuccessListener(snapshot -> {
            int currentPoints = snapshot.exists() ? snapshot.getValue(Integer.class) : 0;
            userRef.child(userId).child("points").setValue(currentPoints + pointsToAdd);
        });
    }

    public static void getPoints(String userId, ValueEventListener listener) {
        userRef.child(userId).child("points").addListenerForSingleValueEvent(listener);
    }
}
