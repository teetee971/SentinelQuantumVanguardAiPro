export default function AgentCard({ agent }) {
  const color =
    agent.status === "active"
      ? "bg-green-600"
      : agent.status === "idle"
      ? "bg-yellow-600"
      : "bg-red-600";

  return (
    <div className="rounded-xl p-4 bg-gray-800 shadow-lg border border-gray-700 transition hover:scale-105 hover:border-gray-500">
      <h2 className="text-lg font-semibold mb-2">{agent.name}</h2>
      <p className="text-sm text-gray-400 mb-1">Latence : {agent.latency} ms</p>
      <div className={`px-2 py-1 rounded-md inline-block text-sm ${color}`}>
        {agent.status.toUpperCase()}
      </div>
    </div>
  );
}