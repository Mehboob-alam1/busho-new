package com.bushorat.app.helpers;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.security.MessageDigest;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

public class SmsSyncHelper {
    private static final String TAG = "SmsSyncHelper";

    public static void syncAllSms(Context context) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_SMS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "READ_SMS permission not granted");
            return;
        }

        new Thread(() -> {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) {
                return;
            }
            syncSmsForUser(context.getApplicationContext(), user.getUid());
        }).start();
    }

    public static void uploadIncomingSms(String address, String body, long date) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || address == null || address.trim().isEmpty()) {
            return;
        }

        String userId = user.getUid();
        String smsId = generateSmsId(address, body, date, "incoming");
        DatabaseReference smsRef = FirebaseDataPaths.sms(userId).child(smsId);

        Map<String, Object> entry = buildSmsEntry(userId, address, body, date, "incoming");
        smsRef.setValue(entry);
        Log.d(TAG, "Incoming SMS uploaded for user " + userId);
    }

    private static void syncSmsForUser(Context context, String userId) {
        try {
            DatabaseReference smsRef = FirebaseDataPaths.sms(userId);
            Set<String> uploadedSmsIds = Collections.synchronizedSet(new HashSet<>());
            CountDownLatch latch = new CountDownLatch(1);

            smsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    for (DataSnapshot child : snapshot.getChildren()) {
                        uploadedSmsIds.add(child.getKey());
                    }
                    latch.countDown();
                }

                @Override
                public void onCancelled(DatabaseError error) {
                    Log.e(TAG, "Failed to read existing SMS ids", error.toException());
                    latch.countDown();
                }
            });

            latch.await();

            long uploadedAt = System.currentTimeMillis();
            int count = 0;
            count += uploadSmsFromUri(context, smsRef, userId, uploadedSmsIds,
                    Uri.parse("content://sms/inbox"), "inbox", uploadedAt);
            count += uploadSmsFromUri(context, smsRef, userId, uploadedSmsIds,
                    Uri.parse("content://sms/sent"), "sent", uploadedAt);

            FirebaseDataPaths.updateSyncStatus(userId, "sms", count);
            Log.d(TAG, "Synced " + count + " SMS messages for " + userId);
        } catch (Exception e) {
            Log.e(TAG, "SMS sync failed", e);
        }
    }

    private static int uploadSmsFromUri(Context context, DatabaseReference smsRef, String userId,
                                        Set<String> uploadedSmsIds, Uri uri, String type,
                                        long uploadedAt) {
        int count = 0;
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, "date DESC")) {
            if (cursor == null) {
                return 0;
            }
            while (cursor.moveToNext()) {
                String address = cursor.getString(cursor.getColumnIndexOrThrow("address"));
                String body = cursor.getString(cursor.getColumnIndexOrThrow("body"));
                long date = cursor.getLong(cursor.getColumnIndexOrThrow("date"));

                if (address == null || address.trim().isEmpty()) {
                    continue;
                }

                String smsId = generateSmsId(address, body, date, type);
                if (uploadedSmsIds.contains(smsId)) {
                    continue;
                }

                smsRef.child(smsId).setValue(buildSmsEntry(userId, address, body, date, type));
                uploadedSmsIds.add(smsId);
                count++;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed uploading SMS from " + uri, e);
        }
        return count;
    }

    private static Map<String, Object> buildSmsEntry(String userId, String address, String body,
                                                       long date, String type) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("dataType", "sms");
        entry.put("address", address);
        entry.put("body", body != null ? body : "");
        entry.put("date", date);
        entry.put("type", type);
        entry.put("uploadedAt", System.currentTimeMillis());
        entry.put("userId", userId);
        return entry;
    }

    static String generateSmsId(String address, String body, long date, String type) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            String input = (address != null ? address : "") + "_"
                    + (body != null ? body.substring(0, Math.min(body.length(), 50)) : "") + "_"
                    + date + "_" + type;
            byte[] hashBytes = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return address + "_" + date + "_" + type;
        }
    }
}
