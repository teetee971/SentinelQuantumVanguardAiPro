# Référence de travail V1 commerciale

**Inventaire du 18 novembre 2026, Guadeloupe.** La branche locale `integration/sentinel-v1-commercial-20261118` a été ouverte dans le worktree `/workspace/work/sentinel-v1` depuis la tête C11 `4e444a59a7692320c9a8f77c48ce19b676726c3c`. À l’inventaire initial ce worktree était propre. Les changements V1 sont réalisés dans ce worktree dédié.

| Référence observée | SHA |
| --- | --- |
| Branche C11 locale et distante `fix/sentinel-ai-revocation-barrier-20261118` | `4e444a59a7692320c9a8f77c48ce19b676726c3c` |
| Branche V1 locale à la création, sans ref distante lors de l’inventaire | `4e444a59a7692320c9a8f77c48ce19b676726c3c` |
| `origin/main` vérifiée en lecture seule | `935344423b2af2a4a57c58123f2c5c178b46ec03` |
| Baseline avant le correctif de révocation P0 | `0bfb6c26f7f9b5086a448949f406d17e0466f9a7` |
| Code du build de qualification C11 | `ea5f0a7d019b1113dd25810a6f325db789f38f39` |
| Code du candidat V1 testé et compilé | `25c4e2c9224abe8feaf07474a2d092ea657330f5` |

Autre worktree observé `/workspace/work/sentinel-reference` sur la branche C11. La copie physique `~/sentinel-cycle11-s24-validation` sur le téléphone et Termux/proot restent hors du workspace cloud. Les écarts C9/C10/C11/main et les preuves de la barrière sont documentés séparément. [Audit C11](../cycle11/AUDIT_GLOBAL_C11.md) · [Correctifs C11](../cycle11/CORRECTIONS_C11.md)

## Artefacts et preuves hérités

| Fichier | Identité | SHA-256 |
| --- | --- | --- |
| `sentinel-c11-arm64-debug.apk` | `com.sentinel.quantum.aivalidation.c11` | `87b9f213244ced7bcd633b75da265d142435a1061e695012b8d1bfc7a817174e` |
| `sentinel-c11-instrumentation.apk` | `com.sentinel.quantum.aivalidation.c11.test` | `ed17e8797e1d2bd756f571582cb8950385a9a93955f9a3a358067bb0b1207213` |
| `Sentinel_IA_C11_Qualification_S24_4e444a59.zip` | Transfert privé de la paire et du dossier de preuve C11 | `1a6c1c22312e0ecd43f5f5006cf8dd069abe9b18a1dd4c874d30d1543016ba1a` |

Les deux APK C11 partagent le certificat debug SHA-256 `0d5e03ce9117817a0cfe55f2db1368f949218508abe34edf31a013cddde67275`. L’empreinte de l’archive locale a été revérifiée pour cet inventaire. Le transfert privé reste réservé aux personnes déjà autorisées. [Paquet privé C11](https://drive.google.com/file/d/1GUp912JmAZ_cwcLLon9ZiiDkuctYpqBs/view?usp=drivesdk) · [Protocole, empreintes et portée C11](../cycle11/VALIDATION_S24_PLUS.md) · [Journal local C11](../cycle11/C11_LOCAL_EVIDENCE.log)

Les résultats Android initiaux et le roundtrip rapportés par le propriétaire sont rattachés à son installation. La correspondance de cette installation avec les empreintes ci-dessus, les deux scénarios de reprise, le Keyguard réel et l’inférence Qwen ARM64 restent à établir. Les journaux C9 et C11 sont des preuves propres à leurs installations. Les commandes du parcours V1 ciblent exclusivement la nouvelle identité isolée.

## Périmètre et canaux V1

| Objet | Décision |
| --- | --- |
| Socle | Phone Core avec appel, filtrage local, contacts, historique et messagerie SMS/MMS avec rôles et permissions Android effectifs |
| Exclusions du candidat | Agent IA C11, console Qwen, Wi-Fi, Bluetooth, trackers, LAN, VPN, Mesh, Wear, transformation vocale et parcours commerciaux ou services adjacents |
| Activation build | Opt-in Gradle `-PsentinelCommercialV1=true`. Contrôler la portée sur l’APK et l’AAB fusionnés |
| Debug privé d’acceptation | `com.sentinel.quantum.v1validation`. Installation sur appareil et lignes de test dédiés après contrôle du certificat |
| Candidat de distribution | `com.sentinel.quantum`, AAB Play. L’APK/AAB non signé sert à l’inspection locale. Signature de production, accès Play et soumission sont des étapes séparées |
| Publication | Aucun canal public activé par ce dossier. Aucun tag release, aucune fusion ou exécution GitHub Actions payante |

Les journaux d’inventaire et l’état Git final sont inclus dans le transfert V1 avec la liste exacte des chemins modifiés. Les quatre commits de code suivent la tête C11 sur la branche V1. Les commits de documentation conservent les octets du code générateur du candidat. [État de release](RELEASE_READINESS.md) · [Acceptation physique](S24_PHYSICAL_ACCEPTANCE.md)
