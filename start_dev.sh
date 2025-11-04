#!/data/data/com.termux/files/usr/bin/bash
# Sentinel Quantum Vanguard AI Pro – Supervisor v5.1
# Quantum Graphs & Metrics Live View

PROJECT="Sentinel Quantum Vanguard AI Pro"
URL="http://127.0.0.1:5173"
STATUS_FILE="status.html"
LOG_FILE="sentinel.log"
CRASH_LOG="crash_history.log"
METRICS_FILE="metrics.log"

# --- Fonction d'envoi Telegram ---
send_msg() {
  local MSG="$1"
  echo "$MSG"
}

# --- Enregistrement des crashs ---
log_crash_event() {
  echo "$(date '+%Y-%m-%d %H:%M:%S') — Crash détecté, tentative de redémarrage" >> "$CRASH_LOG"
}

# --- Génération du dashboard HTML ---
generate_status_html() {
  local STATUS="$1"
  local COLOR="$2"
  local DATE=$(date '+%Y-%m-%d %H:%M:%S')
  local LASTLOG=$(tail -n 6 "$LOG_FILE" 2>/dev/null)
  local CPU=$(top -bn1 | grep -E "Cpu" | awk '{print $2}' | cut -d'.' -f1 2>/dev/null)
  local MEM_USED=$(free -m | awk '/Mem:/ {print $3}')
  local MEM_TOTAL=$(free -m | awk '/Mem:/ {print $2}')
  local MEM_PERC=$((100*MEM_USED/MEM_TOTAL))
  local UPTIME=$(ps -p $PID -o etime= 2>/dev/null | tr -d ' ')

  echo "$(date '+%H:%M:%S'),$CPU,$MEM_PERC" >> "$METRICS_FILE"
  tail -n 100 "$METRICS_FILE" > "$METRICS_FILE.tmp" && mv "$METRICS_FILE.tmp" "$METRICS_FILE"

  local TIMES=($(awk -F, '{print $1}' "$METRICS_FILE"))
  local CPUS=($(awk -F, '{print $2}' "$METRICS_FILE"))
  local RAMS=($(awk -F, '{print $3}' "$METRICS_FILE"))
  local LAST_CRASHES="Aucun"
  [ -f "$CRASH_LOG" ] && LAST_CRASHES=$(tail -n 3 "$CRASH_LOG")

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
  <svg viewBox="0 0 300 100" preserveAspectRatio="none">
    <path class="cpu" d="M0,${CPUS[0]:-50} 
EOF

  local i=0
  for cpu in "${CPUS[@]}"; do
    echo "L${i},$((100 - cpu))" >> "$STATUS_FILE"
    ((i+=3))
  done

  cat >> "$STATUS_FILE" <<EOF
" />
    <path class="ram" d="M0,${RAMS[0]:-50} 
EOF

  i=0
  for ram in "${RAMS[@]}"; do
    echo "L${i},$((100 - ram))" >> "$STATUS_FILE"
    ((i+=3))
  done

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

<small>Sentinel Supervisor v5.1 — Quantum Graphs & Metrics Live View</small>
</body>
</html>
EOF
}

# --- Lancement du serveur Vite / Functions ---
launch_servers() {
  npx concurrently "npm run dev" "npm run functions" >> "$LOG_FILE" 2>&1 &
  PID=$!
}

# --- Boucle de supervision ---
supervise() {
  while true; do
    generate_status_html "🟢 En ligne" "green"
    sleep 30
    if ! ps -p $PID > /dev/null; then
      log_crash_event
      send_msg "⚠️ Crash détecté — tentative de redémarrage..."
      launch_servers
      send_msg "✅ Redémarrage réussi ($(date '+%H:%M:%S'))"
    fi
  done
}

# --- Démarrage ---
echo "🚀 $PROJECT — Mode dev initialisé sur $URL"
launch_servers
supervise
