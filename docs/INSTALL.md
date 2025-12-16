# GUIDE D'INSTALLATION
**Sentinel Quantum Vanguard AI Pro**

**Dernière mise à jour**: Gérée automatiquement via CI/CD

---

## VUE D'ENSEMBLE

Ce guide explique comment installer Sentinel Quantum Vanguard AI Pro sur différentes plateformes :
- **PWA** (Progressive Web App) - Android, iOS, Desktop
- **APK Android** - Distribution corporative

Aucun compte externe requis. Aucune dépendance vers des stores publics.

---

## MÉTHODE 1 : INSTALLATION PWA (RECOMMANDÉE)

### Avantages PWA
✅ Installation instantanée  
✅ Mises à jour automatiques  
✅ Fonctionne offline  
✅ Aucun téléchargement APK  
✅ Compatible tous appareils  

---

### Installation sur Android

#### Étape 1 : Accéder au site
Ouvrez Chrome et accédez à :
```
https://votre-domaine-sentinel.pages.dev
```

#### Étape 2 : Installer l'application
1. Appuyez sur **Menu** (⋮) en haut à droite
2. Sélectionnez **"Ajouter à l'écran d'accueil"**
3. Confirmez le nom de l'application : **"Sentinel AI"**
4. Appuyez sur **"Ajouter"**

#### Étape 3 : Lancer l'application
L'icône Sentinel apparaît sur votre écran d'accueil. Appuyez dessus pour lancer l'application en mode standalone (plein écran, sans barre d'adresse).

#### Alternative : Prompt d'installation automatique
Si le navigateur le propose, vous verrez un bandeau en bas de page :
```
"Installer Sentinel Quantum Vanguard"
```
Appuyez sur **"Installer"** pour une installation directe.

---

### Installation sur iOS (iPhone/iPad)

#### Étape 1 : Accéder au site
Ouvrez **Safari** et accédez à :
```
https://votre-domaine-sentinel.pages.dev
```

