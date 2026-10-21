#!/data/data/com.termux/files/usr/bin/bash
# Local notification test; does not modify Sentinel or watch state.
set -euo pipefail
if ! command -v termux-notification >/dev/null 2>&1; then
 echo "BLOQUÉ: commande termux-notification absente."
 echo "Installer le paquet : pkg install termux-api"
 echo "Installer également l'application complémentaire Termux:API depuis la même source que Termux."
 exit 2
fi
echo "Commande termux-notification disponible"
if termux-notification --id 1988 --title "Sentinel Autocontrol" --content "Test de notification locale — aucun changement GitHub simulé"; then
 echo "ENVOI_DEMANDÉ: vérifier visuellement la notification Android."
 echo "ATTENTION: succès de la commande != preuve d'affichage de la notification."
else
 echo "ÉCHEC: vérifier l'application Termux:API et les autorisations de notification Android."
 exit 1
fi
