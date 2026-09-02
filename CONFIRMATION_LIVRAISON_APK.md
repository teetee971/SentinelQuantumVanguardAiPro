# APK Android — état de livraison vérifiable

Ce document remplace l'ancienne confirmation de livraison. Il ne déclare aucune release comme validée sans preuve actuelle.

## Références actuelles

Projet Android canonique : `native-android-app/`.

Build de validation : `.github/workflows/build-native-android.yml`.

Release signée : `.github/workflows/android-release.yml`.

La release signée est déclenchée uniquement par un tag `v*`. Le workflow vérifie le format du tag et exige que le commit du tag soit rattaché à `main` avant le build signé et la publication.

## Artefacts

Le workflow de release produit un ou plusieurs APK issus de `assembleRelease`, vérifie leur présence et génère un checksum SHA-256 pour chaque APK avant publication.

Le nom exact et le contenu de l'artefact doivent être relevés dans l'exécution CI ou la release correspondante. Aucun nom de fichier, poids ou URL de téléchargement ne doit être inventé à l'avance.

## Signature

Les secrets de signature attendus sont `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` et `KEY_PASSWORD`.

Le keystore de production ne doit jamais être commité. Le workflow le décode temporairement dans le runner et le supprime après traitement.

Il est interdit de présenter un debug keystore comme signature de production.

## Validation d'une livraison

Une livraison n'est considérée comme validée que si les preuves suivantes sont disponibles :

1. exécution CI réussie ;
2. APK présent et non vide ;
3. SHA-256 vérifié ;
4. signature vérifiée ;
5. installation et lancement testés sur appareil ;
6. absence de secret dans le dépôt et l'artefact ;
7. correspondance vérifiable entre tag, commit source et artefact.

## Blocage CI actuel

L'issue #195 documente des échecs GitHub Actions avant l'exécution des étapes. Tant que ce blocage persiste, ce document ne doit pas être utilisé pour déclarer une livraison validée ou une CI globale réussie.

## Historique

Les références à `android-app/android/`, `release-apk.yml`, aux secrets `RELEASE_KEYSTORE_*` ou à des flavors inexistants sont obsolètes.

## Séparation

Sentinel Quantum Vanguard AI Pro reste totalement séparé de A KI PRI SA YÉ. Aucun import, secret, configuration, dépendance ou couplage opérationnel avec cet autre projet n'est autorisé.

## Règle de preuve

`correctif appliqué ≠ testé ≠ CI réussie ≠ artefact validé ≠ release validée ≠ sécurité prouvée`.
