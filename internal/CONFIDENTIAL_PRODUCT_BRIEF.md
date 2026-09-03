# SENTINEL QUANTUM VANGUARD AI PRO
## Document interne confidentiel

**Usage :** interne uniquement  
**Version de référence :** 2.0.0-pro  
**État :** plateforme défensive contrôlée

## 1. Positionnement actuel

Sentinel Quantum Vanguard AI Pro est une plateforme de cybersécurité défensive orientée gouvernance, audit, simulation contrôlée, provenance des preuves et validation des décisions.

Le dépôt ne doit avoir aucune dépendance opérationnelle vers A KI PRI SA YÉ, Firebase ou une autre application externe non explicitement autorisée par l'architecture Sentinel.

Le projet ne doit pas être présenté comme un antivirus, un EDR ou un système de réponse autonome pleinement opérationnel tant que les capacités correspondantes n'ont pas été implémentées et validées.

## 2. Architecture de référence

- Web/PWA à la racine, déployé sur Cloudflare Pages.
- Application Android native maintenue dans `native-android-app/`.
- Gouvernance IA dans `ai-governance/`.
- Moteurs de décision, confiance, impact, actions et provenance dans leurs répertoires dédiés.
- Fuzzing de gouvernance dans `security/fuzz/`.
- Contrôle d'isolation canonique dans `scripts/check-sentinel-isolation.js`.
- Contrôle de pinning GitHub Actions dans `scripts/check-github-actions-pinning.js`.

L'ancien répertoire `ai-modules/` et son système d'agents Phase E/F ont été supprimés : ils étaient non intégrés, obsolètes et essentiellement constitués de placeholders.

## 3. Contrôles runtime

`config/feature-flags.js` ne contient plus de drapeaux d'agents legacy ni de capacités futures fictives. Les contrôles conservés sont limités au backend, aux logs, au mobile, aux capacités de sécurité optionnelles et au mécanisme d'arrêt d'urgence.

Les valeurs par défaut restent conservatrices : écriture backend, streaming live, auto-update et capacités optionnelles sont désactivés.

Le module `config/logging.js` est désormais indépendant des anciennes phases et des anciens agents. Le stockage d'audit de sécurité reste distinct du logging applicatif général.

## 4. Sécurité et séparation

La séparation entre Sentinel et A KI PRI SA YÉ est une contrainte d'architecture, pas une convention documentaire. Les références utilisées dans les tests négatifs d'isolation sont intentionnelles et ne constituent pas des dépendances opérationnelles.

Le contrôle d'isolation doit rester actif dans CI. Il est interdit de supprimer ou d'affaiblir ce contrôle pour faire passer un build.

## 5. Validation

Une correction de code n'est pas une preuve de sécurité.

Les états suivants doivent être distingués :

1. code corrigé ;
2. tests locaux exécutés ;
3. workflows CI réellement exécutés ;
4. contrôles de sécurité exécutés ;
5. validation de release.

Aucune mention de « 100 % », « risque zéro », « certifié », « production-ready » ou équivalent ne doit être utilisée sans preuve correspondant exactement à l'affirmation.

## 6. Nettoyage appliqué en septembre 2026

- suppression des anciens workflows/scanners d'isolation en doublon ;
- suppression des artefacts APK, médias et page d'installation invalides ;
- suppression de la documentation d'activation Phase F obsolète ;
- suppression du système d'agents legacy et de ses modules placeholders ;
- suppression des scripts PowerShell de maintenance/validation non utilisés ;
- simplification du logging ;
- modernisation des contrôles runtime ;
- conservation des garde-fous Firebase/A KI PRI SA YÉ dans les tests d'isolation.

## 7. Règle de maintenance

Avant toute suppression ou activation future : rechercher les références, vérifier les imports et workflows, modifier sur une branche dédiée, exécuter les validations disponibles, puis fusionner uniquement après revue du diff.
