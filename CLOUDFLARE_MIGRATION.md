# Cloudflare is no longer used for core app/admin.

The Android app and admin panel are back on the original Firebase project **phisinig**:

- Auth: Firebase Authentication (Google Sign-In on app)
- Data: Realtime Database (`user_data/`, `app_visibility/`, …)
- Gallery: Firebase Storage

The `cloudflare/` folder left in the repo is unused. Prefer Firebase for all current features.
