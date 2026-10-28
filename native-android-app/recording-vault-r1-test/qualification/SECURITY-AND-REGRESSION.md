# Recording Vault R3 — sécurité et non-régression

Date : 2026-10-28. **Qualification de production NON acquise.**
Base : `7b4b84363feac5c691dd9861bd044be7ca34acfc`.
Branche : `feature/sentinel-recording-vault-r3-persistence-20261028`.
Périmètre des changements : uniquement `native-android-app/recording-vault-r1-test/`.

## Résultats observés

| Vérification | Résultat réellement observé | Preuve |
|---|---|---|
| Reproduction JVM initiale sur Store/Crypto R2 | 7 scénarios réussis, 2 échecs attendus | `logs/host-baseline.log` |
| Reproduction JVM étendue sur sources du commit de base | 8 réussis, 4 échecs attendus, dont deux exigences du nouveau verrou | `logs/host-baseline-expanded.log` |
| JVM/Linux finale, classes Kotlin réelles, adaptateurs Android simulés | 12 scénarios réussis, 0 échec | `logs/host-final.log` |
| Script ADB, simulations négatives sur hôte | 4 tests réussis : rejet des faux succès, tests ignorés, compte incomplet et erreur ADB | `logs/device-runner-host-tests.log` |
| Compilation Android APK debug + APK instrumentation | Réussie avec AGP 9.4.1, Gradle 9.8.0, SDK 37.0, build-tools 36.0.0, JDK 21.0.13 | `logs/android-final.log`, `logs/apk-sha256.txt` |
| Lint debug | 0 erreur, 2 avertissements hérités (target API 36 et icône absente) | `logs/lint-final.txt` |
| `testDebugUnitTest` | `NO-SOURCE` : aucun test exécuté par cette tâche | `logs/android-final.log` |
| Instrumentation Android connectée | BLOQUÉE : aucun appareil connecté ; aucun test exécuté | `logs/adb-devices.log`, `logs/android-connected.log` |
| Galaxy S24+ / Android 16, persistance, clé matérielle et concurrence Android | NON EXÉCUTÉS | Procédure `GALAXY-S24-VALIDATION.md` |

Un scénario JVM réussi caractérise volontairement une faiblesse : **la substitution de chiffrés valides est acceptée**. Son résultat est une preuve de la limitation, pas une validation de sécurité.
Les adaptateurs de `qualification/host/` ne sont jamais intégrés aux APK. Le fournisseur de clé est un double de test en mémoire, avec une clé fixe initiale connue pour les tests multiprocessus. AES/GCM vient du fournisseur JVM réel. Aucune preuve AndroidKeyStore, TEE ou StrongBox ne se déduit de ces tests.

## Constats et correctifs

1. **Publication écrasant une entrée concurrente.** R2 vérifie `exists()` puis `renameTo()`. Le test crée un lien pendant l'accès à la clé : un lien pendant vers un chemin absent échappe à `exists()` et se fait remplacer. Reproduction déterministe en échec avant correction, réussie après. R3 ouvre le temporaire par `CREATE_NEW` sans suivre le lien final, écrit et force son contenu, puis publie avec `Files.createLink`. Cette opération atomique refuse une entrée déjà existante, lien pendant compris. Aucun repli vers un renommage écrasant en cas de système de fichiers incompatible.
2. **Déchiffrement créant une clé.** R2 appelle le même chemin « charger ou générer » pour lire et écrire. Après disparition de l'alias, une lecture génère une clé qui ne peut pas déchiffrer l'ancien contenu. Le test de fournisseur simulé démontre cet effet de bord. R3 exige une clé existante au déchiffrement, échoue sans en générer, et refuse de remplacer un alias existant d'un autre type. Le comportement sur Android réel après perte/invalidation de clé reste à qualifier sans supprimer la clé R1/R2 de l'utilisateur.
3. **Verrou limité à un processus.** `synchronized` ne coordonne pas les autres VM Android. R3 garde ce verrou local et acquiert un verrou de fichier permanent dans `noBackupFilesDir`, commun à tous les répertoires Store utilisant l'alias. Les opérations Store, y compris la première génération de clé, sont couvertes. Le test hôte démontre le blocage d'un second processus tant que le premier possède le verrou et son déblocage ensuite ; huit processus JVM donnent un seul écrivain gagnant. Le fichier de verrou ne doit jamais être supprimé/remplacé. Le test de lien sur le verrou contrôle une exigence nouvelle, pas une vulnérabilité d'un verrou R2 inexistant.
4. **Lecture non bornée après vérification du chemin.** R2 vérifie une taille puis appelle `readBytes()` sur le chemin rouvert. R3 ouvre une seule fois avec `NOFOLLOW_LINKS`, borne l'allocation depuis ce descripteur et lit exactement cette taille, puis vérifie EOF. Troncature/croissance détectées provoquent un échec ; le tag GCM valide le contenu avant retour. Les limites statiques sont testées. Une course hostile de croissance/troncature pendant la lecture n'a pas été orchestrée sur appareil.
5. **Minimum Android incohérent.** Le module annonçait API 24 alors que R2 utilisait déjà `java.nio.file` (API 26). Lint R3 a produit 40 erreurs `NewApi`. `minSdk` passe à 26 uniquement dans ce module ; aucune prétention de compatibilité API 24/25. Le Galaxy Android 16 est dans la plage. `compileSdk 37`, `targetSdk 36` restent inchangés.

