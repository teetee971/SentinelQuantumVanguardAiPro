# Corrections Sentinel IA C11 · 18 novembre 2026

**Correction de protocole vérifiée en JVM. Qualification physique C11 ouverte.**

## Séquence des changements

Le checkout est parti de `b717802b171c6324ce7c8559e92d7c36cc2fd04a`. `cc877a290c6af938235fbcdc8397636586fb0538` ajoute le protocole d’identité et l’isolation. `ea5f0a7d019b1113dd25810a6f325db789f38f39` distingue le libellé C11 du libellé historique C9. Les APK debug vérifiés correspondent à `ea5f0a7`. Les fichiers de preuve et le lanceur opérateur sont ajoutés après ce build.

| Fichier | Justification |
| --- | --- |
| `native-android-app/app/src/androidTest/java/com/sentinel/quantum/ai/SentinelAiRestartIdentity.kt` | Identité du processus fondée sur le PID et le champ 22 de `/proc/self/stat` dans le même `BOOT_COUNT`. Un nombre de boots strictement supérieur constitue la preuve distincte du mode reboot. L’identité canonique est authentifiée dans un domaine HMAC C11 lié à l’alias. |
| `native-android-app/app/src/androidTest/java/com/sentinel/quantum/ai/SentinelAiRestartIdentityTest.kt` | Six tests déterministes de l’identité, de la séparation des modes et du refus de métadonnées altérées ou hors fixture. |
| `native-android-app/app/src/androidTest/java/com/sentinel/quantum/ai/SentinelAiPersistenceQualificationTest.kt` | Clé AndroidKeyStore et fixture par mode et UUID. La préparation persiste et relit les métadonnées, vérifie la séquence 1 et reste active jusqu’à l’arrêt explicite. La vérification confirme l’identité, réouvre l’ancre précédente, ajoute la séquence 2 et publie son reçu après vérification du journal. Les fixtures persistantes sont conservées. |
| `native-android-app/app/src/androidTest/java/com/sentinel/quantum/ai/SentinelAiActivityInstrumentationTest.kt` | Le test inter-activités qui journalise la révocation requiert le paquet C11. |
| `native-android-app/app/src/androidTest/java/com/sentinel/quantum/ai/SentinelAiActionJournalEvidenceTest.kt` | Les reçus et la fenêtre de journal correspondent au paquet C11 et portent des clés de rapport C11. |
| `audit/cycle11/validation/init.gradle` et `AndroidManifest.xml` | Recette opt-in ARM64, paquet `com.sentinel.quantum.aivalidation.c11` et libellé dédié. |
| `audit/cycle11/validation/qualify_restart.sh` | Orchestration ADB des deux modes et conservation des reçus et codes retour dans un dossier neuf. |

Le périmètre Git laisse le code Android de production, le service Qwen et les preuves historiques C9 hors de ces corrections. Le protocole utilise une nouvelle clé de fixture AndroidKeyStore. Le journal réel reste dans le paquet C11 isolé. Les modules Phone Core, SMS/MMS, réseau et produit ont fait l’objet d’un audit source.

## Tests et autoloop

| Contrôle | Statut | Portée |
| --- | --- | --- |
| Ancienne décision PID/boot, test de régression | FAIL attendu | 2 assertions rouges sur 5 cas. L’égalité de PID masque un nouveau processus et un reboot satisfait par erreur le mode processus. |
| Tests d’identité et d’authentification des métadonnées | PASS | 6 tests JUnit 4.13.2 sous JDK 17 et Kotlin 2.4.20. HMAC JVM avec clé de fixture. |
| JVM Android et Wear | PASS | 1 720 tests dans 358 suites, zéro échec, erreur ou test ignoré. |
| Compilation Kotlin Android, instrumentation et assemblage ARM64 | PASS | Gradle 9.8.0, SDK 37.0, Build-Tools 36.0.0. Compilation instrumentée initiale sur `cc877a2`, vérification et assemblage de la paire sur `ea5f0a7`. |
| Contrôles APK | PASS | Signature v2 des deux APK, certificat commun, mêmes empreintes que le manifeste SHA-256 local, paquet de l’app, ABI et runner ciblés. |
| Service loopback | PASS | 22 tests Python, dont récupération d’un processus enfant lors de la déconnexion. Le protocole Qwen réel ARM64 reste distinct. Un avertissement de dépréciation Starlette. |
| Contrat réseau loopback | PASS | 3 contrôles statiques. |
| Lanceur de persistance S24+ | PASS en simulation | Syntaxe Bash, 10 scénarios ADB simulés dont deux parcours positifs, et refus de 10 réutilisations de dossier. |
| Persistance AndroidKeyStore et S24+ | BLOCKED | Aucun appareil ADB. |

Le contrôle d’identité échoue si `/proc/self/stat` ou `BOOT_COUNT` est inaccessible. Une préparation expirée ou un arrêt du runner sans preuve du PID relève d’une nouvelle tentative. Un reboot ordonné avec `adb reboot` est le contrôle demandé dans cette recette. Une coupure électrique pendant une écriture demeure un scénario séparé.

[Journal de preuves local](C11_LOCAL_EVIDENCE.log) · [Vérification physique](VALIDATION_S24_PLUS.md)
