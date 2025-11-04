#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
source ~/.config/sentinel/.env 2>/dev/null || true
export GIT_TERMINAL_PROMPT=0
export GIT_ASKPASS=$(which true)

logdir="$HOME/logs"; mkdir -p "$logdir"

declare -A CMDS=(
  ["AutoResolve"]="$HOME/scripts/sentinel-auto-resolve.sh"
  ["Recovery Agent"]="$HOME/scripts/sentinel-recovery-agent.sh"
  ["Watchdog v2"]="$HOME/scripts/sentinel-watchdog-v2.sh"
  ["Network Guardian"]="$HOME/scripts/sentinel-network-guardian.sh"
)

ensure_one () {
  local name="$1" cmd="$2"
  mapfile -t pids < <(pgrep -f "$cmd" || true)
  if [ "${#pids[@]}" -gt 1 ]; then
    printf '%s\n' "${pids[@]:1}" | xargs -r kill
    echo "[$(date +'%F %T')] dedup $name (${#pids[@]}->1)" >>"$logdir/supervisor.log"
  fi
  pgrep -f "$cmd" >/dev/null && return 0
  nohup bash -c "$cmd" >>"$logdir/${name// /_}.log" 2>&1 &
  echo "[$(date +'%F %T')] started $name (pid $!)" >>"$logdir/supervisor.log"
}

while true; do
  for name in "${!CMDS[@]}"; do ensure_one "$name" "${CMDS[$name]}"; done
  sleep 15
done
