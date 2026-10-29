# Livraison finale — Recording Vault V2

**État : code, tests, documentation, preuves disponibles et PR brouillon livrés.
Qualification fonctionnelle Galaxy documentée, puis journaux bruts reçus et contrôlés dans Codex.
Réserves ouvertes ci-dessous ; aucune fusion ni qualification absolue de durabilité.**

## Périmètre livré

Branche exclusive : `fix/recording-vault-v2-migration-hardening-20261028`.
Base : `601f7baedc4e23337fa6b9a595c05c41b7466f53`.
PR brouillon : https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1745

Migration explicite V1 → V2, source V1 conservée, AES-GCM avec nouvel IV et AAD
liées au répertoire logique et à l'identifiant. Publication V2 distincte et
immuable avec marqueur authentifié, reprise après interruption, synchronisations
obligatoires et refus de tout repli silencieux après engagement V2.

Le JNI RENAME_NOREPLACE, son C et le binaire ARM64 d'origine restent inchangés.
Aucun changement de Room, dépendance, application principale ou autre module.
main et la branche source sont inchangées. Les seuls nettoyages Android du
protocole concernent ses fixtures uniques ; aucune purge des V1 existants.

## Bilan vérifiable

| Validation | Résultat et provenance |
| --- | --- |
| Tests Kotlin/JVM | **72 réussis, 0 échoué**, exécutés dans Codex ; AndroidKeyStore simulé, AES-GCM/JNI Linux réels |
| Tests Python | **15 réussis, 0 échoué**, exécutés dans Codex ; clients ADB simulés explicitement |
| Build Android hôte | APK principal et tests compilés ; reconstruction offline avec 78 tâches réexécutées réussie |
| Lint hôte | 0 erreur, 2 avertissements documentés |
| Build ARM64 Ubuntu proot | BUILD SUCCESSFUL, 76 tâches exécutées, JDK17.0.20/Gradle9.8 ; journal utilisateur |
| Suite Galaxy SM-S926B/API36 | **38 tests réellement exécutés et vérifiés**, dont 14 de migration ; journal utilisateur |
| Phases physiques complémentaires | 3 phases de persistance et 9 setup/verify/cleanup de migration validées ; pas 12 nouveaux cas distincts |
| Redémarrage normal après interruption published | **Validé sur journaux bruts reçus**, run 5c2171ab39644dd8b4f7cb8936760200, compteur 281 → 283, verify et cleanup réussis |
| Sessions interrompues | 4 dans le premier protocole et 1 avant redémarrage, **jamais comptées comme PASS** |
| Sécurité de clé | KeyInfo rapporte **TEE, 256 bits, USER_AUTH_REQUIRED=false** ; pas d'attestation indépendante |
| Audit public indépendant des dépendances | Six avis publics applicables à Vite 8.3.1 du frontend ; correctif 8.3.3 identifié. Aucun chemin Vite dans Recording Vault isolé. Correspondance aux alertes GitHub NON VÉRIFIÉE |
| Git | diff --check réussi ; code/protocole et JNI comparés aux références, sans changement après qualification |

La découverte de 38 tests n'est pas comptée comme exécution. Les sorties
NO-SOURCE de Gradle ne comptent pas comme tests exécutés. Les échecs initiaux
puis corrections sont conservés dans les preuves, sans masquer leur historique.
Les contrôles GitHub Cloudflare/GitGuardian observés ne constituent pas une CI
Android ni une analyse des six alertes Dependabot.

## Références et SHA

| Élément | SHA / document |
| --- | --- |
| Conception initiale | `392e8054fd9146f95f14cca90f82477537c06e3b` |
| Implémentation | `74c3392d3a19d72e9993afb88f63882a1e07eb0d` |
| Protocole initial et preuves | `29491eed87e8754580fae2d8850f3e3b22bfc676` |
| Code annoncé et confirmé pour le run Galaxy | `eea40d423a616e845da5e4295b6e9cdb76672c32` |
| Documentation du run physique | `2a6b5112d38c574402444945e57a53551727276d` |
| Documentation TEE et empreinte d'archive | `5f998d33e1cccf2bb60c48ce274a3c4507921752` |

Les commits suivants finalisent uniquement les documents ; le HEAD exact est
visible dans la PR et dans `git log`. Aucun nouveau build du téléphone n'est
nécessaire pour un changement documentaire seul.

