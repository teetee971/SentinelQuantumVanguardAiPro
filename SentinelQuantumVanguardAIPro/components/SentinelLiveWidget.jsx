import React, { useEffect, useState } from "react";

export default function SentinelLiveWidget() {
  const [data, setData] = useState({
    status: "checking...",
    uptime: "—",
    lastDeploy: "—",
    alerts: 0,
  });

  useEffect(() => {
    async function fetchStatus() {
      try {
        const res = await fetch("https://sentinelquantumvanguardaipro.pages.dev");
        const ok = res.status === 200;

        const wf = await fetch(
          "https://api.github.com/repos/teetee971/SentinelQuantumVanguardAIPro/actions/runs?per_page=5"
        );
        const wfData = await wf.json();
        const last = wfData.workflow_runs?.[0]?.updated_at ?? "—";

        const rel = await fetch(
          "https://api.github.com/repos/teetee971/SentinelQuantumVanguardAIPro/releases?per_page=20"
        );
        const relData = await rel.json();
        const alerts = relData.filter(r => r.name?.toLowerCase().includes("alert")).length;

        setData({
          status: ok ? "🟢 En ligne" : "🔴 Hors ligne",
          uptime: ok ? "100 %" : "0 %",
          lastDeploy: new Date(last).toLocaleString(),
          alerts,
        });
      } catch (e) {
        setData({ status: "⚠️ Erreur", uptime: "?", lastDeploy: "?", alerts: "?" });
      }
    }

    fetchStatus();
    const interval = setInterval(fetchStatus, 60000);
    return () => clearInterval(interval);
  }, []);

  return (
    <div className="bg-zinc-900 border border-zinc-800 rounded-2xl p-4 shadow-lg flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 text-zinc-100">
      <div>
        <h2 className="text-xl font-semibold tracking-tight">Sentinel Live Audit</h2>
        <p className="text-sm text-zinc-400">État du réseau & CI/CD</p>
      </div>
      <div className="flex flex-wrap gap-6 text-sm">
        <div><span className="font-medium">Statut :</span> {data.status}</div>
        <div><span className="font-medium">Uptime :</span> {data.uptime}</div>
        <div><span className="font-medium">Dernier déploiement :</span> {data.lastDeploy}</div>
        <div><span className="font-medium">Alertes :</span> {data.alerts}</div>
      </div>
    </div>
  );
}
