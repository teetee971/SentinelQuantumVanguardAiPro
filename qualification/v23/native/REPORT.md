# MMS JPEG v23 — qualification finale de la reprise

**Statut livraison : BLOCKED. Correctifs et qualification automatisée disponibles : PASS.**

Code exécuté : `8c2c2b98c4a25d6d3b355bca034ca4b54f9ba2b0` ; arbre Android `8e67df8cd5dbbe625f090579a7cfb049c2efcf32`.
Base de reprise : `708b7a208fd93efc9a4198802fa093603af6f7b9` ; référence stable v22 `fafe9f5b1c57b21f276ca62189acab625a490ec9`.
Worktree isolé : `/workspace/sentinel-mms-jpeg-native`, branche locale `fix/mms-jpeg-strict-validation-v23`. Livraison via la PR1755, qui doit rester en brouillon. Aucun merge main, installation sur téléphone, reset, changement de rôle ou émission MMS réelle. Les scripts et preuves sont ajoutés après le SHA de code sans changer l’arbre Android.

## Cause racine et correction

Le test original `truncatedEntropyWithAnAppendedEndMarkerIsRejected` a été exécuté sur708b7a20 :1test,1échec,0ignoré. ImageDecoder/Skia peut récupérer une entropie JPEG tronquée sans appeler le listener d’image partielle. SOI/EOI ne constitue donc pas une validation.

Le nouveau module `mms-jpeg-validator` embarque les sources non modifiées de libjpeg-turbo3.2.1, épinglées au commit7b4993786d856cbccabfc0ed2f923ba000e326cc. Le pont JNI consomme toute l’entropie à résolution de sortie1/8, termine le décodage et transforme les avertissements en refus. Aucun décodeur Huffman partiel Kotlin n’a été créé. Le choix natif, les limites et la provenance sont détaillés dans DESIGN.md et le PROVENANCE.md du module.

Le validateur précède le passage à l’identique et la réduction ; le JPEG produit est lui-même validé. Une bibliothèque indisponible provoque un refus. Les JPEG conformes gardent leurs octets. Les API24–27 utilisent BitmapFactory avec application explicite d’EXIF ; les API28+ conservent ImageDecoder pour le rendu orienté. PNG/GIF/WebP ne sont pas transformés.

Le sender continue d’utiliser le SmsManager de la SIM choisie, son budget, le transactionId unique, le PDU exact du préparateur et les mêmes octets préparés dans le provider. Le contrôle du PDU entier n’est jamais désactivé.

## Autocorrections vérifiées

- Entropie tronquée + EOI : test initial conservé ; six nouvelles troncatures10/25/50/75/90/99% sur les JPEG produits par le code Bitmap Android du laboratoire.
- Estimation mémoire progressive corrigée : dimensions réelles de chaque composante et arrondi aux MCU. Le JPEG progressif420de12MP est accepté ; le444de même taille dépasse explicitement la politique de ressources.
- Allocation JNI : OutOfMemoryError converti en refus et validation placée dans le try mémoire du compresseur. État natif possédé par le tas, destruction du décodeur, libération JNI_ABORT sur les sorties ; bitmaps recyclés.
- CMake : intégration ExternalProject, conformément au contrat amont ; Ninja transmis au sous-build ; bibliothèque statique cachée, seul le symbole JNI public exporté. Quatre ABI compilées.
- Signature : la garde bloquait le bundle non signé à cause des tâches release de la bibliothèque. Elle ne considère maintenant que le projetapp ; le dry-run unsigned réussit et le signed reste refusé sans secrets. Aucun secret de signature n’a été ajouté.
- Annulation : trois tests ont d’abord démontré que le sender poursuivait malgré une interruption après préparation/staging/persistance. Les refus sont maintenant placés avant les effets correspondants ; la façade réellement utilisée par l’UI relie la coroutine à runInterruptible(IO). Un test supplémentaire conserve UNKNOWN et le journal si le transport a déjà été appelé. Aucun retry automatique, aucune modification du protocole de callback/récupération.
- Le texte UI ne prétend plus que le validateur strict nécessite Android9 ; les refus conservent les pièces jointes et ne promettent aucune livraison.

Revue indépendante en lecture seule : les défauts d’estimation mémoire et de frontière try ont été corrigés ; dernière revue JNI/annulation sans défaut critique ou important démontré. La revue ne compte pas comme exécution de tests.

## Résultats exécutés

Hôte Linuxx86_64, JDK17.0.21+9, Gradle9.8.0, AGP9.4.1, Kotlin2.4.20, SDK37/build-tools36.0.0, NDKr28c28.2.13676358, CMake Android officiel3.22.1. Les premiers tests amont ont utilisé CMake4.4.4 ; les passages finaux app/lab utilisent3.22.1. Robolectric4.16.1 avec graphiques natifs API27/28/35 ; compatibilité legacy API24/27. Ce n’est pas une exécution Android physique, et API36/37 ne sont pas revendiquées exécutées.

