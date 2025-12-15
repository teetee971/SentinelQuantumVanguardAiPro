# 🏗️ Architecture Technique - Sentinel Quantum Vanguard AI Pro

**Date :** 15 décembre 2024  
**Version :** 1.0.0-release  
**Type :** Application Mobile Android + PWA Web

---

## 📋 Vue d'Ensemble

**Sentinel Quantum Vanguard AI Pro** est une solution de sécurité mobile hybride combinant :
- **Application Android native** (React Native + Kotlin)
- **Progressive Web App** (PWA installable)
- **Architecture modulaire** (agents IA spécialisés)

---

## 🎯 Architecture Globale

```
┌─────────────────────────────────────────────────────────┐
│                   UTILISATEUR FINAL                     │
│             (Mobile Android / Navigateur Web)           │
└───────────────┬─────────────────────────────────────────┘
                │
    ┌───────────▼──────────┐         ┌──────────────────┐
    │   Android APK        │         │    PWA Web       │
    │  (Natif + RN)        │         │  (SPA React)     │
    └───────────┬──────────┘         └────────┬─────────┘
                │                              │
    ┌───────────▼──────────────────────────────▼─────────┐
    │           Modules IA Sécurité                      │
    │  - Phone Security (détection appels)               │
    │  - AI Protection (analyse fraude)                  │
    │  - SMS Scanner (phishing SMS)                      │
    │  - Network Monitor (connexions suspectes)          │
    └───────────┬────────────────────────────────────────┘
                │
    ┌───────────▼──────────┐
    │  Stockage Local      │
    │  - SQLite Database   │
    │  - SharedPreferences │
    │  - LocalStorage (PWA)│
    └──────────────────────┘

✅ Zéro serveur obligatoire
✅ Architecture autonome
✅ Stockage 100% local
```

---

## 📱 Android APK (Native)

### Stack Technique

| Composant | Technologie | Version |
|-----------|-------------|---------|
| **Framework** | React Native | 0.73.2 |
| **Language natif** | Kotlin | 1.9.22 |
| **Build Tool** | Gradle | 8.1.4 |
| **Android SDK** | API 23-34 | Android 6.0-14 |
| **JDK** | Temurin (OpenJDK) | 17 |

### Modules Android Natifs

#### 1. Phone Security Module (Kotlin)

**Fichiers :** `android-app/android/app/src/main/kotlin/`

**Fonctionnalités :**
- Détection appels entrants (TelephonyManager)
- Lecture état téléphone (READ_PHONE_STATE)
- Accès historique appels (READ_CALL_LOG)
- Enrichissement contacts (READ_CONTACTS)

**API Android utilisées :**
```kotlin
import android.telephony.TelephonyManager
import android.telecom.Call
import android.provider.CallLog
import android.provider.ContactsContract
```

#### 2. WebView Bridge

**Technologie :** AndroidX WebKit  
**Communication :** JavaScript ↔ Kotlin via postMessage

**Interface :**
```kotlin
webView.addJavascriptInterface(PhoneSecurityBridge(), "AndroidBridge")
```

### Permissions Android

**Déclarées dans :** `android-app/android/app/src/main/AndroidManifest.xml`

| Permission | Niveau | Justification |
|------------|--------|---------------|
| `READ_PHONE_STATE` | Dangereuse | Détection appels |
| `READ_CALL_LOG` | Dangereuse | Historique appels |
| `READ_CONTACTS` | Dangereuse | Identification appelant |
| `READ_SMS` (Institutional) | Dangereuse | Détection phishing SMS |
| `RECORD_AUDIO` (Institutional) | Dangereuse | Enregistrement appels |
| `INTERNET` | Normale | Mise à jour module IA |
| `RECEIVE_BOOT_COMPLETED` | Normale | Persistance monitoring |

**Gestion Runtime :**
```kotlin
ActivityCompat.requestPermissions(
    this,
    arrayOf(Manifest.permission.READ_PHONE_STATE),
    REQUEST_CODE
)
```

### Stockage de Données

#### SQLite Database

**Fichier :** `sentinel_security.db`  
**Localisation :** `/data/data/com.sentinel.quantum.institutional/databases/`

