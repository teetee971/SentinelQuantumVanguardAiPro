# Audit initial — migration V1 vers V2

Date : 2026-10-29 UTC. Statut : audit et proposition uniquement ; migration non implémentée.

## Périmètre vérifié

- Source : `fix/recording-vault-v2-aad-20261028`.
- HEAD source : `601f7baedc4e23337fa6b9a595c05c41b7466f53`.
- Branche locale créée : `fix/recording-vault-v2-migration-hardening-20261028`.
- Aucun AGENTS.md trouvé dans le clone. Aucun changement de main, données Android,
  dépendances, code applicatif ou bibliothèque JNI pendant cet audit.

## Constats

1. `RecordingVaultCrypto` utilise AES-GCM, IV de 12 octets et tag de 128 bits.
   V1 ne possède pas d'AAD. V2 authentifie un domaine, le nom logique du
   répertoire et l'identifiant avec des longueurs explicites.
2. `save` produit V2 ; `read` accepte V1 et V2. Aucun mécanisme de migration ou
   état persistant interdisant V1 après passage à V2 n'est présent.
3. Les lectures bornent les allocations à 16 MiB de clair, refusent les liens
   symboliques et vérifient troncature/croissance sur le descripteur ouvert.
4. Le verrou persistant sérialise les processus coopérants. Il ne protège pas
   contre un attaquant du même UID contournant ce verrou.
5. Le contenu temporaire est forcé avant `renameat2(RENAME_NOREPLACE)` ; les
   entrées de répertoire ne sont pas synchronisées après publication. Une
   mort de processus ne constitue pas une preuve de durabilité électrique.
6. Les tests instrumentés existants couvrent V1 et les substitutions V2 entre
   identifiants et répertoires. Le test hôte nommé « legacy valid ciphertext
   substitution is ACCEPTED » utilise pourtant `save`, désormais V2 : son
   attente est incohérente. Cela est un constat statique, pas un échec exécuté.
7. Le lanceur hôte compile le binding JNI, sans construction/chargement d'une
   bibliothèque hôte documentée. Il faut rendre cette qualification exécutable
   et distinguer JNI Linux et JNI Android ARM64.
8. `run-device.sh` attend exactement 24 tests. La phase d'interruption actuelle
   fabrique un temporaire partiel ; elle n'interrompt pas la migration réelle.
9. Le build isolé demande AGP 9.4.1, compileSdk 37, targetSdk 36, minSdk 26 et
   Gradle 9.8.0. L'appareil API 36 peut exécuter cette cible, mais la compilation
   exige la plateforme SDK 37. Compatibilité JDK 17 à vérifier, non présumée.

## Proposition technique à préciser avant implémentation

Préférer une publication V2 immuable distincte, par identifiant, à un
remplacement en place de l'unique fichier V1. Conserver le V1 pendant cette
mission ; aucune suppression automatique des anciens enregistrements.

- Authentifier entièrement V1 par `doFinal` avant de chiffrer ou publier V2.
  V1 authentifie le contenu, pas son attribution historique à un identifiant.
- Chiffrer avec un nouvel IV généré par le fournisseur cryptographique et les
  AAD V2 existantes. Ne jamais écrire de clair sur disque.
- Préparer un fichier privé borné avec CREATE_NEW/NOFOLLOW_LINKS, forcer son
  contenu, le relire et authentifier le résultat dans son contexte final.
- Publier avec le JNI NOREPLACE conservé ; synchroniser le répertoire. Les
  états persistants et leur ordre de synchronisation doivent être spécifiés
  avant de coder, y compris le cas publication réussie/synchronisation échouée.
- Introduire une preuve persistante authentifiée de l'exigence V2 liée au
  répertoire et à l'identifiant. Une fois V2 exigé, absence ou corruption V2
  provoque une erreur explicite, jamais un repli silencieux vers V1.
- Reprise sous le verrou existant, sans écrasement et sans mutation si le V2
  authentifié est déjà publié. Garder les artefacts ambigus pour diagnostic.
- ENOSPC, EIO, corruption ou conflit : erreur explicite ; conserver la source
  et toute copie validée. Ne pas confondre échec de retour et absence de
  publication. Ne pas interpréter un V2 invalide comme un V1 admissible.

