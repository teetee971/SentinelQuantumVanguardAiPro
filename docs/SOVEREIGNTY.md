# Souveraineté numérique — Sentinel Quantum Vanguard AI Pro

## Statut du document

Ce document décrit les principes d'architecture et les options de déploiement actuellement documentées. Il ne constitue pas une certification, une homologation ou une garantie réglementaire.

## 1. Périmètre actuel

Sentinel est maintenu comme une plateforme défensive à périmètre contrôlé. Le dépôt contient notamment :

- une interface web/PWA et son pipeline de build ;
- des contrôles de gouvernance, d'intégrité et d'isolation ;
- des scénarios de simulation et de fuzzing ;
- un composant Android source séparé dans `native-android-app/`.

Aucune protection antivirus/EDR/SOC de production n'est revendiquée par le dépôt. Les composants expérimentaux ne doivent pas être présentés comme des capacités opérationnelles validées tant qu'une validation correspondante n'a pas été exécutée.

## 2. Indépendance vis-à-vis des autres projets

Sentinel et **A KI PRI SA YÉ** sont deux projets distincts.

Le dépôt Sentinel ne doit pas introduire de dépendance opérationnelle à A KI PRI SA YÉ, à Firebase ou à une autre infrastructure appartenant à cet autre projet.

Les références Firebase présentes dans les scanners d'isolation, tests négatifs et fixtures de sécurité sont intentionnelles : elles servent à vérifier que des dépendances interdites seraient détectées. Elles ne constituent pas des dépendances d'exécution.

## 3. Déploiement web

Le déploiement de production est prévu via l'intégration Git de Cloudflare Pages :

- branche de production : `main` ;
- commande : `npm run build` ;
- sortie : `frontend/dist` ;
- racine du projet : `/`.

Cloudflare Pages prend en charge les déploiements automatiques depuis GitHub lorsqu'une intégration Git est configurée. Les paramètres effectifs doivent rester cohérents avec le dépôt et le tableau de bord Cloudflare.

## 4. Données et réseau

Il ne faut pas transformer une propriété du frontend en affirmation générale sur toutes les configurations futures.

Les affirmations relatives à l'absence de télémétrie, au stockage local, aux appels réseau ou à la résidence des données doivent être vérifiées sur la version effectivement déployée et sur les fonctionnalités réellement activées.

Aucune promesse de fonctionnement « 100 % offline », de résidence européenne automatique ou de transfert nul ne doit être considérée comme une garantie universelle sans configuration et test correspondants.

## 5. Android

Le code source Android est conservé séparément dans `native-android-app/`.

Aucun APK précompilé et signé n'est actuellement annoncé comme disponible. Les workflows et guides de build décrivent une chaîne de génération ; ils ne constituent pas la preuve qu'un APK a été compilé, signé et validé.

Une future distribution Android devra être accompagnée d'un artefact réellement généré, signé, vérifié et identifié par sa version et son empreinte.

## 6. Référentiels et certifications

Sentinel peut être conçu ou déployé en tenant compte de référentiels tels que l'ANSSI, le RGS, le RGPD, NIS2 ou SecNumCloud lorsque le périmètre et l'hébergement le justifient.

Cela ne signifie pas que le dépôt est certifié, homologué ou conforme à chacun de ces référentiels. Toute conformité réglementaire doit être évaluée sur le périmètre réel, avec les contrôles et preuves nécessaires.

Les termes « certifié », « conforme », « homologué », « SecNumCloud », « ISO 27001 », « CSPN » ou « HDS » ne doivent être utilisés comme statut actuel que lorsqu'une preuve officielle correspondante existe.

## 7. Traçabilité et audit

Le dépôt contient des contrôles automatisés de gouvernance, d'intégrité et d'isolation. Ces contrôles constituent des mécanismes de vérification ; ils ne prouvent pas à eux seuls l'absence de vulnérabilité.

Une validation doit toujours distinguer :

1. code présent dans le dépôt ;
2. test exécuté avec succès ;
3. déploiement effectivement réalisé ;
4. capacité opérationnelle réellement observée ;
5. audit ou certification externe, lorsqu'il existe.

## 8. Modèle de risque

Aucun niveau de risque « nul » ou « zéro risque » ne doit être attribué au logiciel sur la seule base de son architecture ou de ses tests internes.

Les risques doivent être documentés avec leur périmètre, leur hypothèse, leur niveau de confiance et les mesures de réduction associées.

## 9. Principes de déploiement souverain

Pour un déploiement institutionnel, les choix d'hébergement, de traitement des données, de journalisation, de gestion des identités, de sauvegarde et de supervision doivent être définis avec l'organisation déployeuse.

Le self-hosting peut être étudié lorsque la maîtrise de l'infrastructure est une exigence. Il ne constitue pas automatiquement une garantie de conformité.

## 10. Règle éditoriale

La documentation Sentinel doit rester alignée sur les preuves disponibles dans le dépôt et les exécutions réellement effectuées.

Une fonctionnalité future doit être identifiée comme future. Une simulation doit être identifiée comme simulation. Une validation CI doit être distinguée d'un audit de sécurité. Une possibilité de déploiement doit être distinguée d'un déploiement réellement effectué.

**Version :** 2.0 — septembre 2026
