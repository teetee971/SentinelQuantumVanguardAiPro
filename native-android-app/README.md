# Sentinel Quantum Vanguard — Native Android App

## Engagement produit gratuit

La recherche de numéros, les listes personnelles, l’analyse locale de SMS et le filtrage d’appels constituent le socle gratuit de l’application Android. Les futurs add-ons professionnels (veille avancée, enquêtes, gouvernance d’organisation, exports et connecteurs) doivent rester séparés de ce socle.

Le site public présente l’application mais ne distribue actuellement aucun APK. La première mise à disposition devra être liée à un artefact signé, un checksum, une CI réussie sur le SHA exact et un test sur appareil réel.

Application Android native en Kotlin avec Jetpack Compose pour la consultation de flux OSINT publics.

## Caractéristiques

- Kotlin + Jetpack Compose
- Accès sans authentification
- Lecture seule des sources OSINT publiques
- Sources : CERT-FR, ANSSI, CVE/NVD
- Interface sombre, sobre et institutionnelle
- Backend Wangiri/Caller Reputation optionnel pour l’enrichissement distant ; les chemins critiques locaux restent indépendants du cloud
- Vérification manuelle locale de numéros avec validation bornée et statistiques de session
- Filtrage Android via le rôle système Call Screening : blocage par règles utilisateur et silencieux uniquement pour les préfixes issus d'un paquet signé, frais et anti-rollback
- Analyse locale bornée d'un email brut : en-têtes, Authentication-Results observé, domaines et liens
- Cache local hors-ligne des flux OSINT (bannière « données locales du … »), recherche texte et filtre par source, marquage lu/non lu — tout est stocké localement, sans analytique
- Journal local : recherche par mot-clé/tag et filtre par niveau, export sanitisé partageable via le sélecteur de partage Android (les données exportées passent par le même filtrage anti-secrets que le journal affiché)
- Analyse d'un email partagé depuis une autre application (feuille de partage Android, `ACTION_SEND` texte brut) directement vers l'analyseur local, sans nouvelle permission
- Vérification manuelle et optionnelle de mises à jour de vigilance signées pour le filtrage d'appels (interface prête, désactivée par défaut tant qu'aucun émetteur/clé de production n'est provisionné)
- Client WireGuard Android intégré derrière `VpnService` avec états fail-closed ; aucune passerelle Sentinel de sortie n’étant encore déployée, le service VPN public n’est pas revendiqué comme opérationnel.
- Phone Core avec centre d’activation et de test : demande explicite des rôles Téléphone, Call Screening et SMS, vérification des permissions, accès au composeur Sentinel et à la messagerie de test.
- Composeur `ROLE_DIALER` + `InCallService` Sentinel pour appels entrants/sortants, réponse/refus/raccrochage, mise en attente et DTMF ; validation appareil physique encore requise.
- Client SMS par défaut testable avec `ROLE_SMS`, envoi `SmsManager`, réception `SMS_DELIVER`, conversations locales et multi-SIM. Le décodage complet des pièces jointes MMS reste en validation.

## Prérequis

- Android Studio compatible avec AGP 9.4
- JDK 17
- Android SDK Platform 37 pour la compilation
- Gradle 9.7.1 via le wrapper fourni
- Android 7.0 (API 24) minimum pour l'exécution

## Compatibilité du filtrage d’appels

L’application peut s’exécuter à partir d’Android 7.0 (API 24). Le composant système `CallScreeningService` existe à partir de cette API, mais le parcours guidé actuel de sélection du rôle repose sur `RoleManager.ROLE_CALL_SCREENING`, disponible à partir d’Android 10 (API 29). En conséquence, tant qu’un parcours legacy API 24–28 n’est pas implémenté et testé, Sentinel revendique l’activation guidée du filtrage d’appels uniquement à partir d’Android 10. Les autres fonctions locales restent soumises à leurs propres prérequis.

## Installation

1. Cloner le dépôt.
2. Ouvrir `native-android-app/` dans Android Studio.
3. Synchroniser les dépendances Gradle.
4. Lancer sur un émulateur ou un appareil physique.

## Build APK Debug

```bash
cd native-android-app
./gradlew assembleDebug
```

L'APK est généré dans `app/build/outputs/apk/debug/`.

