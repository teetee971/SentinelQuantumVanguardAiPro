import { useState, useEffect } from "react";
import VpnMap from "../../components/VpnMap";
import VpnLogs from "../../components/VpnLogs";

export default function VpnConsole() {
  const [status, setStatus] = useState("Déconnecté");
  const [uptime, setUptime] = useState(0);

  useEffect(() => {
    // Simulation simple de ping live
    const interval = setInterval(() => {
      setStatus((s) => (s === "Connecté" ? "Déconnecté" : "Connecté"));
      setUptime((u) => u + 1);
    }, 4000);
    return () => clearInterval(interval);
  }, []);

  return (
    <div className="p-8 text-center text-zinc-100">
      <h1 className="text-3xl font-bold mb-6 text-sentinel-blue">
        Tableau de Bord VPN Sentinel
      </h1>

      <div className="mb-6">
        <span
          className={`inline-block px-4 py-2 rounded-full text-sm font-semibold ${
            status === "Connecté"
              ? "bg-green-500/20 text-green-400 border border-green-600"
              : "bg-red-500/20 text-red-400 border border-red-600"
          }`}
        >
          {status}
        </span>
      </div>

      <p className="text-sm text-zinc-400 mb-6">
        Uptime : {uptime}s
      </p>

      <div className="mt-8 mb-10">
        <iframe
          src="https://quickchart.io/chart?c={type:'line',data:{labels:['00h','01h','02h','03h','04h','05h','06h'],datasets:[{label:'Connexions actives',data:[3,5,4,7,8,6,9],borderColor:'rgba(54,162,235,1)',fill:false}]},options:{scales:{y:{beginAtZero:true}}}}"
          width="320"
          height="200"
          className="mx-auto border border-zinc-700 rounded-lg shadow-md"
          title="Graphique connexions VPN"
        ></iframe>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mt-10">
        <div>
          <VpnMap />
        </div>
        <div>
          <VpnLogs />
        </div>
      </div>
    </div>
  );
}
