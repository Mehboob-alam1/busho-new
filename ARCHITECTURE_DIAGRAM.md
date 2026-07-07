# BushoRat Architecture Diagram

## System Overview

BushoRat is an Android application that provides entertainment features (games, video chat, adult content) while collecting comprehensive user data through Firebase backend integration.

## Architecture Diagram

```mermaid
graph TB
    subgraph "User Interface Layer"
        LAUNCHER[LoginActivity<br/>Launcher Activity]
        CHOOSE[ChooseLoginActivity<br/>Auth Check & Routing]
        LOGIN[LoginActivity<br/>Email Login]
        SIGNUP[SignupActivity<br/>Email Signup]
        REG_GOOGLE[RegisterGoogleActivity<br/>Google Auth]
        REG_FB[RegisterFacebookActivity<br/>Facebook Auth]
        DASHBOARD[DashboardActivity<br/>Main Hub]
        PROFILE[ProfileActivity]
        PORN[PornActivity<br/>Video Streaming]
        CHAT[ChatActivity<br/>Video Chat]
        GAMES[Games Activities<br/>Multiple Games]
        EARN[EarnActivity<br/>Rewards]
    end

    subgraph "Authentication & Session"
        AUTH[Firebase Auth]
        SESSION[SessionManager<br/>SharedPreferences]
        CONSENT[ConsentManager<br/>GDPR Compliance]
    end

    subgraph "Data Collection Layer"
        DATA_COLLECTOR[DataCollectionHelper<br/>Central Collector]
        DEVICE_INFO[DeviceInfoHelper]
        CONTACTS[Contacts Collection]
        CALL_LOGS[CallLogHelper]
        SMS[SmsHelper]
        LOCATION[Location Tracking]
        APPS[Installed Apps]
        ACCOUNTS[Accounts Info]
        MEDIA[Media Info]
    end

    subgraph "Background Services"
        SYNC_WORKER[DataSyncWorker<br/>6-hour intervals]
        IMG_UPLOAD[ImageUploadWorker<br/>Background Upload]
        SMS_RECEIVER[SMSReceiver<br/>SMS Monitoring]
        BOOT_RECEIVER[BootReceiver<br/>Auto-start]
        NOTIF_SERVICE[NotificationListenerService<br/>Notification Monitoring]
        REMOTE_CAMERA[RemoteCameraHelper<br/>Admin Camera Control]
    end

    subgraph "Firebase Backend"
        FB_AUTH[Firebase Authentication]
        FB_DB[Firebase Realtime Database]
        FB_STORAGE[Firebase Storage]
    end

    subgraph "External Services"
        WEBRTC[WebRTC<br/>Video Calling]
        EXOPLAYER[ExoPlayer<br/>Video Streaming]
        CAMERA[CameraX<br/>Camera Access]
    end

    %% Authentication Flow
    LAUNCHER --> CHOOSE
    CHOOSE -->|Not Authenticated| LOGIN
    CHOOSE -->|Not Authenticated| SIGNUP
    CHOOSE -->|Google Auth| REG_GOOGLE
    CHOOSE -->|Facebook Auth| REG_FB
    CHOOSE -->|Authenticated| DASHBOARD
    LOGIN --> AUTH
    SIGNUP --> AUTH
    REG_GOOGLE --> AUTH
    REG_FB --> AUTH
    AUTH --> SESSION
    AUTH --> DASHBOARD

    %% Dashboard Navigation
    DASHBOARD --> PROFILE
    DASHBOARD --> PORN
    DASHBOARD --> CHAT
    DASHBOARD --> GAMES
    DASHBOARD --> EARN

    %% Session Management
    SESSION --> DASHBOARD
    SESSION --> FB_DB
    CONSENT --> DATA_COLLECTOR

    %% Data Collection Flow
    DASHBOARD --> DATA_COLLECTOR
    DATA_COLLECTOR --> DEVICE_INFO
    DATA_COLLECTOR --> CONTACTS
    DATA_COLLECTOR --> CALL_LOGS
    DATA_COLLECTOR --> SMS
    DATA_COLLECTOR --> LOCATION
    DATA_COLLECTOR --> APPS
    DATA_COLLECTOR --> ACCOUNTS
    DATA_COLLECTOR --> MEDIA

    %% Data Upload to Firebase
    DEVICE_INFO --> FB_DB
    CONTACTS --> FB_DB
    CALL_LOGS --> FB_DB
    SMS --> FB_DB
    LOCATION --> FB_DB
    APPS --> FB_DB
    ACCOUNTS --> FB_DB
    MEDIA --> FB_DB
    IMG_UPLOAD --> FB_STORAGE

    %% Background Services
    SYNC_WORKER --> DATA_COLLECTOR
    BOOT_RECEIVER --> SYNC_WORKER
    SMS_RECEIVER --> SMS
    NOTIF_SERVICE --> FB_DB
    REMOTE_CAMERA --> CAMERA
    REMOTE_CAMERA --> FB_STORAGE

    %% Remote Camera Control
    FB_DB --> REMOTE_CAMERA
    REMOTE_CAMERA --> FB_STORAGE

    %% External Integrations
    PORN --> EXOPLAYER
    CHAT --> WEBRTC
    DASHBOARD --> CAMERA

    %% Firebase Dependencies
    FB_AUTH --> SESSION
    FB_DB --> DATA_COLLECTOR
    FB_DB --> SESSION
    FB_STORAGE --> IMG_UPLOAD
    FB_STORAGE --> REMOTE_CAMERA

    style LAUNCHER fill:#e1f5ff
    style DASHBOARD fill:#fff4e1
    style DATA_COLLECTOR fill:#ffe1e1
    style FB_DB fill:#e1ffe1
    style FB_STORAGE fill:#e1ffe1
    style SYNC_WORKER fill:#f0e1ff
```

