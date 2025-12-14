# Phase B+ Sprint 1 - ThreatScore & Explainability

**Version:** 2.1.0 Sprint 1  
**Status:** IMPLEMENTED  
**Date:** December 2024

---

## 🎯 Sprint 1 Objectifs

Implémenter le système de scoring intelligent et l'explicabilité des décisions tel que défini dans PHASE_B_PLUS_PROPOSAL.md.

---

## ✅ Fonctionnalités Implémentées

### 1. ThreatScore System (`ThreatScore`)

**Fichier:** `/android-app/src/modules/phone/PhoneModuleEnhanced.ts`

Score de menace intelligent de 0 à 100 avec décomposition détaillée :

```typescript
interface ThreatScore {
  overall: number;           // Score global 0-100
  breakdown: {
    frequency: number;       // 0-20 pts - Fréquence d'appels
    timing: number;          // 0-15 pts - Horaires inhabituels
    duration: number;        // 0-25 pts - Durée typique
    pattern: number;         // 0-20 pts - Motifs d'appels
    source: number;          // 0-20 pts - Origine géographique
  };
  riskLevel: 'SAFE' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  explanation: string;
  recommendations: string[];
}
```

**Algorithme de scoring :**

- **Frequency (0-20 pts)** : Basé sur appels/jour
  - > 5 appels/jour = 20 pts (très suspect)
  - 3-5 appels/jour = 15 pts (suspect)
  - 1-3 appels/jour = 10 pts (modéré)
  - 0.5-1 appel/jour = 5 pts (léger)

- **Timing (0-15 pts)** : Appels nocturnes (< 8h ou > 22h)
  - > 50% nocturnes = 15 pts
  - 30-50% nocturnes = 10 pts
  - 10-30% nocturnes = 5 pts

- **Duration (0-25 pts)** : Durée moyenne des appels
  - < 3 secondes = 25 pts (robocall classique)
  - 3-10 secondes = 20 pts (très suspect)
  - 10-30 secondes = 10 pts (suspect)
  - 30-60 secondes = 5 pts (modéré)

- **Pattern (0-20 pts)** : Comportement utilisateur
  - > 50% signalé = 20 pts
  - > 70% bloqué = 18 pts
  - > 80% ignoré = 15 pts
  - > 50% bloqué = 12 pts

- **Source (0-20 pts)** : Analyse origine numéro
  - Numéro invalide/spoofé = 15-20 pts
  - Numéros premium = 5 pts
  - Régions à risque = 5 pts

**Niveaux de risque :**
- CRITICAL: ≥ 80 pts
- HIGH: 60-79 pts
- MEDIUM: 40-59 pts
- LOW: 20-39 pts
- SAFE: < 20 pts

### 2. CallMemory - Base de Données Locale

**Fichier:** `/android-app/src/modules/phone/PhoneModuleEnhanced.ts`

Système de mémoire locale pour apprentissage progressif :

```typescript
interface CallMemory {
  number: string;
  firstSeen: number;
  lastSeen: number;
  totalCalls: number;
  userActions: {
    blocked: number;
    answered: number;
    ignored: number;
    reported: number;
  };
  userNotes?: string;
  communityScore?: number;
  tags: string[];
  averageDuration: number;
  callTimes: number[];
}
```

**Fonctionnalités :**
- Historique complet par numéro
- Tracking actions utilisateur
- Calcul durée moyenne automatique
- Tags automatiques (spam, démarchage)
- Notes personnalisées
- Stockage des heures d'appels (100 derniers)

**Méthodes :**
- `updateCallMemory()` - Met à jour après chaque appel
- `getCallMemory()` - Récupère historique
- `addCallNote()` - Ajoute note personnelle
- `addCallTag()` - Ajoute tag manuel

### 3. CallDecisionExplanation - IA Explicable

**Fichier:** `/android-app/src/modules/phone/PhoneModuleEnhanced.ts`