⚠️ **Important** : Utilisez Safari (Chrome ne supporte pas l'installation PWA sur iOS)

#### Étape 2 : Ajouter à l'écran d'accueil
1. Appuyez sur le bouton **Partager** (□↑) en bas de l'écran
2. Faites défiler et sélectionnez **"Sur l'écran d'accueil"**
3. Confirmez le nom : **"Sentinel AI"**
4. Appuyez sur **"Ajouter"** en haut à droite

#### Étape 3 : Lancer l'application
L'icône apparaît sur votre écran d'accueil. Appuyez dessus pour lancer.

**Note** : Sur iOS, la PWA fonctionnera en mode standalone mais avec certaines limitations (pas de notifications push natives, pas de stockage illimité).

---

### Installation sur Desktop (Windows/Mac/Linux)

#### Étape 1 : Accéder au site
Ouvrez **Chrome** ou **Edge** et accédez à :
```
https://votre-domaine-sentinel.pages.dev
```

#### Étape 2 : Installer l'application

**Option A : Via l'icône d'installation**
1. Cherchez l'icône **"Installer"** (⊕ ou ↓) dans la barre d'adresse
2. Cliquez dessus
3. Confirmez dans la popup : **"Installer"**

**Option B : Via le menu**
1. Cliquez sur **Menu** (⋮) en haut à droite
2. Sélectionnez **"Installer Sentinel Quantum Vanguard..."**
3. Confirmez : **"Installer"**

#### Étape 3 : Lancer l'application
L'application est accessible via :
- **Windows** : Menu Démarrer ou icône sur le Bureau
- **Mac** : Applications ou Dock
- **Linux** : Menu Applications

L'application s'ouvre dans sa propre fenêtre, sans barre d'adresse.

---

## MÉTHODE 2 : INSTALLATION APK ANDROID

### Quand utiliser l'APK ?
- Distribution via **MDM** (Mobile Device Management) corporatif
- Déploiement sur appareils sans accès internet
- Contraintes institutionnelles imposant un APK signé
- Besoin de version "figée" (sans auto-update)

### Prérequis
- Android 5.0+ (API 21+)
- Option **"Sources inconnues"** activée (pour installation hors Play Store)

---

### Téléchargement APK

#### Via serveur institutionnel
```
https://votre-serveur-interne/sentinel/sentinel-v1.0.0.apk
```

Signature SHA-256 :
```
[Remplacer par hash réel après signature]
```

#### Vérification de l'intégrité
```bash
# Sur ordinateur
sha256sum sentinel-v1.0.0.apk

# Sur Android (avec Termux)
sha256sum /sdcard/Download/sentinel-v1.0.0.apk
```

Comparez le hash obtenu avec celui fourni par votre administrateur.

---

### Installation APK

#### Étape 1 : Autoriser les sources inconnues
1. Allez dans **Paramètres** → **Sécurité**
2. Activez **"Sources inconnues"** ou **"Installer des applications inconnues"**
3. Autorisez votre navigateur ou gestionnaire de fichiers

⚠️ **Note** : Sur Android 8+, l'autorisation se fait par application.

#### Étape 2 : Installer l'APK
1. Téléchargez l'APK depuis le serveur institutionnel
2. Ouvrez le fichier depuis **Téléchargements**
3. Appuyez sur **"Installer"**
4. Confirmez les permissions demandées :
   - **INTERNET** : Accès réseau pour charger la PWA
   - **ACCESS_NETWORK_STATE** : Détection connexion (offline/online)

#### Étape 3 : Lancer l'application
L'icône **Sentinel Quantum Vanguard** apparaît dans le tiroir d'applications.

---

### Distribution APK via MDM

Pour les administrateurs système utilisant un MDM (Intune, MobileIron, Workspace ONE, etc.) :

1. **Upload de l'APK**
   - Téléversez `sentinel-v1.0.0.apk` sur votre console MDM
   - Configurez les métadonnées (nom, version, description)

2. **Configuration des déploiements**
   - Créez un groupe de déploiement (ex: "Équipe Sécurité")
   - Assignez l'application au groupe
   - Définissez le mode : Obligatoire ou Disponible

3. **Politiques de mise à jour**
   - L'APK charge la PWA en interne
   - Les updates de contenu se font automatiquement via la PWA
   - Republier l'APK seulement si changement natif Android

4. **Permissions & Restrictions**
   - Aucune permission dangereuse requise
   - Compatible avec profils de travail Android Enterprise
   - Peut être déployé en mode "kiosk" si besoin

---

## DÉSINSTALLATION

### Désinstaller la PWA

**Android** :
1. Appui long sur l'icône Sentinel
2. Glissez vers **"Désinstaller"** ou **"Informations sur l'application"**
3. Confirmez la désinstallation

**iOS** :
1. Appui long sur l'icône Sentinel
2. Sélectionnez **"Supprimer l'app"**
3. Confirmez

**Desktop** :
1. Ouvrez **chrome://apps** dans Chrome
2. Clic droit sur Sentinel → **"Supprimer de Chrome"**
3. Confirmez

### Désinstaller l'APK

**Android** :
1. **Paramètres** → **Applications**
2. Recherchez **"Sentinel Quantum Vanguard"**
3. Appuyez sur **"Désinstaller"**

---

## MODE HORS-LIGNE

### Fonctionnalités offline

La PWA et l'APK Android fonctionnent en mode hors-ligne avec limitations :

✅ **Disponible offline** :
- Interface utilisateur complète
- Illustrations SVG
- Navigation entre sections
- Lecture de contenu en cache

⚠️ **Nécessite connexion** :
- Chargement initial (première visite)
- Vidéo hero (si présente)
- Modules nécessitant authentification backend
- Synchronisation des données en temps réel

### Préparation au mode offline

**Première visite (avec connexion)** :
1. Parcourez toutes les sections principales
2. Attendez que les assets se chargent complètement
3. Le service worker met en cache automatiquement

**Utilisation offline** :
- Lancez l'application normalement
- Le contenu en cache s'affiche automatiquement
- Une notification peut indiquer l'absence de connexion

---

## MISES À JOUR

### Mises à jour PWA

**Automatiques** :
- La PWA vérifie automatiquement les mises à jour au lancement
- Le service worker télécharge la nouvelle version en arrière-plan
- Au prochain lancement, la nouvelle version est active

**Manuelles** (forcer la mise à jour) :
1. Fermez complètement l'application
2. Rouvrez-la (ou actualisez dans le navigateur)
3. Si une mise à jour existe, elle s'applique au redémarrage

### Mises à jour APK

**Contenu** (automatique) :
- L'APK charge la PWA en interne
- Les updates de contenu/design se font automatiquement
- Aucune action utilisateur requise

**Application native** (manuelle) :
- Si changement dans le code Android natif
- Téléchargez la nouvelle version de l'APK
- Installez par-dessus l'ancienne (pas besoin de désinstaller)

**Via MDM** (automatique) :
- Votre administrateur déploie la nouvelle version
- L'installation se fait automatiquement selon les politiques MDM

---

## DÉPANNAGE

### La PWA ne s'installe pas

**Sur Android** :
- ✅ Vérifiez que vous utilisez **Chrome** (pas Firefox/Samsung Internet)
- ✅ Assurez-vous d'être sur HTTPS (pas HTTP)
- ✅ Videz le cache : Paramètres → Applications → Chrome → Stockage → Vider le cache

**Sur iOS** :
- ✅ Utilisez **Safari** (obligatoire sur iOS)
- ✅ Assurez-vous d'être sur la dernière version d'iOS
- ✅ Pas de mode navigation privée actif

**Sur Desktop** :
- ✅ Chrome ou Edge (version récente recommandée)
- ✅ L'icône d'installation n'apparaît qu'une fois le manifest chargé
- ✅ Rechargez la page (Ctrl+R ou Cmd+R)

### L'APK ne s'installe pas

**Erreur : "Application non installée"** :
- ✅ Vérifiez que "Sources inconnues" est activé
- ✅ Vérifiez l'intégrité de l'APK (hash SHA-256)
- ✅ Essayez de désinstaller l'ancienne version d'abord

**Erreur : "Signature en conflit"** :
- L'APK a été signé avec une clé différente
- Désinstallez l'ancienne version
- Réinstallez avec le nouvel APK

### L'application ne fonctionne pas offline

**Vérifications** :
- ✅ Avez-vous ouvert l'application au moins une fois en ligne ?
- ✅ Le service worker est-il enregistré ? (vérifiez dans la console)
- ✅ Videz le cache et rechargez en ligne une fois

### Problèmes de performance

**Sur mobile** :
- Fermez les autres applications en arrière-plan
- Redémarrez l'application
- Videz le cache du service worker et rechargez

**Sur desktop** :
- Fermez les onglets inutiles
- Désactivez les extensions de navigateur
- Utilisez Chrome ou Edge (optimisés pour les PWA)

---

## SUPPORT TECHNIQUE

### Documentation complète
- **Guide Android APK** : `/android/README.md`
- **Audit de conformité** : `/docs/AUDIT_FINAL.md`
- **Documentation de livraison** : `/docs/DELIVERY.md`

### Prérequis système

| Plateforme | Version minimale | Recommandée |
|------------|------------------|-------------|
| Android | 5.0 (API 21) | 10+ |
| iOS | 14.0 | 16+ |
| Windows | 10 | 11 |
| macOS | 10.14 | 13+ |
| Linux | Ubuntu 18.04+ | 22.04+ |

### Navigateurs supportés

| Navigateur | Version minimale | PWA Install |
|------------|------------------|-------------|
| Chrome | 80+ | ✅ Oui |
| Edge | 80+ | ✅ Oui |
| Safari | 14+ | ✅ Oui (iOS/Mac) |
| Firefox | 90+ | ⚠️ Partiel |
| Samsung Internet | 12+ | ✅ Oui |

---

## CONFORMITÉ & SÉCURITÉ

### Données et vie privée
- ✅ Aucune donnée personnelle collectée par la PWA
- ✅ Pas de tracking tiers
- ✅ Connexion HTTPS obligatoire
- ✅ Stockage local sécurisé (chiffré par le navigateur)

### Permissions APK
L'APK Android demande uniquement :
- **INTERNET** : Communication réseau
- **ACCESS_NETWORK_STATE** : Détection de la connectivité

Aucune permission dangereuse. Aucun accès :
- Contacts
- Localisation
- Caméra
- Micro
- Stockage externe

### Certification & Audit
- ✅ CodeQL security scan : **PASSED** (0 vulnérabilités)
- ✅ Accessibilité WCAG 2.1 AA : **90%**
- ✅ Performance Lighthouse : **95%**
- ✅ Conformité institutionnelle : **100%**

Voir `/docs/AUDIT_FINAL.md` pour le détail complet.

---

## FOIRE AUX QUESTIONS (FAQ)

**Q : La PWA est-elle aussi sécurisée que l'APK ?**  
R : Oui. La PWA utilise HTTPS et les mêmes standards de sécurité. L'APK Android charge la même PWA en interne.

**Q : Puis-je installer les deux (PWA + APK) ?**  
R : Non recommandé. Choisissez l'une ou l'autre selon votre cas d'usage.

**Q : Les mises à jour de la PWA sont-elles automatiques ?**  
R : Oui, le service worker télécharge les mises à jour en arrière-plan.

**Q : L'APK fonctionne-t-il sans connexion internet ?**  
R : Partiellement. Le chargement initial nécessite une connexion. Ensuite, le mode offline est disponible.

**Q : Peut-on déployer l'APK sur Google Play Store ?**  
R : Oui, mais ce n'est pas nécessaire pour une distribution corporative. Pour Play Store, une TWA (Trusted Web Activity) est recommandée. Voir `/android/README.md`.

**Q : Quelle différence entre PWA et APK ?**  
R :
- **PWA** : Installation via navigateur, updates automatiques, multi-plateformes
- **APK** : Fichier natif Android, contrôle via MDM, version figée

**Q : Comment vérifier que j'ai la dernière version ?**  
R : Ouvrez l'application, la version est affichée dans **À propos** ou **Paramètres**.

---

**Dernière mise à jour** : Gérée automatiquement via CI/CD  
**Maintenu par** : Sentinel Development Team  
**Version du guide** : 1.0.0
