import { useState } from "react";
import {
  useAuthGuardian,
  useSecureFormWatcher,
  useTokenAutoRefresher,
  useSessionHijackGuardian,
  useLicenseManager,
  useMonetizerAI,
} from "../components/security";

export default function SecurityDashboard() {
  const [activeModule, setActiveModule] = useState("overview");
  const authGuardian = useAuthGuardian();
  const formWatcher = useSecureFormWatcher();
  const tokenRefresher = useTokenAutoRefresher();
  const sessionGuardian = useSessionHijackGuardian();
  const licenseManager = useLicenseManager();
  const monetizer = useMonetizerAI();

  const [testInput, setTestInput] = useState("");
  const [licenseKey, setLicenseKey] = useState("");

  const modules = [
    {
      id: "overview",
      name: "Vue d'ensemble",
      icon: "🔐",
      color: "text-sentinel-blue",
    },
    {
      id: "authguardian",
      name: "AuthGuardian",
      icon: "🧩",
      color: "text-green-400",
    },
    {
      id: "formwatcher",
      name: "SecureFormWatcher",
      icon: "🔒",
      color: "text-blue-400",
    },
    {
      id: "tokenrefresher",
      name: "TokenAutoRefresher",
      icon: "🔁",
      color: "text-purple-400",
    },
    {
      id: "sessionguardian",
      name: "SessionHijackGuardian",
      icon: "🧱",
      color: "text-red-400",
    },
    {
      id: "licensemanager",
      name: "LicenseManager",
      icon: "🪪",
      color: "text-yellow-400",
    },
    {
      id: "monetizerai",
      name: "MonetizerAI",
      icon: "💰",
      color: "text-emerald-400",
    },
  ];

  const handleTestInput = (value) => {
    setTestInput(value);
    formWatcher.validateInput("test", value);
  };

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100 p-6">
      <div className="max-w-7xl mx-auto">
        {/* Header */}
        <div className="mb-8">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h1 className="text-4xl font-bold text-sentinel-blue mb-2">
                MODULE 12 — Sécurité & Authentification
              </h1>
              <p className="text-zinc-400">
                Protection des accès, licences et sessions sécurisées
              </p>
            </div>
            <a
              href="/"
              className="px-4 py-2 bg-zinc-800 hover:bg-zinc-700 border border-zinc-700 rounded-lg transition"
            >
              ← Accueil
            </a>
          </div>

          {/* Status Overview */}
          <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-4">
            <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-4">
              <div className="text-xs text-zinc-400 mb-1">AuthGuardian</div>
              <div className="flex items-center gap-2">
                <div className={`w-2 h-2 rounded-full ${authGuardian.suspiciousActivity ? "bg-red-500" : "bg-green-500"}`}></div>
                <span className={`text-sm font-semibold ${authGuardian.suspiciousActivity ? "text-red-400" : "text-green-400"}`}>
                  {authGuardian.suspiciousActivity ? "Alerte" : "Actif"}
                </span>
              </div>
            </div>

            <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-4">
              <div className="text-xs text-zinc-400 mb-1">FormWatcher</div>
              <div className="flex items-center gap-2">
                <div className={`w-2 h-2 rounded-full ${formWatcher.isSecure ? "bg-green-500" : "bg-red-500"}`}></div>
                <span className={`text-sm font-semibold ${formWatcher.isSecure ? "text-green-400" : "text-red-400"}`}>
                  {formWatcher.isSecure ? "Sécurisé" : "Menace"}
                </span>
              </div>
            </div>

            <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-4">
              <div className="text-xs text-zinc-400 mb-1">TokenRefresher</div>
              <div className="flex items-center gap-2">
                <div className={`w-2 h-2 rounded-full ${tokenRefresher.isRefreshing ? "bg-yellow-500" : "bg-green-500"}`}></div>
                <span className={`text-sm font-semibold ${tokenRefresher.isRefreshing ? "text-yellow-400" : "text-green-400"}`}>
                  {tokenRefresher.isRefreshing ? "Rafraîch." : "Actif"}
                </span>
              </div>
            </div>

            <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-4">
              <div className="text-xs text-zinc-400 mb-1">SessionGuard</div>
              <div className="flex items-center gap-2">
                <div className={`w-2 h-2 rounded-full ${sessionGuardian.isSessionValid ? "bg-green-500" : "bg-red-500"}`}></div>
                <span className={`text-sm font-semibold ${sessionGuardian.isSessionValid ? "text-green-400" : "text-red-400"}`}>
                  {sessionGuardian.isSessionValid ? "Valide" : "Invalide"}
                </span>
              </div>
            </div>

            <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-4">
              <div className="text-xs text-zinc-400 mb-1">LicenseManager</div>
              <div className="flex items-center gap-2">
                <div className={`w-2 h-2 rounded-full ${licenseManager.licenseInfo.isValid ? "bg-green-500" : "bg-yellow-500"}`}></div>
                <span className={`text-sm font-semibold ${licenseManager.licenseInfo.isValid ? "text-green-400" : "text-yellow-400"}`}>
                  {licenseManager.licenseInfo.type}
                </span>
              </div>
            </div>

            <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-4">
              <div className="text-xs text-zinc-400 mb-1">MonetizerAI</div>
              <div className="flex items-center gap-2">
                <div className="w-2 h-2 rounded-full bg-green-500 animate-pulse"></div>
                <span className="text-sm font-semibold text-green-400">
                  ${monetizer.revenueData.currentRevenue.toFixed(0)}
                </span>
              </div>
            </div>
          </div>
        </div>

        {/* Module Selection */}
        <div className="flex gap-2 mb-6 overflow-x-auto pb-2">
          {modules.map((module) => (
            <button
              key={module.id}
              onClick={() => setActiveModule(module.id)}
              className={`flex items-center gap-2 px-4 py-2 rounded-lg transition whitespace-nowrap ${
                activeModule === module.id
                  ? "bg-sentinel-blue text-white"
                  : "bg-zinc-900 text-zinc-400 hover:bg-zinc-800"
              }`}
            >
              <span>{module.icon}</span>
              <span className="text-sm font-medium">{module.name}</span>
            </button>
          ))}
        </div>

        {/* Module Content */}
        <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-6">
          {/* Overview */}
          {activeModule === "overview" && (
            <div>
              <h2 className="text-2xl font-bold mb-6">Vue d'ensemble du MODULE 12</h2>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                {modules.slice(1).map((module) => (
                  <div
                    key={module.id}
                    className="bg-zinc-800/50 border border-zinc-700 rounded-lg p-6 hover:border-sentinel-blue transition cursor-pointer"
                    onClick={() => setActiveModule(module.id)}
                  >
                    <div className="flex items-start gap-4">
                      <div className="text-4xl">{module.icon}</div>
                      <div className="flex-1">
                        <h3 className={`text-xl font-semibold mb-2 ${module.color}`}>
                          {module.name}
                        </h3>
                        <p className="text-sm text-zinc-400">
                          {module.id === "authguardian" && "Garant de la sécurité des connexions et déconnexions"}
                          {module.id === "formwatcher" && "Protection des formulaires contre injections et fraude"}
                          {module.id === "tokenrefresher" && "Renouvellement automatique des jetons d'accès"}
                          {module.id === "sessionguardian" && "Détection d'usurpation de session en temps réel"}
                          {module.id === "licensemanager" && "Gestion des licences et validation d'authenticité"}
                          {module.id === "monetizerai" && "Optimisation IA des revenus et abonnements"}
                        </p>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* AuthGuardian */}
          {activeModule === "authguardian" && (
            <div>
              <h2 className="text-2xl font-bold mb-4 flex items-center">
                <span className="mr-3">🧩</span>
                AuthGuardian
              </h2>
              <p className="text-zinc-400 mb-6">
                Garant de la sécurité des connexions et déconnexions sur toutes les plateformes Sentinel.
              </p>

              <div className="space-y-4">
                <div className="bg-zinc-800/50 p-4 rounded-lg">
                  <h3 className="font-semibold mb-2">État de session</h3>
                  <div className="grid grid-cols-2 gap-4 text-sm">
                    <div>
                      <span className="text-zinc-400">Authentifié:</span>
                      <span className={`ml-2 font-semibold ${authGuardian.user ? "text-green-400" : "text-red-400"}`}>
                        {authGuardian.user ? "Oui" : "Non"}
                      </span>
                    </div>
                    <div>
                      <span className="text-zinc-400">Activité suspecte:</span>
                      <span className={`ml-2 font-semibold ${authGuardian.suspiciousActivity ? "text-red-400" : "text-green-400"}`}>
                        {authGuardian.suspiciousActivity ? "Oui" : "Non"}
                      </span>
                    </div>
                  </div>
                </div>

                <div className="bg-zinc-800/50 p-4 rounded-lg">
                  <h3 className="font-semibold mb-3">Sous-modules actifs</h3>
                  <div className="space-y-2">
                    {["Login Verifier", "Session Tracker", "Logout Integrity Checker", "Access Log Sentinel"].map((sub) => (
                      <div key={sub} className="flex items-center gap-2 text-sm">
                        <div className="w-2 h-2 bg-green-500 rounded-full animate-pulse"></div>
                        <span className="text-zinc-300">{sub}</span>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* SecureFormWatcher */}
          {activeModule === "formwatcher" && (
            <div>
              <h2 className="text-2xl font-bold mb-4 flex items-center">
                <span className="mr-3">🔒</span>
                SecureFormWatcher
              </h2>
              <p className="text-zinc-400 mb-6">
                Surveille et protège les formulaires web contre l'injection de scripts, la fraude ou le phishing.
              </p>

              <div className="space-y-4">
                <div className="bg-zinc-800/50 p-4 rounded-lg">
                  <h3 className="font-semibold mb-3">Test de sécurité</h3>
                  <input
                    type="text"
                    value={testInput}
                    onChange={(e) => handleTestInput(e.target.value)}
                    placeholder="Essayez: <script>alert('test')</script>"
                    className="w-full bg-zinc-900 border border-zinc-700 rounded-lg px-4 py-2 text-zinc-100 focus:outline-none focus:border-sentinel-blue"
                  />
                  {formWatcher.threats.length > 0 && (
                    <div className="mt-3 p-3 bg-red-900/20 border border-red-800 rounded-lg">
                      <div className="font-semibold text-red-400 mb-2">Menace détectée!</div>
                      {formWatcher.threats.map((threat, idx) => (
                        <div key={idx} className="text-sm text-zinc-300">
                          • {threat.type} sur le champ: {threat.field}
                        </div>
                      ))}
                    </div>
                  )}
                </div>

                <div className="bg-zinc-800/50 p-4 rounded-lg">
                  <h3 className="font-semibold mb-3">Sous-modules actifs</h3>
                  <div className="space-y-2">
                    {["Input Validator", "Form Integrity Scanner", "Captcha AI Verifier", "Spam Filter Node"].map((sub) => (
                      <div key={sub} className="flex items-center gap-2 text-sm">
                        <div className="w-2 h-2 bg-green-500 rounded-full animate-pulse"></div>
                        <span className="text-zinc-300">{sub}</span>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* TokenAutoRefresher */}
          {activeModule === "tokenrefresher" && (
            <div>
              <h2 className="text-2xl font-bold mb-4 flex items-center">
                <span className="mr-3">🔁</span>
                TokenAutoRefresher
              </h2>
              <p className="text-zinc-400 mb-6">
                Gère la validité et le renouvellement automatique des jetons d'accès (API, sessions, authentification).
              </p>

              <div className="space-y-4">
                <div className="bg-zinc-800/50 p-4 rounded-lg">
                  <h3 className="font-semibold mb-3">État du jeton</h3>
                  <div className="grid grid-cols-2 gap-4 text-sm">
                    <div>
                      <span className="text-zinc-400">Valide:</span>
                      <span className={`ml-2 font-semibold ${tokenRefresher.tokenStatus.isValid ? "text-green-400" : "text-red-400"}`}>
                        {tokenRefresher.tokenStatus.isValid ? "Oui" : "Non"}
                      </span>
                    </div>
                    <div>
                      <span className="text-zinc-400">Rafraîchissements:</span>
                      <span className="ml-2 font-semibold text-blue-400">
                        {tokenRefresher.tokenStatus.refreshCount}
                      </span>
                    </div>
                    <div>
                      <span className="text-zinc-400">En cours:</span>
                      <span className={`ml-2 font-semibold ${tokenRefresher.isRefreshing ? "text-yellow-400" : "text-green-400"}`}>
                        {tokenRefresher.isRefreshing ? "Oui" : "Non"}
                      </span>
                    </div>
                    <div>
                      <span className="text-zinc-400">Dernier:</span>
                      <span className="ml-2 font-semibold text-zinc-300">
                        {tokenRefresher.tokenStatus.lastRefresh?.toLocaleTimeString() || "N/A"}
                      </span>
                    </div>
                  </div>
                </div>

                <div className="bg-zinc-800/50 p-4 rounded-lg">
                  <h3 className="font-semibold mb-3">Sous-modules actifs</h3>
                  <div className="space-y-2">
                    {["Token Lifespan Manager", "Expiry Watchdog", "Auto Renewal Engine", "Session Validator"].map((sub) => (
                      <div key={sub} className="flex items-center gap-2 text-sm">
                        <div className="w-2 h-2 bg-green-500 rounded-full animate-pulse"></div>
                        <span className="text-zinc-300">{sub}</span>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* SessionHijackGuardian */}
          {activeModule === "sessionguardian" && (
            <div>
              <h2 className="text-2xl font-bold mb-4 flex items-center">
                <span className="mr-3">🧱</span>
                SessionHijackGuardian
              </h2>
              <p className="text-zinc-400 mb-6">
                Détecte toute tentative d'usurpation ou de détournement de session utilisateur.
              </p>

              <div className="space-y-4">
                <div className="bg-zinc-800/50 p-4 rounded-lg">
                  <h3 className="font-semibold mb-3">État de session</h3>
                  <div className="grid grid-cols-2 gap-4 text-sm">
                    <div>
                      <span className="text-zinc-400">Session valide:</span>
                      <span className={`ml-2 font-semibold ${sessionGuardian.isSessionValid ? "text-green-400" : "text-red-400"}`}>
                        {sessionGuardian.isSessionValid ? "Oui" : "Non"}
                      </span>
                    </div>
                    <div>
                      <span className="text-zinc-400">Tentatives d'usurpation:</span>
                      <span className={`ml-2 font-semibold ${sessionGuardian.hijackAttempts.length > 0 ? "text-red-400" : "text-green-400"}`}>
                        {sessionGuardian.hijackAttempts.length}
                      </span>
                    </div>
                  </div>
                </div>

                <div className="bg-zinc-800/50 p-4 rounded-lg">
                  <h3 className="font-semibold mb-3">Sous-modules actifs</h3>
                  <div className="space-y-2">
                    {["Session Integrity AI", "IP Behavior Tracker", "Identity Fingerprint Engine", "Auto Termination Node"].map((sub) => (
                      <div key={sub} className="flex items-center gap-2 text-sm">
                        <div className="w-2 h-2 bg-green-500 rounded-full animate-pulse"></div>
                        <span className="text-zinc-300">{sub}</span>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* LicenseManager */}
          {activeModule === "licensemanager" && (
            <div>
              <h2 className="text-2xl font-bold mb-4 flex items-center">
                <span className="mr-3">🪪</span>
                LicenseManager
              </h2>
              <p className="text-zinc-400 mb-6">
                Gère les licences logicielles, la validation d'authenticité et la conformité d'utilisation.
              </p>

              <div className="space-y-4">
                <div className="bg-zinc-800/50 p-4 rounded-lg">
                  <h3 className="font-semibold mb-3">Informations de licence</h3>
                  <div className="grid grid-cols-2 gap-4 text-sm">
                    <div>
                      <span className="text-zinc-400">Type:</span>
                      <span className="ml-2 font-semibold text-sentinel-blue uppercase">
                        {licenseManager.licenseInfo.type}
                      </span>
                    </div>
                    <div>
                      <span className="text-zinc-400">Valide:</span>
                      <span className={`ml-2 font-semibold ${licenseManager.licenseInfo.isValid ? "text-green-400" : "text-red-400"}`}>
                        {licenseManager.licenseInfo.isValid ? "Oui" : "Non"}
                      </span>
                    </div>
                    <div>
                      <span className="text-zinc-400">Activations:</span>
                      <span className="ml-2 font-semibold text-zinc-300">
                        {licenseManager.licenseInfo.activations}/{licenseManager.licenseInfo.maxActivations}
                      </span>
                    </div>
                    <div>
                      <span className="text-zinc-400">Fonctionnalités:</span>
                      <span className="ml-2 font-semibold text-zinc-300">
                        {licenseManager.licenseInfo.features.length}
                      </span>
                    </div>
                  </div>
                </div>

                <div className="bg-zinc-800/50 p-4 rounded-lg">
                  <h3 className="font-semibold mb-3">Test d'activation</h3>
                  <div className="flex gap-2">
                    <input
                      type="text"
                      value={licenseKey}
                      onChange={(e) => setLicenseKey(e.target.value.toUpperCase())}
                      placeholder="SENTINEL-XXXX-XXXX-XXXX"
                      className="flex-1 bg-zinc-900 border border-zinc-700 rounded-lg px-4 py-2 text-zinc-100 focus:outline-none focus:border-sentinel-blue font-mono text-sm"
                    />
                    <button
                      onClick={() => licenseManager.activateLicense(licenseKey)}
                      className="px-4 py-2 bg-sentinel-blue hover:bg-blue-600 rounded-lg transition"
                    >
                      Activer
                    </button>
                  </div>
                  <p className="text-xs text-zinc-500 mt-2">
                    Démo: SENTINEL-PRO1-2024-DEMO
                  </p>
                </div>

                <div className="bg-zinc-800/50 p-4 rounded-lg">
                  <h3 className="font-semibold mb-3">Sous-modules actifs</h3>
                  <div className="space-y-2">
                    {["License Validator", "Key Activation Node", "Usage Tracker", "Compliance Auditor"].map((sub) => (
                      <div key={sub} className="flex items-center gap-2 text-sm">
                        <div className="w-2 h-2 bg-green-500 rounded-full animate-pulse"></div>
                        <span className="text-zinc-300">{sub}</span>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* MonetizerAI */}
          {activeModule === "monetizerai" && (
            <div>
              <h2 className="text-2xl font-bold mb-4 flex items-center">
                <span className="mr-3">💰</span>
                MonetizerAI
              </h2>
              <p className="text-zinc-400 mb-6">
                Module de prévision et de monétisation IA. Optimise les modèles d'abonnement et de partenariat.
              </p>

              <div className="space-y-4">
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                  <div className="bg-zinc-800/50 p-4 rounded-lg">
                    <div className="text-xs text-zinc-400 mb-1">Revenu actuel</div>
                    <div className="text-2xl font-bold text-green-400">
                      ${monetizer.revenueData.currentRevenue.toFixed(0)}
                    </div>
                  </div>
                  <div className="bg-zinc-800/50 p-4 rounded-lg">
                    <div className="text-xs text-zinc-400 mb-1">Prévu (mois prochain)</div>
                    <div className="text-2xl font-bold text-blue-400">
                      ${monetizer.revenueData.predictedRevenue.toFixed(0)}
                    </div>
                  </div>
                  <div className="bg-zinc-800/50 p-4 rounded-lg">
                    <div className="text-xs text-zinc-400 mb-1">Croissance</div>
                    <div className="text-2xl font-bold text-emerald-400">
                      +{monetizer.revenueData.monthlyGrowth.toFixed(1)}%
                    </div>
                  </div>
                </div>

                <div className="bg-zinc-800/50 p-4 rounded-lg">
                  <h3 className="font-semibold mb-3">Plans d'abonnement</h3>
                  <div className="space-y-2">
                    {monetizer.subscriptionPlans.map((plan) => (
                      <div key={plan.id} className="flex items-center justify-between bg-zinc-900 px-4 py-3 rounded-lg">
                        <div>
                          <div className="font-semibold">{plan.name}</div>
                          <div className="text-xs text-zinc-400">{plan.subscribers} abonnés</div>
                        </div>
                        <div className="text-right">
                          <div className="font-bold text-green-400">${plan.price}/mois</div>
                          <div className="text-xs text-zinc-400">
                            ${(plan.price * plan.subscribers).toFixed(0)}/mois
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>

                <div className="bg-zinc-800/50 p-4 rounded-lg">
                  <h3 className="font-semibold mb-3">Partenaires actifs</h3>
                  <div className="space-y-2">
                    {monetizer.partners.filter(p => p.status === "active").map((partner) => (
                      <div key={partner.id} className="flex items-center justify-between bg-zinc-900 px-4 py-3 rounded-lg">
                        <div className="font-medium">{partner.name}</div>
                        <div className="text-green-400 font-semibold">${partner.revenue}</div>
                      </div>
                    ))}
                  </div>
                </div>

                <div className="bg-zinc-800/50 p-4 rounded-lg">
                  <h3 className="font-semibold mb-3">Sous-modules actifs</h3>
                  <div className="space-y-2">
                    {["Revenue Predictor", "Partner Tracker", "Subscription Optimizer", "Dynamic Pricing AI"].map((sub) => (
                      <div key={sub} className="flex items-center gap-2 text-sm">
                        <div className="w-2 h-2 bg-green-500 rounded-full animate-pulse"></div>
                        <span className="text-zinc-300">{sub}</span>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