Alternative : conserver le chemin `<id>.rv`, déplacer V1 vers une sauvegarde
durable puis publier V2, avec journal authentifié et reprise de chaque étape.
Cette alternative préserve le nom physique mais ajoute une fenêtre sans fichier
canonique et davantage de transitions à qualifier. Le choix reste ouvert.

## Risques et limites à documenter

Un marqueur local ne garantit pas un anti-rollback absolu si l'attaquant peut
restaurer simultanément fichiers et marqueurs. Une restauration cohérente de
tout le stockage demande un ancrage externe ou un état matériel monotone
qualifié. AndroidKeyStore seul ne démontre pas cette propriété. AAD ne détecte
pas non plus la restauration d'un ancien V2 valide dans le même contexte.
Conserver V1 consomme de l'espace et laisse une copie de format ancien sur
disque ; la politique de lecture doit néanmoins interdire son utilisation
après engagement V2. La purge future des V1 reste hors périmètre.

## Journal réel de cette session

| Commande | Résultat |
| --- | --- |
| `git ls-remote https://github.com/teetee971/SentinelQuantumVanguardAiPro.git refs/heads/fix/recording-vault-v2-aad-20261028` | code 0 ; SHA source ci-dessus |
| `gh auth status` | code 0 ; compte teetee971 authentifié |
| `git clone --single-branch --branch fix/recording-vault-v2-aad-20261028 https://github.com/teetee971/SentinelQuantumVanguardAiPro.git /workspace/SentinelQuantumVanguardAiPro` | code 0 |
| `git switch -c fix/recording-vault-v2-migration-hardening-20261028` | succès, branche locale créée |
| `python3 native-android-app/recording-vault-r1-test/qualification/test-device-runner.py` | code 0 ; 4 tests réussis, aucun échoué ; ADB simulé uniquement |
| `bash native-android-app/recording-vault-r1-test/qualification/run-host-tests.sh` | code 127 ; kotlinc absent ; aucun test Kotlin exécuté |
| `java -version` | code 0 ; OpenJDK 21.0.12.1 ; JDK 17 non présent dans /usr/lib/jvm |
| Depuis native-android-app : `timeout 30 bash ./gradlew --offline -p recording-vault-r1-test/qualification/android-build :recording-vault-r1-test:assembleDebug :recording-vault-r1-test:assembleDebugAndroidTest` | code 1 ; distribution Gradle absente, téléchargement wrapper refusé par le réseau ; aucune compilation Android |
| `gh api repos/teetee971/SentinelQuantumVanguardAiPro/dependabot/alerts --paginate` avec sélection des alertes ouvertes | code 1 ; HTTP 403 Resource not accessible by integration |
| `git diff --check` avant ce document | code 0 ; aucun changement de code |

Ni kotlinc, ni adb, ni gradle trouvés par command -v. Aucun appareil Android
accessible via ADB dans cette session ; qualification physique NON EXÉCUTÉE.
Les résultats historiques de 24 tests ne sont pas des résultats de cette session.

## Dependabot

Les six alertes annoncées ne sont pas accessibles avec les droits actuels.
Impossible d'identifier leurs GHSA, versions et chemins de manifests, donc
impossible d'affirmer qu'elles affectent ou épargnent Recording Vault.
Le module déclare AndroidX runner 1.7.0 et ext:junit 1.3.0 pour instrumentation ;
le build isolé utilise AGP 9.4.1 et des dépendances transitives à examiner aussi.
Un export des six alertes (GHSA, paquet, manifest, plage vulnérable, correctif)
ou un accès en lecture est nécessaire. Aucun changement de dépendance effectué.

## Suite proposée

Spécifier la machine d'états de migration et son modèle de menace, puis couvrir
les 13 scénarios demandés avec injection d'erreurs et de crashs aux frontières
de durabilité. Corriger la caractérisation hôte devenue obsolète. Étendre le
protocole Android pour interrompre la migration réelle et découvrir les tests
attendus sans nombre figé, tout en rejetant tests ignorés et sessions partielles.
Qualifier séparément la JVM, le JNI ARM64, AndroidKeyStore et le stockage du
SM-S926B. Préparer les commandes Termux finales avec les nouveaux points d'entrée,
puis commits et PR brouillon sans fusion. Aucun de ces résultats futurs n'est
déclaré réussi dans le présent audit.
