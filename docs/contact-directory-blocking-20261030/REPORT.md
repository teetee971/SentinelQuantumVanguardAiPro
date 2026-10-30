# Candidat18 — photos du répertoire et accès au blocage

**NOT READY pour déclarer conformité D.1 ou fiabilité du blocage physique.** Fonctionnalités implémentées, suites statiques/JVM et compilation réussies ; signature historique et qualification des nouveaux parcours18 restent nécessaires avant usage validé. Aucun APK18 installé automatiquement.

## Source et modifications réelles

Source APK `a8495a7d1b2de32cef19a42d49f340bea8bb434e`, base `261d178b`, branche `fix/d1-phone-messages-conformity-20261029`, PR brouillon1748. SOURCE_FILES.txt énumère les16fichiers du commit source, aucune suppression. Commits de livraison suivants documentaires. versionCode18/versionName1.0.9 ; package com.sentinel.quantum ; min24/target36/compile37.

- Répertoire réel de SentinelDialerActivity : nom/photo forment un en-tête cliquable ouvrant la même fiche que le bouton explicite, cible avatar64dp. D1ContactAvatar était déjà utilisé dans cette liste ; aucune photo distante ajoutée.
- LocalContactLookup lit PHOTO_URI en plus de PHOTO_THUMBNAIL_URI. ContactPhotoPolicy choisit la miniature locale valide, sinon la photo principale locale valide. Une URI présente mais illisible, une image trop grande pour la borne2MiB/8000px, une permission refusée ou une absence de photo gardent l'initiale ; pas de promesse de récupérer tous les portraits.
- Fiche réelle : NumberCallBlockButton sur chaque numéro frais lisible. Confirmation puis vérification sélection/contact/numéro/permission avec la garde du provider existante, hors thread UI.
- Conversation réellement ouverte dans SmsComposeActivity : même composant pour le destinataire sélectionné, uniquement si numéro admissible. Avant mutation : permission SMS, fil encore sélectionné, destinataire courant et message encore présent dans ce fil avec le numéro normalisé. Pas de repli vers un ancien destinataire, pas d'ajout automatique ni d'action SMS.
- Confirmation non sauvegardée, effacée à la pause/changement propriétaire ; lecture état/rôle à RESUMED. Toutes les nouvelles actions utilisent CallBlocklistStore existant. État exact via empreintes compatibles avec les représentations du moteur, sans prendre un préfixe pour une règle exacte. Une règle exacte déjà présente n'est pas réécrite ni prolongée.
- Texte distingue règle enregistrée et rôle Android. Le rôle ne prouve pas un rejet effectif ; pas de blocage SMS, collecte API, suppression ou modification de contact.

## Revue et tests de non-régression

Revue statique indépendante des nouveaux chemins : deux P2 identifiés avant livraison. Une lecture provider/Keystore non interruptible pouvait continuer après annulation ; plusieurs boutons pouvaient concurrencer le read-modify-write du store. Le chemin initial extrait a reproduit3échecs d'assertion sur4tests : cancellationDuringNonInterruptibleReadNeverCommitsLater, simultaneousButtonsPreserveBothRules, selectionLostDuringMembershipReadDoesNotCommit. Aucun échec radio reproduit.

ConfirmedCallBlockMutation sérialise les nouvelles actions par Mutex partagé, relit appartenance sous verrou et revérifie annulation/validation/état courant après lectures lentes, immédiatement avant ajout. Ces4tests passent dans la suite finale. Une seconde revue confirme traitement des deux P2 sans autre problème important introduit identifié. La sérialisation couvre ces nouveaux boutons ; autres auteurs historiques du store non modifiés. Une écriture synchrone déjà commencée ne peut pas être annulée/rétroactivement retirée ; aucune telle garantie annoncée.

Trois tests instrumentés de la vraie confirmation compilés : annuler sans mutation puis confirmer une fois, changement propriétaire annulant confirmation, règle présente distincte d'un rôle non attribué. **NON EXÉCUTÉS** : aucun appareil ADB. Pas de mesure tactile/Activity/permissions physique18 ni de conformité visuelle revendiquée. Repli URI et appartenance exacte disposent aussi de tests JVM.

## Résultats vérifiés

