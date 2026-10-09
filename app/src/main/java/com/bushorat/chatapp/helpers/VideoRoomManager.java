package com.bushorat.chatapp.helpers;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class VideoRoomManager {
    private static final String TAG = "VideoRoomManager";
    private static final String ROOMS_NODE = "video_rooms";
    private static final String QUEUE_NODE = "user_call";
    
    private DatabaseReference roomsRef;
    private DatabaseReference queueRef;
    private FirebaseAuth auth;
    
    public VideoRoomManager() {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        roomsRef = database.getReference(ROOMS_NODE);
        queueRef = database.getReference(QUEUE_NODE);
        auth = FirebaseAuth.getInstance();
    }
    
    /**
     * Join the queue for random video matching
     * @param userId Current user ID
     * @param genderPreference Preferred gender for matching (null for any)
     * @param callback Callback when match is found or queue joined
     */
    public void joinQueue(String userId, String genderPreference, RoomMatchCallback callback) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            callback.onError("User not authenticated");
            return;
        }
        
        // Create queue entry
        Map<String, Object> queueEntry = new HashMap<>();
        queueEntry.put("userId", userId);
        queueEntry.put("genderPreference", genderPreference != null ? genderPreference : "any");
        queueEntry.put("timestamp", ServerValue.TIMESTAMP);
        queueEntry.put("status", "waiting");
        
        queueRef.child(userId).setValue(queueEntry).addOnSuccessListener(aVoid -> {
            Log.d(TAG, "User " + userId + " joined queue with preference: " + genderPreference);
            
            // Small delay to ensure data is written before searching
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                // Try to find a match immediately
                findMatch(userId, genderPreference, callback);
                
                // Also listen for incoming matches
                listenForMatch(userId, callback);
            }, 500);
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Failed to join queue", e);
            callback.onError("Failed to join queue: " + e.getMessage());
        });
    }
    
    /**
     * Find a match for the user in the queue - improved matching logic similar to RandomVideoCall repo
     */
    private void findMatch(String userId, String genderPreference, RoomMatchCallback callback) {
        // Try to find an existing waiting user first (faster matching)
        queueRef.orderByChild("status").equalTo("waiting")
                .limitToFirst(50)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if (!snapshot.exists()) {
                            Log.d(TAG, "No users in queue, waiting for match...");
                            // Keep listening for new users
                            return;
                        }
                        
                        java.util.List<DataSnapshot> candidates = new java.util.ArrayList<>();
                        
                        // Collect all potential matches
                        for (DataSnapshot queueSnap : snapshot.getChildren()) {
                            String queueUserId = queueSnap.getKey();
                            if (queueUserId == null || queueUserId.equals(userId)) {
                                continue; // Skip self
                            }
                            
                            String pref = queueSnap.child("genderPreference").getValue(String.class);
                            if (pref == null) pref = "any";
                            
                            // Check if preferences match (similar to RandomVideoCall logic)
                            boolean canMatch = genderPreference == null || 
                                            genderPreference.equals("any") || 
                                            pref.equals("any") ||
                                            genderPreference.equalsIgnoreCase(pref);
                            
                            if (canMatch) {
                                candidates.add(queueSnap);
                            }
                        }
                        
                        // Randomly select from candidates for true random matching (like RandomVideoCall)
                        if (!candidates.isEmpty()) {
                            // Random selection for true randomness
                            int randomIndex = new Random().nextInt(candidates.size());
                            DataSnapshot selectedMatch = candidates.get(randomIndex);
                            String matchUserId = selectedMatch.getKey();
                            
                            if (matchUserId != null && !matchUserId.equals(userId)) {
                                // Double check user is still waiting before matching
                                String matchStatus = selectedMatch.child("status").getValue(String.class);
                                if ("waiting".equals(matchStatus)) {
                                    Log.d(TAG, "Found match: " + matchUserId + " for user: " + userId);
                                    // Create a room for these two users
                                    createRoom(userId, matchUserId, callback);
                                    return;
                                }
                            }
                        }
                        
                        Log.d(TAG, "No suitable match found, staying in queue... Will retry when new users join.");
                        
                        // Set up listener for new users joining queue to retry matching
                        retryMatchingWhenNewUserJoins(userId, genderPreference, callback);
                    }
                    
                    @Override
                    public void onCancelled(DatabaseError error) {
                        Log.e(TAG, "Error finding match", error.toException());
                        callback.onError("Error finding match: " + error.getMessage());
                    }
                });
    }
    
    /**
     * Listen for when another user matches with this user
     */
    private void listenForMatch(String userId, RoomMatchCallback callback) {
        queueRef.child(userId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    return;
                }
                
                String status = snapshot.child("status").getValue(String.class);
                if ("matched".equals(status)) {
                    String roomId = snapshot.child("roomId").getValue(String.class);
                    if (roomId != null) {
                        // Get room details
                        roomsRef.child(roomId).addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot roomSnapshot) {
                                if (roomSnapshot.exists()) {
                                    String createdBy = roomSnapshot.child("createdBy").getValue(String.class);
                                    String participant2 = roomSnapshot.child("participant2").getValue(String.class);
                                    
                                    boolean isHost = userId.equals(createdBy);
                                    callback.onMatchFound(roomId, isHost, 
                                        isHost ? participant2 : createdBy);
                                }
                            }
                            
                            @Override
                            public void onCancelled(DatabaseError error) {
                                Log.e(TAG, "Error getting room details", error.toException());
                            }
                        });
                    }
                }
            }
            
            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "Error listening for match", error.toException());
            }
        });
    }
    
    /**
     * Create a video call room for two users - improved logic from RandomVideoCall repo
     */
    private void createRoom(String user1, String user2, RoomMatchCallback callback) {
        // Use user IDs as room identifier for simplicity (similar to RandomVideoCall)
        String roomId = generateRoomId();
        
        Map<String, Object> room = new HashMap<>();
        room.put("roomId", roomId);
        room.put("createdBy", user1);
        room.put("participant2", user2);
        room.put("participant1", user1); // Add explicit participant1
        room.put("createdAt", ServerValue.TIMESTAMP);
        room.put("status", "active");
        room.put("type", "random_video");
        room.put("incoming", user2); // For compatibility with CallActivity
        room.put("isAvailable", true);
        
        // Set both users as matched and remove from queue
        Map<String, Object> user1Update = new HashMap<>();
        user1Update.put("status", "matched");
        user1Update.put("roomId", roomId);
        user1Update.put("incoming", user2);
        user1Update.put("createdBy", user1);
        
        Map<String, Object> user2Update = new HashMap<>();
        user2Update.put("status", "matched");
        user2Update.put("roomId", roomId);
        user2Update.put("incoming", user1);
        user2Update.put("createdBy", user1);
        
        // Atomic update: create room and update both users simultaneously
        Map<String, Object> updates = new HashMap<>();
        updates.put(ROOMS_NODE + "/" + roomId, room);
        updates.put(QUEUE_NODE + "/" + user1, user1Update);
        updates.put(QUEUE_NODE + "/" + user2, user2Update);
        
        FirebaseDatabase.getInstance().getReference().updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Room created: " + roomId + " for users: " + user1 + ", " + user2);
                    // Notify both users
                    callback.onMatchFound(roomId, true, user2);
                    
                    // Trigger match for user2 as well
                    notifyMatch(user2, roomId, user1, false);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to create room", e);
                    callback.onError("Failed to create room: " + e.getMessage());
                });
    }
    
    /**
     * Retry matching when a new user joins the queue
     */
    private void retryMatchingWhenNewUserJoins(String userId, String genderPreference, RoomMatchCallback callback) {
        // Listen for new users joining queue with status "waiting"
        queueRef.orderByChild("status").equalTo("waiting")
                .limitToLast(1) // Only listen to the last one (newest)
                .addChildEventListener(new ChildEventListener() {
                    @Override
                    public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                        String newUserId = snapshot.getKey();
                        if (newUserId != null && !newUserId.equals(userId)) {
                            // A new user joined, try to match again
                            findMatch(userId, genderPreference, callback);
                        }
                    }
                    
                    @Override
                    public void onChildChanged(DataSnapshot snapshot, String previousChildName) {}
                    
                    @Override
                    public void onChildRemoved(DataSnapshot snapshot) {}
                    
                    @Override
                    public void onChildMoved(DataSnapshot snapshot, String previousChildName) {}
                    
                    @Override
                    public void onCancelled(DatabaseError error) {}
                });
    }
    
    /**
     * Notify the second user of the match
     */
    private void notifyMatch(String userId, String roomId, String otherUserId, boolean isHost) {
        // The listener in joinQueue will pick this up automatically
        // But we ensure the queue entry is updated
        queueRef.child(userId).child("status").setValue("matched");
        queueRef.child(userId).child("roomId").setValue(roomId);
        queueRef.child(userId).child("incoming").setValue(otherUserId);
        queueRef.child(userId).child("createdBy").setValue(userId.equals(otherUserId) ? userId : otherUserId);
    }
    
    /**
     * Leave the queue
     */
    public void leaveQueue(String userId) {
        queueRef.child(userId).removeValue().addOnSuccessListener(aVoid -> {
            Log.d(TAG, "User " + userId + " left queue");
        });
    }
    
    /**
     * End a room/call
     */
    public void endRoom(String roomId, String userId) {
        roomsRef.child(roomId).child("status").setValue("ended")
                .addOnSuccessListener(aVoid -> {
                    // Clean up after a delay
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        roomsRef.child(roomId).removeValue();
                    }, 5000);
                });
        
        // Remove from queue if still there
        leaveQueue(userId);
    }
    
    /**
     * Get room information
     */
    public void getRoomInfo(String roomId, RoomInfoCallback callback) {
        roomsRef.child(roomId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String createdBy = snapshot.child("createdBy").getValue(String.class);
                    String participant2 = snapshot.child("participant2").getValue(String.class);
                    String status = snapshot.child("status").getValue(String.class);
                    
                    callback.onRoomInfo(createdBy, participant2, status);
                } else {
                    callback.onError("Room not found");
                }
            }
            
            @Override
            public void onCancelled(DatabaseError error) {
                callback.onError("Error getting room info: " + error.getMessage());
            }
        });
    }
    
    private String generateRoomId() {
        return "room_" + System.currentTimeMillis() + "_" + new Random().nextInt(1000);
    }
    
    /**
     * Get active rooms count
     */
    public void getActiveRoomsCount(RoomCountCallback callback) {
        roomsRef.orderByChild("status").equalTo("active")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        long count = snapshot.getChildrenCount();
                        callback.onCount((int) count);
                    }
                    
                    @Override
                    public void onCancelled(DatabaseError error) {
                        callback.onError("Error getting room count");
                    }
                });
    }
    
    /**
     * Get queue count
     */
    public void getQueueCount(QueueCountCallback callback) {
        queueRef.orderByChild("status").equalTo("waiting")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        long count = snapshot.getChildrenCount();
                        callback.onCount((int) count);
                    }
                    
                    @Override
                    public void onCancelled(DatabaseError error) {
                        callback.onError("Error getting queue count");
                    }
                });
    }
    
    public interface RoomMatchCallback {
        void onMatchFound(String roomId, boolean isHost, String otherUserId);
        void onError(String error);
    }
    
    public interface RoomInfoCallback {
        void onRoomInfo(String createdBy, String participant2, String status);
        void onError(String error);
    }
    
    public interface RoomCountCallback {
        void onCount(int count);
        void onError(String error);
    }
    
    public interface QueueCountCallback {
        void onCount(int count);
        void onError(String error);
    }
}

