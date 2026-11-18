# Audit contradictoire · Sentinel Phone Core V1

**18 novembre 2026. Verdict : NO-GO production.**

Cet audit a lu le dépôt sur `cursor/v1-play-compliance-20261118-c39e` et les pages Play citées. Il n’a pas ouvert la Play Console, pas reconstruit l’AAB, pas capturé le réseau, pas exécuté les tests. Le compte Play n’est pas à recréer. Aucune fusion. La PR #1767 n’est pas modifiée.

Les déclarations du développeur (compte personnel déjà créé, application « Sentinel Quantum Pro », non-trader, Thierry Naud, e-mail, gratuit) sont des déclarations. Elles ne sont pas une preuve de conformité. Le nom « Sentinel Quantum Pro » n’apparaît pas dans le dépôt : il n’a pas été revu dans la Console.

## 1. Verdict

**NO-GO.** La publication gratuite n’est pas démontrée comme sûre ni comme conforme. Plusieurs faits de code sont établis. Plusieurs preuves exigées avant publication sont absentes.

## 2. Anomalies

| Id | Gravité | Constat | Preuve | Statut |
| --- | --- | --- | --- | --- |
| A1 | P0 | Le bouton « Politique de confidentialité » ouvre la politique de la vitrine, pas la politique V1. | `CommercialPhoneCoreScreens.kt` lignes 134-137, URL `https://sentinelquantumvanguardaipro.pages.dev/public/privacy.html` | Confirmé dans le source. NON CORRIGÉ. |
| A2 | P0 | Un appel français peut déclencher un GET de catalogue sans l’interrupteur de consentement. | `SentinelCallScreeningService.kt` lignes 157-174 ouvre `CallerIdActivity`. `CallerIdActivity.kt` lignes 172-179 appelle `lookup()` sans test de `COMMERCIAL_PHONE_CORE_V1` ni de l’interrupteur. `ArcepDirectoryClient.kt` lignes 44-54 télécharge si aucune copie fraîche n’est en mémoire. | Confirmé dans le source. NON EXÉCUTÉ sur appareil. |
| A3 | P0 | La recherche du composeur télécharge aussi hors interrupteur. | `SentinelDialerActivity.kt` lignes 817-855. | Confirmé dans le source. |
| A4 | P0 | Pas d’AAB signé revérifié sur l’arbre courant, pas de manifeste fusionné produit dans cette session, pas de recette S24+. | `audit/v1-commercial/TEST_EVIDENCE.md` rattache ses PASS au commit `25c4e2c9`, antérieur à `113862500`. Cette session n’a pas relancé Gradle. | NON EXÉCUTÉ ici. Les PASS anciens ne couvrent pas l’arbre courant. |
| B1 | P1 | La feuille Data safety classait les SMS et les appels comme collectés par le développeur. | Version corrigée dans `data-safety-v1.md`. Aucun envoi V1 relu vers un serveur Sentinel. | Affirmation précédente retirée. Risque résiduel : lecture large possible du mot « collect ». |
| B2 | P1 | Auth0, LiveKit, WireGuard et Wear restent des dépendances Gradle. Leurs composants connus sont retirés du manifeste source V1. | `app/build.gradle` dépendances. `src/v1Commercial/AndroidManifest.xml` `tools:node="remove"`. | Absence à l’exécution NON DÉMONTRÉE. |
| B3 | P1 | Le statut non-trader de novembre n’a pas été revu dans la Console et n’est pas une autorisation permanente. | Déclaration du développeur. Page Play du 18 novembre 2026. | NON VÉRIFIÉ en Console. |
| B4 | P1 | Identité incomplète pour publier la politique : pays, adresse, téléphone, DPO, base juridique, URL publique. | Champs encore vides dans la politique. | À COMPLÉTER. L’e-mail déclaré est `sentinelquantumvanguardaipro@gmail.com`. La boîte n’a pas été testée. |
| B5 | P1 | Le titre Console annoncé (« Sentinel Quantum Pro ») et le libellé V1 (« Sentinel Phone Core ») ne sont pas alignés dans une fiche vérifiée. | `app/build.gradle` ligne 31 : libellé release `Sentinel Phone Core`. Le nom Console n’est pas dans le dépôt. | NON VÉRIFIÉ en Console. |
| C1 | P2 | `public/terms.html`, `public/cgv.html` et `public/pricing.html` décrivent encore des offres payantes. | Lecture des pages. Le graphe V1 ne les ouvre pas. | Ne pas les copier sur la fiche. |
| C2 | P2 | `WANGIRI_API_BASE_URL` reste compilé vers `sentinel-moteur-api.onrender.com`. | `app/build.gradle` ligne 24. L’appel de réputation relu est coupé par `COMMERCIAL_PHONE_CORE_V1`. | Pas de capture réseau. |