| Vérification | Réussis | Échecs | Ignorés | Preuve |
|---|---:|---:|---:|---|
| Suite application finale |1539|0|0|JUnit et cancellation-full-regression.log, commande globale exit0 |
| Laboratoire final |71|0|0|Même commande globale exit0 ; cas partagés, ne pas additionner comme couverture unique |
| wearable-contract / security |2+26|0|0|Exécutés pendant la campagne ; modules inchangés, dernier passage UP-TO-DATE |
| Suite amont libjpeg-turbo |212|0|0|ctest hôte, sources épinglées inchangées |
| JNI hôte complémentaire |13 assertions|0|—|Progressif12MP, mémoire, restart, troncatures, limites et interruption |
| Politique Android / vérité produit / hygiène |7/69/6|0|0|Trois commandes Node exécutées |
| Lint app / lab |0erreur|0|—|app223warnings+19hints ; lab4warnings conservés |
| Kotlin, Debug app/lab et AndroidTest |Assemblés|0final|—|cancellation-build.log exit0 ; pas d’exécution instrumentée |
| Bundle releaseUnsigned avec R8 |Assemblé|0final|—|cancellation-unsigned-bundle.log exit0 |
| Signatures v2 / ZIP16K / nouveaux ELF et JNI |Vérifiés sur4APK|0|—|ARTIFACT_COMMANDS.json ; unique symbole JNI et licences embarquées |
| ELF64bits de l’app et du bundle |16par paquet conformes16K|0|—|ALL_NATIVE_ELF.json ; 32bits contrôlés séparément, pas confondus avec les plateformes16K |
| Bundletool1.18.3 validate |exit0|0|—|Bundle structurellement valide, reste NON SIGNÉ |
| Permissions |30inchangées|0ajout|—|Comparaison au badging7ef494b3 ; ne vaut pas approbation Google Play |
| Appareils / MMS réels v23 |NON EXÉCUTÉS|—|—|ADB sans appareil ; aucune installation autorisée |

Les commandes exactes sont dans COMMANDS.md. Les logs/JUnit et codes de sortie sont archivés dans le dossier de preuves. Le build combiné initial a échoué à R8 avec disparition du daemon ; le compteur cgroupoom_kill=1 a été observé sous8Gio. Les builds isolés suivants ont réussi. Ce premier échec n’est pas transformé en PASS. D’autres amorçages ont nécessité le cache du pluginlibrary, l’extraction correcte des liens NDK et un répertoire temporaire RAM ; ils ne constituent aucun test JPEG positif.

## Artefacts effectivement construits

Dossier permanent : `/workspace/qualification-evidence/v23-native-8c2c2b98/`.

| Fichier | Octets | SHA-256 |
|---|---:|---|
| `app.apk` | 99453750 | `f30ec1caa4b984c66fd9e8001e7386e026517fb0829fd3bd2a7ef4de6735c79b` |
| `app-test.apk` | 1158262 | `d62c57193c1a995ce08d92a9c98283c29ca4e993bce75f3f05e009474d324198` |
| `lab.apk` | 2277484 | `8a179e194fdee7a897be3762f671c9acf775aaed0763da1e9668e3c3f689f8b8` |
| `lab-test.apk` | 944423 | `69be4f1c6a002c9c7f7d9dac655e09416c6abe2394dc154f01bc8571470003d9` |
| `app-releaseUnsigned.aab` | 43551785 | `fcfb687b5c5f1ba5824a1a44a87c576aaf5e575f29a5c403c8738b954b897379` |

`app.apk` : com.sentinel.quantum, versionCode23/versionName1.0.10, min24/target36. **Ne pas installer sur le S24+**. `app-test.apk` ne doit pas être exécuté globalement sur le téléphone de production : cette branche n’intègre pas les gardes de tous les anciens tests destructifs.

`lab.apk` : com.sentinel.quantum.mmslab.v23, sans permission téléphonique. Son installation reste interdite par la consigne actuelle, même isolée, jusqu’à une nouvelle autorisation.

Signataire cloud debug existant : fb90f8db77aff2e470ecbd9162b7308b550fdffdf9cd7c9c9daaa37c3812d7b6, différent du signataire du S24+. Le keystore existant est inchangé ; aucun contenu de clé privée ni son empreinte n’est exporté. L’AAB est volontairement non signé et non publiable. Les copies d’artefacts sont indépendantes des sorties Gradle, sans lien dur ; le vérificateur refuse l’écrasement d’une archive différente.

Les premiers artefacts49a38b4d ont été retirés avant livraison lorsque les tests d’annulation ont révélé le défaut. Leurs logs/empreintes restent historiques, pas des APK proposés à l’installation. Les anciens artefacts publiés d’autres chantiers et la v22 n’ont pas été modifiés.

## Limites et critères restants

**BLOCKED pour livraison finale**, malgré les vérifications automatisées disponibles réussies : JNI/Bionic/Android réels, pile Samsung, mémoire réelle et livraison opérateur n’ont pas été exécutés. Un build réussi ou un PDU conforme ne prouve jamais un MMS livré.

La politique accepte uniquement les JPEG8bits,5MiBmaximum,20MPsource ; estimation progressive60MiB, réglage décodeur64MiB sans backing-store,128scans maximum. Ce n’est pas un quotaRSS absolu ni global entre threads. Des JPEG valides dépassant ces limites sont refusés. Les tests ne prouvent pas une acceptation exhaustive de tous les fichiers JPEG ni l’intégrité photographique cryptographique.

La compilation Termux/prootUbuntuARM64 n’a pas été exécutée. Le NDK officiel utilisé a un hôte Linuxx86_64 ; l’ancien pipeline Kotlin/AAPT2ARM64 ne suffit pas à prouver la recompilation de cette dépendance native. Aucun outil ARM64 non vérifié n’a été substitué.

Cette PR n’est pas une consolidation des autres branches Doctor/GeoIntel/guard/GooglePlay et ne prétend pas approuver les30permissions ou les déclarations PlayConsole. Aucun upload Play, signature de production, main merge ou release officielle.

Procédure physique et préservation v22 : [PHYSICAL_RUNBOOK.md](PHYSICAL_RUNBOOK.md). À ce stade, revenir à v22 consiste à la laisser installée et l’ouvrir : aucune restauration, suppression de données ou downgrade nécessaire.
