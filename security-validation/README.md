# Sentinel Defensive Validation Lab

Ce répertoire définit le banc de validation défensive de Sentinel Quantum Vanguard.

## Principe

Le moteur est un **adversary-simulation harness** destiné exclusivement aux environnements possédés, de test ou explicitement autorisés. Il valide les protections de Sentinel sans fournir de capacité d'attaque autonome contre des tiers.

La suite privilégie les entrées synthétiques, les artefacts de test approuvés et les scénarios non destructifs. Aucun identifiant réel, aucune donnée personnelle réelle et aucun malware opérationnel ne doivent être introduits dans la CI.

## Contrôles obligatoires

Avant toute exécution :

1. cible explicitement enregistrée dans le périmètre de test ;
2. fenêtre de test active ;
3. environnement isolé ou réseau de laboratoire ;
4. scénario autorisé dans le catalogue versionné ;
5. journalisation complète ;
6. limitation de débit et kill-switch ;
7. interdiction de cible Internet non explicitement autorisée ;
8. arrêt immédiat sur crash, fuite de données ou sortie du périmètre.

Une éventuelle politique géographique ou de conformité peut compléter ces contrôles, mais **ne remplace jamais l'autorisation technique et contractuelle**.

## Classes de validation

- `input-validation` : JSON/NDJSON malformé, tailles limites, enums et champs inattendus ;
- `replay` : doublons, timestamps obsolètes et rejoués ;
- `threat-intel` : données TI synthétiques incohérentes ou non fiables ;
- `web` : marqueurs de pages de test anti-phishing approuvées ;
- `app-security` : métadonnées APK et profils de permissions synthétiques ;
- `antivirus` : artefacts de test antivirus approuvés ;
- `flood` : charge synthétique bornée pour vérifier rate limiting et stabilité ;
- `integrity` : mutations de configuration sans exécution destructive.

## Critères de sortie

Une campagne est **KO** si elle provoque un crash, une croissance mémoire non bornée, une sortie du périmètre autorisé, l'exécution d'un malware réel ou une perte de traçabilité.

Les scénarios critiques doivent être détectés à 100 %. Les scénarios élevés doivent atteindre au minimum 95 %, sauf exception documentée et approuvée.

## Séparation des projets

Ce banc appartient exclusivement à Sentinel Quantum Vanguard. Il ne doit contenir aucun code, identifiant, configuration, secret, dépendance ou pipeline provenant de **A KI PRI SA YÉ**.

Toute réintroduction d'un identifiant ou d'une dépendance interdite doit faire échouer le contrôle d'isolation CI.
