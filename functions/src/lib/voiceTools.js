import axios from "axios";

// Convertit un flux audio Base64 en texte (Whisper Realtime or provider)
export async function streamToText(audioBase64){
  // This example uses OpenAI Whisper endpoint as a placeholder.
  try{
    const resp = await axios.post(
      "https://api.openai.com/v1/audio/transcriptions",
      { file: audioBase64, model: "whisper-1" },
      { headers:{Authorization:`Bearer ${process.env.OPENAI_API_KEY}`} }
    );
    return resp.data.text || "";
  }catch(e){ return ""; }
}

// Génère la voix de synthèse (OpenAI TTS or provider)
export async function textToSpeech(text,tone){
  try{
    const voice = tone==="calme"?"alloy":"verse";
    const resp = await axios.post(
      "https://api.openai.com/v1/audio/speech",
      { model:"gpt-4o-mini-tts", voice, input:text },
      { headers:{Authorization:`Bearer ${process.env.OPENAI_API_KEY}`}, responseType:"arraybuffer" }
    );
    return Buffer.from(resp.data,"binary").toString("base64");
  }catch(e){ return null; }
}
