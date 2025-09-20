import React, { useState } from "react";
import { fetchPrixDOM } from "./api";
import "./SentinelAI.css";

export default function SentinelAI() {
  const [prompt, setPrompt] = useState("");
  const [response, setResponse] = useState("");
  const [loading, setLoading] = useState(false);
  
  // Price query states
  const [produit, setProduit] = useState("");
  const [territoire, setTerritoire] = useState("");
  const [priceData, setPriceData] = useState([]);
  const [priceLoading, setPriceLoading] = useState(false);

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

  const queryPrices = async () => {
    if (!produit.trim() || !territoire.trim()) return;
    setPriceLoading(true);
    setPriceData([]);
    try {
      const data = await fetchPrixDOM(produit, territoire);
      setPriceData(data);
    } catch (err) {
      console.error("Erreur lors de la requête des prix:", err);
      setPriceData([]);
    } finally {
      setPriceLoading(false);
    }
  };

  return (
    <div className="app-container">
      <h1 className="main-title">Sentinel Quantum Vanguard AI Pro</h1>
      
      {/* AI Chat Section */}
      <div className="section">
        <h2 className="section-title">Assistant IA</h2>
        <textarea
          className="textarea"
          rows={4}
          value={prompt}
          placeholder="Pose ta question ici..."
          onChange={(e) => setPrompt(e.target.value)}
        ></textarea>
        <button
          onClick={sendPrompt}
          disabled={loading}
          className="button button-ai"
          style={{ marginTop: '16px' }}
        >
          {loading ? "Chargement..." : "Envoyer à l'IA"}
        </button>
        <div className="results-container">
          {response ? <pre className="response-text">{response}</pre> : <span className="placeholder">La réponse s'affichera ici.</span>}
        </div>
      </div>

      {/* Price Query Section */}
      <div className="section">
        <h2 className="section-title">Recherche de Prix (OPMR)</h2>
        <div className="grid grid-md-2">
          <input
            type="text"
            className="input"
            value={produit}
            placeholder="Produit (ex: essence, gasoil)"
            onChange={(e) => setProduit(e.target.value)}
          />
          <input
            type="text"
            className="input"
            value={territoire}
            placeholder="Territoire (ex: Martinique, Guadeloupe)"
            onChange={(e) => setTerritoire(e.target.value)}
          />
        </div>
        <button
          onClick={queryPrices}
          disabled={priceLoading || !produit.trim() || !territoire.trim()}
          className="button button-price"
        >
          {priceLoading ? "Recherche..." : "Rechercher les Prix"}
        </button>
        
        {/* Price Results */}
        <div className="results-container">
          {priceData.length > 0 ? (
            <div>
              <h3 className="results-title">Résultats ({priceData.length})</h3>
              <div>
                {priceData.map((item, index) => (
                  <div key={index} className="price-item">
                    <div className="price-header">
                      <span className="price-store">{item.enseigne}</span>
                      <span className="price-value">{item.prix}€</span>
                    </div>
                    <div className="price-date">{item.date}</div>
                  </div>
                ))}
              </div>
            </div>
          ) : (
            <span className="placeholder">Les résultats de prix s'afficheront ici.</span>
          )}
        </div>
      </div>
    </div>
  );
}