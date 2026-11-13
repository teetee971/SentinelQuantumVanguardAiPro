# Installer et revenir au téléphone Samsung

1. Télécharger ce dossier et vérifier `sha256sum -c SHA256SUMS.txt`.
2. Activer le débogage USB sur le téléphone de test ; autoriser l’ordinateur. Vérifier modèle/version via la fiche de tests physiques.
3. Depuis le dossier contenant les APK :

```bash
adb install -r Sentinel-PhoneCore-V1-debug.apk
adb shell monkey -p com.sentinel.quantum -c android.intent.category.LAUNCHER 1
```

Le second appel lance uniquement l’application ; il ne passe aucun appel téléphonique. En cas de `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, ne pas désinstaller automatiquement : l’installation existante possède probablement une autre clé. Sauvegarder les données utiles et décider explicitement d’une désinstallation sur ce téléphone de test (qui efface les données locales), ou utiliser un autre appareil/profil. Le debug n’est pas destiné à Google Play.

4. Ouvrir Téléphone → Réglages/Centre d’activation. Accepter le rôle Téléphone puis les permissions nécessaires aux fonctions choisies. Contacts et historique restent soumis à leurs autorisations. Les modules SMS/MMS ne sont pas requis pour essayer un appel normal ; le diagnostic global de 14 preuves couvre aussi ces modules et n’est pas un certificat V1.
5. Vérifier : `adb shell cmd role get-role-holders android.app.role.DIALER` doit retourner `com.sentinel.quantum` après accord effectif. Effectuer seulement les appels manuels vers la ligne de test connue selon `PHONE_CORE_PHYSICAL_TESTS.md`.

## Retour à Samsung

Hors appel : Paramètres Samsung → Applications → Choix des applications par défaut → Application Téléphone → Téléphone Samsung. Ou ouvrir cette page avec :

```bash
adb shell am start -a android.settings.MANAGE_DEFAULT_APPS_SETTINGS
adb shell cmd role get-role-holders android.app.role.DIALER
```

Le package Samsung est souvent `com.samsung.android.dialer`, mais doit être observé sur l’appareil ; ne pas installer ni attribuer aveuglément un nom supposé. Refaire un appel entrant/sortant entre les lignes de test et confirmer que Samsung gère l’appel avant d’éventuellement désinstaller Sentinel. Ne pas laisser le rôle sans titulaire pour forcer un état de test.

Aucune installation, attribution de rôle ou reprise Samsung n’a été exécutée sur un S24+ dans cet environnement : **NON TESTÉ**.