## Build APK Release

```bash
./gradlew assembleRelease
```

La release de production doit être signée avec le certificat prévu pour la distribution. Les secrets de signature ne doivent jamais être stockés dans le dépôt.

## Structure du projet

```text
native-android-app/
├── app/
│   ├── src/main/
│   │   ├── java/com/sentinel/quantum/
│   │   │   ├── MainActivity.kt
│   │   │   ├── data/
│   │   │   ├── navigation/
│   │   │   └── ui/
│   │   ├── res/
│   │   └── AndroidManifest.xml
│   ├── build.gradle
│   └── proguard-rules.pro
├── gradle/
├── build.gradle
├── settings.gradle
└── gradle.properties
```

## Sécurité et permissions

L'application déclare actuellement :

- `INTERNET` pour récupérer les flux OSINT publics ;
- `ACCESS_NETWORK_STATE` pour connaître l'état de la connectivité ;
- `POST_NOTIFICATIONS` sur Android 13+ lorsque l'utilisateur active les notifications OSINT ;
- `READ_CONTACTS` uniquement après une action explicite de l'utilisateur pour enrichir localement la fiche Caller ID ;
- des permissions Wi-Fi/Bluetooth bornées pour les fonctions locales de scan ;
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` uniquement jusqu'à Android 12L (`maxSdkVersion=32`) lorsque la plateforme l'exige pour les résultats de scan Wi-Fi/BLE.

Les permissions `CALL_PHONE` et `READ_CALL_LOG` sont déclarées pour le mode composeur et restent conditionnées au rôle Téléphone et à une action explicite de l’utilisateur. `READ_PHONE_STATE` est utilisé pour détecter les lignes SIM lors d’un envoi SMS multi-SIM. Les permissions `READ_SMS`, `RECEIVE_SMS` et `SEND_SMS` restent conditionnées au rôle `ROLE_SMS`; l’application ne doit pas les utiliser comme messagerie par défaut tant qu’Android n’a pas effectivement attribué ce rôle. Aucune permission caméra ou microphone n’est demandée par Phone Core. Le service de filtrage d’appels fonctionne uniquement après attribution explicite du rôle Android `ROLE_CALL_SCREENING`; l'analyse email n'accède à aucune boîte mail.

Le manifeste interdit le trafic HTTP en clair (`usesCleartextTraffic=false`) et désactive la sauvegarde Android (`allowBackup=false`). Le build release active également R8/ProGuard.

Un `FileProvider` (`androidx.core.content.FileProvider`, non exporté) est déclaré uniquement pour l'export du journal de sécurité local, et n'expose que le sous-répertoire de cache dédié `sentinel_log_export/` (voir `res/xml/file_paths.xml`) ; aucun autre fichier de l'application n'est accessible par ce biais. Le cache OSINT hors-ligne et l'état lu/non lu sont stockés localement (SharedPreferences), sans transmission réseau ni analytique.

La normalisation est France-first : les formes nationales, `+33` et `0033`
sont rapprochées. Aucun préfixe de réputation non signé n'est activé par défaut.
L'infrastructure vérifie des paquets P-256 bornés, expirables et à séquence
croissante, mais aucune source commerciale ou communautaire n'est fournie.

## Dépendances principales

- AndroidX Core KTX
- AndroidX Lifecycle
- Activity Compose
- Jetpack Compose / Material 3
- Navigation Compose
- Rome Tools pour RSS/Atom
- OkHttp pour les flux HTTP
- WireGuard Android tunnel backend
- Kotlin Coroutines

Les versions sont maintenues dans `app/build.gradle` et alignées sur les versions Android/Compose actuellement retenues.

## Validation

Le workflow `.github/workflows/build-native-android.yml` constitue le build Android de validation. Le workflow `.github/workflows/android-release.yml` est réservé aux releases signées sur tags de version.

Une modification du code ou des dépendances ne vaut pas validation CI tant que le runner GitHub n'a pas effectivement exécuté les étapes et produit des résultats exploitables.

## Version actuelle

- Version code : 1
- Version nom : 1.0.0
- Package : `com.sentinel.quantum`
- `minSdk` : 24
- `targetSdk` : 36
- `compileSdk` : 37
