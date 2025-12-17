# SOC Live Fonctionnel - Documentation Technique

## Vue d'ensemble

Le SOC Live a été transformé d'un tableau de bord décoratif avec données simulées en un **SOC fonctionnel** utilisant exclusivement des **sources de données réelles et publiques**.

## ✅ Conformité aux Exigences

### 1. Source de Logs Réelle

**Avant** : Données hardcodées, compteurs simulés, événements fictifs  
**Après** : APIs publiques avec données réelles

#### Sources connectées :

| Source | API | Données |
|--------|-----|---------|
| **GitHub Security Advisories** | `https://api.github.com/advisories` | Avis de sécurité réels, vulnérabilités publiées |
| **NVD/CVE** | `https://services.nvd.nist.gov/rest/json/cves/2.0` | Base de données CVE officielle (NIST) |

#### Caractéristiques :
- ✅ Appels API réels en client-side (JavaScript)
- ✅ Compatible Cloudflare Pages (pas de backend requis)
- ✅ CORS-friendly (APIs publiques accessibles)
- ✅ Auto-refresh toutes les 5 minutes
- ✅ Bouton de rafraîchissement manuel

### 2. Événements Réellement Générés

**Avant** : Fausses actualités US-CERT, CISA, ENISA hardcodées  
**Après** : Événements réels avec traçabilité complète

#### Données affichées :

```javascript
Événement = {
  source: "GitHub Security" | "NVD/CVE",
  title: string,              // Titre réel de l'advisory/CVE
  description: string,        // Description officielle
  severity: "CRITICAL" | "HIGH" | "MEDIUM" | "LOW",  // Sévérité réelle
  time: ISO8601,              // Timestamp réel de publication
  id: string                  // GHSA-xxx ou CVE-xxxx-xxxxx
}
```

#### Exemples d'événements réels :
- **GitHub Security** : `GHSA-xxxx-xxxx-xxxx` - Vulnérabilités dans packages npm, PyPI, Maven, etc.
- **CVE** : `CVE-2024-xxxxx` - Vulnérabilités NIST officielles

### 3. Indicateurs de Santé et Volume

**Avant** : Compteurs animés aléatoirement, valeurs fictives  
**Après** : Métriques réelles calculées à partir des données

#### Indicateurs de santé :

| Indicateur | Mesure |
|------------|--------|
| **Statut API** | 🟢 En ligne / 🔴 Hors ligne (test réel de connexion) |
| **Événements chargés** | Nombre réel d'événements récupérés de l'API |
| **Dernière mise à jour** | Timestamp réel du dernier fetch |
| **Latence** | Temps de réponse API (si disponible) |

#### Métriques de volume :

```javascript
Volume = {
  totalEvents: count,           // Nombre total d'événements chargés
  criticalCount: count,         // Événements CRITICAL
  highMediumCount: count,       // Événements HIGH ou MEDIUM
  lastHour: count               // Événements de la dernière heure
}
```

**Calcul en temps réel** :
- Pas de valeurs hardcodées
- Agrégation des données API
- Mise à jour automatique à chaque refresh

### 4. Suppression du Contenu Statique/Décoratif

#### ❌ Supprimé :

- **Fausses actualités** : Tous les articles US-CERT, CISA, ENISA, CERT-FR hardcodés
- **Carte mondiale animée** : Canvas avec animations de "cyberattaques" simulées
- **Compteurs simulés** : Valeurs qui changeaient aléatoirement toutes les 3 secondes
- **Timestamps fictifs** : Générés dynamiquement en JavaScript
- **Statistiques inventées** : "127 attaques actives", "1,247 événements/24h", etc.
- **Journal SOC fictif** : Événements hardcodés avec messages génériques

#### ✅ Conservé (car fonctionnel) :

- **Interface utilisateur** : Design et layout (réutilisé)
- **Navigation** : Menu et liens
- **Styles** : CSS pour présentation

## Architecture Technique

### Stack

```
Frontend seul (pas de backend)
├── HTML5
├── CSS3 (variables CSS, grid, flexbox)
└── Vanilla JavaScript (fetch API)
```

### Flux de Données

```
┌─────────────────┐
│  Navigateur     │
│  (soc-live.html)│
└────────┬────────┘
         │
         ├──fetch──> GitHub API (advisories)
         │              │
         │              └─> Données JSON réelles
         │
         └──fetch──> NVD API (CVE)
                       │
                       └─> Données JSON réelles
```

### Code JavaScript Principal

```javascript
// Chargement GitHub Security Advisories
async function loadGitHubAdvisories() {
  const response = await fetch(
    'https://api.github.com/advisories?per_page=15&sort=published&order=desc'
  );
  const data = await response.json();
  // Traitement des données réelles...
}

// Chargement CVE/NVD
async function loadCVEData() {
  const response = await fetch(
    'https://services.nvd.nist.gov/rest/json/cves/2.0?resultsPerPage=10'
  );
  const data = await response.json();
  // Traitement des CVEs réelles...
}
```

## Fonctionnalités

### 1. Indicateurs de Santé Temps Réel

- **Statut des Sources** : 
  - 🟢 En ligne : API répond normalement
  - 🟡 Chargement : Requête en cours
  - 🔴 Hors ligne : API inaccessible ou erreur

