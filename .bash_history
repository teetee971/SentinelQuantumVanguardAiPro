# --- Lancement des serveurs ---
launch_servers() {   echo "[$(date)] 🚀 Lancement du mode dev..." >>"$LOG_FILE";   npx concurrently "npm run dev" "npm run functions" >>"$LOG_FILE" 2>&1 &   PID=$!;   sleep 4;   if ps -p $PID >/dev/null 2>&1; then     send_tg "🟢 *$PROJECT* lancé avec succès sur [$URL]($URL)";   else     send_tg "⚠️ Erreur pendant lancement serveur";   fi; }
# --- Vérification des messages Telegram entrants ---
check_tg_commands() {   local offset=$(cat "$LAST_UPDATE_FILE" 2>/dev/null || echo 0);   local response=$(curl -s "https://api.telegram.org/bot$BOT_TOKEN/getUpdates?offset=$offset");   local updates=$(echo "$response" | jq -c '.result[]?');    for update in $updates; do     local update_id=$(echo "$update" | jq '.update_id');     local text=$(echo "$update" | jq -r '.message.text // empty');      echo $((update_id + 1)) >"$LAST_UPDATE_FILE";      case "$text" in       "/status")         local lastlog=$(tail -n 2 "$LOG_FILE" | tr '\n' ' ');         send_tg "📡 *$PROJECT*\n🌐 [$URL]($URL)\n🕓 Dernier log : \`$lastlog\`\nPID: $PID";         ;;       "/restart")         send_tg "🔁 Redémarrage demandé par admin...";         kill $PID 2>/dev/null;         sleep 3;         launch_servers;         ;;       *)         ;;     esac;   done; }
# --- Démarrage initial ---
send_tg "🧠 *$PROJECT*\nMode développement initialisé sur [$URL]($URL)"
launch_servers
# --- Boucle principale ---
while true; do   check_tg_commands;   if ! ps -p $PID >/dev/null 2>&1; then     echo "[$(date)] ⚠️ Crash détecté" >>"$LOG_FILE";     send_tg "⚠️ Crash détecté — redémarrage automatique...";     for ((i = 1; i <= RETRIES; i++)); do       launch_servers;       sleep 8;       if ps -p $PID >/dev/null 2>&1; then         send_tg "✅ Redémarrage réussi (tentative $i)";         break;       fi;     done;   fi;   sleep 15; done
nano start_dev.sh
chmod +x start_dev.sh
pkg install jq -y
./start_dev.sh
#!/bin/bash
# Sentinel Quantum Vanguard AI Pro - Supervisor (v4.2)
# Supporte /status, /restart, /logs via Telegram
BOT_TOKEN="7630324617:AAGYNTiOIhHFDyT83Qt_DezDUxEzEJf-K9E"
CHAT_ID="5707814118"
PROJECT="Sentinel Quantum Vanguard AI Pro"
PORT=5173
LOG_FILE="dev_log.txt"
LAST_UPDATE_FILE=".tg_last_update"
RETRIES=3
# --- Récupère IP locale ---
IP=$(ip addr show wlan0 | grep 'inet ' | awk '{print $2}' | cut -d'/' -f1)
[ -z "$IP" ] && IP="127.0.0.1"
URL="http://$IP:$PORT"
# --- Envoi Telegram ---
send_tg() {   curl -s -X POST "https://api.telegram.org/bot$BOT_TOKEN/sendMessage"     -d chat_id="$CHAT_ID"     -d text="$1"     -d parse_mode="Markdown" >/dev/null; }
# --- Lancement serveur ---
launch_servers() {   echo "[$(date)] 🚀 Démarrage du mode dev..." >>"$LOG_FILE";   npx concurrently "npm run dev" "npm run functions" >>"$LOG_FILE" 2>&1 &   PID=$!;   sleep 4;   if ps -p $PID >/dev/null 2>&1; then     send_tg "🟢 *$PROJECT* lancé avec succès sur [$URL]($URL)";   else     send_tg "⚠️ Échec du démarrage serveur.";   fi; }
# --- Lecture des commandes Telegram ---
check_tg_commands() {   local offset=$(cat "$LAST_UPDATE_FILE" 2>/dev/null || echo 0);   local response=$(curl -s "https://api.telegram.org/bot$BOT_TOKEN/getUpdates?offset=$offset");   local updates=$(echo "$response" | jq -c '.result[]?');    for update in $updates; do     local update_id=$(echo "$update" | jq '.update_id');     local text=$(echo "$update" | jq -r '.message.text // empty');     echo $((update_id + 1)) >"$LAST_UPDATE_FILE";      case "$text" in       "/status"|"status")         local lastlog=$(tail -n 3 "$LOG_FILE" | tr '\n' ' ');         send_tg "📡 *$PROJECT*\n🌐 [$URL]($URL)\n🕓 Dernier log : \`$lastlog\`\nPID: $PID";         ;;       "/restart"|"restart")         send_tg "🔁 Redémarrage demandé...";         kill $PID 2>/dev/null;         sleep 3;         launch_servers;         ;;       "/logs"|"logs")         local last10=$(tail -n 10 "$LOG_FILE" | sed 's/`/"/g');         send_tg "📄 *Derniers logs :*\n\`\`\`$last10\`\`\`";         ;;       *)         ;;     esac;   done; }
# --- Démarrage initial ---
send_tg "🧠 *$PROJECT*\nMode développement initialisé sur [$URL]($URL)"
launch_servers
# --- Boucle principale ---
while true; do   check_tg_commands;   if ! ps -p $PID >/dev/null 2>&1; then     send_tg "⚠️ Crash détecté — tentative de redémarrage...";     for ((i=1;i<=RETRIES;i++)); do       launch_servers;       sleep 8;       if ps -p $PID >/dev/null 2>&1; then         send_tg "✅ Redémarrage réussi (tentative $i)";         break;       fi;     done;   fi;   sleep 15; done
pkg install jq -y
nano start_dev.sh
chmod +x start_dev.sh
./start_dev.sh
nano start_dev.sh
chmod +x start_dev.sh
pkg install jq -y
./start_dev.sh
#!/bin/bash
# Sentinel Quantum Vanguard AI Pro — Supervisor v4.4 CrashAlert
# Envoi du log complet à Telegram en cas de crash
BOT_TOKEN="7630324617:AAGYNTiOIhHFDyT83Qt_DezDUxEzEJf-K9E"
CHAT_ID="5707814118"
PROJECT="Sentinel Quantum Vanguard AI Pro"
PORT=5173
LOG_FILE="dev_log.txt"
LAST_UPDATE_FILE=".tg_last_update"
RETRIES=3
# --- Récupération IP locale sûre ---
IP=$(ip -4 addr show wlan0 2>/dev/null | grep -oP '(?<=inet\s)\d+(\.\d+){3}' | head -n1)
[ -z "$IP" ] && IP=$(ip -4 addr show eth0 2>/dev/null | grep -oP '(?<=inet\s)\d+(\.\d+){3}' | head -n1)
[ -z "$IP" ] && IP="127.0.0.1"
URL="http://$IP:$PORT"
# --- Envoi Telegram (texte + pièces jointes) ---
send_tg() {   curl -s -X POST "https://api.telegram.org/bot$BOT_TOKEN/sendMessage"     -d chat_id="$CHAT_ID" -d parse_mode="Markdown" -d text="$1" >/dev/null; }
send_menu() {   local MSG="$1";   local REPLY='{
    "keyboard":[[{"text":"/status"},{"text":"/restart"},{"text":"/logs"}]],
    "resize_keyboard":true
  }';   curl -s -X POST "https://api.telegram.org/bot$BOT_TOKEN/sendMessage"     -d chat_id="$CHAT_ID" -d parse_mode="Markdown" -d text="$MSG"     -d reply_markup="$REPLY" >/dev/null; }
