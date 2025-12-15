# Téléchargement et Installation - Sentinel Quantum Vanguard AI Pro

## Vue d'Ensemble

Guide complet pour télécharger et installer l'application Android Sentinel Quantum Vanguard AI Pro sur votre appareil.

## 📱 Prérequis

### Compatibilité
- **Android 6.0 (Marshmallow)** ou supérieur
- **API Level** : 23 à 34 (Android 6.0 à Android 14)
- **Architectures** : ARM, ARM64, x86, x86_64 (toutes)
- **Espace disque** : 50 MB minimum

### Vérifier votre Version Android

1. Ouvrir **Paramètres** → **À propos du téléphone**
2. Chercher **Version Android** ou **Android version**
3. Vérifier que la version est **6.0 ou supérieure**

## 📥 Téléchargement

### Option 1 : GitHub Releases (Recommandé)

**APK Publique** (Grand Public & Professionnels)

```
https://github.com/teetee971/SentinelQuantumVanguardAiPro/releases/latest/download/Sentinel-Public-v1.0.0.apk
```

**APK Institutionnelle** (Forces de Sécurité, Défense, Administrations)

```
https://github.com/teetee971/SentinelQuantumVanguardAiPro/releases/latest/download/Sentinel-Institutional-v1.0.0.apk
```

### Option 2 : Depuis la Page Web

1. Visiter : https://sentinelquantumvanguardaipro.pages.dev
2. Cliquer sur **"Télécharger Android"**
3. Choisir la variante appropriée
4. Enregistrer le fichier APK

### Option 3 : QR Code

Scannez le QR code disponible sur la page de téléchargement :
- https://sentinelquantumvanguardaipro.pages.dev/download.html

## ✅ Vérification d'Intégrité

**Avant d'installer, vérifiez l'intégrité de l'APK téléchargée.**

### Étape 1 : Télécharger les Checksums

```
https://github.com/teetee971/SentinelQuantumVanguardAiPro/releases/latest/download/checksums.txt
```

### Étape 2 : Vérifier le SHA-256

**Sur Android :**
1. Installer une app de vérification (ex: Hash Droid, Hash Checker)
2. Sélectionner l'APK téléchargée
3. Comparer le hash SHA-256 avec celui de `checksums.txt`

**Sur PC (Linux/macOS) :**
```bash
sha256sum Sentinel-Public-v1.0.0.apk
```

**Sur PC (Windows PowerShell) :**
```powershell
Get-FileHash Sentinel-Public-v1.0.0.apk -Algorithm SHA256
```

### Checksums Attendus (v1.0.0)

