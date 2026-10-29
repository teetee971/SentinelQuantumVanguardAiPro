# Galaxy S24+ — compte rendu de migration du 2026-10-29

## Provenance et portée

Résultats **exécutés par l'utilisateur dans Termux/Ubuntu proot sur son Galaxy**,
puis transmis dans la conversation. Codex a examiné cette transcription ; il
n'a pas exécuté ces commandes ADB lui-même et n'a pas encore reçu l'archive des
journaux bruts. Ce document est un compte rendu, pas une reconstruction de logs.

Code annoncé avant build : `eea40d423a616e845da5e4295b6e9cdb76672c32`, branche
`fix/recording-vault-v2-migration-hardening-20261028`.
Le protocole a écrit `commit.txt` dans le dossier d'évidence sur le téléphone ;
ce fichier brut reste à collecter avec les journaux.

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
Les lignes MIGRATION_READY, les identités de processus et les détails de sécurité
de clé restent à examiner directement dans `logcat.log` archivé.

L'instrumentation exercée utilise AndroidKeyStore réel, le JNI ARM64 et les E/S
Android. Les succès attestent le fonctionnement observé des synchronisations
sur ce run ; ils ne prouvent pas la durabilité face à une coupure électrique.
Le niveau SOFTWARE / TRUSTED_ENVIRONMENT / STRONGBOX n'est pas visible dans la
transcription et reste **NON DÉTERMINÉ** tant que KEY_SECURITY_LEVEL n'est pas lu.

## Limites et vérifications restantes

1. Collecter les logs bruts, `commit.txt`, `expected.json` et leurs empreintes.
2. Campagne après **redémarrage complet du téléphone : NON EXÉCUTÉE dans ce run**.
3. Coupure d'alimentation, vrai disque Android plein, invalidation matérielle de
   clé : **NON EXÉCUTÉS**. Les injections ENOSPC/EIO ne remplacent pas ces campagnes.
4. Anti-rollback local limité : la restauration cohérente de tout le stockage
   peut restaurer aussi les marqueurs ; aucune protection absolue revendiquée.
5. Les V1 n'authentifient pas l'attribution historique à un id/répertoire.
6. Dependabot : les six alertes restent **NON VÉRIFIÉES (HTTP403)**.

Le passage de ce protocole ne constitue pas une validation de transport SMS,
MMS, RCS ou d'enregistrement des deux côtés d'un appel. Aucun changement de
périmètre ni fusion de la PR n'est effectué.

## Collecte sur le téléphone, sans relancer les tests

Dans Termux :

```bash
evidence="$HOME/vault-migration-device-20261029T013435Z"
cat "$evidence/commit.txt"
grep -E 'KEY_SECURITY_LEVEL=|KEY_SIZE=|USER_AUTH_REQUIRED=|MIGRATION_READY' "$evidence/logcat.log"
tar -czf "$HOME/vault-migration-c48883da20ff488ba71a09f7b2a57c95.tar.gz" -C "$HOME" "$(basename "$evidence")"
sha256sum "$HOME/vault-migration-c48883da20ff488ba71a09f7b2a57c95.tar.gz"
```

Conserver cette archive et transmettre les journaux pour revue. Ne pas effacer
les données Android, la clé ou le verrou global pour compléter cette collecte.
