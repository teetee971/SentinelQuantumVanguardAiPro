import React, { useState } from "react";

export default function Antivirus() {
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);

  const handleScan = async (e) => {
    const file = e.target.files[0];
    if (!file) return;

    setLoading(true);
    setResult(null);

    try {
      const formData = new FormData();
      formData.append("file", file);

      const res = await fetch(
        "https://us-central1-sentinel-vanguard-ai-pro.cloudfunctions.net/scanThreats",
        { method: "POST", body: formData }
      );

      const data = await res.json();
      setResult(data);
    } catch (error) {
      console.error("Erreur scan IA Defender:", error);
      setResult({ status: "Erreur lors du scan IA", threats: [] });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex flex-col items-center justify-center bg-black text-white p-6">
      <h1 className="text-3xl font-semibold mb-6 text-emerald-400">
        Sentinel IA Defender
      </h1>

      <input
        type="file"
        onChange={handleScan}
        className="mb-4 p-2 border border-emerald-500 rounded-md bg-gray-900 text-sm"
      />

      {loading && <p>Analyse IA en cours...</p>}

      {result && (
        <div className="mt-6 p-4 border border-gray-700 rounded-lg bg-gray-800 w-full max-w-md text-left">
          <p><strong>Statut :</strong> {result.status}</p>
          <p><strong>Menaces détectées :</strong> {result.threats?.length || 0}</p>
          <p><strong>Fichier scanné :</strong> {result.scanned || "N/A"}</p>
          <p><strong>Horodatage :</strong> {result.timestamp}</p>
        </div>
      )}
    </div>
  );
}
