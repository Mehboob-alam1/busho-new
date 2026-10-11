package com.bushorat.chatapp.helpers;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

/**
 * Plays incoming-call ringtone until stopped.
 */
public final class CallRingtonePlayer {
    private Ringtone ringtone;
    private MediaPlayer mediaPlayer;

    public void start(Context context) {
        stop();
        Context app = context.getApplicationContext();
        Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        if (uri == null) {
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ringtone = RingtoneManager.getRingtone(app, uri);
                if (ringtone != null) {
                    AudioAttributes attrs = new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build();
                    ringtone.setAudioAttributes(attrs);
                    ringtone.setLooping(true);
                    ringtone.play();
                }
            } else {
                mediaPlayer = MediaPlayer.create(app, uri);
                if (mediaPlayer != null) {
                    mediaPlayer.setLooping(true);
                    mediaPlayer.start();
                }
            }
        } catch (Exception ignored) {
            stop();
        }
    }

    public void stop() {
        try {
            if (ringtone != null && ringtone.isPlaying()) {
                ringtone.stop();
            }
        } catch (Exception ignored) {
        }
        ringtone = null;
        try {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.release();
            }
        } catch (Exception ignored) {
        }
        mediaPlayer = null;
    }
}
