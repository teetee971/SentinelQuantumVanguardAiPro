/**
 * Utilities communes pour le module téléphone
 */

/**
 * Obtenir la couleur associée à un niveau de risque
 */
export const getRiskColor = (level: string): string => {
  switch (level) {
    case 'CRITICAL': return '#dc2626';
    case 'HIGH': return '#ea580c';
    case 'MEDIUM': return '#f59e0b';
    case 'LOW': return '#84cc16';
    case 'SAFE': return '#10b981';
    default: return '#6b7280';
  }
};

/**
 * Obtenir le label en français pour un niveau de risque
 */
export const getRiskLabel = (level: string): string => {
  switch (level) {
    case 'CRITICAL': return 'CRITIQUE';
    case 'HIGH': return 'ÉLEVÉ';
    case 'MEDIUM': return 'MOYEN';
    case 'LOW': return 'FAIBLE';
    case 'SAFE': return 'SÛR';
    default: return 'INCONNU';
  }
};

/**
 * Obtenir l'icône pour un niveau de risque
 */
export const getRiskIcon = (level: string): string => {
  switch (level) {
    case 'CRITICAL': return '🚨';
    case 'HIGH': return '⚠️';
    case 'MEDIUM': return '⚡';
    case 'LOW': return '✓';
    case 'SAFE': return '✅';
    default: return '❓';
  }
};

/**
 * Formater une durée en secondes vers un format lisible
 */
export const formatDuration = (seconds: number): string => {
  if (seconds === 0) return '0s';
  const min = Math.floor(seconds / 60);
  const sec = seconds % 60;
  return min > 0 ? `${min}m ${sec}s` : `${sec}s`;
};

/**
 * Formater un timestamp vers un format relatif (Il y a X min/h/j)
 */
export const formatTimestamp = (timestamp: number): string => {
  const date = new Date(timestamp);
  const now = new Date();
  const diff = now.getTime() - date.getTime();
  
  if (diff < 3600000) {
    return `Il y a ${Math.floor(diff / 60000)} min`;
  } else if (diff < 86400000) {
    return `Il y a ${Math.floor(diff / 3600000)}h`;
  } else {
    return date.toLocaleDateString('fr-FR', { 
      day: '2-digit', 
      month: '2-digit', 
      hour: '2-digit', 
      minute: '2-digit' 
    });
  }
};

/**
 * Obtenir le label en français pour un type de numéro
 */
export const getNumberTypeLabel = (type: string): string => {
  switch (type) {
    case 'mobile': return 'Mobile';
    case 'fixe': return 'Fixe';
    case 'gratuit': return 'Gratuit';
    case 'surtaxé': return 'Surtaxé';
    case 'voip': return 'VoIP';
    default: return 'Inconnu';
  }
};

/**
 * Obtenir l'icône pour une action d'appel
 */
export const getActionIcon = (action: string): string => {
  switch (action) {
    case 'ANSWERED': return '📞';
    case 'BLOCKED': return '🚫';
    case 'AI_ANSWERED': return '🤖';
    case 'MISSED': return '📵';
    case 'REJECTED': return '⛔';
    default: return '❓';
  }
};

/**
 * Obtenir le label pour une action d'appel
 */
export const getActionLabel = (action: string): string => {
  switch (action) {
    case 'ANSWERED': return 'Répondu';
    case 'BLOCKED': return 'Bloqué';
    case 'AI_ANSWERED': return 'IA';
    case 'MISSED': return 'Manqué';
    case 'REJECTED': return 'Rejeté';
    default: return 'Inconnu';
  }
};