Les checksums officiels sont publiés dans chaque GitHub Release :
- [Voir les checksums](https://github.com/teetee971/SentinelQuantumVanguardAiPro/releases/latest)

⚠️ **Si le checksum ne correspond pas, NE PAS installer l'APK** → Fichier corrompu ou modifié.

## 🔓 Autoriser l'Installation

Android bloque par défaut l'installation d'apps hors Google Play Store. Vous devez autoriser l'installation depuis "sources inconnues".

### Android 8.0+ (Oreo et supérieur)

1. Ouvrir **Paramètres**
2. Aller dans **Apps & notifications** (ou **Applications**)
3. Sélectionner **Accès spécial** ou **Special app access**
4. Appuyer sur **Installer des apps inconnues** ou **Install unknown apps**
5. Sélectionner votre **navigateur** (Chrome, Firefox, etc.)
6. Activer **Autoriser depuis cette source** ou **Allow from this source**

### Android 7.1 et inférieur (Nougat)

1. Ouvrir **Paramètres**
2. Aller dans **Sécurité** ou **Security**
3. Activer **Sources inconnues** ou **Unknown sources**
4. Confirmer l'avertissement

⚠️ **Conseil de sécurité** : Désactivez cette option après l'installation de Sentinel.

## 📲 Installation

### Étape 1 : Localiser le Fichier APK

1. Ouvrir l'app **Fichiers**, **Mes fichiers** ou **Downloads**
2. Naviguer vers **Téléchargements** ou **Downloads**
3. Trouver `Sentinel-Public-v1.0.0.apk` (ou Institutional)

### Étape 2 : Lancer l'Installation

1. **Appuyer sur le fichier APK**
2. Si demandé, **autoriser l'installation depuis cette source**
3. L'écran d'installation s'affiche

### Étape 3 : Examiner les Permissions

Sentinel demande uniquement 2 permissions :

| Permission | Utilisation | Obligatoire |
|-----------|-------------|-------------|
| **INTERNET** | Charger le site web Sentinel | ✅ Oui |
| **ACCESS_NETWORK_STATE** | Détecter si hors-ligne | ✅ Oui |

**Version Institutionnelle** demande une permission supplémentaire :
- **READ_CALL_LOG** : Journal d'appels sécurisé (module téléphone)

### Étape 4 : Installer

1. **Appuyer sur "Installer"** ou **"Install"**
2. Attendre la fin de l'installation (5-10 secondes)
3. Message **"App installée"** ou **"App installed"**

### Étape 5 : Ouvrir l'Application

1. **Appuyer sur "Ouvrir"** ou **"Open"**
2. L'application Sentinel se lance
3. Écran de démarrage (splash screen) pendant 2 secondes
4. Chargement du site web sécurisé

## 🎉 Premier Lancement

### Comportement Attendu

1. **Splash Screen** : Logo Sentinel pendant 2 secondes
2. **Chargement** : Barre de progression pendant le chargement
3. **Page d'Accueil** : Interface web Sentinel s'affiche
4. **Navigation** : Bouton retour Android fonctionne dans le WebView

### Fonctionnalités Actives

- ✅ Navigation dans le site Sentinel
- ✅ Mode plein écran immersif
- ✅ Thème sombre automatique
- ✅ Protection anti-capture d'écran (FLAG_SECURE)
- ✅ Gestion hors-ligne avec page d'erreur
- ✅ Bouton retour natif Android

### Si Vous N'Avez Pas de Connexion

L'app affiche une page d'erreur hors-ligne :
- Message : "Pas de connexion réseau"
- Bouton "Réessayer" pour recharger

## 🔄 Mises à Jour

### Comment Mettre à Jour

Sentinel **ne se met PAS à jour automatiquement** (pas sur Play Store).

**Pour installer une mise à jour :**

1. **Télécharger la nouvelle APK** depuis GitHub Releases
2. **Vérifier le checksum** (SHA-256)
3. **Installer par-dessus** l'ancienne version (même signature)
4. Vos données sont préservées

### Vérifier la Version Installée

1. Ouvrir Sentinel
2. Menu → **À propos** ou **About**
3. Version affichée (ex: 1.0.0)

Ou via ADB :
```bash
adb shell dumpsys package com.sentinel.quantum.public | grep versionName
```

### Notifications de Mise à Jour

Surveillez :
- [GitHub Releases](https://github.com/teetee971/SentinelQuantumVanguardAiPro/releases)
- Site web : https://sentinelquantumvanguardaipro.pages.dev

## 🗑️ Désinstallation

### Méthode 1 : Via Paramètres Android

1. Ouvrir **Paramètres**
2. Aller dans **Apps** ou **Applications**
3. Trouver **Sentinel Quantum Vanguard AI Pro**
4. Appuyer sur **Désinstaller** ou **Uninstall**
5. Confirmer

### Méthode 2 : Longue Pression

1. Localiser l'icône Sentinel sur l'écran d'accueil
2. **Appui long** sur l'icône
3. Glisser vers **Désinstaller** ou **Uninstall**
4. Confirmer

### Données Supprimées

La désinstallation supprime :
- ✅ Application (code, resources)
- ✅ Cache web
- ✅ Permissions accordées

❌ **Aucune donnée personnelle** n'est collectée ou stockée.

## ❓ Résolution de Problèmes

### "Application non installée"

**Causes possibles :**

1. **Signature différente** → Désinstaller l'ancienne version d'abord
2. **Espace insuffisant** → Libérer 50 MB minimum
3. **APK corrompue** → Re-télécharger et vérifier checksum
4. **Version Android incompatible** → Vérifier Android 6.0+ minimum

**Solution :**
```bash
# Via ADB
adb uninstall com.sentinel.quantum.public
adb install Sentinel-Public-v1.0.0.apk
```

### "Analyse en cours..." Bloqué

**Symptôme :** Installation bloquée sur "Analyse de l'application"

**Solution :**
1. Forcer l'arrêt de Google Play Services (Paramètres → Apps)
2. Réessayer l'installation
3. Ou installer via ADB (voir ci-dessous)

### Écran Noir au Lancement

**Causes :**
- Pas de connexion internet
- DNS bloqué/filtré

**Solution :**
1. Vérifier la connexion WiFi/4G
2. Désactiver VPN/proxy si actif
3. Essayer un autre réseau
4. Vérifier que `sentinelquantumvanguardaipro.pages.dev` est accessible

### Capture d'Écran Bloquée

**Normal** : Sentinel active FLAG_SECURE pour bloquer les captures.

**Contournement** : Aucun (fonctionnalité de sécurité intentionnelle).

## 🛠️ Installation Avancée (ADB)

### Prérequis

- Android SDK Platform Tools installé
- USB Debugging activé sur l'appareil
- Câble USB

### Procédure

```bash
# 1. Vérifier la connexion
adb devices

# 2. Installer l'APK
adb install Sentinel-Public-v1.0.0.apk

# 3. Lancer l'application
adb shell am start -n com.sentinel.quantum.public/.SplashActivity

# 4. Voir les logs en temps réel
adb logcat | grep Sentinel
```

### Désinstallation via ADB

```bash
# Public variant
adb uninstall com.sentinel.quantum.public

# Institutional variant
adb uninstall com.sentinel.quantum.institutional
```

## 🔒 Sécurité

### Vérifier l'Authenticité

**Certificat de signature :**

L'APK officielle est signée avec le certificat Sentinel. Vérifier via :

```bash
keytool -printcert -jarfile Sentinel-Public-v1.0.0.apk
```

**Fingerprint SHA-256 attendu :**
```
[À publier après génération du keystore de production]
```

### Permissions

Sentinel demande uniquement :
- `INTERNET` (obligatoire)
- `ACCESS_NETWORK_STATE` (obligatoire)

**Variante Institutionnelle ajoute :**
- `READ_CALL_LOG` (optionnel, module téléphone)

### Protection Anti-Malware

- ✅ Code source ouvert et auditable
- ✅ Aucune collecte de données
- ✅ Aucune publicité
- ✅ Aucun tracker
- ✅ Signature vérifiable
- ✅ Checksums publiés

## 📚 Documentation Complémentaire

- [Guide de Production](./PRODUCTION_RELEASE_GUIDE.md) - Build et release APK
- [Guide Android APK](./ANDROID_APK_GUIDE.md) - Développement et build
- [Usages Institutionnels](./USAGES_INSTITUTIONNELS_FR.md) - Cas d'usage professionnels
- [Avertissements Légaux](../public/legal-disclaimer.html) - Cadre juridique

## 🆘 Support

### Problème d'Installation

1. Vérifier les prérequis (Android 6.0+)
2. Consulter la section "Résolution de Problèmes"
3. Ouvrir une issue GitHub si le problème persiste

### Problème de Fonctionnement

1. Vérifier la connexion internet
2. Réinstaller l'application
3. Vérifier les logs via ADB
4. Signaler sur GitHub Issues

### Contact

- **GitHub Issues** : https://github.com/teetee971/SentinelQuantumVanguardAiPro/issues
- **Discussions** : https://github.com/teetee971/SentinelQuantumVanguardAiPro/discussions

---

**Dernière mise à jour :** Décembre 2024  
**Version du guide :** 1.0.0  
**APK Version :** 1.0.0+
