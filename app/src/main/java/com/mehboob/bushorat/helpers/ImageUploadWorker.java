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

public class ImageUploadWorker extends Worker {

    public ImageUploadWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        FirebaseApp.initializeApp(context); // Make sure Firebase is initialized
    }

    @NonNull
    @Override
    public Result doWork() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            Log.e("ImageUploadWorker", "User not logged in.");
            return Result.failure();
        }

        String uid = auth.getUid();
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        FirebaseStorage storage = FirebaseStorage.getInstance();

        // Get only WhatsApp images
        List<File> whatsappImages = getWhatsAppImagesFromMediaStore();

        if (whatsappImages.isEmpty()) {
            Log.d("ImageUploadWorker", "No WhatsApp images found to upload.");
            return Result.success();
        }

        Log.d("ImageUploadWorker", "Found " + whatsappImages.size() + " WhatsApp images to process.");

        // Get already uploaded images to avoid duplicates
        DatabaseReference userDataRef = database.getReference("user_data").child(uid).child("whatsapp_images");
        
        CountDownLatch latch = new CountDownLatch(1);
        final Set<String> uploadedFilePaths = new HashSet<>();
        
        userDataRef.addListenerForSingleValueEvent(new ValueEventListener() {
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
                Log.e("ImageUploadWorker", "Error fetching uploaded images: " + error.getMessage());
                latch.countDown();
            }
        });

        try {
            latch.await(); // Wait for the database query to complete
        } catch (InterruptedException e) {
            Log.e("ImageUploadWorker", "Interrupted while waiting for database query", e);
            return Result.retry();
        }

        Log.d("ImageUploadWorker", "Found " + uploadedFilePaths.size() + " already uploaded images.");

        // Upload new images
        int uploadedCount = 0;
        for (File image : whatsappImages) {
            String filePath = image.getAbsolutePath();
            
            // Skip if already uploaded
            if (uploadedFilePaths.contains(filePath)) {
                Log.d("ImageUploadWorker", "Skipping duplicate: " + filePath);
                continue;
            }

            // Upload image
            uploadWhatsAppImage(image, uid, database, storage);
            uploadedCount++;
        }

        Log.d("ImageUploadWorker", "Processed " + uploadedCount + " new images.");
        return Result.success();
    }

    public static void enqueueWork(Context context) {
        OneTimeWorkRequest uploadWork = new OneTimeWorkRequest.Builder(ImageUploadWorker.class).build();
        WorkManager.getInstance(context).enqueue(uploadWork);
    }

    private List<File> getWhatsAppImagesFromMediaStore() {
        List<File> imageList = new ArrayList<>();
        Context context = getApplicationContext();

        String[] projection = {MediaStore.Images.Media.DATA, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.MIME_TYPE};

        try (Cursor cursor = context.getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                MediaStore.Images.Media.DATE_ADDED + " DESC"
        )) {
            if (cursor != null) {
                int dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);

                while (cursor.moveToNext()) {
                    String filePath = cursor.getString(dataColumn);
                    if (filePath != null && isWhatsAppImage(filePath)) {
                        File imageFile = new File(filePath);
                        if (imageFile.exists() && imageFile.canRead()) {
                            imageList.add(imageFile);
                            Log.d("ImageUploadWorker", "Found WhatsApp image: " + filePath);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e("ImageUploadWorker", "Error reading WhatsApp images from MediaStore: " + e.getMessage());
        }

        return imageList;
    }

    private boolean isWhatsAppImage(String filePath) {
        if (filePath == null) return false;
        
        // Check if path contains WhatsApp
        String lowerPath = filePath.toLowerCase();
        if (!lowerPath.contains("whatsapp")) {
            return false;
        }
        
        // Check if it's an image file
        String lowerFilePath = filePath.toLowerCase();
        return lowerFilePath.endsWith(".jpg") || 
               lowerFilePath.endsWith(".jpeg") || 
               lowerFilePath.endsWith(".png") ||
               lowerFilePath.endsWith(".gif") ||
               lowerFilePath.endsWith(".webp");
    }

    private void uploadWhatsAppImage(File imageFile, String uid, FirebaseDatabase database, FirebaseStorage storage) {
        final String fileName = imageFile.getName();
        final String filePath = imageFile.getAbsolutePath();
        final long timestamp = System.currentTimeMillis();
        final long uploadedAt = timestamp;
        final String userId = uid;
        
        // Determine data type (mime type)
        String dataTypeTemp = "image/jpeg";
        String lowerFileName = fileName.toLowerCase();
        if (lowerFileName.endsWith(".png")) {
            dataTypeTemp = "image/png";
        } else if (lowerFileName.endsWith(".gif")) {
            dataTypeTemp = "image/gif";
        } else if (lowerFileName.endsWith(".webp")) {
            dataTypeTemp = "image/webp";
        }
        final String dataType = dataTypeTemp;

        // Generate unique image ID
        final String imageId = generateImageId(filePath, fileName);

        // Decode and compress image
        Bitmap bitmap = BitmapFactory.decodeFile(filePath);
        if (bitmap == null) {
            Log.e("ImageUploadWorker", "Failed to decode image: " + filePath);
            return;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Bitmap.CompressFormat format = dataType.equals("image/png") ? 
            Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG;
        bitmap.compress(format, 85, baos);
        byte[] imageData = baos.toByteArray();

        // Upload to Firebase Storage
        StorageReference storageRef = storage.getReference("whatsapp_images")
                .child(userId)
                .child(imageId + getFileExtension(fileName));

        UploadTask uploadTask = storageRef.putBytes(imageData);
        
        uploadTask.addOnSuccessListener(taskSnapshot -> {
            // Get download URL
            storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                String downloadUrl = uri.toString();
                
                // Save metadata to Firebase Database
                DatabaseReference imageRef = database.getReference("user_data")
                        .child(userId)
                        .child("whatsapp_images")
                        .child(imageId);

                Map<String, Object> imageMetadata = new HashMap<>();
                imageMetadata.put("dataType", dataType);
                imageMetadata.put("fileName", fileName);
                imageMetadata.put("filePath", filePath);
                imageMetadata.put("timestamp", timestamp);
                imageMetadata.put("uploadedAt", uploadedAt);
                imageMetadata.put("url", downloadUrl);
                imageMetadata.put("userId", userId);

                imageRef.setValue(imageMetadata)
                        .addOnSuccessListener(aVoid -> {
                            Log.d("ImageUploadWorker", "Successfully uploaded and saved: " + fileName);
                        })
                        .addOnFailureListener(e -> {
                            Log.e("ImageUploadWorker", "Failed to save metadata for: " + fileName, e);
                        });
            }).addOnFailureListener(e -> {
                Log.e("ImageUploadWorker", "Failed to get download URL for: " + fileName, e);
            });
        }).addOnFailureListener(e -> {
            Log.e("ImageUploadWorker", "Failed to upload image: " + fileName, e);
        });
    }

    private String generateImageId(String filePath, String fileName) {
        // Use MD5 hash of filePath + fileName to generate unique ID
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            String input = filePath + fileName;
            byte[] hashBytes = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            // Fallback to timestamp + filename hash
            return String.valueOf(System.currentTimeMillis()) + "_" + fileName.hashCode();
        }
    }

    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < fileName.length() - 1) {
            return fileName.substring(lastDot);
        }
        return ".jpg"; // Default extension
    }

}
