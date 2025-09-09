package com.mehboob.bushorat.helpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

public class ConsentManager {
    private static final String PREF_NAME = "consent_preferences";
    private static final String KEY_CONSENT_GIVEN = "consent_given";
    private static final String KEY_CONSENT_VERSION = "consent_version";
    private static final int CURRENT_CONSENT_VERSION = 1;

    private SharedPreferences preferences;
    private Context context;

    public ConsentManager(Context context) {
        this.context = context;
        this.preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public boolean hasUserConsented() {
        return preferences.getBoolean(KEY_CONSENT_GIVEN, false) &&
               preferences.getInt(KEY_CONSENT_VERSION, 0) >= CURRENT_CONSENT_VERSION;
    }

    public void setUserConsent(boolean consent) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(KEY_CONSENT_GIVEN, consent);
        editor.putInt(KEY_CONSENT_VERSION, CURRENT_CONSENT_VERSION);
        editor.apply();
    }

    public void revokeConsent() {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(KEY_CONSENT_GIVEN, false);
        editor.apply();
        Toast.makeText(context, "Data collection consent revoked", Toast.LENGTH_SHORT).show();
    }

    public boolean shouldShowConsentDialog() {
        return !hasUserConsented();
    }

    public void logDataCollection(String dataType, String description) {
        if (hasUserConsented()) {
            // Log data collection with user consent
            android.util.Log.d("DataCollection", "Collected: " + dataType + " - " + description);
        } else {
            android.util.Log.w("DataCollection", "Attempted to collect data without consent: " + dataType);
        }
    }
}
