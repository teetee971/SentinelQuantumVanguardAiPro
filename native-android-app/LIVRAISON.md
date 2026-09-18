# État de livraison — Application Android Native Sentinel Quantum Vanguard

## Statut

Le dépôt contient le code source Android natif dans `native-android-app/`.

Aucun APK précompilé n'est actuellement distribué dans le dépôt et aucun lien de téléchargement d'APK ne doit être présenté comme officiel tant qu'un artefact signé n'a pas été effectivement produit et publié.

## Périmètre validé

- Kotlin + Jetpack Compose
- Consultation de sources OSINT publiques et fonctions locales Android
- CERT-FR, ANSSI et CVE/NVD
- Filtrage d’appels local via `CallScreeningService` après attribution explicite du rôle système
- Analyse locale de SMS collé/partagé, sans rôle SMS par défaut
- Aucun backend propriétaire obligatoire pour le chemin critique de filtrage
- Aucune authentification
- Aucune collecte ou télémétrie applicative annoncée
- Permissions réseau, notifications, contacts optionnels et Wi-Fi/Bluetooth bornées selon les fonctions documentées
- Interface sombre, sobre et institutionnelle
- Aucune promesse de protection globale de type antivirus, EDR, pare-feu ou VPN

## Source de vérité

La documentation opérationnelle Android est :

- `native-android-app/README.md`
- `native-android-app/BUILD_GUIDE.md`
- les fichiers Gradle et le code présents sous `native-android-app/`
- les workflows GitHub Actions Android lorsqu'ils ont effectivement exécuté leurs étapes

L'ancien `APK_README.md` a été supprimé car il décrivait un APK comme disponible alors qu'aucun artefact distribué n'était garanti.

## Build local

Depuis le dépôt :

```bash
cd native-android-app
./gradlew assembleDebug
```

Pour une release :

```bash
./gradlew assembleRelease
```

Une release destinée à la distribution doit être signée avec un certificat géré hors du dépôt. Les clés et secrets de signature ne doivent jamais être commités.

## Validation

La présence du code source ne constitue pas, à elle seule, une preuve de compilation ou d'installation réussie.

Une validation Android est considérée comme acquise uniquement lorsqu'un workflow ou un build local a réellement exécuté la compilation et fourni un résultat exploitable.

## Séparation des projets

Sentinel Quantum Vanguard AI Pro reste strictement séparé de `A KI PRI SA YÉ` et de toute infrastructure étrangère. Les références Firebase présentes dans les scanners d'isolation et leurs tests sont des motifs interdits utilisés pour vérifier cette séparation ; elles ne constituent pas des dépendances opérationnelles.

## Android App Bundle (AAB) — livraison intermédiaire

Le workflow `.github/workflows/build-aab-playconsole.yml` produit un Android App Bundle (`bundleReleaseUnsigned`) à partir de la même source Android canonique. Cet AAB reste un **artefact intermédiaire de validation d'empaquetage**, pas la livraison Play Console finale :

- il n'est **pas signé** (aucun `signingConfig` n'est appliqué à la variante `releaseUnsigned`) ;
- il ne peut donc pas être publié tel quel sur le Play Console ;
- il prouve seulement que l'empaquetage AAB compile et respecte les règles de base (`applicationId`, `targetSdk`, `versionCode`, `versionName`).

La publication réelle sur Play Console utilise l’AAB signé produit par `.github/workflows/android-release.yml` avec les secrets de production (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`), ou une configuration équivalente de Play App Signing. L’AAB `releaseUnsigned` de validation ne doit jamais être soumis comme artefact de production.
