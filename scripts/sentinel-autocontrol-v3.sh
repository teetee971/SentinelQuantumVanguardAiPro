#!/data/data/com.termux/files/usr/bin/bash
# Sentinel Autocontrol V3: read-only PR watcher, hourly eligible; no auto install/push/build.
set -euo pipefail
BASE="$HOME/sentinel-autocontrol"
mkdir -p "$BASE"
command -v python3 >/dev/null || { echo "BLOQUÉ: python3 absent"; exit 2; }
command -v curl >/dev/null || { echo "BLOQUÉ: curl absent"; exit 2; }
command -v flock >/dev/null || { echo "BLOQUÉ: flock absent (pkg install util-linux)"; exit 2; }
exec 9>"$BASE/run.lock"
flock -n 9 || { echo "Autocontrol déjà en cours"; exit 0; }
TMP="$(mktemp "$BASE/github.XXXXXX")"
trap 'rm -f "$TMP"' EXIT
CODE="$(curl -sSL --retry 2 --connect-timeout 10 --max-time 40 -H 'Accept: application/vnd.github+json' -H 'User-Agent: Sentinel-Autocontrol-V3' -o "$TMP" -w '%{http_code}' 'https://api.github.com/repos/teetee971/SentinelQuantumVanguardAiPro/pulls?state=all&per_page=100')" || { echo "BLOQUÉ: GitHub inaccessible"; exit 2; }
[[ "$CODE" == "200" ]] || { echo "BLOQUÉ: GitHub HTTP $CODE"; exit 2; }
python3 - "$TMP" "$BASE/state.json" "$BASE" <<'PY'
import json,sys,os,tempfile,datetime,subprocess,shutil
source,state,base=sys.argv[1:]
targets=(1718,1719,1720,1721)
try:
    data=json.load(open(source,encoding='utf-8'))
    if not isinstance(data,list): raise ValueError('format GitHub incorrect')
    prs={p['number']:p for p in data if isinstance(p,dict) and p.get('number') in targets}
    # Incomplete pagination must never be interpreted as a deletion or change.
    if len(prs)!=len(targets): raise ValueError('PR manquantes : pagination/réponse incomplète')
    current={str(n):{'sha':prs[n]['head']['sha'],'state':prs[n]['state'],'merged':bool(prs[n].get('merged_at'))} for n in targets}
    for n in targets:
        if len(current[str(n)]['sha'])!=40: raise ValueError('SHA invalide')
    old=json.load(open(state,encoding='utf-8')) if os.path.isfile(state) else None
    changes=[f"PR #{n}: {old[str(n)]['sha'][:8]} → {current[str(n)]['sha'][:8]}" for n in targets if old and old.get(str(n))!=current[str(n)]]
    print('SENTINEL AUTOCONTROL V3 —',datetime.datetime.now().astimezone().isoformat(timespec='seconds'))
    for n in targets: print(f"PR #{n}: {current[str(n)]['sha'][:12]} ({current[str(n)]['state']})")
    if old is None: print('BASELINE=ENREGISTRÉE — aucune notification au premier passage')
    elif changes:
        print('CHANGEMENTS DÉTECTÉS:',*changes,sep='\n')
        if shutil.which('termux-notification'):
            try: subprocess.run(['termux-notification','--title','Sentinel : changement GitHub','--content','; '.join(changes)[:400],'--id','1987'],timeout=12,check=True)
            except Exception as e: print('Notification indisponible:',type(e).__name__)
        else: print('Notification non disponible : installer Termux:API et pkg install termux-api')
    else: print('AUCUN CHANGEMENT — compilation non nécessaire')
    fd,tmp=tempfile.mkstemp(prefix='.state-',dir=base)
    with os.fdopen(fd,'w',encoding='utf-8') as f: json.dump(current,f,indent=2)
    os.replace(tmp,state)
    print('VERDICT=LECTURE_SEULE — aucune fusion, build, installation ou push')
except (ValueError,KeyError,TypeError,UnicodeError,OSError) as e:
    print('VERDICT=BLOQUÉ —',e)
    sys.exit(2)
PY
