> Étape ultérieure : le propriétaire a maintenant signé et vérifié sur place le candidat. Voir [résultat de signature fourni](OWNER_SIGNING_RESULT.md). Le présent rapport décrit la préparation antérieure et les limites du cloud. Installation toujours non exécutée.

# Réparation de signature D.1 — état vérifié

**Verdict : NOT READY. Aucun APK compatible signé avec la clé installée n'a été produit. Aucune installation, désinstallation, suppression de données, rotation de clé ou CI distante déclenchée.**

## Identités et origine probable

Constat confirmé par l'utilisateur sur Galaxy S24+ : com.sentinel.quantum, version1.0.9/code10, certificat `68915958f2f8683afead51cc3a1e578d182df4b9d9b2505c48c261b1a17b3fe2`.

Ancien candidat Codex dfd27286 et keystore debug local : `dee0d0f14e3a211d5ce9adca0280acde443e2312857d33da2258bef0f1b632e1`. Cette différence établit le conflit de mise à jour en l'absence de lignée autorisée démontrée. Le précédent résultat MATCH contre un fichier local n'était pas une preuve de signature installée.

Le certificat6891… figure déjà à la ligne22 des scripts historiques scripts/termux-security-lab-preview-update.sh et scripts/termux-security-lab-preview-v15.sh. Ils construisent dans Ubuntu proot, sous le projet $HOME/sentinel-security-lab-androidtest, puis vérifient ce certificat. C'est une piste sérieuse vers le keystore debug de cet Ubuntu ; ce n'est pas une preuve que le fichier existe encore. **Ne pas relancer ces anciens scripts** : ils changent de checkout et construisent une ancienne branche, pas le D.1 actuel.

## Recherche effectuée, sans copier de clé

- Inventaire des fichiers de signature accessibles dans /workspace et /home/agent/.android ; seul le debug local est présent. Son certificat public est dee0…, différent du certificat installé.
- /root/.android de cet hôte est inaccessible ; aucune conclusion « vide » ne lui est attribuée. Cet emplacement n'est pas le /root de l'Ubuntu proot du téléphone.
- Historique Git : un ancien store Android debug a été trouvé. Lecture de son certificat directement depuis l'objet Git en flux vers la JVM, sans extraction d'un fichier de keystore et sans appel à getKey : certificat d9b9bdec64458d1d969de37defba0d814d27656376c8a78cc9f77e081776b330, différent du certificat installé. Aucune copie de ce store dans la livraison.
- Aucune variable de signature/Codemagic n'est fournie à cet environnement. Aucun connecteur Codemagic disponible.
- codemagic.yaml existe dans la branche de sauvegarde divergente et son historique, pas dans la branche D.1 courante. Il référence le groupe sentinel_production et les noms SENTINEL_PRODUCTION_KEYSTORE_B64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD, EXPECTED_CERTIFICATE_SHA256. Aucune valeur lue/affichée, aucun groupe secret téléchargé/décodé. La présence d'une référence de groupe ne démontre pas sa disponibilité ni son certificat.
- Lecture des seuls noms de secrets GitHub refusée HTTP403 « Resource not accessible by integration ». Aucun contournement ni secret récupéré.
- Aucun fichier de lignée trouvé dans le périmètre accessible ou les chemins historiques suivis. Le candidat signé dee0… conservé est v2, pas v3/v3.1. L'APK installé n'a pas été reçu : une éventuelle preuve externe reste inconnue. **Aucune lignée6891→dee0 valide démontrée.** Un certificat public/SHA ne permet pas de fabriquer cette autorisation ; ne pas générer une nouvelle clé pour contourner le conflit.

Inventaire et certificats publics : evidence/inventory.json, historical-store-public-certificate.json et audit-final/. Aucun matériel privé, mot de passe ou base64 dans les preuves.

## Préparation réalisée

Sources de build : **50c66852c13d6ed4354d2a818277a7241ef45c4b**. Sources fonctionnelles D.1 qualifiées : dfd272861fd6a428df07f139a2665605d1dc2b2e. Aucun changement de src/main, de manifeste, de permissions, de Room, de préférences ou de fonction D.1.

Gradle : versionCode11 (>10), versionName1.0.9. Nouvelle variante d1UpdateUnsigned : initWith debug, signingConfig=null, pas de suffixe de package, pas de minification. Elle prépare le comportement debug déjà qualifié pour une signature **dans l'environnement qui possède la clé d'origine**. Ce n'est pas une release Play ni un APK installable.

Fichier : Sentinel-D1-v11-UNSIGNED-50c66852.apk ; taille101293063 octets ; SHA-256 `854e61e38bfafe0ec8f1871c0833b68c07fcc42485683f6e872f562def1958d9`.

Package com.sentinel.quantum, versionCode11, min24, target36, compile37. Zipalign code0. apksigner verify code1 **attendu : aucune signature**, jamais annoncé PASS. Aucun nouvel APK signé avec une mauvaise clé n'est proposé comme réparation. Métadonnées complètes dans ARTIFACTS.json.

## Vérifications exécutées

- Kotlin + testDebugUnitTest + assembleD1UpdateUnsigned : code0, build2m45s.
- Android : 1412 exécutés,1412 réussis,0 échec/erreur/ignoré,304 XML. Les tests sont ceux du cœur debug ; ils ne prouvent pas une installation de l'APK non signé.
- Contrats Node ciblés :19 réussis,0 échec/ignoré. Le contrôle de version a échoué avant passage10→11, puis réussi.
- Audit de keystore, CLI réelles : local dee0 contre cible6891→code4 ; témoin contre dee0→code0 ; store absent→code2.
- Revue de sécurité : écrasement possible d'un rapport lié par lien dur identifié puis corrigé par création exclusive. Trois tests Python réels :2 assertions échouées avant correction,3 tests réussis après. Aucun store réel sacrifié pour les tests (fixtures texte publiques).
- Revue finale indépendante : aucun autre défaut bloquant, sans exécution par le reviewer.
- Aucun test instrumenté ou physique exécuté ; les26 scénarios S24+ restent séparément à faire après signature et autorisation.

## Suite minimale

1. Exécuter LOCAL_KEY_AUDIT.md dans Ubuntu proot sur le téléphone, en lecture seule. Ne transmettre que le JSON public ; jamais un .keystore/.jks/.p12/.pfx, mot de passe ou base64.
2. Si MATCH_PRIVATE_ENTRY : cela prouve certificat et présence d'entrée privée, **pas encore une signature réussie**. Utiliser LOCAL_SIGNING_HANDOFF.md pour signer l'APK déjà préparé, sur place, sans copier la clé.
3. Vérifier le certificat6891, package/version11, signature, alignement, contenu et SHA de l'APK signé. Aucun READY avant ces preuves et les autres prérequis de mise à jour. Aucune installation automatique.
4. Si clé absente ou mot de passe local inconnu : blocage maintenu ; voir ISOLATED_QUALIFICATION.md. Ne pas effacer l'application actuelle, ses données ni ses rôles pour contourner Android.

PR1748 maintenue en brouillon. Aucun main/backup fusionné, aucun workflow payant, aucune clé créée ou exposée. Schéma Room inchangé, non suivis préservés. Les liens observés de livraison sont dans la PR après dépôt, sans lien inventé.
