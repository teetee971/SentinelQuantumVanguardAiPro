# Procédure de qualification physique BLE — Galaxy S24+ et second Android

Cette procédure prépare la qualification; elle n'est pas un résultat de test.
Au 24-10-2026, aucun appareil ni `adb` n'était disponible dans l'environnement
Codex. Le statut `PHYSICAL_BLE_PASS` reste donc non attribué.

## Préconditions

- Galaxy S24+ et un second Android compatible BLE, batterie suffisante, mode
  développeur activé.
- APK debug construit depuis la branche Offline Mesh et empreinte SHA-256
  conservée.
- Permissions accordées à l'exécution : `BLUETOOTH_SCAN`,
  `BLUETOOTH_CONNECT` et, si l'application advertise, `BLUETOOTH_ADVERTISE`.
  Les permissions legacy Bluetooth sont limitées à Android <= 30 par le
  manifeste. Ne pas accorder de permission de localisation sauf exigence de la
  version Android testée.

## Installation et collecte

```bash
adb devices -l
adb install -r app-debug.apk
adb shell pm grant <package> android.permission.BLUETOOTH_SCAN
adb shell pm grant <package> android.permission.BLUETOOTH_CONNECT
adb shell pm grant <package> android.permission.BLUETOOTH_ADVERTISE
adb logcat -c
adb logcat -v threadtime > sentinel-ble-<device>-<timestamp>.log
```

Conserver `adb shell getprop`, le modèle/version Android, l'APK, son SHA-256,
les permissions effectives et les journaux complets. Sur Android récent,
utiliser l'écran système de permissions si `pm grant` refuse une permission.

## Scénarios

1. Démarrer le serveur sur A puis le client sur B. Vérifier découverte du
   service, découverte des caractéristiques et abonnement CCCD.
2. Échanger un message assez grand pour franchir le MTU négocié dans chaque
   sens. Vérifier les fragments, le réassemblage, l'ACK, les retries et
   l'absence de message partiel livré à l'application.
3. Répéter avec écritures concurrentes, fragments dupliqués et ACK périmé.
   Résultat attendu : isolation par appareil, rejet des callbacks obsolètes et
   aucune livraison privée avant authentification.
4. Couper le Bluetooth ou éloigner un appareil jusqu'à perte de liaison,
   attendre le délai radio, puis reconnecter. Vérifier arrêt de l'ancienne
   session et création d'une session fraîche.
5. Tester un appareil/identité non approuvé : le handshake doit échouer et le
   canal privé doit rester fermé.
6. Révoquer la clé dans la politique de confiance, reconnecter, puis vérifier
   le refus. Restaurer la relation et vérifier qu'une nouvelle session peut
   reprendre après une authentification valide.
7. Répéter à plusieurs distances et orientations, en notant RSSI, durée,
   nombre de retries, statut de chaque callback et moment de la déconnexion.

## Preuves attendues

Pour chaque scénario : horodatage monotone, identifiants anonymisés des deux
appareils, version de session, MTU, statut CCCD, traces de handshake/ACK sans
clé ni secret, raison d'interruption, fichier log et résultat attendu/observé.
Ne jamais capturer ni committer de clé privée, secret de session ou contenu
privé non nécessaire.

## Critère de sortie

La qualification physique n'est réussie que si les deux appareils exécutent
tous les scénarios, que les journaux sont archivés et qu'une revue humaine
confirme l'absence de données privées avant authentification. Les tests
unitaires ou la compilation ne remplacent pas cette preuve.