**Tables :**
```sql
CREATE TABLE calls (
    id INTEGER PRIMARY KEY,
    phone_number TEXT,
    timestamp INTEGER,
    duration INTEGER,
    risk_score REAL,
    country_code TEXT,
    is_spam BOOLEAN
);

CREATE TABLE blocked_numbers (
    id INTEGER PRIMARY KEY,
    phone_number TEXT UNIQUE,
    reason TEXT,
    created_at INTEGER
);
```

**Accès :**
```kotlin
val db = SQLiteDatabase.openOrCreateDatabase(
    context.getDatabasePath("sentinel_security.db"),
    null
)
```

**Amélioration recommandée :**
```gradle
// Chiffrement SQLCipher
implementation "net.zetetic:android-database-sqlcipher:4.5.4"
```

---

## 🌐 PWA Web

### Stack Technique

| Composant | Technologie | Version |
|-----------|-------------|---------|
| **Framework** | React | 18.2+ |
| **Build** | Vite | 4.x |
| **Hosting** | Cloudflare Pages | - |
| **Offline** | Service Worker | Workbox |

### Fichiers Clés

```
/
├── index.html              # Point d'entrée
├── manifest.json           # PWA manifest
├── sw.js                   # Service Worker
├── public/
│   ├── icons/              # Icons PWA
│   └── offline.html        # Page offline
├── assets/
│   └── modules/            # Modules IA JS
└── vite.config.js          # Configuration Vite
```

### PWA Manifest

**Fichier :** `public/manifest.json`

```json
{
  "name": "Sentinel Quantum Vanguard AI Pro",
  "short_name": "Sentinel AI",
  "start_url": "/",
  "display": "standalone",
  "background_color": "#000000",
  "theme_color": "#00ff00",
  "icons": [
    {
      "src": "/icons/icon-192.png",
      "sizes": "192x192",
      "type": "image/png"
    },
    {
      "src": "/icons/icon-512.png",
      "sizes": "512x512",
      "type": "image/png"
    }
  ]
}
```

### Service Worker (Offline)

**Fichier :** `sw.js` ou généré via Workbox

**Stratégies :**
- Cache-First : Assets statiques (JS, CSS, images)
- Network-First : API calls (si backend)
- Stale-While-Revalidate : Données modules IA

```javascript
self.addEventListener('fetch', (event) => {
  if (event.request.destination === 'document') {
    event.respondWith(caches.match(event.request)
      .then(response => response || fetch(event.request))
    );
  }
});
```

### LocalStorage (Browser)

**Utilisation :**
```javascript
// Sauvegarde configuration utilisateur
localStorage.setItem('sentinel_config', JSON.stringify(config));

// Historique détections (limité 5MB)
localStorage.setItem('detection_history', JSON.stringify(history));
```

**Limitation :** ~5-10 MB par domaine

---

## 🤖 Modules IA

### Architecture Modulaire

```
/ai-modules/
├── phone-security/         # Module téléphone
│   ├── detector.js         # Détection appels
│   ├── analyzer.js         # Analyse risque
│   └── blocker.js          # Blocage
├── sms-scanner/            # Module SMS
│   ├── phishing-detector.js
│   └── pattern-matcher.js
├── network-monitor/        # Module réseau
│   └── connection-analyzer.js
└── ai-core/                # Moteur IA
    ├── ml-engine.js        # Machine Learning
    └── decision-tree.js    # Arbre décision
```

### Algorithmes Détection

#### 1. Détection Spam Téléphonique

**Heuristiques :**
```javascript
function calculateRiskScore(phoneNumber, metadata) {
  let score = 0;
  
  // Facteurs de risque
  if (isInternational(phoneNumber)) score += 0.2;
  if (isUnknownNumber(phoneNumber)) score += 0.3;
  if (hasRepeatingDigits(phoneNumber)) score += 0.1;
  if (isShortDuration(metadata.duration)) score += 0.2;
  if (isKnownSpamPattern(phoneNumber)) score += 0.5;
  
  return Math.min(score, 1.0);
}
```

#### 2. Détection Phishing SMS

