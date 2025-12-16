# 🔐 Release Keystore Setup Guide

Ce guide explique comment générer un keystore de production pour signer officiellement l'APK Android de Sentinel Quantum Vanguard AI Pro.

---

## 📋 Prérequis

- Java JDK 11+ installé (inclut l'outil `keytool`)
- Accès administrateur au repository GitHub

---

## 🔑 Génération du Keystore de Production

### Étape 1 : Générer le Keystore

> ⚠️ **IMPORTANT :** Remplacez les mots de passe par des valeurs sécurisées et uniques. Ne jamais utiliser les exemples tels quels !

Exécutez cette commande sur votre machine locale :

```bash
# Définir vos mots de passe sécurisés (min. 16 caractères, mixte)
read -s -p "Mot de passe keystore: " KEYSTORE_PASS && echo
read -s -p "Mot de passe clé: " KEY_PASS && echo

# Générer le keystore
keytool -genkeypair \
  -v \
  -storetype PKCS12 \
  -keystore release.keystore \
  -alias sentinel-release \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -storepass "$KEYSTORE_PASS" \
  -keypass "$KEY_PASS" \
  -dname "CN=Sentinel Quantum Vanguard, OU=Mobile Security, O=SentinelSecurity, L=Paris, ST=IDF, C=FR"

# Effacer les variables après usage
unset KEYSTORE_PASS KEY_PASS
```

**Paramètres importants :**

| Paramètre | Description | Recommandation |
|-----------|-------------|----------------|
| `-keyalg RSA` | Algorithme de signature | RSA (standard) |
| `-keysize 4096` | Taille de la clé | 4096 bits (sécurité maximale) |
| `-validity 10000` | Validité en jours | ~27 ans |
| `-storetype PKCS12` | Format du keystore | PKCS12 (recommandé) |
| `-alias` | Nom de la clé | Unique et identifiable |

### Étape 2 : Encoder le Keystore en Base64

```bash
# Linux / Mac
base64 -w 0 release.keystore > release.keystore.base64.txt

# Windows (PowerShell)
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore")) | Out-File -Encoding ASCII release.keystore.base64.txt
```

> ⚠️ **IMPORTANT :** Le fichier `release.keystore.base64.txt` contient des données sensibles. Supprimez-le immédiatement après avoir copié son contenu dans GitHub Secrets (voir Étape 4).

### Étape 3 : Vérifier le Keystore

```bash
keytool -list -v -keystore release.keystore
```

Vous devriez voir :
- Type d'entrée : `PrivateKeyEntry`
- Algorithme : `RSA`
- Taille de clé : `4096 bits`

### Étape 4 : Sauvegarder et Supprimer les Fichiers Sensibles

```bash
# 1. Sauvegardez release.keystore dans un endroit sécurisé (coffre-fort, gestionnaire de mots de passe)

# 2. Copiez le contenu de release.keystore.base64.txt dans GitHub Secrets (voir section suivante)

# 3. Supprimez les fichiers temporaires de manière sécurisée
if command -v shred &> /dev/null; then
  shred -u release.keystore.base64.txt
else
  rm -f release.keystore.base64.txt
fi
```

---

## 🔒 Configuration des Secrets GitHub

### Secrets Requis

Accédez à : **Settings → Secrets and variables → Actions → New repository secret**

| Secret Name | Description | Contenu |
|-------------|-------------|---------|
| `RELEASE_KEYSTORE_BASE64` | Keystore encodé en base64 | Contenu de `release.keystore.base64.txt` |
| `RELEASE_KEYSTORE_PASSWORD` | Mot de passe du keystore | Le mot de passe défini à la génération |
| `RELEASE_KEY_ALIAS` | Alias de la clé | `sentinel-release` (ou l'alias utilisé) |
| `RELEASE_KEY_PASSWORD` | Mot de passe de la clé | Le mot de passe de clé défini à la génération |

### Recommandations pour les Mots de Passe

- **Longueur minimum** : 16 caractères
- **Complexité** : Mélange de majuscules, minuscules, chiffres, symboles
- **Unicité** : Un mot de passe différent pour le keystore et la clé
- **Stockage** : Utilisez un gestionnaire de mots de passe sécurisé

---

## ✅ Vérification de l'APK Signé

### Vérifier la Signature

```bash
# Avec apksigner (Android SDK)
apksigner verify --verbose --print-certs SentinelQuantumVanguardAIPro-vX.Y.Z.apk

# Avec jarsigner (JDK)
jarsigner -verify -verbose -certs SentinelQuantumVanguardAIPro-vX.Y.Z.apk
```

### Vérifier le Checksum SHA-256

```bash
# Linux / Mac
sha256sum -c SentinelQuantumVanguardAIPro-vX.Y.Z.apk.sha256

# Windows (PowerShell)
$expected = Get-Content SentinelQuantumVanguardAIPro-vX.Y.Z.apk.sha256
$computed = (Get-FileHash SentinelQuantumVanguardAIPro-vX.Y.Z.apk -Algorithm SHA256).Hash
if ($computed -eq $expected.Split()[0].ToUpper()) { "✅ Checksum valide" } else { "❌ Checksum invalide" }
```

### Afficher les Informations de l'APK

```bash
# Avec aapt (Android SDK)
aapt dump badging SentinelQuantumVanguardAIPro-vX.Y.Z.apk | grep -E "package|application-label|sdkVersion"
```

---

## 🛡️ Bonnes Pratiques de Sécurité

### ✅ À Faire

1. **Sauvegardez le keystore** dans un endroit sécurisé (coffre-fort, gestionnaire de mots de passe)
2. **Utilisez des mots de passe forts** (min. 16 caractères, mixte)
3. **Ne partagez jamais** le keystore ou les mots de passe
4. **Vérifiez les secrets GitHub** sont bien configurés avant un release
5. **Auditez régulièrement** les accès aux secrets

### ❌ À Ne Pas Faire

1. **Ne commitez jamais** le keystore dans le repository
2. **Ne stockez pas** les mots de passe en clair dans le code
3. **N'utilisez pas** le debug.keystore pour les releases production
4. **Ne partagez pas** les secrets via email ou messagerie non sécurisée

---

## 🔄 Rotation du Keystore

Si vous devez créer un nouveau keystore (compromission, expiration) :

1. Générez un nouveau keystore (voir Étape 1)
2. Mettez à jour les secrets GitHub
3. Publiez une nouvelle version avec le nouveau keystore
4. Informez les utilisateurs de la nouvelle signature

> ⚠️ **Note :** Les utilisateurs devront désinstaller l'ancienne version avant d'installer la nouvelle car les signatures seront différentes.

---

## 🆘 Dépannage

### Erreur : "Keystore decode failed"

```
❌ ERROR: Failed to decode keystore
```

**Solution :** Vérifiez que `RELEASE_KEYSTORE_BASE64` contient bien le contenu encodé correctement :
```bash
# Re-encoder
base64 -w 0 release.keystore > release.keystore.base64.txt
```

### Erreur : "Keystore file is too small"

```
❌ ERROR: Keystore file is too small (X bytes)
```

**Solution :** Le fichier base64 a été tronqué. Copiez l'intégralité du contenu de `release.keystore.base64.txt`.

### Erreur : "Signature verification failed"

```
❌ ERROR: APK signature verification failed
```

**Solution :** Vérifiez que :
- `RELEASE_KEYSTORE_PASSWORD` est correct
- `RELEASE_KEY_ALIAS` correspond à l'alias dans le keystore
- `RELEASE_KEY_PASSWORD` est correct

---

## 📚 Références

- [Android App Signing](https://developer.android.com/studio/publish/app-signing)
- [keytool Documentation](https://docs.oracle.com/javase/8/docs/technotes/tools/unix/keytool.html)
- [GitHub Encrypted Secrets](https://docs.github.com/en/actions/security-guides/encrypted-secrets)

---

**Dernière mise à jour :** Décembre 2025  
**Version :** 1.0
