# Offensive Security Simulation Platform - Architecture

## Vue d'ensemble

Sentinel Quantum Vanguard AI Pro est transformé en une **plateforme complète de simulation d'Offensive Security** (Red Team / Adversary Simulation) destinée aux SOC, CERT et RSSI.

## Principes Fondamentaux

### 🔒 Conformité et Légalité

**100% Légal et Éthique**:
- ✅ Aucun exploit réel
- ✅ Aucun accès non autorisé
- ✅ Aucune attaque réelle
- ✅ Simulations contrôlées uniquement
- ✅ Éducatif et professionnel
- ✅ Open Source et auditable

**Standards de Conformité**:
- **MITRE ATT&CK**: Framework de référence pour techniques adversaires
- **NIST CSF**: Cybersecurity Framework (Detect, Respond)
- **ANSSI**: Bonnes pratiques cybersécurité française
- **OWASP**: Sécurité applications web

### 🎯 Objectifs

1. **Formation SOC/CERT**: Entraînement à la détection d'attaques
2. **Test de détection**: Valider la couverture MITRE ATT&CK
3. **Génération d'IOC**: Créer des indicateurs de compromission
4. **Documentation**: Scénarios d'attaque documentés
5. **Mesurable**: Métriques de couverture et détection

## Architecture Globale

```
┌─────────────────────────────────────────────────────────────┐
│                    Frontend (Cloudflare Pages)               │
├─────────────────────────────────────────────────────────────┤
│  • Dashboard Red Team                                        │
│  • MITRE ATT&CK Matrix Interactive                          │
│  • Campaign Builder                                          │
│  • SOC Logs Viewer                                          │
│  • Scenario Library                                          │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│            Simulation Engine (Client-side JS)                │
├─────────────────────────────────────────────────────────────┤
│  ┌───────────────────────────────────────────────────────┐  │
│  │ Scenario Generator                                     │  │
│  │  • MITRE Techniques Library (14 tactiques, 193 techniques)│
│  │  • Attack Chain Orchestrator                          │  │
│  │  • Timing & Sequencing Engine                         │  │
│  └───────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ Detection Simulation                                   │  │
│  │  • IOC Generator (IP, Hash, Domain, Registry, etc.)  │  │
│  │  • Behavioral Pattern Generator                       │  │
│  │  • Alert Correlator                                   │  │
│  └───────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ Logging System                                         │  │
│  │  • Event Generator (SIEM format)                      │  │
│  │  • Timestamp Management                               │  │
│  │  • Audit Trail                                        │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│              Data Sources (Publiques)                        │
├─────────────────────────────────────────────────────────────┤
│  • MITRE ATT&CK Matrix (JSON officiel)                     │
│  • CVE/NVD Database                                         │
│  • GitHub Security Advisories                              │
│  • Threat Intel OSINT                                       │
└─────────────────────────────────────────────────────────────┘
```

## Modules Principaux

### 1. Simulation Engine

**Responsabilité**: Générer des scénarios d'attaque simulés conformes MITRE ATT&CK

**Composants**:
- **Scenario Generator**: Crée des campagnes d'attaque multi-étapes
- **Technique Library**: Base de données de 193 techniques MITRE
- **Orchestrator**: Séquence les actions selon kill chain
- **Validator**: Vérifie conformité légale et faisabilité

**Format de Scénario**:
```javascript
{
  id: "scenario-001",
  name: "APT28-style Campaign",
  description: "Simulation d'une campagne APT avec reconnaissance → accès initial → persistance",
  tactics: [
    {
      id: "TA0043",
      name: "Reconnaissance",
      techniques: [
        {
          id: "T1595",
          name: "Active Scanning",
          simulated: true,
          ioc: ["scan-pattern-1.json"],
          logs: ["event-001.log"]
        }
      ]
    }
  ],
  duration: "2 hours",
  complexity: "medium",
  detectability: "high"
}
```

