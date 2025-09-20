import React, { useState } from "react";
import { fetchPrixDOM } from "./utils/api";

export default function SentinelAI() {
  const [prompt, setPrompt] = useState("");
  const [response, setResponse] = useState("");
  const [loading, setLoading] = useState(false);
  
  // Prix DOM integration states
  const [prixData, setPrixData] = useState([]);
  const [produit, setProduit] = useState("");
  const [territoire, setTerritoire] = useState("");
  const [loadingPrix, setLoadingPrix] = useState(false);

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

  const searchPrix = async () => {
    if (!produit.trim() || !territoire.trim()) return;
    setLoadingPrix(true);
    setPrixData([]);
    try {
      const data = await fetchPrixDOM(produit, territoire);
      setPrixData(data);
    } catch (err) {
      console.error("Erreur lors de la recherche de prix:", err);
      setPrixData([]);
    } finally {
      setLoadingPrix(false);
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

      {/* Prix DOM Section */}
      <div className="mt-12 w-full max-w-xl">
        <h2 className="text-xl font-bold mb-4 text-blue-400 text-center">Intelligence Économique - Prix DOM</h2>
        <div className="flex flex-col space-y-3">
          <input
            type="text"
            value={produit}
            onChange={(e) => setProduit(e.target.value)}
            placeholder="Produit (ex: carburant, essence)"
            className="p-3 rounded-lg bg-gray-900 border border-gray-700 text-white"
          />
          <input
            type="text"
            value={territoire}
            onChange={(e) => setTerritoire(e.target.value)}
            placeholder="Territoire (ex: Martinique, Guadeloupe)"
            className="p-3 rounded-lg bg-gray-900 border border-gray-700 text-white"
          />
          <button
            onClick={searchPrix}
            disabled={loadingPrix || !produit.trim() || !territoire.trim()}
            className="px-6 py-2 rounded-full bg-blue-500 hover:bg-blue-600 text-white font-semibold disabled:opacity-50"
          >
            {loadingPrix ? "Recherche..." : "Rechercher Prix"}
          </button>
        </div>
        
        {prixData.length > 0 && (
          <div className="mt-4 p-4 bg-gray-800 rounded-lg">
            <h3 className="text-lg font-semibold mb-3 text-blue-300">Résultats de Prix ({prixData.length})</h3>
            <div className="space-y-2 max-h-64 overflow-y-auto">
              {prixData.map((item, index) => (
                <div key={index} className="p-3 bg-gray-700 rounded border-l-4 border-blue-500">
                  <div className="flex justify-between items-center">
                    <span className="font-medium text-white">{item.enseigne}</span>
                    <span className="text-lime-400 font-bold">{item.prix}€</span>
                  </div>
                  <div className="text-sm text-gray-400 mt-1">{item.date}</div>
                </div>
              ))}
            </div>
          </div>
        )}
        
        {prixData.length === 0 && !loadingPrix && produit && territoire && (
          <div className="mt-4 p-4 bg-gray-800 rounded-lg text-center text-gray-400">
            Aucun résultat trouvé pour "{produit}" en "{territoire}"
          </div>
        )}
      </div>
    </div>
  );
}