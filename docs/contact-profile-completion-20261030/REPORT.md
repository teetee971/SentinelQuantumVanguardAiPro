# Sentinel D.1 — fiche Contacts, candidat12

## Résultat et limites

Correctif implémenté et vérifié par compilation/tests JVM. **NOT READY pour installation : l’APK de mise à jour est volontairement NON SIGNÉ.** Il doit être signé sur le téléphone avec la clé originale, puis contrôlé (certificat, package, version, alignement, SHA) avant une mise à jour expressément autorisée. Aucune installation, désinstallation, suppression de données ou changement de clé réalisé par le cloud. Le S24+ reste en version11 selon les preuves utilisateur précédentes.

**Conformité D.1 complète : NON VALIDÉE. Tests instrumentés : NON EXÉCUTÉS. Tests physiques de la version12 : NON EXÉCUTÉS.** Les observations physiques antérieures concernent uniquement la version11. Les appels/SMS/MMS réseau restent à tester avec une seconde ligne disponible.

## Provenance

- Dépôt : https://github.com/teetee971/SentinelQuantumVanguardAiPro
- Branche : `fix/d1-phone-messages-conformity-20261029`
- PR brouillon : https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1748
- Base vérifiée distante/locale : `1be459199c5171ddf716b0002a91edac620b0ba5`
- **Commit source des APK : `06f913c057b9b1814f4dea7c436047df3dd2dfd4`**
- Arbre native-android-app : `d0ffdd263bfc35ee5d1416e97980d4f6fda436fe`
- Version : com.sentinel.quantum, versionName1.0.9, **versionCode12** (supérieur à11 installé).
- Build post-commit : `:app:compileDebugKotlin :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleD1UpdateUnsigned :wearable-security:test`, exit0. Tâches application incrémentales ; vraie exécution complète app immédiatement avant commit, sources identiques, aucun correctif source après ce commit.
- Liste exacte des fichiers du commit source : [SOURCE_FILES.txt](SOURCE_FILES.txt). Les commits ultérieurs de livraison ne changent que la documentation/preuves.

## Correctifs réellement utilisés

`SentinelDialerActivity` continue d’ouvrir `D1ContactProfileSheet` : pas de composant laissé inutilisé. La fiche utilise maintenant `LocalContactProfileReader` et `loadContactProfile` pour lire, hors thread UI, uniquement les rubriques du contact sélectionné. Aucun provider write, migration Room, service distant ou enrichissement Voice.

- Téléphones relus et dédupliqués visuellement. Les boutons utilisent uniquement les numéros de la lecture fraîche réussie ; aucun repli sur un ancien numéro pendant chargement/refus/échec. Libellés locaux lorsque disponibles.
- Nom structuré/phonétique, e-mails, adresses (avec repli structuré), entreprise, fonction, service, description du poste, symbole, nom phonétique d’entreprise et bureau, dates/anniversaire, notes, sites/profils importés, autres noms, relations, messagerie et SIP.
- États chargement, permission requise, fournisseur indisponible et rubriques non renseignées distincts. Dates sans âge calculé ni certitude inventée.
- Identité du résultat liée au contact/révision/génération/activation ; permission relue après I/O ; annulation vérifiée aussi après SecurityException. À la pause, détails retirés et producteur annulé ; relecture au retour.
- Feuille ouverte entièrement, action Fermer fixe ; contenu défilable ; photo réelle centrée dans le visuel décoratif existant ; nom et source locale ; Modifier/Partager placés avant les longues rubriques. Conteneurs de fiche/avatar/boutons concernés explicitement sombres/cyan, sans retoucher tout le thème.
- Photo : la capture utilisateur prouve un chargement sur version11 ; **le nouveau cadrage version12 n’a pas encore été observé physiquement**. Pas de photo ni donnée personnelle copiée dans Git.

## Tests vérifiés

| Contrôle | Réussites | Échecs/erreurs | Ignorés |
|---|---:|---:|---:|
| Application Android, suite unitaire complète `testDebugUnitTest` |1422|0/0|0|
| Module wearable-security, suite JVM `test` |26|0/0|0|
| Contrats Node release/debug/AAB |15|0|0|
| Tests instrumentés (6 nouveaux compilés dans APK de tests) | NON EXÉCUTÉS | NON EXÉCUTÉS | sans résultat runtime |

Dix nouveaux tests JVM protègent normalisation/doublons/bornes, permission avant/après lecture, absence fournisseur, identité/révision/génération/activation, annulation et exception de permission tardive. Ce dernier test a réellement échoué avant correction (10 tests,1 échec), puis réussi avec la suite1422/1422. Les phases initiales RED d’API absente et erreurs de compilation intermédiaires restent documentées ; elles ne sont pas des validations de livraison. La compilation initiale du lecteur a révélé des méthodes de libellés inexistantes sur Website/Nickname ; mappings explicites corrigés. Une première compilation UI a révélé un nom de couleur inexistant ; corrigé. Deux compilations intermédiaires se chevauchaient pendant édition et ont échoué ; leurs APK éventuels ne sont pas livrés. Le build final qualifié et le build post-commit ont été séquentiels sur code stabilisé.

