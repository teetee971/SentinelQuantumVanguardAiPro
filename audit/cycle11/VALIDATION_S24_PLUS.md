# Validation Galaxy S24+ · Sentinel IA C11

**BLOCKED sur cet environnement.** L’inventaire ADB est vide. La copie `~/sentinel-cycle11-s24-validation` et Termux/proot du téléphone ne sont pas montés. Le signalement de sept scénarios Android réussis, d’une installation et d’un roundtrip concerne la copie de validation du propriétaire. Les logs et empreintes correspondants ne sont pas accessibles ici. Ces résultats ne sont pas attribués aux APK de cette page.

## Paire debug de qualification locale

Code de build `ea5f0a7d019b1113dd25810a6f325db789f38f39`. Les APK contrôlés sont disponibles dans le workspace cloud sous `/workspace/work/c11-evidence/apks`.

| Fichier | Identité | SHA-256 |
| --- | --- | --- |
| `sentinel-c11-arm64-debug.apk` | `com.sentinel.quantum.aivalidation.c11`, ARM64 | `87b9f213244ced7bcd633b75da265d142435a1061e695012b8d1bfc7a817174e` |
| `sentinel-c11-instrumentation.apk` | `com.sentinel.quantum.aivalidation.c11.test`, cible C11 avec `AndroidJUnitRunner` | `ed17e8797e1d2bd756f571582cb8950385a9a93955f9a3a358067bb0b1207213` |

Certificat debug SHA-256 commun `0d5e03ce9117817a0cfe55f2db1368f949218508abe34edf31a013cddde67275`. Signature v2 vérifiée. En cas de reconstruction avec un autre keystore debug, recalculer les deux empreintes et vérifier la signature commune. En présence d’une installation C11, comparer son certificat avant toute mise à jour. Préserver l’installation existante en cas de conflit.

| Parcours physique | Statut de cette mission |
| --- | --- |
| Installation et démarrage de la nouvelle paire | BLOCKED |
| Roundtrip de la nouvelle fixture et 20 écritures concurrentes | BLOCKED |
| Préparation, arrêt explicite et réouverture sur le même boot | BLOCKED |
| Nouvelle préparation, reboot S24+ et réouverture | BLOCKED |
| Révocation inter-activités, retour Keyguard et callbacks tardifs | BLOCKED |
| Inférence réelle Qwen ARM64, annulation et reprise | NOT RUN, environnement Termux et modèle approuvé indisponibles |
| Régression Phone Core / rôles / protections radio et réseau | NOT RUN sur S24+ |

## 1. Préparer et vérifier l’installation

Construire sur le SHA de code ou utiliser les deux APK vérifiés. La recette C11 ajoute l’identité du paquet et son libellé au build debug. Les répertoires et alias du paquet C9 restent hors de toutes les commandes suivantes.

```sh
cd native-android-app
bash gradlew -I ../audit/cycle11/validation/init.gradle :app:assembleDebug :app:assembleDebugAndroidTest
```

Utiliser un dossier neuf et privé par tentative. Réserver un S24+ avec débogage USB autorisé, deux lignes de test si les parcours Phone Core sont prévus et un verrouillage Android actif. Relever avant et après le modèle, l’ABI, la version, le niveau de correctif, les rôles Téléphone, SMS et filtrage d’appels et l’état VPN. Exécuter les commandes sur le serial de ce seul appareil.

```sh
pkg=com.sentinel.quantum.aivalidation.c11
runner="$pkg.test/androidx.test.runner.AndroidJUnitRunner"
adb -s "$serial" shell getprop ro.product.model
adb -s "$serial" shell getprop ro.build.version.security_patch
adb -s "$serial" shell 'for role in DIALER SMS CALL_SCREENING; do cmd role get-role-holders android.app.role.$role; done'
adb -s "$serial" shell settings get secure always_on_vpn_app
sha256sum -c SHA256SUMS
apksigner verify --verbose --print-certs sentinel-c11-arm64-debug.apk
apksigner verify --verbose --print-certs sentinel-c11-instrumentation.apk
adb -s "$serial" install -r sentinel-c11-arm64-debug.apk
adb -s "$serial" install -t -r sentinel-c11-instrumentation.apk
adb -s "$serial" shell pm path "$pkg"
adb -s "$serial" shell pm path "$pkg.test"
adb -s "$serial" shell am instrument -w -e package com.sentinel.quantum.ai "$runner"
```

