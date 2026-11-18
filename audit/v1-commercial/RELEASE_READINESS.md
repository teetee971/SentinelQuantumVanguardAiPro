# État de préparation release · Sentinel Phone Core V1

**NO-GO pour la distribution commerciale.** Les preuves de recette S24+, de signature sous l’identité de livraison, de validation Play et de support opérationnel sont ouvertes. Le profil V1 permet de préparer et d’inspecter des artefacts en vue de ces contrôles. C11 garde son dossier et son paquet de recette propres.

## Candidats et preuve locale

Code compilé `25c4e2c9224abe8feaf07474a2d092ea657330f5`. L’archive privée rassemble les journaux et les fichiers du tableau d’empreintes.

| Élément | Contrôle | Statut |
| --- | --- | --- |
| Source et périmètre V1 | Diff depuis `4e444a59a7692320c9a8f77c48ce19b676726c3c`, contrats de démarrage et inspection des manifestes réellement empaquetés | PASS · 18 permissions et 27 composants pour chaque variante V1 |
| Debug de recette | `com.sentinel.quantum.v1validation`, label **Sentinel V1 - Recette**, ARM64, versions `1.0.9-v1-validation` / `10`. Runner limité au paquet de recette. | PASS · APK et instrumentation compilés, CRC et signatures APK v2 vérifiés. Installation physique BLOCKED. |
| APK/AAB d’inspection | `com.sentinel.quantum`, variante `releaseUnsigned`, `targetSdk 36` et `versionCode 10` | PASS · inspection unsigned, CRC, validation bundletool, contrôles d’alignement ZIP/ELF 16 Kio et manifestes. |
| AAB de soumission Play | `com.sentinel.quantum`, versionnement monotone. AAB signé avec clé d’upload, attestations et certificat d’application Play établis | BLOCKED · secrets de signature et Play Console indisponibles |
| Tests sur l’artefact livré | 14 critères Phone Core v6, refus/retrait des rôles, One UI, opérateur(s), navigation et repli. Rattacher l’attestation autorisée au SHA de l’artefact | BLOCKED · aucun appareil accessible |
| Support et retour arrière | Contact testé, obligations applicables confirmées, preuve du rétablissement des applications système et de la suspension de rollout | BLOCKED · circuit de support et Play non vérifiés |

| Fichier dans `artifacts/` | SHA-256 |
| --- | --- |
| `sentinel-v1-recette-arm64-debug.apk` | `8c34c8a13def5ca31c533a12a21a6f84603aac64af1c7250a1469f45658682f4` |
| `sentinel-v1-recette-instrumentation.apk` | `baa5952101ace837678c7b7869a03e81275bb73bccc1c7b731e3d93ffa5e56e9` |
| `sentinel-v1-release-inspection-unsigned.apk` | `814c2f694d1ebd316ed4959b2fa2896961db3383baa1691f733d48eb07d82f61` |
| `sentinel-v1-release-inspection-unsigned.aab` | `795de1ef3f0ec49f378774718a9f3f8964296d8dbd0c7892a084219e9a995e08` |

Les deux APK de recette partagent le certificat debug SHA-256 `0d5e03ce9117817a0cfe55f2db1368f949218508abe34edf31a013cddde67275`. Le build signé s’est arrêté au contrôle de configuration de la signature. La recette d’une distribution Play utilisera l’empreinte du binaire signé correspondant. [Baseline Git et fichiers C11](BASELINE.md) · [Critères S24+](S24_PHYSICAL_ACCEPTANCE.md)

## Chemin de build et d’inspection

Construire les deux profils dans un environnement maîtrisé et archiver la sortie par SHA. Les tâches ci-dessous utilisent exclusivement l’opt-in V1. Exécuter les suites pertinentes en profil V1 et en profil historique selon leurs contrats respectifs. Le rapport de validation doit distinguer les résultats JVM, compilations, artefacts, installation et tests physiques.

```sh
cd native-android-app
bash gradlew -PsentinelCommercialV1=true :app:assembleDebug :app:assembleDebugAndroidTest
bash gradlew -PsentinelCommercialV1=true :app:assembleReleaseUnsigned :app:bundleReleaseUnsigned
```

Pour chaque sortie inspecter archive, package et version, manifestes, permissions, composants exportés et ABI. Utiliser `aapt2 dump badging` et l’arbre du manifeste pour l’APK, `bundletool validate` et `bundletool dump manifest --module=base` pour l’AAB. Relier la liste de bibliothèques et le graphe résolu aux composants et flux réellement actifs. Vérifier l’alignement 16 Kio et les binaires natifs conservés. Contrôler la signature du debug avec `apksigner verify --verbose --print-certs`. L’absence de signature est attendue dans le profil `releaseUnsigned`. Garder les deux classes d’artefacts dans des répertoires séparés.

La signature de release rassemble la provenance source, les SHA-256, les deux rapports de certificats, le graphe Android `releaseRuntimeClasspath`, le SBOM et les attestations d’installation. Le profil `release` du build demande les valeurs `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` et `KEY_PASSWORD`. Garder les secrets dans le canal autorisé. La procédure historique sur tag et tête de `main` n’est pas déclenchée dans la mission V1. Toute exécution de workflow, soumission Play ou publication requiert une décision et l’environnement appropriés. [Guide de release](../../docs/PRODUCTION_RELEASE_GUIDE.md) · [Checklist du dépôt](../../RELEASE_CHECKLIST.md)

## Matrice de décision et de rollback

| Étape | Critère avant progression | Repli admissible |
| --- | --- | --- |
| Qualification privée | Recette debug v1validation identifiable, signatures et SHA stables, limite des accès, essais de rôle et retour aux titulaires initiaux réussis | Retirer les rôles via Android, restaurer titulaires/notifications, garder apps et données. |
| Essai du canal Play | AAB signé et accepté, permissions/app content et canaux de test validés, certificat de distribution confirmé, installation du binaire Play sur les dispositifs visés | Rester sur track privé, exclure un appareil non qualifié, suspendre le rollout et documenter. |
| Publication limitée | Critères physiques complets, fiche et Data safety alignées, support joignable, voie de retour arrière répétée, autorisation de publication | Stopper le rollout et rétablir les rôles système pour les clients touchés. Correctif à `versionCode` supérieur, même identité de signature. |
| Extension de périmètre ou canal direct | Preuves séparées par module/type d’appareil et chaîne de signatures compatible | Maintenir le périmètre Phone Core et le dernier canal qualifié. |

Décision d’assistance à préparer avant exposition publique. Désigner une adresse surveillée, une personne d’astreinte ou un circuit de triage, une procédure de transfert sécurisé des preuves expurgées, et une procédure publique de retour aux apps système. Garder le téléchargement et les offres payantes en attente tant que ces canaux sont ouverts. [Conformité et responsabilités](ANDROID_DISTRIBUTION_COMPLIANCE.md)
