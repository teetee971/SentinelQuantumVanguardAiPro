import React, { useState } from "react";

// Component for price comparison badges
function PriceBadge({ produit }) {
  const diff = produit.prix_dom - produit.prix_metropole;
  let badgeColor = "#6B7280"; // gray-400 equivalent
  let sign = "";
  
  if (diff > 0) { 
    badgeColor = "#EF4444"; // red-500 equivalent
    sign = "+"; 
  }
  if (diff < 0) { 
    badgeColor = "#10B981"; // green-500 equivalent
    sign = ""; // The sign will be handled by the negative number
  }
  if (diff === 0) { 
    badgeColor = "#3B82F6"; // blue-500 equivalent
    sign = ""; 
  }

  return (
    <span style={{
      padding: "4px 8px",
      borderRadius: "4px",
      backgroundColor: badgeColor,
      color: "white",
      fontSize: "14px",
      fontWeight: "500"
    }}>
      {sign}{Math.abs(diff).toFixed(2)} €
    </span>
  );
}

export default function SentinelAI() {
  const [prompt, setPrompt] = useState("");
  const [response, setResponse] = useState("");
  const [loading, setLoading] = useState(false);

  // Sample product data for price comparison demo
  const sampleProducts = [
    {
      id: 1,
      name: "Produit A",
      prix_dom: 25.50,
      prix_metropole: 20.00
    },
    {
      id: 2,
      name: "Produit B", 
      prix_dom: 15.00,
      prix_metropole: 18.50
    },
    {
      id: 3,
      name: "Produit C",
      prix_dom: 30.00,
      prix_metropole: 30.00
    }
  ];

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

  const appStyle = {
    minHeight: "100vh",
    backgroundColor: "#000000",
    color: "#ffffff",
    padding: "24px",
    display: "flex",
    flexDirection: "column",
    alignItems: "center",
    justifyContent: "center",
    fontFamily: "system-ui, -apple-system, sans-serif"
  };

  const titleStyle = {
    fontSize: "2rem",
    fontWeight: "bold",
    marginBottom: "24px",
    color: "#84CC16",
    textAlign: "center"
  };

  const textareaStyle = {
    width: "100%",
    maxWidth: "600px",
    padding: "16px",
    borderRadius: "12px",
    backgroundColor: "#1F2937",
    border: "1px solid #374151",
    color: "#ffffff",
    fontSize: "16px",
    resize: "vertical",
    outline: "none"
  };

  const buttonStyle = {
    marginTop: "16px",
    padding: "8px 24px",
    borderRadius: "24px",
    backgroundColor: loading ? "#65A30D" : "#84CC16",
    color: "#000000",
    fontWeight: "600",
    border: "none",
    cursor: loading ? "not-allowed" : "pointer",
    opacity: loading ? 0.5 : 1,
    fontSize: "16px"
  };

  const responseStyle = {
    marginTop: "24px",
    width: "100%",
    maxWidth: "600px",
    padding: "16px",
    backgroundColor: "#1F2937",
    borderRadius: "8px",
    minHeight: "100px"
  };

  const sectionStyle = {
    marginTop: "40px",
    width: "100%",
    maxWidth: "600px",
    padding: "20px",
    backgroundColor: "#1F2937",
    borderRadius: "8px"
  };

  const productItemStyle = {
    display: "flex",
    justifyContent: "space-between",
    alignItems: "center",
    padding: "12px",
    marginBottom: "8px",
    backgroundColor: "#374151",
    borderRadius: "6px"
  };

  return (
    <div style={appStyle}>
      <h1 style={titleStyle}>Sentinel Quantum Vanguard AI Pro</h1>
      
      <textarea
        style={textareaStyle}
        rows={4}
        value={prompt}
        placeholder="Pose ta question ici..."
        onChange={(e) => setPrompt(e.target.value)}
      />
      
      <button
        onClick={sendPrompt}
        disabled={loading}
        style={buttonStyle}
      >
        {loading ? "Chargement..." : "Envoyer à l'IA"}
      </button>
      
      <div style={responseStyle}>
        {response ? (
          <pre style={{ whiteSpace: "pre-wrap", margin: 0 }}>{response}</pre>
        ) : (
          <span style={{ color: "#9CA3AF" }}>La réponse s'affichera ici.</span>
        )}
      </div>

      {/* Price Comparison Demo Section */}
      <div style={sectionStyle}>
        <h2 style={{ color: "#84CC16", marginBottom: "16px", fontSize: "1.5rem" }}>
          Surveillance des Prix - Comparaison DOM/Métropole
        </h2>
        <p style={{ color: "#D1D5DB", marginBottom: "20px", fontSize: "14px" }}>
          Détection automatique des écarts de prix entre DOM et Métropole
        </p>
        
        {sampleProducts.map(produit => (
          <div key={produit.id} style={productItemStyle}>
            <div>
              <div style={{ fontWeight: "600", marginBottom: "4px" }}>{produit.name}</div>
              <div style={{ fontSize: "14px", color: "#9CA3AF" }}>
                DOM: {produit.prix_dom.toFixed(2)}€ | Métropole: {produit.prix_metropole.toFixed(2)}€
              </div>
            </div>
            <PriceBadge produit={produit} />
          </div>
        ))}
      </div>
    </div>
  );
}