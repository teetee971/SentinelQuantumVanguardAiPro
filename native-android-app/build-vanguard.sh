#!/bin/bash
# =============================================================================
# PIPELINE AUTOMATISÉ DE COMPILATION DE PRODUCTION - SENTINEL QUANTUM VANGUARD
# =============================================================================

echo -e "\n\033[1;34m[1/4]\033[0m Arrêt des daemons Gradle rémanents..."
./gradlew --stop > /dev/null 2>&1

echo -e "\033[1;34m[2/4]\033[0m Nettoyage des caches et des anciens artefacts..."
./gradlew clean > /dev/null 2>&1

echo -e "\033[1;34m[3/4]\033[0m Lancement de l'assemblage Release (Optimisation R8/ProGuard)..."
./gradlew assembleRelease --no-configuration-cache

if [ $? -eq 0 ]; then
    echo -e "\033[1;32m[SUCCÈS]\033[0m Compilation de production validée."
    
    APK_SRC=$(find . -name "*release.apk" | head -n 1)
    APK_DEST="/sdcard/Download/sentinel-quantum-vanguard-release.apk"
    
    echo -e "\033[1;34m[4/4]\033[0m Transfert du binaire vers le stockage partagé..."
    cp "$APK_SRC" "$APK_DEST"
    
    SIZE=$(du -sh "$APK_DEST" | cut -f1)
    echo -e "\n\033[1;32m==================================================\033[0m"
    echo -e "\033[1;32m      APK DISPONIBLE DANS VOS TÉLÉCHARGEMENTS     \033[0m"
    echo -e " Chemin : $APK_DEST"
    echo -e " Taille optimisée : $SIZE"
    echo -e "\033[1;32m==================================================\033[0m\n"
else
    echo -e "\n\033[1;31m[ÉCHEC]\033[0m Erreur lors de la compilation. Inspectez les logs ci-dessus.\n"
    exit 1
fi
