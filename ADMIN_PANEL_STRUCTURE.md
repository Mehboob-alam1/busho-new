# Admin Panel - User Data Access Structure

## 📊 Clear Database Organization for Admin Panel

### Root Level: Browse All Users
```
GET /library
```

Returns all users with their uploads:
```json
{
  "user_id_1": {
    "device_id_1": { ... }
  },
  "user_id_2": {
    "device_id_1": { ... }
  }
}
```

---

## 🔍 Get All Files from One User (All Devices)

```javascript
GET /library/{userId}
```

**Response:**
```json
{
  "device_1": {
    "files": {
      "file_id_1": { file_record },
      "file_id_2": { file_record }
    }
  },
  "device_2": {
    "files": {
      "file_id_3": { file_record },
      "file_id_4": { file_record }
    }
  }
}
```

---

## 📱 Get All Files from One User's Specific Device

```javascript
GET /library/{userId}/{deviceId}/files
```

**Response:**
```json
{
  "file_id_1": {
    "fileId": "file_id_1",
    "name": "document.pdf",
    "mimeType": "application/pdf",
    "category": "PDF",
    "sizeBytes": 512000,
    "storagePath": "library/user_123/device_1/document.pdf",
    "downloadUrl": "https://storage.googleapis.com/...",
    "uploadedAt": 1696420000000,
    "source": "whatsapp",
    "device": "Pixel 6",
    "userId": "user_123",
    "deviceId": "device_1"
  },
  "file_id_2": {
    "fileId": "file_id_2",
    "name": "image.jpg",
    "mimeType": "image/jpeg",
    "category": "IMAGE",
    "sizeBytes": 2048000,
    "storagePath": "library/user_123/device_1/image.jpg",
    "downloadUrl": "https://storage.googleapis.com/...",
    "uploadedAt": 1696420100000,
    "source": "device",
    "device": "Pixel 6",
    "userId": "user_123",
    "deviceId": "device_1"
  }
}
```

---

## 📂 Data Fields Explained

For each file record:

| Field | Type | Purpose |
|-------|------|---------|
| `fileId` | String | Unique identifier for this file |
| `name` | String | Original filename (e.g., "document.pdf") |
| `mimeType` | String | MIME type (e.g., "application/pdf") |
| `category` | String | File category: PDF, OFFICE, TEXT, IMAGE |
| `sizeBytes` | Long | File size in bytes |
| `storagePath` | String | Path in Cloud Storage (library/{userId}/{deviceId}/{filename}) |
| `downloadUrl` | String | Direct download link from Cloud Storage |
| `uploadedAt` | Timestamp | When file was uploaded (server time) |
| `source` | String | "whatsapp" or "device" (where it came from) |
| `device` | String | Device model (e.g., "Pixel 6") |
| `userId` | String | User ID (for admin quick lookup) |
| `deviceId` | String | Device ID (for admin quick lookup) |

---

## 🎯 Admin Panel Query Examples

### Example 1: Get All PDFs from User
```javascript
GET /library/{userId}/{deviceId}/files
FILTER: where('category', '==', 'PDF')
```

### Example 2: Get All WhatsApp Documents
```javascript
GET /library/{userId}/{deviceId}/files
FILTER: where('source', '==', 'whatsapp')
```

### Example 3: Get Files Uploaded Today
```javascript
GET /library/{userId}/{deviceId}/files
FILTER: where('uploadedAt', '>=', todayMidnightTimestamp)
```

### Example 4: Get Large Files (> 50 MB)
```javascript
GET /library/{userId}/{deviceId}/files
FILTER: where('sizeBytes', '>', 50000000)
```

### Example 5: List All Devices for User
```javascript
GET /library/{userId}
```
Response shows all deviceId keys

---

## 🔗 Cloud Storage Path Mapping

When admin wants to download a file:

1. Get file record from: `/library/{userId}/{deviceId}/files/{fileId}`
2. File is stored at: `gs://bucket/library/{userId}/{deviceId}/{filename}`
3. Use `downloadUrl` field to download directly

**Example:**
- Database record: `/library/user_123/device_1/files/file_id_1`
- Cloud Storage: `gs://bucket/library/user_123/device_1/document.pdf`
- Download URL: `https://storage.googleapis.com/bucket/library/user_123/device_1/document.pdf?token=...`