## 3. Preuves techniques

Version lue dans `native-android-app/app/build.gradle` : `applicationId` `com.sentinel.quantum`, `versionName` `1.0.9`, `versionCode` 10, `minSdk` 24, `targetSdk` 36, `compileSdk` 37. Le debug commercial ajoute `.v1validation` et `-v1-validation`. Ce paquet debug ne doit pas être déposé sur l’application Play.

Permissions du manifeste principal conservées par l’overlay V1, et exigées par `audit/v1-commercial/validation/check_merged_manifest.py` : `CALL_PHONE`, `READ_CALL_LOG`, `READ_PHONE_STATE`, `READ_CONTACTS`, `POST_NOTIFICATIONS`, `USE_FULL_SCREEN_INTENT`, `SEND_SMS`, `RECEIVE_SMS`, `READ_SMS`, `RECEIVE_MMS`, `RECEIVE_WAP_PUSH`, plus `INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`. L’overlay retire micro, caméra, position, Wi-Fi, Bluetooth et la projection média. `WRITE_SMS`, `WRITE_CALL_LOG` et `PROCESS_OUTGOING_CALLS` ne sont pas déclarées.

Rôles dans le source : `ROLE_DIALER`, `ROLE_SMS`, `ROLE_CALL_SCREENING` à partir d’Android 10. Avant Android 10, `CallScreeningActivationPolicy.kt` lignes 12-21 utilise l’application Téléphone par défaut. Le script de manifeste attend les composants exportés protégés : filtrage `BIND_SCREENING_SERVICE`, appel `BIND_INCALL_SERVICE`, SMS `BROADCAST_SMS`, MMS `BROADCAST_WAP_PUSH`, réponse `SEND_RESPOND_VIA_MESSAGE`, appel manqué `MODIFY_PHONE_STATE`. Ce script n’a pas été exécuté ici sur un XML fusionné.

Paiement et publicité : aucune dépendance Billing ni `play-services-ads` dans les Gradle du module. Cela ne prouve pas l’absence d’une dépendance transitive. Le graphe n’a pas été résolu dans cette session.

Compte : le graphe V1 n’ouvre pas `SettingsScreen`. Les activités Auth0 sont retirées du manifeste V1. Les classes Auth0 restent dans `app/src/main/java/com/sentinel/quantum/auth/`.

Catalogues :

- Démarrage : `SentinelApplication.onCreate` ne lance pas de réseau. À la reprise d’activité, `launchForegroundMaintenance` appelle `PublishedNumberCatalogScheduler.sync` avec l’interrupteur. S’il est faux, le travail est annulé (`PublishedNumberCatalogScheduler.kt` lignes 21-24).
- Arrière-plan : seulement si l’interrupteur est vrai. Le worker revérifie l’interrupteur (`PublishedNumberCatalogWorker.kt` lignes 31-32) puis GET. Période 24 h plus un passage immédiat.
- Refus ou coupure : couper l’interrupteur annule les travaux en file. Un GET déjà parti n’est pas annulé par ce code.
- Recherche manuelle : `SentinelDialerActivity.lookup` ne lit pas l’interrupteur.
- Fiche d’appel : `CallerIdActivity` non plus. Ce n’est pas un geste « rechercher ».
- Données dans la requête : `downloadText()` fait un GET de l’URL fixe, en-tête `Accept: application/json`, sans numéro. Le numéro est comparé après, sur l’appareil (`ArcepDirectoryClient.kt` lignes 62-70 et 99-108). L’adresse IP n’est pas lue par le code. Elle est visible par l’hôte. Pas de capture.

Réputation distante : `CallerIdActivity.kt` lignes 116-117 force `enrichmentEnabled` à faux en V1. `CallRuleSyncConfig.SYNC_ENABLED` est `false`.

Sauvegarde : `allowBackup=false` et `data_extraction_rules.xml` excluent le domaine. Non revérifié sur le manifeste fusionné.

## 4. Références

Distinction : **règle Play**, **texte juridique**, **recommandation**, **hypothèse**.

