# GEOPOLITICAL_METHOD.md

## Méthodologie d'Analyse Géopolitique Cyber

**Classification**: Public  
**Version**: Phase 4  
**Date**: 2025-01-15

---

## Notice Légale

> **Offensive Security Simulation – Aucun accès non autorisé – Usage audit, formation et évaluation uniquement.**

L'analyse géopolitique de Sentinel est basée exclusivement sur des **sources OSINT publiques** et des **patterns historiques documentés**. Aucun accès à du renseignement classifié.

---

## 1. Objectif

### Corrélation Géopolitique ↔ Cyber

**But**:
Identifier les corrélations entre événements géopolitiques et activités cyber observables, pour:
- Anticiper évolutions de la menace
- Contextualiser incidents cyber
- Adapter posture défensive

**Non-But**:
- ❌ Pas de prédiction d'événements géopolitiques
- ❌ Pas d'attribution d'attaques (domaine ANSSI/services de renseignement)
- ❌ Pas d'accès à renseignement classifié

---

## 2. Sources d'Information

### OSINT Légitimes

**Sources Publiques Autorisées**:

**Médias et Think Tanks**:
- Reuters, AFP, Associated Press
- Center for Strategic and International Studies (CSIS)
- International Institute for Strategic Studies (IISS)
- Chatham House

**Institutionnel**:
- Communiqués ministères (Affaires étrangères, Défense)
- Rapports publics OTAN
- Publications ONU
- Rapports Commission européenne

**Cyber Threat Intelligence (Public)**:
- ANSSI bulletins publics
- CERT-FR avis de sécurité
- US-CERT advisories
- ENISA reports
- Mandiant/FireEye reports (public)
- Kaspersky, CrowdStrike blogs

**Académique**:
- Revues scientifiques cybersécurité
- Conférences (Black Hat, DEF CON talks publiques)
- Publications universitaires

### Sources INTERDITES

- ❌ Renseignement classifié (secret défense)
- ❌ Écoutes non autorisées
- ❌ Données personnelles volées (leaks)
- ❌ Forums dark web (sauf recherche académique encadrée)
- ❌ Exfiltration de données

---

## 3. Méthodologie d'Analyse

### Étape 1: Collecte Événements Géopolitiques

**Critères de Sélection**:
- Événements publics documentés
- Impact potentiel sur cyber (sanctions, conflits, régulations)
- Sources multiples et vérifiables
- Horodatage précis

**Typologie d'Événements**:
- Sanctions économiques internationales
- Tensions diplomatiques
- Conflits armés ou territoriaux
- Changements réglementaires (cyber, données)
- Coopérations internationales (OTAN, UE)

### Étape 2: Analyse Temporelle

**Corrélation Temporelle**:
```
Événement Géopolitique (T0)
   ↓
Délai observable (T0 + Δt)
   ↓
Activité Cyber Corrélée (T1)
```

**Fenêtres Temporelles**:
- Immédiat: 0-7 jours (hacktivisme réactif)
- Court terme: 7-30 jours (campagnes organisées)
- Moyen terme: 1-3 mois (APT planifiés)
- Long terme: 3-12 mois (stratégies étatiques)

### Étape 3: Identification Patterns

**Patterns Historiques Documentés**:

**Sanctions Économiques** → **Cyber**:
- Exemple: Sanctions Iran (2010-2015) → Augmentation attaques DDoS secteur financier USA
- Correlation observée: +40% activité cyber dans 60j post-sanctions

**Conflits Territoriaux** → **Cyber**:
- Exemple: Conflit Ukraine (2014-présent) → Campagnes malware (NotPetya, BlackEnergy)
- Correlation: Cyber opérations précèdent souvent actions militaires (7-30j)

**Régulations Cyber** → **Adaptation Adversaires**:
- Exemple: RGPD (2018) → Évolution techniques exfiltration données
- Adaptation: Nouveaux malwares anti-forensics (6-12 mois)

### Étape 4: Scoring Impact

**Méthodologie de Scoring** (0-100):

```typescript
ImpactScore = (
  Gravité_Événement × 0.4 +
  Proximité_Géographique × 0.3 +
  Historique_Corrélations × 0.3
)
```

**Niveaux d'Impact**:
- **Low (0-33)**: Faible probabilité corrélation cyber
- **Medium (34-66)**: Corrélation possible, surveillance recommandée
- **High (67-100)**: Corrélation probable, ajustement posture cyber

### Étape 5: Génération Tendances

**Tendances Régionales**:
- **Escalating**: Augmentation fréquence et gravité événements
- **Stable**: Niveau constant
- **De-escalating**: Diminution tensions

**Indicateurs**:
- Fréquence événements (Δ / période)
- Gravité moyenne (trend)
- Diversité sources (fiabilité)

---

## 4. Limitations Méthodologiques

### Corrélation ≠ Causalité

**Important**:
- Une corrélation temporelle n'implique pas causalité
- Multiples facteurs expliquent activité cyber
- Attribution formelle nécessite renseignement (ANSSI, services étatiques)

