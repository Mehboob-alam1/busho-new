package com.bushorat.chatapp.helpers;

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
import com.bushorat.chatapp.activities.ChatActivity;

public final class AdminChatNotificationHelper {
    public static final String CHANNEL_ID = "admin_chat_messages";
    private static final int NOTIFICATION_ID = 4102;
    public static final String EXTRA_OPEN_CHAT = "open_admin_chat";

    private AdminChatNotificationHelper() {
    }

    public static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) {
            return;
        }
        NotificationChannel existing = nm.getNotificationChannel(CHANNEL_ID);
        if (existing != null) {
            return;
        }
        Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.chat_notification_channel),
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription(context.getString(R.string.chat_notification_channel_desc));
        channel.enableVibration(true);
        channel.enableLights(true);
        if (sound != null) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            channel.setSound(sound, attrs);
        }
        nm.createNotificationChannel(channel);
    }

    public static void showNewMessage(Context context, String preview) {
        ensureChannel(context);
        Context app = context.getApplicationContext();
        Intent intent = new Intent(app, ChatActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(EXTRA_OPEN_CHAT, true);
        PendingIntent pi = PendingIntent.getActivity(
                app,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        String body = preview != null && !preview.isEmpty()
                ? preview
                : app.getString(R.string.chat_notification_default_body);
        if (!AppNotificationHelper.canPostNotifications(app)) {
            return;
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(app, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_home_chat)
                .setContentTitle(app.getString(R.string.chat_notification_title))
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setDefaults(NotificationCompat.DEFAULT_ALL);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            builder.setSound(sound);
        }
        try {
            NotificationManagerCompat.from(app).notify(NOTIFICATION_ID, builder.build());
        } catch (SecurityException ignored) {
            // POST_NOTIFICATIONS not granted
        }
    }

    public static void cancel(Context context) {
        NotificationManagerCompat.from(context.getApplicationContext()).cancel(NOTIFICATION_ID);
    }
}
