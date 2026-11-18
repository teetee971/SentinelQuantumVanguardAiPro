# Acceptation physique Galaxy S24+ · Phone Core V1

**BLOCKED sur cette exécution.** Aucun S24+ n’est accessible dans le workspace cloud. Ce document décrit les scénarios à constater sur l’appareil. Chaque résultat physique ci-dessous commence à **NON EXÉCUTÉ**. L’installation d’une version C11 ou les tests JVM ne remplissent aucun scénario V1.

## Artefact et état initial

1. Utiliser un S24+, deux lignes/appareils de recette et des données fictives. L’acceptation double-SIM exige deux lignes actives sur le S24+. Relever modèle exact, version One UI/Android, API, correctif, ABI, opérateurs anonymisés, taille de page, état radio et utilisateur Android. Réserver la session au propriétaire des lignes. S’abstenir d’appeler les urgences.
2. Identifier l’APK debug **Sentinel V1 - Recette** `com.sentinel.quantum.v1validation`, version `1.0.9-v1-validation`, profil ARM64. L’APK de test éventuel doit cibler ce paquet et être signé avec le même certificat. Inspecter les manifests, les empreintes et les signatures avant toute installation. Garder les APK C9/C11 et les données de leur paquet hors du parcours.
3. Relever les applications titulaires de chaque rôle avant installation. Noter l’état initial des permissions et canaux, de la SIM de départ et du VPN. Effectuer tous les changements de rôle et de permission depuis les réglages Android avec le propriétaire. Les commandes ADB servent ici à observer et installer la recette, sans pré-attribuer des droits.
4. Si le paquet de recette est déjà présent, faire correspondre son certificat et son `versionCode`. En cas de conflit ou de doute sur ses données, arrêter l’installation et préserver l’existant. Recueillir l’activité de démarrage, les chemins de paquets et le SHA-256 du fichier exact.

Depuis le dépôt, construire la recette avec `bash gradlew -PsentinelCommercialV1=true :app:assembleDebug :app:assembleDebugAndroidTest` sous `native-android-app`. Pour la paire livrée, exécuter le prévol depuis `artifacts/` dans l’archive. Utiliser un shell Bash équipé d’ADB, de `aapt2`, d’`apksigner` et de `sha256sum`. En cas d’indisponibilité de ces commandes dans Termux, exécuter le prévol depuis un hôte ADB muni des Platform-Tools et Build-Tools Android. Choisir le numéro de série dans `adb devices -l` et définir `S24_SERIAL` et `V1_PREFLIGHT` (dossier neuf et privé pour les preuves). [Empreintes et versions de la paire livrée](RELEASE_READINESS.md)

```bash
set -euo pipefail
: "${S24_SERIAL:?Sélectionner le S24+}" "${V1_PREFLIGHT:?Donner un dossier de preuve neuf}"
pkg=com.sentinel.quantum.v1validation
APK=sentinel-v1-recette-arm64-debug.apk
TEST_APK=sentinel-v1-recette-instrumentation.apk
mkdir -m 700 -- "$V1_PREFLIGHT"
sha256sum -c --strict SHA256SUMS
aapt2 dump badging "$APK" > "$V1_PREFLIGHT/app.badging"
aapt2 dump badging "$TEST_APK" > "$V1_PREFLIGHT/test.badging"
aapt2 dump xmltree --file AndroidManifest.xml "$TEST_APK" > "$V1_PREFLIGHT/test.manifest"
grep -Fq "package: name='$pkg' " "$V1_PREFLIGHT/app.badging"
grep -Fq "package: name='$pkg.test' " "$V1_PREFLIGHT/test.badging"
grep -Fq "versionCode='10' versionName='1.0.9-v1-validation'" "$V1_PREFLIGHT/app.badging"
grep -Fq "native-code: 'arm64-v8a'" "$V1_PREFLIGHT/app.badging"
grep -Eq 'targetPackage.*="com[.]sentinel[.]quantum[.]v1validation"' "$V1_PREFLIGHT/test.manifest"
grep -Fq 'androidx.test.runner.AndroidJUnitRunner' "$V1_PREFLIGHT/test.manifest"

cert_sha() {
  apksigner verify --print-certs "$1" | awk '/^Signer #1 certificate SHA-256 digest:/ { print $NF }'
}
cert=$(cert_sha "$APK")
test_cert=$(cert_sha "$TEST_APK")
[[ $cert == 0d5e03ce9117817a0cfe55f2db1368f949218508abe34edf31a013cddde67275 && $test_cert == "$cert" ]]
adb_v1() { adb -s "$S24_SERIAL" "$@"; }
[[ $(adb_v1 get-state) == device ]]
model=$(adb_v1 shell getprop ro.product.model | tr -d '\r')
[[ $model == SM-S926* ]]
adb_v1 shell getprop ro.build.version.sdk
adb_v1 shell getprop ro.build.version.security_patch
adb_v1 shell getconf PAGE_SIZE
android_user=$(adb_v1 shell am get-current-user | tr -d '\r')
[[ $android_user =~ ^[0-9]+$ ]]
for role in DIALER SMS CALL_SCREENING; do
  adb_v1 shell cmd role get-role-holders --user "$android_user" "android.app.role.$role" \
    > "$V1_PREFLIGHT/$role.before.txt"
done
adb_v1 shell settings get secure always_on_vpn_app > "$V1_PREFLIGHT/vpn.before.txt"

for id in "$pkg" "$pkg.test"; do
  installed=$(adb_v1 shell pm list packages --user "$android_user" "$id" | tr -d '\r')
  if grep -Fxq "package:$id" <<< "$installed"; then
    remote_apk=$(adb_v1 shell pm path --user "$android_user" "$id" | tr -d '\r' \
      | sed -n 's/^package:\(.*\/base[.]apk\)$/\1/p')
    [[ $remote_apk == /*/base.apk && $remote_apk != *$'\n'* ]]
    adb_v1 pull "$remote_apk" "$V1_PREFLIGHT/$id.installed.apk"
    installed_cert=$(cert_sha "$V1_PREFLIGHT/$id.installed.apk")
    [[ $installed_cert == "$cert" ]]
    if [[ $id == "$pkg" ]]; then
      aapt2 dump badging "$V1_PREFLIGHT/$id.installed.apk" > "$V1_PREFLIGHT/$id.installed.badging"
      installed_code=$(sed -n "s/^package:.*versionCode='\([0-9][0-9]*\)'.*/\1/p" "$V1_PREFLIGHT/$id.installed.badging")
      [[ $installed_code =~ ^[0-9]+$ && $installed_code -le 10 ]]
    fi
  fi
done
printf 'Prévol réussi pour %s / utilisateur %s / certificat %s\n' "$model" "$android_user" "$cert"
```