| Sujet | Nature | Source lue le 18 novembre 2026 |
| --- | --- | --- |
| Une personne physique peut publier une application gratuite | Règle Play. Un compte personnel peut publier. Un compte organisation est exigé pour le VPN, la finance, la santé ou le gouvernement. Le source V1 retire le service VPN. Cela ne tranche pas le droit français du commerce. | [Type de compte](https://support.google.com/googleplay/android-developer/answer/13634885) · [Informations du compte](https://support.google.com/googleplay/android-developer/answer/13628312) |
| Trader / non-trader | Règle Play, à la charge juridique de la personne. Le statut doit rester exact. Un faux non-trader peut mener au retrait. Une application gratuite sans achat ni publicité et sans structure professionnelle est un indice, pas une conclusion. Échéance de déclaration lue : 1er février 2027, déclaration demandée dès novembre 2026. | [Trader status](https://support.google.com/googleplay/android-developer/answer/17594752) · [Annonce du 29 octobre 2026](https://support.google.com/googleplay/android-developer/answer/18100766) |
| France et Guadeloupe | Hypothèse non relue sur EUR-Lex dans cette session : la Guadeloupe est une région ultrapériphérique française, le RGPD y est applicable comme dans l’Union. Les mentions légales exactes d’une personne non professionnelle n’ont pas été relues sur Légifrance. | Règlement (UE) 2016/679 : https://eur-lex.europa.eu/legal-content/FR/TXT/?uri=CELEX:32016R0679 — page non retéléchargée aujourd’hui |
| Confidentialité Play | Règle Play. Politique dans l’application et dans la Console, identité, contact, données, sécurité, conservation, suppression. | [User Data](https://support.google.com/googleplay/android-developer/answer/10144311) |
| SMS, journal, téléphone par défaut | Règle Play. Le gestionnaire par défaut doit détenir le rôle avant la demande de permission, et cesser l’usage au retrait du rôle. | [SMS et Call Log](https://support.google.com/googleplay/android-developer/answer/10208820) · [Permissions sensibles](https://support.google.com/googleplay/android-developer/answer/9888170) |
| Data safety, collecté et partagé | Règle Play. Collecter = transmettre hors de l’appareil, y compris par un SDK. Le traitement seulement local n’est pas une collecte. Partager = transfert à un tiers, avec exceptions dont l’action choisie par la personne. | [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469) |
| Test fermé | Règle Play pour un compte personnel créé après le 13 novembre 2023 : 12 testeurs inscrits 14 jours consécutifs. Le développeur dit que le test fermé est requis pour son compte. La date de création n’a pas été lue dans la Console. | [Tests des comptes personnels](https://support.google.com/googleplay/android-developer/answer/14151465) |
| targetSdk 36 | Règle Play en vigueur au 18 novembre 2026 : les nouvelles applications et mises à jour doivent cibler Android 16 / API 36 depuis le 31 août 2026. L’extension au 1er novembre 2026 est passée. Le source annonce 36. L’AAB courant n’a pas été redumpé. | [Target API](https://support.google.com/googleplay/android-developer/answer/11926878) |
| Annonces et finance | Règle Play. Réponses proposées : pas d’annonces, pas de fonction financière. Non soumises. | [Revue](https://support.google.com/googleplay/android-developer/answer/9859455) · [Financial features](https://support.google.com/googleplay/android-developer/answer/13849271) |

Recommandation, pas une obligation démontrée ici : faire relire le statut non-trader par la personne, ou par un conseil, avant une distribution publique régulière. Le mot « commercial » dans le nom du profil Gradle n’est pas un statut de trader.

## 5. Écarts documentation / code

- La documentation parlait d’un téléchargement à la recherche manuelle. Elle omettait le GET automatique de `CallerIdActivity` après le filtrage.
- Elle classait les communications opérateur comme une collecte du développeur. Le code ne le montre pas.
- Elle présentait l’absence de SDK publicitaire comme un fait de binaire. Seuls les Gradle directs ont été lus.
- Les PASS de `TEST_EVIDENCE.md` ne sont pas des PASS de cette session ni du commit de conformité.
- « Sentinel Quantum Pro » et la vérification d’identité ne sont pas des faits du dépôt.

## 6. Correctifs minimaux

1. Publier la politique V1 à une URL HTTPS, puis changer les lignes 134-137 de `CommercialPhoneCoreScreens.kt`. Tester le bouton.
2. Empêcher `ArcepDirectoryClient.loadDirectory` et `RtrDirectoryClient` de télécharger tant que l’interrupteur n’est pas vrai, y compris depuis `CallerIdActivity` et `SentinelDialerActivity`. Si la copie manque, afficher un état local sans GET.
3. Ne pas soumettre la Data safety avec « SMS collectés » tant que le seul motif est l’opérateur.
4. Produire l’AAB signé `com.sentinel.quantum`, versionCode 10, lancer `check_merged_manifest.py`, puis un relevé réseau du premier appel français et du premier lancement.
5. Ne pas créer de second compte Play. Ne pas déposer le paquet `.v1validation`.

Le code n’a pas été modifié par cet audit.

## 7. Tests indispensables

| Test | Résultat de cette session |
| --- | --- |
| Unitaires JVM du commit `25c4e2c9` | PASS enregistré dans le dépôt. Non rejoué. |
| Instrumentation sur téléphone | NON EXÉCUTÉ. Le dépôt dit BLOCKED, aucun appareil. |
| Lint du même candidat | PASS enregistré, 0 erreur, 246 avertissements. Non rejoué. |
| Sécurité / OSV du graphe releaseUnsigned de ce candidat | PASS enregistré sur 189 coordonnées. Non rejoué. Dependabot `main` : NON VÉRIFIÉ, HTTP 403 dans le dépôt. |
| Révocation de rôle SMS, téléphone, filtrage | NON EXÉCUTÉ sur appareil. Des contrats source existent. |
| Appel entrant, appel sortant, SMS, MMS | NON EXÉCUTÉ. |
| GET catalogue à l’ouverture de la fiche d’appel, interrupteur coupé | NON EXÉCUTÉ. Le source le prévoit. |
| Bouton politique | NON EXÉCUTÉ. Le source ouvre la vitrine. |
| AAB signé et Play Console | IMPOSSIBLE ICI. Signature BLOCKED dans le dépôt. Console non ouverte. |
| S24+ | NON EXÉCUTÉ. |

## 8. Informations juridiques encore ouvertes

Pays, adresse légale, téléphone du compte, orthographe exacte dans le profil Payments, délégué à la protection des données, base juridique, autorité de réclamation, conservation des journaux de `pages.dev`, surveillance réelle de `sentinelquantumvanguardaipro@gmail.com`, vérification d’identité, date de création du compte, nom exact affiché dans la Console. Aucun SIREN ni SIRET n’existe dans ces documents, et aucun ne doit être inventé.

## 9. Ordre jusqu’à la publication

1. Corriger A1 et A2 dans le code, avec tests.
2. Signer l’AAB `com.sentinel.quantum` et contrôler le manifeste fusionné.
3. Recette S24+ : rôles, retrait des rôles, appel, SMS, MMS, fiche d’appel sans GET si l’interrupteur est coupé.
4. Compléter les champs juridiques encore vides. Relire le statut non-trader.
5. Aligner le titre Play et le libellé de l’application.
6. Test fermé sur le compte existant. Pas de production tant que les points 1 à 5 et le critère des 12 testeurs, s’il s’applique, ne sont pas constatés.
7. Seulement ensuite, soumettre Data safety, permissions et fiche.

## 10. Affirmations précédentes corrigées

| Affirmation | Jugement | Pourquoi |
| --- | --- | --- |
| Les SMS et les appels sont collectés par le développeur parce que l’opérateur les transporte. | Excessive, retirée. | Aucune requête V1 relue n’envoie ces contenus au développeur. La définition Play excepte le traitement local. |
| Le téléchargement hors interrupteur se limite à la recherche manuelle. | Incomplète. | `CallerIdActivity` télécharge sans ce geste. |
| L’absence de publicité et de paiement est prouvée dans le binaire. | Partielle. | Vrai pour les dépendances directes lues. Faux comme preuve d’AAB. |
| Les tests V1 sont PASS pour la publication. | Fausse si on l’applique à l’arbre courant. | Les PASS cités visent `25c4e2c9`. Le S24+ et la signature restent ouverts dans le même document. |
| Le nom « Sentinel Quantum Pro » est établi. | Non démontré ici. | Absent du dépôt. Non relu dans la Console. |
| Le statut non-trader autorise le lancement. | Fausse comme autorisation. | La page Play exige que le statut reste exact. Il n’a pas été revu. |
| La conformité Play est acquise. | Fausse. | A1, A2 et A4 sont ouverts. |