### 2. MITRE ATT&CK Integration

**14 Tactiques Couvertes**:
1. **Reconnaissance** (TA0043) - 10 techniques
2. **Resource Development** (TA0042) - 7 techniques
3. **Initial Access** (TA0001) - 9 techniques
4. **Execution** (TA0002) - 12 techniques
5. **Persistence** (TA0003) - 19 techniques
6. **Privilege Escalation** (TA0004) - 13 techniques
7. **Defense Evasion** (TA0005) - 42 techniques
8. **Credential Access** (TA0006) - 17 techniques
9. **Discovery** (TA0007) - 29 techniques
10. **Lateral Movement** (TA0008) - 9 techniques
11. **Collection** (TA0009) - 17 techniques
12. **Command and Control** (TA0011) - 16 techniques
13. **Exfiltration** (TA0010) - 9 techniques
14. **Impact** (TA0040) - 13 techniques

**Total**: 193 techniques simulables

### 3. IOC Generator

**Types d'IOC Générés**:
- **Network**: IP, Domain, URL, User-Agent
- **File**: Hash (MD5, SHA1, SHA256), Path, Name
- **Registry**: Keys, Values (Windows simulation)
- **Process**: Name, Command-line, Parent-child
- **Behavioral**: Patterns, Anomalies, Sequences

**Format IOC**:
```javascript
{
  type: "network",
  indicator: "203.0.113.42",
  category: "C2 Communication",
  technique: "T1071.001",
  confidence: "high",
  context: "Simulated beacon to C2 server",
  timestamp: "2025-12-17T02:30:00Z"
}
```

### 4. SOC Logging System

**Formats Supportés**:
- **CEF** (Common Event Format)
- **JSON** (SIEM standard)
- **Syslog**
- **Windows Event Log** (simulé)

**Exemple Log**:
```json
{
  "timestamp": "2025-12-17T02:30:00.000Z",
  "event_id": "4688",
  "source": "Sentinel-Simulation",
  "severity": "medium",
  "technique": "T1059.001",
  "tactic": "Execution",
  "description": "PowerShell execution detected",
  "process": "powershell.exe",
  "command_line": "powershell.exe -enc <base64>",
  "parent_process": "explorer.exe",
  "user": "DOMAIN\\user",
  "simulated": true
}
```

## Scénarios Pré-configurés

### Scénario 1: Reconnaissance Passive
**Techniques**:
- T1595.001: Active Scanning - Scanning IP Blocks
- T1590: Gather Victim Network Information
- T1591: Gather Victim Org Information

**Durée**: 30 minutes  
**Logs générés**: 50-100 événements  
**Détectabilité**: Faible

### Scénario 2: Phishing Campaign
**Techniques**:
- T1566.001: Phishing - Spearphishing Attachment
- T1204.002: User Execution - Malicious File
- T1059.001: Command and Scripting Interpreter - PowerShell

**Durée**: 1 heure  
**Logs générés**: 200-300 événements  
**Détectabilité**: Moyenne

### Scénario 3: Lateral Movement
**Techniques**:
- T1021.001: Remote Services - Remote Desktop Protocol
- T1550.002: Use Alternate Authentication Material - Pass the Hash
- T1078: Valid Accounts

**Durée**: 2 heures  
**Logs générés**: 500+ événements  
**Détectabilité**: Haute

## Dashboard Red Team

### Composants UI

1. **Campaign Dashboard**
   - Campagnes actives
   - Progression kill chain
   - Métriques en temps réel

2. **MITRE ATT&CK Heatmap**
   - Couverture des techniques
   - Fréquence de détection
   - Gaps de visibilité

3. **Timeline Viewer**
   - Séquence d'événements
   - Corrélation parent-child
   - Graphe de kill chain

4. **Metrics & Analytics**
   - Taux de détection
   - MTTD (Mean Time To Detect)
   - Couverture tactique

5. **Scenario Builder**
   - Éditeur visuel
   - Bibliothèque de techniques
   - Validation de séquence

