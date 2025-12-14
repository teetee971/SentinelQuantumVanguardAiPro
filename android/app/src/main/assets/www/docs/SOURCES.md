# Sources de Données — Sentinel Quantum Vanguard AI Pro

## Vue d'Ensemble

Sentinel agrège des données de threat intelligence provenant exclusivement de sources publiques officielles et vérifiées. Aucune source privée, commerciale ou non documentée n'est utilisée.

---

## Sources Gouvernementales US

### CISA (Cybersecurity and Infrastructure Security Agency)
- **URL** : https://www.cisa.gov/
- **Type** : Alertes, advisories, bulletins
- **Fréquence** : Continue (RSS/API)
- **Format** : XML, JSON
- **Contenu** : Vulnérabilités critiques, campagnes actives, recommandations

### US-CERT (United States Computer Emergency Readiness Team)
- **URL** : https://www.us-cert.gov/
- **Type** : Bulletins techniques, alertes
- **Fréquence** : Quotidienne
- **Format** : HTML, RSS
- **Contenu** : Analyses techniques, IOC, mitigations

### NVD (National Vulnerability Database)
- **URL** : https://nvd.nist.gov/
- **Type** : Base de données CVE
- **Fréquence** : Continue
- **Format** : JSON (API REST)
- **Contenu** : CVE, scores CVSS, références, patchs

---

## Sources Gouvernementales Européennes

### CERT-FR (Centre gouvernemental de veille, d'alerte et de réponse aux attaques informatiques)
- **URL** : https://www.cert.ssi.gouv.fr/
- **Type** : Alertes, avis de sécurité, bulletins
- **Fréquence** : Plusieurs fois par semaine
- **Format** : HTML, RSS
- **Contenu** : Menaces ciblant la France, recommandations, IOC

### ENISA (European Union Agency for Cybersecurity)
- **URL** : https://www.enisa.europa.eu/
- **Type** : Rapports, études, alertes
- **Fréquence** : Hebdomadaire/Mensuelle
- **Format** : PDF, HTML
- **Contenu** : Tendances UE, best practices, threat landscape

### NCSC-UK (National Cyber Security Centre)
- **URL** : https://www.ncsc.gov.uk/
- **Type** : Advisories, guidance, rapports
- **Fréquence** : Quotidienne
- **Format** : HTML, RSS
- **Contenu** : Menaces UK, recommandations techniques, analyses

---

## Sources Techniques

### CVE (Common Vulnerabilities and Exposures)
- **URL** : https://cve.mitre.org/
- **Type** : Identifiants de vulnérabilités
- **Fréquence** : Continue
- **Format** : JSON
- **Contenu** : ID CVE, descriptions, références

### MITRE ATT&CK
- **URL** : https://attack.mitre.org/
- **Type** : Framework de tactiques et techniques
- **Fréquence** : Trimestrielle (mises à jour)
- **Format** : JSON, STIX
- **Contenu** : Tactiques, techniques, procédures (TTP), groupes APT

### AlienVault OTX (Open Threat Exchange)
- **URL** : https://otx.alienvault.com/
- **Type** : IOC communautaires
- **Fréquence** : Continue
- **Format** : JSON (API)
- **Contenu** : IP malveillantes, domaines, hash de malware

---

## Sources Commerciales Ouvertes

### Abuse.ch
- **URL** : https://abuse.ch/
- **Type** : Malware tracking, botnet C2
- **Fréquence** : Continue
- **Format** : CSV, JSON
- **Contenu** : URLhaus, Feodo Tracker, SSL Blacklist

### PhishTank
- **URL** : https://www.phishtank.com/
- **Type** : Base phishing
- **Fréquence** : Continue
- **Format** : JSON, CSV
- **Contenu** : URLs de phishing vérifiées

### Spamhaus
- **URL** : https://www.spamhaus.org/
- **Type** : IP/domaines malveillants
- **Fréquence** : Continue
- **Format** : Zone files, API
- **Contenu** : Spam, malware, botnet

---

## Agrégation et Traitement

### Pipeline de Données

1. **Collecte**
   - Polling périodique des sources (1h à 24h selon la source)
   - Webhooks pour sources supportant les notifications push
   - Parsing des formats divers (XML, JSON, RSS, HTML)

2. **Normalisation**
   - Conversion vers format interne standardisé
   - Déduplication des IOC
   - Enrichissement avec métadonnées (timestamp, confidence, source)

3. **Corrélation**
   - Cross-référencement entre sources
   - Scoring de confiance basé sur nombre de sources
   - Identification de campagnes liées

4. **Distribution**
   - Diffusion aux agents endpoint (IOC, signatures)
   - Publication sur SOC Live (visualisation)
   - Stockage historique (analyse tendances)

### Fréquences de Mise à Jour

| Source | Fréquence Polling | Délai Propagation | Priorité |
|--------|-------------------|-------------------|----------|
| CISA | 1 heure | < 30 min | Haute |
| US-CERT | 2 heures | < 1 heure | Haute |
| CERT-FR | 3 heures | < 1 heure | Haute |
| NVD | 6 heures | < 2 heures | Moyenne |
| ENISA | 24 heures | < 6 heures | Basse |
| Abuse.ch | 1 heure | < 30 min | Haute |

---

## Critères de Sélection des Sources

Toutes les sources doivent respecter ces critères :

1. **Fiabilité**
   - Organisme reconnu et établi
   - Historique de qualité prouvé
   - Processus de vérification documenté

2. **Gratuité**
   - Accès public sans coût
   - Pas de limitation drastique (rate limits raisonnables)
   - Licence compatible avec notre usage

3. **Fraîcheur**
   - Mise à jour régulière (au moins hebdomadaire)
   - Données temps réel ou quasi temps réel
   - Historique disponible

4. **Format**
   - Machine-readable (JSON, XML, CSV)
   - API ou flux RSS disponible
   - Documentation technique accessible

5. **Légalité**
   - Conforme au RGPD (pour sources EU)
   - Pas de données personnelles sensibles
   - Termes d'utilisation respectés

---

## Sources Exclues

### Pourquoi Certaines Sources Ne Sont Pas Utilisées

#### Sources Commerciales Payantes
- **CrowdStrike Falcon Intelligence** : Payant, non accessible gratuitement
- **Recorded Future** : Payant, coût prohibitif
- **Mandiant Threat Intelligence** : Payant, accès restreint

#### Sources Non Vérifiables
- **Pastebin générique** : Trop de faux positifs, non modéré
- **Forums underground** : Légalité douteuse, fiabilité incertaine
- **Twitter OSINT brut** : Non vérifié, bruit important

#### Sources Avec Restrictions Légales
- **Feeds nationaux classifiés** : Accès restreint, nécessite clearance
- **Données LEA (Law Enforcement)** : Non publiques, confidentielles

---

## Ajout de Nouvelles Sources

### Processus de Validation

1. **Proposition** : Via GitHub Issue avec justification
2. **Évaluation** : Vérification des 5 critères ci-dessus
3. **Test** : Intégration en mode test pendant 30 jours
4. **Validation** : Analyse qualité (faux positifs, latence, couverture)
5. **Production** : Activation complète et documentation

### Critères de Rejet
- Taux de faux positifs > 5%
- Latence moyenne > 6 heures
- Disponibilité < 95%
- Violation des termes d'utilisation
- Qualité des données insuffisante

---

## Transparence

La liste complète des sources est maintenue à jour dans ce document. Toute modification fait l'objet d'un commit Git avec justification.

**Dernière mise à jour** : Décembre 2024  
**Sources actives** : 11  
**Sources en test** : 0  
**Sources rejetées (historique)** : 3
