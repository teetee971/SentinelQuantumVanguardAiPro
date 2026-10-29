# Poursuite de qualification — 2026-10-29 UTC

Code testé : commit 29491eed87e8754580fae2d8850f3e3b22bfc676. Aucun changement
applicatif depuis ce commit ; cette poursuite ne modifie que rapports et preuves.

Le build offline initial échouait sur AGP absent. La résolution online a utilisé
le proxy de session et un truststore JDK copié avec le CA de session ajouté par
keytool ; TLS n'a jamais été désactivé. Outillage sous /workspace/tooling uniquement.

Commandes d'installation exécutées : téléchargement des quatre archives officielles
listées dans sdk-packages-sha1.txt par curl, vérification SHA-1 contre le manifeste
SDK officiel, extraction ; puis les deux appels suivants, codes 0 (réponses
positives fournies sur stdin pendant cette installation) :

```bash
/workspace/tooling/android-sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=/workspace/tooling/android-sdk --licenses
/workspace/tooling/android-sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=/workspace/tooling/android-sdk 'platforms;android-37.0' 'build-tools;36.0.0'
```
Le premier essai Gradle online avait échoué faute de métadonnées/licence du SDK ;
sdkmanager a installé les métadonnées correctes, puis le build a réussi.

`commands.json` conserve les argv et codes des builds Gradle réels. Le build
online réussi exécute 76 tâches ; la reconstruction `--offline --rerun-tasks`
exécute 78 tâches, dont Kotlin principal et instrumenté, les deux APK et lint.
`testDebugUnitTest` est NO-SOURCE : ne compter aucun test de cette tâche.
Les 72 tests Kotlin hôte et 12 Python précédents restent les seuls tests exécutés ;
leur code n'a pas changé et les preuves initiales sont conservées.

Résultat lint : 0 erreur, 2 avertissements existants de configuration : cible API36
et icône absente du module de test. Le compilateur signale aussi la dépréciation
de isInsideSecureHardware dans un test existant. Ces points ne justifient pas
un changement de cible ou de périmètre.

APK : empreintes dans apk-sha256.txt, signatures vérifiées par apksigner.
Ces APK sont signés par la clé debug de l'environnement cloud : ne pas présumer
qu'elle correspond à la clé déjà installée sur Galaxy. Ne jamais désinstaller ou
vider les données pour contourner une erreur de signature ; reconstruire avec la
clé existante sur l'environnement Termux/proot.

Aucun APK ni clé privée n'est ajouté au dépôt. Les deux APK restent dans les
répertoires build/outputs/apk du workspace. JNI C et binaire ARM64 comparés à la
base : inchangés. ADB ne voit aucun appareil. Tests instrumentés, fsync Android,
AndroidKeyStore réel, redémarrage et comportement Galaxy : NON EXÉCUTÉS.

Le graphe de dépendances du seul module est exporté pour faciliter l'audit futur.
Il ne remplace pas les six alertes Dependabot inaccessibles : NON VÉRIFIÉES,
HTTP403, aucun avis d'absence ou de résolution.
