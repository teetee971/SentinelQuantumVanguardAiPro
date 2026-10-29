# Livraison courante D.1 — navigation Messages

## Provenance et périmètre

Sources APK : **8cc1ad4ecad1b95a1542ef6203418cdf0f42101d**, branche `fix/d1-phone-messages-conformity-20261029`, PR brouillon #1748. Le candidat précédent est 70d5cecd (photos), conservé séparément. Les nouveaux fichiers sont dans `/workspace/d1-delivery-sms-navigation/` ; `ARTIFACTS.json` donne les tailles, SHA-256 et chemins exacts. Les commits documentaires ultérieurs ne modifient pas les sources Android de ces APK.

## Défauts et correctifs

- Avant : Retour de l’en-tête appelait toujours finish(), même depuis une conversation ; la touche/geste système n’avait pas de gestion de niveau interne. Après : conversation → liste ; compositeur ouvert depuis la liste → liste ; liste racine ou compositeur externe SENDTO → application appelante. La toolbar et le callback Android partagent la même politique.
- Avant : choisir Écrire conservait selectedThreadId ; le nouveau compositeur pouvait garder l’expéditeur précédent en en-tête. Après : désélection de la conversation, vidage des messages affichés en mémoire, titre Nouveau SMS/Nouveau MMS selon le mode. Le chargement d’une conversation est également ignoré lorsque le compositeur est visible.
- La navigation interne conserve les champs texte rememberSaveable et le contexte du retour après recréation. Aucun transport, envoi, rôle, préférence, table ou écriture Contacts/SMS/MMS n’est ajouté ou modifié. Les pièces jointes restent un état remember préexistant, non restauré après recréation : cette limite n’est pas prétendue corrigée.
- Suppression du titre de composition en double ; formulation MMS compréhensible (« envoi via votre opérateur ») en remplacement du détail technique de PDU. Aucun chiffrement de bout en bout ni service distant disponible n’est inventé.

Une revue indépendante du code et des ajouts finaux de tests n’a identifié aucun défaut important avéré. Elle reste statique : pas de test matériel effectué par le reviewer.

## Résultats vérifiés

| Contrôle réellement exécuté | Résultat | Preuve |
|---|---|---|
| Phase rouge initiale | Échec compilation, sortie1 : nouvelle politique pas encore implémentée ; aucun test exécuté à cette étape | `evidence/sms-navigation/sms-navigation-red.log` et `.exit` |
| Tests ciblés + compilation des tests instrumentés | Réussis, sortie0, 43s ; politique navigation 4 tests | `evidence/sms-navigation/sms-navigation-targeted.log` et `.exit` |
| Qualification app complète : compileDebugKotlin, testDebugUnitTest, assembleDebug, assembleDebugAndroidTest | Réussie, sortie0, 49s ; Kotlin compilé lors de l’étape ciblée, tâche à jour lors du build final | `evidence/sms-navigation/sms-navigation-full.log` et `.exit` |
| Suite unitaire app finale | **1373 exécutés, 1373 réussis, 0 échec, 0 erreur, 0 ignoré**, 298 suites XML | `evidence/sms-navigation/sms-navigation-unit-summary.json` ; XML complets dans les archives |
| Régressions photo conservées | Politique/identité7 + annulation1 réussis, inclus dans les 1373 | ContactPhotoPolicyTest et ContactPhotoLoadTest |
| Contrats Node relancés | **19 exécutés et réussis, 0 échec/ignoré**, sortie0 | `evidence/sms-navigation/sms-navigation-node.log` et `.exit` |
| Deux APK : signature | apksigner réussi, sorties0, signature v2 et certificat debug inchangé | `evidence/sms-navigation/sms-navigation-*-signature.log` et `.exit` |
| Deux APK : alignement | zipalign -c -v 4 réussi, sorties0 | `evidence/sms-navigation/sms-navigation-*-alignment.log` et `.exit` |
| Code embarqué | SmsNavigationPolicy présente dans le DEX de l’APK debug | `evidence/sms-navigation/apk-navigation-code.json` |
| Manifeste/build source | Aucun changement par cette itération ; applicationId com.sentinel.quantum, version1.0.9/code10, min24/target36/compile37 | `evidence/sms-navigation/sms-navigation-manifest-config.log`, `sms-navigation-apk-badging.log` |
| Room non suivi | Schéma présent, SHA inchangé a7c5b4e1926c4441629734c0703ba6174e6bcf3796394b51302846bac0dae0c4 | `evidence/sms-navigation/sms-navigation-room.sha256` |
| Appareil connecté | Aucun appareil listé par ADB ; ce n’est pas un test de l’application | `evidence/sms-navigation/sms-navigation-adb-devices.log` |

