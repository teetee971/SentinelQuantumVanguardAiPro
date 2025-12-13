# Sentinel Quantum Vanguard AI Pro

## Vue d'Ensemble

Sentinel Quantum Vanguard AI Pro est une plateforme de cybersécurité hybride combinant analyse cloud et protection endpoint locale.

### Architecture

#### Cloud / SOC
- **SOC Live** : Visualisation mondiale des menaces en temps réel
- **Threat Intelligence** : Agrégation de sources publiques officielles (CISA, US-CERT, CERT-FR, ENISA, NCSC-UK, NVD)
- **Analyse globale** : Corrélation de données, identification de patterns
- **Diffusion des règles** : Distribution périodique des IOC aux agents

#### Endpoint
- **Agent PC** (Windows/Linux) : Surveillance processus, analyse comportementale, blocage local
- **Agent Android** (sans root) : Analyse APK, détection permissions abusives, filtrage DNS
- **Protection locale** : Détection et neutralisation autonomes

## États des Modules

### 🟢 Actif
- **SOC Live** — Carte mondiale, flux d'alertes, journal d'événements (lecture seule)
- **Threat Intelligence** — Agrégation sources publiques, IOC, CVE, tendances

### 🟡 En Développement
- **Antivirus IA** — Protection antimalware avancée (scanning, signatures, heuristique)
- **EDR** — Endpoint Detection & Response (détection comportementale, forensique, hunting)

### 💤 Roadmap
- **Agents IA** — Automatisation avancée, apprentissage continu
- **Network Security** — IDS/IPS, DPI, segmentation réseau
- **CSPM** — Cloud Security Posture Management

## Principes Fondamentaux

### Transparence Totale
- Distinction claire entre fonctionnalités actives, en développement et roadmap
- Documentation complète des limites et cas non couverts
- Code source ouvert pour audit indépendant
- Aucune promesse mensongère ou exagération marketing

### Respect de la Vie Privée
- Aucune interception du trafic utilisateur par le cloud
- Collecte minimale de données (métriques anonymisées uniquement)
- Pas de revente de données à des tiers
- Protection locale autonome sans dépendance cloud en temps réel

### Sécurité par Conception
- Architecture Zero Trust
- Defense in Depth
- Principe du moindre privilège
- Isolation des composants

## Installation

### Prérequis
- **PC** : Windows 10/11 ou Linux (Ubuntu 20.04+, Debian 11+)
- **Android** : Version 6.0 (API 23) minimum
- **Réseau** : Connexion internet pour mise à jour des IOC

### Agents Endpoint

#### PC (En développement)
```bash
# Installation Linux (exemple)
wget https://releases.sentinel-ai.pro/agent-linux-latest.deb
sudo dpkg -i agent-linux-latest.deb
sudo systemctl start sentinel-agent
```

#### Android (En développement)
1. Activer "Sources inconnues" dans les paramètres
2. Télécharger l'APK depuis download.html
3. Installer et accorder les permissions requises
4. Configurer le VPN local (optionnel)

## Configuration

### Fichier de Configuration
```yaml
# /etc/sentinel/config.yml (Linux)
# C:\ProgramData\Sentinel\config.yml (Windows)

endpoint:
  enabled: true
  update_interval: 3600  # Mise à jour IOC toutes les heures
  
protection:
  behavioral_analysis: true
  process_monitoring: true
  network_filtering: true
  auto_quarantine: true

logging:
  level: info
  path: /var/log/sentinel/
  max_size: 100MB
```

## Utilisation

### SOC Live
Accédez à `soc-live.html` pour :
- Visualiser les menaces mondiales
- Consulter les alertes récentes
- Analyser les tendances
- Parcourir les bulletins CERT/CISA

### Threat Intelligence
Accédez à `threat-intel.html` pour :
- Rechercher des IOC spécifiques
- Consulter les CVE récents
- Suivre les campagnes APT
- Recevoir des alertes sectorielles

### Agent Endpoint
Les agents fonctionnent automatiquement en arrière-plan :
- Mises à jour IOC périodiques depuis le cloud
- Surveillance continue des processus et connexions
- Blocage automatique basé sur les règles
- Logs détaillés pour investigation

## Dépannage

### L'agent ne démarre pas
```bash
# Vérifier les logs
sudo tail -f /var/log/sentinel/agent.log

# Vérifier le statut du service
sudo systemctl status sentinel-agent

# Redémarrer l'agent
sudo systemctl restart sentinel-agent
```

### Faux Positifs
Si un processus légitime est bloqué :
1. Consulter les logs pour identifier la règle
2. Ajouter une exception dans la configuration
3. Redémarrer l'agent
4. Signaler le faux positif via GitHub Issues

### Performance
Si l'agent consomme trop de ressources :
- Réduire la fréquence d'analyse dans la configuration
- Désactiver l'analyse comportementale temporairement
- Exclure certains répertoires du monitoring

## Documentation Complémentaire

- [ROADMAP.md](ROADMAP.md) — Feuille de route et fonctionnalités futures
- [SOURCES.md](SOURCES.md) — Liste détaillée des sources de données
- [Modèle de sécurité](../security-model.html) — Architecture et flux de données
- [Limites & éthique](../limits.html) — Ce que Sentinel ne fait pas

## Support

### Communauté
- **GitHub Issues** : [Signaler un bug ou demander une fonctionnalité](https://github.com/teetee971/SentinelQuantumVanguardAiPro/issues)
- **Discussions** : [Participer aux discussions](https://github.com/teetee971/SentinelQuantumVanguardAiPro/discussions)

### Contribution
Le projet est open source. Les contributions sont bienvenues :
- Correction de bugs
- Amélioration de la documentation
- Nouvelles fonctionnalités
- Signatures de détection

## Licence

Apache License 2.0 — Voir LICENSE pour le texte complet.

## Disclaimer

Sentinel Quantum Vanguard AI Pro est une plateforme de veille et de démonstration en cybersécurité. Les fonctionnalités de protection endpoint sont en développement. Aucune garantie n'est fournie. Utilisation à vos propres risques.
