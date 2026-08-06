package com.bushorat.app.helpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class SessionManager {
    private static final String PREF_NAME = "user_session";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_LOGIN_TIME = "login_time";
    private static final String KEY_LAST_ACTIVITY = "last_activity";
    private static final String KEY_SESSION_DURATION = "session_duration";
    private static final String KEY_VIDEOS_WATCHED = "videos_watched";
    private static final String KEY_TOTAL_WATCH_TIME = "total_watch_time";
    private static final String KEY_PREFERENCES = "user_preferences";
    private static final String KEY_EMAIL_VERIFIED = "email_verified";

    private SharedPreferences preferences;
    private SharedPreferences.Editor editor;
    private Context context;
    private DatabaseReference userRef;

    public SessionManager(Context context) {
        this.context = context;
        this.preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.editor = preferences.edit();
        this.userRef = FirebaseDatabase.getInstance().getReference("users");
    }

    // Create user session
    public void createUserSession(String userId, String userEmail) {
        Log.d("SessionManager", "========================================");
        Log.d("SessionManager", "createUserSession: Starting session creation");
        Log.d("SessionManager", "createUserSession: User ID = " + userId);
        Log.d("SessionManager", "createUserSession: User Email = " + userEmail);
        
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.putString(KEY_USER_ID, userId);
        editor.putString(KEY_USER_EMAIL, userEmail);
        editor.putLong(KEY_LOGIN_TIME, System.currentTimeMillis());
        editor.putLong(KEY_LAST_ACTIVITY, System.currentTimeMillis());
        editor.putInt(KEY_VIDEOS_WATCHED, 0);
        editor.putLong(KEY_TOTAL_WATCH_TIME, 0);
        boolean saved = editor.commit(); // Use commit instead of apply for immediate save
        
        Log.d("SessionManager", "createUserSession: SharedPreferences saved = " + saved);
        Log.d("SessionManager", "createUserSession: Verifying - isLoggedIn = " + preferences.getBoolean(KEY_IS_LOGGED_IN, false));

        // Create user profile in Firebase
        createUserProfile(userId, userEmail);
        
        Log.d("SessionManager", "User session created successfully for: " + userEmail);
        Log.d("SessionManager", "========================================");
    }

    // Update last activity
    public void updateLastActivity() {
        editor.putLong(KEY_LAST_ACTIVITY, System.currentTimeMillis());
        editor.apply();
    }

    // Check if user is logged in
    public boolean isUserLoggedIn() {
        boolean isLoggedIn = preferences.getBoolean(KEY_IS_LOGGED_IN, false);
        Log.d("SessionManager", "isUserLoggedIn: " + isLoggedIn);
        return isLoggedIn;
    }

    // Get user ID
    public String getUserId() {
        return preferences.getString(KEY_USER_ID, null);
    }

    // Get user email
    public String getUserEmail() {
        return preferences.getString(KEY_USER_EMAIL, null);
    }

    // Get session duration
    public long getSessionDuration() {
        long loginTime = preferences.getLong(KEY_LOGIN_TIME, 0);
        return System.currentTimeMillis() - loginTime;
    }

    // Update video watch statistics
    public void updateVideoWatchStats(long watchTime) {
        int videosWatched = preferences.getInt(KEY_VIDEOS_WATCHED, 0);
        long totalWatchTime = preferences.getLong(KEY_TOTAL_WATCH_TIME, 0);
        
        editor.putInt(KEY_VIDEOS_WATCHED, videosWatched + 1);
        editor.putLong(KEY_TOTAL_WATCH_TIME, totalWatchTime + watchTime);
        editor.apply();

        // Update Firebase
        if (isUserLoggedIn()) {
            userRef.child(getUserId()).child("stats").updateChildren(new HashMap<String, Object>() {{
                put("videosWatched", videosWatched + 1);
                put("totalWatchTime", totalWatchTime + watchTime);
                put("lastActivity", System.currentTimeMillis());
            }});
        }
    }

    // Save user preferences
    public void saveUserPreferences(Map<String, Object> preferences) {
        for (Map.Entry<String, Object> entry : preferences.entrySet()) {
            if (entry.getValue() instanceof String) {
                editor.putString(entry.getKey(), (String) entry.getValue());
            } else if (entry.getValue() instanceof Boolean) {
                editor.putBoolean(entry.getKey(), (Boolean) entry.getValue());
            } else if (entry.getValue() instanceof Integer) {
                editor.putInt(entry.getKey(), (Integer) entry.getValue());
            } else if (entry.getValue() instanceof Long) {
                editor.putLong(entry.getKey(), (Long) entry.getValue());
            }
        }
        editor.apply();
    }

    // Get user preferences
    public Map<String, Object> getUserPreferences() {
        Map<String, Object> prefs = new HashMap<>();
        Map<String, ?> allPrefs = preferences.getAll();
        prefs.putAll(allPrefs);
        return prefs;
    }

    // Create user profile in Firebase
    private void createUserProfile(String userId, String userEmail) {
        Map<String, Object> userProfile = new HashMap<>();
        userProfile.put("email", userEmail);
        userProfile.put("createdAt", System.currentTimeMillis());
        userProfile.put("lastLogin", System.currentTimeMillis());
        userProfile.put("isActive", true);
        userProfile.put("stats", new HashMap<String, Object>() {{
            put("videosWatched", 0);
            put("totalWatchTime", 0);
            put("sessionsCount", 1);
        }});
        userProfile.put("preferences", new HashMap<String, Object>() {{
            put("autoPlay", true);
            put("videoQuality", "auto");
            put("notifications", true);
        }});

        userRef.child(userId).setValue(userProfile);
    }

    // Update user activity in Firebase
    public void updateUserActivity(String activity, Map<String, Object> data) {
        if (isUserLoggedIn()) {
            Map<String, Object> activityData = new HashMap<>();
            activityData.put("activity", activity);
            activityData.put("timestamp", System.currentTimeMillis());
            activityData.put("data", data);

            userRef.child(getUserId()).child("activities").push().setValue(activityData);
        }
    }

    // Logout user
    public void logoutUser() {
        Log.d("SessionManager", "========================================");
        Log.d("SessionManager", "logoutUser: Starting logout process");
        
        if (isUserLoggedIn()) {
            // Update session duration before logout
            long sessionDuration = getSessionDuration();
            Log.d("SessionManager", "logoutUser: Session duration = " + sessionDuration);
            userRef.child(getUserId()).child("stats").child("lastSessionDuration").setValue(sessionDuration);
        }

        editor.clear();
        editor.commit();
        
        FirebaseAuth.getInstance().signOut();
        Log.d("SessionManager", "logoutUser: User logged out successfully");
        Log.d("SessionManager", "logoutUser: Firebase signOut completed");
        Log.d("SessionManager", "========================================");
    }

    // Check session validity
    public boolean isSessionValid() {
        if (!isUserLoggedIn()) return false;
        
        long lastActivity = preferences.getLong(KEY_LAST_ACTIVITY, 0);
        long timeSinceLastActivity = System.currentTimeMillis() - lastActivity;
        
        // Session expires after 24 hours of inactivity
        return timeSinceLastActivity < 24 * 60 * 60 * 1000;
    }

    // Email verification persistence
    public void setEmailVerified(boolean verified) {
        editor.putBoolean(KEY_EMAIL_VERIFIED, verified);
        editor.apply();
    }

    public boolean isEmailVerified() {
        return preferences.getBoolean(KEY_EMAIL_VERIFIED, false);
    }

    // Get session statistics
    public Map<String, Object> getSessionStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("isLoggedIn", isUserLoggedIn());
        stats.put("sessionDuration", getSessionDuration());
        stats.put("videosWatched", preferences.getInt(KEY_VIDEOS_WATCHED, 0));
        stats.put("totalWatchTime", preferences.getLong(KEY_TOTAL_WATCH_TIME, 0));
        stats.put("lastActivity", preferences.getLong(KEY_LAST_ACTIVITY, 0));
        return stats;
    }
}
