# Google Play — dossier de dernière validation

## Décision de diffusion

| Canal | Prêt maintenant | Blocage concret |
|---|---|---|
| Test interne Play | **NON** | AAB non signé ; propriétaire : clé d’upload/Play App Signing, code de version libre, fiche d’application et déclarations de permissions/Data Safety à confirmer. L’APK debug peut être testé par USB immédiatement ; cela n’est pas un test interne Play. |
| Test fermé Play | **NON** | Mêmes prérequis de compte/signature/déclarations ; campagne S24+ non exécutée ; audience/pays/testeurs et conditions d’accès au canal du compte à vérifier par le propriétaire. |
| Production | **NON** | Aucun résultat physique, prérequis Play ci-dessus non soldés, aucune autorisation de soumission en production donnée. |

Aucun upload, dépense, service payant ou publication n’a été effectué. Les restrictions de permissions sensibles et les déclarations restent à traiter aussi pour un canal de test ; ne pas considérer le test interne comme une exemption générale.

## Configuration vérifiée

`applicationId=com.sentinel.quantum`, versionCode=10, versionName=1.0.9, minSdk=24, targetSdk=36, compileSdk=37. JDK17/Gradle9.8/AGP9.4.1/Kotlin2.4.20. Le code 10 doit être strictement supérieur au dernier code déjà utilisé sur **tous** les canaux du compte ; le compte Play n’est pas connecté ici. S’il est pris, modifier le code dans Gradle, ajuster son test de contrat et reconstruire les artefacts avant upload.

Le bundle `releaseUnsigned` est minifié avec R8, non débogable et **non signé**. `bundletool validate` et l’alignement 16 Kio sont contrôlés automatiquement dans les journaux de cette livraison ; ces contrôles ne valent pas approbation Play.

## Signature compatible Play App Signing

Utiliser la clé d’upload déjà autorisée par le propriétaire et laisser Play gérer sa clé de signature d’application. Ne jamais copier une clé privée dans Git ou dans le dossier de livraison. Aucun nouveau keystore n’est créé par les scripts.

Option privilégiée : injecter les quatre variables depuis un gestionnaire de secrets local/CI autorisé (sans en imprimer les valeurs), puis reconstruire :

```bash
cd native-android-app
# KEYSTORE_FILE, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD doivent déjà être définies.
bash gradlew :app:bundleRelease --no-daemon --console=plain
jarsigner -verify -verbose -certs app/build/outputs/bundle/release/app-release.aab
```

La configuration refuse le build `release` sans les quatre variables. Elle ne remplace jamais la clé de release par la clé debug. `releaseUnsigned` efface explicitement la signature héritée.

L’AAB livré est également signable hors Gradle : copier le bundle sous un autre nom, puis `jarsigner` avec la clé d’upload existante et des mots de passe lus depuis l’environnement, jamais dans l’historique de commande :

```bash
cp Sentinel-PhoneCore-V1-release-unsigned.aab Sentinel-PhoneCore-V1-release-signed.aab
jarsigner -keystore "$KEYSTORE_FILE" -storepass:env KEYSTORE_PASSWORD -keypass:env KEY_PASSWORD Sentinel-PhoneCore-V1-release-signed.aab "$KEY_ALIAS"
jarsigner -verify -verbose -certs Sentinel-PhoneCore-V1-release-signed.aab
# Puis bundletool validate sur ce nouveau fichier et nouvelle empreinte SHA-256.
```

Certificat d’upload à comparer à Play Console ; certificat auto-signé d’upload possible, les avertissements de chaîne ne remplacent pas la vérification d’identité de clé. Aucun AAB signé n’a été produit ici.

## Permissions et composants

Le manifeste source comporte 24 permissions effectives. Après retrait des déclarations caméra/projection inutilisées et de ScreenCaptureService, le manifeste fusionné attendu comporte **30** permissions ; le compte exact et tous les composants exportés sont produits par `logs/release-manifest.log` et doivent être lus comme preuve du binaire courant.

