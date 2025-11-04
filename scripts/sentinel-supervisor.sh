#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
logdir="$HOME/logs"; mkdir -p "$logdir"
declare -A CMDS=(
  ["AutoResolve"]="$HOME/scripts/sentinel-auto-resolve.sh"
  ["Recovery Agent"]="$HOME/scripts/sentinel-recovery-agent.sh"
  ["Watchdog v2"]="$HOME/scripts/sentinel-watchdog-v2.sh"
  ["Network Guardian"]="$HOME/scripts/sentinel-network-guardian.sh"
)
ensure() {
  local name="$1" cmd="$2"
  if ! pgrep -af "$cmd" >/dev/null; then
    nohup bash "$cmd" >>"$logdir/${name// /_}.log" 2>&1 &
    echo "[$(date +'%F %T')] started $name" >>"$logdir/supervisor.log"
  fi
}
while true; do
  for n in "${!CMDS[@]}"; do ensure "$n" "${CMDS[$n]}"; done
  sleep 15
done
