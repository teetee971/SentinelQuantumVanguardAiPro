# Galaxy S24+ — compte rendu de migration du 2026-10-29

## Provenance et portée

Résultats **exécutés par l'utilisateur dans Termux/Ubuntu proot sur son Galaxy**,
puis transmis dans la conversation. Codex a examiné cette transcription ; il
n'a pas exécuté ces commandes ADB lui-même et n'a pas encore reçu l'archive des
journaux bruts. Ce document est un compte rendu, pas une reconstruction de logs.

Code annoncé avant build : `eea40d423a616e845da5e4295b6e9cdb76672c32`, branche
`fix/recording-vault-v2-migration-hardening-20261028`.
Le contenu de `commit.txt`, transmis ensuite par l’utilisateur, confirme ce
SHA complet. Le fichier brut reste à importer avec l’archive des journaux.

- Modèle observé : **SM-S926B**, Android **16**, API **36**.
- Fingerprint : `samsung/e2sxeea/e2s:16/BP4A.251205.006/S926BXXSHDZI1:user/release-keys`.
- Début du protocole : **2026-10-29T01:34:35Z**.
- Run : **c48883da20ff488ba71a09f7b2a57c95**.
- Répertoire Termux : `$HOME/vault-migration-device-20261029T013435Z`.
- L'adresse ADB locale et les chemins privés complets sont omis de ce rapport.

## Build observé sur ARM64

Ubuntu proot, **OpenJDK 17.0.20**, wrapper **Gradle 9.8.0**, SDK `/root/android-sdk`,
aapt2 ARM64 existant réutilisé. Build isolé par
`-p recording-vault-r1-test/qualification/android-build`, options offline,
no-daemon, max-workers=1 et compilateur Kotlin in-process.

Tâches demandées : assembleDebug, assembleDebugAndroidTest et lintDebug.
Résultat transmis : **BUILD SUCCESSFUL in 1m 33s**, **76 tâches exécutées**.
Kotlin principal et instrumenté compilés. Aucun :app configuré dans cette exécution.
Le résultat lint détaillé ARM64 n'est pas inclus dans la transcription ; ne pas
lui attribuer automatiquement le nombre d'avertissements du build cloud.
L'impossibilité de strip du JNI existant n'a pas empêché le packaging ; aucun
changement du binaire n'est déduit de ce message.

## Artefacts installés

SHA-256 affichés par le protocole avant installation :

| APK | SHA-256 |
| --- | --- |
| Application debug | `99eaf2cce1baf6540d8dfaaccd3c975af1819ac55bc8a74bcebccd6a5baec6cf` |
| Tests debug | `0638664072b166e50006044ee29aa00264b4f4d93ffdebda24405304d4586715` |

Les deux installations `-r -t` retournent **Success**. Cela valide l'acceptation
des APK construits dans l'environnement utilisateur, pas celle des APK signés
par la clé debug cloud. Aucune désinstallation ni pm clear dans le protocole.
Le cleanup ne concerne que les fixtures du run, pas les enregistrements existants.

## Résultats instrumentés transmis

| Étape | Preuve dans la transcription | Portée |
| --- | --- | --- |
| Découverte | DISCOVERED 38 tests | **Pas une exécution**, jamais comptée comme PASS |
| Suite de régression et migration | OK (38 tests), INSTRUMENTATION_CODE: -1, VERIFIED 38 executed tests | **38 tests exécutés, aucun échec signalé**, dont 14 nouveaux tests de migration |
| Persistance write / verify / cleanup | 3 sessions OK (1 test), code terminal -1 et VERIFIED | Phases réussies, processus arrêté entre écriture et vérification |
| Migration prepared | setup / verify / cleanup validés ; interruption externe entre setup et verify | Reprise à partir du candidat préparé |
| Migration required | setup / verify / cleanup validés ; interruption externe | Reprise après publication de l'intention authentifiée |
| Migration published | setup / verify / cleanup validés ; interruption externe | Reprise après rename du V2, avant synchronisation finale |
| Fin du protocole | PROTOCOL_COMPLETED avec le même run | Protocole complet, pas une campagne de coupure électrique |

Les phases validées représentent **12 invocations supplémentaires** (3 de
persistance et 9 de migration), et non 12 nouveaux cas distincts. Les quatre
sessions interrompues (une ancienne caractérisation et trois migrations) ne
sont **pas** comptées comme réussies. Leur retour shell 0 est explicitement
signalé comme interruption, pas comme un résultat JUnit réussi.

