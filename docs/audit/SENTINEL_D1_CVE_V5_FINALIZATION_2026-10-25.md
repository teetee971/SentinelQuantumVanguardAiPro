# Audit finalisation Sentinel D1 + CVE V5 — 2026-10-25

## Périmètre et méthode

- Dépôt : `teetee971/SentinelQuantumVanguardAiPro`
- Branche auditée : `integration/sentinel-d1-cve-v5-20261025`
- Commit de référence : `760f57ecc8e2582f48ef251ef4d1c9386a87ab9e`
- Aucun workflow GitHub Actions n’a été lancé. La validation Android cloud prévue est Codemagic ; la validation locale demandée est Termux.
- Les tests matériels, modem, opérateur, BLE radio et émulateur ne sont pas simulés par des tests JVM.

## Matrice fonctionnelle

| Module / capacité | Statut | Preuve disponible | Blocage ou limite de livraison |
|---|---|---|---|
| Phone Core / rôles Android / états | PARTIAL | Contrats Android et tests de vérité produit présents ; schéma corrigé à v5 et 14 critères | Validation réelle multi-OEM, rôles, appels et réversibilité encore requise |
| SMS | PARTIAL | Politiques `ROLE_SMS`, permissions, callbacks et tests unitaires présents | Réception/émission opérateur, multi-SIM, redémarrage et révocation à tester sur Galaxy S24+ |
| MMS | PARTIAL | Décodeur WSP borné, quarantaine et tests adversariaux présents | Compatibilité PDU/opérateur, pièces jointes et envoi réel non validés physiquement |
| Contacts / Caller ID | PARTIAL | Policies d’accès explicite et adaptateur d’intelligence testés | Résolution provider et UX sur appareil réel non testées |
| VPN WireGuard | PARTIAL | `VpnService`, `GoBackend`, catalogue fail-closed et tests présents | Aucune passerelle Sentinel de sortie provisionnée ; DNS, routage, consentement et coupure restent à tester |
| BLE Mesh GATT bidirectionnel | PARTIAL | Codec, fragmentation, réassemblage, stop-and-wait ACK, chiffrement et session authentifiée couverts côté JVM | Aucun test radio GATT Android deux-pairs ; MTU, notifications CCCD, pertes/retries et interop constructeur restent ouverts |
| Wi-Fi | PARTIAL | Scanner, permissions bornées et vérité de résultat testés | Scan réel Android/OEM, localisation et restrictions constructeur non validés |
| Sécurité / permissions / secrets | PASS (périmètre local) | `test:credential-hygiene` : 6/6 ; contrats de permissions et états fail-closed présents | Le PASS ne vaut pas validation matérielle ni audit indépendant de production |
| CVE V5 | PASS | Suite ciblée : 32/32 ; archivage durable, ACK, anti-rejeu, limites et vue publique textuelle | Applicabilité aux six alertes GitHub non déterminable sans accès Dependabot autorisé |
| Interface D1 | PARTIAL | Contrats de source/UI et états explicites présents | APK installé et parcours visuel sur émulateur/S24+ non prouvés dans cette session |
| Assistant de premier lancement | PARTIAL | Store de setup, activation et permissions explicites présents | Parcours complet, refus/révocation et reprise après redémarrage à exécuter sur appareil |
| Codemagic | FAIL avant correction / PARTIAL après correction locale | Les workflows existaient mais refusaient la branche cible ; correction dans `codemagic.yaml` | Aucun build Codemagic n’a été exécuté depuis cette session ; groupe de signature doit exister dans Codemagic |

## Blocages réels

