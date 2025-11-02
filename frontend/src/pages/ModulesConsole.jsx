import { useState, useEffect } from 'react';
import sentinelOrchestrator from '../modules/SentinelOrchestrator.js';

export default function ModulesConsole() {
  const [systemStatus, setSystemStatus] = useState(null);
  const [healthCheck, setHealthCheck] = useState(null);
  const [loading, setLoading] = useState(false);
  const [selectedModule, setSelectedModule] = useState(null);

  useEffect(() => {
    loadSystemStatus();
  }, []);

  const loadSystemStatus = () => {
    const status = sentinelOrchestrator.getSystemStatus();
    setSystemStatus(status);
  };

  const runHealthCheck = async () => {
    setLoading(true);
    const health = await sentinelOrchestrator.healthCheck();
    setHealthCheck(health);
    setLoading(false);
  };

  const runEmergencyRecovery = async () => {
    setLoading(true);
    const result = await sentinelOrchestrator.emergencyRecovery();
    alert(`Emergency Recovery Complete!\n${result.recovered.length} modules processed`);
    loadSystemStatus();
    setLoading(false);
  };

  const getModuleIcon = (category) => {
    const icons = {
      infrastructure: '⚙️',
      voice: '🎙️',
      monitoring: '📊',
      selfHealing: '🔧'
    };
    return icons[category] || '🔹';
  };

  const getCategoryName = (category) => {
    const names = {
      infrastructure: 'Infrastructure & CI/CD',
      voice: 'Voice & Communication',
      monitoring: 'Supervision & Monitoring',
      selfHealing: 'Self-Healing Modules'
    };
    return names[category] || category;
  };

  if (!systemStatus) {
    return (
      <div className="min-h-screen bg-zinc-950 text-zinc-100 p-6 flex items-center justify-center">
        <div className="text-center">
          <div className="text-4xl mb-4">⚡</div>
          <div className="text-xl">Initializing Sentinel Modules...</div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100 p-6">
      <div className="max-w-7xl mx-auto">
        {/* Header */}
        <div className="mb-8">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h1 className="text-4xl font-bold text-sentinel-blue mb-2">
                🤖 Sentinel AI Modules Console
              </h1>
              <p className="text-zinc-400">
                Central management for all Sentinel AI modules • Version {systemStatus.version}
              </p>
            </div>
            <div className="text-right">
              <div className="text-sm text-zinc-500">Uptime</div>
              <div className="text-2xl font-mono text-green-400">
                {Math.floor(systemStatus.uptime / 1000)}s
              </div>
            </div>
          </div>

          {/* Action Buttons */}
          <div className="flex gap-4">
            <button
              onClick={runHealthCheck}
              disabled={loading}
              className="bg-blue-600 hover:bg-blue-700 disabled:bg-zinc-700 px-6 py-2 rounded-lg transition font-semibold"
            >
              {loading ? '⏳ Running...' : '🔍 Run Health Check'}
            </button>
            <button
              onClick={runEmergencyRecovery}
              disabled={loading}
              className="bg-red-600 hover:bg-red-700 disabled:bg-zinc-700 px-6 py-2 rounded-lg transition font-semibold"
            >
              {loading ? '⏳ Recovering...' : '🚨 Emergency Recovery'}
            </button>
            <button
              onClick={loadSystemStatus}
              className="bg-zinc-700 hover:bg-zinc-600 px-6 py-2 rounded-lg transition font-semibold"
            >
              🔄 Refresh
            </button>
          </div>
        </div>

        {/* Health Check Results */}
        {healthCheck && (
          <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-6 mb-6">
            <h2 className="text-xl font-bold mb-4 flex items-center gap-2">
              {healthCheck.healthy ? '✅' : '⚠️'} System Health Check
            </h2>
            <div className="text-sm text-zinc-400 mb-4">
              Last check: {healthCheck.timestamp.toLocaleString()}
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
              {Object.entries(healthCheck.details).map(([category, modules]) => {
                const healthyCount = Object.values(modules).filter(m => m.healthy).length;
                const totalCount = Object.keys(modules).length;
                return (
                  <div key={category} className="bg-zinc-800 rounded p-4">
                    <div className="text-lg font-semibold mb-2">
                      {getModuleIcon(category)} {getCategoryName(category)}
                    </div>
                    <div className="text-2xl font-bold text-green-400">
                      {healthyCount}/{totalCount}
                    </div>
                    <div className="text-xs text-zinc-500 mt-1">modules healthy</div>
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {/* Modules Grid */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {Object.entries(systemStatus.modules).map(([category, modules]) => (
            <div key={category} className="bg-zinc-900 border border-zinc-800 rounded-lg p-6">
              <h2 className="text-2xl font-bold mb-4 flex items-center gap-2">
                {getModuleIcon(category)} {getCategoryName(category)}
              </h2>
              <div className="space-y-3">
                {Object.entries(modules).map(([moduleName, moduleStatus]) => (
                  <div
                    key={moduleName}
                    className="bg-zinc-800 rounded p-4 hover:bg-zinc-750 transition cursor-pointer"
                    onClick={() => setSelectedModule({ category, moduleName, ...moduleStatus })}
                  >
                    <div className="flex items-center justify-between mb-2">
                      <div className="font-semibold text-lg">
                        {moduleStatus.module || moduleName}
                      </div>
                      <div className={`px-3 py-1 rounded text-xs font-bold ${
                        moduleStatus.status === 'active' 
                          ? 'bg-green-500/20 text-green-400' 
                          : 'bg-red-500/20 text-red-400'
                      }`}>
                        {moduleStatus.status}
                      </div>
                    </div>
                    {moduleStatus.submodules && (
                      <div className="flex gap-2 flex-wrap">
                        {Object.entries(moduleStatus.submodules).map(([subName, isActive]) => (
                          <span
                            key={subName}
                            className={`text-xs px-2 py-1 rounded ${
                              isActive ? 'bg-green-500/10 text-green-500' : 'bg-red-500/10 text-red-500'
                            }`}
                          >
                            {isActive ? '✓' : '✗'} {subName}
                          </span>
                        ))}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>

        {/* Module Detail Modal */}
        {selectedModule && (
          <div 
            className="fixed inset-0 bg-black/80 flex items-center justify-center p-6 z-50"
            onClick={() => setSelectedModule(null)}
          >
            <div 
              className="bg-zinc-900 border border-zinc-700 rounded-lg p-6 max-w-2xl w-full max-h-[80vh] overflow-auto"
              onClick={(e) => e.stopPropagation()}
            >
              <div className="flex items-center justify-between mb-4">
                <h3 className="text-2xl font-bold">{selectedModule.module}</h3>
                <button
                  onClick={() => setSelectedModule(null)}
                  className="text-zinc-500 hover:text-zinc-300 text-2xl"
                >
                  ×
                </button>
              </div>
              <div className="space-y-4">
                <div>
                  <div className="text-sm text-zinc-500 mb-1">Status</div>
                  <div className={`text-lg font-bold ${
                    selectedModule.status === 'active' ? 'text-green-400' : 'text-red-400'
                  }`}>
                    {selectedModule.status}
                  </div>
                </div>
                {selectedModule.submodules && (
                  <div>
                    <div className="text-sm text-zinc-500 mb-2">Sub-modules</div>
                    <div className="bg-zinc-800 rounded p-4 space-y-2">
                      {Object.entries(selectedModule.submodules).map(([name, active]) => (
                        <div key={name} className="flex items-center justify-between">
                          <span className="text-zinc-300">{name}</span>
                          <span className={active ? 'text-green-400' : 'text-red-400'}>
                            {active ? '✓ Active' : '✗ Inactive'}
                          </span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
                <pre className="bg-zinc-800 rounded p-4 text-xs overflow-auto">
                  {JSON.stringify(selectedModule, null, 2)}
                </pre>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
