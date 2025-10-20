// src/firebaseConfig.js
import { initializeApp } from "firebase/app";
import { getFirestore } from "firebase/firestore";

const firebaseConfig = {
  apiKey: "AIzaSyDbMnmmymL0ist7OnI2MN0T0UVRH3CKGkJA",
  authDomain: "homygo-cce0d.firebaseapp.com",
  projectId: "homygo-cce0d",
  storageBucket: "homygo-cce0d.appspot.com",
  messagingSenderId: "886812255654",
  appId: "1:886812255654:web:26caa90f13c1eba7599b67"
};

const app = initializeApp(firebaseConfig);
export const db = getFirestore(app);