Les nouveaux tests d’instrumentation couvrent la vraie lecture de curseur synthétique, numéros dédupliqués/actions, retrait d’un ancien numéro, absence de numéro frais, accès aux détails par défilement et fermeture de la vraie feuille à texte200% avec note longue. Ils sont **compilés seulement**. ADB cloud ne liste aucun appareil ; aucun appel de tests à l’adresse Wi-Fi privée ni aucune installation automatique.

Revue indépendante réalisée : lacunes nom/organisation et utilisation des anciens numéros relevées, traitées et couvertes. Précision : l’Activity recharge déjà le carnet au retour (LaunchedEffect vers1004), contrairement à l’hypothèse initiale de rechargement uniquement manuel ; l’interdiction de repli sur numéros périmés reste une protection utile pendant cette lecture. Vérifications physiques, défilement/accessibilité réels et transport restent ouverts.

## APK et contrôles

- **unsigned** : `Sentinel-D1-v12-UNSIGNED-06f913c0.apk`, 102260778 octets, SHA-256 `e0d779cf5601fae9ceeecb609d5d38bc497d09a671f47d2c6a73d3f33efa021b` ; signature exit1, alignement4 exit0, alignement16KiB exit0, badging exit0.
- **cloud_debug** : `Sentinel-D1-v12-CLOUD-debug-06f913c0.apk`, 101428857 octets, SHA-256 `5680e7dc8df1baf826366943c866e312ddb748264f6b65a1fce99aa8bac10b36` ; signature exit0, alignement4 exit0, alignement16KiB exit0, badging exit0.
- **instrumentation** : `Sentinel-D1-v12-androidTest-06f913c0.apk`, 1225537 octets, SHA-256 `5ff3ba77f9db666bf1450bc7e5d23ba9e9d931001890fb03dfc1532f4219136f` ; signature exit0, alignement4 exit0, alignement16KiB exit0, badging exit0.

L’échec apksigner du fichier unsigned est **attendu** : ce n’est pas une signature validée. APK cloud debug et test : signaturev2 vérifiée, un signataire dee0d0f14e3a211d5ce9adca0280acde443e2312857d33da2258bef0f1b632e1, **incompatible avec l’application du propriétaire** (68915958f2f8683afead51cc3a1e578d182df4b9d9b2505c48c261b1a17b3fe2). Ne pas les installer sur l’application existante. Aucun APK12 signé6891 disponible dans le cloud. La clé reste exclusivement chez le propriétaire.

Téléchargement **NON SIGNÉ, NON INSTALLABLE** : https://drive.google.com/file/d/1nCVx-urn3rpHufaOfHaJdlKrWM2Tgozv/view?usp=drivesdk

## Écarts et validations restant à faire

- La fiche devient plus complète pour les types Android courants ; les données propriétaires de comptes tiers et groupes ne sont pas intégrées. Valeurs bornées :256 rubriques,16384 caractères/note,4096/autre valeur ; l’UI signale ces bornes. Les doublons nationaux/internationaux ne sont pas fusionnés par supposition géographique.
- Aucun historique de communications, profil social vérifié, score de confiance ni identité confirmée inventé. Partager conserve le comportement existant (nom/numéros) ; ce n’est pas un export universel vCard.
- La maquette R03 et C01 reste dans la conversation (registre VISUAL_REFERENCES.md). Le globe décoratif existant n’est pas une preuve de reproduction exacte. Palette générale des autres écrans, Messages et Téléphone inchangés dans ce correctif.
- À vérifier sur S24+ : ouverture entièrement développée, Fermer sans défilement, accès à toutes rubriques, note longue/texte200%/TalkBack, bon contact après allers-retours, édition externe/suppression et révocation Contacts. Utiliser un contact de test ; aucune donnée privée dans preuves publiques.
- Appels entrants/sortants/simultanés et SMS/MMS transport exigent une seconde ligne ; conserver NON TESTÉ tant qu’indisponible. Matrice26 : docs/d1-requalification-20261029/S24_PHYSICAL_MATRIX_20261030.md, preuves version11 seulement.
- Voice : aucune modification ni sollicitation facturable ; les limites précédentes restent dans VOICE_LIMITATIONS.md. Ce correctif n’établit aucune disponibilité Voice.
- Room non suivi conservé, SHA du schéma1.json : a7c5b4e1926c4441629734c0703ba6174e6bcf3796394b51302846bac0dae0c4.
- Aucune fusion, aucun workflow payant, aucune installation automatique.

## Archive d’audit

https://drive.google.com/file/d/1o3iUrXcULrh4uiFT5EQDteg2bzWLnoN5/view?usp=drivesdk

Sentinel-D1-v12-audit-06f913c0.zip, 52658 octets, SHA-256 `a82833222ae05bfebe9bbabb66e91d32597b4500acd3d3b94f1a4ac2e1c0b8b0`. Archive figée avant l’ajout de ce reçu : rapport, contrôles, journaux et diff source ; aucun APK/keystore/capture privée. Métadonnées Drive relues, taille confirmée.