- **Compteurs Réels** :
  - Nombre d'événements chargés par source
  - Total des événements
  - Répartition par sévérité

- **Timestamps Précis** :
  - Dernière mise à jour de chaque source
  - Temps relatif ("Il y a 2 heures")

### 2. Affichage des Événements

- **Tri chronologique** : Plus récents en premier
- **Badges de sévérité** : Couleur selon niveau réel
- **Traçabilité** : Chaque événement affiche son ID source (GHSA-xxx ou CVE-xxx)
- **Horodatage réel** : Date de publication officielle

### 3. Rafraîchissement

- **Manuel** : Bouton "Actualiser les données"
- **Automatique** : Toutes les 5 minutes
- **Indicateur** : Bouton désactivé pendant le chargement

## Limitations et Transparence

### Ce que fait le SOC :

✅ Affiche des vulnérabilités réelles de sources officielles  
✅ Met à jour les données régulièrement  
✅ Fournit une vue centralisée des événements de sécurité  
✅ Indique le statut de santé des sources de données  

### Ce que le SOC ne fait PAS :

❌ **Aucune protection active** sur les systèmes utilisateurs  
❌ **Aucune détection locale** de menaces  
❌ **Aucune action automatique** sur les alertes  
❌ **Aucun EDR/Antivirus** installé ou actif  
❌ **Lecture seule** - pas de mitigation des vulnérabilités  

## APIs Utilisées

### GitHub Security Advisories API

**Endpoint** : `https://api.github.com/advisories`  
**Documentation** : https://docs.github.com/en/rest/security-advisories  
**Rate limit** : 60 requêtes/heure (sans authentification)  
**Données** : Avis de sécurité GitHub, vulnérabilités packages open-source

**Exemple de réponse** :
```json
{
  "ghsa_id": "GHSA-xxxx-xxxx-xxxx",
  "summary": "SQL Injection in package-name",
  "description": "A SQL injection vulnerability...",
  "severity": "high",
  "published_at": "2024-12-15T10:30:00Z"
}
```

### NVD CVE API

**Endpoint** : `https://services.nvd.nist.gov/rest/json/cves/2.0`  
**Documentation** : https://nvd.nist.gov/developers/vulnerabilities  
**Rate limit** : 5 requêtes/30 secondes (sans API key)  
**Données** : Base de données CVE du NIST (National Vulnerability Database)

**Exemple de réponse** :
```json
{
  "cve": {
    "id": "CVE-2024-12345",
    "descriptions": [{
      "lang": "en",
      "value": "Buffer overflow in application X..."
    }],
    "metrics": {
      "cvssMetricV31": [{
        "cvssData": {
          "baseSeverity": "CRITICAL"
        }
      }]
    },
    "published": "2024-12-16T08:00:00.000"
  }
}
```

## Déploiement

### Cloudflare Pages

Le SOC fonctionnel est compatible avec le déploiement Cloudflare Pages :

- ✅ **Pas de backend requis** : Tout est client-side
- ✅ **APIs publiques** : Accessibles via CORS
- ✅ **Static site** : Fichier HTML unique
- ✅ **Performance** : Edge CDN Cloudflare

### Build

Aucun build requis. Le fichier `public/soc-live.html` peut être servi directement.

```bash
# Développement local
cd public
python3 -m http.server 8000
# Ouvrir http://localhost:8000/soc-live.html
```

## Tests et Validation

### Test 1 : Vérifier GitHub API

```bash
curl https://api.github.com/advisories?per_page=5
```

✅ Doit retourner des avis de sécurité réels en JSON

### Test 2 : Vérifier NVD API

```bash
curl https://services.nvd.nist.gov/rest/json/cves/2.0?resultsPerPage=5
```

✅ Doit retourner des CVEs réelles en JSON

### Test 3 : Validation de la page

1. Ouvrir `soc-live.html` dans un navigateur
2. Vérifier que les indicateurs de santé passent à "🟢 En ligne"
3. Vérifier que des événements s'affichent avec IDs réels (GHSA-xxx ou CVE-xxx)
4. Vérifier les timestamps (doivent être récents)
5. Cliquer sur "Actualiser" et voir le rechargement

## Maintenance

### Monitoring

- Surveiller les rate limits des APIs
- Vérifier la disponibilité des endpoints
- Tester périodiquement le chargement des données

### Évolutions Possibles

1. **Plus de sources** : Ajouter CERT-FR RSS, ANSSI, etc.
2. **Filtres** : Par sévérité, par source, par date
3. **Recherche** : Rechercher dans les événements
4. **Persistence** : LocalStorage pour cache
5. **Graphiques** : Visualisation des tendances

## Conclusion

Le SOC Live est maintenant un **SOC fonctionnel** qui :

- ✅ Utilise des sources de données **réelles et publiques**
- ✅ Affiche des événements **réellement générés**
- ✅ Fournit des indicateurs de **santé et volume réels**
- ✅ Ne contient **aucune donnée fictive ou simulée**

Il reste un outil **informatif en lecture seule**, mais toutes les données affichées sont maintenant **vérifiables et traçables**.

---

**Dernière mise à jour** : 2024-12-17  
**Version** : 2.0.0-functional