send_file() {   local FILE="$1";   local CAPTION="$2";   [ -f "$FILE" ] && curl -s -F chat_id="$CHAT_ID"     -F document=@"$FILE" -F caption="$CAPTION"     "https://api.telegram.org/bot$BOT_TOKEN/sendDocument" >/dev/null; }
# --- Lancement serveur ---
launch_servers() {   echo "[$(date)] 🚀 Démarrage du mode dev..." >>"$LOG_FILE";   (npx concurrently "npm run dev" "npm run functions" >>"$LOG_FILE" 2>&1) &   PID=$!;   sleep 4;   ps -p $PID >/dev/null 2>&1 &&     send_tg "🟢 *$PROJECT* lancé avec succès sur [$URL]($URL)" ||     send_tg "⚠️ Échec du démarrage serveur."; }
# --- Commandes Telegram ---
check_tg_commands() {   local offset=$(cat "$LAST_UPDATE_FILE" 2>/dev/null || echo 0);   local resp=$(curl -s "https://api.telegram.org/bot$BOT_TOKEN/getUpdates?offset=$offset");   local updates=$(echo "$resp" | jq -c '.result[]?');   for up in $updates; do     local id=$(echo "$up" | jq '.update_id');     local txt=$(echo "$up" | jq -r '.message.text // empty');     echo $((id + 1)) >"$LAST_UPDATE_FILE";     case "$txt" in       "/status"|"status")         send_menu "📡 *$PROJECT*\n🌐 [$URL]($URL)\nPID: $PID\nDerniers logs :\n\`\`\`$(tail -n3 "$LOG_FILE")\`\`\`";         ;;       "/restart"|"restart")         send_tg "🔁 Redémarrage demandé...";         kill $PID 2>/dev/null; sleep 3; launch_servers;;       "/logs"|"logs")         send_file "$LOG_FILE" "📄 *Derniers logs $PROJECT*";;       "/menu"|"menu") send_menu "📋 Menu interactif disponible :";;     esac;   done; }
# --- Démarrage initial ---
send_menu "🧠 *$PROJECT*\nMode développement initialisé sur [$URL]($URL)"
launch_servers
# --- Boucle principale ---
while true; do   check_tg_commands;   if ! ps -p $PID >/dev/null 2>&1; then     send_tg "⚠️ Crash détecté — tentative de redémarrage...";     send_file "$LOG_FILE" "🚨 Log complet lors du crash";     for ((i=1;i<=RETRIES;i++)); do       launch_servers; sleep 8;       if ps -p $PID >/dev/null 2>&1; then         send_tg "✅ Redémarrage réussi (tentative $i)";         break;       fi;     done;   fi;   sleep 15; done
chmod +x start_dev.sh
pkg install jq -y
./start_dev.sh
#!/bin/bash
# Sentinel Quantum Vanguard AI Pro — Supervisor v4.5 CrashAlert+Audio
BOT_TOKEN="7630324617:AAGYNTiOIhHFDyT83Qt_DezDUxEzEJf-K9E"
CHAT_ID="5707814118"
PROJECT="Sentinel Quantum Vanguard AI Pro"
PORT=5173
LOG_FILE="dev_log.txt"
LAST_UPDATE_FILE=".tg_last_update"
RETRIES=3
IP=$(ip -4 addr show wlan0 2>/dev/null | grep -oP '(?<=inet\s)\d+(\.\d+){3}' | head -n1)
[ -z "$IP" ] && IP="127.0.0.1"
URL="http://$IP:$PORT"
send_tg() {   curl -s -X POST "https://api.telegram.org/bot$BOT_TOKEN/sendMessage"     -d chat_id="$CHAT_ID"     -d parse_mode="Markdown"     -d disable_notification=false     -d text="$1" >/dev/null; }
send_menu() {   local MSG="$1";   local REPLY='{
    "keyboard":[[{"text":"/status"},{"text":"/restart"},{"text":"/logs"}]],
    "resize_keyboard":true
  }';   curl -s -X POST "https://api.telegram.org/bot$BOT_TOKEN/sendMessage"     -d chat_id="$CHAT_ID"     -d parse_mode="Markdown"     -d disable_notification=false     -d text="$MSG"     -d reply_markup="$REPLY" >/dev/null; }
