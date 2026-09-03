# Souveraineté numérique — Sentinel Quantum Vanguard AI Pro

**Statut :** contrôle de séparation en cours de vérification

## Périmètre

Ce document concerne exclusivement Sentinel Quantum Vanguard AI Pro. Sentinel et les applications externes sont des projets distincts et ne doivent partager ni code applicatif, ni configuration, ni identifiants, ni pipeline de déploiement.

## État de la séparation

- Les services Firebase non requis par Sentinel sont interdits dans la configuration opérationnelle.
- Les configurations étrangères et identifiants externes doivent être contrôlés par CI et audit du dépôt.
- L'historique Git peut contenir des éléments provenant d'anciennes versions ; leur purge éventuelle doit être traitée séparément et vérifiée.
- La distribution Android utilise une signature Sentinel dédiée.

## Contrôles obligatoires

1. Rechercher les dépendances Firebase dans Gradle et les autres manifests.
2. Rechercher `google-services.json` dans le projet Android.
3. Rechercher les identifiants, packages et domaines appartenant à des projets externes.
4. Rechercher les secrets ou jetons Firebase dans le dépôt et les workflows.
5. Vérifier les workflows GitHub Actions et les scripts de déploiement.
6. Vérifier les dépendances transitives avant chaque release.
7. Maintenir un contrôle CI bloquant toute réintroduction opérationnelle.

## Hébergement et infrastructure

Le choix d'un hébergeur souverain distinct est une décision d'architecture à documenter séparément. Il ne faut pas déclarer une migration effective sans preuve de déploiement et de fonctionnement.

## Données et confidentialité

Les données sensibles doivent rester sous le contrôle de l'architecture Sentinel prévue. Les services tiers ne doivent être ajoutés qu'après analyse de risque, minimisation des données, contrat approprié et validation de conformité.

## Historique Git

La suppression d'un fichier du HEAD ne supprime pas nécessairement ses anciennes versions. Toute purge d'un secret ou d'une configuration étrangère doit être traitée comme une opération Git distincte, avec sauvegarde, rotation éventuelle des credentials concernés et vérification de l'historique après réécriture.

## Règle de release

Une release Sentinel est bloquée si un composant opérationnel introduit une dépendance Firebase, une configuration externe interdite, un package étranger ou un secret qui n'appartient pas au projet.

Les contrôles automatisés conservent leurs signatures de détection afin de bloquer toute réintroduction accidentelle.
