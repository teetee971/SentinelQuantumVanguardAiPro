import { useState } from "react";
import axios from "axios";

export default function PegasusScan() {
  const [deviceId, setDeviceId] = useState("");
  const [result, setResult] = useState(null);

  const handleScan = async () => {
    const res = await axios.post("http://127.0.0.1:3333/pegasus-scan", { deviceId });
    setResult(res.data.result);
  };

  return (
    <div>
      <h2 className="text-xl text-sentinel-accent mb-3 font-semibold">Pegasus Scan IA</h2>
      <input
        value={deviceId}
        onChange={(e) => setDeviceId(e.target.value)}
        placeholder="ID appareil"
        className="bg-black/40 border border-gray-600 p-2 rounded text-sm"
      />
      <button
        onClick={handleScan}
        className="ml-3 bg-sentinel-accent text-black px-3 py-1 rounded font-bold"
      >
        Scanner
      </button>
      {result && <p className="mt-4 text-gray-300">Résultat : {result}</p>}
    </div>
  );
}
