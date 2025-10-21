{/* === JOURNAL IA EN DIRECT === */}
<motion.div
  className="bg-[#0f172a]/80 border border-blue-800/40 p-6 rounded-2xl shadow-lg backdrop-blur-xl"
  initial={{ opacity: 0 }}
  animate={{ opacity: 1 }}
  transition={{ duration: 1.2 }}
>
  <h2 className="text-2xl font-semibold text-blue-400 mb-4">
    Journal IA en direct
  </h2>
  <div className="max-h-64 overflow-y-auto space-y-2 text-sm font-mono text-gray-300 bg-[#020617]/60 p-4 rounded-xl border border-blue-900/40">
    {[
      "[22:30:12] QuantumFailoverAI : Surveillance du réseau terminée ✅",
      "[22:31:04] CognitiveTraceAgent : Détection d’une anomalie comportementale ⚠️",
      "[22:31:22] AutoRollbackCommander : Isolation du module instable effectuée 🛡️",
      "[22:32:10] HeuristicPredictorAI : Risque neutralisé avec succès 🔍",
      "[22:33:18] GlobalFailoverWatcher : Synchronisation complète du réseau 🌐",
      "[22:34:01] SentinelHealer : Module réparé automatiquement 🔧",
      "[22:35:15] PerformanceAutoTuner : Optimisation CPU et mémoire terminée ⚡",
      "[22:36:41] CognitiveSwitchoverAI : Reconfiguration dynamique réussie 🔄",
    ].map((log, i) => (
      <motion.div
        key={i}
        className="border-b border-blue-900/30 pb-1"
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ delay: i * 0.15 }}
      >
        {log}
      </motion.div>
    ))}
  </div>
</motion.div>
