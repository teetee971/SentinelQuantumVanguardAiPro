# Livraison courante D.1 — actions sur le numéro affiché

## Provenance et référence

Sources APK : **d7fce52a53ed5edfe88415f4bd0d03b3132493b6**. Branche `fix/d1-phone-messages-conformity-20261029`, PR brouillon #1748, candidat précédent aed338fb. Livraison nouvelle et séparée : `/workspace/d1-delivery-conversation-actions/`. Tailles, SHA et chemins dans `ARTIFACTS.json`. Les commits documentaires ultérieurs ne changent pas ces sources Android.

Référence : R02 « Messages sécurisés », registre `VISUAL_REFERENCES.md`. Cadrage : `docs/superpowers/specs/2026-10-29-d1-conversation-actions-design.md`. Il s’agit des raccourcis Appeler/Ajouter prévus sur cette vue, pas d’un enrichissement d’identité ou d’un nouveau service.

## Implémentation réelle

- Le composant d’en-tête partagé reçoit un slot `actions` optionnel et vide par défaut. **SmsComposeActivity l’utilise réellement**, seulement quand une conversation est ouverte et que le compositeur n’est pas visible. Téléphone/Contacts conservent leur présentation existante.
- ConversationNumberActions lit uniquement l’adresse du résumé affiché. Il ne remplace pas une adresse absente par celle d’un autre message et ne déduit aucun groupe/participant/identité.
- La politique réutilise les séparateurs visuels déjà acceptés par ContactDialNumberPolicy, puis n’accepte que chiffres ASCII et + initial. Labels alphanumériques, URI, retours ligne, adresses multiples délimitées, pauses/attentes, extensions et codes *# sont refusés. Aucun pays n’est deviné. Le format accepté ne prouve pas la joignabilité.
- Appeler ce numéro construit un **ACTION_DIAL explicite vers SentinelDialerActivity** avec `tel:`. Le composeur est prérempli ; aucun ACTION_CALL ni appel automatique ajouté. L’appel ne peut être demandé qu’ensuite par l’utilisateur dans le parcours existant.
- Ajouter aux contacts construit **ACTION_INSERT_OR_EDIT**, MIME Contacts Android et extra PHONE modifiable. Aucune écriture ContentResolver ou sauvegarde automatique ; l’utilisateur confirme dans l’éditeur Android. Aucune permission READ/WRITE_CONTACTS supplémentaire demandée pour ce raccourci.
- Si l’adresse n’est pas exploitable, un motif remplace les boutons. Si une Activity cible ne peut pas s’ouvrir, une erreur fixe est affichée sans exception ou numéro supplémentaire dans les logs.
- Les deux boutons utilisent une FlowRow sous les informations de l’en-tête, avec minimum tactile 48dp, pour des libellés lisibles à largeur/police variables. Ce placement adaptatif ne constitue pas une reproduction pixel à pixel des icônes à droite de R02. Le bouton Plus non implémenté n’est pas ajouté.

Aucun rôle, manifeste, permission, préférence, schéma Room, DAO, pipeline d’appel/envoi ou données SMS/MMS existantes modifiés. La revue statique indépendante n’a trouvé aucun défaut important avéré ; elle ne prouve pas l’ouverture réelle des applications Samsung.

## Résultats exécutés

| Contrôle | Résultat vérifié | Preuve relative au dossier |
|---|---|---|
| Phase rouge initiale | Échec compilation, sortie1 : nouvelle politique non implémentée ; aucun test exécuté à cette étape | `evidence/conversation-actions/conversation-actions-red.log` et `.exit` |
| Première tentative ciblée | Échec compilation des tests Android, sortie1 : import assertDoesNotExist invalide, corrigé avant livraison | `evidence/conversation-actions/conversation-actions-targeted.log` et `.exit` |
| Tentative ciblée corrigée | Réussie, sortie0, 28s : cinq tests JVM et compilation des tests Android | `evidence/conversation-actions/conversation-actions-targeted-fixed.log` et `.exit` |
| Suite app complète + assembleDebug + assembleDebugAndroidTest | Réussie, sortie0, 50s ; Kotlin à jour après compilation ciblée | `evidence/conversation-actions/conversation-actions-full.log` et `.exit` |
| Suite unitaire app finale | **1386 exécutés, 1386 réussis, 0 échec, 0 erreur, 0 ignoré**, 300 suites XML | `evidence/conversation-actions/conversation-actions-unit-summary.json` ; XML complets dans la livraison |
| Contrats Node relancés | **19 exécutés et réussis, 0 échec/ignoré**, sortie0 | `evidence/conversation-actions/conversation-actions-node.log` et `.exit` |
| Signature des deux APK | apksigner réussi, sorties0 ; certificat debug inchangé | `evidence/conversation-actions/conversation-actions-*-signature.log` et `.exit` |
| Alignement des deux APK | zipalign -c -v 4 réussi, sorties0 | `evidence/conversation-actions/conversation-actions-*-alignment.log` et `.exit` |
| Intégration réelle aux sources et paquets | Callsite de l’Activity, slot et classes applicatives présents ; nouvelles classes de tests et test MMS précédent présents dans l’APK instrumenté | `evidence/conversation-actions/source-wiring.log`, `apk-debug-classes.json`, `apk-androidTest-classes.json` |
| Room non suivi | Schéma présent, inchangé : a7c5b4e1926c4441629734c0703ba6174e6bcf3796394b51302846bac0dae0c4 | `evidence/conversation-actions/conversation-actions-room.sha256` |
| Manifeste/build source | Aucun changement ; applicationId com.sentinel.quantum, version1.0.9/code10, min24/target36/compile37 | `evidence/conversation-actions/conversation-actions-manifest-config.log`, `conversation-actions-apk-badging.log` |
| Appareils ADB | Aucun connecté ; ce n’est pas un test de l’application | `evidence/conversation-actions/conversation-actions-adb-devices.log` |