Chaque décision vient avec explication complète :

```typescript
interface CallDecisionExplanation {
  decision: 'ALLOW' | 'BLOCK' | 'WARN';
  confidence: number;
  factors: DecisionFactor[];
  reasoning: string;
  alternativeAction?: string;
}

interface DecisionFactor {
  name: string;
  weight: number;            // -50 à +50 pts
  value: string;
  impact: 'POSITIVE' | 'NEGATIVE' | 'NEUTRAL';
  explanation: string;
}
```

**Facteurs analysés :**

1. **Historique de blocage** (-30 pts si > 70% bloqué)
2. **Historique de réponses** (+25 pts si > 5 réponses)
3. **Durée moyenne** (-25 pts si < 10s, +15 pts si > 60s)
4. **Fréquence** (-20 pts si > 3 appels/jour)
5. **Origine** (-10 à -20 pts si suspect)
6. **Signalements** (-35 pts si signalé spam)
7. **Profil de protection** (ajustement selon profil)

**Logique de décision :**
- Score ≤ -50 → BLOCK (confiance 70-95%)
- Score ≤ -20 → WARN (confiance 60-85%)
- Score > -20 → ALLOW (confiance 50-90%)

**Exemple d'explication générée :**
```
Décision : BLOQUER
Confiance : 85%

Facteurs analysés :
✗ Historique de blocage : 85% bloqué (-30 pts)
   Vous avez fréquemment bloqué ce numéro
✗ Durée moyenne : 7s (-25 pts)
   Appels très courts typiques de robocalls
✗ Fréquence d'appels : 4.2 appels/jour (-20 pts)
   Fréquence inhabituelle pour numéro légitime
✗ Signalements spam : 2 signalement(s) (-35 pts)
   Vous avez signalé ce numéro comme spam

Profil de protection : BALANCED
```

### 4. Protection Profiles

**Fichier:** `/android-app/src/modules/phone/PhoneModuleEnhanced.ts`

5 profils de protection prédéfinis :

```typescript
enum ProtectionProfile {
  MINIMAL = 'minimal',       // +20 pts (permissif)
  BALANCED = 'balanced',     // 0 pts (équilibré)
  AGGRESSIVE = 'aggressive', // -15 pts (restrictif)
  PARANOID = 'paranoid',     // -30 pts (très restrictif)
  CUSTOM = 'custom'          // 0 pts (manuel)
}
```

**Impact sur décisions :**
- **MINIMAL** : Alertes uniquement, aucun blocage auto
- **BALANCED** : Équilibre entre protection et accessibilité
- **AGGRESSIVE** : Bloque agressivement les inconnus suspects
- **PARANOID** : Whitelist uniquement - tout bloquer par défaut
- **CUSTOM** : Configuration manuelle fine grain

### 5. Activity Timeline

**Fichier:** `/android-app/src/modules/phone/PhoneModuleEnhanced.ts`

Timeline des activités suspectes :

```typescript
interface TimelineEntry {
  id: string;
  timestamp: number;
  event: string;
  severity: 'LOW' | 'MEDIUM' | 'HIGH';
  details: string;
  automated: boolean;
  number?: string;
  action?: string;
}
```

**Fonctionnalités :**
- Enregistrement chronologique événements
- Filtrage par sévérité
- 1000 dernières entrées conservées
- Nettoyage automatique des entrées anciennes
- Marquage actions auto vs manuelles

**Méthodes :**
- `addTimelineEntry()` - Ajoute événement
- `getTimeline()` - Récupère timeline
- `getTimelineBySeverity()` - Filtre par sévérité
- `clearOldTimeline()` - Nettoie anciennes entrées

### 6. Statistics & Insights

**Fichier:** `/android-app/src/modules/phone/PhoneModuleEnhanced.ts`

Statistiques globales sur l'activité :

