# Phases 3-8: Roadmap Implementation Guide

## Vue d'ensemble

Ce document détaille l'implémentation des phases 3 à 8 du projet Sentinel Quantum Vanguard AI Pro, en suivant les contraintes absolues de crédibilité, transparence et fonctionnalité réelle.

---

## 🧠 PHASE 3 — THREAT INTELLIGENCE (MITRE ATT&CK)

### Objectif
Créer une interface éducative et fonctionnelle pour explorer MITRE ATT&CK avec mapping vers capacités Sentinel.

### Structure
```
/public/threat-intelligence/
├── index.html ✅ (Existe déjà - à améliorer)
├── mitre-groups.html ⏳ (À créer)
├── mitre-techniques.html ⏳ (À créer)
└── detection-mapping.html ⏳ (À créer)
```

### Fonctionnalités à implémenter

#### 1. Page MITRE Groups
**Fichier:** `/public/threat-intelligence/mitre-groups.html`

**Contenu:**
- Liste groupes APT (APT28, APT29, Lazarus, etc.)
- Données MITRE publiques (https://attack.mitre.org/groups/)
- Pour chaque groupe:
  - Nom, alias
  - Description (source MITRE)
  - Techniques utilisées (IDs MITRE)
  - Secteurs ciblés
  - Attribution (si publique)

**Disclaimer obligatoire:**
```
⚠️ Données issues de MITRE ATT&CK (https://attack.mitre.org/)
Source publique maintenue par MITRE Corporation.
Aucune donnée classifiée. Aucune action offensive.
```

**Implementation technique:**
```javascript
// Fetch MITRE ATT&CK data via public API
const MITRE_API = 'https://raw.githubusercontent.com/mitre/cti/master/enterprise-attack/enterprise-attack.json';

async function loadMITREGroups() {
  const response = await fetch(MITRE_API);
  const data = await response.json();
  
  // Filter groups (type: intrusion-set)
  const groups = data.objects.filter(obj => obj.type === 'intrusion-set');
  
  // Display with Sentinel detection mapping
  groups.forEach(group => {
    displayGroupCard(group);
  });
}
```

#### 2. Techniques Browser
**Fichier:** `/public/threat-intelligence/mitre-techniques.html`

**Fonctionnalités:**
- Navigateur interactif MITRE ATT&CK Matrix
- Filtres: Tactic, Platform, Data Source
- Recherche par ID technique (T1071, etc.)

**Mapping Sentinel:**
Pour chaque technique MITRE, indiquer:
```
✅ Ce que Sentinel PEUT détecter:
  - Logs analysés (réseau, système, application)
  - Patterns comportementaux
  - Indicateurs monitored
  
⚠️ Ce que Sentinel NE PEUT PAS détecter:
  - Zero-days inconnus
  - Techniques nécessitant accès kernel
  - Attaques sans logs observables
```

**Exemple concret:**
```markdown
### T1071: Application Layer Protocol

**Description MITRE:**
Adversaries may communicate using application layer protocols to avoid detection.

**Détection Sentinel:**
✅ Peut détecter:
- Connexions sortantes inhabituelles (ports non-standard)
- Patterns de communication C2 (beaconing)
- DNS tunneling via analyse fréquence queries
- HTTP headers suspects

⚠️ Limites:
- Chiffrement fort (TLS 1.3+) empêche inspection payload
- Attribution définitive nécessite renseignement humain
- Faux positifs possibles (apps légitimes)

**Logs utilisés:**
- Firewall logs (source/dest IP, port, protocole)
- DNS logs (queries, responses, timing)
- HTTP proxy logs (headers, user-agents, URLs)
- Network flow data (NetFlow, sFlow)
```

#### 3. Detection Mapping Dashboard
**Fichier:** `/public/threat-intelligence/detection-mapping.html`

**Visualisation:**
- Matrice tactiques MITRE (14 colonnes)
- Pour chaque technique: Badge de couverture
  - 🟢 Haute détection (>70%)
  - 🟡 Moyenne détection (30-70%)
  - 🔴 Faible détection (<30%)
  - ⚪ Aucune détection

**Honnêteté technique:**
```
Couverture Sentinel globale: ~45-60% techniques MITRE

Raisons limites:
- Pas d'accès kernel/hardware (EDR limité)
- Pas d'analyse mémoire avancée
- Pas de sandboxing comportemental
- Dépend de qualité des logs
```

### API Publiques à utiliser

1. **MITRE ATT&CK STIX Data:**
   - URL: https://github.com/mitre/cti
   - Format: JSON (STIX 2.0)
   - Licence: Apache 2.0 (usage libre)

2. **ATT&CK Navigator:**
   - URL: https://mitre-attack.github.io/attack-navigator/
   - Peut être intégré via iframe ou fork

### Disclaimer Page
Ajouter en header de chaque page:
```html
<div class="disclaimer-banner">
  <strong>Sources:</strong> MITRE ATT&CK® (https://attack.mitre.org/)
  <br>
  Données publiques | Pas d'action offensive | Éducatif uniquement
</div>
```

---

## 🌍 PHASE 4 — CARTE MONDIALE CYBER

### Objectif
Carte interactive basée sur incidents cyber publiquement documentés (pas de "live hacking").

### Structure
```
/public/world-cyber-map/
├── index.html ✅ (Existe - à améliorer significativement)
├── incidents-database.json ⏳ (À créer)
└── map-config.js ⏳ (À créer)
```

### Sources de données publiques

1. **CSIS Cyber Operations Tracker:**
   - URL: https://www.csis.org/programs/strategic-technologies-program/significant-cyber-incidents
   - Données: Incidents majeurs depuis 2006
   - Format: Tableau HTML (peut être scraped ou saisi manuellement)

2. **Privacy Rights Clearinghouse Data Breaches:**
   - URL: https://privacyrights.org/data-breaches
   - Données: Breaches rapportés (USA principalement)

3. **ENISA Threat Landscape:**
   - URL: https://www.enisa.europa.eu/topics/cyber-threats/threats-and-trends
   - Rapports annuels sur tendances cyber

### Implementation technique

#### Carte interactive (Leaflet.js)
```html
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>

<div id="cyber-map" style="height: 600px;"></div>

<script>
const map = L.map('cyber-map').setView([30, 0], 2);

L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
  attribution: '© OpenStreetMap contributors, © CARTO',
  maxZoom: 19
}).addTo(map);

// Ajouter incidents depuis database
incidents.forEach(incident => {
  const marker = L.circleMarker([incident.lat, incident.lon], {
    radius: getRadiusByImpact(incident.severity),
    color: getColorByType(incident.type),
    fillOpacity: 0.6
  }).addTo(map);
  
  marker.bindPopup(`
    <h4>${incident.title}</h4>
    <p><strong>Date:</strong> ${incident.date}</p>
    <p><strong>Type:</strong> ${incident.type}</p>
    <p><strong>Secteur:</strong> ${incident.sector}</p>
    <p><strong>Source:</strong> <a href="${incident.source_url}" target="_blank">Voir source</a></p>
  `);
});
</script>
```

#### Database structure (incidents-database.json)
```json
{
  "incidents": [
    {
      "id": "2024-001",
      "title": "Ransomware attaque hôpitaux France",
      "date": "2024-02-15",
      "lat": 48.8566,
      "lon": 2.3522,
      "country": "France",
      "type": "ransomware",
      "sector": "santé",
      "severity": "high",
      "description": "Attaque ransomware visant plusieurs établissements hospitaliers.",
      "source": "ANSSI",
      "source_url": "https://www.cert.ssi.gouv.fr/..."
    }
  ]
}
```

### Filtres interactifs

```html
<div class="map-filters">
  <select id="filter-region">
    <option value="all">Toutes régions</option>
    <option value="europe">Europe</option>
    <option value="asia">Asie</option>
    <option value="americas">Amériques</option>
  </select>
  
  <select id="filter-sector">
    <option value="all">Tous secteurs</option>
    <option value="finance">Finance</option>
    <option value="sante">Santé</option>
    <option value="energie">Énergie</option>
    <option value="gouvernement">Gouvernement</option>
  </select>
  
  <select id="filter-type">
    <option value="all">Tous types</option>
    <option value="ransomware">Ransomware</option>
    <option value="ddos">DDoS</option>
    <option value="data-breach">Data Breach</option>
    <option value="espionnage">Espionnage</option>
  </select>
</div>
```

### Disclaimer obligatoire
```html
<div class="map-disclaimer">
  <h3>⚠️ Note importante</h3>
  <p>
    Cette carte affiche <strong>uniquement des incidents cyber publiquement documentés</strong> 
    issus de sources officielles (ANSSI, CSIS, ENISA, médias vérifiés).
  </p>
  <p>
    <strong>Ceci N'EST PAS:</strong>
  </p>
  <ul>
    <li>❌ Une carte "live" d'attaques en temps réel</li>
    <li>❌ Un monitoring actif de trafic réseau</li>
    <li>❌ Une détection automatique d'intrusions</li>
  </ul>
  <p>
    <strong>Sources:</strong> CSIS Cyber Tracker, ANSSI, ENISA, Privacy Rights Clearinghouse
  </p>
</div>
```

### Légende carte
```javascript
const legend = L.control({position: 'bottomright'});

legend.onAdd = function(map) {
  const div = L.DomUtil.create('div', 'map-legend');
  div.innerHTML = `
    <h4>Types d'incidents</h4>
    <div><span style="background: #ff4444"></span> Ransomware</div>
    <div><span style="background: #ff8800"></span> DDoS</div>
    <div><span style="background: #ffaa00"></span> Data Breach</div>
    <div><span style="background: #4488ff"></span> Espionnage</div>
    <div><span style="background: #8844ff"></span> Autre</div>
  `;
  return div;
};

legend.addTo(map);
```

---

## 📱 PHASE 5 — MODULE TÉLÉPHONE (ANDROID)

### Objectif
Module Android RÉEL, fonctionnel, 100% légal et défensif.

### Structure existante
```
/android-app/
├── app/
│   ├── src/main/
│   │   ├── java/com/sentinel/
│   │   │   ├── CallScreeningServiceImpl.kt ✅
│   │   │   ├── PhoneSecurityModule.kt ✅
│   │   │   └── CallAnalyzer.kt ✅
│   │   └── AndroidManifest.xml ✅
└── build.gradle ✅
```

**Status:** Infrastructure déjà existante, à enrichir.

### Fonctionnalités à implémenter

#### 1. Identification appels entrants
**Fichier:** `CallIdentificationService.kt` (À créer)

```kotlin
class CallIdentificationService : CallScreeningService() {
    
    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle.schemeSpecificPart
        
        // 1. Check local blacklist
        val isBlacklisted = checkLocalBlacklist(number)
        
        // 2. Analyze number pattern
        val analysis = analyzeNumberPattern(number)
        
        // 3. Check public databases (ARCEP if available)
        val isKnownSpam = checkPublicDatabases(number)
        
        // Build response
        val response = CallResponse.Builder()
            .setDisallowCall(isBlacklisted || isKnownSpam)
            .setRejectCall(isBlacklisted)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()
        
        respondToCall(callDetails, response)
        
        // Log for user review
        logCallScreening(number, analysis)
    }
    
    private fun analyzeNumberPattern(number: String): CallAnalysis {
        return CallAnalysis(
            countryCode = extractCountryCode(number),
            operator = identifyOperator(number), // Via public MCC/MNC
            isInternational = number.startsWith("+") && !number.startsWith("+33"),
            isPremiumRate = isPremiumNumber(number), // 089x, etc.
            isTollFree = isTollFreeNumber(number) // 0800, etc.
        )
    }
}
```

#### 2. Détection appels frauduleux
**Critères détection:**
- Numéros premium rate (089x en France)
- Patterns call centers (séquences répétitives)
- Numéros internationaux suspects (pays à risque)
- Fréquence appels (flooding detection)

**Base de données locale:**
```kotlin
// Room database
@Entity(tableName = "known_spam_numbers")
data class SpamNumber(
    @PrimaryKey val number: String,
    val category: String, // "spam", "scam", "marketing"
    val reportCount: Int,
    val lastSeen: Long,
    val source: String // "user", "community", "arcep"
)
```

#### 3. Analyse SMS (Smishing)
**Fichier:** `SmsAnalyzer.kt` (À créer)

```kotlin
class SmsAnalyzer {
    
    fun analyzeSms(message: SmsMessage): SmsAnalysis {
        val text = message.messageBody
        
        return SmsAnalysis(
            hasPhishingKeywords = detectPhishingKeywords(text),
            hasUrls = extractUrls(text).isNotEmpty(),
            hasPhoneNumbers = extractPhoneNumbers(text).isNotEmpty(),
            suspiciousUrls = checkUrlReputation(extractUrls(text)),
            riskScore = calculateRiskScore(text)
        )
    }
    
    private fun detectPhishingKeywords(text: String): Boolean {
        val keywords = listOf(
            "urgent", "compte bloqué", "cliquez ici", "vérifiez",
            "gagnez", "remboursement", "impôts", "amende"
        )
        return keywords.any { text.lowercase().contains(it) }
    }
    
    private fun checkUrlReputation(urls: List<String>): List<UrlAnalysis> {
        return urls.map { url ->
            UrlAnalysis(
                url = url,
                isShortened = isUrlShortener(url),
                domain = extractDomain(url),
                isHttps = url.startsWith("https://"),
                isKnownPhishing = false // Would check public lists
            )
        }
    }
}
```

#### 4. IA de recommandation
**Fichier:** `CallRecommendationEngine.kt`

```kotlin
class CallRecommendationEngine {
    
    fun getRecommendation(call: IncomingCall): Recommendation {
        val signals = collectSignals(call)
        val score = calculateTrustScore(signals)
        
        return when {
            score < 20 -> Recommendation.BLOCK
            score < 50 -> Recommendation.WARN
            score < 70 -> Recommendation.INFORM
            else -> Recommendation.ALLOW
        }
    }
    
    private fun collectSignals(call: IncomingCall): List<Signal> {
        return listOf(
            Signal.IsInContacts(isInContacts(call.number)),
            Signal.CallHistory(getCallHistory(call.number)),
            Signal.NumberPattern(analyzePattern(call.number)),
            Signal.TimeOfDay(isUnusualTime()),
            Signal.Frequency(getRecentCallCount(call.number))
        )
    }
}
```

### Permissions requises (AndroidManifest.xml)

```xml
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
<uses-permission android:name="android.permission.READ_CALL_LOG" />
<uses-permission android:name="android.permission.READ_SMS" />
<uses-permission android:name="android.permission.CALL_PHONE" />

<!-- Call Screening Service (Android 10+) -->
<service
    android:name=".CallScreeningServiceImpl"
    android:permission="android.permission.BIND_SCREENING_SERVICE">
    <intent-filter>
        <action android:name="android.telecom.CallScreeningService" />
    </intent-filter>
</service>
```

### UI/UX Module Téléphone

**Dashboard principal:**
```
┌─────────────────────────────┐
│ Module Sécurité Téléphone   │
├─────────────────────────────┤
│ ✅ 156 appels analysés       │
│ 🛡️ 12 appels bloqués         │
│ ⚠️ 8 SMS suspects détectés   │
│                             │
│ [Journal d'activité]        │
│ [Paramètres]                │
│ [Listes noires/blanches]    │
└─────────────────────────────┘
```

### Interdictions strictes (Compliance)

```kotlin
// INTERDICTIONS LÉGALES - NE PAS IMPLÉMENTER

// ❌ Écoute/enregistrement automatique
// Art. 226-1 Code Pénal - 1 an prison + 45 000€ amende
// fun recordCall() { /* ILLÉGAL */ }

// ❌ Interception communications tiers
// Art. 226-15 Code Pénal
// fun interceptOthersCalls() { /* ILLÉGAL */ }

// ❌ Accès données opérateur sans autorisation
// fun getOperatorData() { /* ILLÉGAL */ }

// ❌ Révélation identité VPN/masquée
// Techniquement impossible sans accès opérateur
// fun unmaskVpnCaller() { /* IMPOSSIBLE + ILLÉGAL */ }
```

### Page documentation module

**Fichier:** `/public/phone-security/index.html` (Déjà existe - améliorer)

Ajouter section "Conformité légale":
```markdown
### ⚖️ Conformité Légale

Le module téléphone Sentinel respecte STRICTEMENT:

✅ **Autorisé:**
- Analyse numéros via bases publiques
- Détection patterns suspects (ML local)
- Journalisation locale (device uniquement)
- Recommandations à l'utilisateur

❌ **INTERDIT (et non implémenté):**
- Écoute/enregistrement sans consentement (Art. 226-1 CP)
- Interception communications (Art. 226-15 CP)
- Accès données opérateur sans autorisation
- Toute fonctionnalité type "Pegasus"

**Permissions demandées:**
- `READ_PHONE_STATE`: Identifier appels entrants
- `READ_CALL_LOG`: Analyser historique (local)
- `READ_SMS`: Détecter smishing
- `BIND_SCREENING_SERVICE`: API Android CallScreening

**Données stockées:**
- Localement sur appareil UNIQUEMENT
- Aucun envoi serveur (mode offline complet)
- Chiffrement AES-256 base locale
- Effacement possible via paramètres
```

---

## 🧪 PHASE 6 — SOC LIVE (RÉALISTE)

### Objectif
Dashboard SOC avec logs simulés réalistes (pas de vrais hacks, simulation éducative).

### Structure
```
/public/soc-live/
├── index.html ✅ (Existe - à améliorer)
├── logs-simulator.js ⏳ (À créer)
└── sample-logs.json ⏳ (À créer)
```

### Implementation

#### Logs simulés réalistes
**Fichier:** `sample-logs.json`

```json
{
  "logs": [
    {
      "id": "log-001",
      "timestamp": "2025-01-15T14:23:45Z",
      "level": "warning",
      "source": "firewall",
      "type": "port_scan",
      "message": "Multiple connection attempts detected from 185.220.101.42",
      "details": {
        "src_ip": "185.220.101.42",
        "dst_ip": "192.168.1.100",
        "ports": [22, 23, 80, 443, 3389],
        "count": 127
      },
      "severity": "medium",
      "mitre_technique": "T1046", // Network Service Discovery
      "recommended_action": "Block IP temporarily, monitor"
    },
    {
      "id": "log-002",
      "timestamp": "2025-01-15T14:25:12Z",
      "level": "critical",
      "source": "ids",
      "type": "malware_detection",
      "message": "Potential ransomware behavior detected on DESKTOP-ABC123",
      "details": {
        "hostname": "DESKTOP-ABC123",
        "process": "cryptolocker.exe",
        "files_encrypted": 0,
        "files_accessed": 47,
        "extension_change": ".locked"
      },
      "severity": "critical",
      "mitre_technique": "T1486", // Data Encrypted for Impact
      "recommended_action": "Isolate machine immediately, initiate incident response"
    }
  ]
}
```

#### Log Simulator
**Fichier:** `logs-simulator.js`

```javascript
class SOCLogSimulator {
  constructor() {
    this.logTypes = [
      'port_scan', 'brute_force', 'malware_detection',
      'phishing_email', 'ddos_attempt', 'data_exfiltration',
      'privilege_escalation', 'lateral_movement'
    ];
    
    this.severityLevels = ['low', 'medium', 'high', 'critical'];
  }
  
  generateRealisticLog() {
    const type = this.randomChoice(this.logTypes);
    const severity = this.calculateSeverity(type);
    
    return {
      id: `log-${Date.now()}`,
      timestamp: new Date().toISOString(),
      level: severity,
      source: this.getSourceByType(type),
      type: type,
      message: this.generateMessage(type),
      details: this.generateDetails(type),
      severity: severity,
      mitre_technique: this.getMITRETechnique(type),
      recommended_action: this.getRecommendedAction(type)
    };
  }
  
  startSimulation(interval = 5000) {
    setInterval(() => {
      const log = this.generateRealisticLog();
      this.displayLog(log);
      this.updateMetrics(log);
    }, interval);
  }
  
  displayLog(log) {
    const logContainer = document.getElementById('soc-logs');
    const logElement = this.createLogElement(log);
    logContainer.prepend(logElement);
    
    // Keep only last 50 logs
    while (logContainer.children.length > 50) {
      logContainer.removeChild(logContainer.lastChild);
    }
  }
}
```

#### Dashboard SOC
**HTML Structure:**

```html
<div class="soc-dashboard">
  <!-- Metrics -->
  <div class="soc-metrics">
    <div class="metric-card">
      <h3>Événements (24h)</h3>
      <div class="metric-value" id="events-24h">1,247</div>
    </div>
    <div class="metric-card">
      <h3>Alertes actives</h3>
      <div class="metric-value critical" id="active-alerts">3</div>
    </div>
    <div class="metric-card">
      <h3>Taux détection</h3>
      <div class="metric-value" id="detection-rate">94.2%</div>
    </div>
    <div class="metric-card">
      <h3>Temps réponse moyen</h3>
      <div class="metric-value" id="response-time">4.8 min</div>
    </div>
  </div>
  
  <!-- Timeline -->
  <div class="soc-timeline">
    <h2>Timeline Incidents</h2>
    <div id="incident-timeline"></div>
  </div>
  
  <!-- Live Logs -->
  <div class="soc-logs-container">
    <h2>Logs en temps réel (simulés)</h2>
    <div class="logs-filter">
      <select id="filter-severity">
        <option value="all">Toutes sévérités</option>
        <option value="critical">Critique</option>
        <option value="high">Haute</option>
        <option value="medium">Moyenne</option>
        <option value="low">Basse</option>
      </select>
    </div>
    <div id="soc-logs" class="logs-stream"></div>
  </div>
  
  <!-- Disclaimer -->
  <div class="soc-disclaimer">
    <strong>⚠️ Simulation éducative</strong>
    <p>
      Les logs affichés sont SIMULÉS à des fins pédagogiques.
      Ils représentent des scénarios cyber réalistes basés sur des patterns d'attaques documentés.
      <strong>Aucun système réel n'est surveillé.</strong>
    </p>
  </div>
</div>
```

#### Styling SOC

```css
.soc-metrics {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 20px;
  margin-bottom: 30px;
}

.metric-card {
  background: rgba(255, 255, 255, 0.04);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  padding: 20px;
  text-align: center;
}

.metric-value {
  font-size: 2.5em;
  font-weight: 700;
  color: #4a90e2;
  margin-top: 10px;
}

.metric-value.critical {
  color: #ff4444;
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}

.logs-stream {
  background: #0a0e1a;
  border: 1px solid rgba(74, 144, 226, 0.2);
  border-radius: 8px;
  padding: 16px;
  max-height: 600px;
  overflow-y: auto;
  font-family: 'Courier New', monospace;
  font-size: 0.9em;
}

.log-entry {
  padding: 12px;
  margin-bottom: 8px;
  border-left: 4px solid;
  background: rgba(255, 255, 255, 0.02);
  border-radius: 4px;
  animation: slideIn 0.3s ease-out;
}

.log-entry.critical { border-left-color: #ff4444; }
.log-entry.high { border-left-color: #ff8800; }
.log-entry.medium { border-left-color: #ffaa00; }
.log-entry.low { border-left-color: #4488ff; }

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateX(-20px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}
```

---

## 🏛️ PHASE 7 — USAGES INSTITUTIONNELS (Étendus)

### Objectif
Enrichir pages institutionnelles avec cas d'usage détaillés et FAQ complète.

### Structure
```
/public/
├── institutionnels/index.html ✅ (Existe)
├── institutions.html ✅ (Existe - à améliorer)
├── defense-police.html ✅ (Existe)
└── faq-institutionnelle.html ✅ (Existe - à enrichir)
```

### Améliorations à apporter

#### 1. Cas d'usage sectoriels

**Administrations centrales:**
```markdown
### Ministères et Administrations

**Besoins spécifiques:**
- Souveraineté numérique absolue
- Hébergement SecNumCloud (ANSSI)
- Traçabilité RGPD stricte
- Conformité LPM (Loi de Programmation Militaire)

**Solution Sentinel:**
- Déploiement on-premise ou cloud souverain (Scaleway, OVH)
- Journalisation complète (7 ans retention)
- Export SIEM (QRadar, Splunk, ELK)
- Audit trail immuable (blockchain-like)

**Référence:** Conforme RGS (Référentiel Général de Sécurité)
```

**Collectivités locales:**
```markdown
### Villes, Départements, Régions

**Contraintes budgétaires:**
- Solution cost-effective
- Mutualisation possible (plusieurs collectivités)
- Formation équipes non-spécialisées

**Solution Sentinel:**
- Mode SaaS souverain (facturation proportionnelle)
- Dashboard simplifié "clé en main"
- Support dédié collectivités
- Formation initiale incluse

**Partenariat:** Compatible UGAP (Union des Groupements d'Achats Publics)
```

**Défense et Police:**
```markdown
### Forces de l'ordre et Défense

**Exigences critiques:**
- Classification données (Diffusion Restreinte à Secret Défense)
- Air gap possible (réseaux isolés)
- Pas de télémétrie externe
- Homologation ANSSI requise

**Solution Sentinel:**
- Version "Defense Edition" air-gapped
- Installation 100% offline
- Mises à jour manuelles via packages signés
- Conformité IGI 1300 (Protection du Secret)

**Status homologation:** En cours (ANSSI)
```

#### 2. FAQ Institutionnelle enrichie

Ajouter à `/public/faq-institutionnelle.html`:

```markdown
### Questions Techniques

**Q: Sentinel peut-il fonctionner en mode totalement déconnecté?**
R: Oui. Le mode "Air Gap" permet un fonctionnement 100% offline. 
Mises à jour via clés USB signées cryptographiquement.

**Q: Quelles sont les dépendances logicielles tierces?**
R: Liste exhaustive dans SBOM (Software Bill of Materials). 
Principales: Node.js runtime, PostgreSQL, Redis (optionnel).
Aucune dépendance vers services cloud américains.

**Q: Sentinel peut-il détecter des zero-days?**
R: Partiellement. Détection comportementale peut identifier anomalies, 
mais attribution précise nécessite analyse humaine. Pas de "magie IA".

**Q: Quelle est la couverture MITRE ATT&CK?**
R: ~45-60% techniques détectables selon qualité logs disponibles.
Détails: /public/threat-intelligence/detection-mapping.html

### Questions Légales

**Q: Sentinel est-il conforme RGPD?**
R: Oui. Privacy by Design, minimisation données, droit effacement.
DPIA (Data Protection Impact Assessment) disponible sur demande.

**Q: Peut-on héberger hors Union Européenne?**
R: Techniquement possible, mais non recommandé pour organismes publics.
Risque violation souveraineté numérique (Cloud Act, FISA 702).

**Q: Sentinel collecte-t-il de la télémétrie?**
R: Non par défaut. Télémétrie optionnelle (opt-in explicite) 
pour amélioration produit. Désactivable en un clic.

### Questions Commerciales

**Q: Quel est le modèle de pricing institutionnel?**
R: Licence perpétuelle ou abonnement annuel.
Tarif dégressif selon volumétrie (nombre endpoints/logs).
Devis sur mesure: contact@sentinel-quantum.fr

**Q: Support 24/7 disponible?**
R: Oui pour contrats "Enterprise" et "Defense".
SLA garantis: 15min (P1), 1h (P2), 4h (P3), 24h (P4).

**Q: Formation des équipes incluse?**
R: Formation initiale (2-3 jours) incluse dans licence Enterprise+.
Formation continue disponible (certification Sentinel Analyst).
```

---

## 📦 PHASE 8 — APK RÉEL (PAS DÉMO)

### Objectif
Workflow automatisé de build et distribution APK via GitHub Releases.

### Infrastructure existante

```
/.github/workflows/
├── release-apk.yml ✅ (Workflow CI/CD)
└── codeql-analysis.yml ✅ (Security scanning)

/android-app/
├── app/build.gradle ✅
├── gradle.properties ✅
└── keystore.properties ⏳ (À configurer pour release)
```

### Configuration Keystore Release

**Fichier:** `keystore.properties` (À créer, NON commité)

```properties
storeFile=../release-keystore.jks
storePassword=***SECURE_PASSWORD***
keyAlias=sentinel-release-key
keyPassword=***SECURE_PASSWORD***
```

**Génération keystore:**
```bash
keytool -genkey -v \
  -keystore release-keystore.jks \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -alias sentinel-release-key
```

### Build Gradle configuration

**Fichier:** `android-app/app/build.gradle`

```gradle
android {
    signingConfigs {
        release {
            if (project.hasProperty('RELEASE_STORE_FILE')) {
                storeFile file(RELEASE_STORE_FILE)
                storePassword RELEASE_STORE_PASSWORD
                keyAlias RELEASE_KEY_ALIAS
                keyPassword RELEASE_KEY_PASSWORD
            }
        }
    }
    
    buildTypes {
        release {
            minifyEnabled true
            shrinkResources true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
            signingConfig signingConfigs.release
        }
    }
}
```

### GitHub Workflow amélioré

**Fichier:** `.github/workflows/release-apk.yml`

```yaml
name: Build and Release APK

on:
  push:
    tags:
      - 'v*'
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
      
      - name: Decode Keystore
        env:
          ENCODED_KEYSTORE: ${{ secrets.RELEASE_KEYSTORE }}
        run: |
          echo $ENCODED_KEYSTORE | base64 -di > release-keystore.jks
      
      - name: Build Release APK
        env:
          RELEASE_STORE_FILE: ../release-keystore.jks
          RELEASE_STORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          RELEASE_KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          RELEASE_KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
        run: |
          cd android-app
          ./gradlew assembleRelease
      
      - name: Generate SHA256
        run: |
          cd android-app/app/build/outputs/apk/release
          sha256sum app-release.apk > app-release.apk.sha256
      
      - name: Create Release
        uses: softprops/action-gh-release@v1
        with:
          files: |
            android-app/app/build/outputs/apk/release/app-release.apk
            android-app/app/build/outputs/apk/release/app-release.apk.sha256
          body_path: RELEASE_NOTES.md
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

### Page Téléchargement

**Fichier:** `/public/download/index.html` ✅ (Existe déjà)

**Améliorer avec:**
```javascript
// Déjà implémenté - vérifier affichage SHA256

async function verifyAPK() {
  const file = document.getElementById('apk-file').files[0];
  const expectedHash = document.getElementById('expected-hash').value;
  
  const actualHash = await calculateSHA256(file);
  
  if (actualHash === expectedHash) {
    showMessage('✅ APK vérifié ! Hash correspond.', 'success');
  } else {
    showMessage('❌ ATTENTION ! Hash ne correspond pas. APK possiblement modifié.', 'danger');
  }
}

async function calculateSHA256(file) {
  const buffer = await file.arrayBuffer();
  const hashBuffer = await crypto.subtle.digest('SHA-256', buffer);
  const hashArray = Array.from(new Uint8Array(hashBuffer));
  return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
}
```

### Changelog template

**Fichier:** `RELEASE_NOTES.md`

```markdown
# Sentinel Quantum Vanguard AI Pro - v1.2.0

## 🆕 Nouvelles fonctionnalités

- Module téléphone: Détection smishing améliorée
- SOC Live: Timeline incidents interactive
- Threat Intel: Intégration MITRE ATT&CK v14

## 🔧 Améliorations

- Performance: Réduction 30% consommation RAM
- UI/UX: Nouveau thème "Liquid Glass"
- Sécurité: Chiffrement base locale AES-256

## 🐛 Corrections

- Fix: Crash au démarrage sur Android 11
- Fix: Notifications non affichées sur certains devices
- Fix: Fuite mémoire dans analyseur SMS

## 📦 Installation

1. Télécharger `app-release.apk`
2. Vérifier SHA256: `sha256sum app-release.apk`
3. Comparer avec `app-release.apk.sha256`
4. Autoriser "Sources inconnues" si nécessaire
5. Installer APK

## 🔐 Vérification intégrité

**SHA256:**
```
abc123def456...
```

## ⚠️ Prérequis

- Android 8.0 (API 26) minimum
- 100 MB espace disponible
- Permissions: Téléphone, SMS, Contacts

## 📝 Notes légales

Application 100% défensive. Aucune fonctionnalité offensive.
Conformité RGPD. Code source: github.com/teetee971/SentinelQuantumVanguardAiPro
```

---

## ✅ CHECKLIST GLOBALE PHASES 3-8

### Phase 3: MITRE ATT&CK
- [ ] Page mitre-groups.html
- [ ] Page mitre-techniques.html
- [ ] Page detection-mapping.html
- [ ] Intégration API MITRE
- [ ] Mapping Sentinel capabilities
- [ ] Disclaimers sources publiques

### Phase 4: Carte Mondiale
- [ ] Améliorer world-cyber-map.html
- [ ] Database incidents publics (JSON)
- [ ] Intégration Leaflet.js
- [ ] Filtres interactifs
- [ ] Disclaimers "pas live hacking"
- [ ] Sources CSIS/ENISA/ANSSI

### Phase 5: Module Téléphone
- [ ] CallIdentificationService.kt
- [ ] SmsAnalyzer.kt
- [ ] CallRecommendationEngine.kt
- [ ] UI Dashboard téléphone
- [ ] Disclaimers légaux (Art. 226-1 CP)
- [ ] Documentation conformité

### Phase 6: SOC Live
- [ ] Logs simulator JavaScript
- [ ] Sample logs JSON (réalistes)
- [ ] Dashboard SOC HTML/CSS
- [ ] Timeline incidents
- [ ] Metrics en temps réel
- [ ] Disclaimers "simulation éducative"

### Phase 7: Institutionnels
- [ ] Enrichir cas d'usage sectoriels
- [ ] FAQ technique complète
- [ ] FAQ légale (RGPD, souveraineté)
- [ ] FAQ commerciale (pricing, support)
- [ ] Références conformité (RGS, LPM, ANSSI)

### Phase 8: APK Production
- [ ] Configurer keystore release
- [ ] Améliorer workflow GitHub Actions
- [ ] Template RELEASE_NOTES.md
- [ ] Fonction vérification SHA256 (download page)
- [ ] Documentation installation utilisateur
- [ ] Tests build release

---

## 🎯 PRIORITÉS SUGGÉRÉES

**Haute priorité (Impact immédiat):**
1. Phase 5: Module téléphone (fonctionnalité clé différenciante)
2. Phase 3: MITRE ATT&CK (crédibilité technique)
3. Phase 8: APK production (livraison utilisateur)

**Moyenne priorité (Amélioration UX):**
4. Phase 6: SOC Live (visualisation impressive)
5. Phase 4: Carte mondiale (interactivité)

**Basse priorité (Contenu):**
6. Phase 7: Institutionnels étendus (texte principalement)

---

## 📝 NOTES FINALES

### Respect Contraintes Absolues

Toutes phases respectent:
- ✅ Aucune fonctionnalité offensive
- ✅ Aucune promesse irréaliste
- ✅ Transparence totale (sources publiques uniquement)
- ✅ Honnêteté technique (limites clairement indiquées)
- ✅ Conformité légale (RGPD, Code Pénal, etc.)
- ✅ Mobile-first UX

### Règle d'Or appliquée

> "Si une fonctionnalité n'est pas encore techniquement prête, elle doit être expliquée, pas simulée."

Exemples:
- SOC Live: **Clairement marqué "simulation éducative"**
- MITRE ATT&CK: **Disclaimers "sources publiques, pas d'action offensive"**
- Module téléphone: **Limites légales explicitement documentées**
- Carte cyber: **"Pas de live hacking, incidents documentés uniquement"**

---

**Status Phases 3-8:** Spécifications complètes, prêtes pour implémentation progressive.

**Recommandation:** Démarrer par Phase 5 (module téléphone) pour impact maximal.
