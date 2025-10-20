import React, { useEffect, useState } from "react";
import { Card, CardContent } from "@/components/ui/card";

export default function NetworkConsole() {
  const [logs, setLogs] = useState([]);
  const [socket, setSocket] = useState(null);
  const [connected, setConnected] = useState(false);
  const [message, setMessage] = useState("");

  useEffect(() => {
    const ws = new WebSocket("wss://echo.websocket.events"); // serveur test, à remplacer plus tard
    setSocket(ws);

    ws.onopen = () => {
      setConnected(true);
      setLogs(l => [...l, "🟢 Connexion WebSocket établie"]);
    };

    ws.onmessage = (e) => {
      if (e.data) setLogs(l => [...l, `📨 ${e.data}`]);
    };

    ws.onclose = () => {
      setConnected(false);
      setLogs(l => [...l, "🔴 Déconnecté du réseau"]);
    };

    return () => ws.close();
  }, []);

  const sendMessage = () => {
    if (socket && connected && message.trim()) {
      socket.send(message);
      setLogs(l => [...l, `➡️ Envoi : ${message}`]);
      setMessage("");
    }
  };

  return (
    <div className="min-h-screen bg-gradient-to-b from-gray-950 to-gray-900 text-gray-100 p-6 flex flex-col items-center">
      <h1 className="text-3xl font-bold text-cyan-400 mb-2">🌐 Sentinel AI Network Console</h1>
      <p className="text-sm text-gray-400 mb-4">
        Flux temps réel – communication entre agents (Wave 23)
      </p>

      <Card className="w-full max-w-4xl bg-gray-900/70 border border-cyan-500/20 shadow-xl mb-4">
        <CardContent className="p-4 h-[400px] overflow-y-auto font-mono text-sm bg-black/20 rounded-lg">
          {logs.map((log, i) => (
            <div key={i} className="mb-1">{log}</div>
          ))}
        </CardContent>
      </Card>

      <div className="flex gap-2 w-full max-w-4xl">
        <input
          type="text"
          value={message}
          onChange={e => setMessage(e.target.value)}
          placeholder="Envoyer une commande ou un message réseau..."
          className="flex-1 px-3 py-2 rounded-lg bg-gray-800 border border-gray-700 focus:border-cyan-500 outline-none"
        />
        <button
          onClick={sendMessage}
          disabled={!connected}
          className="px-4 py-2 bg-cyan-600/40 hover:bg-cyan-600/60 rounded-lg transition disabled:opacity-50"
        >
          Envoyer
        </button>
      </div>

      <div className="mt-4 text-sm text-gray-400">
        Statut réseau : {connected ? "🟢 Connecté" : "🔴 Hors ligne"}
      </div>

      <footer className="mt-6 text-gray-500 text-xs">
        Sentinel Quantum Vanguard AI Pro — Wave 23 Network Console
      </footer>
    </div>
  );
}
