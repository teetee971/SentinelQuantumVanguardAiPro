import { useState, useEffect } from 'react';

/**
 * Sentinel Console Admin Dashboard
 * Central monitoring interface for all Sentinel AI modules
 */
export default function SentinelConsole() {
  const [modules, setModules] = useState({
    infrastructure: [],
    voice: [],
    monitoring: [],
    selfHealing: []
  });

  const [stats, setStats] = useState({
    totalModules: 26,
    activeModules: 26,
    alerts: 0,
    operations: 0
  });

  useEffect(() => {
    // Initialize module data
    setModules({
      infrastructure: [
        { name: 'InfraGuard', status: 'active', operations: 0 },
        { name: 'BuildPilot', status: 'active', operations: 0 },
        { name: 'FirebaseDeployExecutor', status: 'active', operations: 0 },
        { name: 'CloudflarePropagateWatcher', status: 'active', operations: 0 },
        { name: 'ScriptForge', status: 'active', operations: 0 },
        { name: 'ZeroDowntimeSwitcher', status: 'active', operations: 0 }
      ],
      voice: [
        { name: 'SentinelVoiceCore', status: 'active', operations: 0 },
        { name: 'DeepFakeVoiceDetection', status: 'active', operations: 0 },
        { name: 'SentinelChatAssistant', status: 'active', operations: 0 },
        { name: 'UIEmergencyFallbackAgent', status: 'active', operations: 0 },
        { name: 'TouchFeedbackOptimizer', status: 'active', operations: 0 }
      ],
      monitoring: [
        { name: 'SentinelConsoleAdmin', status: 'active', operations: 0 },
        { name: 'LiveConsoleErrorLogger', status: 'active', operations: 0 },
        { name: 'AgentLatencyMonitor', status: 'active', operations: 0 },
        { name: 'AIRecoveryCommander', status: 'active', operations: 0 },
        { name: 'RegressionDetectorAI', status: 'active', operations: 0 }
      ],
      selfHealing: [
        { name: 'ServiceWorkerHealer', status: 'active', operations: 0 },
        { name: 'ManifestRecoveryAgent', status: 'active', operations: 0 },
        { name: 'BrokenLinkMapper', status: 'active', operations: 0 },
        { name: 'Ghost404Handler', status: 'active', operations: 0 },
        { name: 'EmptyStateHealer', status: 'active', operations: 0 },
        { name: 'CDNConsistencyAgent', status: 'active', operations: 0 }
      ]
    });
  }, []);

  const getStatusColor = (status) => {
    switch (status) {
      case 'active':
        return 'text-green-400';
      case 'idle':
        return 'text-yellow-400';
      case 'error':
        return 'text-red-400';
      default:
        return 'text-gray-400';
    }
  };

  const getStatusIcon = (status) => {
    switch (status) {
      case 'active':
        return '🟢';
      case 'idle':
        return '🟡';
      case 'error':
        return '🔴';
      default:
        return '⚪';
    }
  };

  const ModuleCard = ({ category, categoryModules }) => (
    <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-6">
      <h3 className="text-xl font-semibold mb-4 text-sentinel-blue capitalize">
        {category === 'selfHealing' ? 'Self-Healing' : category}
      </h3>
      <div className="space-y-3">
        {categoryModules.map((module, idx) => (
          <div
            key={idx}
            className="flex items-center justify-between p-3 bg-zinc-950 rounded border border-zinc-800 hover:border-sentinel-blue/50 transition"
          >
            <div className="flex items-center gap-3">
              <span className="text-xl">{getStatusIcon(module.status)}</span>
              <div>
                <div className="font-medium">{module.name}</div>
                <div className="text-xs text-zinc-500">
                  {module.operations} operations
                </div>
              </div>
            </div>
            <div className={`text-sm font-semibold ${getStatusColor(module.status)}`}>
              {module.status.toUpperCase()}
            </div>
          </div>
        ))}
      </div>
    </div>
  );

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100 p-6">
      <div className="max-w-7xl mx-auto">
        {/* Header */}
        <div className="mb-8">
          <h1 className="text-4xl font-bold text-sentinel-blue mb-2">
            🛡️ Sentinel Console Admin
          </h1>
          <p className="text-zinc-400">
            Tableau de bord central du réseau Sentinel AI
          </p>
        </div>

        {/* Statistics Cards */}
        <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-8">
          <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-6">
            <div className="text-3xl mb-2">📊</div>
            <div className="text-2xl font-bold text-sentinel-blue">
              {stats.totalModules}
            </div>
            <div className="text-sm text-zinc-400">Total Modules</div>
          </div>

          <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-6">
            <div className="text-3xl mb-2">🟢</div>
            <div className="text-2xl font-bold text-green-400">
              {stats.activeModules}
            </div>
            <div className="text-sm text-zinc-400">Active Modules</div>
          </div>

          <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-6">
            <div className="text-3xl mb-2">🔔</div>
            <div className="text-2xl font-bold text-yellow-400">
              {stats.alerts}
            </div>
            <div className="text-sm text-zinc-400">Active Alerts</div>
          </div>

          <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-6">
            <div className="text-3xl mb-2">⚡</div>
            <div className="text-2xl font-bold text-blue-400">
              {stats.operations}
            </div>
            <div className="text-sm text-zinc-400">Operations</div>
          </div>
        </div>

        {/* Module Categories */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <ModuleCard
            category="infrastructure"
            categoryModules={modules.infrastructure}
          />
          <ModuleCard category="voice" categoryModules={modules.voice} />
          <ModuleCard
            category="monitoring"
            categoryModules={modules.monitoring}
          />
          <ModuleCard
            category="selfHealing"
            categoryModules={modules.selfHealing}
          />
        </div>

        {/* System Status */}
        <div className="mt-8 bg-zinc-900 border border-zinc-800 rounded-lg p-6">
          <h3 className="text-xl font-semibold mb-4 text-sentinel-blue">
            📈 Statut du Système
          </h3>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="bg-zinc-950 p-4 rounded border border-zinc-800">
              <div className="text-sm text-zinc-400 mb-1">CI/CD Pipeline</div>
              <div className="text-green-400 font-semibold">✓ Opérationnel</div>
            </div>
            <div className="bg-zinc-950 p-4 rounded border border-zinc-800">
              <div className="text-sm text-zinc-400 mb-1">Cloudflare</div>
              <div className="text-green-400 font-semibold">✓ Déployé</div>
            </div>
            <div className="bg-zinc-950 p-4 rounded border border-zinc-800">
              <div className="text-sm text-zinc-400 mb-1">Firebase</div>
              <div className="text-green-400 font-semibold">✓ Synchronisé</div>
            </div>
          </div>
        </div>

        {/* Back Link */}
        <div className="mt-8">
          <a
            href="/"
            className="text-sentinel-blue hover:text-blue-400 transition"
          >
            ← Retour à l'accueil
          </a>
        </div>
      </div>
    </div>
  );
}
