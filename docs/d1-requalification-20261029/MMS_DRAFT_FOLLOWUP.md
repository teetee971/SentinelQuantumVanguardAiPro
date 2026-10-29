# Livraison courante D.1 — images MMS et recréation d’écran

> Rapport historique des sources aed338fb. Voir [CURRENT_DELIVERY.md](CURRENT_DELIVERY.md) pour le candidat actuel ; ARTIFACTS.json décrit désormais ce dernier.

## Provenance et comportement

Sources des APK : **aed338fbd14ae49cd65a9998f5a8f47d9191edc0**. Branche `fix/d1-phone-messages-conformity-20261029`, PR brouillon #1748 ; candidat précédent 8cc1ad4e. Livraison locale nouvelle et séparée : `/workspace/d1-delivery-mms-draft/`. Tailles, SHA-256 et chemins exacts dans `ARTIFACTS.json`. Un commit documentaire ultérieur ne change pas les sources Android de ces APK.

Avant, les images sélectionnées étaient dans un remember de l’Activity : elles disparaissaient lors de sa recréation. Le chargement était aussi lié au scope de cette composition.

Après correction :

- Un AndroidViewModel associé à l’Activity conserve le brouillon d’images et son chargement pendant les changements de configuration. Il utilise uniquement l’Application, pas l’ancienne Activity.
- Les payloads restent en mémoire, avec les bornes existantes du loader : huit images maximum, 5MiB par image, 10MiB cumulés. Le correctif ne sérialise ni image ni URI dans un Bundle/SavedStateHandle, n’ajoute aucun fichier persistant ni table, et ne prend aucun grant URI persistant. Le pipeline d’envoi existant et ses fichiers PDU temporaires ne sont pas remplacés.
- Le résultat d’un chargement annulé ou remplacé est rejeté par annulation et génération. Effacer, retirer ou revenir au mode SMS ne permet pas à une ancienne lecture de réintroduire des images. Annuler le sélecteur conserve la sélection précédente.
- L’envoi attend le chargement des images. La frontière submitMms lit le dernier instantané du brouillon et refuse un instantané pendant loading, indépendamment de l’état visuel du bouton. Elle ne réutilise pas une ancienne liste capturée par l’UI.
- Le sélecteur peut remplacer une sélection déjà au maximum ; la limite est appliquée par le loader plutôt qu’en rendant le bouton de remplacement inactif. Les erreurs restent explicites et sans exception ou URI brute affichée.
- Une fermeture définitive libère l’état du ViewModel. **La mort du processus n’est pas couverte** : aucune nouvelle persistance durable de brouillon n’est créée, les images devront être resélectionnées.

Aucun rôle, permission, manifeste, préférence, schéma Room, DAO ou transport opérateur n’est modifié par cette itération. Aucun SMS/MMS ni contact existant n’est supprimé. Aucune fonction d’envoi automatique n’est ajoutée.

## Vérifications réellement exécutées

| Contrôle | Résultat | Preuve relative au dossier |
|---|---|---|
| Première phase rouge | Compilation échouée, sortie1, contrôleur non implémenté ; aucun test exécuté à cette étape | `evidence/mms-draft/mms-draft-red.log` et `.exit` |
| Régression de frontière avant correction | **1 test exécuté, 1 échec attendu**, sortie1 : un instantané était disponible pendant le chargement | `evidence/mms-draft/mms-loading-gate-red.log`, `.exit` et `.xml` |
| Tests ciblés + compilation des tests instrumentés | Réussis, sortie0, 48s ; huit cas du contrôleur | `evidence/mms-draft/mms-draft-targeted.log` et `.exit` |
| Suite app complète et génération APK debug/AndroidTest | Réussies, sortie0, 47s ; Kotlin à jour après compilation ciblée | `evidence/mms-draft/mms-draft-full.log` et `.exit` |
| Suite unitaire app finale | **1381 exécutés, 1381 réussis, 0 échec, 0 erreur, 0 ignoré**, 299 suites XML | `evidence/mms-draft/mms-draft-unit-summary.json` ; XML complets dans les archives |
| Contrats Node relancés | **19 exécutés et réussis, 0 échec/ignoré**, sortie0 | `evidence/mms-draft/mms-draft-node.log` et `.exit` |
| Signature des deux APK | apksigner réussi, sorties0 ; certificat debug inchangé | `evidence/mms-draft/mms-draft-*-signature.log` et `.exit` |
| Alignement des deux APK | zipalign -c -v 4 réussi, sorties0 | `evidence/mms-draft/mms-draft-*-alignment.log` et `.exit` |
| Intégration au paquet | MmsAttachmentDraft et MmsAttachmentDraftViewModel présents dans le DEX ; Activity réellement branchée au ViewModel | `evidence/mms-draft/apk-mms-draft-code.json` et sources de SmsComposeActivity |
| Schéma Room non suivi | Présent, inchangé : a7c5b4e1926c4441629734c0703ba6174e6bcf3796394b51302846bac0dae0c4 | `evidence/mms-draft/mms-draft-room.sha256` |
| Manifeste/build source | Aucun changement ; applicationId com.sentinel.quantum, version1.0.9/code10, min24/target36/compile37 | `evidence/mms-draft/mms-draft-manifest-config.log`, `mms-draft-apk-badging.log` |
| Appareils ADB | Aucun appareil connecté ; ce n’est pas une validation runtime | `evidence/mms-draft/mms-draft-adb-devices.log` |

