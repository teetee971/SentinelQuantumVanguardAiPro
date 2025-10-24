import { useEffect, useState } from "react";
import axios from "axios";

export default function Journal() {
  const [logs, setLogs] = useState([]);

  useEffect(() => {
    axios.get("http://127.0.0.1:3333/journal").then(res => setLogs(res.data.entries || []));
  }, []);

  return (
    <div>
      <h2 className="text-xl text-sentinel-accent mb-3 font-semibold">Journal IA Sentinel</h2>
      {logs.length === 0 ? (
        <p className="text-gray-400">Aucune activité pour le moment.</p>
      ) : (
        <ul className="space-y-2">
          {logs.map((log, i) => (
            <li key={i} className="p-2 bg-black/30 rounded border border-gray-700">{log}</li>
          ))}
        </ul>
      )}
    </div>
  );
}