Le script n'atteint verify/cleanup qu'après observation des readiness dans
logcat et force-stop ; la transcription montre aussi les pidof vides après arrêt.
Les extraits MIGRATION_READY et de sécurité de clé ont ensuite été transmis
par l’utilisateur (voir ci-dessous). Le logcat complet reste à importer pour
revue de l’ensemble des identités et phases.

L'instrumentation exercée utilise AndroidKeyStore réel, le JNI ARM64 et les E/S
Android. Les succès attestent le fonctionnement observé des synchronisations
sur ce run ; ils ne prouvent pas la durabilité face à une coupure électrique.
Les extraits supplémentaires rapportent **TRUSTED_ENVIRONMENT (TEE)** et
**256 bits** ; **USER_AUTH_REQUIRED=false**. Cela décrit la valeur rapportée
par AndroidKeyStore/KeyInfo, pas une attestation matérielle indépendante.
Aucune protection StrongBox ni exigence d’authentification utilisateur n’est
revendiquée pour cette clé.

## Limites et vérifications restantes

1. Collecter les logs bruts, `commit.txt`, `expected.json` et leurs empreintes.
2. Redémarrage absent de ce premier run, puis **validé dans une campagne distincte** :
   [GALAXY-REBOOT-RUN-20261029.md](GALAXY-REBOOT-RUN-20261029.md).
3. Coupure d'alimentation, vrai disque Android plein, invalidation matérielle de
   clé : **NON EXÉCUTÉS**. Les injections ENOSPC/EIO ne remplacent pas ces campagnes.
4. Anti-rollback local limité : la restauration cohérente de tout le stockage
   peut restaurer aussi les marqueurs ; aucune protection absolue revendiquée.
5. Les V1 n'authentifient pas l'attribution historique à un id/répertoire.
6. Dependabot : les six alertes restent **NON VÉRIFIÉES (HTTP403)**.

Le passage de ce protocole ne constitue pas une validation de transport SMS,
MMS, RCS ou d'enregistrement des deux côtés d'un appel. Aucun changement de
périmètre ni fusion de la PR n'est effectué.

## Extraits supplémentaires transmis par l’utilisateur

Contenu de commit.txt : `eea40d423a616e845da5e4295b6e9cdb76672c32`.

Extraits logcat tels que fournis (horloge locale du téléphone ; fuseau non
collecté, aucune conversion UTC supposée) :

```text
10-28 21:34:50.192 18450 18464 I SentinelR1KeyAudit: KEY_SECURITY_LEVEL=TRUSTED_ENVIRONMENT
10-28 21:34:50.192 18450 18464 I SentinelR1KeyAudit: KEY_SIZE=256
10-28 21:34:50.192 18450 18464 I SentinelR1KeyAudit: USER_AUTH_REQUIRED=false
10-28 21:35:16.292 18963 18978 I SentinelR3: MIGRATION_READY run=c48883da20ff488ba71a09f7b2a57c95 point=prepared process=18963:66300634
10-28 21:35:19.968 19296 19312 I SentinelR3: MIGRATION_READY run=c48883da20ff488ba71a09f7b2a57c95 point=required process=19296:66300992
10-28 21:35:23.672 19503 19518 I SentinelR3: MIGRATION_READY run=c48883da20ff488ba71a09f7b2a57c95 point=published process=19503:66301361
```

Ces lignes confirment le même run et trois processus interrompus distincts.
Elles ne remplacent pas les assertions des phases verify ni les journaux complets.

## Archive créée sur le téléphone

Archive annoncée : `$HOME/vault-migration-c48883da.tar.gz`.
SHA-256 **communiqué par l’utilisateur** :

```text
01d1799265059ccbc02a581c612896f18fbb2a7aaf9799d8c5a855273fee03dd
```

L’archive n’a pas encore été reçue par Codex ; cette empreinte n’a donc pas été
recalculée indépendamment et le contenu brut n’a pas été vérifié. Conserver et
transmettre cette archive pour compléter la revue, sans effacer de données Android,
la clé ou le verrou global. Une campagne de redémarrage sur une nouvelle fixture
a ensuite réussi selon les captures utilisateur ; voir le compte rendu distinct.

## Mise à jour après réception des preuves

Le 2026-10-29, Codex a reçu l’archive finale, recalculé ses empreintes et
contrôlé directement les journaux avec le vérificateur du dépôt. Les résultats
transmis sont confirmés. Les mentions antérieures de fichiers non reçus
décrivent l’état avant réception et sont désormais levées. Voir
[le contrôle détaillé et les empreintes](FINAL-HANDOFF.md#contrôle-indépendant-des-archives-reçues--2026-10-29).
