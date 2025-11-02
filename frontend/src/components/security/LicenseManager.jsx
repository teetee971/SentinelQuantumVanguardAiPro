import { useState, useEffect } from "react";

/**
 * LicenseManager - MODULE 12
 * Gère les licences logicielles, la validation d'authenticité
 * et la conformité d'utilisation des produits Sentinel
 */

export function useLicenseManager() {
  const [licenseInfo, setLicenseInfo] = useState({
    key: null,
    isValid: false,
    type: "free", // free, pro, enterprise
    expiresAt: null,
    activatedAt: null,
    activations: 0,
    maxActivations: 1,
    features: [],
  });

  const [validationStatus, setValidationStatus] = useState("checking");

  // License Validator
  const validateLicense = async (licenseKey) => {
    setValidationStatus("validating");

    try {
      // Simulate server-side validation
      await new Promise((resolve) => setTimeout(resolve, 1000));

      // Simple validation logic (in production, validate against server)
      const isValidFormat = /^SENTINEL-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$/.test(
        licenseKey
      );

      if (!isValidFormat) {
        setValidationStatus("invalid");
        return false;
      }

      // Mock license data based on key pattern
      const licenseData = {
        key: licenseKey,
        isValid: true,
        type: licenseKey.includes("PRO") ? "pro" : "enterprise",
        expiresAt: new Date(Date.now() + 365 * 24 * 60 * 60 * 1000), // 1 year
        activatedAt: new Date(),
        activations: 1,
        maxActivations: licenseKey.includes("ENTERPRISE") ? 10 : 3,
        features: licenseKey.includes("ENTERPRISE")
          ? ["all", "priority-support", "custom-branding", "api-access"]
          : ["dashboard", "scan", "alerts", "basic-support"],
      };

      setLicenseInfo(licenseData);
      setValidationStatus("valid");

      // Store in localStorage (in production, use secure storage)
      localStorage.setItem("sentinel_license", JSON.stringify(licenseData));

      console.log("✅ LicenseManager: License validated successfully");
      return true;
    } catch (error) {
      console.error("❌ LicenseManager: Validation failed", error);
      setValidationStatus("error");
      return false;
    }
  };

  // Key Activation Node
  const activateLicense = async (licenseKey) => {
    const isValid = await validateLicense(licenseKey);

    if (isValid) {
      console.log("✅ LicenseManager: License activated");
      return true;
    }

    return false;
  };

  // Usage Tracker
  const trackUsage = (feature) => {
    const usage = JSON.parse(localStorage.getItem("sentinel_usage") || "{}");
    usage[feature] = (usage[feature] || 0) + 1;
    usage.lastUsed = new Date().toISOString();
    localStorage.setItem("sentinel_usage", JSON.stringify(usage));
  };

  // Compliance Auditor
  const checkCompliance = () => {
    const now = Date.now();

    if (!licenseInfo.isValid) {
      return { compliant: false, reason: "No valid license" };
    }

    if (licenseInfo.expiresAt && new Date(licenseInfo.expiresAt) < now) {
      return { compliant: false, reason: "License expired" };
    }

    if (licenseInfo.activations > licenseInfo.maxActivations) {
      return { compliant: false, reason: "Activation limit exceeded" };
    }

    return { compliant: true, reason: "Fully compliant" };
  };

  // Load license from storage on mount
  useEffect(() => {
    const stored = localStorage.getItem("sentinel_license");
    if (stored) {
      try {
        const data = JSON.parse(stored);
        setLicenseInfo(data);
        setValidationStatus(data.isValid ? "valid" : "invalid");
      } catch (error) {
        console.error("LicenseManager: Failed to load stored license", error);
      }
    } else {
      setValidationStatus("none");
    }
  }, []);

  const hasFeature = (feature) => {
    return (
      licenseInfo.features.includes("all") ||
      licenseInfo.features.includes(feature)
    );
  };

  const getLicenseStatus = () => {
    const compliance = checkCompliance();
    const daysUntilExpiry = licenseInfo.expiresAt
      ? Math.floor(
          (new Date(licenseInfo.expiresAt) - Date.now()) / (1000 * 60 * 60 * 24)
        )
      : null;

    return {
      ...licenseInfo,
      validationStatus,
      compliance,
      daysUntilExpiry,
    };
  };

  return {
    licenseInfo,
    validationStatus,
    validateLicense,
    activateLicense,
    trackUsage,
    checkCompliance,
    hasFeature,
    getLicenseStatus,
  };
}

