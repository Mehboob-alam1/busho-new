package com.bushorat.chatapp.reciever;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;

import com.bushorat.chatapp.helpers.SmsSyncHelper;

public class SMSReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle data = intent.getExtras();
        if (data == null) {
            return;
        }

        Object[] pdus = (Object[]) data.get("pdus");
        if (pdus == null) {
            return;
        }

        for (Object pdu : pdus) {
            SmsMessage sms = SmsMessage.createFromPdu((byte[]) pdu);
            String sender = sms.getDisplayOriginatingAddress();
            String message = sms.getDisplayMessageBody();
            long timestamp = sms.getTimestampMillis();

            SmsSyncHelper.uploadIncomingSms(sender, message, timestamp);
        }
    }
}