## Métriques de Performance

### Métriques Générées

**Couverture MITRE ATT&CK**:
```
Couverture = (Techniques Détectées / Techniques Simulées) × 100%
```

**Mean Time To Detect (MTTD)**:
```
MTTD = Σ(Temps_Détection_i) / Nombre_Événements
```

**Detection Rate**:
```
Detection_Rate = (Alertes_Correctes / Total_Événements) × 100%
```

**False Positive Rate**:
```
FPR = (Fausses_Alertes / Total_Alertes) × 100%
```

## Sécurité et Limitations

### ✅ Ce que fait la plateforme

- Génère des scénarios d'attaque **simulés**
- Crée des logs **réalistes** pour entraînement SOC
- Produit des IOC **fictifs** mais réalistes
- Documente les techniques MITRE ATT&CK
- Mesure la couverture de détection

### ❌ Ce que la plateforme NE fait PAS

- ❌ Aucun exploit réel
- ❌ Aucune connexion réseau non autorisée
- ❌ Aucune modification de systèmes tiers
- ❌ Aucun malware fonctionnel
- ❌ Aucune attaque réelle

### 🛡️ Garanties de Sécurité

1. **Isolation**: Tout s'exécute en local (browser)
2. **Simulation uniquement**: Pas d'actions réseau réelles
3. **Open Source**: Code auditable publiquement
4. **Traçabilité**: Chaque action loggée
5. **Disclaimer**: Avertissement d'usage éthique

## Documentation Requise

### Documents à Créer

1. **Architecture Technique** (`docs/OFFENSIVE_ARCHITECTURE.md`)
2. **Guide Utilisateur SOC** (`docs/SOC_USER_GUIDE.md`)
3. **Bibliothèque de Scénarios** (`docs/SCENARIO_LIBRARY.md`)
4. **Mapping MITRE ATT&CK** (`docs/MITRE_MAPPING.md`)
5. **Legal Disclaimer** (`docs/LEGAL_DISCLAIMER.md`)
6. **Compliance Report** (`docs/COMPLIANCE.md`)

## Déploiement

### Stack Technique

**Frontend**:
- HTML5 / CSS3
- Vanilla JavaScript (ES6+)
- IndexedDB (stockage local)
- Web Workers (calculs lourds)

**Data**:
- MITRE ATT&CK JSON (officiel)
- Local Storage / IndexedDB
- Pas de backend requis

**Hosting**:
- Cloudflare Pages
- CDN global
- Edge computing

### Compatibilité

- ✅ Navigateurs modernes (Chrome, Firefox, Edge, Safari)
- ✅ Cloudflare Pages
- ✅ GitHub Pages (alternative)
- ✅ Pas de backend requis
- ✅ 100% client-side

## Roadmap de Développement

### Phase 1: Fondations (Semaine 1)
- [x] Architecture documentée
- [ ] Moteur de simulation de base
- [ ] Bibliothèque MITRE ATT&CK
- [ ] Premier scénario (Reconnaissance)

### Phase 2: Modules Core (Semaine 2)
- [ ] 5 scénarios pré-configurés
- [ ] IOC Generator
- [ ] Logging System
- [ ] Dashboard basique

### Phase 3: UI Avancée (Semaine 3)
- [ ] MITRE ATT&CK Heatmap
- [ ] Scenario Builder
- [ ] Timeline Viewer
- [ ] Metrics Dashboard

### Phase 4: Documentation (Semaine 4)
- [ ] 6 documents techniques
- [ ] API documentation
- [ ] Guides utilisateur
- [ ] Legal compliance

### Phase 5: Validation (Semaine 5)
- [ ] Tests de conformité
- [ ] Audit de sécurité
- [ ] Validation MITRE
- [ ] Release v1.0

---

**Version**: 1.0.0-alpha  
**Date**: 2025-12-17  
**Status**: 🚧 En développement
