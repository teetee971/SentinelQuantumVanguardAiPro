import axios from "axios";

// Convertit un flux audio Base64 en texte (Whisper Realtime ou Google STT)
export async function streamToText(audioBase64){
  const resp = await axios.post(
    "https://api.openai.com/v1/audio/transcriptions",
    { file: audioBase64, model: "whisper-1" },
    { headers:{Authorization:`Bearer ${process.env.OPENAI_API_KEY}`} }
  );
  return resp.data.text || "";
}

// Génère la voix de synthèse (OpenAI TTS v2 / ElevenLabs / Google TTS)
export async function textToSpeech(text,tone){
  const voice = tone==="calme"?"alloy":"verse";
  const resp = await axios.post(
    "https://api.openai.com/v1/audio/speech",
    { model:"gpt-4o-mini-tts", voice, input:text },
    { headers:{Authorization:`Bearer ${process.env.OPENAI_API_KEY}`}, responseType:"arraybuffer" }
  );
  return Buffer.from(resp.data,"binary").toString("base64");
}
