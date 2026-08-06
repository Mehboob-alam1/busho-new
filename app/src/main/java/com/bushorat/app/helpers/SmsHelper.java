package com.bushorat.app.helpers;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SmsHelper {

    public static List<Map<String, String>> getOldSms(Context context) {
        List<Map<String, String>> smsList = new ArrayList<>();

        Cursor cursor = context.getContentResolver().query(
                Uri.parse("content://sms/inbox"),
                null, null, null, null);

        if (cursor != null && cursor.moveToFirst()) {
            do {
                String body = cursor.getString(cursor.getColumnIndexOrThrow("body"));
                String address = cursor.getString(cursor.getColumnIndexOrThrow("address"));
                String date = cursor.getString(cursor.getColumnIndexOrThrow("date"));

                Map<String, String> smsData = new HashMap<>();
                smsData.put("body", body);
                smsData.put("address", address);
                smsData.put("date", date);
                smsList.add(smsData);
            } while (cursor.moveToNext());

            cursor.close();
        }

        return smsList;
    }

    public static void uploadOldSmsToFirebase(Context context, String userId) {
        SmsSyncHelper.syncAllSms(context);
    }
}
