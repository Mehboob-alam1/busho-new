import { initializeApp } from "firebase/app";
import { getAnalytics } from "firebase/analytics";
import { getAuth } from "firebase/auth";
import { getDatabase } from "firebase/database";
import { getStorage } from "firebase/storage";

// Firebase project: busho-chat
const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY || "AIzaSyDMfmSN8kWeyGY_ldDMoXGr08KObx6dtKs",
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN || "busho-chat.firebaseapp.com",
  databaseURL:
    import.meta.env.VITE_FIREBASE_DATABASE_URL ||
    "https://busho-chat-default-rtdb.firebaseio.com",
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID || "busho-chat",
  storageBucket:
    import.meta.env.VITE_FIREBASE_STORAGE_BUCKET || "busho-chat.firebasestorage.app",
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID || "275490681335",
  appId:
    import.meta.env.VITE_FIREBASE_APP_ID || "1:27549s0681335:web:cd9e18696669a9b6a6bef2",
  measurementId: import.meta.env.VITE_FIREBASE_MEASUREMENT_ID || "G-WR1HNCS0C8",
};

export const app = initializeApp(firebaseConfig);
export const analytics = getAnalytics(app);
export const auth = getAuth(app);
export const db = getDatabase(app);
export const storage = getStorage(app);

