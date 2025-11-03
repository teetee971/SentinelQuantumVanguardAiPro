import express from "express";
import admin from "firebase-admin";
import { streamToText, textToSpeech } from "./lib/voiceTools.js";
import { callGPT } from "./lib/aiCore.js";

const app = express();
app.use(express.json());
if (!admin.apps.length) admin.initializeApp();
const db = admin.firestore();

app.post("/voice", async (req,res)=>{
  const { audioBase64, sessionId } = req.body;
  if(!audioBase64) return res.status(400).json({error:"audioBase64 required"});
  try{
    const textIn = await streamToText(audioBase64);
    const ai = await callGPT(textIn);
    const audioOut = await textToSpeech(ai.reply, ai.tone);
    await db.collection("phone_conversations").doc(sessionId||Date.now().toString())
      .set({ text_in:textIn, text_out:ai.reply, tone:ai.tone, ts:Date.now() },{merge:true});
    res.json({ audioBase64: audioOut, reply: ai.reply });
  }catch(e){ res.status(500).json({error:e.message}); }
});
export const api = app;
