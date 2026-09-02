# Sentinel OSINT & Influence Intelligence

Module défensif d'analyse de sources ouvertes pour détecter des campagnes coordonnées, suivre des narratifs et cartographier leur propagation.

## Périmètre

Le module analyse uniquement des données publiques obtenues par des moyens autorisés et respectueux des conditions d'utilisation des sources. Il ne contourne pas les contrôles d'accès, n'accède pas aux comptes privés et ne tente pas d'identifier une personne comme malveillante sur la seule base d'un score algorithmique.

## Architecture

- `schemas/observed-event.schema.json` : contrat normalisé des observations OSINT.
- `coordination/coordination-engine.js` : score explicable de coordination comportementale.
- `narratives/narrative-engine.js` : extraction et rapprochement déterministe de marqueurs narratifs multilingues.
- `graph/propagation-graph.js` : construction d'un graphe de propagation à partir d'observations déjà normalisées.

## Principes

1. Provenance obligatoire pour chaque observation.
2. Séparation entre observation, inférence et attribution.
3. Les scores sont des indicateurs de risque/coordination, jamais des preuves autonomes.
4. Les contenus supprimés, privés ou obtenus par contournement ne sont pas collectés.
5. Les données personnelles sont minimisées et conservées selon une politique de rétention définie par le déploiement.
6. Toute sortie IA doit rester explicable et réversible par un analyste humain.

## Déploiement à grande échelle

La collecte temps réel devra être branchée sur des connecteurs autorisés, avec quotas, back-pressure, déduplication, horodatage UTC, cache, contrôle de provenance et journal d'audit. Le dépôt ne fournit volontairement aucun scraper de contournement de plateforme.

## Séparation Sentinel / A KI PRI SA YÉ

Ce module appartient exclusivement à Sentinel Quantum Vanguard. Il ne doit partager aucun code, identifiant, secret, configuration, dépendance ou pipeline avec A KI PRI SA YÉ.
