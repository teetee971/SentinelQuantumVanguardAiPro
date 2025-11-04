import { initializeApp } from "firebase/app";
import { getFirestore } from "firebase/firestore";

const firebaseConfig = {
  apiKey: "TA_CLE_API",
  authDomain: "sentinel-ai.firebaseapp.com",
  projectId: "sentinel-ai",
  storageBucket: "sentinel-ai.appspot.com",
  messagingSenderId: "1234567890",
  appId: "1:1234567890:web:xxxxxx"
};

const app = initializeApp(firebaseConfig);
export const db = getFirestore(app);
