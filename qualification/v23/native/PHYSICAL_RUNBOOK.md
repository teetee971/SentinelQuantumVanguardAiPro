# Qualification physique future — non exécutée

La consigne actuelle interdit toute nouvelle installation sur le GalaxyS24+. Les étapes ci-dessous restent **conditionnelles à une nouvelle autorisation explicite**. Aucune n’a été exécutée pendant cette mission.

## Laboratoire isolé, en premier

1. Vérifier le SHA-256 des artefacts contre `SHA256SUMS` et contrôler au préalable l’identifiant avec `aapt2 dump badging`.
2. L’application de test est **`com.sentinel.quantum.mmslab.v23`**, sans permissions SMS/téléphonie. Ne pas intervertir les deux APK lab avec `app.apk` / `app-test.apk`.
3. Après autorisation seulement, installer `lab.apk` puis `lab-test.apk` avec `adb -s DEVICE install -r -t CHEMIN_APK`. Conserver les résultats et les versions Android/ABI.
4. Lancer uniquement :

```bash
adb -s DEVICE shell am instrument -w -r com.sentinel.quantum.mmslab.v23.test/androidx.test.runner.AndroidJUnitRunner
```

5. Conserver la sortie entière, y compris le nombre de tests, échecs, ignorés, annulations et le test original `truncatedEntropyWithAnAppendedEndMarkerIsRejected`. Le cas paramétré par une boucle doit refuser10/25/50/75/90/99% avec EOI ajouté. Vérifier aussi les orientations EXIF, progressifs, restart, budgets et préparateur. Aucune véritable émission MMS n’est réalisée par ce laboratoire.
6. Refaire cette qualification sur appareils/émulateurs API24–27 puis28+ ; les tests hôte ne remplacent pas les chargeurs JNI/Bionic réels.

## Circuit opérateur, étape distincte

L’installation du package principal exige une autre autorisation et une signature compatible avec la v22 du propriétaire. Les APK cloud sont signés avec la clé debug existante du cloud ; ils ne constituent pas une mise à jour compatible de la v22 du S24+.

**Ne pas désinstaller la v22, ne pas vider ses données, ne pas changer ses rôles et ne pas lancer la suite complète `com.sentinel.quantum.test` sur cet appareil.** Cette branche ne prétend pas intégrer les gardes de tous les anciens tests instrumentés. L’APK `app-test.apk` ne doit pas être lancé globalement sur le téléphone de production.

Après qualification isolée et autorisation séparée d’une mise à jour signée par le propriétaire : sélectionner la SIM réelle ; relever sa limite ; envoyer manuellement un petit JPEG conforme, une photo compressible de1–2Mo, plusieurs JPEG et un JPEG+PNG compatible. Vérifier PDU entier<=limiteSIM, texte/destination/ordre conservés, provider/transport cohérents et absence de double envoi. Faire refuser les JPEG tronqués, un PNG trop volumineux et une configuration opérateur absente sans staging ni écriture provider. Contrôler révocation du rôle/permission et interruption pendant préparation. Ne pas provoquer de perte de données pour tester une erreur.

Distinguer soumission Android, callback transport et livraison/réception observée. Un message affiché «envoyé» ou un build réussi ne prouve pas la livraison opérateur.

## Préservation / retour v22

Pour la mission actuelle, aucune mise à jour n’a été installée : la v22 n’a pas à être restaurée. Après un futur test du laboratoire autorisé, fermer le laboratoire et rouvrir Sentinel. Ne pas utiliser de downgrade, désinstallation, réinitialisation, suppression Room ou suppression de conversation comme procédure de retour.