send_file() {   local FILE="$1"; local CAPTION="$2";   [ -f "$FILE" ] && curl -s -F chat_id="$CHAT_ID"     -F document=@"$FILE"     -F caption="$CAPTION"     -F disable_notification=false     "https://api.telegram.org/bot$BOT_TOKEN/sendDocument" >/dev/null; }
launch_servers() {   echo "[$(date)] 🚀 Démarrage dev..." >>"$LOG_FILE";   (npx concurrently "npm run dev" "npm run functions" >>"$LOG_FILE" 2>&1) &   PID=$!;   sleep 4;   ps -p $PID >/dev/null 2>&1     && send_tg "🟢 *$PROJECT* lancé sur [$URL]($URL)"     || send_tg "⚠️ Échec du démarrage serveur."; }
check_tg_commands() {   local offset=$(cat "$LAST_UPDATE_FILE" 2>/dev/null || echo 0);   local resp=$(curl -s "https://api.telegram.org/bot$BOT_TOKEN/getUpdates?offset=$offset");   local updates=$(echo "$resp" | jq -c '.result[]?');   for up in $updates; do     local id=$(echo "$up" | jq '.update_id');     local txt=$(echo "$up" | jq -r '.message.text // empty');     echo $((id + 1)) >"$LAST_UPDATE_FILE";     case "$txt" in       "/status"|"status")         send_menu "📡 *$PROJECT*\n🌐 [$URL]($URL)\nPID : $PID\nLogs :\n\`\`\`$(tail -n3 "$LOG_FILE")\`\`\`" ;;       "/restart"|"restart")         send_tg "🔁 Redémarrage demandé...";         kill $PID 2>/dev/null; sleep 3; launch_servers ;;       "/logs"|"logs") send_file "$LOG_FILE" "📄 Derniers logs $PROJECT" ;;     esac;   done; }
send_menu "🧠 *$PROJECT*\nMode dev initialisé sur [$URL]($URL)"
launch_servers
while true; do   check_tg_commands;   if ! ps -p $PID >/dev/null 2>&1; then     send_tg "🚨 *ALERTE CRASH \nTentative de redémarrage automatique..."    send_file "$LOG_FILE" "🧾 Log complet avant crash";     for ((i=1;i<=RETRIES;i++)); do       launch_servers; sleep 8;       if ps -p $PID >/dev/null 2>&1; then         send_tg "✅ Redémarrage réussi (tentative $i)";         break;       fi;     done;   fi;   sleep 15; done
#!/bin/bash
# Sentinel Quantum Vanguard AI Pro — Supervisor v4.6 Full Audio Alert
BOT_TOKEN="7630324617:AAGYNTiOIhHFDyT83Qt_DezDUxEzEJf-K9E"
CHAT_ID="5707814118"
PROJECT="Sentinel Quantum Vanguard AI Pro"
PORT=5173
LOG_FILE="dev_log.txt"
LAST_UPDATE_FILE=".tg_last_update"
RETRIES=3
# --- IP locale ---
IP=$(ip -4 addr show wlan0 2>/dev/null | grep -oP '(?<=inet\s)\d+(\.\d+){3}' | head -n1)
[ -z "$IP" ] && IP="127.0.0.1"
URL="http://$IP:$PORT"
# --- Envoi Telegram texte/son ---
send_tg() {   curl -s -X POST "https://api.telegram.org/bot$BOT_TOKEN/sendMessage"     -d chat_id="$CHAT_ID"     -d parse_mode="Markdown"     -d disable_notification=false     -d text="$1" >/dev/null; }
# --- Envoi menu interactif ---
send_menu() {   local MSG="$1";   local REPLY='{
    "keyboard":[[{"text":"/status"},{"text":"/restart"},{"text":"/logs"}]],
    "resize_keyboard":true
  }';   curl -s -X POST "https://api.telegram.org/bot$BOT_TOKEN/sendMessage"     -d chat_id="$CHAT_ID"     -d parse_mode="Markdown"     -d disable_notification=false     -d text="$MSG"     -d reply_markup="$REPLY" >/dev/null; }