Après le prévol réussi, dans le même shell et sur ce même utilisateur Android, installer la recette. Examiner l’erreur de signature ou de version en cas de refus avant toute action supplémentaire.

```bash
adb_v1 install --user "$android_user" -r "$APK"
adb_v1 install --user "$android_user" -r -t "$TEST_APK"
adb_v1 shell pm path --user "$android_user" "$pkg"
adb_v1 shell pm path --user "$android_user" "$pkg.test"
adb_v1 shell am instrument -w -r --user "$android_user" \
  -e class com.sentinel.quantum.ui.CommercialPhoneCoreNavigationInstrumentationTest \
  "$pkg.test/androidx.test.runner.AndroidJUnitRunner" | tee "$V1_PREFLIGHT/v1-navigation.instrumentation.log"
```

Si aucun test instrumenté propre au périmètre n’est disponible ou signé, consigner ce statut et poursuivre la recette manuelle avec l’APK applicatif autorisé. Une campagne instrumentée doit nommer ses classes, établir leur exécution, et attacher résultat, stdout et logcat filtré. Les tests des modules exclus ne sont pas des oracles de la recette V1.

## Matrice physique

| ID | Préparation et action | Oracle de réussite | Statut initial |
| --- | --- | --- | --- |
| A01 · installation | Démarrage frais, puis retour arrière et réouverture de l’app | Paquet/label attendus, aucun crash/ANR, navigation disponible et parcours hors périmètre absents. À l’installation, aucune permission dangereuse accordée par l’essai. | NON EXÉCUTÉ |
| A02 · refus des rôles | Au premier lancement refuser Téléphone, SMS et filtrage séparément | L’état exact est visible. Aucun accès à l’historique ou à la messagerie protégée. Le refus d’un rôle n’accorde aucun autre rôle. | NON EXÉCUTÉ |
| A03 · attribution et permissions | Attribuer un rôle à la fois puis refuser ou accepter l’autorisation associée | L’app réévalue les titulaires, leurs permissions et leur disponibilité au retour des réglages. Aucune lecture persistante sur rôle ou permission manquante. | NON EXÉCUTÉ |
| A04 · appel entrant | Recevoir un appel de test, répondre, vérifier l’état actif, raccrocher | Communication audible sur les deux postes, identité/source réellement observable et fermeture nette de l’interface. | NON EXÉCUTÉ |
| A05 · appel sortant | Composer sur chaque SIM et répondre sur la seconde ligne | SIM explicitement choisie, appel connecté confirmé par les deux postes, historique direction/SIM cohérent. | NON EXÉCUTÉ |
| A06 · notification et raccrochage | Recevoir un appel verrouillé puis déverrouillé. Pendant l’appel actif utiliser la commande de fin dans la notification. Rejouer avec plein écran refusé. | Notification cohérente avec les droits. La commande agit sur l’appel actif exact, une seule fois. Une ancienne action de notification n’affecte pas un appel remplacé. | NON EXÉCUTÉ |
| A07 · filtrage | Activer puis retirer `ROLE_CALL_SCREENING`. Établir séparément appel autorisé et décision locale sur un numéro de test contrôlé | Décision observée dans Android/diagnostic avec rôle. Arrêt de la capacité et état visible au retrait. | NON EXÉCUTÉ |
| A08 · contacts et journal d’appels | Ouvrir un contact fictif d’un compte de recette dans « Modifier dans Contacts ». Annuler, puis rouvrir et sauvegarder un changement. Observer l’historique, puis retirer permissions et rôle Téléphone. | Éditeur système explicite, annulation préservant la fiche et rafraîchissement au retour après sauvegarde. Synchronisation limitée au compte géré dans Android. Refus sans permission ou éditeur. Les anciennes données disparaissent du rendu après retrait, y compris sur résultat différé. | NON EXÉCUTÉ |
| A09 · SMS | Recevoir, émettre court et multiparties, observer callbacks sur chacune des SIM | Réception réelle, unique écriture logique, chaque partie envoyée. La confirmation de livraison n’est attribuée qu’aux callbacks réels de l’opérateur. | NON EXÉCUTÉ |
| A10 · MMS | Envoyer un MMS de recette puis recevoir et prévisualiser un MMS pris en charge. Répéter erreur et SIM indisponible | Callback transport de succès unique en émission. Aperçu sûr en réception, traitement d’erreur sans faux succès. | NON EXÉCUTÉ |
| A11 · retrait SMS/MMS | Avec un opérateur de test et une fenêtre contrôlable, retirer le rôle pendant un chemin en attente. Reprendre avec profil sain | Les effets différés réévaluent le rôle avant usage et ne publient aucune nouvelle donnée protégée devenue non autorisée. Un effet déjà engagé par Android/opérateur est relevé distinctement. Ni doublon ni ancien statut attribué au nouveau rôle. | NON EXÉCUTÉ |
| A12 · reprise | Basculer arrière-plan, fermer puis relancer l’app et, dans une fenêtre d’essai séparée, redémarrer le téléphone | Régénération des faits Android, historique cohérent, absence de répétition de soumission ou de restauration d’une autorisation retirée. | NON EXÉCUTÉ |
| A13 · refus notifications | Désactiver la permission, un canal puis le plein écran, et rejouer un appel et un SMS | Aucun faux signal de publication. État dégradé explicite et fonction de base conforme au rôle conservé. | NON EXÉCUTÉ |
| A14 · retour aux titulaires initiaux | Faire reprendre individuellement les rôles à leurs titulaires initialement relevés. Comparer l’état après fermeture/réouverture de Sentinel | Les applications Téléphone et SMS restaurées prennent en charge les scénarios de test. V1 affiche la perte des rôles et bloque les accès. Les données et journaux restent conservés. | NON EXÉCUTÉ |

