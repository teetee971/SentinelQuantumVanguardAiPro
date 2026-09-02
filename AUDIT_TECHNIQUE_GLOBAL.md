# Audit technique global — Sentinel Quantum Vanguard AI Pro

**Date de mise à jour :** 2 septembre 2026  
**Statut :** AUDIT DE DURCISSEMENT EN COURS  
**Périmètre :** Sentinel Quantum Vanguard AI Pro uniquement

> Ce document ne constitue pas une certification de sécurité ni une déclaration de production-ready. Les contrôles doivent être vérifiés sur le code, la CI et les artefacts réellement produits.

## 1. Frontière de projet obligatoire

Sentinel Quantum Vanguard AI Pro et **A KI PRI SA YÉ sont deux projets totalement séparés**.

La séparation attendue couvre :
- code source et dépendances ;
- identifiants d'application et configurations ;
- credentials et secrets ;
- pipelines CI/CD ;
- déploiements ;
- artefacts Android ;
- services backend et comptes associés.

Aucune dépendance opérationnelle Firebase/A KI PRI SA YÉ ne doit être introduite dans Sentinel.

Un contrôle CI dédié existe pour bloquer la réintroduction d'identifiants ou de configurations connus. Son succès doit être vérifié sur le dernier commit avant toute certification.

## 2. Corrections déjà appliquées

Les éléments suivants ont été durcis dans le dépôt :

- déploiement Firebase désactivé dans `SUPERPACK_MANIFEST.yml` ;
- déploiement Firebase désactivé dans `SUPERPACK_METADATA.json` ;
- initialisation et déploiement Firebase retirés de `TRIGGER_SUPERPACK.txt` ;
- `SECURITY_README.md` réaligné sur une démarche de vérification, sans prétendre à une certification ;
- guide Android réaligné sur un build Sentinel indépendant de Firebase et d'A KI PRI SA YÉ ;
- architecture de notifications réalignée sur les mécanismes Android natifs, sans FCM ;
- checklist de souveraineté corrigée pour distinguer les contrôles réalisés des contrôles restant à vérifier ;
- rapport de finalisation corrigé afin de supprimer les affirmations historiques non vérifiées de type « 100 % production-ready » ;
- garde-fou CI d'isolation ajouté et corrigé pour ne pas s'auto-déclencher sur sa propre documentation.

## 3. Contrôles encore obligatoires

### Dépendances

Vérifier les fichiers de build, lockfiles et dépendances transitives. Aucun composant Firebase ou identifiant A KI PRI SA YÉ ne doit être présent dans le graphe opérationnel.

### CI/CD

Auditer tous les workflows pour :
- permissions minimales ;
- absence de credentials Firebase ;
- absence d'injection via des données GitHub non fiables dans `run` ;
- absence de secrets exposés dans les logs ;
- actions tierces épinglées lorsque le niveau de risque le justifie ;
- intégrité et provenance des artefacts.

### Secrets

Rechercher les credentials codés en dur et les formats de clés connus. Toute exposition réelle doit entraîner suppression du secret du code, rotation et analyse de l'historique.

### Android

Vérifier :
- `AndroidManifest.xml` et chaque permission ;
- composants `exported` ;
- intents et deep links ;
- WebView éventuelle ;
- configuration réseau ;
- interdiction du cleartext HTTP ;
- signature release et provenance de l'APK.

Aucune permission sensible ne doit être considérée comme acceptable uniquement parce qu'elle existait dans une ancienne documentation. Elle doit être justifiée par le code et le besoin fonctionnel réel.

### Entrées et endpoints

Identifier les frontières de confiance : URI, intents, paramètres réseau, JSON, fichiers, notifications et données externes. Chaque entrée doit être validée, bornée et traitée comme non fiable.

### Fuzzing et robustesse

Le fuzzing doit cibler les parseurs et frontières d'entrée réellement présentes dans l'application. Les résultats doivent être exécutés en CI ou conservés comme artefacts reproductibles avant de déclarer le contrôle réussi.

## 4. Historique Git et contamination ancienne

Une ancienne version du dépôt a contenu une configuration liée à A KI PRI SA YÉ. Cette présence est historique et ne doit pas être confondue avec l'état courant de `main`.

La suppression de cette donnée de l'historique Git est un chantier distinct : elle nécessite sauvegarde, stratégie de réécriture, vérification des références restantes et rotation des credentials concernés. Aucune réécriture destructive n'est effectuée implicitement dans le cadre de cet audit.

## 5. Règle de certification

Le projet ne pourra être déclaré « sécurité validée » qu'après vérification objective des contrôles suivants :

1. isolation CI réussie ;
2. audit complet des workflows ;
3. audit des dépendances ;
4. scan des secrets ;
5. audit des permissions Android ;
6. audit des endpoints et entrées non fiables ;
7. fuzzing exécuté et sans anomalie bloquante ;
8. build Android reproductible et signature vérifiée ;
9. scan final du dépôt courant ;
10. absence de réintroduction de Firebase/A KI PRI SA YÉ dans l'architecture opérationnelle.

## 6. Décision actuelle

**Décision : NE PAS CERTIFIER LA PRODUCTION À CE STADE.**

Les corrections documentaires et de configuration déjà appliquées constituent un durcissement réel, mais elles ne remplacent pas les contrôles exécutables restants.

## 7. Références internes

- `SECURITY_README.md`
- `compliance/souverainete.md`
- `FINALIZATION_REPORT.md`
- `docs/BUILD_APK_GUIDE.md`
- `docs/NOTIFICATIONS_ARCHITECTURE.md`
- `.github/workflows/sentinel-isolation.yml`
