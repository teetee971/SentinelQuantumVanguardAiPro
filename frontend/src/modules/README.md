# Sentinel AI Modules

Ce répertoire contient tous les modules IA du système Sentinel Quantum Vanguard AI Pro.

## Architecture des Modules

### 📦 MODULE 7 — Infrastructure & CI/CD
**Automatisation, déploiement et supervision du pipeline Sentinel**

- **InfraGuard** : Supervision de l'infrastructure et des processus CI/CD
- **BuildPilot** : Chef d'orchestre CI/CD
- **FirebaseDeployExecutor** : Déploiement Firebase automatisé
- **CloudflarePropagateWatcher** : Surveillance de la propagation DNS
- **ScriptForge** : Génération et injection automatique de scripts CI/CD
- **ZeroDowntimeSwitcher** : Transitions de déploiement sans interruption

### 🎙️ MODULE 8 — Voix & Communication
**Interaction vocale, sécurité audio et détection de manipulation**

- **SentinelVoiceCore** : Cœur du système vocal Sentinel
- **DeepFakeVoiceDetection** : Détection d'usurpation vocale et d'audio falsifié
- **SentinelChatAssistant** : Interface de communication IA
- **UIEmergencyFallbackAgent** : Agent de secours d'interface utilisateur
- **TouchFeedbackOptimizer** : Optimisation de la réactivité tactile

### 📊 MODULE 9 — Supervision & Monitoring
**Contrôle global, diagnostic et maintenance IA**

- **SentinelConsoleAdmin** : Tableau de bord central du réseau Sentinel
- **LiveConsoleErrorLogger** : Journal IA des erreurs globales
- **AgentLatencyMonitor** : Surveillance de la vitesse de réponse
- **AIRecoveryCommander** : Module de réparation automatique
- **RegressionDetectorAI** : Détection de régressions après mise à jour

### 🔧 MODULE 10 — Modules Autoréparables
**Résilience et maintenance automatique des systèmes Sentinel**

- **ServiceWorkerHealer** : Réparation automatique du Service Worker PWA
- **ManifestRecoveryAgent** : Restauration du manifest.json
- **BrokenLinkMapper** : Scanner et correcteur de liens morts
- **Ghost404Handler** : Redirection intelligente des erreurs 404
- **EmptyStateHealer** : Correction des états vides
- **CDNConsistencyAgent** : Vérification de la cohérence CDN

## Utilisation

### Import des modules

```javascript
// Import de tous les modules
import * as SentinelModules from './modules/index.js';

// Import par catégorie
import * as Infrastructure from './modules/infrastructure/index.js';
import * as Voice from './modules/voice/index.js';
import * as Monitoring from './modules/monitoring/index.js';
import * as SelfHealing from './modules/self-healing/index.js';

// Import d'un module spécifique
import { InfraGuard } from './modules/infrastructure/InfraGuard.js';
```

### Initialisation

```javascript
import { initializeSentinelModules } from './modules/index.js';

const modules = initializeSentinelModules();
```

### Exemple d'utilisation

```javascript
import { InfraGuard } from './modules/infrastructure/InfraGuard.js';

// Créer une instance
const infraGuard = new InfraGuard();

// Valider un build
const validation = await infraGuard.validateBuild('build-123');

// Obtenir le statut
const status = infraGuard.getStatus();
console.log(status);
```

## Console d'Administration

Une interface web complète est disponible pour superviser tous les modules :

**URL** : `/admin/sentinel-console`

La console affiche :
- Statut en temps réel de tous les modules
- Statistiques d'opérations
- Alertes actives
- État du système CI/CD, Cloudflare et Firebase

## Structure des Fichiers

```
modules/
├── infrastructure/
│   ├── InfraGuard.js
│   ├── BuildPilot.js
│   ├── FirebaseDeployExecutor.js
│   ├── CloudflarePropagateWatcher.js
│   ├── ScriptForge.js
│   ├── ZeroDowntimeSwitcher.js
│   └── index.js
├── voice/
│   ├── SentinelVoiceCore.js
│   ├── DeepFakeVoiceDetection.js
│   ├── SentinelChatAssistant.js
│   ├── UIEmergencyFallbackAgent.js
│   ├── TouchFeedbackOptimizer.js
│   └── index.js
├── monitoring/
│   ├── SentinelConsoleAdmin.js
│   ├── LiveConsoleErrorLogger.js
│   ├── AgentLatencyMonitor.js
│   ├── AIRecoveryCommander.js
│   ├── RegressionDetectorAI.js
│   └── index.js
├── self-healing/
│   ├── ServiceWorkerHealer.js
│   ├── ManifestRecoveryAgent.js
│   ├── BrokenLinkMapper.js
│   ├── Ghost404Handler.js
│   ├── EmptyStateHealer.js
│   ├── CDNConsistencyAgent.js
│   └── index.js
├── index.js
└── README.md
```

## Développement

### Ajouter un nouveau module

1. Créer le fichier dans le dossier approprié
2. Implémenter la classe avec les méthodes requises
3. Exporter dans le fichier `index.js` du dossier
4. Mettre à jour le fichier `modules/index.js` principal
5. Ajouter à la console d'administration si nécessaire

### Standards de code

Chaque module doit :
- Avoir un constructeur initialisant le statut
- Implémenter une méthode `getStatus()`
- Documenter les méthodes principales avec JSDoc
- Gérer les erreurs avec try/catch
- Retourner des objets avec timestamps

## Tests

```bash
# Installer les dépendances
cd frontend
npm install

# Lancer les tests (à implémenter)
npm test

# Build
npm run build
```

## Licence

Ce projet fait partie du système Sentinel Quantum Vanguard AI Pro.
