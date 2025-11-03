import axios from "axios";

// Convertit un flux audio Base64 en texte (Whisper Realtime ou Google STT)
export async function streamToText(audioBase64){
  const FormData = (await import('form-data')).default;
  const buffer = Buffer.from(audioBase64, 'base64');
  const form = new FormData();
  form.append('file', buffer, {
    filename: 'audio.webm',
    contentType: 'audio/webm'
  });
  form.append('model', 'whisper-1');
  
  const resp = await axios.post(
    "https://api.openai.com/v1/audio/transcriptions",
    form,
    { 
      headers: {
        ...form.getHeaders(),
        Authorization: `Bearer ${process.env.OPENAI_API_KEY}`
      }
    }
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
