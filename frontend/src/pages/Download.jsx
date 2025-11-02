import { useState, useEffect } from "react";

export default function Download() {
  const [qrCode, setQrCode] = useState("");
  const [version, setVersion] = useState("1.0.0");
  const [downloadStats, setDownloadStats] = useState({
    android: 15420,
    windows: 8934,
    pwa: 6721
  });

  useEffect(() => {
    // Générer un QR Code via API
    const url = window.location.origin;
    setQrCode(`https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=${encodeURIComponent(url)}`);
  }, []);

  const downloads = [
    {
      platform: "Android",
      icon: "📱",
      version: version,
      size: "45.2 MB",
      color: "bg-green-700",
      hoverColor: "hover:bg-green-600",
      downloads: downloadStats.android,
      verified: true,
      file: "sentinel-quantum-vanguard.apk"
    },
    {
      platform: "Windows",
      icon: "💻",
      version: version,
      size: "128.5 MB",
      color: "bg-blue-700",
      hoverColor: "hover:bg-blue-600",
      downloads: downloadStats.windows,
      verified: true,
      file: "sentinel-quantum-vanguard-setup.exe"
    },
    {
      platform: "PWA",
      icon: "🌐",
      version: version,
      size: "Web App",
      color: "bg-purple-700",
      hoverColor: "hover:bg-purple-600",
      downloads: downloadStats.pwa,
      verified: true,
      file: "pwa"
    }
  ];

  const handleDownload = (platform, file) => {
    alert(`Téléchargement de ${platform} commencé!\n\nNote: Ceci est une version de démonstration. Le fichier "${file}" serait normalement téléchargé ici.`);
    
    // Incrémenter le compteur de téléchargements
    setDownloadStats(prev => ({
      ...prev,
      [platform.toLowerCase()]: prev[platform.toLowerCase()] + 1
    }));
  };

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100 p-6">
      <div className="max-w-6xl mx-auto">
        <div className="text-center mb-12">
          <h1 className="text-4xl font-bold text-sentinel-blue mb-2">
            Téléchargements
          </h1>
          <p className="text-zinc-400">
            Sentinel Quantum Vanguard AI Pro - Version {version}
          </p>
        </div>

        {/* QR Code Section */}
        <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-8 mb-8">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-8 items-center">
            <div>
              <h2 className="text-2xl font-bold mb-4">Scan & Install</h2>
              <p className="text-zinc-400 mb-6">
                Scannez ce QR code avec votre smartphone pour télécharger directement l'application Sentinel Quantum Vanguard AI Pro.
              </p>
              <div className="space-y-3">
                <div className="flex items-center gap-3 text-sm">
                  <svg className="w-5 h-5 text-green-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                  </svg>
                  <span>Téléchargement direct sécurisé</span>
                </div>
                <div className="flex items-center gap-3 text-sm">
                  <svg className="w-5 h-5 text-green-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                  </svg>
                  <span>QR code unique par version</span>
                </div>
                <div className="flex items-center gap-3 text-sm">
                  <svg className="w-5 h-5 text-green-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                  </svg>
                  <span>Certification "Sentinel Verified"</span>
                </div>
              </div>
            </div>
            <div className="flex justify-center">
              <div className="bg-white p-4 rounded-lg">
                <img
                  src={qrCode}
                  alt="QR Code pour téléchargement"
                  className="w-48 h-48"
                />
                <div className="text-center mt-2">
                  <span className="text-xs text-zinc-800 font-medium">v{version}</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Download Cards */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
          {downloads.map((download) => (
            <div
              key={download.platform}
              className="bg-zinc-900 border border-zinc-800 rounded-lg p-6 hover:border-sentinel-blue transition"
            >
              <div className="text-center mb-4">
                <div className="text-5xl mb-3">{download.icon}</div>
                <h3 className="text-xl font-bold mb-1">{download.platform}</h3>
                <div className="flex items-center justify-center gap-2 mb-2">
                  <span className="text-sm text-zinc-400">Version {download.version}</span>
                  {download.verified && (
                    <svg className="w-4 h-4 text-green-400" fill="currentColor" viewBox="0 0 20 20">
                      <path fillRule="evenodd" d="M6.267 3.455a3.066 3.066 0 001.745-.723 3.066 3.066 0 013.976 0 3.066 3.066 0 001.745.723 3.066 3.066 0 012.812 2.812c.051.643.304 1.254.723 1.745a3.066 3.066 0 010 3.976 3.066 3.066 0 00-.723 1.745 3.066 3.066 0 01-2.812 2.812 3.066 3.066 0 00-1.745.723 3.066 3.066 0 01-3.976 0 3.066 3.066 0 00-1.745-.723 3.066 3.066 0 01-2.812-2.812 3.066 3.066 0 00-.723-1.745 3.066 3.066 0 010-3.976 3.066 3.066 0 00.723-1.745 3.066 3.066 0 012.812-2.812zm7.44 5.252a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
                    </svg>
                  )}
                </div>
                <p className="text-sm text-zinc-500">{download.size}</p>
              </div>

              <div className="mb-4">
                <div className="flex items-center justify-between text-sm mb-2">
                  <span className="text-zinc-400">Téléchargements</span>
                  <span className="text-zinc-300 font-medium">
                    {download.downloads.toLocaleString()}
                  </span>
                </div>
                <div className="bg-zinc-800 rounded-full h-2 overflow-hidden">
                  <div
                    className={`h-full ${download.color}`}
                    style={{ width: `${(download.downloads / 20000) * 100}%` }}
                  ></div>
                </div>
              </div>

              <button
                onClick={() => handleDownload(download.platform, download.file)}
                className={`w-full px-4 py-3 ${download.color} ${download.hoverColor} rounded-lg transition font-semibold`}
              >
                Télécharger {download.platform}
              </button>

              <div className="mt-4 text-xs text-zinc-500 text-center">
                {download.verified && (
                  <span className="text-green-400">✓ Sentinel Verified</span>
                )}
              </div>
            </div>
          ))}
        </div>

        {/* Features */}
        <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-6 mb-8">
          <h2 className="text-2xl font-bold mb-6 text-center">Fonctionnalités incluses</h2>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            <div className="flex items-start gap-3">
              <div className="w-10 h-10 bg-sentinel-blue/20 rounded-lg flex items-center justify-center flex-shrink-0">
                <svg className="w-6 h-6 text-sentinel-blue" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
                </svg>
              </div>
              <div>
                <h3 className="font-semibold mb-1">Protection mobile complète</h3>
                <p className="text-sm text-zinc-400">
                  Scan de sécurité embarqué et protection en temps réel
                </p>
              </div>
            </div>

            <div className="flex items-start gap-3">
              <div className="w-10 h-10 bg-green-700/20 rounded-lg flex items-center justify-center flex-shrink-0">
                <svg className="w-6 h-6 text-green-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
                </svg>
              </div>
              <div>
                <h3 className="font-semibold mb-1">Alertes en temps réel</h3>
                <p className="text-sm text-zinc-400">
                  Notifications IA instantanées des menaces détectées
                </p>
              </div>
            </div>

            <div className="flex items-start gap-3">
              <div className="w-10 h-10 bg-purple-700/20 rounded-lg flex items-center justify-center flex-shrink-0">
                <svg className="w-6 h-6 text-purple-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
                </svg>
              </div>
              <div>
                <h3 className="font-semibold mb-1">Dashboard global</h3>
                <p className="text-sm text-zinc-400">
                  Accès au tableau de bord centralisé Sentinel
                </p>
              </div>
            </div>

            <div className="flex items-start gap-3">
              <div className="w-10 h-10 bg-yellow-700/20 rounded-lg flex items-center justify-center flex-shrink-0">
                <svg className="w-6 h-6 text-yellow-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
                </svg>
              </div>
              <div>
                <h3 className="font-semibold mb-1">Mode hors-ligne</h3>
                <p className="text-sm text-zinc-400">
                  Sécurité portable et universelle sans connexion
                </p>
              </div>
            </div>

            <div className="flex items-start gap-3">
              <div className="w-10 h-10 bg-red-700/20 rounded-lg flex items-center justify-center flex-shrink-0">
                <svg className="w-6 h-6 text-red-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                </svg>
              </div>
              <div>
                <h3 className="font-semibold mb-1">Scan Pegasus</h3>
                <p className="text-sm text-zinc-400">
                  Détection de spyware et malware intégrée
                </p>
              </div>
            </div>

            <div className="flex items-start gap-3">
              <div className="w-10 h-10 bg-blue-700/20 rounded-lg flex items-center justify-center flex-shrink-0">
                <svg className="w-6 h-6 text-blue-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3.055 11H5a2 2 0 012 2v1a2 2 0 002 2 2 2 0 012 2v2.945M8 3.935V5.5A2.5 2.5 0 0010.5 8h.5a2 2 0 012 2 2 2 0 104 0 2 2 0 012-2h1.064M15 20.488V18a2 2 0 012-2h3.064M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
              </div>
              <div>
                <h3 className="font-semibold mb-1">ThreatMap</h3>
                <p className="text-sm text-zinc-400">
                  Visualisation mondiale des menaces cyber
                </p>
              </div>
            </div>
          </div>
        </div>

        {/* System Requirements */}
        <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-6 mb-8">
          <h2 className="text-2xl font-bold mb-6">Configuration requise</h2>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            <div>
              <h3 className="font-semibold mb-3 text-green-400">📱 Android</h3>
              <ul className="space-y-2 text-sm text-zinc-400">
                <li>• Android 8.0 ou supérieur</li>
                <li>• 2 GB RAM minimum</li>
                <li>• 100 MB d'espace libre</li>
                <li>• Connexion internet</li>
              </ul>
            </div>
            <div>
              <h3 className="font-semibold mb-3 text-blue-400">💻 Windows</h3>
              <ul className="space-y-2 text-sm text-zinc-400">
                <li>• Windows 10/11 64-bit</li>
                <li>• 4 GB RAM minimum</li>
                <li>• 500 MB d'espace libre</li>
                <li>• Connexion internet</li>
              </ul>
            </div>
            <div>
              <h3 className="font-semibold mb-3 text-purple-400">🌐 PWA</h3>
              <ul className="space-y-2 text-sm text-zinc-400">
                <li>• Navigateur moderne</li>
                <li>• Chrome, Firefox, Edge, Safari</li>
                <li>• JavaScript activé</li>
                <li>• Connexion internet</li>
              </ul>
            </div>
          </div>
        </div>

        {/* Navigation */}
        <div className="flex gap-4">
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
