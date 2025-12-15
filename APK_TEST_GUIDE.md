# 🚀 TEST APK - GUIDE IMMÉDIAT

## 📥 TÉLÉCHARGEMENT APK (MAINTENANT)

### Méthode 1: GitHub Actions (Automatique)
1. Va sur https://github.com/teetee971/SentinelQuantumVanguardAiPro/actions
2. Clique sur **"Build Android Debug APK"**
3. Sélectionne le dernier run avec ✅ (vert)
4. Descends jusqu'à **"Artifacts"**
5. Télécharge **"sentinel-quantum-vanguard-debug-apk"**
6. Extrais le ZIP → tu as **app-debug.apk**

### Méthode 2: Build Local (Si GitHub Actions indisponible)
```bash
cd android-app
npm install
cd android
./gradlew assembleDebug
```
APK généré dans : `android/app/build/outputs/apk/debug/app-debug.apk`

---

## 📱 INSTALLATION SUR TON TÉLÉPHONE

### Étape 1: Activer Sources Inconnues
1. **Paramètres** → **Sécurité** → **Sources inconnues** → **Autoriser**
   
   OU (Android 8+)
   
2. **Paramètres** → **Applications** → **Accès spécial** → **Installer apps inconnues** → Autorise ton navigateur/gestionnaire de fichiers

### Étape 2: Transférer l'APK
**Option A: Via câble USB**
```bash
adb install app-debug.apk
```

**Option B: Via transfert de fichier**
1. Connecte ton téléphone en USB (mode transfert de fichiers)
2. Copie `app-debug.apk` dans ton téléphone
3. Ouvre le fichier depuis le gestionnaire de fichiers
4. Clique **Installer**

**Option C: Via cloud (Drive/Dropbox)**
1. Upload l'APK sur Drive/Dropbox
2. Télécharge depuis ton téléphone
3. Ouvre et installe

---

## ✅ TEST FONCTIONNEL (AUJOURD'HUI)

### Test 1: Lancement de l'App
```
✅ L'app se lance
✅ Tu vois l'écran d'accueil
✅ Bouton "📱 Phone Security" visible
```

### Test 2: Permissions
```
1. Clique sur "Phone Security"
2. Clique sur "🔐 Activer les Permissions"
3. Accorde les permissions :
   - ✅ Journal d'appels
   - ✅ Contacts
   - ✅ État du téléphone
```

### Test 3: Voir le Journal d'Appels
```
1. Dans Phone Security, clique "Historique d'appels"
2. Tu vois tes vrais appels récents
3. Chaque appel affiche :
   - Numéro
   - Date/heure
   - Durée
   - Type (INCOMING/OUTGOING/MISSED)
```

### Test 4: DÉTECTION APPEL ENTRANT (CRITIQUE)
```
1. Demande à quelqu'un de t'appeler
   OU
   Appelle-toi depuis un autre téléphone

2. RÉSULTAT ATTENDU:
   ✅ Popup s'affiche AUTOMATIQUEMENT
   ✅ Affiche le numéro
   ✅ Affiche le pays (si détectable)
   ✅ Affiche le niveau de risque
   ✅ 3 boutons visibles:
      - ✅ AUTORISER (vert)
      - 🚫 BLOQUER (rouge)
      - 🚩 SIGNALER (orange)
```

### Test 5: Bloquer un Numéro
```
1. Reçois un appel (ou simule)
2. Clique "🚫 BLOQUER"
3. Message de confirmation
4. Va dans "Historique"
5. Vérifie que le numéro est marqué "BLOCKED"
```

### Test 6: Signaler un Spam
```
1. Reçois un appel
2. Clique "🚩 SIGNALER"
3. Message de confirmation
4. Le numéro est marqué comme spam dans le journal
```

---

## 🔍 CE QUI DOIT FONCTIONNER (V1)

