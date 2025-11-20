import { useState } from "react";
import axios from "axios";

export default function TestIA() {
  const [prompt, setPrompt] = useState("");
  const [response, setResponse] = useState("");

  const handleSend = async () => {
    const res = await axios.post("http://127.0.0.1:3333/test-ia", { prompt });
    setResponse(res.data.response);
  };

  return (
    <div>
      <h1 className="text-xl text-sentinel-accent mb-3 font-semibold">Test IA Sentinel</h1>
      <textarea
        value={prompt}
        onChange={(e) => setPrompt(e.target.value)}
        placeholder="Saisis ton prompt IA..."
        className="w-full bg-black/30 border border-gray-700 p-2 rounded text-sm"
      />
      <button
        onClick={handleSend}
        className="mt-3 bg-sentinel-accent text-black px-4 py-1 rounded font-bold"
      >
        Envoyer
      </button>
      {response && <div className="mt-4 p-3 bg-black/40 border border-gray-700 rounded">{response}</div>}
    </div>
  );
}