**Pattern Matching :**
```javascript
const PHISHING_PATTERNS = [
  /urgent.*compte.*bloqué/i,
  /cliquez.*lien.*vérifier/i,
  /remboursement.*carte.*bancaire/i,
  /\bhttps?:\/\/[^\s]+\b/  // URLs suspectes
];

function isPhishingSMS(message) {
  return PHISHING_PATTERNS.some(pattern => 
    pattern.test(message)
  );
}
```

---

## 🔐 Sécurité

### Signature APK

**Keystore :** Production (RSA 2048-bit)  
**Algorithme :** SHA-256withRSA  
**Validité :** 25 ans  
**Stockage :** GitHub Secrets (RELEASE_KEYSTORE_BASE64)

**Configuration Gradle :**
```gradle
signingConfigs {
    release {
        storeFile file('release.keystore')
        storePassword System.getenv('KEYSTORE_PASSWORD')
        keyAlias System.getenv('KEY_ALIAS')
        keyPassword System.getenv('KEY_PASSWORD')
    }
}
```

### Obfuscation & Minification

**ProGuard/R8 :**
```gradle
buildTypes {
    release {
        minifyEnabled true
        shrinkResources true
        proguardFiles 'proguard-rules.pro'
    }
}
```

**Résultat :**
- Code Kotlin obfusqué
- Ressources inutilisées supprimées
- Taille APK réduite (~30%)

---

## 📦 Build & CI/CD

### Pipeline GitHub Actions

**Fichier :** `.github/workflows/android-release.yml`

**Étapes :**
1. Checkout code (actions/checkout@v4)
2. Setup Java 17 (actions/setup-java@v4)
3. Setup Node.js 18 (actions/setup-node@v4)
4. Install dependencies (npm ci)
5. Decode keystore (base64 decode)
6. Setup Android SDK (android-actions/setup-android@v3)
7. Build APK (./gradlew assembleInstitutionalRelease)
8. Generate SHA256
9. Upload to GitHub Release

**Durée :** ~5-10 minutes

### Artefacts Produits

| Fichier | Taille | Description |
|---------|--------|-------------|
| `SentinelQuantumVanguardAIPro-v1.0.0-release.apk` | ~25-30 MB | APK signé |
| `*.apk.sha256` | ~100 bytes | Checksum |

---

## 🌍 Déploiement

### Android APK

**Distribution :**
- GitHub Releases (principal)
- Téléchargement direct (HTTP)
- Installation manuelle (sideload)

**Pas de :**
- ❌ Google Play Store (permissions sensibles)
- ❌ Store tiers obligatoire
- ❌ Backend serveur requis

### PWA Web

**Hosting :** Cloudflare Pages  
**URL :** https://sentinelquantumvanguardaipro.pages.dev  
**CDN :** Global (200+ villes)  
**HTTPS :** Automatique (Let's Encrypt)  
**Déploiement :** Git push → auto-deploy

**Alternative recommandée UE :**
- Scaleway Object Storage + CDN
- OVHcloud Web Hosting
- Netlify EU region

---

## 📊 Performance

### Métriques Cibles

| Métrique | Valeur | Statut |
|----------|--------|--------|
| **Taille APK** | < 35 MB | ✅ ~25-30 MB |
| **Temps démarrage** | < 3s | ✅ ~2s |
| **Mémoire RAM** | < 150 MB | ✅ ~100 MB |
| **Batterie** | < 2% / jour | ✅ Optimisé |
| **PWA First Paint** | < 2s | ✅ ~1.5s |

---

## 🔄 Évolutions Futures

### Court Terme (1-3 mois)
- [ ] SQLCipher (chiffrement DB)
- [ ] Export données JSON/CSV
- [ ] Machine Learning on-device (TensorFlow Lite)

### Moyen Terme (3-6 mois)
- [ ] Backend REST optionnel (Scaleway)
- [ ] Synchronisation multi-appareils (chiffrée)
- [ ] Version iOS (React Native)

### Long Terme (6-12 mois)
- [ ] Federated Learning (IA décentralisée)
- [ ] P2P sync (sans serveur central)
- [ ] Blockchain audit trail (optionnel)

---

**Dernière mise à jour :** 15 décembre 2024  
**Statut :** ✅ Architecture validée
