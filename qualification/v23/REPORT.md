# Qualification MMS v23 — BLOQUÉE

Code et artefacts vérifiés : `7ef494b3606842f4be60c47ff8fbde8e2b8cfd99`.
Arbre Android : `9e9a1b12d1c6985ac173b5daeff65d72cc871d2c`.
PR brouillon : https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1755

## Résultat

La compression est intégrée au véritable sender après ses contrôles SIM/rôle/permissions. Le PDU entier reste vérifié contre la limite de l'abonnement sélectionné. Les mêmes pièces transformées alimentent PDU et provider. Rejets avant préparation terminée : aucun staging, aucune écriture provider, aucune téléphonie. Les tests contrôlent ces effets. Les soumissions incertaines ne sont jamais relancées automatiquement.

**Défaut Important non résolu :** `truncatedEntropyWithAnAppendedEndMarkerIsRejected`. Android récupère silencieusement une troncature de l'entropie JPEG suivie d'un EOI ajouté. ImageDecoder refuse les erreurs partielles signalées, mais le backend libjpeg ne signale pas celle-ci via son API publique. Le test reste actif et échoue. Il faut un validateur/décodeur strict éprouvé exposant cette erreur native. Aucun PASS global, aucune qualification de livraison et aucun contournement du test.

## Archive et isolation

SHA-256 de l'archive : `138aa86bfcce3d1d0221178b0b347a697f0991a1642dab4b8a7d4a68f9998e8d`.
762 fichiers réguliers, 3430178 octets décompressés. Manifeste TRANSFER_MANIFEST.json et empreintes internes vérifiés ; aucun chemin dangereux, lien, fichier spécial ou sensible importé. Comparaison avec Git : six fichiers créés/modifiés seulement, tous contrôlés avant import. Base et HEAD exportés : `fafe9f5b1c57b21f276ca62189acab625a490ec9`. Aucune empreinte indépendante signée n'a été fournie : le calcul et la cohérence interne ne prouvent pas l'identité de l'expéditeur.

Worktree `/workspace/sentinel-mms-v23`, branche `fix/mms-image-compression-v23`. Worktree initial propre et préservé au SHA `5dbb6190f758da92145ce08bfbf3c2823cb728c5`. Aucun changement de main, des workflows, schémas Room, secrets ou keystores. Aucun appareil ADB accessible. Aucune installation, réinitialisation, modification de rôle ni émission MMS. La v22 du téléphone reste préservée.

## Exécution

Hôte Linux x86_64, JDK17.0.21+9, Gradle9.8.0, SDK37, build-tools36.0.0. Robolectric4.16.1 : API35 avec graphiques natifs, API27 pour le chemin ancien. Ce n'est ni Termux/ARM64 ni une exécution physique Samsung/API37. AndroidX EXIF1.4.1 est la nouvelle dépendance de production ; Robolectric reste limité aux tests.

Commande de qualification exécutée, depuis le worktree propre :

```bash
source /workspace/toolchains/env.sh
export ROBOLECTRIC_SDK_DIR=/workspace/toolchains/robolectric-sdks
bash qualification/v23/reproduce.sh 7ef494b3606842f4be60c47ff8fbde8e2b8cfd99 /workspace/qualification-evidence/v23-7ef494b3
```

**Code retour1 attendu et conservé**, causé par le test JPEG actif. Toutes les commandes exactes sont dans COMMANDS.txt, leurs codes retour dans VALIDATION_LEDGER.json, sorties et JUnit dans EXECUTION_PROOFS.zip. Compilation, Lint et assemblage ne comptent pas comme tests réussis ni comme livraison opérateur.

| Vérification exécutée | Résultat | Tests | Échecs | Ignorés |
|---|---|---:|---:|---:|
| Kotlin app + laboratoire | Réussi | — | — | — |
| Suite app avec APK signé | Échec JPEG connu |1495|1|0|
| Suite laboratoire | Même échec JPEG |32|1|0|
| wearable-contract | Réussi |2|0|0|
| wearable-security | Réussi |26|0|0|
| Contrat dashboard ciblé | Réussi |1|0|0|
| Politique version Android | Réussi |7|0|0|
| Contrats produit Android | Réussi |69|0|0|
| Hygiène identifiants | Réussi |6|0|0|
| Lint app + laboratoire | 0 erreur ; app223 avertissements/19 hints, lab7 avertissements |—|—|—|
| Debug app + lab | Assemblés |—|—|—|
| AndroidTest app + lab | Assemblés, NON EXÉCUTÉS sur appareil |—|—|—|
| Signatures v2 / badging / alignement16KiB | Réussis sur les4 APK |—|—|—|
| Vérification Git/diff et isolation APK lab | Réussie |—|—|—|
| Tests physiques / MMS réels v23 | NON EXÉCUTÉS |—|—|—|