Les quatre tests JVM de navigation contrôlent conversation→liste, racine→sortie, compositeur interne→liste et compositeur externe→sortie même en présence d’une ancienne sélection. Ils ne montent pas l’Activity. Les 1373 sont le total d’une seule suite app finale ; Node 19 est une suite distincte. Le backend Voice inchangé n’est pas retesté et aucune disponibilité distante nouvelle n’est revendiquée.

Commandes de qualification : lanceur de session `/workspace/d1-evidence/run-gradle.py`, JDK17/Gradle9.8.0 et SDK conservés ; tâches finales :

```text
:app:compileDebugKotlin :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
-PsentinelSignatureFixture=/workspace/phone-core-v1-delivery/Sentinel-PhoneCore-V1-debug.apk
--no-daemon --console=plain --max-workers=2
```

Contrats supplémentaires :

```bash
node --test scripts/phone-core-certification-truth.test.js scripts/android-release-policy.test.js scripts/intelligence-product-truth.test.js
```

## Instrumentation et physique : NON EXÉCUTÉS

Quatre scénarios Activity/Compose ont été ajoutés et compilés :
1. Composer depuis la liste, vérifier les titres SMS puis MMS, saisir un brouillon, recréer l’Activity, revenir par la toolbar et retrouver le texte.
2. Retour du compositeur interne par le dispatcher : liste visible, Activity toujours reprise.
3. SENDTO externe puis retour toolbar : Activity terminée.
4. SENDTO externe puis retour dispatcher : Activity terminée.

Ils utilisent l’Activity réelle, ne déclenchent aucun envoi et n’insèrent aucun SMS ou contact dans un provider. **Aucun n’a été exécuté ici**, faute de runtime Android utilisable. Le dispatcher ne teste pas le geste/touche physique avec clavier/IME. Une vraie conversation sélectionnée reste également à vérifier sur appareil avec données de test autorisées.

Installation et commande sur profil de test, après examen :

```bash
sha256sum -c SHA256SUMS.txt
adb install -r Sentinel-D1-debug-8cc1ad4e.apk
adb install -r Sentinel-D1-androidTest-8cc1ad4e.apk
adb shell am instrument -w -e class com.sentinel.quantum.ui.SmsNavigationInstrumentationTest com.sentinel.quantum.test/androidx.test.runner.AndroidJUnitRunner
```

Voir `PHYSICAL_TESTS_AND_CAPTURE.md` pour la procédure complète et le retour vers Téléphone Samsung. Ne jamais effacer les données ou désinstaller pour contourner un certificat incompatible. Ne pas effectuer d’appel/envoi vers un numéro inconnu ou surtaxé.

## Limites restantes

- S24+, appels réels, SMS/MMS opérateur, permissions/révocations, photos sur provider Samsung : **NON TESTÉS pour ce candidat**.
- Comparaison visuelle du rendu aux références D.1 : **NON EXÉCUTÉE** ; aucune capture réelle du nouveau candidat obtenue.
- Brouillons : conservation texte dans l’Activity et sa recréation ; pas de nouveau stockage durable après sortie définitive, pièces jointes non restaurées après recréation.
- Voice : dernier constat conservé live200/ready503, cause précise non établie ; pas de nouvelle sonde ni changement de secrets. Transformation d’appel SIM non prise en charge.
- Contacts enrichis, capacités avancées illustrées et autres limites : voir KNOWN_ISSUES.md. Aucun AAB, build release, bundletool ni publication Google Play effectués pour ce candidat debug.

D.1 n’est pas déclaré terminé sur la base de ces tests automatisés.
