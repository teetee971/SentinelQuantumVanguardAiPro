import { useEffect, useState } from "react";
import { getAuth } from "firebase/auth";

/**
 * TokenAutoRefresher - MODULE 12
 * Gère la validité et le renouvellement automatique des jetons d'accès
 * (API, sessions, authentification)
 */

export function useTokenAutoRefresher(options = {}) {
  const {
    refreshInterval = 50 * 60 * 1000, // 50 minutes par défaut
    expiryThreshold = 5 * 60 * 1000,   // 5 minutes avant expiration
  } = options;

  const [tokenStatus, setTokenStatus] = useState({
    isValid: false,
    expiresAt: null,
    lastRefresh: null,
    refreshCount: 0,
  });

  const [isRefreshing, setIsRefreshing] = useState(false);

  // Token Lifespan Manager
  const checkTokenExpiry = (token) => {
    if (!token) return false;
    
    try {
      // Decode JWT token (simplified)
      const payload = JSON.parse(atob(token.split('.')[1]));
      const expiresAt = payload.exp * 1000;
      const now = Date.now();
      
      return expiresAt - now < expiryThreshold;
    } catch (error) {
      console.error("TokenAutoRefresher: Error checking token expiry", error);
      return true; // Assume expired if can't check
    }
  };

  // Auto Renewal Engine
  const refreshToken = async () => {
    setIsRefreshing(true);
    
    try {
      const auth = getAuth();
      const user = auth.currentUser;

      if (user) {
        // Force refresh the token
        const newToken = await user.getIdToken(true);
        
        setTokenStatus({
          isValid: true,
          expiresAt: Date.now() + 3600000, // 1 hour
          lastRefresh: new Date(),
          refreshCount: tokenStatus.refreshCount + 1,
        });

        console.log("✅ TokenAutoRefresher: Token refreshed successfully");
        return newToken;
      }
    } catch (error) {
      console.error("❌ TokenAutoRefresher: Failed to refresh token", error);
      setTokenStatus((prev) => ({ ...prev, isValid: false }));
    } finally {
      setIsRefreshing(false);
    }
  };

  // Expiry Watchdog
  useEffect(() => {
    const auth = getAuth();
    const user = auth.currentUser;

    if (!user) return;

    // Initial token check
    user.getIdToken().then((token) => {
      const needsRefresh = checkTokenExpiry(token);
      
      if (needsRefresh) {
        refreshToken();
      } else {
        setTokenStatus({
          isValid: true,
          expiresAt: Date.now() + 3600000,
          lastRefresh: new Date(),
          refreshCount: 0,
        });
      }
    });

    // Set up automatic refresh interval
    const intervalId = setInterval(() => {
      refreshToken();
    }, refreshInterval);

    // Session Validator - Check before token expires
    const expiryCheckId = setInterval(() => {
      user.getIdToken().then((token) => {
        const needsRefresh = checkTokenExpiry(token);
        if (needsRefresh) {
          refreshToken();
        }
      });
    }, expiryThreshold / 2);

    return () => {
      clearInterval(intervalId);
      clearInterval(expiryCheckId);
    };
  }, [refreshInterval, expiryThreshold]);

  const forceRefresh = () => {
    return refreshToken();
  };

  const getTokenInfo = () => {
    return {
      ...tokenStatus,
      isRefreshing,
      timeUntilExpiry: tokenStatus.expiresAt
        ? tokenStatus.expiresAt - Date.now()
        : null,
    };
  };

  return {
    tokenStatus,
    isRefreshing,
    forceRefresh,
    getTokenInfo,
  };
}

export default function TokenAutoRefresher({ children, showStatus = false }) {
  const { tokenStatus, isRefreshing, getTokenInfo } = useTokenAutoRefresher();

  if (showStatus) {
    return (
      <div>
        {children}
        <div className="fixed bottom-4 right-4 bg-zinc-900 border border-zinc-800 rounded-lg p-4 text-xs max-w-xs">
          <div className="flex items-center justify-between mb-2">
            <span className="font-semibold text-zinc-300">
              TokenAutoRefresher
            </span>
            {isRefreshing && (
              <div className="w-3 h-3 border-2 border-sentinel-blue border-t-transparent rounded-full animate-spin"></div>
            )}
          </div>
          <div className="space-y-1 text-zinc-500">
            <div className="flex justify-between">
              <span>Status:</span>
              <span
                className={
                  tokenStatus.isValid ? "text-green-400" : "text-red-400"
                }
              >
                {tokenStatus.isValid ? "Valid" : "Invalid"}
              </span>
            </div>
            <div className="flex justify-between">
              <span>Refreshes:</span>
              <span className="text-zinc-300">{tokenStatus.refreshCount}</span>
            </div>
            {tokenStatus.lastRefresh && (
              <div className="flex justify-between">
                <span>Last Refresh:</span>
                <span className="text-zinc-300">
                  {tokenStatus.lastRefresh.toLocaleTimeString()}
                </span>
              </div>
            )}
          </div>
        </div>
      </div>
    );
  }

  return <>{children}</>;
}
