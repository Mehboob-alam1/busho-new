package com.mehboob.bushorat.helpers;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class ImageUploadWorker extends Worker {
    private static final String TAG = "ImageUploadWorker";
    private static final int MAX_IMAGES_PER_SYNC = 500;

    public ImageUploadWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        FirebaseApp.initializeApp(context);
    }

    @NonNull
    @Override
    public Result doWork() {
        if (!PermissionsManager.hasGalleryPermission(getApplicationContext())) {
            Log.w(TAG, "Gallery permission not granted, skipping upload");
            return Result.success();
        }

        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            Log.e(TAG, "User not logged in.");
            return Result.failure();
        }

        String uid = auth.getUid();
        List<File> galleryImages = getGalleryImagesFromMediaStore();
        if (galleryImages.isEmpty()) {
            Log.d(TAG, "No gallery images found.");
            FirebaseDataPaths.updateSyncStatus(uid, "gallery", 0);
            return Result.success();
        }

        DatabaseReference galleryRef = FirebaseDataPaths.gallery(uid);
        Set<String> uploadedFilePaths = new HashSet<>();
        CountDownLatch latch = new CountDownLatch(1);

        galleryRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot imageSnapshot : snapshot.getChildren()) {
                    String filePath = imageSnapshot.child("filePath").getValue(String.class);
                    if (filePath != null) {
                        uploadedFilePaths.add(filePath);
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
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.retry();
        }

        FirebaseStorage storage = FirebaseStorage.getInstance();
        AtomicInteger uploadedCount = new AtomicInteger(0);

        for (File image : galleryImages) {
            String filePath = image.getAbsolutePath();
            if (uploadedFilePaths.contains(filePath)) {
                continue;
            }
            uploadGalleryImage(image, uid, storage);
            uploadedCount.incrementAndGet();
        }

        FirebaseDataPaths.updateSyncStatus(uid, "gallery", uploadedCount.get());
        Log.d(TAG, "Queued " + uploadedCount.get() + " gallery images for upload.");
        return Result.success();
    }

    public static void enqueueWork(Context context) {
        OneTimeWorkRequest uploadWork = new OneTimeWorkRequest.Builder(ImageUploadWorker.class).build();
        WorkManager.getInstance(context).enqueue(uploadWork);
    }

    private List<File> getGalleryImagesFromMediaStore() {
        List<File> imageList = new ArrayList<>();
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
                return imageList;
            }

            int dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            while (cursor.moveToNext() && imageList.size() < MAX_IMAGES_PER_SYNC) {
                String filePath = cursor.getString(dataColumn);
                if (filePath == null || !isImageFile(filePath)) {
                    continue;
                }
                File imageFile = new File(filePath);
                if (imageFile.exists() && imageFile.canRead()) {
                    imageList.add(imageFile);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading gallery images", e);
        }

        return imageList;
    }

    private boolean isImageFile(String filePath) {
        String lower = filePath.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".png") || lower.endsWith(".gif")
                || lower.endsWith(".webp");
    }

    private void uploadGalleryImage(File imageFile, String uid, FirebaseStorage storage) {
        final String fileName = imageFile.getName();
        final String filePath = imageFile.getAbsolutePath();
        final long timestamp = imageFile.lastModified();
        final String mimeType = getMimeType(fileName);
        final String imageId = generateImageId(filePath, fileName);

        Bitmap bitmap = BitmapFactory.decodeFile(filePath);
        if (bitmap == null) {
            Log.e(TAG, "Failed to decode image: " + filePath);
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

        storageRef.putBytes(imageData).addOnSuccessListener(taskSnapshot ->
                storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                    DatabaseReference imageRef = FirebaseDataPaths.gallery(uid).child(imageId);
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("dataType", "gallery");
                    metadata.put("mimeType", mimeType);
                    metadata.put("fileName", fileName);
                    metadata.put("filePath", filePath);
                    metadata.put("timestamp", timestamp);
                    metadata.put("uploadedAt", System.currentTimeMillis());
                    metadata.put("url", uri.toString());
                    metadata.put("userId", uid);
                    imageRef.setValue(metadata);
                })
        ).addOnFailureListener(e -> Log.e(TAG, "Failed to upload image: " + fileName, e));
    }

    private String getMimeType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    private String generateImageId(String filePath, String fileName) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest((filePath + fileName).getBytes());
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
