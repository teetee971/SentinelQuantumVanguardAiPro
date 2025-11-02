import { useEffect, useState } from "react";
import { getAuth, onAuthStateChanged } from "firebase/auth";

/**
 * AuthGuardian - MODULE 12
 * Garant de la sécurité des connexions et déconnexions
 * Vérifie l'identité et prévient les tentatives d'accès non autorisé
 */

export function useAuthGuardian() {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [sessionLog, setSessionLog] = useState([]);
  const [suspiciousActivity, setSuspiciousActivity] = useState(false);

  useEffect(() => {
    const auth = getAuth();
    
    // Login Verifier & Session Tracker
    const unsubscribe = onAuthStateChanged(auth, (currentUser) => {
      setUser(currentUser);
      setLoading(false);

      if (currentUser) {
        // Log successful login
        const loginEvent = {
          type: "login",
          timestamp: new Date(),
          userId: currentUser.uid,
          email: currentUser.email,
          ip: "Protected", // Would use real IP detection in production
        };
        
        setSessionLog((prev) => [...prev, loginEvent]);
        
        // Access Log Sentinel - Monitor for suspicious patterns
        checkForSuspiciousActivity(loginEvent);
      }
    });

    // Logout Integrity Checker
    const handleBeforeUnload = () => {
      if (user) {
        const logoutEvent = {
          type: "logout",
          timestamp: new Date(),
          userId: user.uid,
        };
        setSessionLog((prev) => [...prev, logoutEvent]);
      }
    };

    window.addEventListener("beforeunload", handleBeforeUnload);

    return () => {
      unsubscribe();
      window.removeEventListener("beforeunload", handleBeforeUnload);
    };
  }, [user]);

  const checkForSuspiciousActivity = (event) => {
    // Simulate suspicious activity detection
    const recentLogins = sessionLog.filter(
      (log) => log.type === "login" && Date.now() - log.timestamp < 300000
    );

    if (recentLogins.length > 3) {
      setSuspiciousActivity(true);
      console.warn("⚠️ AuthGuardian: Suspicious login activity detected!");
    }
  };

  const verifySession = () => {
    return user !== null;
  };

  const getSessionInfo = () => {
    return {
      isAuthenticated: !!user,
      user: user,
      sessionLog: sessionLog,
      hasSuspiciousActivity: suspiciousActivity,
      sessionDuration: user ? Date.now() - user.metadata.lastSignInTime : 0,
    };
  };

  return {
    user,
    loading,
    verifySession,
    getSessionInfo,
    suspiciousActivity,
  };
}

export default function AuthGuardian({ children, requireAuth = false }) {
  const { user, loading, suspiciousActivity } = useAuthGuardian();

  if (loading) {
    return (
      <div className="min-h-screen bg-zinc-950 flex items-center justify-center">
        <div className="text-center">
          <div className="inline-block animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-sentinel-blue mb-4"></div>
          <p className="text-zinc-400">Vérification AuthGuardian...</p>
        </div>
      </div>
    );
  }

  if (requireAuth && !user) {
    return (
      <div className="min-h-screen bg-zinc-950 flex items-center justify-center p-6">
        <div className="max-w-md w-full bg-zinc-900 border border-zinc-800 rounded-lg p-8 text-center">
          <div className="text-6xl mb-4">🔒</div>
          <h2 className="text-2xl font-bold text-zinc-100 mb-2">
            Accès Restreint
          </h2>
          <p className="text-zinc-400 mb-6">
            AuthGuardian requiert une authentification pour accéder à cette page.
          </p>
          <a
            href="/"
            className="px-6 py-3 bg-sentinel-blue hover:bg-blue-600 rounded-lg transition inline-block"
          >
            Retour à l'accueil
          </a>
        </div>
      </div>
    );
  }

  if (suspiciousActivity) {
    return (
      <div className="min-h-screen bg-zinc-950 flex items-center justify-center p-6">
        <div className="max-w-md w-full bg-red-900/20 border border-red-800 rounded-lg p-8 text-center">
          <div className="text-6xl mb-4">⚠️</div>
          <h2 className="text-2xl font-bold text-red-400 mb-2">
            Activité Suspecte Détectée
          </h2>
          <p className="text-zinc-300 mb-6">
            AuthGuardian a détecté une activité inhabituelle sur votre compte.
            Veuillez réessayer plus tard.
          </p>
          <a
            href="/"
            className="px-6 py-3 bg-zinc-800 hover:bg-zinc-700 border border-zinc-700 rounded-lg transition inline-block"
          >
            Retour à l'accueil
          </a>
        </div>
      </div>
    );
  }

  return <>{children}</>;
}
