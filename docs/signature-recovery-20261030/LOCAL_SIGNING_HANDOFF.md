# Signature sur place — seulement après preuve de la clé historique

**NON EXÉCUTÉE** : aucune clé6891 utilisable n'est disponible dans cet environnement. Les commandes suivantes sont une procédure préparée, pas des preuves de signature. Elles ne réalisent aucune installation et ne copient aucun keystore.

Préconditions : MATCH_PRIVATE_ENTRY pour le store original, propriétaire habilité dans l'environnement sécurisé, SDK Android/Java existants, APK non signé exact et sortie nouvelle. Ne pas créer une nouvelle clé. La clé/mot de passe ne quitte pas cet environnement.

Dans Bash **à l'intérieur d'Ubuntu proot**, renseigner les chemins de l'APK public téléchargé et du store existant. L'alias androiddebugkey ne convient que s'il est celui effectivement vérifié. Les chemins SDK sont ceux des anciens scripts, à vérifier localement.

```bash
set -euo pipefail
unsigned=/chemin/vers/Sentinel-D1-v11-UNSIGNED-50c66852.apk
signed=/chemin/vers/Sentinel-D1-v11-SIGNED-original.apk
store=/root/.android/debug.keystore
sdk=/root/android-sdk/build-tools/36.0.0
test -s "$unsigned" && test -s "$store" && test ! -e "$signed"
actual="$(sha256sum "$unsigned" | cut -d ' ' -f 1)"
test "$actual" = 854e61e38bfafe0ec8f1871c0833b68c07fcc42485683f6e872f562def1958d9
"$sdk/zipalign" -c -v 4 "$unsigned"
```

Après audit local du bon certificat, saisir les mots de passe uniquement au clavier local masqué ; ne pas les envoyer à ChatGPT ni activer set-x :

```bash
read -r -s -p 'Mot de passe local du store : ' SENTINEL_SIGN_STOREPASS
printf '\n'
read -r -s -p 'Mot de passe local de la clé : ' SENTINEL_SIGN_KEYPASS
printf '\n'
export SENTINEL_SIGN_STOREPASS SENTINEL_SIGN_KEYPASS
"$sdk/apksigner" sign --ks "$store" --ks-key-alias androiddebugkey \
  --ks-pass env:SENTINEL_SIGN_STOREPASS --key-pass env:SENTINEL_SIGN_KEYPASS \
  --out "$signed" "$unsigned"
unset SENTINEL_SIGN_STOREPASS SENTINEL_SIGN_KEYPASS
```

Contrôles publics obligatoires après succès (si une commande échoue, arrêter, aucune installation) :

```bash
"$sdk/apksigner" verify --verbose --print-certs "$signed"
"$sdk/zipalign" -c -v 4 "$signed"
"$sdk/aapt" dump badging "$signed"
sha256sum "$unsigned" "$signed"
```

Exiger : un seul signataire, certificat SHA-256 **68915958f2f8683afead51cc3a1e578d182df4b9d9b2505c48c261b1a17b3fe2**, package com.sentinel.quantum, versionCode11 (>10), versionName1.0.9, vérification et alignement code0. Vérifier également les empreintes des entrées ZIP de code/ressources avant/après : la signature ne doit pas changer le contenu applicatif (seules les métadonnées de signature peuvent être ajoutées). Ne pas réaligner après signature.

Conserver uniquement l'APK signé public, empreintes, versions et sorties de vérification. Ne pas joindre le store ni les variables. Si un échec survient avant unset, retirer les variables locales puis arrêter ; aucun mot de passe n'est nécessaire dans un rapport d'erreur.

Une lignée de rotation différente nécessite une preuve Android valide et un audit séparé ; ne pas substituer une autre empreinte pour faire passer le contrôle. Un APK signé avec dee0 n'est pas une réparation pour le S24+ actuel.

Installation seulement après comparaison des preuves, autorisation explicite et vérification des prérequis. Les26 scénarios physiques restent NON TESTÉS ; la signature n'est pas un PASS fonctionnel et n'autorise aucune publication Play.
