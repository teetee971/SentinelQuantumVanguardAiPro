# Qualification MMS v21 — 6 novembre 2026

Statut global : BLOCKED. Galaxy S24+ : NOT_TESTED pour cette version.

Source qualifiée : `0ce9deef226f22a971c3781ef95e1d9f87c8999f`.
Arbre Android : `5b6794f346073a983fc8ae2cd5231f82279a799d`.

Les observations fournies par l’utilisateur sur v20 confirment l’installation, la lecture des contacts et du journal, ainsi qu’un SMS simple avec accusé de livraison. Elles montrent également un échec des miniatures MMS. Le libellé de quarantaine de la liste des PDU est générique et ne constitue pas un verdict de malveillance sur le JPEG.

La v21 utilise la route Android canonique `content://mms/part/<id>`, avec revalidation du parent, de l’identité et du MIME avant et après décodage. Les limites de taille, de pixels et les contrôles de permission restent actifs. Douze tests de régression couvrent le contrat et le flux fournisseur. Aucun nouvel écran, schéma Room, dépendance ou wrapper n’a été ajouté ou modifié dans ce correctif.

## Contrôles exécutés

- PhoneSecurityVisualDashboardSourceContractTest : 1 test, aucun échec.
- testDebugUnitTest : 1 421 tests ; premier passage sans fixture : 1 test ignoré ; nouveau passage avec le nouvel APK : aucun échec ni test ignoré.
- lintDebug : 0 erreur, 221 warnings, 19 hints. Classification et rapports complets archivés.
- assembleDebug et assembleDebugAndroidTest : PASS ; exécution instrumentée sur appareil non réalisée.
- APK debug : versionCode 21, versionName 1.0.9, minSdk 24, targetSdk 36, compileSdk 37 ; signature v2 vérifiée, alignement vérifié.
- SHA-256 APK cloud : `0055e96acef36c1e7ea83c4049e4cc8aa2a0af88078f062dca4b07694557e4a5`.
- Contrats produit : 69 PASS après synchronisation de la documentation courante ; l’échec initial documentaire est conservé.
- Audit npm : 0 vulnérabilité. Détails des alertes Dependabot inaccessibles ; aucune résolution revendiquée.

## Installation contrôlée

Le kit privé Drive est référencé dans SUMMARY.json. Il signe localement avec la clé debug existante de l’utilisateur sans transmettre cette clé. Le script vérifie les empreintes, la signature, l’identité des entrées ZIP et les offsets natifs. Il n’installe rien. Sa validation avec une clé de test et quatre cas négatifs est archivée. La signature locale et l’installation de v21 restent à effectuer.

Ne pas désinstaller, effacer les données ni rétrograder. Préserver la modification MMS locale du dépôt Termux. Réouvrir le MMS existant après mise à niveau pour vérifier l’aperçu, sans nouvel envoi nécessaire.

## Contrôles physiques restants

Miniatures MMS entrantes et sortantes, appels réels et filtrage, SMS multiparties/Unicode, notifications en arrière-plan, permissions révoquées, seconde SIM si disponible, exécution instrumentée appropriée et conservation exhaustive des données après migration. Les observations v20 ne qualifient pas automatiquement v21.

Les commandes, journaux, résultats JUnit, empreintes des sources et preuves de signature sont dans `qualification/evidence/20261106-mms-provider-v21-0ce9deef/`. Le commit de livraison peut ajouter uniquement documentation et preuves ; la qualification finale doit alors être réexécutée et son arbre Android comparé.
