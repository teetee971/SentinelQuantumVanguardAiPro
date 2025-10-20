import React, { useState } from "react";

export default function SentinelAI() {
  const [prompt, setPrompt] = useState("");
  const [response, setResponse] = useState("");
  const [loading, setLoading] = useState(false);

  const sendPrompt = async () => {
    if (!prompt.trim()) return;
    setLoading(true);
    setResponse("");
    try {
      const res = await fetch("/api/ia", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ prompt })
      });
      const data = await res.json();
      setResponse(data.output || "(Aucune réponse)");
    } catch (err) {
      setResponse("Erreur : " + err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-black text-white p-6 flex flex-col items-center justify-center">
      <h1 className="text-3xl font-bold mb-6 text-lime-400 text-center">Sentinel Quantum Vanguard AI Pro</h1>
      <textarea
        className="w-full max-w-xl p-4 rounded-xl bg-gray-900 border border-gray-700 text-white"
        rows={4}
        value={prompt}
        placeholder="Pose ta question ici..."
        onChange={(e) => setPrompt(e.target.value)}
      ></textarea>
      <button
        onClick={sendPrompt}
        disabled={loading}
        className="mt-4 px-6 py-2 rounded-full bg-lime-500 hover:bg-lime-600 text-black font-semibold disabled:opacity-50"
      >
        {loading ? "Chargement..." : "Envoyer à l'IA"}
      </button>
      <div className="mt-6 w-full max-w-xl p-4 bg-gray-800 rounded-lg min-h-[100px]">
        {response ? <pre className="whitespace-pre-wrap">{response}</pre> : <span className="text-gray-400">La réponse s'affichera ici.</span>}
      </div>
    </div>
  );
}