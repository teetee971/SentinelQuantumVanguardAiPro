import { useState, useEffect } from 'react';
import axios from 'axios';

// API base URL - will use environment variable or fallback
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:3333';

/**
 * Hook to fetch and monitor usage data
 * @param {string} userId - User identifier
 * @returns {object} Usage data and loading state
 */
export function useUsageMetering(userId) {
  const [usage, setUsage] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!userId) {
      setLoading(false);
      return;
    }

    const fetchUsage = async () => {
      try {
        const response = await axios.get(`${API_BASE_URL}/api/usage/${userId}`);
        setUsage(response.data);
        setError(null);
      } catch (err) {
        console.error('Error fetching usage:', err);
        setError(err.message);
      } finally {
        setLoading(false);
      }
    };

    fetchUsage();
    // Refresh usage data every 30 seconds
    const interval = setInterval(fetchUsage, 30000);

    return () => clearInterval(interval);
  }, [userId]);

  return { usage, loading, error };
}

/**
 * Hook to check if a specific quota is exceeded
 * @param {object} usage - Current usage data
 * @param {string} plan - User's current plan
 * @param {string} metric - Metric to check (events, api_calls, etc.)
 * @returns {object} Quota status
 */
export function useQuotaCheck(usage, plan, metric) {
  const [quotaStatus, setQuotaStatus] = useState({
    exceeded: false,
    percentage: 0,
    warning: false,
  });

  // Define quotas per plan
  const quotas = {
    freemium: {
      events: 1000,
      stt_minutes: 10,
      tts_minutes: 10,
      api_calls: 500,
      scans: 50,
    },
    starter: {
      events: 10000,
      stt_minutes: 60,
      tts_minutes: 60,
      api_calls: 5000,
      scans: 500,
    },
    pro: {
      events: 50000,
      stt_minutes: 300,
      tts_minutes: 300,
      api_calls: 25000,
      scans: 2500,
    },
    business: {
      events: 500000,
      stt_minutes: 1000,
      tts_minutes: 1000,
      api_calls: 250000,
      scans: 25000,
    },
    enterprise: {
      events: Infinity,
      stt_minutes: Infinity,
      tts_minutes: Infinity,
      api_calls: Infinity,
      scans: Infinity,
    },
  };

  useEffect(() => {
    if (!usage || !plan || !metric) {
      return;
    }

    const quota = quotas[plan]?.[metric];
    const current = usage[metric] || 0;

    if (!quota || quota === Infinity) {
      setQuotaStatus({
        exceeded: false,
        percentage: 0,
        warning: false,
      });
      return;
    }

    const percentage = (current / quota) * 100;
    setQuotaStatus({
      exceeded: current >= quota,
      percentage: Math.min(percentage, 100),
      warning: percentage >= 80,
      remaining: Math.max(quota - current, 0),
      limit: quota,
    });
  }, [usage, plan, metric]);

  return quotaStatus;
}

/**
 * Hook to track usage for a specific action
 * @param {string} userId - User identifier
 * @param {string} metric - Metric to track
 * @returns {function} Function to increment usage
 */
export function useTrackUsage(userId, metric) {
  const trackUsage = async (amount = 1) => {
    if (!userId || !metric) return;

    try {
      await axios.post(`${API_BASE_URL}/api/usage/track`, {
        userId,
        metric,
        amount,
      });
    } catch (err) {
      console.error('Error tracking usage:', err);
    }
  };

  return trackUsage;
}

/**
 * Hook to get next reset date
 * @returns {Date} Next reset date (1st of next month)
 */
export function useResetDate() {
  const [resetDate, setResetDate] = useState(null);

  useEffect(() => {
    const now = new Date();
    const nextMonth = new Date(now.getFullYear(), now.getMonth() + 1, 1);
    setResetDate(nextMonth);
  }, []);

  return resetDate;
}
