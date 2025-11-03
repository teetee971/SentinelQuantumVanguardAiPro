// Import the functions you need from the SDKs you need
import { initializeApp } from "firebase/app";
import { getAnalytics } from "firebase/analytics";
import { getFirestore } from "firebase/firestore";
// TODO: Add SDKs for Firebase products that you want to use
// https://firebase.google.com/docs/web/setup#available-libraries

// Your web app's Firebase configuration
// For Firebase JS SDK v7.20.0 and later, measurementId is optional
const firebaseConfig = {
  apiKey: "AIzaSyA1bAmv8nsWZykeGQDmdrXqBqNifD1YYZE",
  authDomain: "sentinel-vanguard-ai-pro.firebaseapp.com",
  projectId: "sentinel-vanguard-ai-pro",
  storageBucket: "sentinel-vanguard-ai-pro.firebasestorage.app",
  messagingSenderId: "444130809317",
  appId: "1:444130809317:web:63941d5cb8df0bfcf6cb0c",
  measurementId: "G-PZGVR3D6T9"
};

// Initialize Firebase
const app = initializeApp(firebaseConfig);
const analytics = getAnalytics(app);
export const db = getFirestore(app);
