import { Link } from "react-router-dom";

export default function ModuleTemplate({
  icon,
  title,
  subtitle,
  status = "active",
  statusColor = "green",
  stats,
  subModules,
  recentActivity,
  keyFeatures,
  benefits
}) {
  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100">
      {/* Header */}
      <header className="border-b border-zinc-800 bg-zinc-900/50 backdrop-blur">
        <div className="container mx-auto px-6 py-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center space-x-4">
              <Link to="/dashboard" className="text-zinc-400 hover:text-zinc-100">
                <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
                </svg>
              </Link>
              <div className="text-3xl">{icon}</div>
              <div>
                <h1 className="text-2xl font-bold text-sentinel-blue">
                  {title}
                </h1>
                <p className="text-xs text-zinc-400">
                  {subtitle}
                </p>
              </div>
            </div>
            <div className="flex items-center space-x-2">
              <div className={`w-2 h-2 bg-${statusColor}-500 rounded-full animate-pulse`}></div>
              <span className={`text-sm text-${statusColor}-400`}>
                {status === "active" ? "Opérationnel" : status}
              </span>
            </div>
          </div>
        </div>
      </header>

      {/* Stats Overview */}
      {stats && (
        <div className="border-b border-zinc-800 bg-zinc-900/30">
          <div className="container mx-auto px-6 py-6">
            <div className={`grid grid-cols-2 md:grid-cols-3 lg:grid-cols-${Math.min(stats.length, 6)} gap-4`}>
              {stats.map((stat, idx) => (
                <div key={idx} className="bg-zinc-900 border border-zinc-800 rounded-lg p-4">
                  <div className={`text-2xl font-bold text-${stat.color || 'blue'}-400`}>{stat.value}</div>
                  <div className="text-xs text-zinc-400">{stat.label}</div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* Main Content */}
      <div className="container mx-auto px-6 py-8">
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-8">
          {/* Sub-Modules Status */}
          {subModules && subModules.length > 0 && (
            <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-6">
              <h2 className="text-xl font-semibold mb-4 flex items-center">
                <svg className="w-5 h-5 mr-2 text-sentinel-blue" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
                </svg>
                Sous-Modules
              </h2>
              <div className="space-y-3">
                {subModules.map((module, idx) => (
                  <div key={idx} className="bg-zinc-800/50 border border-zinc-700 rounded-lg p-4">
                    <div className="flex items-center justify-between mb-2">
                      <div className="flex items-center space-x-2">
                        <div className="w-2 h-2 bg-green-500 rounded-full animate-pulse"></div>
                        <span className="font-semibold">{module.name}</span>
                      </div>
                      <span className="text-xs text-green-400 uppercase">{module.status || "active"}</span>
                    </div>
                    {module.metrics && (
                      <div className="grid grid-cols-2 gap-2 text-xs text-zinc-400">
                        {Object.entries(module.metrics).map(([key, value]) => (
                          <div key={key}>
                            {key}: <span className="text-zinc-200">{value}</span>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Recent Activity */}
          {recentActivity && recentActivity.length > 0 && (
            <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-6">
              <h2 className="text-xl font-semibold mb-4 flex items-center">
                <svg className="w-5 h-5 mr-2 text-blue-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
                </svg>
                Activité Récente
              </h2>
              <div className="space-y-3">
                {recentActivity.map((activity, idx) => (
                  <div key={idx} className="bg-zinc-800/50 border border-zinc-700 rounded-lg p-4">
                    <div className="flex items-center justify-between mb-2">
                      <span className="font-semibold text-sm">{activity.title}</span>
                      <span className="text-xs text-zinc-500">{activity.time}</span>
                    </div>
                    <p className="text-xs text-zinc-400">{activity.description}</p>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* Key Features */}
        {keyFeatures && keyFeatures.length > 0 && (
          <div className="mb-8">
            <h2 className="text-2xl font-bold mb-4">Fonctions Clés</h2>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              {keyFeatures.map((feature, idx) => (
                <div key={idx} className="bg-zinc-900 border border-zinc-800 rounded-lg p-6">
                  <h3 className={`text-lg font-semibold mb-3 text-${feature.color || 'blue'}-400`}>
                    ✓ {feature.title}
                  </h3>
                  <p className="text-sm text-zinc-400">
                    {feature.description}
                  </p>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Benefits */}
        {benefits && benefits.length > 0 && (
          <div className="bg-gradient-to-r from-zinc-900 to-zinc-800 border border-zinc-700 rounded-lg p-6">
            <h2 className="text-2xl font-bold mb-4">Bénéfices Clients</h2>
            <div className={`grid grid-cols-1 md:grid-cols-2 lg:grid-cols-${Math.min(benefits.length, 4)} gap-4`}>
              {benefits.map((benefit, idx) => (
                <div key={idx} className="flex items-start space-x-3">
                  <div className="text-2xl">{benefit.icon}</div>
                  <div>
                    <div className="font-semibold mb-1">{benefit.title}</div>
                    <div className="text-sm text-zinc-400">{benefit.description}</div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
