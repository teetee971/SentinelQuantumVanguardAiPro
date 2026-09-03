# État de livraison — Application Android Native Sentinel Quantum Vanguard

## Statut

Le dépôt contient le code source Android natif dans `native-android-app/`.

Aucun APK précompilé n'est actuellement distribué dans le dépôt et aucun lien de téléchargement d'APK ne doit être présenté comme officiel tant qu'un artefact signé n'a pas été effectivement produit et publié.

## Périmètre validé

- Kotlin + Jetpack Compose
- Consultation en lecture seule de sources OSINT publiques
- CERT-FR, ANSSI et CVE/NVD
- Aucun backend propriétaire
- Aucune authentification
- Aucune collecte ou télémétrie applicative annoncée
- Permissions limitées à l'accès réseau nécessaire aux flux
- Interface sombre, sobre et institutionnelle
- Pas de promesse de cybersécurité active

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
