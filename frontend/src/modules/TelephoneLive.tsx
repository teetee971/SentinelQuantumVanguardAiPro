import React, { useState, useRef } from "react";

export default function TelephoneLive(){
  const [reply,setReply]=useState(""); const [listening,setListening]=useState(false);
  const mediaRef = useRef<MediaRecorder|null>(null);

  async function startRecording(){
    const stream = await navigator.mediaDevices.getUserMedia({audio:true});
    const rec = new MediaRecorder(stream); mediaRef.current=rec;
    const chunks:BlobPart[]=[];
    rec.ondataavailable=e=>chunks.push(e.data);
    rec.onstop=async()=>{
      const blob = new Blob(chunks,{type:"audio/webm"}); 
      const base64 = await blob.arrayBuffer().then(b=>{
        const bytes = new Uint8Array(b);
        let binary = '';
        for (let i = 0; i < bytes.length; i++) {
          binary += String.fromCharCode(bytes[i]);
        }
        return btoa(binary);
      });
      const r=await fetch("/.netlify/functions/voice",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({audioBase64:base64})});
      const d=await r.json(); if(d.audioBase64){ 
        const audio=new Audio("data:audio/mp3;base64,"+d.audioBase64); audio.play(); 
        setReply(d.reply);
      }
    };
    rec.start(); setListening(true);
    setTimeout(()=>{rec.stop();setListening(false);},5000);
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
