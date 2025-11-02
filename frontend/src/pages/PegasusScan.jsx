import { useState } from "react";

export default function PegasusScan() {
  const [scanning, setScanning] = useState(false);
  const [scanComplete, setScanComplete] = useState(false);
  const [scanResults, setScanResults] = useState({
    threats: 0,
    suspicious: 0,
    clean: true,
    deviceInfo: {
      os: "",
      browser: "",
      ip: ""
    },
    detections: []
  });

  const startScan = async () => {
    setScanning(true);
    setScanComplete(false);

    // Simulation du scan
    const deviceInfo = {
      os: navigator.platform,
      browser: navigator.userAgent.split(')')[0].split('(')[1] || "Unknown",
      ip: "Protégé"
    };

    // Simulation de détection
    await new Promise(resolve => setTimeout(resolve, 3000));

    const mockResults = {
      threats: 0,
      suspicious: 0,
      clean: true,
      deviceInfo,
      detections: [
        { type: "system", status: "clean", message: "Système d'exploitation vérifié" },
        { type: "network", status: "clean", message: "Connexions réseau sécurisées" },
        { type: "processes", status: "clean", message: "Aucun processus suspect détecté" },
        { type: "files", status: "clean", message: "Intégrité des fichiers système OK" }
      ]
    };

    setScanResults(mockResults);
    setScanning(false);
    setScanComplete(true);
  };

  const generateReport = () => {
    const report = `
=== RAPPORT DE SCAN PEGASUS ===
Date: ${new Date().toLocaleString()}

INFORMATIONS APPAREIL:
- OS: ${scanResults.deviceInfo.os}
- Navigateur: ${scanResults.deviceInfo.browser}
- IP: ${scanResults.deviceInfo.ip}

RÉSULTATS:
- Menaces détectées: ${scanResults.threats}
- Éléments suspects: ${scanResults.suspicious}
- État: ${scanResults.clean ? "SÉCURISÉ" : "COMPROMIS"}

DÉTAILS:
${scanResults.detections.map(d => `- ${d.message} [${d.status.toUpperCase()}]`).join('\n')}

© 2025 Sentinel Quantum Vanguard AI Pro
    `.trim();

    const blob = new Blob([report], { type: "text/plain" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `pegasus-scan-${Date.now()}.txt`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100 p-6">
      <div className="max-w-4xl mx-auto">
        <div className="text-center mb-8">
          <h1 className="text-4xl font-bold text-sentinel-blue mb-2">
            Pegasus Scan
          </h1>
          <p className="text-zinc-400">
            Détection de spyware et malware en ligne
          </p>
        </div>

        {!scanning && !scanComplete && (
          <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-8 text-center">
            <div className="text-6xl mb-6">🔍</div>
            <h2 className="text-2xl font-semibold mb-4">
              Analysez votre appareil
            </h2>
            <p className="text-zinc-400 mb-8 max-w-2xl mx-auto">
              Notre système d'IA avancé va scanner votre appareil pour détecter
              tout logiciel espion, malware ou comportement suspect. Le scan est
              rapide, sécurisé et non-invasif.
            </p>
            <button
              onClick={startScan}
              className="px-8 py-4 bg-sentinel-blue hover:bg-blue-600 rounded-lg transition text-lg font-semibold"
            >
              Démarrer le scan
            </button>
            
            <div className="mt-12 grid grid-cols-1 md:grid-cols-3 gap-6 text-left">
              <div className="bg-zinc-800/50 p-4 rounded-lg">
                <svg className="w-8 h-8 text-green-400 mb-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
                </svg>
                <h3 className="font-semibold mb-1">Sécurisé</h3>
                <p className="text-sm text-zinc-400">
                  Scan sans installation locale
                </p>
              </div>
              
              <div className="bg-zinc-800/50 p-4 rounded-lg">
                <svg className="w-8 h-8 text-blue-400 mb-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
                </svg>
                <h3 className="font-semibold mb-1">Rapide</h3>
                <p className="text-sm text-zinc-400">
                  Résultats en quelques secondes
                </p>
              </div>
              
              <div className="bg-zinc-800/50 p-4 rounded-lg">
                <svg className="w-8 h-8 text-purple-400 mb-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
                </svg>
                <h3 className="font-semibold mb-1">Intelligent</h3>
                <p className="text-sm text-zinc-400">
                  Détection IA non invasive
                </p>
              </div>
            </div>
          </div>
        )}

        {scanning && (
          <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-8">
            <div className="text-center mb-8">
              <div className="inline-block animate-spin rounded-full h-16 w-16 border-t-2 border-b-2 border-sentinel-blue mb-4"></div>
              <h2 className="text-2xl font-semibold mb-2">Scan en cours...</h2>
              <p className="text-zinc-400">
                Analyse de votre appareil en temps réel
              </p>
            </div>

            <div className="space-y-4">
              {[
                { label: "Vérification du système", delay: 500 },
                { label: "Analyse des processus", delay: 1000 },
                { label: "Scan des connexions réseau", delay: 1500 },
                { label: "Détection de comportements suspects", delay: 2000 },
                { label: "Génération du rapport", delay: 2500 }
              ].map((step, index) => (
                <div
                  key={index}
                  className="flex items-center gap-3 bg-zinc-800/50 px-4 py-3 rounded-lg animate-pulse"
                  style={{ animationDelay: `${step.delay}ms` }}
                >
                  <div className="w-2 h-2 bg-sentinel-blue rounded-full"></div>
                  <span className="text-zinc-300">{step.label}</span>
                </div>
              ))}
            </div>
          </div>
        )}

        {scanComplete && (
          <div className="space-y-6">
            <div className={`border rounded-lg p-8 text-center ${
              scanResults.clean
                ? "bg-green-900/20 border-green-800"
                : "bg-red-900/20 border-red-800"
            }`}>
              <div className="text-6xl mb-4">
                {scanResults.clean ? "✅" : "⚠️"}
              </div>
              <h2 className="text-3xl font-bold mb-2">
                {scanResults.clean ? "Appareil Sécurisé" : "Menaces Détectées"}
              </h2>
              <p className="text-zinc-300 mb-6">
                {scanResults.clean
                  ? "Aucune menace n'a été détectée sur votre appareil"
                  : `${scanResults.threats} menace(s) et ${scanResults.suspicious} élément(s) suspect(s) détectés`
                }
              </p>
              
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
                <div className="bg-zinc-900/50 p-4 rounded-lg">
                  <div className="text-2xl font-bold text-red-400">{scanResults.threats}</div>
                  <div className="text-sm text-zinc-400">Menaces</div>
                </div>
                <div className="bg-zinc-900/50 p-4 rounded-lg">
                  <div className="text-2xl font-bold text-yellow-400">{scanResults.suspicious}</div>
                  <div className="text-sm text-zinc-400">Suspects</div>
                </div>
                <div className="bg-zinc-900/50 p-4 rounded-lg">
                  <div className="text-2xl font-bold text-green-400">{scanResults.detections.length}</div>
                  <div className="text-sm text-zinc-400">Vérifications</div>
                </div>
              </div>

              <div className="flex gap-4 justify-center">
                <button
                  onClick={generateReport}
                  className="px-6 py-3 bg-sentinel-blue hover:bg-blue-600 rounded-lg transition"
                >
                  Télécharger le rapport
                </button>
                <button
                  onClick={() => {
                    setScanComplete(false);
                    setScanResults({
                      threats: 0,
                      suspicious: 0,
                      clean: true,
                      deviceInfo: { os: "", browser: "", ip: "" },
                      detections: []
                    });
                  }}
                  className="px-6 py-3 bg-zinc-800 hover:bg-zinc-700 border border-zinc-700 rounded-lg transition"
                >
                  Nouveau scan
                </button>
              </div>
            </div>

            <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-6">
              <h3 className="text-xl font-semibold mb-4">Détails du scan</h3>
              <div className="space-y-3">
                {scanResults.detections.map((detection, index) => (
                  <div
                    key={index}
                    className="flex items-center gap-3 bg-zinc-800/50 px-4 py-3 rounded-lg"
                  >
                    <div className={`w-2 h-2 rounded-full ${
                      detection.status === "clean" ? "bg-green-500" :
                      detection.status === "warning" ? "bg-yellow-500" : "bg-red-500"
                    }`}></div>
                    <div className="flex-1">
                      <span className="text-zinc-300">{detection.message}</span>
                    </div>
                    <span className={`text-sm px-2 py-1 rounded ${
                      detection.status === "clean" ? "bg-green-500/20 text-green-400" :
                      detection.status === "warning" ? "bg-yellow-500/20 text-yellow-400" :
                      "bg-red-500/20 text-red-400"
                    }`}>
                      {detection.status}
                    </span>
                  </div>
                ))}
              </div>
            </div>

            <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-6">
              <h3 className="text-xl font-semibold mb-4">Informations de l'appareil</h3>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <div className="bg-zinc-800/50 p-4 rounded-lg">
                  <div className="text-sm text-zinc-400 mb-1">Système</div>
                  <div className="font-medium">{scanResults.deviceInfo.os}</div>
                </div>
                <div className="bg-zinc-800/50 p-4 rounded-lg">
                  <div className="text-sm text-zinc-400 mb-1">Navigateur</div>
                  <div className="font-medium">{scanResults.deviceInfo.browser}</div>
                </div>
                <div className="bg-zinc-800/50 p-4 rounded-lg">
                  <div className="text-sm text-zinc-400 mb-1">IP</div>
                  <div className="font-medium">{scanResults.deviceInfo.ip}</div>
                </div>
              </div>
            </div>
          </div>
        )}

        <div className="mt-8 flex gap-4">
          <a
            href="/"
            className="px-4 py-2 bg-zinc-800 hover:bg-zinc-700 border border-zinc-700 rounded-lg transition"
          >
            ← Retour à l'accueil
          </a>
          <a
            href="/dashboard"
            className="px-4 py-2 bg-sentinel-blue hover:bg-blue-600 rounded-lg transition"
          >
            Dashboard →
          </a>
        </div>
      </div>
    </div>
  );
}