Ces scénarios complètent les 14 identifiants de preuve canoniques Phone Core v6. Reporter chaque identifiant, même si plusieurs sont observés lors d’un seul scénario. En cas d’absence de rapport opérateur ou d’impossibilité de créer la fenêtre de course, noter **BLOCKED** et préciser le prérequis manquant. Les succès locaux doivent être corroborés par l’appareil et le poste correspondant. [Protocole Phone Core v6 et identifiants](../../docs/PHONE_CORE_PHYSICAL_VALIDATION.md)

## Retour arrière et preuve remise

| Symptôme | Action opérateur | Validation |
| --- | --- | --- |
| Échec d’installation, signature ou paquet inattendu | Arrêter. Conserver la sortie et comparer l’empreinte au manifeste de preuve | Les APK et données existants restent présents. |
| Problème téléphone ou remontée d’appel | Réattribuer Téléphone et filtrage aux titulaires relevés avant l’essai, dans Android | Appel de test réussi vers et depuis les applications système. |
| Interruption SMS/MMS | Réattribuer SMS au titulaire initial et vérifier la SIM active, fermer V1 | Réception/émission de recette avec le titulaire initial. Le stockage des messages reste intact. |
| Régression d’une mise à jour de canal signé | Suspendre le rollout. Préparer un correctif signé sous la même identité et à `versionCode` supérieur | Repasser le smoke test, la reprise des données et les rôles avec le binaire du canal. |

Préserver toutes les installations, preuves et journaux. Le retour arrière repose sur la réattribution des rôles et un correctif de canal à version supérieure. La suppression d’applications, la remise à zéro des données et le downgrade ADB sont hors protocole.

Pour chaque ligne conserver horodatage, code/empreinte/certificat, état des rôles avant/après, résultat exact, preuve filtrée et ticket de défaut. Expurger numéros, contacts, corps SMS/MMS, identifiants de SIM, identifiants ADB et secrets avant tout partage. La promotion `PHYSICAL_DEVICE` requiert l’observation signée autorisée et liée à l’artefact exact. Un candidat Play est réessayé sur le binaire livré par son propre canal. [Conformité et signature](ANDROID_DISTRIBUTION_COMPLIANCE.md)