```typescript
getCallStatistics(): {
  totalNumbers: number;
  totalCalls: number;
  spamCalls: number;
  blockedCalls: number;
  answeredCalls: number;
  averageThreatScore: number;
}
```

**Métriques calculées :**
- Nombre total de numéros uniques
- Nombre total d'appels
- Appels spam détectés
- Appels bloqués par utilisateur
- Appels répondus
- Score de menace moyen global

---

## 🔧 Feature Flags Sprint 1

**Fichier:** `/android-app/src/config/featureFlags.ts`

5 nouveaux flags ajoutés (tous OFF par défaut) :

```typescript
// Phase B+ Sprint 1 - ALL OFF by default
PHONE_THREAT_SCORING: false,
PHONE_CALL_MEMORY: false,
PHONE_EXPLAINABLE_DECISIONS: false,
PHONE_ACTIVITY_TIMELINE: false,
PHONE_PROTECTION_PROFILES: false,
```

**Activation progressive recommandée :**
1. `PHONE_CALL_MEMORY` - Base de données locale
2. `PHONE_THREAT_SCORING` - Scoring (dépend de CallMemory)
3. `PHONE_EXPLAINABLE_DECISIONS` - Décisions (dépend de scoring)
4. `PHONE_PROTECTION_PROFILES` - Profils utilisateur
5. `PHONE_ACTIVITY_TIMELINE` - Timeline

---

## 📐 Architecture

### Séparation des Responsabilités

- **PhoneModule.ts** (existant) : Framework permissions, accès natif
- **PhoneModuleEnhanced.ts** (nouveau) : Logique intelligence locale
- **featureFlags.ts** : Contrôle activation granulaire

### Pas de Modifications Structurelles

✅ Aucune modification des fichiers existants  
✅ Extension par nouveau module  
✅ Compatible avec Phase B existante  
✅ Activation via feature flags uniquement

---

## 🔒 Conformité & Transparence

### Traitement 100% Local

✅ Tous les calculs en local  
✅ Aucune donnée envoyée au cloud  
✅ Aucun ML externe  
✅ Base de données locale (Map)  
✅ Heuristiques simples et explicables

### Google Play Compliance

✅ Pas de collecte de données  
✅ Pas de tracking utilisateur  
✅ Transparence totale  
✅ Explications claires  
✅ Contrôle utilisateur complet

### Légalement Irréprochable

✅ Aucune surveillance  
✅ Aucune interception  
✅ Analyse locale uniquement  
✅ Consentement utilisateur  
✅ Données personnelles seulement

---

## 🧪 Utilisation

### Exemple 1 : Calculer ThreatScore

```typescript
import { phoneModuleEnhanced } from './modules/phone/PhoneModuleEnhanced';

const number = '+33123456789';
const callHistory = phoneModuleEnhanced.getCallMemory(number);
const threatScore = phoneModuleEnhanced.calculateThreatScore(number, callHistory);

console.log(`Threat Level: ${threatScore.riskLevel}`);
console.log(`Score: ${threatScore.overall}/100`);
console.log(`Explanation: ${threatScore.explanation}`);
console.log(`Recommendations:`);
threatScore.recommendations.forEach(r => console.log(`- ${r}`));
```

### Exemple 2 : Prendre Décision Expliquée

```typescript
const decision = phoneModuleEnhanced.makeCallDecision(number, callHistory);

console.log(`Decision: ${decision.decision}`);
console.log(`Confidence: ${decision.confidence}%`);
console.log(`\nReasoning:\n${decision.reasoning}`);

if (decision.alternativeAction) {
  console.log(`\nAlternative: ${decision.alternativeAction}`);
}
```

### Exemple 3 : Mettre à Jour Mémoire

```typescript
// Après un appel
phoneModuleEnhanced.updateCallMemory(
  '+33123456789',
  15, // duration en secondes
  'blocked' // action: answered | blocked | ignored | reported
);

// Ajouter note
phoneModuleEnhanced.addCallNote(
  '+33123456789',
  'Démarchage EDF - bloquer'
);

// Ajouter tag
phoneModuleEnhanced.addCallTag('+33123456789', 'démarchage');
```

