import React, { useState, useRef } from "react";

export default function TelephoneLive(){
  const [reply,setReply]=useState(""); const [listening,setListening]=useState(false);
  const mediaRef = useRef(null);

  async function startRecording(){
    try{
      const stream = await navigator.mediaDevices.getUserMedia({audio:true});
      const rec = new MediaRecorder(stream);
      mediaRef.current = rec;
      const chunks = [];
      rec.ondataavailable = e => chunks.push(e.data);
      rec.onstop = async () => {
        const blob = new Blob(chunks,{type:"audio/webm"});
        const arrayBuffer = await blob.arrayBuffer();
        const base64 = btoa(String.fromCharCode(...new Uint8Array(arrayBuffer)));
        const r = await fetch("/.netlify/functions/voice",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({audioBase64:base64})});
        const d = await r.json();
        if(d.audioBase64){ const audio = new Audio("data:audio/mp3;base64,"+d.audioBase64); audio.play(); }
        setReply(d.reply || "");
      };
      rec.start(); setListening(true);
      setTimeout(()=>{ rec.stop(); setListening(false); }, 5000);
    }catch(e){ console.error(e); alert('Microphone error: ' + e.message); }
  }

  return (
    <div className="p-6 text-white text-center space-y-4">
      <h1 className="text-2xl font-bold">Agent Vocal IA Sentinel SRV-1</h1>
      <p>Parle pendant 5 secondes : l'IA t'écoute et te répond.</p>
      <button onClick={startRecording} disabled={listening} className="btn bg-blue-600 px-4 py-2 rounded">
        {listening?"Écoute…":"Commencer"}
      </button>
      {reply && <p className="mt-4 text-lg text-green-400">Réponse : {reply}</p>}
    </div>
  );
}
