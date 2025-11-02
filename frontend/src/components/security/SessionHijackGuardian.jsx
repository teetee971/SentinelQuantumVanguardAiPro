import { useEffect, useState } from "react";
import { getAuth } from "firebase/auth";

/**
 * SessionHijackGuardian - MODULE 12
 * Détecte toute tentative d'usurpation ou de détournement de session utilisateur
 * Empêche la prise de contrôle frauduleuse en temps réel
 */

export function useSessionHijackGuardian() {
  const [sessionFingerprint, setSessionFingerprint] = useState(null);
  const [isSessionValid, setIsSessionValid] = useState(true);
  const [hijackAttempts, setHijackAttempts] = useState([]);
  const [lastValidation, setLastValidation] = useState(new Date());

  // Identity Fingerprint Engine
  const generateFingerprint = () => {
    const fingerprint = {
      userAgent: navigator.userAgent,
      language: navigator.language,
      platform: navigator.platform,
      screenResolution: `${window.screen.width}x${window.screen.height}`,
      timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
      colorDepth: window.screen.colorDepth,
      timestamp: Date.now(),
    };

    return JSON.stringify(fingerprint);
  };

  // Session Integrity AI
  const validateSessionIntegrity = (currentFingerprint) => {
    if (!sessionFingerprint) return true;

    const stored = JSON.parse(sessionFingerprint);
    const current = JSON.parse(currentFingerprint);

    // Check for critical changes
    const criticalMismatch =
      stored.userAgent !== current.userAgent ||
      stored.platform !== current.platform ||
      stored.screenResolution !== current.screenResolution;

    if (criticalMismatch) {
      const attempt = {
        type: "SESSION_HIJACK_ATTEMPT",
        timestamp: new Date(),
        details: {
          storedFingerprint: stored,
          currentFingerprint: current,
        },
      };

      setHijackAttempts((prev) => [...prev, attempt]);
      console.error("🚨 SessionHijackGuardian: Hijack attempt detected!");
      return false;
    }

    return true;
  };

  // IP Behavior Tracker (simulated)
  const trackIPBehavior = () => {
    // In production, this would check IP address changes
    // For demo, we simulate monitoring
    const behavior = {
      timestamp: new Date(),
      status: "normal",
      ipChanges: 0,
      locationChanges: 0,
    };

    return behavior;
  };

  // Auto Termination Node
  const terminateSession = async () => {
    console.warn("⚠️ SessionHijackGuardian: Terminating session due to security threat");
    
    const auth = getAuth();
    try {
      await auth.signOut();
      setIsSessionValid(false);
      
      // Clear all session data
      sessionStorage.clear();
      localStorage.clear();
    } catch (error) {
      console.error("Error terminating session:", error);
    }
  };

  // Initialize and monitor
  useEffect(() => {
    // Generate initial fingerprint
    const initialFingerprint = generateFingerprint();
    setSessionFingerprint(initialFingerprint);

    // Validate session periodically
    const validationInterval = setInterval(() => {
      const currentFingerprint = generateFingerprint();
      const isValid = validateSessionIntegrity(currentFingerprint);

      setLastValidation(new Date());

      if (!isValid) {
        setIsSessionValid(false);
        terminateSession();
      }
    }, 30000); // Check every 30 seconds

    // Track IP behavior
    const behaviorInterval = setInterval(() => {
      trackIPBehavior();
    }, 60000); // Check every minute

    // Monitor for suspicious activity
    const handleVisibilityChange = () => {
      if (document.hidden) {
        console.log("🔍 SessionHijackGuardian: Tab hidden, monitoring...");
      } else {
        // Re-validate when tab becomes visible
        const currentFingerprint = generateFingerprint();
        const isValid = validateSessionIntegrity(currentFingerprint);
        
        if (!isValid) {
          setIsSessionValid(false);
          terminateSession();
        }
      }
    };

    document.addEventListener("visibilitychange", handleVisibilityChange);

    return () => {
      clearInterval(validationInterval);
      clearInterval(behaviorInterval);
      document.removeEventListener("visibilitychange", handleVisibilityChange);
    };
  }, [sessionFingerprint]);

  const getSessionStatus = () => {
    return {
      isValid: isSessionValid,
      hijackAttempts: hijackAttempts.length,
      lastValidation,
      fingerprint: sessionFingerprint ? "Set" : "Not Set",
    };
  };

  const forceValidation = () => {
    const currentFingerprint = generateFingerprint();
    return validateSessionIntegrity(currentFingerprint);
  };

  return {
    isSessionValid,
    hijackAttempts,
    getSessionStatus,
    forceValidation,
    terminateSession,
  };
}

export default function SessionHijackGuardian({ children, autoTerminate = true }) {
  const { isSessionValid, hijackAttempts, getSessionStatus } =
    useSessionHijackGuardian();

  if (!isSessionValid && autoTerminate) {
    return (
      <div className="min-h-screen bg-zinc-950 flex items-center justify-center p-6">
        <div className="max-w-md w-full bg-red-900/20 border border-red-800 rounded-lg p-8 text-center">
          <div className="text-6xl mb-4">🚨</div>
          <h2 className="text-2xl font-bold text-red-400 mb-2">
            Session Compromise Détectée
          </h2>
          <p className="text-zinc-300 mb-4">
            SessionHijackGuardian a détecté une tentative d'usurpation de
            session. Votre session a été terminée par sécurité.
          </p>
          <div className="bg-zinc-900 rounded-lg p-4 mb-6">
            <div className="text-sm text-zinc-400 space-y-2">
              <div className="flex justify-between">
                <span>Tentatives détectées:</span>
                <span className="text-red-400 font-semibold">
                  {hijackAttempts.length}
                </span>
              </div>
              <div className="flex justify-between">
                <span>Status:</span>
                <span className="text-red-400 font-semibold">Terminée</span>
              </div>
            </div>
          </div>
          <div className="space-y-3">
            <a
              href="/"
              className="block px-6 py-3 bg-sentinel-blue hover:bg-blue-600 rounded-lg transition"
            >
              Retour à l'accueil
            </a>
            <p className="text-xs text-zinc-500">
              Si vous pensez qu'il s'agit d'une erreur, contactez le support.
            </p>
          </div>
        </div>
      </div>
    );
  }

  if (hijackAttempts.length > 0) {
    return (
      <div>
        <div className="bg-yellow-900/20 border border-yellow-800 rounded-lg p-4 mb-4">
          <div className="flex items-center gap-2 mb-2">
            <svg
              className="w-5 h-5 text-yellow-400"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
              />
            </svg>
            <span className="font-semibold text-yellow-400">
              SessionHijackGuardian: Alerte
            </span>
          </div>
          <p className="text-sm text-zinc-300">
            {hijackAttempts.length} tentative(s) d'usurpation détectée(s) et
            bloquée(s).
          </p>
        </div>
        {children}
      </div>
    );
  }

  return <>{children}</>;
}