### Exemple 4 : Gérer Timeline

```typescript
phoneModuleEnhanced.addTimelineEntry(
  'Spam call blocked',
  'HIGH',
  'Robocall detected and blocked automatically',
  true, // automated
  '+33123456789',
  'BLOCKED'
);

const timeline = phoneModuleEnhanced.getTimeline(10); // 10 derniers
const highSeverity = phoneModuleEnhanced.getTimelineBySeverity('HIGH');
```

### Exemple 5 : Changer Profil Protection

```typescript
import { ProtectionProfile } from './modules/phone/PhoneModuleEnhanced';

phoneModuleEnhanced.setProtectionProfile(ProtectionProfile.AGGRESSIVE);
const current = phoneModuleEnhanced.getProtectionProfile();
```

### Exemple 6 : Statistiques

```typescript
const stats = phoneModuleEnhanced.getCallStatistics();

console.log(`Total numbers: ${stats.totalNumbers}`);
console.log(`Total calls: ${stats.totalCalls}`);
console.log(`Spam calls: ${stats.spamCalls}`);
console.log(`Average threat: ${stats.averageThreatScore.toFixed(1)}/100`);
```

---

## 🎨 Intégration UI (À venir)

Propositions pour Sprint 2 UI :

### Écran ThreatScore
- Gauge circulaire 0-100
- Breakdown en barres colorées
- Liste recommandations avec icônes
- Badge niveau de risque

### Écran Decision Explanation
- Carte décision avec confiance
- Liste facteurs avec poids
- Timeline facteurs (positif/négatif)
- Action alternative suggérée

### Écran Timeline
- Liste chronologique
- Filtres par sévérité
- Badges automatique/manuel
- Détails expandables

### Écran Statistics
- Cartes métriques
- Graphiques évolution
- Top numéros suspects
- Tendances hebdomadaires

---

## 📊 Métriques de Succès Sprint 1

### Objectifs
- ✅ Système de scoring implémenté
- ✅ Base de données mémoire locale
- ✅ Décisions expliquées
- ✅ Profils de protection
- ✅ Timeline d'activité
- ✅ Statistiques globales

### KPIs
- Précision scoring : À tester avec données réelles
- Taux de faux positifs : Objectif < 5%
- Satisfaction explications : À mesurer avec users
- Performance : < 50ms pour calcul score
- Mémoire : < 1MB pour 1000 numéros

---

## 🚀 Prochaines Étapes

### Sprint 2 : UI & UX
- [ ] Écrans détaillés pour chaque feature
- [ ] Visualisations graphiques
- [ ] Notifications intelligentes
- [ ] Onboarding profils protection

### Sprint 3 : Optimisations
- [ ] Cache résultats scoring
- [ ] Indexation mémoire par tags
- [ ] Export/import données
- [ ] Backup automatique

### Sprint 4 : Advanced Features
- [ ] Corrélation patterns
- [ ] Détection campagnes spam
- [ ] Recommandations proactives
- [ ] Rapports hebdomadaires

---

## 📝 Notes Techniques

### Stockage
- Map en mémoire (session)
- Pour production : AsyncStorage ou SQLite
- Chiffrement si données sensibles
- Backup cloud optionnel (opt-in)

### Performance
- Calculs légers (heuristiques simples)
- Pas de ML lourd
- Cache si nécessaire
- Lazy loading timeline

### Tests
- Unit tests pour scoring
- Tests décisions avec cas limites
- Tests mémoire (leaks)
- Tests performance

---

**Sprint 1 COMPLET**  
**Phase B+ ThreatScore & Explainability ✅**  
**Prêt pour intégration UI**

🛡️ Sentinel Quantum Vanguard AI Pro - Phase B+ Sprint 1