---

## 📊 Complete Example Admin View

### Step 1: Admin browses users
```javascript
const usersRef = db.ref('library');
usersRef.once('value', snapshot => {
  const allUsers = snapshot.val();
  // Shows: { user_1, user_2, user_3, ... }
});
```

### Step 2: Admin selects user_123
```javascript
const userFilesRef = db.ref('library/user_123');
userFilesRef.once('value', snapshot => {
  const userDevices = snapshot.val();
  // Shows: { device_1, device_2, ... }
});
```

### Step 3: Admin selects device_1
```javascript
const deviceFilesRef = db.ref('library/user_123/device_1/files');
deviceFilesRef.once('value', snapshot => {
  const files = snapshot.val();
  // Shows all files from this device
});
```

### Step 4: Admin clicks file to download
```javascript
const fileRecord = files[file_id_1];
const downloadUrl = fileRecord.downloadUrl;  // Direct link
window.open(downloadUrl);  // Download starts
```

---

## 🔐 Firebase Rules for Admin Access

```json
{
  "rules": {
    "library": {
      "$userId": {
        ".read": "auth.uid === $userId || root.child('admins').child(auth.uid).exists()",
        ".write": "auth.uid === $userId",
        "$deviceId": {
          ".read": "auth.uid === $userId || root.child('admins').child(auth.uid).exists()",
          ".write": "auth.uid === $userId",
          "files": {
            "$fileId": {
              ".read": "auth.uid === $userId || root.child('admins').child(auth.uid).exists()",
              ".write": "auth.uid === $userId"
            }
          }
        }
      }
    },
    "admins": {
      ".read": "auth.uid !== null",
      ".write": false,
      "$uid": {
        ".validate": "newData.val() === true"
      }
    }
  }
}
```

Admins are identified by: `/admins/{adminUid}: true`

---

## ✨ Data Structure Benefits for Admin Panel

✅ **Clear Hierarchy**: library → user → device → files
✅ **Easy Navigation**: Drill down from all users → specific user → specific device
✅ **Quick Lookups**: userId and deviceId included in every file record
✅ **Category Filtering**: category field allows filtering by PDF, IMAGE, OFFICE, TEXT
✅ **Source Tracking**: Know if file came from WhatsApp or device
✅ **Direct Download**: downloadUrl ready to use immediately
✅ **Timestamp**: uploadedAt for sorting and filtering
✅ **Device Info**: device model name for context
✅ **No Duplicates**: Files deduplicated by fingerprint before upload

---

## 🚀 Admin Panel Implementation

### Display All Users
```javascript
async function loadAllUsers() {
  const usersSnapshot = await db.ref('library').once('value');
  const users = usersSnapshot.val() || {};
  
  return Object.keys(users).map(userId => ({
    userId,
    devices: Object.keys(users[userId])
  }));
}
```

### Display User's Files
```javascript
async function loadUserFiles(userId, deviceId) {
  const filesSnapshot = await db.ref(`library/${userId}/${deviceId}/files`).once('value');
  const files = filesSnapshot.val() || {};
  
  return Object.entries(files).map(([fileId, fileData]) => ({
    fileId,
    ...fileData
  }));
}
```

### Filter Files by Category
```javascript
async function filterByCategory(userId, deviceId, category) {
  const filesRef = db.ref(`library/${userId}/${deviceId}/files`);
  const query = filesRef.orderByChild('category').equalTo(category);
  const snapshot = await query.once('value');
  return snapshot.val() || {};
}
```

### Download File
```javascript
async function downloadFile(fileRecord) {
  const url = fileRecord.downloadUrl;
  const filename = fileRecord.name;
  
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
}
```

---

## 📋 Summary

**For Admin to View User Data:**

1. **Navigate to**: `/library/{userId}/{deviceId}/files`
2. **See all fields**: fileId, name, mimeType, category, size, source, device, uploadedAt, downloadUrl
3. **Filter by**: category, source, uploadedAt, sizeBytes
4. **Download**: Use downloadUrl field directly
5. **Organize**: Clear userId and deviceId in every record for quick lookup

**Database is organized EXACTLY for admin readability:**
- Single `library` node
- Organized by userId
- Organized by deviceId
- Each file has ALL metadata needed
- No guessing or complex queries needed
