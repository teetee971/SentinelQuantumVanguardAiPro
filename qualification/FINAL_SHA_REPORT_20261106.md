> Qualification historique. État courant : [reprise API 24/25](NOTIFICATION_SETTINGS_FOLLOWUP_20261106.md) et `VALIDATION_LEDGER.json`.

# SENTINEL — qualification et gel fonctionnel

**Statut global : BLOCKED. Contrôles techniques locaux : PASS. Galaxy S24+ : NOT_TESTED.**

Le SHA demandé `249c347768932b73a958bb3dd953958f152a1ac8` est rejeté : les quatre commandes demandées échouent sur `PhoneSecurityScreen.kt:840`, import Alignment manquant. Aucune réutilisation des anciens PASS. Les journaux et empreintes de ce SHA sont dans `evidence/20261106-initial-249c3477`.

Le code corrigé qualifié est `1ac8bd5e325416bdb5ba1bca59566a561393b45b`, arbre Android `0547bb2780ec041a204bac8ad7ebf1b4925ee34e`. Les commits ultérieurs de preuves doivent conserver cet arbre. Le SHA de livraison contenant ce rapport est vérifié séparément avant poussée ; les journaux correspondants sont conservés hors du dépôt pour éviter une autoréférence du commit dans son propre contenu.

## Résultats exacts

| Contrôle | Résultat |
| --- | --- |
| PhoneSecurityVisualDashboardSourceContractTest | 1 test, 0 échec, 0 erreur, 0 ignoré |
| :app:testDebugUnitTest sans fixture | 1 405 tests, 0 échec, 0 erreur, 1 ignoré (SIGNED_APK_FIXTURE_REQUIRED) |
| :app:testDebugUnitTest --rerun avec le nouvel APK signé | 1 405 tests, 0 échec, 0 erreur, **0 ignoré** |
| :app:lintDebug | PASS ; 0 erreur, 221 warnings, 19 hints |
| :app:assembleDebug | PASS |
| Régression du curseur MMS | 4 tests ; 2 échecs reproduits avant correction, puis 0 échec |
| Cohérence produit Android | 69 tests, 0 échec |
| Provenance | 6 tests, 0 échec avec le lanceur direct |
| Hygiène des credentials | 6 tests, 0 échec |
| Frontend après patch Vite | 13 tests, build statique et démarrage HTTP local réussis |
| APK | Signature v2, zipalign et badging vérifiés ; aucun motif de secret reconnu dans les entrées décompressées |

L’invocation initiale par découverte unittest des tests de provenance a échoué : ce script existant charge son helper uniquement dans son bloc __main__. Son exécution directe réussit sans modification du script. Les tests de contrat source ne remplacent pas les essais Android réels.

## Corrections et environnement

Import Alignment rétabli ; lecture des métadonnées MMS avant le passage après-fin du curseur ; Vite 8.3.1 → 8.3.3 pour six avis de sécurité. Aucun ajout de fonctionnalité, d’écran ou de dashboard ; aucun changement de schéma Room, de dépendance Android, du wrapper ou des versions Gradle/JDK/SDK du projet. Pas de mise à jour majeure.

Gradle 9.8.0 vérifié par SHA-256 officiel, Temurin 17.0.21+9 et SDK `platforms;android-37.0` / build-tools 36.0.0 installés sous `/workspace/toolchains`, hors dépôt. Le wrapper fonctionne avec proxy Java et truststore système, TLS actif. Les anciens outils documentés sous /tmp ne sont pas présents ; la cause historique du refus de connexion n’est pas démontrable dans cette session. L’URL exacte 9.8.0 fonctionne ; l’URL abrégée 9.8 ne désigne pas cette distribution. Les dépôts Gradle, Google Maven, Maven Central et plugins répondent avec TLS valide. Le repli Termux/proot ou Codemagic n’est pas nécessaire dans cette session ; aucun compte ou workflow externe n’a été lancé.

