import React from "react"

export default function AgentHealthCard({ name, status }) {
  const colors = {
    online: "border-green-500 shadow-[0_0_15px_rgba(34,197,94,0.4)] animate-pulse-slow",
    offline: "border-red-500 shadow-[0_0_15px_rgba(239,68,68,0.3)] opacity-60",
    recovering: "border-yellow-400 shadow-[0_0_15px_rgba(250,204,21,0.4)] animate-pulse",
  }

  return (
    <div
      className={`p-5 bg-gradient-to-b from-neutral-900 to-neutral-950 rounded-xl border ${colors[status]} transition-all hover:scale-105 hover:shadow-[0_0_20px_rgba(34,211,238,0.5)]`}
    >
      <p className="text-lg font-semibold text-cyan-300 tracking-wide">
        {name}
      </p>
      <p
        className={`text-sm mt-1 capitalize ${
          status === "online"
            ? "text-green-400"
            : status === "recovering"
            ? "text-yellow-300"
            : "text-red-400"
        }`}
      >
        {status}
      </p>
    </div>
  )
}
