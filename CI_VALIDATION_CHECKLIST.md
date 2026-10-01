# CI Validation Checklist — Sentinel Quantum Vanguard AI Pro

Ce document décrit les preuves à exiger sur le **SHA courant**. Il ne conserve pas de statut vert global : un workflow réussi sur un ancien commit n’est pas une preuve pour le commit suivant.

## 1. Intégrité et supply chain

- [ ] `Integrity Check` réussi.
- [ ] références GitHub Actions externes pinées sur SHA complet.
- [ ] contrôle d’isolation Sentinel réussi.
- [ ] aucun secret, keystore, `google-services.json` ou clé privée committé.
- [ ] audit npm sans vulnérabilité bloquante selon la politique du dépôt.

## 2. Gouvernance et sécurité

- [ ] `Security Governance Validation` réussi.
- [ ] `Sentinel AI Governance Validation` réussi.
- [ ] `Security Fuzzing` réussi.
- [ ] `Pre-production Final Gate` réussi.
- [ ] CodeQL réussi pour les langages/jobs applicables au SHA.
- [ ] aucun contrôle n’a été désactivé, rendu optionnel ou contourné pour obtenir le vert.

## 3. Frontend

- [ ] `Frontend Validation` réussi.
- [ ] liens statiques, claims publics, pages légales et règles de sécurité client validés.
- [ ] `Lighthouse Pre-production Baseline` réussi lorsque déclenché.
- [ ] aucun état « opérationnel », pourcentage d’avancement ou téléchargement public n’est affiché sans preuve correspondante.

## 4. Android — build de validation

Configuration exécutable actuelle :

- `compileSdk 37`
- `targetSdk 36`
- `minSdk 24`
- `versionCode 7`
- `versionName 1.0.6`
- JDK 17
- AGP 9.4.1
- Kotlin 2.4.20
- Gradle 9.8.0

Preuves requises :

- [ ] `Build Native Android APK` réussi.
- [ ] tests unitaires Android réussis.
- [ ] lint Android réussi.
- [ ] `:app:exportReleaseDependencyInventory` réussi avec composants et relations non vides.
- [ ] APK debug construit, package vérifié, alignement et signature vérifiés.
- [ ] scan anti-secrets de l’APK réussi.
- [ ] installation et lancement sur émulateur Android 10 réussis.
- [ ] parcours premier lancement / reprise Phone Core du smoke test réussi.
- [ ] `Build Android App Bundle (Unsigned Validation)` réussi pour l’AAB de validation non signé.

## 5. Release signée

La présence du workflow ne vaut pas exécution.

Avant création du tag :

- [ ] `main` est exactement au SHA choisi et toutes les gates applicables sont vertes.
- [ ] le tag est exactement `v<versionName>` — actuellement `v1.0.6`.
- [ ] environnement GitHub `android-production` configuré et protégé.
- [ ] secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` disponibles.
- [ ] stratégie Play App Signing décidée.

Après le run `Android Release` :

- [ ] APK signé présent.
- [ ] AAB signé présent.
- [ ] SHA-256 vérifiés.
- [ ] rapports de certificat vérifiés et identité de signature attendue confirmée.
- [ ] SBOM présent.
- [ ] `release-dependencies.json` présent et vérifié.
- [ ] `release-evidence.json` retourne `verified: true`.
- [ ] brouillon GitHub Release conservé non public jusqu’aux tests physiques.

## 6. Validation physique Phone Core

Ces points ne peuvent pas être remplacés par un émulateur ou une CI :

- [ ] appels opérateur entrants et sortants ;
- [ ] Call Screening réel ;
- [ ] surface InCall / Caller ID ;
- [ ] contacts et journal avec rôles/permissions réels ;
- [ ] SMS entrant ;
- [ ] SMS SENT + DELIVERED ;
- [ ] MMS sécurisé sur opérateur réel ;
- [ ] notifications ;
- [ ] cold-start / redémarrage / révocation et reprise ;
- [ ] multi-SIM ;
- [ ] matrice de versions Android / constructeurs / opérateurs ciblés.

## 7. Play Console et publication

- [ ] AAB accepté par Play Console.
- [ ] fiche Play et politique de confidentialité alignées avec le binaire.
- [ ] permissions sensibles justifiées et acceptées.
- [ ] aucun téléchargement direct activé si l’identité de signature est incompatible avec Play App Signing.
- [ ] publication publique seulement après preuves CI + release signée + tests physiques + validation Play.

## Règle de décision

`code présent ≠ build réussi ≠ release signée ≠ test physique ≠ publication validée`.

Toute case non prouvée reste non validée.
