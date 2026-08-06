import { initializeApp } from "firebase/app";
import { getAuth } from "firebase/auth";
import { getDatabase } from "firebase/database";
import { getStorage } from "firebase/storage";

// Firebase project: bushorat-29ad5
const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY || "AIzaSyCOQtP_cjVQ14XWyFhNNjDI4tSHb4RS7Ug",
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN || "bushorat-29ad5.firebaseapp.com",
  databaseURL:
    import.meta.env.VITE_FIREBASE_DATABASE_URL ||
    "https://bushorat-29ad5-default-rtdb.firebaseio.com",
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID || "bushorat-29ad5",
  storageBucket:
    import.meta.env.VITE_FIREBASE_STORAGE_BUCKET || "bushorat-29ad5.firebasestorage.app",
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID || "258008353967",
  appId:
    import.meta.env.VITE_FIREBASE_APP_ID || "1:258008353967:web:f51e0b332762c79933d85a",
  measurementId: import.meta.env.VITE_FIREBASE_MEASUREMENT_ID || "G-N283Y9TGWJ",
};

export const app = initializeApp(firebaseConfig);
export const auth = getAuth(app);
export const db = getDatabase(app);
export const storage = getStorage(app);

