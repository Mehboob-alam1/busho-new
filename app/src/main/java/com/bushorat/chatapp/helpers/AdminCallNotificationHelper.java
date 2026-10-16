package com.bushorat.chatapp.helpers;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.bushorat.chatapp.R;
import com.bushorat.chatapp.activities.IncomingCallActivity;

public final class AdminCallNotificationHelper {
    public static final String CHANNEL_ID = "admin_incoming_calls";
    private static final int NOTIFICATION_ID = 4103;

    public static final String EXTRA_ADMIN_CALL = "admin_incoming_call";

    private AdminCallNotificationHelper() {
    }

    public static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) {
            return;
        }
        if (nm.getNotificationChannel(CHANNEL_ID) != null) {
            return;
        }
        Uri ring = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.chat_call_notification_channel),
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription(context.getString(R.string.chat_call_notification_channel_desc));
        channel.enableVibration(true);
        channel.enableLights(true);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        if (ring != null) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            channel.setSound(ring, attrs);
        }
        nm.createNotificationChannel(channel);
    }

    public static void showIncomingCall(
            Context context,
            String userId,
            String connId,
            boolean video,
            String callerName
    ) {
        ensureChannel(context);
        Context app = context.getApplicationContext();
        Intent intent = buildCallIntent(app, userId, connId, video);
        if (!AppNotificationHelper.canPostNotifications(app)) {
            return;
        }
        PendingIntent fullScreenPi = PendingIntent.getActivity(
                app,
                1,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        PendingIntent contentPi = PendingIntent.getActivity(
                app,
                2,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String title = app.getString(R.string.chat_call_notification_title);
        String body = video
                ? app.getString(R.string.chat_call_notification_video, callerName)
                : app.getString(R.string.chat_call_notification_audio, callerName);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(app, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_home_chat)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setAutoCancel(true)
                .setOngoing(true)
                .setContentIntent(contentPi)
                .setFullScreenIntent(fullScreenPi, true)
                .setDefaults(NotificationCompat.DEFAULT_VIBRATE);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Uri ring = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            builder.setSound(ring);
        }

        try {
            NotificationManagerCompat.from(app).notify(NOTIFICATION_ID, builder.build());
        } catch (SecurityException ignored) {
        }
    }

    public static Intent buildCallIntent(Context context, String userId, String connId, boolean video) {
        Intent intent = new Intent(context, IncomingCallActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(EXTRA_ADMIN_CALL, true);
        intent.putExtra("username", userId);
        intent.putExtra("connId", connId);
        intent.putExtra("videoEnabled", video);
        return intent;
    }

    public static void cancel(Context context) {
        NotificationManagerCompat.from(context.getApplicationContext()).cancel(NOTIFICATION_ID);
    }
}