- [Conception](../../../docs/superpowers/specs/2026-10-29-recording-vault-migration-design.md)
- [Résultats et journal réel](MIGRATION-RESULTS.md)
- [Compte rendu physique, APK et provenance](GALAXY-MIGRATION-RUN-20261029.md)
- [Commandes initiales Termux/proot](MIGRATION-QUALIFICATION.md)
- [Résultat de redémarrage transmis](GALAXY-REBOOT-RUN-20261029.md)
- [Procédure de référence de redémarrage](REBOOT-QUALIFICATION.md)
- [Audit public des six avis Vite et limites du rapprochement Dependabot](DEPENDENCY-AUDIT.md)
- [Liste exhaustive des fichiers](MIGRATION-FILES.md)
- [Commandes hôte initiales](evidence-migration/commands.json)
- [Commandes de poursuite](evidence-migration-followup/commands.json)

## Réserves ouvertes à la livraison

| Réserve | État exact | Condition pour lever la réserve |
| --- | --- | --- |
| Archives Galaxy brutes | **Réserve levée** : archives reçues, deux SHA-256 recalculés, identités exactes et journaux contrôlés | Contrôle effectué le 2026-10-29 |
| Coupure électrique / vrai disque Android plein | **NON EXÉCUTÉS** | Campagne isolée appropriée ; ne pas provoquer de perte sur le téléphone personnel |
| Six alertes Dependabot | **NON VÉRIFIÉES, HTTP403** | Accès en lecture ou export des alertes avec GHSA, paquets, versions et manifests ; impact module indéterminé |

Archive annoncée : `vault-migration-c48883da.tar.gz`.
SHA-256 communiqué :
`01d1799265059ccbc02a581c612896f18fbb2a7aaf9799d8c5a855273fee03dd`.
Cette empreinte a été **recalculée et vérifiée dans Codex** après réception.
Un audit public indépendant a examiné six avis Vite de même profil (1 élevé,
4 modérés, 1 faible) ; il ne remplace pas l’accès aux alertes propres au dépôt.
Pour ces six avis publics, le module isolé examiné n’invoque ni ne dépend de Vite.
Le frontend reste à corriger dans un travail séparé autorisé, sans changement
de dépendance pendant cette mission.

La réserve du redémarrage normal est levée ; les journaux bruts reçus confirment les captures utilisateur.
Les réserves encore listées ne sont ni déclarées réussies, ni abandonnées,
ni contournées.

## Limites de sécurité permanentes

Une migration ne peut réparer l'absence d'attribution authentifiée des anciens
V1 avant leur conversion. Un attaquant restaurant de façon cohérente TOUT le
stockage peut restaurer les marqueurs locaux : aucun anti-rollback absolu.
Les courses hostiles du même UID dépassent les garanties du verrou coopératif.
Les succès de fsync observés ne prouvent pas le comportement du matériel lors
d'une coupure électrique. TEE rapporté par KeyInfo ne signifie pas StrongBox
ni attestation indépendante ; la clé n'exige pas d'authentification utilisateur.

La PR reste en brouillon et n'est pas fusionnée. La livraison du code est
finalisée avec ces réserves de qualification explicitement signalées.

## Contrôle indépendant des archives reçues — 2026-10-29

Archive finale `vault-final-evidence.tar.gz`, SHA-256 recalculé :
`877239822fb59d17da13449b09c89fcdc20fbb63fc5e0266e75708077378f8c8`.
L’archive interne correspond également à son empreinte annoncée ci-dessus.
Entrées contrôlées sans extraction globale : aucun lien ni chemin absolu ou traversant.

Le vérificateur du dépôt confirme les 38 identités exécutées, identiques à la
découverte et à expected.json, les 3 phases de persistance et les 9 phases de
migration. Les quatre sessions interrompues ne satisfont pas le vérificateur
de succès et restent exclues des PASS. Le commit déclaré, les empreintes APK,
le modèle/API et les trois points MIGRATION_READY concordent avec le rapport.

Les journaux de reboot confirment setup/verify/cleanup, le même run publié,
le compteur 281 → 283 et result.txt. L’exécution physique reste celle de
l’utilisateur ; Codex a contrôlé directement les fichiers transmis. Les
archives brutes restent hors Git pour conserver privées les adresses et chemins
locaux. Aucune nouvelle qualification de coupure électrique, disque plein ou
attestation matérielle n’est déduite de ce contrôle.
