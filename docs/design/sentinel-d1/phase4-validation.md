# Sentinel D.1 Phase 4 — validation technique et limites

## Références

- Archive : `Sentinel_D1_13_Visuels_Officiels.zip`
- SHA-256 archive : `b1c84e0aa8ea6186bd82d282dea59e2119b91026c2ae46de0b93751aee8c905a`
- Originaux présents : 13 fichiers nommés `1000415621.png` à `1000415633.png`.
- Résolution originale : 864 × 1536 pour chaque écran ; planche conservée séparément.
- Index et empreintes : [`reference-index.md`](reference-index.md).

## Commandes exécutées

Depuis `native-android-app/` avec Temurin 17 :

```text
./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:lintDebug :app:assembleDebug
```

Résultat observé : `BUILD SUCCESSFUL`.

- Tests unitaires JVM : 1434 exécutés, 0 échec, 0 erreur.
- Contrats Phase 4 : 6 tests exécutés, 0 échec, 0 erreur.
- Compilation des tests Android : réussie.
- Lint debug : 0 erreur, 217 avertissements préexistants ou non bloquants, 17 hints.
- Scanner d’isolation : 1315 fichiers, 1786 entrées, 0 violation.

Les avertissements lint sont conservés et ne sont pas masqués par cette refonte. Ils incluent des
API Android conditionnelles, des dépréciations de bibliothèques et des conseils de ressources.

## Preuve visuelle

Les références ont été ouvertes et examinées en résolution originale. La capture du rendu Android
est `NON DISPONIBLE` dans cet environnement : aucun émulateur, appareil adb ou Galaxy S24+ n’est
présent. Les tests source et la compilation ne remplacent pas une comparaison de captures.

Non exécuté : TalkBack réel, grandes polices sur appareil, navigation tactile S24+, GPU/FPS,
consommation, rotation et installation physique.

## État de qualification

- Références et hash : PASS.
- Design system et scènes spécialisées : PASS sur contrats source.
- Layout adaptatif : PASS sur contrats source ; capture réelle nécessaire pour la qualification.
- Build APK : PASS.
- Visuel Android : NON QUALIFIÉ.
- Appareil Galaxy S24+ : NON QUALIFIÉ.

Le SHA-256, la signature et le lien du nouvel APK sont rapportés dans le handoff final après le
rebuild de livraison exécuté sur le dernier commit de la branche Phase 4.