## Persistance et preuves prévues sur appareil

Le protocole exécute 21 tests ordinaires (16 existants R1/R2 et 5 nouveaux), puis une instrumentation distincte pour chaque phase :

- `write` conserve un enregistrement, un défi chiffré indépendant, leur empreinte/metadata et l'identité PID + ticks de démarrage du processus.
- ADB force l'arrêt et vérifie la disparition du processus principal.
- `stageInterruptedWrite` ouvre et synchronise quatre octets dans un `.part`, sauvegarde l'identité du deuxième processus et signale sa disponibilité. L'hôte le force à s'arrêter avec le flux encore ouvert. **C'est une injection déterministe à la phase temporaire, pas une interruption observée à l'intérieur de `Store.save`.** Cette instrumentation interrompue ne compte jamais comme PASS.
- `verify` exige une troisième identité de processus, vérifie le chiffré inchangé, déchiffre l'ancien défi et l'enregistrement, conserve le résidu et effectue une nouvelle sauvegarde. Le nom d'alias ou sa date seuls ne sont pas considérés comme preuve de continuité de clé.
- `cleanup` supprime uniquement les fichiers de cette exécution, jamais l'alias ni le verrou.

La course Android utilise deux services **debug uniquement, non exportés**, avec deux `android:process` différents et une barrière avant `save`. Elle vérifie les PID, un seul succès, un rejet avec un gagnant lisible, et le contenu du gagnant. L'exception du perdant reste diagnostiquable ; un rejet n'est pas présenté comme une preuve de sa cause exacte. La suite provisionne la clé avant cette course : **la création concurrente de la toute première clé n'est pas qualifiée par cette suite**.

## Préservation R1/R2 et périmètre

- Les six classes de tests R1/R2 du commit de base sont inchangées. Elles sont compilées dans l'APK d'instrumentation, mais n'ont pas été réexécutées sur appareil ici.
- Alias `sentinel_recording_vault_r1`, répertoire `recording-vault-r2`, extension `.rv` et enveloppe `version 1 | IV 12 octets | AES-256-GCM ciphertext/tag 128 bits` conservés. Aucun chiffrement en clair sur disque par le Store.
- Taille autorisée Store : 1 à 16 MiB de clair ; 30 à 16 MiB + 29 octets pour un fichier. Fichiers de 0, 1, 12, 28, 29 octets et taille maximale + 1 rejetés ; corruption version/IV/ciphertext/tag et troncature testées.
- Aucun changement Phone Core, SMS, MMS, VPN, BLE, module principal, schémas Room, réglages Gradle parents ou intégration de l'application principale. Les réglages de qualification isolent le build de ce seul APK.
- Aucun effacement de données existantes, aucune suppression de clé, aucune fusion. La PR vise la branche R1/R2 portant exactement le commit demandé, afin que son diff ne réintroduise pas les changements historiques de cette branche vers `main`.
- **Aucune régression observée dans les vérifications exécutées** ; non-régression Android physique non établie. Aucune conclusion sur l'enregistrement audio, encore moins sur les deux côtés d'un appel.

