import React from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { AlertTriangle, TrendingUp, X } from 'lucide-react';

/**
 * UsageWarning Component
 * Displays warnings when users approach or exceed their usage quotas
 */
export default function UsageWarning({ 
  metric, 
  percentage, 
  current, 
  limit, 
  plan,
  onDismiss 
}) {
  const { t } = useTranslation();
  const [dismissed, setDismissed] = React.useState(false);

  if (dismissed) return null;

  const handleDismiss = () => {
    setDismissed(true);
    if (onDismiss) onDismiss();
  };

  // Determine warning level
  const isExceeded = percentage >= 100;
  const isUrgent = percentage >= 90;
  const isWarning = percentage >= 80;

  if (!isWarning && !isUrgent && !isExceeded) return null;

  // Styling based on warning level
  const bgColor = isExceeded 
    ? 'bg-red-900/20 border-red-500/50' 
    : isUrgent 
    ? 'bg-orange-900/20 border-orange-500/50'
    : 'bg-yellow-900/20 border-yellow-500/50';

  const textColor = isExceeded 
    ? 'text-red-400' 
    : isUrgent 
    ? 'text-orange-400'
    : 'text-yellow-400';

  const iconColor = isExceeded 
    ? 'text-red-500' 
    : isUrgent 
    ? 'text-orange-500'
    : 'text-yellow-500';

  return (
    <div className={`relative rounded-lg border ${bgColor} p-4 mb-4`}>
      <button
        onClick={handleDismiss}
        className="absolute top-2 right-2 p-1 hover:bg-white/10 rounded transition"
        aria-label="Dismiss"
      >
        <X size={16} className="text-gray-400" />
      </button>

      <div className="flex items-start gap-3">
        <div className={iconColor}>
          {isExceeded ? <AlertTriangle size={24} /> : <TrendingUp size={24} />}
        </div>

        <div className="flex-1">
          <h3 className={`font-semibold mb-1 ${textColor}`}>
            {isExceeded 
              ? t('usage_metering.quota_exceeded', { metric })
              : t('usage_metering.quota_warning', { percent: percentage.toFixed(0), metric })
            }
          </h3>

          <p className="text-sm text-gray-300 mb-3">
            {isExceeded ? (
              plan === 'freemium' ? (
                <>
                  {t('usage_metering.upgrade_prompt')}
                  {' '}
                  {t('usage_metering.service_suspended')}
                </>
              ) : (
                <>
                  {t('usage_metering.overage_charges')}
                  {' '}
                  <span className="font-semibold">{current - limit}</span> {t('usage_metering.units_over')}
                </>
              )
            ) : (
              <>
                {t('usage_metering.current_usage_detail')} <span className="font-semibold">{current}</span> / {limit}
                {' '}
                ({percentage.toFixed(1)}%)
              </>
            )}
          </p>

          {/* Progress Bar */}
          <div className="w-full h-2 bg-black/40 rounded-full overflow-hidden mb-3">
            <div 
              className={`h-full transition-all duration-300 ${
                isExceeded ? 'bg-red-500' : isUrgent ? 'bg-orange-500' : 'bg-yellow-500'
              }`}
              style={{ width: `${Math.min(percentage, 100)}%` }}
            />
          </div>

          {/* Action Buttons */}
          <div className="flex items-center gap-2">
            <Link
              to="/pricing?utm_source=usage_warning&utm_medium=banner&utm_campaign=quota_upgrade"
              className="inline-flex items-center px-3 py-1.5 rounded bg-sentinel-accent hover:bg-sentinel-accent/90 text-black text-sm font-semibold transition"
            >
              {t('usage_metering.upgrade_now')}
            </Link>
            
            <Link
              to="/dashboard/usage"
              className="inline-flex items-center px-3 py-1.5 rounded border border-white/20 hover:bg-white/10 text-white text-sm transition"
            >
              {t('usage_metering.view_details')}
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}

/**
 * UsageWarningBanner Component
 * Compact banner version for navbar or header
 */
export function UsageWarningBanner({ metrics = [] }) {
  const [dismissed, setDismissed] = React.useState(false);

  if (dismissed || metrics.length === 0) return null;

  // Find the metric with highest percentage
  const criticalMetric = metrics.reduce((max, m) => 
    m.percentage > max.percentage ? m : max
  , metrics[0]);

  const isExceeded = criticalMetric.percentage >= 100;
  const isUrgent = criticalMetric.percentage >= 90;

  if (!isUrgent && !isExceeded) return null;

  return (
    <div className={`w-full py-2 px-4 text-center text-sm ${
      isExceeded ? 'bg-red-900/30' : 'bg-orange-900/30'
    }`}>
      <div className="flex items-center justify-center gap-2 max-w-6xl mx-auto">
        <AlertTriangle size={16} className={isExceeded ? 'text-red-500' : 'text-orange-500'} />
        <span className="text-white">
          {isExceeded 
            ? `Quota exceeded for ${criticalMetric.metric}. ` 
            : `${criticalMetric.percentage.toFixed(0)}% of ${criticalMetric.metric} quota used. `
          }
        </span>
        <Link
          to="/pricing"
          className="underline hover:no-underline text-sentinel-accent font-semibold"
        >
          Upgrade Plan
        </Link>
        <button
          onClick={() => setDismissed(true)}
          className="ml-2 p-1 hover:bg-white/10 rounded"
          aria-label="Dismiss"
        >
          <X size={14} className="text-gray-400" />
        </button>
      </div>
    </div>
  );
}
