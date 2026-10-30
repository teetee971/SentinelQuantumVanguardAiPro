# Candidat13 — défilement de fiche Contacts sans rebond

**Verdict : anomalie physique toujours OUVERTE ; mitigation ciblée à valider sur S24+. NOT READY pour déclarer le gel corrigé ou la conformité D.1.** Aucune installation/signature13 sur le téléphone réalisée depuis le cloud. Aucun nouveau retour physique13.

## Signalement et hypothèse

Le propriétaire indique un blocage en fin de fiche, qui disparaît après quelques secondes quand il remonte et réapparaît s’il reste en bas. Les captures privées restent dans la conversation ; aucune donnée de contact ni photo n’est publiée. Aucun ANR ni journal runtime reçu. Le bouton Fermer pendant le blocage reste à qualifier.

Les sources exactes Foundation1.12.1 montrent que verticalScroll par défaut installe l’effet de dépassement Android ; applyToScroll absorbe les deltas inverses pour relâcher l’étirement avant de faire défiler le contenu. Le framework documente aussi un risque de blocage avec de petits deltas (b/265363356). Voir evidence/framework-investigation.json pour URL officielle et empreinte du source JAR. **Ce mécanisme est compatible avec le signalement, mais ne prouve pas la cause du S24+.** Pas de mise à jour de dépendance ni affirmation de bug Samsung confirmé.

## Modification unique

Dans D1ContactProfileContent, l’overload `verticalScroll(rememberScrollState(), overscrollEffect = null)` désactive uniquement le rebond du contenu. Défilement normal, gestes de la feuille modale, fermeture, sélection de texte, photo, données et callbacks Téléphone/Message conservés. VersionCode12→13 pour mise à jour normale. Aucun provider write, schéma Room ou secret modifié ; aucune fusion/installation automatique. Le schéma local non suivi conserve son SHA a7c5b4e1926c4441629734c0703ba6174e6bcf3796394b51302846bac0dae0c4.

C’est un changement isolé pour tester une hypothèse sur le même appareil. Si le blocage persiste, ne pas empiler de changements : revenir aux observations runtime/gestes et à une collecte ciblée. Aucun RED→GREEN runtime du gel revendiqué.

## Provenance et vérification

- Dépôt : teetee971/SentinelQuantumVanguardAiPro ; branche fix/d1-phone-messages-conformity-20261029 ; PR brouillon1748.
- **Source APK : fd32445e80a8966f0020dcddcf0d0f5a65c41468** ; base distante précédemment vérifiée de39b0d6d91614c8fdc6bae27d79854fd16309e8.
- Liste exacte du commit source : SOURCE_FILES.txt ; commits ultérieurs de livraison exclusivement documentaires.
- Kotlin, suite unitaire app, APK debug/test/unsigned : build exit0 (1m13). Build post-commit exit0 (17s,tâches incrémentales, mêmes sources).
- **Application :1422 réussis,0 échec,0 erreur,0 ignoré** (305XML). **Wearable :26 réussis,0 échec,0 erreur,0 ignoré**, tâche inchangée/cachée, résultats antérieurs conservés ; aucun nouveau comportement wearable prétendu. **Contrats Node :15 réussis,0 échec,0 ignoré**, exécutés à nouveau.
- **Tests instrumentés NON EXÉCUTÉS** : aucun appareil cloud. Le test `repeatedBottomGesturesStillAllowScrollingBackAndClosing` et les autres tests d’interface sont compilés. Pas de preuve GPU/API37/One UI apportée par les tests JVM.
- Package application com.sentinel.quantum, versionName1.0.9, versionCode13, min24/target36/compile37 ; aapt vérifié. Source native inchangée après commit source.

## Artifacts

- **unsigned** : `Sentinel-D1-v13-UNSIGNED-fd32445e.apk`, 101325831 octets, SHA256 `8eff6b7dab06575a4834ffd8e519405e486a89cd4a0865c01fd3069df8c0eb89` ; apksigner exit1, zipalign4 exit0, zipalign16KiB exit0, aapt exit0.
- **cloud_debug** : `Sentinel-D1-v13-CLOUD-debug-fd32445e.apk`, 101428857 octets, SHA256 `6c4f54d089dc2760bf21cdb38a380d2adcdb4f378357fe02cd01304c4c5a7e4f` ; apksigner exit0, zipalign4 exit0, zipalign16KiB exit0, aapt exit0.
- **instrumentation** : `Sentinel-D1-v13-androidTest-fd32445e.apk`, 1228070 octets, SHA256 `dcd983e372b0a58cf9677caf54ec442b6825a0919c46585520437ac6cc8b0bb6` ; apksigner exit0, zipalign4 exit0, zipalign16KiB exit0, aapt exit0.

Téléchargement propriétaire **NON SIGNÉ — NON INSTALLABLE** : https://drive.google.com/file/d/1DKjijOAIUnDVecQmTyLgB7ots-hva-4F/view?usp=drivesdk

Le fichier unsigned a bien une vérification apksigner exit1 attendue ; il n’a pas de certificat validé. Les fichiers cloud debug et test sont signés avec dee0d0f14e3a211d5ce9adca0280acde443e2312857d33da2258bef0f1b632e1 et sont incompatibles avec la signature propriétaire68915958f2f8683afead51cc3a1e578d182df4b9d9b2505c48c261b1a17b3fe2. Ne pas les installer sur l’application existante. La clé historique reste chez le propriétaire ; signer13 localement, vérifier certificat unique/package/code13/contenu/alignement/SHA puis obtenir l’autorisation avant mise à jour. Aucun fichier13 signé dans le cloud. Métadonnées Drive relues, taille confirmée ; SHA de l’APK calculé localement, pas de relecture intégrale distante revendiquée.

## Essai physique ciblé après signature et mise à jour autorisée

1. Confirmer versionCode13, ouvrir le même contact de test et sa fiche.
2. Descendre jusqu’au texte de provenance ; rester immobile cinq secondes. Tester Fermer une première fois.
3. Rouvrir, atteindre la fin, effectuer trois gestes supplémentaires vers le bas de la page, puis remonter avec un geste franc : le contenu doit répondre sans attente de plusieurs secondes. Répéter cinq fois et noter durée/fréquence de tout blocage.
4. Vérifier photo et rubriques intactes, accès au haut et au bas, fermeture/retour et absence d’appel/envoi déclenché par navigation. Texte200%/TalkBack restent un essai distinct.

Le succès de cet essai ne qualifiera que cette interaction. Les26 scénarios généraux, appels/SMS/MMS réseau avec seconde ligne, rôles, révocations et conformité visuelle restent séparés. Voice inchangé, aucune sollicitation facturable ni modification de secret.