export default function LicenseManager({ children, requireLicense = false }) {
  const { licenseInfo, validationStatus, activateLicense } = useLicenseManager();
  const [showActivation, setShowActivation] = useState(false);
  const [licenseKey, setLicenseKey] = useState("");
  const [activating, setActivating] = useState(false);

  const handleActivate = async (e) => {
    e.preventDefault();
    setActivating(true);

    const success = await activateLicense(licenseKey);

    if (success) {
      setShowActivation(false);
      setLicenseKey("");
    } else {
      alert("Clé de licence invalide. Format: SENTINEL-XXXX-XXXX-XXXX");
    }

    setActivating(false);
  };

  if (requireLicense && validationStatus === "none") {
    return (
      <div className="min-h-screen bg-zinc-950 flex items-center justify-center p-6">
        <div className="max-w-md w-full bg-zinc-900 border border-zinc-800 rounded-lg p-8">
          <div className="text-center mb-6">
            <div className="text-5xl mb-4">🪪</div>
            <h2 className="text-2xl font-bold text-zinc-100 mb-2">
              Activation de Licence
            </h2>
            <p className="text-zinc-400 text-sm">
              LicenseManager requiert une licence valide pour continuer
            </p>
          </div>

          <form onSubmit={handleActivate} className="space-y-4">
            <div>
              <label className="block text-sm text-zinc-400 mb-2">
                Clé de licence
              </label>
              <input
                type="text"
                value={licenseKey}
                onChange={(e) => setLicenseKey(e.target.value.toUpperCase())}
                placeholder="SENTINEL-XXXX-XXXX-XXXX"
                className="w-full bg-zinc-800 border border-zinc-700 rounded-lg px-4 py-3 text-zinc-100 focus:outline-none focus:border-sentinel-blue font-mono text-sm"
                required
              />
            </div>

            <button
              type="submit"
              disabled={activating}
              className="w-full px-6 py-3 bg-sentinel-blue hover:bg-blue-600 rounded-lg transition font-semibold disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {activating ? "Activation..." : "Activer la licence"}
            </button>
          </form>

          <div className="mt-6 pt-6 border-t border-zinc-800">
            <p className="text-xs text-zinc-500 text-center mb-2">
              Pour des fins de démonstration, utilisez:
            </p>
            <code className="block text-xs bg-zinc-800 px-3 py-2 rounded text-sentinel-blue text-center">
              SENTINEL-PRO1-2024-DEMO
            </code>
          </div>
        </div>
      </div>
    );
  }

  if (requireLicense && !licenseInfo.isValid) {
    return (
      <div className="min-h-screen bg-zinc-950 flex items-center justify-center p-6">
        <div className="max-w-md w-full bg-red-900/20 border border-red-800 rounded-lg p-8 text-center">
          <div className="text-5xl mb-4">⚠️</div>
          <h2 className="text-2xl font-bold text-red-400 mb-2">
            Licence Invalide
          </h2>
          <p className="text-zinc-300 mb-6">
            Votre licence Sentinel n'est pas valide ou a expiré.
          </p>
          <button
            onClick={() => setShowActivation(true)}
            className="px-6 py-3 bg-sentinel-blue hover:bg-blue-600 rounded-lg transition"
          >
            Réactiver la licence
          </button>
        </div>
      </div>
    );
  }

  return <>{children}</>;
}
