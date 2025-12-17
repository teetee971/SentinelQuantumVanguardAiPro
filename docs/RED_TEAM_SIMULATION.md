# Module 1: Red Team Simulator (MITRE ATT&CK)

## Vue d'ensemble

Le **Red Team Simulator** est un module de simulation d'attaques basé sur le framework **MITRE ATT&CK**. Il permet aux équipes SOC, CERT et RSSI de simuler des campagnes d'attaque complètes sans aucun risque, dans un cadre 100% légal et éthique.

## ⚠️ Avertissement Légal

> **Offensive Security Simulation – Aucun accès non autorisé – Usage audit, formation et évaluation uniquement.**

**Ce module**:
- ✅ Simule des attaques (aucune attaque réelle)
- ✅ Génère des événements et IOCs pour formation
- ✅ Utilise le framework MITRE ATT&CK officiel
- ✅ 100% légal et conforme aux standards

**Ce module NE fait PAS**:
- ❌ Aucun exploit réel
- ❌ Aucun accès non autorisé
- ❌ Aucune attaque sur des systèmes tiers
- ❌ Aucun malware fonctionnel

## Caractéristiques

### Moteur de Scénarios

Le moteur permet de créer et exécuter des scénarios d'attaque basés sur MITRE ATT&CK:

- **14 Tactiques** couvertes (Reconnaissance → Impact)
- **30+ Techniques** implémentées (extensible à 193)
- **3 Scénarios pré-configurés**
- **Générateur de scénarios personnalisés**

### Scénarios Inclus

#### 1. Basic Reconnaissance
**Complexité**: Faible  
**Durée**: 30 minutes  
**Détectabilité**: Faible

**Tactiques**:
- Reconnaissance (TA0043)
  - T1595: Active Scanning
  - T1590: Gather Victim Network Information
  - T1591: Gather Victim Org Information

**Usage**: Formation initiale, test de détection passive

---

#### 2. Phishing Campaign Simulation
**Complexité**: Moyenne  
**Durée**: 1 heure  
**Détectabilité**: Moyenne

**Tactiques**:
- Initial Access (TA0001)
  - T1566: Phishing
- Execution (TA0002)
  - T1059: Command and Scripting Interpreter

**Usage**: Test de détection phishing, formation utilisateurs

---

#### 3. APT-style Multi-stage Attack
**Complexité**: Élevée  
**Durée**: 2 heures  
**Détectabilité**: Élevée

**Tactiques Complètes**:
1. Reconnaissance (TA0043)
2. Initial Access (TA0001)
3. Execution (TA0002)
4. Persistence (TA0003)
5. Privilege Escalation (TA0004)
6. Lateral Movement (TA0008)
7. Exfiltration (TA0010)

**Usage**: Formation avancée, audit complet de détection, test de couverture MITRE ATT&CK

---

## Composants

### 1. Simulation Engine (`OffensiveSimulationEngine`)

Orchestrateur principal qui:
- Valide la conformité légale des scénarios
- Séquence les techniques selon la kill chain
- Génère les événements de simulation
- Coordonne IOC Generator et Logging System

**API**:
```javascript
const engine = new OffensiveSimulationEngine();

// Démarrer une simulation
const simulation = await engine.startScenario('scenario-001');

// Obtenir les résultats
const results = engine.getSimulationResults(simulation.id);

// Arrêter une simulation
engine.stopSimulation(simulation.id);
```

### 2. MITRE ATT&CK Library (`MITREAttackLibrary`)

Base de données complète:
- 14 tactiques MITRE ATT&CK
- 30+ techniques implémentées (extensible)
- Métadonnées (plateforme, sévérité, data sources)

**API**:
```javascript
const library = new MITREAttackLibrary();

// Obtenir une technique
const technique = library.getTechnique('T1595');

// Techniques par tactique
const tactics = library.getTechniquesByTactic('TA0043');

// Recherche
const results = library.searchTechniques('phishing');
```

### 3. Scenario Engine (`ScenarioEngine`)