### ✅ Fonctionnel Maintenant
- [x] Lecture journal d'appels (vrais appels)
- [x] Lecture contacts
- [x] Détection appel entrant en temps réel
- [x] Popup automatique sur appel
- [x] Affichage numéro + pays + risque
- [x] Bouton Autoriser (sauvegarde)
- [x] Bouton Bloquer (liste noire locale)
- [x] Bouton Signaler (marque spam)
- [x] Journal local persistant
- [x] AUCUNE donnée envoyée au cloud

### ⚙️ Framework Prêt (Pas encore actif)
- [ ] Blocage téléphonique réel (nécessite API Telecom)
- [ ] Détection spam avancée (ML)
- [ ] Base de données numéros spam
- [ ] Monitoring réseau

---

## 🐛 PROBLÈMES POSSIBLES

### L'app ne s'installe pas
```
Solution: Vérifie Android 6.0+ (API 23+)
Solution: Active "Sources inconnues"
Solution: Vérifie espace disque (30MB minimum)
```

### Permissions refusées
```
Solution: Va dans Paramètres → Apps → Sentinel → Permissions
Solution: Accorde manuellement les permissions
```

### Popup ne s'affiche pas sur appel
```
Cause possible: Permission PHONE_STATE non accordée
Solution: Paramètres → Apps → Sentinel → Permissions → Téléphone → Autoriser
```

### Journal d'appels vide
```
Cause: Permission READ_CALL_LOG non accordée
Solution: Clique "Activer les Permissions" dans Phone Security
```

---

## 📊 CE QUE TU DOIS VÉRIFIER

### Checklist Test Complet
```
□ L'app se lance sans crash
□ Écran d'accueil s'affiche correctement
□ Bouton Phone Security fonctionne
□ Permissions sont demandées
□ Journal d'appels affiche vrais appels
□ Contacts sont accessibles
□ Appel entrant déclenche popup
□ Popup affiche numéro correctement
□ Pays est détecté (si international)
□ Niveau de risque est affiché
□ Bouton Autoriser fonctionne
□ Bouton Bloquer fonctionne
□ Bouton Signaler fonctionne
□ Journal local persiste après fermeture app
```

---

## 📸 CAPTURES D'ÉCRAN À PRENDRE

Pour documenter le test :
1. **Écran d'accueil** (Dashboard)
2. **Phone Security** (liste features)
3. **Permissions** (statut accordé)
4. **Journal d'appels** (vrais appels)
5. **Popup appel entrant** (le plus important!)
6. **Historique** (appels bloqués/signalés)

---

## 🎯 CRITÈRES DE SUCCÈS

### Succès Total (100%)
```
✅ APK s'installe
✅ App se lance
✅ Permissions accordées
✅ Journal d'appels fonctionne
✅ Détection appel entrant fonctionne
✅ Popup s'affiche automatiquement
✅ Boutons Autoriser/Bloquer/Signaler fonctionnent
✅ Journal persiste
```

### Succès Partiel (80%)
```
✅ APK s'installe
✅ App se lance
✅ Journal d'appels fonctionne
⚠️ Popup ne s'affiche pas (problème permission)
```

### Échec (<50%)
```
❌ APK ne s'installe pas
OU
❌ App crash au lancement
```

---

## 🔄 PROCHAINES ÉTAPES SI SUCCÈS

Si le test fonctionne :
1. ✅ **Module Téléphone validé**
2. → Passer au **Module Réseau**
3. → Ajouter **Threat Intelligence**
4. → Générer **Release APK** signée

Si problèmes :
1. 🐛 **Documenter les erreurs**
2. 🔧 **Corriger les bugs**
3. 🔄 **Re-générer APK**
4. ♻️ **Re-tester**

---

## 📞 SUPPORT

### Logs à vérifier si problème
```bash
# Via adb
adb logcat | grep Sentinel
adb logcat | grep PhoneCallReceiver
adb logcat | grep PhoneSecurityModule
```

### Informations à fournir si bug
- Modèle téléphone
- Version Android
- Message d'erreur exact
- Capture d'écran de l'erreur
- Logs adb (si possible)

---

**🎯 OBJECTIF : APK TESTABLE AUJOURD'HUI**

**📱 GO TEST TON APK MAINTENANT !**
