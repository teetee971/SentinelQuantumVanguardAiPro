# Cadrage RGPD — Sentinel Quantum Vanguard AI Pro

> Document de cadrage. Il ne constitue ni une certification RGPD ni un avis juridique.

**Dernière mise à jour : 3 septembre 2026**

## Statut documentaire

Le dépôt applique une approche de minimisation et de séparation des composants, mais la conformité RGPD ne peut pas être déduite du seul code source. Elle dépend notamment du produit réellement déployé, des traitements effectués, des utilisateurs concernés et des finalités retenues.

Aucune mention de « conforme », « 100 % local », « zéro transfert » ou de base légale déterminée ne doit être considérée comme une preuve sans vérification du déploiement concerné.

## Données potentiellement traitées

Certains composants Android peuvent manipuler localement des informations techniques nécessaires à leurs fonctions. Les données effectivement traitées doivent être vérifiées dans le code et le manifeste de la version concernée.

À la date de cette révision, le manifeste Android ne déclare pas de permissions d'accès aux journaux d'appels, SMS, contacts ou localisation. Toute future permission devra être justifiée et contrôlée par CI.

## Principes à appliquer

Pour tout traitement de données personnelles :

- définir une finalité précise et une base légale appropriée ;
- appliquer la minimisation des données ;
- définir une durée de conservation documentée ;
- informer les personnes concernées lorsque requis ;
- sécuriser les données pendant leur cycle de vie ;
- documenter les destinataires et transferts éventuels ;
- prévoir les mécanismes nécessaires pour l'exercice des droits ;
- réévaluer les traitements après toute évolution fonctionnelle ou d'architecture.

## Transferts et services externes

L'architecture Sentinel ne doit pas introduire de dépendance opérationnelle à Firebase ou à un autre projet externe non autorisé. Les contrôles d'isolation du dépôt constituent une mesure technique de gouvernance ; ils ne remplacent pas une analyse juridique ou une vérification du trafic réseau effectivement généré par une version déployée.

Si un backend, une télémétrie, une synchronisation ou un service tiers est ajouté, la documentation RGPD devra être révisée avant déploiement.

## Analyse de risques

Les risques doivent être évalués selon le traitement réellement effectué et non selon une hypothèse générale de « faible risque ». Les points à examiner comprennent notamment l'accès non autorisé, la perte d'appareil, l'exposition de journaux, les erreurs de classification et les transferts vers des services externes.

Une AIPD/DPIA doit être envisagée lorsque les critères réglementaires applicables sont réunis ; son absence ne doit pas être affirmée automatiquement dans la documentation du projet.

## Droits des personnes

Les modalités d'accès, rectification, effacement, limitation, portabilité et opposition doivent être implémentées ou documentées en fonction des traitements réellement effectués. Une capacité présente dans une spécification ou une interface n'est pas considérée comme implémentée sans preuve dans le code et un test correspondant.

## Gouvernance

Avant une mise en production impliquant des données personnelles, il est recommandé de faire valider :

1. le registre des traitements ;
2. les notices d'information ;
3. les bases légales ;
4. les durées de conservation ;
5. les contrats et transferts éventuels ;
6. les mesures de sécurité ;
7. la procédure de gestion des incidents et demandes de droits.

## Révision

Ce document doit être révisé à chaque changement de permission Android, stockage, réseau, télémétrie, backend, authentification ou traitement de données personnelles.