Gestion des scénarios:
- Bibliothèque de scénarios pré-configurés
- Validation de séquences
- Métadonnées (complexité, durée, détectabilité)

**Format de Scénario**:
```javascript
{
  id: "scenario-001",
  name: "Basic Reconnaissance",
  description: "Passive reconnaissance simulation",
  simulated: true,                    // OBLIGATOIRE
  complexity: "low",
  duration: "30 minutes",
  timingMs: 500,                      // Délai entre techniques
  tactics: [
    {
      id: "TA0043",
      name: "Reconnaissance",
      techniques: [
        {
          id: "T1595",
          name: "Active Scanning",
          eventCount: 10,               // Nombre d'événements générés
          severity: "low"
        }
      ]
    }
  ],
  detectability: "low",
  legalCompliance: true               // Validation légale
}
```

### 4. IOC Generator (`IOCGenerator`)

Génère des **Indicateurs de Compromission** réalistes mais fictifs:

**Types d'IOC**:
- **Network**: IP (RFC 5737 TEST-NET), Domains, URLs
- **File**: Hashes (MD5, SHA1, SHA256), Paths
- **Process**: Names, Command-lines, Parent-child
- **Email**: Addresses, Subjects (phishing)
- **Registry**: Keys, Values (Windows simulation)

**Exemple IOC**:
```javascript
{
  id: "ioc-1234567890-0",
  type: "ip",
  value: "203.0.113.42",              // TEST-NET (RFC 5737)
  category: "C2 Communication",
  technique: "T1071",
  confidence: "high",
  timestamp: "2025-12-17T02:30:00Z",
  simulated: true
}
```

### 5. SOC Logging System (`SOCLoggingSystem`)

Génère des logs au format SIEM standard:

**Formats Supportés**:
- **JSON** (structuré)
- **CEF** (Common Event Format)
- **Syslog** (compatible)

**Exemple Log**:
```json
{
  "timestamp": "2025-12-17T02:30:00.000Z",
  "event_id": 4688,
  "source": "Sentinel-Simulation",
  "level": "MEDIUM",
  "technique_id": "T1059",
  "technique_name": "Command and Scripting Interpreter",
  "tactic_id": "TA0002",
  "tactic_name": "Execution",
  "description": "Suspicious command-line execution detected",
  "platform": "Windows",
  "data_source": "Process monitoring",
  "simulated": true,
  "format": "CEF"
}
```

**Export CEF**:
```
CEF:0|Sentinel|Simulation|1.0|4688|Command and Scripting Interpreter|MEDIUM|rt=2025-12-17T02:30:00.000Z techniqueId=T1059 tacticId=TA0002 simulated=true
```

## Interface Utilisateur

### Dashboard Red Team (`red-team-simulator.html`)

**Sections**:

1. **Sélection de Scénario**
   - Liste des scénarios disponibles
   - Métadonnées (complexité, durée, tactiques)
   - Sélection interactive

2. **Statut de Simulation**
   - État en temps réel
   - Progression
   - Alertes

3. **Résultats**
   - Métriques clés (événements, IOCs, tactiques, techniques)
   - Timeline d'attaque
   - Liste des IOCs
   - Couverture MITRE ATT&CK (heatmap)

4. **Export**
   - JSON (données complètes)
   - CSV (événements)
   - CEF (intégration SIEM)

## Métriques Générées

### Métriques de Simulation

| Métrique | Description |
|----------|-------------|
| **Total Events** | Nombre total d'événements générés |
| **Total IOCs** | Nombre d'indicateurs de compromission |
| **Tactics Used** | Nombre de tactiques MITRE employées |
| **Techniques Used** | Nombre de techniques MITRE simulées |
| **Duration** | Durée totale de la simulation (ms) |

### Métriques de Couverture

**Couverture MITRE ATT&CK**:
```
Coverage = (Techniques Détectées / Techniques Simulées) × 100%
```

**Exemple**:
- Techniques simulées: 7
- Techniques détectées: 5
- Couverture: 71.4%

## Cas d'Usage

### 1. Formation SOC

**Objectif**: Entraîner les analystes à détecter des attaques