1. La qualification Gradle locale est bloquée avant exécution : le wrapper Gradle 9.8.0 n’est pas en cache et le téléchargement depuis `services.gradle.org` échoue par connexion refusée.
2. Le build Codemagic original rejetait la branche de consolidation dans ses allowlists. Les workflows debug, release unsigned et signed préparé acceptent désormais explicitement la branche cible.
3. Le build signé ne peut être qualifié ici : aucun secret ou groupe de signature Codemagic n’est disponible dans l’environnement. Le workflow attend le groupe existant `sentinel-android-signing` et les variables sécurisées `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, sans les imprimer.
4. Les six alertes Dependabot annoncées sur la branche par défaut ne sont pas lisibles avec l’accès courant : l’API GitHub répond `403 Resource not accessible by integration`. `npm audit --package-lock-only --omit=dev` rapporte 0 vulnérabilité de production, mais ce résultat ne remplace pas l’inventaire Dependabot/Gradle.
5. Les tests matériels et d’émulateur ne sont pas une preuve disponible : l’AndroidTest APK peut être compilé par Codemagic, mais il doit être exécuté sur un émulateur avant toute déclaration de validation D1.

## Corrections appliquées

- Rétablissement de `CERTIFICATION_SCHEMA_VERSION = 5`, cohérent avec le contrat Phone Core, la documentation et le gate d’émulation existants.
- Correction du contrat de test VPN pour vérifier le recours à `R.string.vpn_d1_client_integrated`, sans rendre l’interface dépendante d’un littéral non resource-backed.
- `codemagic.yaml` : ajout du lint Debug, autorisation de la branche de consolidation, qualification AAB unsigned conservée, et workflow signed préparé via groupe Codemagic sécurisé.

## Validation à exécuter ensuite

### Termux / local

```sh
cd native-android-app
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:bundleReleaseUnsigned
```

Les preuves doivent inclure les rapports JUnit, le rapport lint, l’APK debug, l’AAB unsigned et les SHA-256. L’AndroidTest APK compilé n’est pas une exécution d’émulateur.

### Codemagic

- `sentinel-d1-debug` : compile Kotlin, tests JVM, lint, AndroidTest APK et APK debug.
- `sentinel-d1-release-unsigned` : manifeste fusionné, gate de sécurité et AAB unsigned.
- `sentinel-d1-release-signed` : tests/lint, APK+AAB signés par le keystore déjà configuré dans le groupe Codemagic, vérification `apksigner`/`jarsigner`, sans secret dans Git ou les logs.

### Galaxy S24+

Tester ultérieurement : attribution et révocation des rôles Téléphone/Call Screening/SMS, appels entrant/sortant, filtrage, contacts, SMS simple et multi-SIM, MMS entrant/sortant, notification, redémarrage, permissions refusées, VPN avec gateway réelle, Wi-Fi scan et deux appareils BLE GATT avec pertes, fragments hors ordre, ACK retardés, doublons, rupture CCCD et reconnexion.

## Risques résiduels

- Le transport BLE est cryptographiquement et logiquement borné côté JVM, mais la pile Android radio et les contraintes OEM peuvent encore introduire des écarts.
- Le client VPN ne doit pas être présenté comme service VPN opérationnel sans gateway disponible et preuve de tunnel réel.
- Les versions de dépendances Android très récentes ne peuvent être déclarées sûres pour les six alertes GitHub sans les identifiants et chemins d’applicabilité exacts ; toute mise à jour doit être faite par lot minimal avec tests de régression.
- Aucun merge automatique n’est effectué.

## Preuves complémentaires — 2026-10-25

- `npm run test:syntax` : PASS, aucune erreur `node --check`.
- `npm run test:security-governance` : PASS, 218/218 tests ; les sorties de test contenant un APK synthétique sont des tests de garde et ne constituent pas un APK Android compilé.
- Suite Android vérité produit : PASS, 69/69 tests.
- Suite CVE V5 ciblée : PASS, 32/32 tests.
- Suite Mesh Zero Trust : PASS, 205/205 tests.
- Hygiène des identifiants/secrets : PASS, 6/6 tests.
- `./gradlew :app:testDebugUnitTest` : NOT_TESTED côté compilation ; le wrapper n’a pas pu télécharger Gradle 9.8.0 (`services.gradle.org` connexion refusée).
