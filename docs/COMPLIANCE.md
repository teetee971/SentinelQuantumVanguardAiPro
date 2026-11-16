# Conformité, sécurité et référentiels — Sentinel Quantum Vanguard AI Pro

## Statut

Ce document décrit le périmètre de conformité documenté du dépôt. Il ne constitue ni une certification, ni une homologation, ni un avis juridique.

## 1. Position actuelle

Sentinel est une plateforme défensive à périmètre contrôlé. Le dépôt fournit notamment une interface web/PWA, des contrôles de gouvernance, des mécanismes d'isolation, des scénarios de simulation et des tests de sécurité.

Aucune certification ANSSI, ISO 27001, SOC 2, HDS, SecNumCloud, CSPN ou RGS n'est revendiquée comme acquise par le dépôt.

Aucune protection antivirus/EDR/SOC de production n'est revendiquée comme actuellement opérationnelle.

## 2. Référentiels

Les référentiels suivants peuvent servir de cadres de conception ou d'évaluation, selon le périmètre réel :

- RGPD : exigences relatives aux traitements de données personnelles ;
- NIS2 : exigences de cybersécurité applicables aux entités concernées ;
- RGS : exigences françaises applicables au périmètre concerné ;
- recommandations ANSSI : bonnes pratiques de sécurité ;
- SecNumCloud : référentiel de qualification d'hébergement, lorsque le service et l'hébergement concernés répondent aux exigences ;
- WCAG/RGAA : accessibilité du produit web.

La présence d'un contrôle technique dans le dépôt ne suffit pas à établir une conformité réglementaire complète.

## 3. Données et confidentialité

Les propriétés relatives au stockage local, aux cookies, à la télémétrie, aux analytics et aux communications réseau doivent être vérifiées sur la version effectivement déployée et pour les fonctionnalités activées.

Il est interdit de transformer une configuration par défaut ou un test statique en garantie générale du type « aucune donnée collectée », « zéro requête externe » ou « aucune donnée transférée » sans preuve correspondante.

Le responsable de traitement et les obligations RGPD doivent être déterminés selon le déploiement réel et les traitements effectivement réalisés.

## 4. Sécurité applicative

Le dépôt contient des contrôles automatisés, notamment :

- gouvernance des fonctions sensibles ;
- contrôle d'isolation de Sentinel ;
- fuzzing de la gouvernance ;
- contrôle de l'épinglage des actions GitHub ;
- validations frontend ;
- analyse CodeQL lorsqu'elle est effectivement exécutée par GitHub.

Un résultat CI est une preuve de l'exécution du contrôle concerné. Il ne constitue pas une preuve d'absence générale de vulnérabilité.

Les contrôles doivent être interprétés à partir de leurs exécutions réelles. Un job interrompu ou bloqué avant ses étapes n'est pas un test de sécurité réussi.

## 5. Dépendances et isolation

Sentinel et A KI PRI SA YÉ sont des projets distincts.

Sentinel ne doit pas avoir de dépendance opérationnelle à Firebase ou à l'infrastructure d'A KI PRI SA YÉ.

Les références Firebase conservées dans le scanner d'isolation et ses tests négatifs sont des fixtures de sécurité destinées à vérifier la détection de dépendances interdites. Elles ne constituent pas des dépendances d'exécution.

## 6. Déploiement web

La chaîne web utilise le build du dépôt :

```text
npm run build
→ frontend/dist
```

La production est prévue via l'intégration Git de Cloudflare Pages, avec `main` comme branche de production lorsque cette configuration est active dans le projet Cloudflare.

La réussite d'un build local ou CI ne doit pas être confondue avec la preuve d'un déploiement de production.

## 7. Android

Le code Android est maintenu séparément dans `native-android-app/`.

Aucun APK précompilé et signé n'est actuellement annoncé comme artefact officiel. Une future distribution devra être fondée sur un artefact réellement généré, signé, vérifié et identifié.

Les guides et workflows Android décrivent une chaîne de build ; ils ne valent pas preuve d'une compilation réussie tant que l'exécution correspondante n'a pas été observée.

## 8. Accessibilité

Le dépôt ne doit pas afficher de score WCAG/RGAA ou de statut de conformité comme fait établi sans rapport d'audit daté et reproductible.

Les objectifs d'accessibilité doivent être vérifiés par des tests automatisés et, lorsque nécessaire, par une évaluation manuelle.

## 9. Disponibilité et performance

Aucun SLA, taux de disponibilité, score Lighthouse ou temps de chargement ne doit être présenté comme une mesure actuelle sans mesure reproductible et datée.

La disponibilité d'un fournisseur d'hébergement ne constitue pas à elle seule une garantie de disponibilité de l'application.

## 10. Gestion des risques

Sentinel ne doit pas être décrit comme « zéro risque », « sans vulnérabilité » ou « totalement sécurisé ».

Une analyse de risque sérieuse doit préciser :

- l'actif et le périmètre ;
- la menace ;
- les hypothèses ;
- la vraisemblance et l'impact ;
- les contrôles existants ;
- les limites des contrôles ;
- les risques résiduels.

## 11. Preuves de conformité

Pour toute affirmation de conformité ou de sécurité, utiliser la hiérarchie suivante :

1. code et configuration présents ;
2. test automatisé exécuté ;
3. résultat reproductible ;
4. déploiement réellement observé ;
5. audit indépendant ;
6. certification ou qualification officielle, lorsque applicable.

Une étape ne doit pas être présentée comme équivalente à la suivante.

## 12. Références officielles

Les exigences réglementaires et les statuts de certification doivent être vérifiés directement auprès des organismes compétents avant toute utilisation institutionnelle.

Pour l'accessibilité web, utiliser les référentiels officiels WCAG/W3C et RGAA lorsque le périmètre français l'exige.

## 13. Règle de maintenance

Toute documentation Sentinel doit être corrigée lorsqu'elle contient :

- une certification non acquise ;
- une fonctionnalité future présentée comme active ;
- un résultat de test non exécuté ;
- un SLA ou score non mesuré ;
- un APK présenté comme disponible sans artefact signé ;
- une dépendance externe non présente dans l'architecture réelle ;
- une promesse absolue de sécurité ou de confidentialité.

**Version :** 2.0 — septembre 2026
