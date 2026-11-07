# Doctor : validation physique isolée, non exécutée ici

Ce protocole n'autorise pas une installation sur le package stable. Il est préparé pour une utilisation ultérieure avec accord explicite. Le cloud ne voit aucun appareil ADB. Les deux premiers tests UI ne suppriment rien et ne suffisent pas à qualifier tout le nettoyeur.

## Vérifier avant toute installation

- Lire `ARTIFACTS.json` et vérifier `SHA256SUMS.txt` dans le dossier contenant les APK de cette itération. Ne pas utiliser les APK de l'ancienne candidate Play.
- Package app attendu : **com.sentinel.quantum.labpreview**, label **Sentinel Lab Preview**, version **1.0.10-lab-preview**, code24.
- Package tests attendu : **com.sentinel.quantum.labpreview.test** ; `instrumentation.targetPackage` doit être **com.sentinel.quantum.labpreview**. Toute référence à `target=com.sentinel.quantum` seul interdit l'exécution de ce kit sur le téléphone stable.
- Les deux APK doivent avoir le même certificat debug cloud, indiqué dans le rapport. Il n'est pas le certificat de la v22 ou d'upload Play.
- Aucun appel/SMS/MMS réel : le laboratoire retire les composants Phone Core privilégiés. Aucun rôle Téléphone/SMS/filtrage à lui accorder. L'ouverture automatique de l'assistant est désactivée uniquement pour ce build.
- Conserver une observation lecture seule de la version/signataire et des rôles actuels de la v22 ; ne pas lire/copier ses conversations ni ses préférences.

Après autorisation seulement, installer les **deux APK du laboratoire**, pas un APK nommé app-debug.apk du package principal. Ne jamais utiliser `pm clear`, `adb uninstall`, un downgrade ou `sentinelAllowDestructive=true` sur le téléphone stable.

## Exécuter les deux tests UI non destructifs

L'adresse ADB change avec le Wi-Fi ; reprendre celle affichée par Android, sans deviner l'IP masquée d'une capture.

```bash
# Remplacer la valeur par le serial réellement présent dans adb devices -l.
SENTINEL_DEVICE='IP:PORT'
adb -s "$SENTINEL_DEVICE" shell pm list instrumentation
# Vérifier visuellement le target exact .labpreview avant la commande suivante.
adb -s "$SENTINEL_DEVICE" shell am instrument -w -r \
  -e class com.sentinel.quantum.ui.SystemCleanerInstrumentationTest \
  com.sentinel.quantum.labpreview.test/androidx.test.runner.AndroidJUnitRunner
```

Conserver la sortie complète, code de retour, nombre réel de tests/échecs/skips, SHA des deux APK, date, modèle, API et version. Ne pas remplacer un skip par un PASS. Ne pas lancer la suite entière sans revue de son périmètre.

## Quatre critères Doctor à observer

Ces critères sont indépendants des 14 critères Phone Core et n'augmentent jamais ce compteur.

1. **Scan sans suppression** : créer uniquement dans le cache du laboratoire une copie factice dans `sentinel_log_export`, âgée d'au moins24h, une copie récente et un faux PDU dans `sentinel_mms_send`. Scanner : seule la copie ancienne doit être proposée. Vérifier que les trois fichiers existent encore.
2. **Confirmation et annulation** : sélectionner la copie ancienne, ouvrir puis annuler la confirmation. Aucun fichier ne doit changer. Recommencer et confirmer : seule cette copie doit disparaître, avec résultat et volume vérifiés. La suppression est définitive, pas une quarantaine.
3. **Fichiers protégés** : copie récente, faux PDU, fichiers inconnus, dossiers, liens symboliques, données de la v22 et autres applications restent intacts. Un fichier changé depuis le scan ne doit pas être supprimé sur l'ancienne observation.
4. **Cycle de vie et rapport** : rotation/navigation pendant le scan et un lot ; pas de double exécution, progression et rapport partiel conservés dans le ViewModel. Après mort du processus, aucun rapport persistant n'est promis. Tester grandes polices/petit écran et absence d'application de partage.

Sur Android7 le scan borné est indisponible et le bouton doit rester désactivé avec explication. Sur Android8+ tester l'énumération interrompue/partielle et un dossier volumineux sans lecture intégrale préalable. Ces scénarios ne sont pas prétendus exécutés sur chaque API.

## Fin

Supprimer seulement les fixtures créées dans le laboratoire si nécessaire, jamais des données de production. Vérifier que code et rôles de la v22 sont inchangés. La v22 n'a pas besoin d'être réinstallée : le laboratoire est un autre package. Les tests opérateur Phone Core nécessitent un protocole distinct sur un artefact autorisé et signé avec le bon certificat.
