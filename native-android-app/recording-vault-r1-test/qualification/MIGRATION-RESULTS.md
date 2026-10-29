# Résultats réels de la mission — 2026-10-29 UTC

**Mise à jour : compilation et lint validés sur hôte et build ARM64 transmis ;
protocole physique Galaxy réussi selon le journal utilisateur (38 tests et
phases de reprise). Redémarrage complet et coupure électrique NON EXÉCUTÉS.**
Provenance détaillée : [GALAXY-MIGRATION-RUN-20261029.md](GALAXY-MIGRATION-RUN-20261029.md).

Branche : `fix/recording-vault-v2-migration-hardening-20261028`.
Base : `601f7baedc4e23337fa6b9a595c05c41b7466f53`.
Conception : commit `392e8054`.
Implémentation : `74c3392d3a19d72e9993afb88f63882a1e07eb0d`.
Les commits ultérieurs du rapport/protocole sont identifiés par l'historique Git
et la PR brouillon. Aucune fusion demandée ou effectuée.

## Vérifications finales exécutées

| Commande / contrôle | Résultat réel |
| --- | --- |
| run-host-tests.sh, Kotlin 2.2.21, Temurin JDK17.0.21 | code 0, **72 réussis, 0 échoué** |
| test-device-runner.py | code 0, **4 réussis, 0 échoué**, ADB simulé |
| test-instrumentation-verifier.py | code 0, **8 réussis, 0 échoué**, traces synthétiques |
| test-device-protocol.py | code 0, **3 réussis, 0 échoué** ; protocole complet contre ADB simulé, 27 identités fictives, interruptions et arrêt sur échec vérifiés |
| bash -n run-device.sh et run-host-tests.sh (invocations séparées) | codes 0 |
| git diff --check | code 0 ; contrôle des changements indexés également effectué avant commits |
| Comparaison du C JNI et du binaire ARM64 avec la base | code 0, inchangés |
| Gradle 9.8.0/JDK17, assembleDebug et assembleDebugAndroidTest | **code 0**, APK application et tests construits, Kotlin principal et instrumenté compilés |
| Reconstruction --offline --rerun-tasks, mêmes APK + lintDebug + testDebugUnitTest | **code 0**, 78 tâches exécutées ; testDebugUnitTest est **NO-SOURCE**, aucun test supplémentaire exécuté par cette tâche |
| lintDebug | **code 0 : 0 erreur, 2 avertissements** (OldTargetApi, MissingApplicationIcon) |
| apksigner verify pour les deux APK | codes 0 ; signatures debug cloud, compatibilité avec la clé Galaxy **non vérifiée** |
| API Dependabot | code 1, HTTP403 Resource not accessible by integration ; **NON VÉRIFIÉ** |
| Galaxy S24+ SM-S926B, API36, AndroidKeyStore réel, JNI ARM64 et force-stop | **EXÉCUTÉS PAR L’UTILISATEUR** : 38 tests vérifiés, phases write/verify/cleanup et 3 points de migration terminés ; transcription reçue, archive brute à collecter |
| Redémarrage complet Galaxy et coupure électrique | **NON EXÉCUTÉS** dans le protocole transmis |

Journal initial : [evidence-migration/commands.json](evidence-migration/commands.json).
Journal de poursuite et résultats Android : [evidence-migration-followup/commands.json](evidence-migration-followup/commands.json).
Les échecs initiaux sont conservés comme historique, pas comme état final du build.
Sorties complètes dans ce même répertoire. Les commandes contiennent les chemins
réels de l'environnement Codex ; les commandes portables pour le Galaxy figurent
dans [MIGRATION-QUALIFICATION.md](MIGRATION-QUALIFICATION.md).

Bilan Python actuel : **15 tests réussis**, dont les 3 tests du protocole complet
contre ADB simulé. Les identités fictives de ces simulations ne sont jamais
comptées comme tests Android réussis. La tentative de connexion ADB à l’adresse
LAN fournie a été refusée ; aucune association ou installation sur le téléphone
n’a eu lieu depuis Codex. L’utilisateur a ensuite exécuté le protocole localement
dans Termux avec succès selon la transcription reçue ; ce résultat est distinct
d’une connexion ADB du cloud.

