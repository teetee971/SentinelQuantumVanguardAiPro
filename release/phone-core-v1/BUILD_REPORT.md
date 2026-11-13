# Livraison Phone Core V1 — build et provenance

**Qualification hôte réussie ; Galaxy S24+ Android 17 NON TESTÉ.** Aucun appareil connecté. Aucune publication Google Play ni fusion de PR.

## Code et périmètre

Branche `work/phone-core-v1-20261029`, PR en brouillon [#1747](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1747), cible `qualification/phone-core-v1-20261029` (base da157f4), jamais main.

Sources Android réellement compilées : **846ed793b1234e1d87ec21a218e50dba6c52d43d**. Les commits suivants ajoutent les documents et renforcent les outils sans modifier les entrées Android : `logs/source-provenance.log` vérifie leur correspondance. Le SHA final de la branche figure dans la PR / `git rev-parse HEAD`, distinct du SHA source des binaires.

Le commit d03dfb07, ses journaux et ses trois artefacts ont été retrouvés et vérifiés par empreinte avant intervention. Les binaires ci-dessous les remplacent pour les prochains essais.

Correctifs de cette passe : publication d’appel manqué protégée contre la révocation concurrente ; permissions héritées CAMERA/FOREGROUND_SERVICE_MEDIA_PROJECTION et service de capture d’écran inutilisé retirés ; commandes de conférence/fusion/permutation non qualifiées masquées. Attente/reprise et double appel ciblé conservés. Modules SMS/MMS, VPN, BLE, Voice Studio audio, Security Lab préservés. Schéma Room présent, non suivi et empreinte inchangée ; aucune suppression.

## Configuration et reproduction

JDK Temurin17.0.21, Gradle9.8.0, AGP9.4.1, Kotlin/Compose2.4.20, KSP2.3.12. SDK `platforms;android-37.0`, Build Tools36.0.0. `com.sentinel.quantum`, versionName1.0.9/versionCode10, min24/target36/compile37. Deux APK signés Android Debug ; bundle releaseUnsigned minifié, non débogable et non signé.

```bash
export JAVA_HOME=/chemin/jdk17
export ANDROID_HOME=/chemin/android-sdk
export GRADLE_EXECUTABLE=/chemin/gradle-9.8.0/bin/gradle # optionnel, sinon gradlew
export BUNDLETOOL_JAR=/chemin/bundletool.jar
export PHONE_CORE_SIGNED_FIXTURE=/chemin/fixture-signee.apk
python3 scripts/qualify-phone-core-v1.py
```

La fixture est un APK signé de confiance servant au test cryptographique : ici l’APK debug d03dfb07 conservé séparément. Les sources/modules/configuration et outils doivent être committés ; les schémas Room générés non suivis sont tolérés. Derrière proxy, conserver les propriétés réseau et le truststore Java de l’environnement, sans désactiver TLS.

`COMMAND_RESULTS.json` conserve les argv exacts, codes de sortie et journaux. Les contrôles additionnels ont leur `.log`/`.exit`. Le build complet réussit en 6 min38 s ; 1 356 tests app réussis sans échec, erreur ou ignoré. Voir `TEST_RESULTS.md` ; les tests hôte ne prouvent pas l’exécution physique.

## Artefacts présents

| Fichier | Octets | SHA-256 |
|---|---:|---|
| Sentinel-PhoneCore-V1-debug.apk | 98030896 | `f425738907548935a306260b0205cd5070ffeb8384173d6a58a50a60ac1b9851` |
| Sentinel-PhoneCore-V1-androidTest.apk | 1136918 | `0c564b78ef56002246d2f1aa79675d0a3cb58f9555256690b7a6d5e3c601b2e8` |
| Sentinel-PhoneCore-V1-release-unsigned.aab | 40039043 | `40d1820130ae07bc18e75d76efcaceee01ba2d0ea60af4a434ec8602acdeb788` |

Chemins réels :

- [Sentinel-PhoneCore-V1-debug.apk](/workspace/SentinelQuantumVanguardAiPro/release/phone-core-v1/Sentinel-PhoneCore-V1-debug.apk) — `:app:assembleDebug`.
- [Sentinel-PhoneCore-V1-androidTest.apk](/workspace/SentinelQuantumVanguardAiPro/release/phone-core-v1/Sentinel-PhoneCore-V1-androidTest.apk) — `:app:assembleDebugAndroidTest`.
- [Sentinel-PhoneCore-V1-release-unsigned.aab](/workspace/SentinelQuantumVanguardAiPro/release/phone-core-v1/Sentinel-PhoneCore-V1-release-unsigned.aab) — `:app:bundleReleaseUnsigned`.

Tous proviennent du commit source 846ed793 et de cette configuration. Le test APK est identique au précédent car ses sources instrumentées n’ont pas changé ; sa tâche Gradle actuelle a été vérifiée. L’APK cible contient les nouveaux correctifs. `ARTIFACTS.json` donne également les chemins originaux Gradle et les liens distants éventuels (`null` lorsqu’indisponibles).

`SHA256SUMS.txt` vérifie les trois binaires. `JUNIT_RESULTS.zip` conserve les XML du passage complet. L’archive `Sentinel-PhoneCore-V1-delivery.zip`, hors Git, contient tout ce dossier ; empreinte dans `DELIVERY_ARCHIVE.sha256`.

## Récupération

Les liens de fichiers locaux donnés dans la conversation correspondent aux fichiers existants ci-dessus. Documents, sommes et journaux sont versionnés ; binaires/archives volumineuses exclus de Git.

Un stockage GitHub a été essayé : brouillon créé, upload **refusé HTTP401/code1**, aucun binaire distant. Le brouillon vide ne constitue pas un lien de téléchargement. `ARTIFACT_STORAGE.md` fournit la commande propriétaire d’upload et de vérification. Aucun token recherché/exposé ; aucun workflow Actions, job Codemagic ou dépense engagé.

## Dernières validations

- S24+ Android17 : **NON TESTÉ**. Suivre `INSTALL_S24_PLUS.md` puis les 14 scénarios de `PHONE_CORE_PHYSICAL_TESTS.md` ; collecteur borné sans appel automatisé.
- Play interne/fermé/production : **NON** maintenant ; signature autorisée, code10 libre, déclarations de permissions/Data Safety et campagne physique à solder selon `GOOGLE_PLAY_READINESS.md`.
- AAB prêt à recevoir une signature d’upload autorisée ; aucune clé privée release créée/exposée/committée. Non signé, il n’est pas téléversable sur Play.
- `KNOWN_ISSUES.md` détaille les limites et modules hors qualification. Aucun « 100 % fonctionnel » annoncé.
