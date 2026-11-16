# SIMULATEUR DE DEVIS — LOGIQUE INTERNE
## Sentinel Quantum Vanguard AI Pro

**⚠️ CONFIDENTIEL — USAGE INTERNE UNIQUEMENT**  
**Ne pas exposer publiquement**  
**Type:** Logique de tarification interne  
**Version:** 1.0  
**Date:** Décembre 2024

---

## AVERTISSEMENT

Ce document définit **uniquement la logique interne** pour un futur simulateur de devis.

**❌ CE N'EST PAS:**
- Un outil public
- Une interface client
- Un système de paiement
- Une grille tarifaire officielle
- Un engagement contractuel

**✅ C'EST:**
- Une documentation de logique interne
- Un framework de calcul pour devis personnalisés
- Un outil d'aide à la décision pour équipe commerciale
- Une base pour discussions internes uniquement

**Principe:** Tout devis réel doit être personnalisé et validé individuellement.

---

## VARIABLES D'ENTRÉE

### 1. Client Profile

**Company Size (Taille Entreprise):**
```javascript
const companySizes = {
  STARTUP: { employees: "1-50", multiplier: 1.0 },
  SMB: { employees: "51-250", multiplier: 1.5 },
  MIDMARKET: { employees: "251-1000", multiplier: 2.5 },
  ENTERPRISE: { employees: "1001-5000", multiplier: 4.0 },
  LARGE_ENTERPRISE: { employees: "5000+", multiplier: 6.0 }
};
```

**Industry Sector (Secteur):**
```javascript
const industrySectors = {
  FINANCE: { riskLevel: "HIGH", multiplier: 1.8 },
  HEALTHCARE: { riskLevel: "HIGH", multiplier: 1.7 },
  GOVERNMENT: { riskLevel: "CRITICAL", multiplier: 2.0 },
  RETAIL: { riskLevel: "MEDIUM", multiplier: 1.2 },
  TECHNOLOGY: { riskLevel: "MEDIUM", multiplier: 1.3 },
  MANUFACTURING: { riskLevel: "MEDIUM", multiplier: 1.1 },
  EDUCATION: { riskLevel: "LOW", multiplier: 0.9 },
  OTHER: { riskLevel: "MEDIUM", multiplier: 1.0 }
};
```

**Geographic Region (Région):**
```javascript
const regions = {
  EUROPE_FRANCE: { currency: "EUR", taxRate: 0.20, multiplier: 1.0 },
  EUROPE_EU: { currency: "EUR", taxRate: 0.20, multiplier: 1.1 },
  NORTH_AMERICA: { currency: "USD", taxRate: 0.08, multiplier: 1.3 },
  ASIA_PACIFIC: { currency: "USD", taxRate: 0.10, multiplier: 1.2 },
  MIDDLE_EAST: { currency: "USD", taxRate: 0.05, multiplier: 1.4 },
  LATAM: { currency: "USD", taxRate: 0.15, multiplier: 0.9 }
};
```

### 2. Features Selection

**Module Selection:**
```javascript
const modules = {
  // Core (toujours inclus)
  CORE_PLATFORM: { 
    name: "Plateforme Core",
    included: true,
    basePrice: 0, // Inclus dans base
    description: "Feature flags, logging, audit trail, UI/UX"
  },
  
  // Optional modules
  BACKEND_READONLY: {
    name: "Backend API (READ-ONLY)",
    included: false,
    basePrice: 500, // EUR/mois base
    description: "Health, status, agents, metrics endpoints"
  },
  
  BACKEND_WRITE: {
    name: "Backend API (WRITE)",
    included: false,
    basePrice: 2000, // EUR/mois base
    requiresAudit: true,
    description: "Full CRUD operations, persistence"
  },
  
  AGENTS_DORMANT: {
    name: "AI Agents (DORMANT)",
    included: false,
    basePrice: 800, // EUR/mois base
    description: "6 agents en état passif"
  },
  
  AGENTS_SANDBOX: {
    name: "AI Agents (SANDBOX)",
    included: false,
    basePrice: 1500, // EUR/mois base
    description: "6 agents en simulation contrôlée"
  },
  
  AGENTS_MONITOR: {
    name: "AI Agents (MONITOR)",
    included: false,
    basePrice: 3000, // EUR/mois base
    requiresAudit: true,
    description: "6 agents en observation active"
  },
  
  AGENTS_ARMED: {
    name: "AI Agents (ARMED)",
    included: false,
    basePrice: 6000, // EUR/mois base
    requiresAudit: true,
    requiresCertification: true,
    description: "6 agents autonomes avec réponse automatique"
  },
  
  LIVE_LOGGING: {
    name: "Live Log Streaming",
    included: false,
    basePrice: 400, // EUR/mois base
    description: "Streaming temps réel des logs"
  },
  
  ANDROID_APP: {
    name: "Application Android",
    included: false,
    basePrice: 1200, // EUR/mois base
    description: "App mobile en release mode"
  },
  
  THREAT_DETECTION: {
    name: "Threat Detection (Future)",
    included: false,
    basePrice: 2500, // EUR/mois base
    status: "PLANNED",
    description: "Détection de menaces avancée"
  },
  
  NETWORK_MONITORING: {
    name: "Network Monitoring (Future)",
    included: false,
    basePrice: 2000, // EUR/mois base
    status: "PLANNED",
    description: "Surveillance réseau temps réel"
  },
  
  INCIDENT_RESPONSE: {
    name: "Incident Response (Future)",
    included: false,
    basePrice: 3500, // EUR/mois base
    requiresAudit: true,
    status: "PLANNED",
    description: "Réponse automatisée aux incidents"
  }
};
```