| Groupe | Décision pour cette livraison | Obligations ou réduction possible |
|---|---|---|
| CALL_PHONE, READ_PHONE_STATE | Conserver : appel opérateur et choix SIM | Consentement Android, refus/retrait, fonctionnement sans SIM à valider sur S24+ |
| READ_CALL_LOG | Conserver pour l’historique du dialer | Permission restreinte : titulaire ROLE_DIALER, usage principal, déclaration Play, arrêt d’accès à la perte du rôle |
| READ_CONTACTS | Optionnelle, lecture locale | Refus doit laisser le pavé utilisable ; finalité à expliquer ; ne pas déclarer absence de traitement des contacts sans distinguer local/distant |
| POST_NOTIFICATIONS, USE_FULL_SCREEN_INTENT, WAKE_LOCK | Conserver pour appels/écran | Déclaration plein écran applicable à l’application d’appel, contrôle utilisateur Android 14+, tests des canaux et révocations |
| SEND_SMS, READ_SMS, RECEIVE_SMS, RECEIVE_MMS, RECEIVE_WAP_PUSH | Conserver pour préserver le module SMS/MMS existant, hors certification V1 | Permission restreinte : éligibilité gestionnaire SMS et formulaire Play à confirmer. Si exclu de la première soumission, il faut une variante cohérente retirant permissions **et** composants/parcours SMS, pas une suppression de permission isolée. Pas de déclaration de SMS opérationnel sans preuve. |
| CAMERA, FOREGROUND_SERVICE_MEDIA_PROJECTION | **RETIRÉES**, aucune utilisation runtime trouvée dans les parcours actuels | Service LiveKit ScreenCaptureService retiré ; futur partage d’écran/caméra hors V1 |
| RECORD_AUDIO | Conservée pour l’aperçu Voice Studio audio existant | À retirer d’une future variante strictement téléphone si ce module est exclu ; ne pas casser l’aperçu préservé ici ; pas d’enregistrement d’appel SIM |
| Wi-Fi, localisation, Bluetooth | Conservées pour les modules de découverte existants | Jamais obligatoires pour appeler ; demandes contextuelles/neverForLocation/maxSdk selon manifeste ; à retirer seulement dans une variante qui exclut ces modules |
| INTERNET, ACCESS_NETWORK_STATE, MODIFY_AUDIO_SETTINGS | Conservées, certaines héritées | API et audio existants ; les appels réseau peuvent révéler IP/métadonnées au fournisseur |
| FOREGROUND_SERVICE, RECEIVE_BOOT_COMPLETED, biométrie et permission privée de receiver | Héritages de dépendances présents | À justifier par l’usage effectif des modules ; contrôler types FGS réellement utilisés dans le manifeste fusionné et déclarations Play |

Entrées propres exportées : MainActivity/partage, SentinelDialerActivity/ACTION_DIAL, SmsComposeActivity/SENDTO ; les services et receivers téléphoniques/SMS sont protégés par des permissions système de binding/broadcast. Les activités d’activation/InCall/diagnostic sont privées. Auth0, WorkManager et WireGuard apportent aussi leurs composants ; la liste exportée exacte est dans le journal, pas supposée à partir du seul manifeste source. FileProvider est privé avec accès par URI accordée.

## Données et confidentialité : faits à convertir en formulaire

La politique versionnée est `PRIVACY_POLICY.md`. Elle doit être publiée à une URL HTTPS accessible, comporter le responsable et un contact réel, la date et les modalités de suppression applicables. La copie versionnée ne prouve pas l’accessibilité de l’URL affichée dans Play. Aucun texte de politique nouveau n’est publié ici.

