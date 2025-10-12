import React from "react";
import ReactDOM from "react-dom/client";

const App = () => (
  <div style={{
    fontFamily: "Inter, Roboto, sans-serif",
    background: "#0b0d10",
    color: "#e0e0e0",
    textAlign: "center",
    paddingTop: "10%"
  }}>
    <h1>🧠 Sentinel Fusion</h1>
    <p>Console IA en ligne - déploiement Cloudflare réussi ✅</p>
    <a href="./audit-report.html" style={{color:"#4fa3ff", textDecoration:"none"}}>
      → Ouvrir le rapport d’audit
    </a>
  </div>
);

ReactDOM.createRoot(document.getElementById("root")).render(<App />);
