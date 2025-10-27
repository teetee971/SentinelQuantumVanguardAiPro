import { useState, useEffect } from "react";

export default function VpnConsole() {
  const [status, setStatus] = useState("Déconnecté");
  const [uptime, setUptime] = useState(0);

  useEffect(() => {
    // simulation simple de ping live
    const interval = setInterval(() => {
      setStatus((s) => (s === "Connecté" ? "Déconnecté" : "Connecté"));
      setUptime((u) => u + 1);
    }, 4000);
    return () => clearInterval(interval);
  }, []);

  return (
    <div className="p-8 text-center text-zinc-100">
      <h1 className="text-3xl font-bold mb-6 text-sentinel-blue">Sentinel VPN Console</h1>
      <div className={`inline-block px-4 py-2 rounded-full border ${
        status === "Connecté" ? "bg-green-500/10 border-green-400 text-green-400" : "bg-red-500/10 border-red-400 text-red-400"
      }`}>
        {status}
      </div>
      <p className="mt-4 text-sm text-zinc-400">Uptime : {uptime}s</p>
      <div className="mt-8">
        <iframe
          src="https://quickchart.io/chart?c={type:'line',data:{labels:['0','10','20','30'],datasets:[{label:'Ping (ms)',data:[20,40,25,60],borderColor:'#22C55E'}]}}"
          width="320"
          height="200"
          className="mx-auto border border-zinc-700 rounded-lg"
        />
      </div>
    </div>
  );
}