| Parcours présent dans les sources | Fait vérifié | À confirmer par le propriétaire avant Data Safety |
|---|---|---|
| Contacts, historique, chronologie d’appels | Providers permissionnés et stores locaux ; signaux de certification bornés sans numéro/contact brut | Conservation réelle, suppression/export, toute télémétrie du produit final ; ne pas assimiler traitement local à transmission distante |
| Réputation distante optionnelle | CallerReputationClient transmet `caller_number` normalisé en JSON au backend selon consentement/pare-feu de confidentialité | Base légale, catégories Play (numéros/autres infos personnelles), finalité, conservation, sous-traitants, suppression, partage/traitement éphémère réel. **Ne pas cocher « aucune donnée collectée » globalement.** |
| Annuaire de numérotation ARCEP | Téléchargement d’un fichier public HTTPS puis correspondance locale | Métadonnées réseau/IP et logs CDN ; le code de cette requête ne joint pas le numéro recherché |
| Auth0 | Bibliothèque et parcours de compte présents | Données de compte effectivement collectées, URL de suppression de compte si création proposée, stockage/conservation et rôle du fournisseur |
| SMS/MMS | Module local et transports opérateur Android présents ; aperçu sûr/quarantaine | Données traitées/rétention, fonction réellement proposée au canal initial ; aucune qualification matérielle ici |
| Voice Studio | Aperçu audio local ; clients audio/LiveKit présents ; pas de capture d’appel SIM | Si une session distante est activée : audio transmis, fournisseur, rétention, suppression et information explicite. Cette livraison ne prouve pas un service audio de production. |
| VPN/BLE/OSINT/Security Lab | Modules conservés | Réglages activés, serveurs/opérateurs/processeurs, données effectivement envoyées ; aucune prétention de conformité sans vérifier le déploiement réel |

Aucune donnée de compte Play, clé ou identité juridique n’est inventée. Le propriétaire doit valider le questionnaire pour **l’ensemble de l’artefact**, qui conserve les modules existants, pas seulement pour le composeur.

## Périmètre et notes de version

V1 à qualifier : rôle Téléphone, composeur, choix SIM, appels entrants/sortants/réponse/rejet/raccrochage, contacts et historique avec consentement, notifications entrantes/en cours/manquées, DTMF, muet/audio, attente/reprise selon capacités et gestion ciblée du double appel. Conférence/fusion/permutation masquées. Pas de promesse d’interception/enregistrement audio SIM, de VPN/BLE/Voice Studio/Security Lab certifiés, de SMS/MMS qualifiés, ni de protection garantie.

Les notes françaises prêtes à copier sont dans `RELEASE_NOTES_FR.txt` (canal de qualification, pas annonce de validation physique).

## Dernières actions propriétaire / téléphone

1. Exécuter `PHONE_CORE_PHYSICAL_TESTS.md` sur le S24+ avec lignes connues, joindre preuves expurgées, corriger tout FAIL avant diffusion.
2. Confirmer l’éligibilité des permissions conservées ou choisir une variante future cohérente qui préserve le produit complet ; compléter Data Safety, politique, déclarations SMS/Call Log/plein écran/FGS, classification de contenu, public cible et accès à l’application.
3. Vérifier versionCode dans Play Console, clé d’upload et Play App Signing ; signer et revérifier le bundle.
4. Play Console → Tests → Test interne → Créer une version → charger l’AAB signé ; ajouter notes FR et testeurs contrôlés. Résoudre les erreurs de Console ; enregistrer l’état de revue. Aucune action Console n’est faite par ce dossier.
5. Après retour interne et qualification, créer un canal fermé avec groupe/pays convenus. Si le compte exige une durée/nombre de testeurs préalable à la production, lire les exigences affichées pour ce compte ; aucun seuil n’est supposé.
6. Ne pas promouvoir vers production sans autorisation explicite distincte du propriétaire.

Sources officielles : https://developer.android.com/develop/connectivity/telecom/dialer-app ; https://support.google.com/googleplay/android-developer/answer/10208820 ; https://support.google.com/googleplay/android-developer/answer/10787469 ; https://support.google.com/googleplay/android-developer/answer/9842756 ; https://support.google.com/googleplay/android-developer/answer/11926878 . Vérifier la politique en vigueur et les exigences spécifiques du compte au moment de soumettre ; aucune approbation par Google n’est présumée.