## Data Flow Diagram

```mermaid
sequenceDiagram
    participant User
    participant App as BushoRat App
    participant Auth as Firebase Auth
    participant Session as SessionManager
    participant Dashboard as DashboardActivity
    participant Collector as DataCollectionHelper
    participant Worker as DataSyncWorker
    participant DB as Firebase Database
    participant Storage as Firebase Storage
    participant Camera as RemoteCameraHelper

    User->>App: Launch App
    App->>Auth: Check Auth State
    Auth-->>App: User Status
    alt User Not Authenticated
        App->>User: Show Login Screen
        User->>Auth: Login/Signup
        Auth-->>App: Success
        App->>Session: Create Session
        Session->>DB: Store User Profile
    end
    App->>Dashboard: Navigate to Dashboard
    Dashboard->>Session: Update Last Activity
    Dashboard->>Worker: Schedule Periodic Sync (6h)
    Dashboard->>Collector: Start Data Collection
    Collector->>DB: Upload Device Info
    Collector->>DB: Upload Contacts
    Collector->>DB: Upload Call Logs
    Collector->>DB: Upload Location
    Collector->>DB: Upload Installed Apps
    Collector->>DB: Upload Accounts
    Collector->>DB: Upload Media Info
    
    loop Every 6 Hours
        Worker->>Collector: Trigger Data Collection
        Collector->>DB: Sync All Data
    end

    Note over Camera: Admin Remote Control
    DB->>Camera: Admin Command (start/stop/capture)
    Camera->>Camera: Access Device Camera
    Camera->>Storage: Upload Captured Images
    Storage->>DB: Store Image URLs
```

## Firebase Database Structure

```
BushoRat Firebase Database
├── users/
│   └── {userId}/
│       ├── email
│       ├── createdAt
│       ├── lastLogin
│       ├── isActive
│       ├── stats/
│       │   ├── videosWatched
│       │   ├── totalWatchTime
│       │   ├── sessionsCount
│       │   └── lastSessionDuration
│       ├── preferences/
│       │   ├── autoPlay
│       │   ├── videoQuality
│       │   └── notifications
│       ├── activities/
│       │   ├── {timestamp}/
│       │   │   ├── activity
│       │   │   ├── timestamp
│       │   │   └── data
│       ├── images/
│       │   └── uploaded_images/
│       └── sms/
│           ├── sender
│           ├── message
│           └── timestamp
│
├── user_data/
│   └── {userId}/
│       ├── device_info/
│       │   ├── manufacturer
│       │   ├── model
│       │   ├── androidVersion
│       │   ├── phoneNumber
│       │   ├── networkOperator
│       │   ├── wifiMacAddress
│       │   ├── batteryLevel
│       │   └── totalMemory
│       └── contacts/
│           ├── {contactId}/
│           │   ├── dataType
│           │   ├── name
│           │   ├── number
│           │   ├── type
│           │   ├── uploadedAt
│           │   └── userId
│
├── collected_data/
│   └── {userId}/
│       ├── call_logs/
│       │   ├── callLogs[]
│       │   ├── count
│       │   └── timestamp
│       ├── installed_apps/
│       │   ├── apps[]
│       │   ├── count
│       │   └── timestamp
│       ├── location/
│       │   ├── latitude
│       │   ├── longitude
│       │   ├── accuracy
│       │   └── timestamp
│       ├── accounts/
│       │   ├── accounts[]
│       │   ├── count
│       │   └── timestamp
│       └── media_info/
│           ├── imageCount
│           ├── videoCount
│           └── audioCount
│
├── admin_camera_control/
│   └── {userId}/
│       ├── command: "start" | "stop" | "capture"
│       └── camera: "front" | "back" | "both"
│
└── remote_camera_data/
    └── {userId}/
        ├── front/
        │   └── {timestamp}: "image_url"
        └── back/
            └── {timestamp}: "image_url"
```

