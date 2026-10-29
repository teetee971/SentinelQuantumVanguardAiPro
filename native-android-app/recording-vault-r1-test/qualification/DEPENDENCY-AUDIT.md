# Audit complémentaire de dépendances — lecture seule

## Statuts à distinguer

**Alertes Dependabot propres au dépôt : NON VÉRIFIÉES (HTTP403).** Aucun avis
d'absence, de fermeture ou de résolution. La correspondance de leurs identifiants
avec les avis ci-dessous n'est pas confirmée par l'API du dépôt.

**Audit public indépendant : effectué.** Le package-lock.json de la branche par
défaut, au commit `935344423b2af2a4a57c58123f2c5c178b46ec03`, verrouille Vite
**8.3.1**. npm audit et les six fiches publiques GitHub Advisories identifient
six avis applicables à cette version : **1 élevé, 4 modérés, 1 faible**. Ce profil
correspond exactement au résumé affiché par GitHub, ce qui constitue un
rapprochement utile, pas une preuve d'identité des six alertes du dépôt.

## Méthode et résultats réels

Les seuls package.json et package-lock.json du snapshot ont été copiés dans un
répertoire de travail séparé, sous /workspace/tooling. Commande exécutée :

```bash
npm audit --package-lock-only --ignore-scripts --json
```

Retour **1** car des vulnérabilités sont signalées ; réponse JSON exploitable,
pas une panne de l'audit. npm regroupe ces six avis sous **un paquet vulnérable**,
Vite, de gravité agrégée élevée : ne pas confondre son total par paquet avec le
nombre d'avis. Aucun npm install, npm audit fix ou script du dépôt exécuté.

Chaque avis a ensuite été consulté via `gh api /advisories/IDENTIFIANT`, avec
retour 0. Les avis n'étaient pas retirés au moment de cette consultation.
Les champs pertinents des réponses sont conservés, sans recopier leurs PoC.

## Six avis publics examinés

Tous incluent Vite **8.3.0 à 8.3.2** dans leurs versions affectées ; premier
correctif de cette série : **8.3.3**. Les avis launch-editor couvrent également
le paquet launch-editor (correctif 2.14.2), mais sa présence séparée dans le
lockfile n'est pas nécessaire pour que l'avis s'applique à Vite 8.3.1.

| Avis | Gravité | Risque et conditions principales décrites par l'avis |
| --- | --- | --- |
| [GHSA-587h-7wmj-h5pm](https://github.com/advisories/GHSA-587h-7wmj-h5pm) | Élevée | Contournement d'existence de chemin sous Windows ; peut permettre une injection de commande en combinaison avec l'échappement incomplet de % |
| [GHSA-698v-4r3x-3vcv](https://github.com/advisories/GHSA-698v-4r3x-3vcv) | Modérée | Échappement de % incomplet dans launch-editor sous Windows, avec contrôle de noms de fichiers et conditions d'environnement/NTFS |
| [GHSA-r825-w8mm-5pxw](https://github.com/advisories/GHSA-r825-w8mm-5pxw) | Modérée | Chemins interprétés comme options de certains éditeurs ; pour Vite, conditions incluant WSL et une entrée de système de fichiers contrôlée |
| [GHSA-9jrq-w75r-8gcw](https://github.com/advisories/GHSA-9jrq-w75r-8gcw) | Faible | Exécution de scripts d'origine tierce dans certaines intégrations de middleware Vite / transformIndexHtml avec module inline |
| [GHSA-rq7h-c2jc-7f22](https://github.com/advisories/GHSA-rq7h-c2jc-7f22) | Modérée | Lecture hors server.fs.allow, avec serveur de développement exposé et collision particulière de chemins sur système non Windows |
| [GHSA-vfpm-58rq-9qcg](https://github.com/advisories/GHSA-vfpm-58rq-9qcg) | Modérée | Exposition des quatre premiers octets de fichiers via le traitement Wasm, selon accessibilité du serveur et runtime Node/Deno |

La version vulnérable dans un lockfile n'établit pas à elle seule qu'une
exploitation est possible dans chaque déploiement. Aucun PoC ni serveur Vite
n'a été lancé pendant cet audit.

## Impact sur Recording Vault

Pour **ces six avis publics**, aucun chemin de dépendance ou d'invocation Vite
n'a été trouvé dans le module isolé Recording Vault :

- Son build.gradle ne déclare que les dépendances AndroidX d'instrumentation ;
  son graphe Gradle résolu contient AndroidX, JUnit et Kotlin/JVM, pas Vite.
- Le settings.gradle de qualification ne configure que recording-vault-r1-test.
- Les scripts de qualification utilisent Kotlin/JVM, outils Android, Python et
  JNI, sans lancement de Node, npm ou Vite.
- Le contenu main de l'APK de qualification est natif/JVM ; aucun frontend Vite
  n'est empaqueté par ce module.
- Les builds et tests qualifiés utilisent ce chemin isolé, dont les preuves
  sont conservées dans evidence-migration-followup/android-module-dependencies.txt.

**Conclusion bornée : ces six avis Vite ne sont pas applicables au runtime ni
au chemin de build isolé Recording Vault examiné.** Le frontend et son outillage
web restent concernés par la version vulnérable. Les effets d'un hôte partagé
compromis et les autres dépendances ne sont pas couverts par cette conclusion.
Il ne s'agit pas d'un certificat d'absence de vulnérabilités du dépôt ou de
Recording Vault, ni d'une résolution des six alertes Dependabot non accessibles.

## Correctif identifié, non appliqué

Pour les six avis étudiés, Vite **8.3.3** est le minimum corrigé de la série 8.3.
Le frontend devra être corrigé dans un travail séparé autorisé, avec mise à jour
du lockfile et validation web appropriée. Aucune dépendance frontend, backend,
Gradle ou autre module n'a été modifiée pendant cette mission.

## Preuves

- [Commandes et empreintes des snapshots](evidence-dependency-audit/commands.json)
- [Réponse npm audit](evidence-dependency-audit/npm-audit.json)
- [Champs des six avis publics](evidence-dependency-audit/public-advisories.json)
- [Graphe Gradle du module](evidence-migration-followup/android-module-dependencies.txt)

L'API des alertes du dépôt a encore répondu 403 lors de cette poursuite.
Le statut de ces alertes demeure **NON VÉRIFIÉ**, sans modification de leurs états.