# --- Envoi fichier log ---
send_file() {   local FILE="$1"; local CAPTION="$2";   [ -f "$FILE" ] && curl -s -F chat_id="$CHAT_ID"     -F document=@"$FILE"     -F caption="$CAPTION"     -F disable_notification=false     "https://api.telegram.org/bot$BOT_TOKEN/sendDocument" >/dev/null; }
# --- Lancement du serveur ---
launch_servers() {   echo "[$(date)] 🚀 Démarrage du mode dev..." >>"$LOG_FILE";   (npx concurrently "npm run dev" "npm run functions" >>"$LOG_FILE" 2>&1) &   PID=$!;   sleep 4;   if ps -p $PID >/dev/null 2>&1; then     send_tg "✅ *$PROJECT* lancé avec succès sur [$URL]($URL) 🔊";   else     send_tg "⚠️ *Erreur de démarrage* du projet 🔊";   fi; }
# --- Vérification des commandes Telegram ---
check_tg_commands() {   local offset=$(cat "$LAST_UPDATE_FILE" 2>/dev/null || echo 0);   local resp=$(curl -s "https://api.telegram.org/bot$BOT_TOKEN/getUpdates?offset=$offset");   local updates=$(echo "$resp" | jq -c '.result[]?');   for up in $updates; do     local id=$(echo "$up" | jq '.update_id');     local txt=$(echo "$up" | jq -r '.message.text // empty');     echo $((id + 1)) >"$LAST_UPDATE_FILE";     case "$txt" in       "/status"|"status")         send_menu "📡 *$PROJECT*\n🌐 [$URL]($URL)\nPID : $PID\nDerniers logs :\n\`\`\`$(tail -n3 "$LOG_FILE")\`\`\`" ;;       "/restart"|"restart")         send_tg "🔁 Redémarrage demandé... 🔊";         kill $PID 2>/dev/null; sleep 3; launch_servers ;;       "/logs"|"logs")         send_file "$LOG_FILE" "📄 Derniers logs $PROJECT" ;;     esac;   done; }
# --- Démarrage initial ---
send_menu "🧠 *$PROJECT*\nMode développement initialisé sur [$URL]($URL) 🔊"
launch_servers
# --- Boucle de supervision ---
while true; do   check_tg_commands;   if ! ps -p $PID >/dev/null 2>&1; then     send_tg "🚨 *ALERTE CRASH  — tentative de redémarrage automatique 🔊"    send_file "$LOG_FILE" "🧾 Log complet avant crash";     for ((i=1;i<=RETRIES;i++)); do       launch_servers; sleep 8;       if ps -p $PID >/dev/null 2>&1; then         send_tg "✅ Redémarrage réussi (tentative $i) 🔊";         break;       fi;     done;   fi;   sleep 15; done
pkg install jq -y
chmod +x start_dev.sh
./start_dev.sh
#!/bin/bash
# Sentinel Quantum Vanguard AI Pro — Supervisor v4.7 Full Audio + Cloudflare Deploy
BOT_TOKEN="7630324617:AAGYNTiOIhHFDyT83Qt_DezDUxEzEJf-K9E"
CHAT_ID="5707814118"
PROJECT="Sentinel Quantum Vanguard AI Pro"
PORT=5173
LOG_FILE="dev_log.txt"
LAST_UPDATE_FILE=".tg_last_update"
RETRIES=3
DEPLOY_LOG="deploy_log.txt"
# --- Détection IP locale (sans netlink root) ---
IP=$(ip -4 addr show wlan0 2>/dev/null | grep -oP '(?<=inet\s)\d+(\.\d+){3}' | head -n1)
[ -z "$IP" ] && IP="127.0.0.1"
URL="http://$IP:$PORT"
# --- Envoi Telegram (texte + son) ---
send_tg() {   curl -s -X POST "https://api.telegram.org/bot$BOT_TOKEN/sendMessage"     -d chat_id="$CHAT_ID" -d parse_mode="Markdown"     -d disable_notification=false -d text="$1" >/dev/null; }
# --- Envoi fichier ---
send_file() {   local FILE="$1"; local CAPTION="$2";   [ -f "$FILE" ] && curl -s -F chat_id="$CHAT_ID"     -F document=@"$FILE" -F caption="$CAPTION"     -F disable_notification=false     "https://api.telegram.org/bot$BOT_TOKEN/sendDocument" >/dev/null; }
# --- Menu interactif ---
send_menu() {   local MSG="$1";   local REPLY='{"keyboard":[[{"text":"/status"},{"text":"/restart"},{"text":"/logs"},{"text":"/deploy"}]],"resize_keyboard":true}';   curl -s -X POST "https://api.telegram.org/bot$BOT_TOKEN/sendMessage"     -d chat_id="$CHAT_ID" -d parse_mode="Markdown"     -d disable_notification=false -d text="$MSG" -d reply_markup="$REPLY" >/dev/null; }
# --- Lancement serveur dev ---
launch_servers() {   echo "[$(date)] 🚀 Démarrage dev..." >>"$LOG_FILE";   (npx concurrently "npm run dev" "npm run functions" >>"$LOG_FILE" 2>&1) &   PID=$!;   sleep 4;   if ps -p $PID >/dev/null 2>&1; then     send_tg "✅ *$PROJECT* lancé avec succès sur [$URL]($URL) 🔊";   else     send_tg "⚠️ *Erreur de démarrage* du projet 🔊";   fi; }
# --- Déploiement Cloudflare Pages/GitHub ---
deploy_project() {   echo "[$(date)] 🚀 Déploiement Cloudflare lancé..." >>"$DEPLOY_LOG";   if npx wrangler pages deploy 2>&1 | tee -a "$DEPLOY_LOG"; then     send_tg "🚀 *Déploiement réussi sur Cloudflare Pages* 🔊";     send_file "$DEPLOY_LOG" "📄 Log de déploiement réussi";   else     send_tg "⚠️ *Échec du déploiement Cloudflare* 🔊";     send_file "$DEPLOY_LOG" "❌ Log de déploiement échoué";   fi; }
# --- Commandes Telegram ---
check_tg_commands() {   local offset=$(cat "$LAST_UPDATE_FILE" 2>/dev/null || echo 0);   local resp=$(curl -s "https://api.telegram.org/bot$BOT_TOKEN/getUpdates?offset=$offset");   local updates=$(echo "$resp" | jq -c '.result[]?');   for up in $updates; do     local id=$(echo "$up" | jq '.update_id');     local txt=$(echo "$up" | jq -r '.message.text // empty');     echo $((id + 1)) >"$LAST_UPDATE_FILE";      case "$txt" in       "/status"|"status")         send_menu "📡 *$PROJECT*\n🌐 [$URL]($URL)\nPID : $PID\nLogs :\n\`\`\`$(tail -n3 "$LOG_FILE")\`\`\`" ;;       "/restart"|"restart")         send_tg "🔁 Redémarrage demandé... 🔊";         kill $PID 2>/dev/null; sleep 3; launch_servers ;;       "/logs"|"logs") send_file "$LOG_FILE" "📄 Derniers logs $PROJECT" ;;       "/deploy"|"deploy")         send_tg "🚀 Déploiement manuel demandé 🔊";         deploy_project ;;     esac;   done; }
# --- Démarrage initial ---
send_menu "🧠 *$PROJECT*\nMode dev initialisé sur [$URL]($URL) 🔊"
launch_servers
# --- Boucle de supervision ---
while true; do   check_tg_commands;   if ! ps -p $PID >/dev/null 2>&1; then     send_tg "🚨 *ALERTE CRASH  — tentative de redémarrage automatique 🔊"    send_file "$LOG_FILE" "🧾 Log complet avant crash";     for ((i=1;i<=RETRIES;i++)); do       launch_servers; sleep 8;       if ps -p $PID >/dev/null 2>&1; then         send_tg "✅ Redémarrage réussi (tentative $i) 🔊";         break;       fi;     done;   fi;   sleep 15; done
e
npm run dev
#!/bin/bash
# Sentinel Quantum Vanguard AI Pro — Supervisor v4.8 HTML Status Dashboard + Local Access
BOT_TOKEN="7630324617:AAGYNTiOIhHFDyT83Qt_DezDUxEzEJf-K9E"
CHAT_ID="5707814118"
PROJECT="Sentinel Quantum Vanguard AI Pro"
PORT=5173
LOG_FILE="dev_log.txt"
LAST_UPDATE_FILE=".tg_last_update"
STATUS_FILE="status.html"
RETRIES=3
DEPLOY_LOG="deploy_log.txt"
# --- Détection IP ---
IP=$(ip -4 addr show wlan0 2>/dev/null | grep -oP '(?<=inet\s)\d+(\.\d+){3}' | head -n1)
[ -z "$IP" ] && IP="127.0.0.1"
URL="http://$IP:$PORT"
# --- Génère le fichier HTML de statut ---
generate_status_html() {   local STATUS="$1";   local COLOR="$2";   local DATE=$(date '+%Y-%m-%d %H:%M:%S');   local LASTLOG=$(tail -n 5 "$LOG_FILE" 2>/dev/null); 
  cat > "$STATUS_FILE" <<EOF
<!DOCTYPE html>
<html lang="fr">
<head>
<meta charset="UTF-8">
<title>Status - $PROJECT</title>
<style>
body{font-family:Arial,Helvetica,sans-serif;background:#0b0c10;color:#eee;margin:0;padding:20px;}
h1{color:#66fcf1;}
.card{background:#1f2833;padding:15px;border-radius:10px;margin-bottom:10px;}
.green{color:#45a29e;} .red{color:#ff5555;} .yellow{color:#f1c40f;}
pre{background:#0b0c10;color:#c5c6c7;padding:10px;border-radius:5px;overflow:auto;}
small{color:#888;}
</style>
</head>
<body>
<h1>🛰️ $PROJECT</h1>
<div class="card">
  <b>Statut actuel :</b> <span class="$COLOR">$STATUS</span><br>
  <b>Adresse locale :</b> <a href="$URL" style="color:#66fcf1">$URL</a><br>
  <b>PID :</b> $PID<br>
  <b>Dernière mise à jour :</b> $DATE
</div>
<div class="card">
  <b>Derniers logs :</b>
  <pre>$LASTLOG</pre>
</div>
<small>Fichier généré automatiquement par Sentinel Supervisor v4.8</small>
</body>
</html>
EOF
 }
# --- Envoi Telegram ---
send_tg() {   curl -s -X POST "https://api.telegram.org/bot$BOT_TOKEN/sendMessage"     -d chat_id="$CHAT_ID" -d parse_mode="Markdown"     -d disable_notification=false -d text="$1" >/dev/null; }
send_file() {   [ -f "$2" ] && curl -s -F chat_id="$CHAT_ID"     -F document=@"$2" -F caption="$1"     "https://api.telegram.org/bot$BOT_TOKEN/sendDocument" >/dev/null; }
# --- Menu Telegram ---
send_menu() {   local MSG="$1";   local REPLY='{"keyboard":[[{"text":"/status"},{"text":"/restart"},{"text":"/logs"},{"text":"/deploy"}]],"resize_keyboard":true}';   curl -s -X POST "https://api.telegram.org/bot$BOT_TOKEN/sendMessage"     -d chat_id="$CHAT_ID" -d parse_mode="Markdown"     -d disable_notification=false -d text="$MSG" -d reply_markup="$REPLY" >/dev/null; }
# --- Lancement du serveur
#!/bin/bash
# Sentinel Quantum Vanguard AI Pro — Supervisor v4.8 HTML Status Dashboard + Local Access
BOT_TOKEN="7630324617:AAGYNTiOIhHFDyT83Qt_DezDUxEzEJf-K9E"
CHAT_ID="5707814118"
PROJECT="Sentinel Quantum Vanguard AI Pro"
PORT=5173
LOG_FILE="dev_log.txt"
LAST_UPDATE_FILE=".tg_last_update"
STATUS_FILE="status.html"
RETRIES=3
DEPLOY_LOG="deploy_log.txt"
# --- Détection IP ---
IP=$(ip -4 addr show wlan0 2>/dev/null | grep -oP '(?<=inet\s)\d+(\.\d+){3}' | head -n1)
[ -z "$IP" ] && IP="127.0.0.1"
URL="http://$IP:$PORT"
# --- Génère le fichier HTML de statut ---
generate_status_html() {   local STATUS="$1";   local COLOR="$2";   local DATE=$(date '+%Y-%m-%d %H:%M:%S');   local LASTLOG=$(tail -n 5 "$LOG_FILE" 2>/dev/null); 
  cat > "$STATUS_FILE" <<EOF
<!DOCTYPE html>
<html lang="fr">
<head>
<meta charset="UTF-8">
<title>Status - $PROJECT</title>
<style>
body{font-family:Arial,Helvetica,sans-serif;background:#0b0c10;color:#eee;margin:0;padding:20px;}
h1{color:#66fcf1;}
.card{background:#1f2833;padding:15px;border-radius:10px;margin-bottom:10px;}
.green{color:#45a29e;} .red{color:#ff5555;} .yellow{color:#f1c40f;}
pre{background:#0b0c10;color:#c5c6c7;padding:10px;border-radius:5px;overflow:auto;}
small{color:#888;}
</style>
</head>
<body>
<h1>🛰️ $PROJECT</h1>
<div class="card">
  <b>Statut actuel :</b> <span class="$COLOR">$STATUS</span><br>
  <b>Adresse locale :</b> <a href="$URL" style="color:#66fcf1">$URL</a><br>
  <b>PID :</b> $PID<br>
  <b>Dernière mise à jour :</b> $DATE
</div>
<div class="card">
  <b>Derniers logs :</b>
  <pre>$LASTLOG</pre>
</div>
<small>Fichier généré automatiquement par Sentinel Supervisor v4.8</small>
</body>
</html>
EOF
 }
# --- Envoi Telegram ---
send_tg() {   curl -s -X POST "https://api.telegram.org/bot$BOT_TOKEN/sendMessage"     -d chat_id="$CHAT_ID" -d parse_mode="Markdown"     -d disable_notification=false -d text="$1" >/dev/null; }
send_file() {   [ -f "$2" ] && curl -s -F chat_id="$CHAT_ID"     -F document=@"$2" -F caption="$1"     "https://api.telegram.org/bot$BOT_TOKEN/sendDocument" >/dev/null; }
# --- Menu Telegram ---
send_menu() {   local MSG="$1";   local REPLY='{"keyboard":[[{"text":"/status"},{"text":"/restart"},{"text":"/logs"},{"text":"/deploy"}]],"resize_keyboard":true}';   curl -s -X POST "https://api.telegram.org/bot$BOT_TOKEN/sendMessage"     -d chat_id="$CHAT_ID" -d parse_mode="Markdown"     -d disable_notification=false -d text="$MSG" -d reply_markup="$REPLY" >/dev/null; }
# --- Lancement du serveur ---
launch_servers() {   echo "[$(date)] 🚀 Démarrage dev..." >>"$LOG_FILE";   (npx concurrently "npm run dev" "npm run functions" >>"$LOG_FILE" 2>&1) &   PID=$!;   sleep 4;   if ps -p $PID >/dev/null 2>&1; then     generate_status_html "🟢 En ligne" "green";     send_file "✅ $PROJECT lancé sur [$URL]($URL) 🔊" "$STATUS_FILE";   else     generate_status_html "🔴 Échec du démarrage" "red";     send_file "⚠️ Erreur de démarrage 🔊" "$STATUS_FILE";   fi; }
# --- Déploiement Cloudflare ---
deploy_project() {   echo "[$(date)] 🚀 Déploiement Cloudflare..." >>"$DEPLOY_LOG";   if npx wrangler pages deploy 2>&1 | tee -a "$DEPLOY_LOG"; then     generate_status_html "🚀 Déployé avec succès" "green";     send_file "🚀 Déploiement réussi 🔊" "$STATUS_FILE";   else     generate_status_html "⚠️ Échec déploiement" "red";     send_file "⚠️ Échec du déploiement 🔊" "$STATUS_FILE";   fi; }
# --- Commandes Telegram ---
check_tg_commands() {   local offset=$(cat "$LAST_UPDATE_FILE" 2>/dev/null || echo 0);   local resp=$(curl -s "https://api.telegram.org/bot$BOT_TOKEN/getUpdates?offset=$offset");   local updates=$(echo "$resp" | jq -c '.result[]?');   for up in $updates; do     local id=$(echo "$up" | jq '.update_id');     local txt=$(echo "$up" | jq -r '.message.text // empty');     echo $((id + 1)) >"$LAST_UPDATE_FILE";      case "$txt" in       "/status"|"status") generate_status_html "🟢 En ligne" "green"; send_file "📊 État actuel du projet" "$STATUS_FILE" ;;       "/restart"|"restart") send_tg "🔁 Redémarrage demandé... 🔊"; kill $PID 2>/dev/null; sleep 3; launch_servers ;;       "/logs"|"logs") send_file "📄 Logs récents" "$LOG_FILE" ;;       "/deploy"|"deploy") send_tg "🚀 Déploiement manuel demandé 🔊"; deploy_project ;;     esac;   done; }
# --- Démarrage initial ---
send_menu "🧠 *$PROJECT*\nMode dev initialisé sur [$URL]($URL) 🔊"
launch_servers
# --- Boucle principale ---
while true; do   check_tg_commands;   if ! ps -p $PID >/dev/null 2>&1; then     generate_status_html "🟥 Crash détecté" "red";     send_file "🚨 Crash détecté 🔊 — tentative de redémarrage" "$STATUS_FILE";     for ((i=1;i<=RETRIES;i++)); do       launch_servers; sleep 8;       if ps -p $PID >/dev/null 2>&1; then         generate_status_html "🟢 Redémarrage réussi (tentative $i)" "green";         send_file "✅ Redémarrage réussi (tentative $i) 🔊" "$STATUS_FILE";         break;       fi;     done;   fi;   sleep 15; done
# --- Serveur local minimal pour le dashboard HTML ---
start_dashboard_server() {   node -e "
    const http = require('http');
    const fs = require('fs');
    const port = 8081;
    http.createServer((req, res) => {
      fs.readFile('status.html', (err, data) => {
        if (err) {
          res.writeHead(500, {'Content-Type': 'text/plain'});
          res.end('Erreur chargement status.html');
        } else {
          res.writeHead(200, {'Content-Type': 'text/html'});
          res.end(data);
        }
      });
    }).listen(port, () => console.log('🧠 Dashboard accessible sur http://127.0.0.1:' + port + '/status.html'));
  " & }
# Démarre le mini serveur au lancement
start_dashboard_server
chmod +x start_dev.sh
./start_dev.sh
chmod +x start_dev.sh
./start_dev.sh
# --- Génère le fichier HTML de statut (v4.9 Live Refresh + Audio Alert) ---
generate_status_html() {   local STATUS="$1";   local COLOR="$2";   local DATE=$(date '+%Y-%m-%d %H:%M:%S');   local LASTLOG=$(tail -n 6 "$LOG_FILE" 2>/dev/null); 
  cat > "$STATUS_FILE" <<EOF
<!DOCTYPE html>
<html lang="fr">
<head>
<meta charset="UTF-8">
<meta http-equiv="refresh" content="20">
<title>Status - $PROJECT</title>
<style>
body{font-family:Arial,Helvetica,sans-serif;background:#0b0c10;color:#eee;margin:0;padding:20px;}
h1{color:#66fcf1;}
.card{background:#1f2833;padding:15px;border-radius:10px;margin-bottom:10px;}
.green{color:#45a29e;} .red{color:#ff5555;} .yellow{color:#f1c40f;}
pre{background:#0b0c10;color:#c5c6c7;padding:10px;border-radius:5px;overflow:auto;}
small{color:#888;}
audio{display:none;}
</style>
</head>
<body>
<h1>🛰️ $PROJECT</h1>

<div class="card">
  <b>Statut actuel :</b> <span class="$COLOR">$STATUS</span><br>
  <b>Adresse locale :</b> <a href="$URL" style="color:#66fcf1">$URL</a><br>
  <b>PID :</b> $PID<br>
  <b>Dernière mise à jour :</b> $DATE
</div>

<div class="card">
  <b>Derniers logs :</b>
  <pre>$LASTLOG</pre>
</div>

<audio autoplay>
  <source src="https://assets.mixkit.co/sfx/preview/mixkit-software-interface-back-2575.mp3" type="audio/mpeg">
</audio>

<small>Fichier généré automatiquement par Sentinel Supervisor v4.9 — Auto-Refresh + Audio Alert</small>
</body>
</html>
EOF
 }
chmod +x start_dev.sh
./start_dev.sh
generate_status_html() {   local STATUS="$1";   local COLOR="$2";   local DATE=$(date '+%Y-%m-%d %H:%M:%S');   local LASTLOG=$(tail -n 6 "$LOG_FILE" 2>/dev/null);   local CPU=$(top -bn1 | grep -E "Cpu" | awk '{print $2}' | cut -d'.' -f1 2>/dev/null);   local MEM_USED=$(free -m | awk '/Mem:/ {print $3}');   local MEM_TOTAL=$(free -m | awk '/Mem:/ {print $2}');   local MEM_PERC=$((100*MEM_USED/MEM_TOTAL));   local UPTIME=$(ps -p $PID -o etime= 2>/dev/null | tr -d ' ');   local CRASH_LOG="crash_history.log"; 
  [ -f "$CRASH_LOG" ] && LAST_CRASHES=$(tail -n 3 "$CRASH_LOG") || LAST_CRASHES="Aucun"; 
  cat > "$STATUS_FILE" <<EOF
<!DOCTYPE html>
<html lang="fr">
<head>
<meta charset="UTF-8">
<meta http-equiv="refresh" content="20">
<title>Status - $PROJECT</title>
<style>
body{font-family:Arial,Helvetica,sans-serif;background:#0b0c10;color:#eee;margin:0;padding:20px;}
h1{color:#66fcf1;}
.card{background:#1f2833;padding:15px;border-radius:10px;margin-bottom:10px;}
.green{color:#45a29e;} .red{color:#ff5555;} .yellow{color:#f1c40f;}
.progress{background:#0b0c10;border-radius:8px;overflow:hidden;height:10px;margin:5px 0;}
.bar{height:10px;background:#45a29e;}
pre{background:#0b0c10;color:#c5c6c7;padding:10px;border-radius:5px;overflow:auto;}
small{color:#888;}
</style>
</head>
<body>
<h1>🛰️ $PROJECT</h1>

<div class="card">
  <b>Statut :</b> <span class="$COLOR">$STATUS</span><br>
  <b>Adresse :</b> <a href="$URL" style="color:#66fcf1">$URL</a><br>
  <b>PID :</b> $PID<br>
  <b>Uptime :</b> ${UPTIME:-non disponible}<br>
  <b>Mise à jour :</b> $DATE
</div>

<div class="card">
  <b>Utilisation CPU :</b> $CPU% 
  <div class="progress"><div class="bar" style="width:${CPU}%"></div></div>
  <b>Utilisation RAM :</b> ${MEM_PERC}% ($MEM_USED / $MEM_TOTAL Mo)
  <div class="progress"><div class="bar" style="width:${MEM_PERC}%"></div></div>
</div>

<div class="card">
  <b>Historique des crashs récents :</b>
  <pre>$LAST_CRASHES</pre>
</div>

<div class="card">
  <b>Derniers logs :</b>
  <pre>$LASTLOG</pre>
</div>

<audio autoplay>
  <source src="https://assets.mixkit.co/sfx/preview/mixkit-software-interface-back-2575.mp3" type="audio/mpeg">
</audio>

<small>Sentinel Supervisor v5.0 — Quantum Metrics & Crash History</small>
</body>
</html>
EOF
 }
log_crash_event() {   echo "$(date '+%Y-%m-%d %H:%M:%S') — Crash détecté, tentative de redémarrage" >> crash_history.log; }
log_crash_event
chmod +x start_dev.sh
./start_dev.sh
generate_status_html() {   local STATUS="$1";   local COLOR="$2";   local DATE=$(date '+%Y-%m-%d %H:%M:%S');   local LASTLOG=$(tail -n 6 "$LOG_FILE" 2>/dev/null);   local CPU=$(top -bn1 | grep -E "Cpu" | awk '{print $2}' | cut -d'.' -f1 2>/dev/null);   local MEM_USED=$(free -m | awk '/Mem:/ {print $3}');   local MEM_TOTAL=$(free -m | awk '/Mem:/ {print $2}');   local MEM_PERC=$((100*MEM_USED/MEM_TOTAL));   local UPTIME=$(ps -p $PID -o etime= 2>/dev/null | tr -d ' ');   local METRICS_FILE="metrics.log";   local CRASH_LOG="crash_history.log"; 
  echo "$(date '+%H:%M:%S'),$CPU,$MEM_PERC" >> "$METRICS_FILE";   tail -n 100 "$METRICS_FILE" > "$METRICS_FILE.tmp" && mv "$METRICS_FILE.tmp" "$METRICS_FILE"; 
  local TIMES=($(awk -F, '{print $1}' "$METRICS_FILE"));   local CPUS=($(awk -F, '{print $2}' "$METRICS_FILE"));   local RAMS=($(awk -F, '{print $3}' "$METRICS_FILE"));   local LAST_CRASHES="Aucun";   [ -f "$CRASH_LOG" ] && LAST_CRASHES=$(tail -n 3 "$CRASH_LOG"); 
  cat > "$STATUS_FILE" <<EOF
<!DOCTYPE html>
<html lang="fr">
<head>
<meta charset="UTF-8">
<meta http-equiv="refresh" content="20">
<title>Status - $PROJECT</title>
<style>
body{font-family:Arial,Helvetica,sans-serif;background:#0b0c10;color:#eee;margin:0;padding:20px;}
h1{color:#66fcf1;}
.card{background:#1f2833;padding:15px;border-radius:10px;margin-bottom:10px;}
.green{color:#45a29e;} .red{color:#ff5555;} .yellow{color:#f1c40f;}
svg{width:100%;height:120px;background:#0b0c10;border-radius:8px;margin-top:5px;}
path{fill:none;stroke-width:2;}
path.cpu{stroke:#45a29e;}
path.ram{stroke:#f1c40f;}
pre{background:#0b0c10;color:#c5c6c7;padding:10px;border-radius:5px;overflow:auto;}
small{color:#888;}
</style>
</head>
<body>
<h1>🛰️ $PROJECT</h1>

<div class="card">
  <b>Statut :</b> <span class="$COLOR">$STATUS</span><br>
  <b>Adresse :</b> <a href="$URL" style="color:#66fcf1">$URL</a><br>
  <b>PID :</b> $PID<br>
  <b>Uptime :</b> ${UPTIME:-non disponible}<br>
  <b>Mise à jour :</b> $DATE
</div>

<div class="card">
  <b>Performances 24h :</b>
  <svg viewBox="0 0 100 100" preserveAspectRatio="none">
    <path class="cpu" d="M0,${CPUS[0]:-50} 
EOF
 
  local i=0;   for cpu in "${CPUS[@]}"; do     echo "L${i},$((100 - cpu))" >> "$STATUS_FILE";     ((i+=3));   done; 
  cat >> "$STATUS_FILE" <<EOF
" />
    <path class="ram" d="M0,${RAMS[0]:-50} 
EOF
    i=0;   for ram in "${RAMS[@]}"; do     echo "L${i},$((100 - ram))" >> "$STATUS_FILE";     ((i+=3));   done; 
  cat >> "$STATUS_FILE" <<EOF
" />
  </svg>
  <div style="font-size:12px;color:#aaa;">CPU = vert • RAM = jaune</div>
</div>

<div class="card">
  <b>Historique des crashs récents :</b>
  <pre>$LAST_CRASHES</pre>
</div>

<div class="card">
  <b>Derniers logs :</b>
  <pre>$LASTLOG</pre>
</div>

<audio autoplay>
  <source src="https://assets.mixkit.co/sfx/preview/mixkit-software-interface-back-2575.mp3" type="audio/mpeg">
</audio>

<small>Sentinel Supervisor v5.1 — Quantum Graphs & Metrics Live View</small>
</body>
</html>
EOF
 }
chmod +x start_dev.sh
./start_dev.sh
cd ~/Sentinel
nano start_dev.sh
chmod +x start_dev.sh
./start_dev.sh
cd SentinelQuantumVanguardAIPro
git pull origin main
npm install
npm run dev