Le premier passage app sans fixture signée avait1 test ignoré ; le passage final ci-dessus fournit l'APK effectivement construit et n'en ignore aucun. Ne pas additionner ces exécutions répétées. Les tests app et laboratoire partagent le cas d'échec : il s'agit d'un seul défaut reproduit deux fois.

## Corrections et limites

- Entrées, dimensions20MP, décodage échantillonné4MP, sorties et nombre d'essais bornés ; recyclage Bitmap ; huit orientations EXIF ; JPEG conformes conservés ; PNG/GIF/WebP préservés.
- Préparation pure avec comptage exact du compositeur existant, calculs Long, ordre et originaux préservés, budget opérateur complet appliqué après composition.
- Même transactionId unique, même PDU préparé et mêmes octets provider ; contrôles répétés après compression et avant téléphonie ; aucun double transport automatisé.
- Lecture SIM résiste à une révocation de permission ; journal de résultat durable même après perte du rôle ; aucun changement de schéma du journal.
- Boucle de lecture0/interruption corrigée ; brouillon édité pendant compression préservé ; SIM capturée avant IO.
- Sources du laboratoire réellement isolées pour Kotlin et Lint ; aucun sender ni permission téléphonique dans son APK. Classes DEX contrôlées, seule classe R générée supplémentaire.

Compression automatique disponible à partir d'API28. API24–27 conservent le passage des petits JPEG et refusent explicitement la nouvelle réduction. Les limites fixes5MiB/20MP peuvent refuser des photographies extrêmes. L'allocation entre plusieurs JPEG est conservatrice et peut laisser une capacité inutilisée ; aucun optimal visuel/global n'est revendiqué. Les avertissements Lint restent documentés. Le défaut d'entropie interdit toute prétention de validation JPEG stricte. Tests physiques et preuve opérateur restent absents.

## Artefacts expérimentaux

Tous les chemins suivants sont dans `/workspace/qualification-evidence/v23-7ef494b3/` et sont des APK debug réellement assemblés. **Ne pas installer app.apk ni app-test.apk** : package principal, v22 à préserver. Signataire cloud debug existant `fb90f8db77aff2e470ecbd9162b7308b550fdffdf9cd7c9c9daaa37c3812d7b6`, différent de la signature du téléphone. Aucun keystore exporté ou modifié.

- `app-test.apk` : 1144538 octets ; SHA-256 `c4d6da27abdd028fb6c91a0706934d8dc307ab0f7d62eec270ae8f3a76570e3f`.
- `app.apk` : 98206671 octets ; SHA-256 `7da0279e3a8dfd274e26d01a9dfc96b9a541181eade9c80f190659f72619dc4a`.
- `lab-test.apk` : 2273127 octets ; SHA-256 `4ab28d5ac78edabff14748fab2078d142eed7513b03a0e074b07e6360233317a`.
- `lab.apk` : 2959019 octets ; SHA-256 `b51da4031005a8e14d2daf4d70a60449b17e8cd6f66dce5d70ee4328dab33a2f`.

`app.apk` : com.sentinel.quantum, versionCode23/versionName1.0.10. `lab.apk` : com.sentinel.quantum.mmslab.v23, versionCode1/0.23-test, minSdk28, aucune permission. Les tests correspondants ciblent chacun leur package ; ne jamais intervertir les deux paires.

Les APK originaux Gradle restent sous `native-android-app/{app,mms-compression-lab}/build/outputs/apk/{debug,androidTest/debug}/`. Les copies nommées ci-dessus et SHA256SUMS sont les références de livraison expérimentale.

## Test physique isolé et retour v22

Voir README.md. Seuls lab.apk et lab-test.apk peuvent être envisagés pour une installation isolée. Utiliser l'adresse ADB actuelle, installer lab puis son test avec `-r -t`, lancer :

```bash
adb -s DEVICE shell am instrument -w -r com.sentinel.quantum.mmslab.v23.test/androidx.test.runner.AndroidJUnitRunner
```

Conserver la sortie intégrale, notamment le cas d'entropie. Aucune modification des rôles ni donnée de Sentinel nécessaire. Revenir à v22 consiste à fermer le laboratoire et rouvrir Sentinel déjà installé. Aucun downgrade, aucune suppression des bases/conversations.

Ne pas tester un véritable MMS v23 tant que le blocage n'est pas résolu et qu'une installation du package principal n'a pas été explicitement autorisée. Les résultats historiques v22 ne constituent aucune preuve v23.

Le commit final ajoute uniquement les preuves et ce rapport ; l'arbre Android vérifié reste celui de7ef494b3. La liste complète des fichiers modifiés est dans FILES_CHANGED.txt.
