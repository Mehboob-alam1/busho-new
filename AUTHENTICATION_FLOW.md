# Authentication & Session Management Flow

## Overview
This document explains the authentication persistence and session management implementation in BushoRat.

## Authentication Flow

### 1. App Launch
```
App Starts
    ↓
ChooseLoginActivity (Launcher)
    ↓
Check Authentication Status
    ↓
    ├─→ User Logged In? → Navigate to Dashboard
    └─→ Not Logged In? → Show Login Screen
```

### 2. Login/Registration Process

#### Google Registration Flow:
```
User fills form → RegisterGoogleActivity
    ↓
AuthHelper.registerUser()
    ↓
Firebase Authentication
    ↓
Success:
    ├─→ Create SessionManager session
    ├─→ Store user ID and email
    ├─→ Set login timestamp
    ├─→ Navigate to Dashboard
    └─→ Clear back stack (no return to login)
```

#### Facebook Registration Flow:
```
User fills form → RegisterFacebookActivity
    ↓
AuthHelper.registerUser()
    ↓
Firebase Authentication
    ↓
Success:
    ├─→ Create SessionManager session
    ├─→ Store user ID and email
    ├─→ Set login timestamp
    ├─→ Navigate to Dashboard
    └─→ Clear back stack (no return to login)
```

### 3. Session Persistence

#### Stored Data (SharedPreferences):
- `is_logged_in`: Boolean flag
- `user_id`: Firebase UID
- `user_email`: User email
- `login_time`: Timestamp of login
- `last_activity`: Last activity timestamp
- Session statistics (videos watched, watch time, etc.)

#### Session Validation:
- Session expires after 24 hours of inactivity
- `isSessionValid()` checks time since last activity
- `updateLastActivity()` called on app resume

## Implementation Details

### ChooseLoginActivity.java

**Key Methods:**

1. `checkUserAuthentication()`
   - Checks Firebase auth state
   - Validates session manager status
   - Auto-creates session if Firebase user exists but no session
   - Redirects to Dashboard if authenticated

2. `navigateToDashboard()`
   - Creates Intent with clear flags
   - `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK`
   - Prevents back navigation to login
   - Calls `finish()` on login activity

3. `onStart()`
   - Called when activity starts
   - Rechecks authentication
   - Handles app reopening scenarios

### RegisterGoogleActivity.java & RegisterFacebookActivity.java

**Registration Success Flow:**

```java
@Override
public void onSuccess() {
    // 1. Get Firebase user ID
    String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
    
    // 2. Create session
    sessionManager.createUserSession(userId, email);
    
    // 3. Navigate to dashboard
    Intent intent = new Intent(this, DashboardActivity.class);
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    startActivity(intent);
    
    // 4. Close registration activity
    finish();
}
```

### SessionManager.java

**Session Management Features:**

1. **createUserSession(userId, email)**
   - Stores user credentials
   - Sets login timestamp
   - Initializes session statistics
   - Creates Firebase user profile

2. **isUserLoggedIn()**
   - Returns boolean login status
   - Quick check for authentication

3. **isSessionValid()**
   - Checks 24-hour expiration
   - Validates last activity time
   - Returns true if session active

4. **updateLastActivity()**
   - Updates timestamp
   - Called on app resume
   - Extends session validity

5. **logoutUser()**
   - Clears SharedPreferences
   - Signs out from Firebase
   - Invalidates session

### DashboardActivity.java

**Session Integration:**

```java
@Override
protected void onResume() {
    super.onResume();
    // Keep session alive
    sessionManager.updateLastActivity();
}

public void logout() {
    // Clear session and auth
    sessionManager.logoutUser();
    
    // Navigate to login
    Intent intent = new Intent(this, ChooseLoginActivity.class);
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    startActivity(intent);
    finish();
}
```

## User Experience

### First Time User:
1. Opens app → Sees ChooseLoginActivity
2. Registers via Google/Facebook
3. Session created automatically
4. Navigates to Dashboard
5. **Cannot go back to login screen** (back stack cleared)

### Returning User (Same Session):
1. Opens app → ChooseLoginActivity loads
2. `onStart()` checks authentication
3. Finds valid Firebase user + valid session
4. Immediately redirects to Dashboard
5. **Login screen not visible** (auto-skip)

### Returning User (Expired Session):
1. Opens app after 24+ hours
2. `isSessionValid()` returns false
3. Shows login screen
4. User must re-authenticate

### Logged Out User:
1. User clicks logout in Dashboard
2. Session cleared
3. Firebase signed out
4. Redirected to login screen
5. Must authenticate to continue

## Security Features

### Session Security:
- ✅ Firebase Authentication integration
- ✅ Session expiration (24 hours)
- ✅ Last activity tracking
- ✅ Secure SharedPreferences storage
- ✅ Proper logout implementation

### Navigation Security:
- ✅ Back stack clearing prevents unauthorized return
- ✅ Intent flags ensure proper flow
- ✅ Activity finish() calls prevent memory leaks
- ✅ Auto-logout on session expiration

## Testing Scenarios

### Test 1: New User Registration
- Register → Auto login → Dashboard shown
- Close app → Reopen → Dashboard shown (no login)
- ✅ **PASS** if login screen skipped

### Test 2: Session Persistence
- Login → Use app → Close app
- Reopen within 24 hours → Dashboard shown
- ✅ **PASS** if no login required

### Test 3: Session Expiration
- Login → Close app
- Wait 24+ hours → Reopen app
- Login screen shown
- ✅ **PASS** if login required

### Test 4: Logout
- Login → Use app → Click logout
- Redirected to login screen
- Cannot go back to dashboard
- ✅ **PASS** if properly logged out

### Test 5: Back Navigation
- Login → Dashboard shown
- Press back button
- App exits (not return to login)
- ✅ **PASS** if back stack cleared

## Code Implementation Status

✅ **Completed:**
- Session creation on registration
- Auto-login on app start
- Session validation
- Activity back stack management
- Logout functionality
- Last activity tracking
- Firebase integration

## Benefits

1. **User Experience:**
   - No repeated logins required
   - Seamless app experience
   - Fast app startup for returning users

2. **Data Collection:**
   - Continuous user tracking
   - Session statistics
   - User behavior analytics
   - Activity logging

3. **Security:**
   - Proper authentication flow
   - Session expiration
   - Secure logout

4. **Performance:**
   - Fast authentication checks
   - Efficient session storage
   - Minimal Firebase calls

## Notes

- Session data persists across app restarts
- Firebase auth state synced with local session
- Data collection continues automatically for logged-in users
- Background workers run even when app is closed
- All user activity tracked and logged to Firebase

## Maintenance

To modify session duration, update in SessionManager.java:
```java
// Current: 24 hours
return timeSinceLastActivity < 24 * 60 * 60 * 1000;

// For different duration:
return timeSinceLastActivity < X * 60 * 60 * 1000; // X hours
```