Les huit cas JVM couvrent : instantané indisponible pendant chargement, annulation du sélecteur, retrait d’une image, clear suivi d’une réponse tardive, remplacement suivi d’une ancienne réponse tardive, sélection refusée, nombre excessif avant lecture et erreur de fournisseur. Ils utilisent le vrai contrôleur avec une lecture contrôlée ; les bornes/MIME/signatures d’image restent celles du loader existant, testé séparément dans la suite. Les données unitaires de contrôle ne prétendent pas être des images opérateur réelles.

La revue statique indépendante n’a identifié aucun défaut important avéré. Elle n’a exécuté aucun test et ne prouve pas le comportement Samsung. Les 1381 correspondent à une seule suite app finale ; Node 19 est une suite distincte. Le backend Voice inchangé n’a pas été retesté ; aucune nouvelle disponibilité distante n’est revendiquée.

Commandes : lanceur de session `/workspace/d1-evidence/run-gradle.py`, JDK17/Gradle9.8.0/SDK existants ; qualification finale :

```text
:app:compileDebugKotlin :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
-PsentinelSignatureFixture=/workspace/phone-core-v1-delivery/Sentinel-PhoneCore-V1-debug.apk
--no-daemon --console=plain --max-workers=2
```

Contrats supplémentaires :

```bash
node --test scripts/phone-core-certification-truth.test.js scripts/android-release-policy.test.js scripts/intelligence-product-truth.test.js
```

## Instrumentation préparée, NON EXÉCUTÉE

Deux scénarios Activity/Compose compilés : image validée toujours présente après recréation ; image retirée qui ne revient pas après recréation. Ils passent par le véritable loader, le ViewModel et le FileProvider existant, avec un PNG de test de 8×8 créé dans un chemin cache déjà autorisé puis supprimé individuellement. Aucun grant persistant, aucune insertion SMS/contact, aucun envoi et aucun effacement de base dans ces tests.

Ils **n’exercent pas** le sélecteur Samsung, ses permissions temporaires, une recréation pendant une lecture ou un envoi, ni la mort du processus. Aucun scénario n’a été exécuté ici, faute de runtime Android utilisable. La rétention pendant une rotation est donc implémentée et préparée à la qualification, pas déclarée PASS matériel.

Installation et commande après examen, sur profil de test autorisé :

```bash
sha256sum -c SHA256SUMS.txt
adb install -r Sentinel-D1-debug-aed338fb.apk
adb install -r Sentinel-D1-androidTest-aed338fb.apk
adb shell am instrument -w -e class com.sentinel.quantum.ui.MmsAttachmentRecreationInstrumentationTest com.sentinel.quantum.test/androidx.test.runner.AndroidJUnitRunner
```

Ne pas désinstaller ou effacer les données pour contourner un certificat incompatible. Scénarios manuels dans `PHYSICAL_TESTS_AND_CAPTURE.md` : sélection/rotation, retrait, remplacement à huit images, annulation, fournisseur illisible, chargement lent, rôle révoqué, résultat d’envoi opérateur. Ne jamais automatiser un envoi ou appel vers un numéro inconnu/surtaxé.

## Limites restantes

- S24+ du candidat, appels réels, réception/envoi/livraison SMS/MMS, révocations et UI/IME : **NON TESTÉS**.
- Captures et fidélité visuelle aux maquettes D.1 : **NON EXÉCUTÉES**. Les illustrations ne sont pas des captures de l’application.
- Restauration des images après mort du processus : **NON IMPLÉMENTÉE**, resélection nécessaire. Aucun nouveau stockage durable de brouillon.
- Rotation pendant chargement/envoi : **NON TESTÉE matériellement** ; aucun renvoi automatique ajouté. Le résultat de livraison reste distinct de l’acceptation Android.
- Voice : dernier constat conservé live200/ready503, cause précise non établie ; pas de nouvelle sonde ni changement de secrets. Transformation audio SIM non prise en charge.
- Autres fonctions avancées, permissions Google Play et conformité complète : voir KNOWN_ISSUES.md. Aucun AAB/release/bundletool ni publication Play de ce candidat debug.

D.1 n’est pas déclaré terminé sur la base des résultats unitaires.
