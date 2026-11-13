#!/data/data/com.termux/files/usr/bin/bash
# Sentinel Autocontrol V2 — lecture seule, aucune compilation ni installation.
set -euo pipefail
ROOT="$HOME/sentinel-qa-integrated-v2"
LOGDIR="$HOME/sentinel-qa-logs"
mkdir -p "$LOGDIR"
LOG="$LOGDIR/autocontrol-v2-$(date +%Y%m%d-%H%M%S)-$$.log"
exec > >(tee "$LOG") 2>&1
fail(){ echo "VERDICT=BLOQUÉ — $*"; exit 2; }
for c in git curl python3; do command -v "$c" >/dev/null || fail "$c absent"; done
[[ -d "$ROOT/.git" ]] || fail "Dépôt intégré absent"
LOCAL="$(git -C "$ROOT" rev-parse HEAD)"
[[ "$LOCAL" == 7c21bb1cdb9b0de8b7c0261b7e8fb8f98834704c ]] || fail "Snapshot local non qualifié"
[[ -z "$(git -C "$ROOT" status --porcelain --untracked-files=no)" ]] || fail "Sources modifiées"
TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT
HTTP="$(curl -sS -L --retry 2 --connect-timeout 10 --max-time 35 -H 'Accept: application/vnd.github+json' -H 'User-Agent: Sentinel-Autocontrol-ReadOnly' -o "$TMP" -w '%{http_code}' 'https://api.github.com/repos/teetee971/SentinelQuantumVanguardAiPro/pulls?state=open&per_page=100')" || fail "GitHub inaccessible"
[[ "$HTTP" == 200 ]] || fail "Réponse GitHub HTTP $HTTP"
python3 - "$TMP" <<'PY'
import json,sys
try:
    with open(sys.argv[1],encoding='utf-8') as f: data=json.load(f)
    if not isinstance(data,list): raise ValueError('Réponse non-liste')
    found={p['number']:p for p in data if isinstance(p,dict) and p.get('number') in (1718,1719,1720,1721)}
    for n in (1718,1719,1720,1721):
        p=found.get(n)
        if p is None:
            print(f'PR #{n}: absente des PR ouvertes; statut à vérifier manuellement')
        else:
            print(f"PR #{n}: {p['head']['sha']} — {p.get('html_url','')}")
    print('VERDICT=INFORMATION_GITHUB_REÇUE — aucun code distant approuvé')
except (ValueError,KeyError,TypeError,UnicodeError) as e:
    print('VERDICT=BLOQUÉ — réponse invalide:',e)
    sys.exit(2)
PY
echo "Aucune mise à jour, installation, fusion, compilation ou tâche récurrente exécutée"
echo "JOURNAL=$LOG"