**Processus**:
1. Lancer un scénario de complexité progressive (low → high)
2. Observer les événements générés
3. Identifier les IOCs
4. Corréler les événements
5. Mesurer le temps de détection (MTTD)

**Résultat**: Analystes formés sur techniques MITRE ATT&CK

---

### 2. Audit de Détection

**Objectif**: Valider la couverture de détection

**Processus**:
1. Sélectionner scénario APT-style (7 tactiques)
2. Exécuter la simulation
3. Vérifier quelles techniques sont détectées
4. Identifier les gaps de visibilité
5. Prioriser les améliorations

**Résultat**: Rapport de couverture MITRE ATT&CK

---

### 3. Génération d'IOCs

**Objectif**: Créer des IOCs pour enrichir CTI

**Processus**:
1. Lancer plusieurs scénarios
2. Collecter tous les IOCs générés
3. Exporter en CSV/JSON
4. Intégrer dans CTI platform

**Résultat**: Base d'IOCs pour training

---

### 4. Test de SIEM

**Objectif**: Valider les règles de corrélation SIEM

**Processus**:
1. Exécuter scénario avec export CEF
2. Importer les logs dans SIEM
3. Vérifier les alertes générées
4. Ajuster les règles de corrélation

**Résultat**: SIEM optimisé

---

## Conformité

### Standards Respectés

- ✅ **MITRE ATT&CK**: Framework officiel
- ✅ **NIST CSF**: Detect, Respond
- ✅ **ANSSI**: Bonnes pratiques cybersécurité
- ✅ **CEF**: Common Event Format (SIEM)

### Garanties Légales

- ✅ Simulations uniquement
- ✅ Aucun code offensif réel
- ✅ Aucun accès non autorisé
- ✅ Usage éducatif et professionnel
- ✅ Open Source auditable

## Extensibilité

### Ajouter une Nouvelle Technique

```javascript
// Dans MITREAttackLibrary.loadTechniques()
{
  id: 'T1234',
  name: 'Ma Nouvelle Technique',
  tactic: 'TA0002',           // Tactic ID
  platform: 'Windows',
  severity: 'high',
  dataSource: 'Process monitoring'
}
```

### Créer un Nouveau Scénario

```javascript
// Dans ScenarioEngine.loadScenarios()
{
  id: 'scenario-custom',
  name: 'Mon Scénario Personnalisé',
  description: '...',
  simulated: true,            // OBLIGATOIRE
  complexity: 'medium',
  duration: '45 minutes',
  timingMs: 1000,
  tactics: [
    {
      id: 'TA0001',
      name: 'Initial Access',
      techniques: [
        { id: 'T1566', name: 'Phishing', eventCount: 10 }
      ]
    }
  ],
  detectability: 'medium',
  legalCompliance: true
}
```

## Dépendances

- **Aucune dépendance externe**
- Vanilla JavaScript (ES6+)
- Compatible navigateurs modernes
- Fonctionne 100% client-side

## Déploiement

### Cloudflare Pages

```bash
# Aucune configuration nécessaire
# Fichiers statiques directement déployables
```

### GitHub Pages

```bash
# Alternative deployment
# Même principe - fichiers statiques
```

## Roadmap Module 1

- [x] Moteur de simulation
- [x] Bibliothèque MITRE ATT&CK (30+ techniques)
- [x] 3 scénarios pré-configurés
- [x] Générateur IOC
- [x] Système logging SOC
- [x] UI Dashboard Red Team
- [x] Export JSON/CSV/CEF
- [ ] API REST (optionnel)
- [ ] Éditeur visuel de scénarios
- [ ] 193 techniques complètes MITRE
- [ ] Intégration STIX/TAXII

---

**Version**: 1.0.0  
**Status**: ✅ Module 1 Complet  
**Date**: 2025-12-17

---

## Prochains Modules

2. SOC Live (Offensif/Défensif)
3. Attack Surface Analyzer
4. Adversary Emulation Engine
5. Compliance ANSSI/NIST/ISO
6. Android APK Agent
7. Sécurité & Traçabilité
