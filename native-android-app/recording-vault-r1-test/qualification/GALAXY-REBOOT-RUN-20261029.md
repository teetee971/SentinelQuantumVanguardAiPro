# Reprise après redémarrage Galaxy — résultat transmis par l'utilisateur

## Provenance

La préparation et la reprise ont été exécutées par l'utilisateur dans Termux
sur son Galaxy, puis communiquées par captures d'écran. Codex les a examinées ;
aucune commande ADB de cette campagne n'a été exécutée directement par Codex.
Les fichiers bruts de cette campagne ne sont pas encore reçus.

La campagne est distincte du premier run c48883da20ff488ba71a09f7b2a57c95 déjà
nettoyé. Elle réutilise les APK de migration construits et installés précédemment,
sans réinstallation ni suppression des données applicatives.

## Séquence observée

- Répertoire de preuves Termux : `$HOME/vault-reboot-published`.
- Run : **5c2171ab39644dd8b4f7cb8936760200**.
- Préparation : setup vérifié, puis **MIGRATION_READY point=published** pour ce run.
- Le processus a été force-stoppé après publication du V2, avant sa dernière
  synchronisation explicite ; le message PRET POUR REDEMARRAGE a été observé.
- Après redémarrage et déverrouillage, connexion ADB rétablie dans Termux.
- Compteur global de démarrage lu avant : **281** ; après : **283**.
- La condition `after > before` a réussi : un changement de démarrage est prouvé.
  La capture ne décrit pas séparément chaque incrément du compteur.
- Phase verify : **VERIFIED 1 executed tests**.
- Phase cleanup : **VERIFIED 1 executed tests**, lancée seulement après verify.
- Résultat affiché :

```text
Compteur avant=281 après=283
VERIFIED 1 executed tests
VERIFIED 1 executed tests
REBOOT_VERIFIED run=5c2171ab39644dd8b4f7cb8936760200 boot_before=281 boot_after=283
```

Ce bloc est une transcription des lignes visibles, pas un fichier log brut
récupéré et réexécuté par Codex. L'adresse ADB privée n'est pas conservée ici.

## Ce qui est vérifié par la phase existante

RecordingVaultMigrationPersistenceInstrumentedTest#migrationAcrossProcessDeath
reprend le même run au point published et vérifie :

- l'identité du nouveau processus par rapport aux processus précédents ;
- la reprise de migrate et le déchiffrement du contenu attendu ;
- l'empreinte du V1 conservé, comparée à celle enregistrée avant interruption ;
- l'idempotence : une seconde migration ne réécrit pas le V2 validé ;
- le nettoyage des seules fixtures de ce run après réussite.

Le succès montre la récupération observée des données et l'utilisabilité de la
clé pour ces données après redémarrage normal. Les deux sorties VERIFIED sont
deux phases du même test, pas deux nouveaux cas indépendants de la suite de 38.

## Portée et limites

**Réserve de redémarrage normal levée sur la base des captures utilisateur.**
La campagne testée est la variante fournie dans la conversation : compteur de
boot, reprise puis cleanup. Elle n'incluait pas la comparaison de fichiers
fingerprint avant/après ajoutée dans la procédure de référence du dépôt ; ce
contrôle supplémentaire n'est donc pas déclaré exécuté.

Aucune preuve de résistance à une coupure électrique brutale, de disque Android
réellement plein ou d'anti-rollback absolu n'en est déduite. Aucun nouveau niveau
TEE/StrongBox n'est mesuré par ce test : le niveau TEE précédemment rapporté
reste celui documenté dans GALAXY-MIGRATION-RUN-20261029.md.

La réception des fichiers setup.log, interrupted.log, verify.log, cleanup.log,
run.txt, boot-before.txt, boot-after.txt et result.txt reste nécessaire pour une
revue indépendante de l'archive brute. Ils sont dans vault-reboot-published ;
le cleanup applicatif ne supprime pas ce répertoire de preuves Termux.

Les six alertes Dependabot restent NON VÉRIFIÉES (HTTP403). Aucun changement de
code, dépendance, branche source, main ou état de fusion n'est lié à ce constat.