- Suite finale Kotlin/unités/APK debug/test/unsigned : exit0,1m26s ; post-commit APK exit0, incrémental, SHA unsigned identique.
- App : **1447 réussis,0 échec,0 erreur,0 ignoré**,310XML. Dont7nouveaux cas JVM :1photo,2appartenance,4coordination. Résultats XML ciblés et résumé conservés.
- Wearable-contract2/2 et wearable-security26/26,0échec/erreur/ignoré ; modules inchangés, tâches pouvant être UP-TO-DATE.
- Node produit69/69 et politiques livraison15/15,0échec/ignoré.
- Journaux initiaux conservés : API helper absente aux premières passes RED,3assertions réellement échouées sur4 au test de coordination initial. La première suite complète passait avant ajout de ces tests ; elle n'est pas présentée comme couverture des races corrigées. Les warnings de dépréciation sont conservés.
- Schéma Room non suivi préservé :1.json SHA256 `a7c5b4e1926c4441629734c0703ba6174e6bcf3796394b51302846bac0dae0c4`. Aucun merge main, secret exporté, recherche d'un vrai contact, workflow payant ni installation automatique.

## Artifacts

- unsigned : `Sentinel-D1-v18-UNSIGNED-a8495a7d.apk` ; 102359861octets ; SHA256 `b9ceba991086dc501816f78744b2bf405dbf3aa2856d22597a46525f929794ef` ; signature exit1, zipalign4/16KiB exit0 et aapt exit0.
- cloud_debug : `Sentinel-D1-v18-CLOUD-debug-a8495a7d.apk` ; 102479271octets ; SHA256 `1fa470cc474204d8cbd9a013b7b82efe83f7f8adc74710647974f8c4595971ef` ; signature exit0, zipalign4/16KiB exit0 et aapt exit0.
- instrumentation : `Sentinel-D1-v18-androidTest-a8495a7d.apk` ; 1249704octets ; SHA256 `27e16065df519a7a5c79f6e3b1a8ba79cb3c2f4b6340503779e8da2c5acf97fd` ; signature exit0, zipalign4/16KiB exit0 et aapt exit0.

[APK18 NON SIGNÉ — à signer localement](https://drive.google.com/file/d/1anf-VOuCs_74GywM4IxFr3vUQVetpEoD/view?usp=drivesdk). Exit1 apksigner unsigned attendu, pas une validation de signature. Cloud debug/test signés dee0d0f14e3a211d5ce9adca0280acde443e2312857d33da2258bef0f1b632e1, incompatibles propriétaire68915958f2f8683afead51cc3a1e578d182df4b9d9b2505c48c261b1a17b3fe2. Clé propriétaire reste dans Ubuntu/Termux du S24+ ; aucun APK18 signé propriétaire reçu. SHA local ; métadonnées Drive nom/taille relues, pas de rehash intégral distant revendiqué.

## Qualification restante et limites séparées

Avant mise à jour : hash unsigned exact, signature locale clé historique, certificat/package/code18/intégrité/alignement/SHA signé, puis autorisation explicite d'installation. Ne jamais désinstaller/effacer pour contourner un problème.

Essais18 : photo en liste et clic nom/photo ouvrent bon contact ; repli et initiale ; deux contacts alternés sans mélange ; fiche et conversation Annuler n'ajoutent rien ; confirmer un numéro de test connu puis constater règle dans écran gestion, pas d'envoi SMS/appel ; rôle non attribué reste explicite ; pause/changement sélection/recréation ne rejouent pas la confirmation. Ne pas bloquer un vrai correspondant nécessaire pour tester ; pas de suppression globale des règles. Tests d'appel réel ultérieurs avec seconde ligne connue et autorisée.

Visuel D.1 : comparaison complète non effectuée, avatar plus grand et nouveau bouton à vérifier sur S24+ et texte agrandi. Gel : mitigation17 conservée, absence de gel rapportée par propriétaire sur17 seulement, nouveau parcours18 à vérifier. Fonctionnalités : pas de débloquage ciblé ajouté ici, gestion historique conservée ; pas de blocage SMS ou système Android ; profils sociaux manuellement associés via navigateur sans collecte API automatique. Filtrage : moteur/service/règles/HMAC inchangés, premier appel froid encore fail-open, appels traversants antérieurs non expliqués par preuve. Voice : aucune modification ni nouvelle mesure, limitations historiques maintenues. Les26scénarios physiques restent distincts et non globalement validés.