Conserver stdout, code retour et logcat filtré de chaque essai. Le test inter-activités doit exécuter `processRevocationBlocksBothActivitiesUntilDrainedAndRequiresNewConsent` dans le paquet C11. Il utilise un effet synthétique et appelle le gestionnaire de la première activité pendant que la seconde porte le bail. Il couvre le refus de l’ancien bail et les contrôles UI. La confirmation en deux fenêtres et le vrai retour système ont leurs scénarios propres.

## 2. Qualifier séparément arrêt et reboot

Depuis la racine, lancer le script avec l’identifiant ADB du S24+ et des dossiers différents. Le mode `reboot` redémarre le téléphone désigné. Le propriétaire doit pouvoir le déverrouiller quand il revient en ligne.

```sh
bash audit/cycle11/validation/qualify_restart.sh "$serial" process "$evidence/process"
bash audit/cycle11/validation/qualify_restart.sh "$serial" reboot "$evidence/reboot"
```

Le reçu de préparation contient le PID, le démarrage kernel, le compteur de boots, le mode, l’UUID et la séquence 1. Le script doit constater le même PID actif avant la commande. La préparation est volontairement interrompue par `force-stop` ou par le reboot après le reçu. La réussite de la phase `verify` exige la séquence 2 et le drapeau de preuve propre au mode. Le mode processus exige le même boot, même si le PID a été réutilisé. Le mode reboot exige un compteur supérieur. Une sortie manquante, une installation différente, l’arrêt prématuré de l’instrumentation ou une expiration laissent le contrôle ouvert. Les fixtures persistantes et le dossier de preuve restent disponibles.

## 3. Termux, Qwen, consentement et refus

Dans le Termux/proot du propriétaire, relever le SHA du service `backend/local-ai/service.py`, du binaire ARM64 et du GGUF approuvé, puis démarrer le service de qualification sur `127.0.0.1:18080` avec un fichier d’appariement neuf et privé. Ne conserver dans le dossier de preuve que les empreintes, statuts et résultats synthétiques. Le secret est saisi directement par le propriétaire dans le paquet isolé.

1. Tester une inférence mathématique synthétique et relever la sortie authentifiée avec `action=NONE`. Répéter avec une demande de checkpoint. Vérifier que la suggestion seule ne crée aucun effet.
2. Utiliser `SentinelAiActionJournalEvidenceTest` avec `sentinel_ai_action_phase=baseline`. Conserver `cycle11_baseline_sequence` et `cycle11_baseline_digest` avant chaque fenêtre. Vérifier la fenêtre avec `sentinel_ai_action_phase=verify`, `sentinel_ai_start_sequence`, `sentinel_ai_start_digest` et `sentinel_ai_expected_checkpoints`.
3. Attendre zéro checkpoint pour l’absence d’opt-in, le refus Keyguard, l’annulation, l’expiration d’une proposition et le retour d’un ancien résultat après révocation d’une autre activité. Vérifier l’état des commandes après retour au premier plan. Exercer la révocation pendant une inférence en vol avec observation, puis contrôler l’absence de résultat tardif réutilisable.
4. Refaire avec nouvel opt-in et authentification Keyguard réelle. Observer exactement un `CHECKPOINT` précédé de `AUTHENTICATED`. Vérifier qu’une répétition de callback ou une fermeture de session ne produit aucun second checkpoint.
5. Sur une fixture distincte avec clé AndroidKeyStore, vérifier continuité, séquences 1 à 20, altération de chaîne et altération d’ancre. Un test de fixture ne prouve pas le comportement de l’activité quand son propre journal est indisponible. Cette panne exige une épreuve séparée sur le paquet C11, avec des données jetables.
6. Vérifier l’annulation du processus Qwen et la récupération du créneau avec le service de qualification. Comparer seulement l’empreinte du fichier chiffré d’appariement avant et après la révocation globale. Réserver « Oublier » à un secret jetable. L’ancien journal C9 et ses alias restent hors du parcours.

Une preuve physique doit relier le serial, les empreintes APK et modèle, les deux phases de persistance et les fenêtres d’action au même ensemble de qualification. [Journal des contrôles locaux](C11_LOCAL_EVIDENCE.log)
