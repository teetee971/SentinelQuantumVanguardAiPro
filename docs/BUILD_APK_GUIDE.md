# GUIDE — Construire l'APK Sentinel

**Sentinel Quantum Vanguard AI Pro — Module Android**

## Objectif

Construire, signer et vérifier l'APK Sentinel. Cette procédure est strictement dédiée à Sentinel.

**Règle d'isolation :** Sentinel ne doit importer, embarquer, configurer ou déployer aucun composant Firebase ni aucun artefact appartenant à A KI PRI SA YÉ. Aucun `google-services.json` provenant d'un autre projet ne doit être présent dans l'arbre Android.

## Prérequis

- JDK 17 ou supérieur
- Android Studio stable
- Android SDK correspondant au `compileSdk` du projet
- Node.js 18+ si le module Android utilise des dépendances Node
- Git

## 1. Cloner le projet

```bash
git clone https://github.com/teetee971/SentinelQuantumVanguardAiPro.git
cd SentinelQuantumVanguardAiPro/android-app
```

## 2. Configuration locale

Créer `android/local.properties` avec le chemin local du SDK. Ce fichier ne doit jamais être commité.

```properties
sdk.dir=/chemin/vers/Android/sdk
```

Vérifier également `android/gradle.properties` et ne placer aucun secret, jeton ou identifiant de service dans le dépôt.

## 3. Vérification d'isolation avant build

Depuis la racine du dépôt :

```bash
git grep -n -i -E 'akiprisaye|a-ki-pri-sa-ye|com\.akiprisaye|google-services\.json|com\.google\.firebase|firebase-messaging|firebase-admin|FIREBASE_TOKEN' -- ':!docs/SECURITY_README.md' ':!PRIVACY_POLICY.md'
```

Tout résultat dans le code, la configuration Gradle, les workflows ou les ressources Android doit être traité comme une anomalie et corrigé avant release.

## 4. Build debug

```bash
cd android
./gradlew clean
./gradlew assembleDebug
```

APK attendu : `app/build/outputs/apk/debug/app-debug.apk`.

## 5. Build release

Créer et conserver le keystore de production hors du dépôt. Les mots de passe doivent être fournis par l'environnement CI/CD ou par le système de build local, jamais dans Git.

```bash
keytool -genkeypair -v -keystore sentinel-release.keystore \
  -alias sentinel-key -keyalg RSA -keysize 3072 -validity 10000
```

Ne jamais commiter le keystore ni `keystore.properties`.

```bash
cd android
./gradlew assembleRelease
```

## 6. Vérification de signature et d'intégrité

```bash
jarsigner -verify -verbose -certs app/build/outputs/apk/release/app-release.apk
sha256sum app/build/outputs/apk/release/app-release.apk
keytool -list -v -keystore sentinel-release.keystore
```

Conserver l'empreinte SHA-256 du certificat Sentinel dans le système de distribution approprié. Elle sert à vérifier l'identité du build Sentinel. Aucune configuration Firebase n'est requise ni autorisée.

## 7. Installation de test

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat | grep Sentinel
```

Tester les permissions, le démarrage, les fonctions de sécurité, les notifications locales, les erreurs réseau et les scénarios de récupération.

## 8. Contrôles de sécurité release

Avant publication :

- vérifier l'`applicationId` Sentinel attendu ;
- vérifier qu'aucun `google-services.json` n'est embarqué ;
- vérifier qu'aucune dépendance Firebase n'est résolue par Gradle ;
- vérifier qu'aucun secret ou jeton n'est présent dans l'APK ou le dépôt ;
- vérifier les permissions Android au principe du moindre privilège ;
- exécuter les tests automatisés et les contrôles CI ;
- vérifier la signature et le SHA-256 du build ;
- tester l'APK sur les versions Android réellement supportées.

## 9. Distribution

Les releases Sentinel peuvent être publiées via GitHub Releases ou le canal Android retenu pour Sentinel. Le pipeline de production doit rester indépendant de tout projet A KI PRI SA YÉ.

## Checklist finale

- [ ] Build reproductible
- [ ] Tests automatisés OK
- [ ] Scan des dépendances OK
- [ ] Scan des secrets OK
- [ ] Scan d'isolation Sentinel/A KI PRI SA YÉ OK
- [ ] Aucune dépendance Firebase
- [ ] Aucun `google-services.json` tiers
- [ ] APK signé
- [ ] SHA-256 vérifié
- [ ] Permissions minimales
- [ ] Release notes à jour