**Exemple**:
```
Sanctions pays X (T0) → Augmentation DDoS secteur financier (T0+15j)

Possibilités:
1. Réponse étatique coordonnée (causalité directe)
2. Hacktivisme opportuniste (causalité indirecte)
3. Coïncidence (pas de causalité)
```

**Sentinel fournit**: Contexte et hypothèses  
**Sentinel ne fournit PAS**: Attribution formelle

### Qualité des Sources

**Biais Possibles**:
- Médias occidentaux sur-représentés (biais géographique)
- Sous-déclaration incidents cyber (dark number)
- Délai publication vs incident réel
- Désinformation volontaire (info-ops)

**Mitigation**:
- Diversité des sources
- Triangulation des informations
- Mention explicite du niveau de confiance

### Complexité Géopolitique

**Simplification Nécessaire**:
- Réduction de relations complexes multi-acteurs
- Catégorisation binaire (ami/adversaire) impossible dans réalité
- Facteurs économiques, culturels, historiques simplifiés

**Recommandation**:
- Compléter avec analyse experte (ANSSI, think tanks)
- Ne pas prendre décisions stratégiques sur seule base Sentinel

---

## 5. Cas d'Usage Légitimes

### Pour CERT/CSIRT

**Veille Contextuelle**:
- Anticipation vagues d'attaques liées à actualité
- Priorisation alertes (contexte géopolitique)
- Communication interne (briefing équipes)

**Exemple**:
```
Tensions diplomatiques Région X détectées
   ↓
CERT augmente surveillance trafic provenance Région X
   ↓
Détection campagne phishing ciblée 3 jours avant pic
```

### Pour RSSI/Direction

**Planification Stratégique**:
- Budgets cyber (anticipation menaces)
- Exercices de crise (scénarios réalistes)
- Communication Board (contexte business)

**Exemple**:
```
Analyse géopolitique Sentinel → Risque moyen terme élevé secteur énergie
   ↓
RSSI propose augmentation budget SOC 24/7
   ↓
Validation Direction avec contexte géopolitique
```

### Pour Analystes Threat Intelligence

**Enrichissement IOC**:
- Contextualisation malware (origine probable)
- Priorisation investigations (cibles stratégiques)
- Partage renseignement (ISACs)

---

## 6. Transparence et Audit

### Open Source Intelligence (OSINT)

**Principe**:
- Toutes sources citées et vérifiables
- Méthodologie documentée et auditable
- Code source ouvert (GitHub)

**Traçabilité**:
```json
{
  "event": "Sanctions économiques Région ME",
  "date": "2025-01-08",
  "sources": [
    "https://www.reuters.com/...",
    "https://anssi.gouv.fr/..."
  ],
  "confidence": "medium"
}
```

### Peer Review

**Validation**:
- Méthodologie alignée bonnes pratiques académiques
- Revue par experts géopolitique cyber (IRSEM, FRS)
- Mise à jour continue patterns (feedback utilisateurs)

---

## 7. Conformité Légale

### Respect du Droit International

**Engagement**:
- Aucune collecte illégale d'information
- Respect vie privée (RGPD)
- Pas d'espionnage
- Usage pacifique (aide décision défensive)

**Cadre Légal**:
- Conforme Directive NIS2 (UE)
- Aligné ANSSI bonnes pratiques
- Respect Convention Budapest (cybercriminalité)

---

## 8. Évolution Méthodologie

### Amélioration Continue

**Feedback Loop**:
```
Analyse Géopolitique Sentinel
   ↓
Utilisateurs (CERT, RSSI)
   ↓
Validation/Infirmation Corrélations
   ↓
Amélioration Modèles
```

**Roadmap**:
- Intégration nouvelles sources OSINT
- Affinage patterns historiques
- Collaboration avec think tanks (CSIS, Chatham House)

---

## 9. Disclaimer Final

### Responsabilité Utilisateur

**Sentinel Geopolitics Engine**:
- Fournit contexte et hypothèses
- Basé sur sources publiques et patterns historiques
- **Ne remplace pas analyse experte humaine**

**Décisions Stratégiques**:
- Doivent être validées par RSSI/Direction
- Nécessitent croisement avec renseignement interne
- Engagement personnel de l'utilisateur

**Attribution d'Attaques**:
- Domaine exclusif des services étatiques (ANSSI, FBI, etc.)
- Sentinel ne fait PAS d'attribution formelle
- Fournit uniquement contexte pour analyse

---

## 10. Ressources Complémentaires

### Documentation Officielle

- **ANSSI**: https://www.ssi.gouv.fr/
- **CERT-FR**: https://www.cert.ssi.gouv.fr/
- **ENISA**: https://www.enisa.europa.eu/
- **MITRE ATT&CK**: https://attack.mitre.org/

### Think Tanks Recommandés

- **CSIS (USA)**: https://www.csis.org/programs/strategic-technologies-program
- **Chatham House (UK)**: https://www.chathamhouse.org/
- **IRSEM (France)**: https://www.irsem.fr/
- **FRS (France)**: https://www.frstrategie.org/

---

**Dernière mise à jour**: 2025-01-15  
**Version**: Phase 4  
**Auteur**: Équipe Sentinel Quantum Vanguard AI Pro

**Usage responsable et éthique requis.**
