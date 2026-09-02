# Souveraineté numérique — Sentinel Quantum Vanguard AI Pro

**Statut :** contrôle de séparation en cours de vérification

## Périmètre

Ce document concerne exclusivement Sentinel Quantum Vanguard AI Pro. Sentinel et A KI PRI SA YÉ sont deux projets distincts et ne doivent partager ni code applicatif, ni configuration, ni identifiants, ni pipeline de déploiement.

## État de la séparation

- Déploiement Firebase : **désactivé dans la configuration Sentinel corrigée**.
- Configuration Firebase/A KI PRI SA YÉ dans l'arbre courant : **à contrôler par CI et audit du dépôt**.
- Historique Git : une ancienne configuration liée à A KI PRI SA YÉ a été identifiée ; elle ne doit pas être considérée comme supprimée de l'historique tant qu'aucune purge d'historique n'a été effectuée et vérifiée.
- Distribution Android : signature Sentinel dédiée ; aucun service Firebase n'est requis.

## Contrôles obligatoires

1. Rechercher les dépendances Firebase dans Gradle et les autres manifests.
2. Rechercher `google-services.json` dans le projet Android.
3. Rechercher les identifiants `akiprisaye`, `a-ki-pri-sa-ye` et `com.akiprisaye` dans le code et la configuration.
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

Une release Sentinel est bloquée si un composant opérationnel introduit :

```text
Firebase / FCM
A KI PRI SA YÉ
akiprisaye
com.akiprisaye
a-ki-pri-sa-ye
google-services.json
FIREBASE_TOKEN
```

Les mentions de ces termes dans la présente documentation servent uniquement à définir les interdictions et ne constituent pas des dépendances runtime.
