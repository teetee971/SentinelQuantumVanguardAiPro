export default function LogsPanel({ logs }) {
  return (
    <div className="bg-gray-800 rounded-xl p-4 overflow-y-auto h-80 border border-gray-700">
      <h2 className="text-lg font-semibold mb-2">🧠 Journal d’activité</h2>
      <ul className="text-sm text-gray-300 space-y-1 font-mono">
        {logs.map((line, i) => (
          <li key={i}>{line}</li>
        ))}
      </ul>
    </div>
  );
}