Les cinq nouveaux tests JVM portent sur formatage/local sans pays deviné, labels/absence, codes spéciaux/contrôles, URI/adresses multiples et limites/Unicode. Les régressions photo 8, navigation 4 et brouillon MMS 8 restent incluses et réussies dans la suite complète. Les 1386 sont le total d’une seule exécution app finale ; Node 19 est distinct. Les échecs de compilation précédents sont conservés et ne sont pas des échecs restants de cette suite finale.

Commande de qualification : lanceur de session `/workspace/d1-evidence/run-gradle.py`, JDK17/Gradle9.8.0/SDK conservés :

```text
:app:compileDebugKotlin :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
-PsentinelSignatureFixture=/workspace/phone-core-v1-delivery/Sentinel-PhoneCore-V1-debug.apk
--no-daemon --console=plain --max-workers=2
```

Contrats Node :

```bash
node --test scripts/phone-core-certification-truth.test.js scripts/android-release-policy.test.js scripts/intelligence-product-truth.test.js
```

## Préparé mais NON EXÉCUTÉ

Cinq nouveaux scénarios Android compilés : trois inspectent les intents sans lancer d’Activity/appel/contact ; deux rendent le vrai composant Compose, vérifient ses clics/numéro et la largeur 280dp/police 200%, ou l’absence de commandes pour BANQUE. Aucun SMS/contact n’est injecté et aucune opération téléphonique ou sauvegarde n’est déclenchée par ces tests.

**Tous sont NON EXÉCUTÉS**, faute de runtime Android utilisable. Ils ne valident donc ni les intentions résolues sur Samsung, ni leur aller-retour, ni le rendu réel en grands caractères. Le résultat APK debug seul ne prouve pas un lancement sans crash. La comparaison visuelle complète D.1 reste NON EXÉCUTÉE.

Après examen, sur profil de test :

```bash
sha256sum -c SHA256SUMS.txt
adb install -r Sentinel-D1-debug-d7fce52a.apk
adb install -r Sentinel-D1-androidTest-d7fce52a.apk
adb shell am instrument -w -e class com.sentinel.quantum.ui.ConversationNumberIntentsInstrumentationTest,com.sentinel.quantum.ui.design.ConversationNumberActionsInstrumentationTest com.sentinel.quantum.test/androidx.test.runner.AndroidJUnitRunner
```

Scénarios S24+ dans `PHYSICAL_TESTS_AND_CAPTURE.md` : vérifier le numéro prérempli sans appeler, revenir, ouvrir Contacts puis annuler sans sauvegarder, essayer un expéditeur alphanumérique et une adresse absente, vérifier TalkBack/police 200%. Toute création réelle et tout appel effectif se font uniquement avec données/lignes de test connues et confirmation manuelle. Ne pas effacer les données ni désinstaller pour contourner un certificat.

## Limites conservées

- S24+, ouverture réelle du composeur/Contacts Samsung, appels et SMS/MMS opérateur, révocations : **NON TESTÉS pour ce candidat**.
- Fidélité du rendu aux maquettes, captures réelles et contraste tactile effectif : **NON EXÉCUTÉS**.
- MMS de groupe : l’adresse du résumé peut être une adresse individuelle ; les raccourcis ciblent seulement le numéro affiché, sans prétendre représenter tous les participants.
- Images de brouillon : retenues en mémoire pendant configuration changes de la même session, pas après mort du processus. Pas de nouvelle persistance durable.
- Voice : dernier constat conservé live200/ready503, cause précise non démontrée. Pas de nouvelle sonde ni modification de secrets ; transformation d’appel SIM non prise en charge.
- Autres capacités avancées et exigences Play : KNOWN_ISSUES.md. Aucun AAB/release/bundletool ni publication Google Play de ce candidat debug.

D.1 n’est pas déclaré terminé sur la base des tests unitaires.
