# Limites stratégiques de Sentinel

**État documentaire : septembre 2026**  
**Classification : public**

Ce document fixe les limites d'usage et de communication de Sentinel Quantum Vanguard AI Pro. Il ne constitue ni une certification, ni une attestation de conformité réglementaire, ni une preuve d'efficacité opérationnelle.

## Positionnement

Sentinel est conçu comme une plateforme **défensive** de cybersécurité, de veille, de simulation contrôlée, d'aide à la décision et de gouvernance. Les composants de Red Team et d'émulation adversariale servent à évaluer des contrôles, former des équipes et produire des observations dans un cadre autorisé. Ils ne donnent pas à Sentinel une vocation d'attaque ou d'intrusion réelle.

> **Simulation défensive contrôlée — aucun accès non autorisé — usage audit, formation et évaluation uniquement.**

## Capacités documentées

Selon les modules présents dans le dépôt, Sentinel peut notamment :

- traiter ou présenter des données OSINT publiques selon les connecteurs et sources effectivement disponibles ;
- exécuter des simulations locales ou contrôlées prévues par le code ;
- produire des éléments d'analyse, de provenance, de gouvernance et d'aide à la décision ;
- évaluer des politiques et des preuves avant certaines transitions sensibles ;
- générer des journaux, rapports ou artefacts lorsque les modules concernés le prévoient ;
- construire et valider l'interface web/PWA et l'application Android native via les pipelines du dépôt.

La présence d'un module dans le code ne prouve pas à elle seule qu'une capacité est déployée, alimentée, autorisée ou opérationnelle dans un environnement donné.

## Limites défensives

Sentinel n'est pas destiné à fournir :

- un accès non autorisé à un système tiers ;
- l'exploitation réelle d'une vulnérabilité contre une cible ;
- du malware fonctionnel ou de la persistance offensive ;
- du vol, de la destruction ou de l'exfiltration de données ;
- du déni de service ;
- une interception non autorisée de communications ;
- une exécution privilégiée autonome hors des mécanismes explicitement autorisés et validés.

Les fonctions de simulation doivent rester distinguées des actions réelles. Une simulation réussie n'est pas une autorisation d'exécution et ne prouve pas qu'une action externe a eu lieu.

## Gouvernance et décision

Les résultats d'analyse, de score, de routage ou de recommandation sont des éléments d'aide à la décision. Ils ne doivent pas être présentés comme une décision humaine, une homologation, une autorisation réglementaire ou une preuve de sécurité absolue.

Pour les opérations sensibles, les contrôles de politique, d'authenticité, de fraîcheur, de liaison à une simulation, d'anti-rejeu et de transition d'état présents dans le dépôt restent les références techniques. Leur présence dans le code ne remplace pas la preuve de configuration, de clés, de déploiement et de fonctionnement de l'environnement réel.

## Données et sources externes

Les modules OSINT et de threat intelligence doivent privilégier des sources publiques et traçables. Une donnée externe peut être incomplète, retardée, indisponible ou erronée. Sentinel ne doit pas inventer un remplacement lorsqu'une source n'est pas disponible.

Une vulnérabilité publiée ou un indicateur observé ne signifie pas qu'un système donné est effectivement compromis ou exposé.

## Simulation adversariale

Les références à MITRE ATT&CK, aux tactiques adversariales, aux scénarios Red Team ou à l'« offensive security » peuvent décrire des **concepts de simulation et d'évaluation**. Elles ne doivent pas être interprétées comme un positionnement produit offensif.

Les usages autorisés comprennent notamment :

- la formation SOC/CERT ;
- l'évaluation de contrôles de détection et de réponse ;
- les exercices Red Team / Blue Team autorisés ;
- les exercices de crise et table-top ;
- la recherche défensive et la documentation de scénarios.

Tout test sur un système réel nécessite une autorisation appropriée de son propriétaire ou responsable légitime.

## IA et automatisation

Les composants d'IA et d'automatisation restent soumis aux garde-fous du dépôt. Ils ne doivent pas être décrits comme infaillibles, parfaitement prédictifs ou capables de prendre sans contrôle toutes les décisions sensibles.

Un fournisseur de modèle, un nom de modèle, un score ou une évaluation ne constitue pas automatiquement une preuve de confiance. Les preuves et signaux de confiance doivent être explicites et vérifiables selon les mécanismes applicables.

## Sécurité, CI et preuves

Un contrôle CI vert prouve uniquement ce que ce contrôle a réellement exécuté sur le commit concerné. Il ne prouve pas à lui seul :

- l'absence totale de vulnérabilités ;
- la sécurité de tous les environnements de production ;
- la révocation d'anciens secrets ;
- la protection administrative de la branche principale ;
- la garde opérationnelle des clés ;
- la conformité à une norme ou à un référentiel externe.

Les affirmations publiques doivent donc rester liées à une preuve datée, identifiable et pertinente.

## Référentiels externes

MITRE ATT&CK, NIST CSF, les publications de l'ANSSI, OWASP ou d'autres référentiels peuvent servir de sources méthodologiques. Leur utilisation ne signifie pas que Sentinel est certifié, homologué, approuvé ou « conforme » par ces organismes.

Toute revendication de conformité réglementaire ou de certification doit reposer sur une évaluation formelle distincte et sur les preuves exigées par le référentiel concerné.

## Confidentialité et légalité

Sentinel ne doit pas être utilisé pour contourner les droits d'accès, collecter illicitement des données personnelles, exploiter des données classifiées sans autorisation ou commettre une atteinte à un système de traitement automatisé de données.

L'utilisateur ou l'organisation qui déploie Sentinel reste responsable de son cadre d'autorisation, de ses données, de ses intégrations, de ses comptes, de ses clés et de ses décisions opérationnelles.

## Limites de communication

Sans preuve spécifique et à jour, ne pas présenter Sentinel comme :

- « sécurité absolue » ou « zéro vulnérabilité » ;
- « certifié gouvernemental » ou « military grade » ;
- « conforme ANSSI/NIST/ISO » au sens d'une certification ou homologation ;
- « temps réel » si la chaîne de données ne le démontre pas ;
- « autonome » au sens d'une autorité illimitée à modifier, déployer ou exécuter des actions privilégiées ;
- « production ready » uniquement parce que le site est accessible ou que la CI est verte.

## Références internes actuelles

Pour l'implémentation et les limites précises, consulter en priorité :

- `docs/DESIGN_SYSTEM.md` pour l'identité visuelle et les garde-fous de communication ;
- `decision-plane/` pour les mécanismes de décision, de preuve et d'autorisation ;
- `ai-governance/` pour la gouvernance des modèles et évaluations ;
- `security/` et `security-digital-twin/` pour les contrôles et simulations de sécurité ;
- `.github/workflows/` pour les contrôles CI effectivement configurés.

## Conclusion

Sentinel est un système défensif et auditable dont la crédibilité dépend de la séparation stricte entre **code**, **simulation**, **preuve**, **autorisation**, **déploiement** et **résultat opérationnel**. Aucune de ces notions ne doit être remplacée par une affirmation marketing non démontrée.
