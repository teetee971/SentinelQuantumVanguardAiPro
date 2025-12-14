// API Configuration
// For production, set API_BASE_URL via environment variable or build configuration
export const API_BASE_URL = process.env.API_BASE_URL || 'http://localhost:3000';

// Demo data for fallback when API is unavailable
export const DEMO_AGENTS = [
  { id: '1', name: 'Threat Scanner', status: 'idle', description: 'Analyse des menaces en temps réel' },
  { id: '2', name: 'Network Monitor', status: 'running', description: 'Surveillance réseau active' },
  { id: '3', name: 'Firewall AI', status: 'idle', description: 'Protection par intelligence artificielle' },
  { id: '4', name: 'Log Analyzer', status: 'completed', description: 'Analyse des journaux système' },
  { id: '5', name: 'Vulnerability Detector', status: 'idle', description: 'Détection des vulnérabilités' },
  { id: '6', name: 'Intrusion Prevention', status: 'running', description: 'Prévention des intrusions' },
];

export const DEMO_LOGS = [
  {
    id: '1',
    timestamp: new Date().toISOString(),
    level: 'info',
    message: 'Système initialisé avec succès',
    source: 'System'
  },
  {
    id: '2',
    timestamp: new Date(Date.now() - 300000).toISOString(),
    level: 'warning',
    message: 'Tentative de connexion suspecte détectée',
    source: 'Network Monitor'
  },
  {
    id: '3',
    timestamp: new Date(Date.now() - 600000).toISOString(),
    level: 'error',
    message: 'Échec de connexion au serveur API',
    source: 'API Client'
  },
  {
    id: '4',
    timestamp: new Date(Date.now() - 900000).toISOString(),
    level: 'info',
    message: 'Scan de sécurité terminé - Aucune menace détectée',
    source: 'Threat Scanner'
  },
  {
    id: '5',
    timestamp: new Date(Date.now() - 1200000).toISOString(),
    level: 'debug',
    message: 'Mise à jour des règles du pare-feu',
    source: 'Firewall AI'
  },
];