## Blocages précis avant production

1. Exécuter et archiver les 21 tests et toutes les phases sur Galaxy S24+ Android 16, avec modèle, build fingerprint, API, empreintes APK, identités des processus et journal KeyInfo. Répéter après redémarrage du téléphone ; tester état verrouillé/déverrouillé. Aucun résultat physique disponible ici.
2. **Intégrité du contexte et anti-rollback absents du format v1.** Un autre chiffré valide du même alias peut remplacer un fichier ; restaurer une ancienne version valide n'est pas détecté. Une évolution versionnée avec AAD (identifiant/contexte) et une politique anti-rollback nécessitent une conception/migration séparée. Compatibilité R1/R2 priorisée dans ce correctif.
3. **Adversaire du même UID/root non couvert.** Vérifications du répertoire, chemins temporaires et fichier de verrou restent sensibles au remplacement d'ancêtres/inodes entre opérations. `NOFOLLOW_LINKS` protège le composant final à l'ouverture, pas toute la résolution. Remplacement par FIFO/fichier spécial ou détournement du verrou peut bloquer ou détourner des opérations. Le confinement UID Android et la coopération des écrivains sont des hypothèses, pas une défense contre du code compromis dans l'application.
4. **Coordination limitée aux participants R3 Store.** Les appels directs concurrents à `RecordingVaultCrypto.encrypt`, un ancien processus R2 ou du code contournant le verrou peuvent encore concourir sur la génération de clé. Qualifier la première création sur une installation/profil de test vierge, sans effacer les validations R1/R2 existantes. La nouvelle suite ne supprime jamais l'alias pour simuler cette situation.
5. **Durabilité coupure électrique non garantie.** Le contenu temporaire est forcé avant publication, mais les entrées du répertoire ne sont pas `fsync` après création/lien/suppression. Force-stop processus n'équivaut pas à coupure d'alimentation. Il faut qualifier ext4/F2FS, fsync de répertoire et erreurs après publication ; un échec de nettoyage peut laisser un enregistrement publié alors que l'appel remonte une erreur (résultat ambigu à réconcilier).
6. **Interruption réelle du chemin de sauvegarde à qualifier.** Le scénario déterministe couvre un temporaire partiel abandonné. Kill pendant la vraie écriture, après publication/avant suppression du temporaire, disque plein/ENOSPC et fautes d'E/S n'ont pas été exécutés. Il faut établir l'état ancien/nouveau/absent à chaque point de coupure.
7. **Pas de quota global ni purge de résidus.** Limite de 16 MiB par objet, mais accumulation illimitée des `.rv` et `.part`. Résidus conservés conformément à R2 pour ne pas supprimer une écriture active. Suppression logique sans garantie d'effacement sécurisé ; un résidu lié peut conserver le chiffré après suppression du nom final. Ajouter une politique de récupération/quota/retention avant exposition produit.
8. **Politique de clé non qualifiée.** Pas de demande explicite StrongBox ni d'authentification utilisateur par utilisation. R1 rapporte le niveau réel sans l'imposer. Décider les exigences TEE/StrongBox, verrouillage, invalidation, perte de clé, mise à jour/signature, désinstallation et restauration. Clé non exportable attendue du fournisseur AndroidKeyStore mais non prouvée ici.
9. **Budgets et disponibilité.** Chiffrement/déchiffrement intégral en mémoire, copies multiples jusqu'à 16 MiB et verrou global bloquant. Mesurer mémoire, latence, ANR si appel sur thread UI, annulation et contentions réelles. Les tailles brutes de Crypto ne sont pas plafonnées par le Store si Crypto est appelé directement.

## Revue

Une revue indépendante en lecture seule a identifié le minimum API incohérent et le risque de tester un APK périmé. Corrections : minimum 26, empreintes systématiques, installation `-r -t` obligatoire avant protocole. La revue n'a exécuté aucun test et ne vaut pas qualification physique. Elle a confirmé la nécessité de distinguer concurrence de publication et toute première génération de clé.
