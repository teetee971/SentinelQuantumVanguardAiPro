# Contacts — candidat17 de mitigation à qualifier

**Premier essai physique17 favorable : le propriétaire rapporte que le défilement ne bloque plus en bas de fiche.** Voir [OWNER_RUNTIME_V17.md](OWNER_RUNTIME_V17.md) pour la portée et les limites. NOT READY pour déclarer D.1 conforme ou le problème définitivement résolu dans tous les parcours. Le propriétaire confirmait que Fermer réagissait immédiatement pendant le gel16 ; cause non démontrée indépendamment.

## Modification et provenance

Source APK : `76911552583432a776ddcfda7a64bc161c0035d7`, branche `fix/d1-phone-messages-conformity-20261029`, PR brouillon1748. Commits de livraison ultérieurs documentaires. Liste exacte SOURCE_FILES.txt.

Dans le composant réellement utilisé D1ContactProfileFrame : `sheetGesturesEnabled=false`. Le panneau ne peut plus être déplacé par glissement ; contenu verticalScroll conservé, rebond du contenu déjà désactivé. Fermer/Retour non modifiés. Aucun changement Téléphone, SMS/MMS, contacts persistés, Room, clés ou connecteurs. VersionCode17/versionName1.0.9, package com.sentinel.quantum, min24/target36/compile37.

Hypothèse : interaction entre nestedScroll/draggable du panneau Material3 et défilement au bord. Le paramètre désactive les premiers, pas le défilement interne. Pas de preuve de reproduction cloud ni de correction physique. Voir DESIGN.md.

## Vérifications

- Kotlin, tests unitaires et APK debug/instrumentation/unsigned : BUILD SUCCESSFUL, 1m15s. Build post-commit sur mêmes sources : exit0, incrémental.
- App : **1440 réussis,0 échec,0 erreur,0 ignoré**,308XML. Wearable-contract :2/2 ; wearable-security :26/26, aucun échec/erreur/ignoré ; tâches inchangées pouvant être UP-TO-DATE.
- Node produit :69/69 ; politiques livraison :15/15, aucun échec/ignoré aux résultats finaux.
- Premier lancement Gradle arrêté avant compilation : projet `:wearable` ambigu ; relance avec `:wearable-contract:test` et `:wearable-security:test`. Premier contrôle Node produit :68/69, documentation encore versionCode16 ; documentation synchronisée17 puis69/69. Journaux initiaux et finaux conservés.
- Nouveau test `draggingProfileHeaderDoesNotMoveOrDismissSheet` écrit avant la modification : glissement tactile de l'en-tête, panneau toujours visible/position stable, fermeture tactile. Test de fermeture au bas sans remonter conservé. **Instrumentation NON EXÉCUTÉE**, aucun RED/GREEN tactile revendiqué. Aucun appareil ADB disponible. Ni ANR ni performance Samsung mesurés dans le cloud.
- Room non suivi préservé :1.json SHA256 `a7c5b4e1926c4441629734c0703ba6174e6bcf3796394b51302846bac0dae0c4`.

## APK et signature

- unsigned : `Sentinel-D1-v17-UNSIGNED-76911552.apk`, 101391451octets ; SHA256 `ae616eda1219bf97c80f2742c965142343746af6df3476d0d651a80baec47185`. Signature exit1, alignement4 et16KiB exit0, aapt exit0.
- cloud_debug : `Sentinel-D1-v17-CLOUD-debug-76911552.apk`, 101494481octets ; SHA256 `d1c6ed6424fcb7f0ae955d408d922abc8bdca20bc6916f476fa664e0687aec83`. Signature exit0, alignement4 et16KiB exit0, aapt exit0.
- instrumentation : `Sentinel-D1-v17-androidTest-76911552.apk`, 1245766octets ; SHA256 `a5ed59a8478ac4367db3cf0f6497060619272603b67104777cfdf5e43d34be87`. Signature exit0, alignement4 et16KiB exit0, aapt exit0.

[APK17 NON SIGNÉ](https://drive.google.com/file/d/1ihvAxU_IJmxwg5HK9VsirdetQC62Xg3y/view?usp=drivesdk). Signature exit1 attendue pour unsigned. Cloud debug/test signés dee0d0f14e3a211d5ce9adca0280acde443e2312857d33da2258bef0f1b632e1, incompatibles propriétaire. Clé historique68915958f2f8683afead51cc3a1e578d182df4b9d9b2505c48c261b1a17b3fe2 reste sur le téléphone ; aucune clé exportée et aucun17 signé propriétaire reçu. Métadonnées Drive taille/nom relues ; SHA du binaire local, pas de rehash distant intégral revendiqué.

Prochaine étape : téléchargement, hash unsigned exact, signature locale avec clé historique et contrôle de contenu/certificat/package/code17/alignement/SHA. Autorisation explicite requise avant mise à jour17. Aucune installation/désinstallation/effacement automatique. Aucun merge main ou workflow payant lancé.

## Essai ciblé S24+

Après mise à jour autorisée : ouvrir fiche longue ; descendre au bas, rester quelques secondes et répéter trois gestes ; remonter immédiatement ; refaire puis Fermer directement en bas ; tester Retour/réouverture/texte agrandi. Relever chaque résultat sans publier les coordonnées/photos réelles. Confirmer la version installée. Si gel persiste, conserver FAIL et rechercher une trace avant un nouveau changement.

Les26scénarios physiques restent distincts. Écarts visuels D.1 non intégralement comparés ; seul le défilement en bas de fiche bénéficie du retour favorable décrit ci-dessus, sans qualification complète des interactions et navigation17. Filtrage des appels traversants non résolu par preuve ; premier appel froid encore fail-open. Enrichissement social : associations manuelles assistées navigateur, aucune collecte API automatique. Voice : pas de changement ni nouvelle validation, limitations historiques maintenues.