Reproduction : fournir les outils ci-dessus, JAVA_HOME et ANDROID_HOME, puis `bash qualification/REPRODUCE_FINAL_SHA.sh SHA_EXACT /chemin/externe/preuves`. Derrière le proxy existant, conserver les variables HTTP(S)_PROXY et configurer les propriétés proxy Java et le truststore système. Le script exige un arbre propre et le SHA exact, ne dispatch aucun workflow et n’installe aucun APK.

## Artefact

- Chemin : `native-android-app/app/build/outputs/apk/debug/app-debug.apk`.
- SHA-256 : `2460b1643bec1137a4a78a18816f3db6cce328c2b94f0434de7c192fae6aa346`.
- Package : `com.sentinel.quantum` ; versionCode `10` ; versionName `1.0.9` ; minSdk `24` ; targetSdk `36` ; compileSdk `37`.
- Certificat debug SHA-256 : `fb90f8db77aff2e470ecbd9162b7308b550fdffdf9cd7c9c9daaa37c3812d7b6`.
- Clé debug créée par ce nouvel environnement ; ce n’est pas une signature release. La continuité de certificat avec une installation existante n’est pas qualifiée. Ne pas désinstaller une installation contenant des données pour contourner un éventuel conflit de signature.
- APK et archive de preuves remis localement ; aucun mécanisme d’hébergement externe durable n’est configuré. Aucun tag/release susceptible de lancer une CI n’est créé.

## Intégrité, avertissements et dépendances

Les contrats Phone Core/SMS/MMS/Contacts, la suite unitaire et la cohérence produit passent. Cela ne prouve pas l’absence de régression physique des providers, des appels ou de l’affichage. La source de la base Room et le schéma version 1 sont identiques à la base de la PR ; identityHash `21617aeab12bfb29ee9bec17bc238d62`. Aucun effacement ou migration destructive ajouté. La migration sur appareil reste NOT_TESTED. Les scans de motifs de secrets ne constituent pas une preuve exhaustive d’absence de secret.

La classification complète des **221 warnings / 19 hints** est dans `LINT_CLASSIFICATION_20261106.md`. Les points résiduels principaux sont les 25 InlinedApi, la visibilité partielle des applications, la sauvegarde/transfert OEM et les écritures SharedPreferences synchrones. En particulier, les chemins de paramètres de notifications API 26 doivent être validés sur API 24/25 ; le repli n’est pas établi pour ces chemins.

Six avis npm identifiés précisément sur Vite, corrigés en 8.3.3 ; audit npm final : **0 vulnérabilité**. Voir `DEPENDENCY_REVIEW_20261106.md`. L’état GitHub Dependabot reste **UNVERIFIED** (REST 403 ; GraphQL liste vide incohérente avec les six alertes signalées). Aucune affirmation de fermeture des alertes ou d’absence de vulnérabilités Maven/Python.

## Essais physiques restants

- Galaxy S24+ : installation compatible avec la signature existante, conservation des données et redémarrage ; rôles Téléphone/SMS, notifications et permissions refusées/révoquées.
- Appels entrants/sortants, écran verrouillé, proximité, audio/Bluetooth, interruption, fin d’appel et double SIM.
- SMS entrants/sortants, Unicode/multipart, SIM sélectionnée ; MMS images/groupes, téléchargement, thumbnails et provider réel, permissions révoquées et annulation.
- Contacts réels, recherche et action d’appel ; conservation des parcours existants.
- Room : ouverture d’une base existante, rétention et schéma après mise à niveau ; sauvegarde/transfert OEM.
- APIs 24/25/28/29/31/37 : compatibilité des rôles, intents de réglages, visibilité des apps et permissions ; TalkBack, gros caractères, contraste et rotation.

PR #1754 doit rester **DRAFT**, sans merge automatique. Poussée limitée à `codex/sentinel-audit-20261105`, avec `[skip ci]`. Aucun workflow GitHub Actions lancé.
