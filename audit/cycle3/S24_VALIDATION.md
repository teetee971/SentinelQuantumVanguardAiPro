# Validation physique restante — aucun résultat présumé

Les binaires livrés sont DEBUG, pas une version de production. Ils contiennent un
client préparatoire non raccordé à l'interface; ils n'embarquent pas Qwen.
Le service llama.cpp doit être construit et exécuté séparément sur ARM64. Le
manifeste n'autorise pas HTTP cleartext:le raccordement Android est encore bloqué
et n'est pas contourné par cette livraison.

Avant installation:comparer la signature de l'APK actuellement installé à celle
du nouveau debug. Une signature différente empêche la mise à jour. Ne pas
désinstaller l'application pour contourner ce refus:les données locales pourraient
être perdues. Préférer un appareil/profil de test ou reconstruire avec le même
certificat debug local. Aucun keystore n'est livré.

Depuis un hôte ADB autorisé, sur un appareil de test compatible:

    adb devices -l
    adb install -r sentinel-cycle3-debug.apk
    adb install -r sentinel-cycle3-debug-androidTest.apk
    adb shell am instrument -w com.sentinel.quantum.test/androidx.test.runner.AndroidJUnitRunner

Vérifier le nom exact du runner dans le manifeste de l'APK de tests livré avant
exécution. Conserver la sortie complète, version Android, correctif de sécurité,
modèle, ABI, versions des APK et leurs empreintes. Ne pas convertir une erreur
d'installation, de signature, de permission ou de rôle en résultat de test réussi.

À qualifier ensuite:consentement et fermeture de session IA, authentification
humaine liée à une proposition exacte, rejet/annulation/expiration/rejeu/révocation,
arrêt du service local, process death, aucune exécution sensible sans autorisation,
journalisation persistante minimisée. Ces parcours n'existent pas encore de bout
en bout dans l'application et nécessitent un développement additionnel.

Rejouer les parcours critiques Phone Core/SMS/MMS/VPN/Wi-Fi/Bluetooth avec les
permissions et rôles accordés puis refusés/révoqués. Aucun test matériel n'est
prouvé par la compilation ou les tests JVM. LiveKit reste désactivé sans barrière
fournisseur de révocation réellement validée.