Les tests hôte exécutent le vrai code Store/Crypto, AES-GCM JVM et le vrai JNI
Linux construit depuis le C inchangé. AndroidKeyStore est simulé. Les fautes
EIO/ENOSPC sont injectées, pas des mesures de disque Android plein. Les reprises
après arrêt JVM concernent trois frontières, dans un autre processus ; elles ne
prouvent pas la résistance à une coupure électrique du téléphone.

## Échecs intermédiaires conservés

- Le test de migration a d'abord échoué sur NoSuchMethodException (fonction
  absente), puis passé après implémentation : `host-red.txt`.
- La revue a trouvé deux défauts : candidat de nouveau save invisible avant
  marqueur, et enrôlement avant force du V2 canonique. Trois tests correspondants
  ont échoué, puis passé après correction : `review-red.txt`, `host.txt`.
- Une erreur de compilation de test (`public` exposant un type `internal`) a été
  corrigée par visibilité internal ; ce n'était pas une exécution de tests.
- La tentative unittest discover a exécuté **0 test**, code 5 : noms de fichiers
  avec tirets non découverts. Elle n'est pas comptée comme réussie ; remplacée
  par les deux invocations explicites ci-dessus (12 tests), documentées aussi
  pour l'utilisateur. La trace `protocol.txt` est conservée.
- Le wrapper Gradle initial ne pouvait pas télécharger sa distribution depuis
  Java. Gradle a ensuite été téléchargé avec curl dans /workspace/tooling, sans
  modification du dépôt ; le build offline atteint maintenant la résolution AGP,
  où il était alors bloqué. La poursuite a résolu AGP via le proxy avec son CA de
  confiance (TLS maintenu), installé le SDK officiel via sdkmanager dans
  /workspace/tooling et validé une reconstruction entièrement offline.
  Kotlin/JDK17 ont également été installés uniquement dans
  ce dossier d'outillage, sans changer les dépendances du projet.

## Revue et risques résiduels

Revue indépendante statique effectuée sur le code et le protocole. Corrections :
récupération authentifiée du seul candidat de nouveau save, force du V2 canonique
avant marqueur, validation du code terminal d'instrumentation, comparaison des
chemins Android canoniques et corruption du tag par XOR déterministe.

La migration ne supprime jamais la source V1. Elle ne déclare aucun succès si
une synchronisation requise échoue. Le marqueur est une intention persistante ;
un appel échoué peut avoir publié un artefact. La reprise valide et resynchronise
avant de retourner. Toute incohérence engagée refuse V1 et conserve les copies
pour diagnostic. Une suppression d'enregistrement ne se produit que via delete
explicitement appelé ; les tests physiques n'effacent que leurs fixtures uniques.

Les attributions historiques des V1 ne sont pas authentifiées. Une restauration
cohérente de TOUT le stockage (V1/V2 et marqueurs) peut contourner l'anti-rollback
local. Le stockage physique, les courses hostiles du même UID, la consommation
mémoire/latence et le comportement Android de fsync restent des limites décrites
par la conception. Aucun anti-rollback absolu n'est revendiqué.

Les APK ont été construits sur Linux x86_64, Gradle 9.8.0, JDK17.0.21 ; le C et
le binaire JNI ARM64 sont inchangés. La compilation n’exécute pas les tests
instrumentés. ADB a été installé : `adb devices -l` ne liste aucun appareil.
Le rapport des dépendances runtime de debugAndroidTest a été exporté, sans
changer de version ni conclure à l’impact des six alertes inaccessibles.

Le build offline ARM64 et le protocole Galaxy sont désormais documentés par la
transcription utilisateur : installations -r -t réussies, 38 tests et reprises
aux trois frontières validés. Les logs bruts et le niveau de sécurité de clé
restent à collecter ; redémarrage complet et vérifications de stockage physique
restent à effectuer. Aucun résultat de coupure électrique n’est revendiqué.
Un échec de signature, d'outil ARM64, de dépendance offline ou de fsync est un
blocage, jamais une raison de vider les données ou de déclarer PASS.

Les six alertes Dependabot restent **NON VÉRIFIÉES (HTTP403)**. Aucun changement
de dépendance, Room, application principale, transport ou autre module.

Liste exhaustive des fichiers livrés : [MIGRATION-FILES.md](MIGRATION-FILES.md).