### 3. Service Level Agreement (SLA)

**SLA Tiers:**
```javascript
const slaTiers = {
  DEMO: {
    name: "Demo / POC",
    uptime: "Best effort",
    support: "Email only",
    responseTime: "48h",
    multiplier: 1.0,
    description: "Pour démonstrations et POC uniquement"
  },
  
  BASIC: {
    name: "Basic",
    uptime: "99%",
    support: "Email + ticket",
    responseTime: "24h",
    multiplier: 1.3,
    description: "Support business hours"
  },
  
  STANDARD: {
    name: "Standard",
    uptime: "99.5%",
    support: "Email + ticket + phone",
    responseTime: "8h",
    multiplier: 1.8,
    description: "Support 8h-20h, 5j/7"
  },
  
  PREMIUM: {
    name: "Premium",
    uptime: "99.9%",
    support: "24/7 priority",
    responseTime: "2h",
    multiplier: 2.5,
    description: "Support 24/7 avec escalation"
  },
  
  ENTERPRISE: {
    name: "Enterprise",
    uptime: "99.99%",
    support: "24/7 dedicated",
    responseTime: "30min",
    multiplier: 4.0,
    description: "CSM dédié + support prioritaire 24/7"
  }
};
```

---

## RÈGLES DE CALCUL

### Formula Principal

```javascript
/**
 * FORMULE DE CALCUL PRINCIPALE (INTERNE)
 */
function calculateQuote(params) {
  // 1. Base Price (sum of selected modules)
  const basePrice = calculateBasePrice(params.modules);
  
  // 2. Apply Company Size Multiplier
  const sizeAdjusted = basePrice * params.companySize.multiplier;
  
  // 3. Apply Industry Sector Multiplier
  const sectorAdjusted = sizeAdjusted * params.sector.multiplier;
  
  // 4. Apply SLA Multiplier
  const slaAdjusted = sectorAdjusted * params.sla.multiplier;
  
  // 5. Apply Deployment Model Multiplier
  const deploymentAdjusted = slaAdjusted * params.deployment.multiplier;
  
  // 6. Apply Region Multiplier
  const regionAdjusted = deploymentAdjusted * params.region.multiplier;
  
  // 7. Apply Volume Discount
  const volumeDiscounted = regionAdjusted * (1 - params.volumeDiscount.discount);
  
  // 8. Apply Contract Duration Discount
  const durationDiscounted = volumeDiscounted * (1 - params.duration.discount);
  
  // 9. Monthly Recurring Revenue (MRR)
  const mrr = durationDiscounted;
  
  // 10. Setup/Onboarding Fee (one-time)
  const setupFee = params.deployment.setup;
  
  // 11. Total Contract Value (TCV)
  const tcv = (mrr * params.duration.months) + setupFee;
  
  return {
    mrr: mrr,
    setupFee: setupFee,
    tcv: tcv,
    currency: params.region.currency
  };
}
```

---

## MESSAGES DE SORTIE

### Message Public

```
"Tarification sur devis uniquement.
Contactez notre équipe commerciale pour un devis personnalisé
adapté à vos besoins et votre infrastructure."
```

---

**FIN DU DOCUMENT — CONFIDENTIEL**

**Version:** 1.0  
**Date:** Décembre 2024  
**Classification:** CONFIDENTIEL — USAGE INTERNE UNIQUEMENT