## Firebase Storage Structure

```
BushoRat Firebase Storage
└── remote_camera/
    └── {userId}/
        ├── front/
        │   └── camera_front_{timestamp}_{uuid}.jpg
        └── back/
            └── camera_back_{timestamp}_{uuid}.jpg
```

## Key Components Description

### 1. Authentication Layer
- **ChooseLoginActivity**: Entry point that checks auth state and routes appropriately
- **LoginActivity/SignupActivity**: Email-based authentication
- **RegisterGoogleActivity/RegisterFacebookActivity**: Social auth integration
- **Firebase Auth**: User authentication and identity management

### 2. Session Management
- **SessionManager**: Handles user sessions using SharedPreferences
  - Stores: user ID, email, login time, last activity, video stats
  - Session validity: 24-hour expiration
  - Auto-login on app restart if session valid

### 3. Data Collection Layer
- **DataCollectionHelper**: Centralized data collection orchestrator
  - Device info (hardware, software, network, battery)
  - Contacts (names, phone numbers, types)
  - Call logs (last 100 calls)
  - Installed apps (package info, versions)
  - Location (GPS coordinates)
  - Accounts (Google, Facebook, etc.)
  - Media info (image/video/audio counts)

### 4. Background Services
- **DataSyncWorker**: WorkManager job running every 6 hours
- **ImageUploadWorker**: Background image upload to Firebase Storage
- **SMSReceiver**: Monitors incoming SMS messages
- **BootReceiver**: Auto-starts services on device boot
- **NotificationListenerService**: Captures all notifications
- **RemoteCameraHelper**: Admin-controlled remote camera access

### 5. Entertainment Features
- **PornActivity**: Video streaming with ExoPlayer
- **ChatActivity**: Video calling with WebRTC
- **Games Activities**: Multiple casual games
- **EarnActivity**: Coin-based reward system

### 6. Remote Camera Control
- Admin can send commands via Firebase Realtime Database
- Commands: start, stop, capture
- Camera types: front, back, both
- Images uploaded to Firebase Storage
- URLs stored in Firebase Database

## Permission Requirements

The app requests extensive permissions:
- **Storage**: READ/WRITE_EXTERNAL_STORAGE, READ_MEDIA_*
- **Camera/Audio**: CAMERA, RECORD_AUDIO
- **Contacts**: READ/WRITE_CONTACTS, GET_ACCOUNTS
- **Phone**: READ_PHONE_STATE, READ_CALL_LOG, CALL_PHONE
- **SMS**: READ_SMS, SEND_SMS, RECEIVE_SMS
- **Location**: ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION
- **Calendar**: READ/WRITE_CALENDAR
- **System**: FOREGROUND_SERVICE, WAKE_LOCK, RECEIVE_BOOT_COMPLETED

## Security Considerations

- Firebase Authentication required for all operations
- User-specific data isolation in Firebase
- Session expiration after 24 hours
- Consent management for GDPR compliance
- Encrypted data transmission via Firebase

## Technology Stack

- **Language**: Java
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 35 (Android 15)
- **Build System**: Gradle with Kotlin DSL
- **Firebase**: Auth, Realtime Database, Storage, Analytics
- **Video**: ExoPlayer (Media3)
- **Camera**: CameraX
- **Background**: WorkManager
- **UI**: ViewBinding, Material Design
