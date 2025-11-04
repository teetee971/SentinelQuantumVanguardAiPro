import os, subprocess, psutil, datetime, traceback
from collections import deque
from flask import Flask, render_template_string, jsonify

app = Flask(__name__)

# --------- ENV ----------
def load_env(path):
    path = os.path.expanduser(path)
    if not os.path.exists(path): return
    with open(path, "r", errors="replace") as f:
        for line in f:
            line=line.strip()
            if not line or line.startswith("#") or "=" not in line: continue
            k,v=line.split("=",1)
            os.environ.setdefault(k.strip(), v.strip())
load_env("~/.config/sentinel/.env")

# --------- CONST ----------
AGENTS = {
    "AutoResolve":         "sentinel-auto-resolve.sh",
    "Watchdog v1":         "sentinel-watchdog.sh",
    "Watchdog v2":         "sentinel-watchdog-v2.sh",
    "Network Guardian":    "sentinel-network-guardian.sh",
    "Recovery Agent":      "sentinel-recovery-agent.sh",
}
LOG_DIR = os.path.expanduser("~/logs")
ERR_LOG = os.path.join(LOG_DIR, "dashboard_errors.log")
os.makedirs(LOG_DIR, exist_ok=True)

# --------- HELPERS ----------
def log_exc(e):
    try:
        with open(ERR_LOG, "a", encoding="utf-8") as f:
            f.write("\n["+datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")+"]\n")
            f.write("".join(traceback.format_exception(e)))
    except Exception:
        pass

def is_proc_running(needle: str) -> bool:
    try:
        r = subprocess.run(["pgrep","-fa",needle], capture_output=True, text=True)
        if r.returncode != 0: return False
        return any(needle in line for line in r.stdout.splitlines())
    except Exception:
        return False

def read_tail(path: str, lines: int = 150) -> str:
    path = os.path.expanduser(path)
    if not os.path.exists(path): return "(pas de log)"
    try:
        with open(path, "r", errors="replace") as f:
            return "".join(deque(f, maxlen=lines)).rstrip()
    except Exception as e:
        return f"(erreur lecture log: {e})"

def uptime_str() -> str:
    boot = datetime.datetime.fromtimestamp(psutil.boot_time())
    d = datetime.datetime.now() - boot
    return f"{d.days}j {(d.seconds//3600):02d}h{((d.seconds%3600)//60):02d}m"

# --------- UI ----------
HTML = """
<!doctype html><html lang="fr"><meta charset="utf-8">
<title>Sentinel Quantum Dashboard</title>
<style>
body{font-family:Inter,Arial,sans-serif;background:#0b1220;color:#e6eefc;margin:0;padding:20px}
.card{background:#0f1a37;border:1px solid #26355a;border-radius:12px;padding:16px;margin:10px auto;max-width:980px}
.badge{padding:4px 8px;border-radius:12px;font-size:12px}
.ok{background:#133d2e;color:#7bffc1}.ko{background:#3d1313;color:#ff9b9b}
table{width:100%;border-collapse:collapse}th,td{border-bottom:1px solid #22314f;padding:8px;text-align:left}
th{color:#9fb3d9}.btn{display:inline-block;margin:6px 4px;padding:8px 12px;border-radius:10px;background:#142246;border:1px solid #2a3a68;color:#cfe1ff;text-decoration:none}
pre{white-space:pre-wrap;word-wrap:break-word;background:#0a142b;border:1px solid #21335b;border-radius:8px;padding:10px}
.footer{opacity:.7;margin-top:12px;font-size:12px}
</style>

<div class="card">
  <h1>Sentinel Quantum Vanguard — Dashboard</h1>
  <div>
    <span class="badge {{ 'ok' if health=='ok' else 'ko' }}">{{ 'OK' if health=='ok' else 'KO' }}</span>
    &nbsp;Uptime: {{ uptime }} — RAM: {{ mem }}%
    &nbsp;| <a class="btn" href="/admin">Admin</a>
    &nbsp;| <a class="btn" href="/healthz">Healthz</a>
  </div>
</div>

<div class="card">
  <h2>Agents</h2>
  <table>
    <tr><th>Agent</th><th>Script</th><th>Statut</th></tr>
    {% for name, row in agents.items() %}
      <tr>
        <td>{{ name }}</td>
        <td>{{ row.script }}</td>
        <td>{% if row.alive %}<span class="badge ok">Actif</span>{% else %}<span class="badge ko">Inactif</span>{% endif %}</td>
      </tr>
    {% endfor %}
  </table>
</div>

<div class="card">
  <h2>Logs récents</h2>
  {% for logname, content in logs.items() %}
    <h3>{{ logname }}</h3><pre>{{ content }}</pre>
  {% endfor %}
  <div class="footer">Dernière mise à jour : {{ now }}</div>
</div>
</html>
"""

# --------- ROUTES ----------
@app.route("/")
def index():
    try:
        agents_view = { n: {"script": s, "alive": is_proc_running(s)} for n,s in AGENTS.items() }
        logs = { s.replace(".sh",".log"): read_tail(os.path.join(LOG_DIR, s.replace(".sh",".log"))) for s in AGENTS.values() }
        health = "ok" if all(a["alive"] for a in agents_view.values()) else "ko"
        return render_template_string(
            HTML,
            health=health,
            uptime=uptime_str(),
            mem=psutil.virtual_memory().percent,
            agents=agents_view,
            logs=logs,
            now=datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        )
    except Exception as e:
        log_exc(e)
        return "<h1>Erreur interne</h1><p>Voir ~/logs/dashboard_errors.log</p>", 500

@app.route("/admin")
def admin():
    return render_template_string(
        "<h1 style='font-family:Inter,Arial'>Admin — lecture seule</h1>"
        "<p>Les actions seront activées après validation.</p>"
        "<p><a href='/'>← Retour Dashboard</a></p>"
    )

@app.route("/healthz")
def healthz():
    try:
        data = {name: is_proc_running(script) for name, script in AGENTS.items()}
        return jsonify({"status": "ok" if all(data.values()) else "degraded", "agents": data})
    except Exception as e:
        log_exc(e)
        return jsonify({"status":"error"}), 500

@app.errorhandler(Exception)
def _catch_all(e):
    log_exc(e)
    return "<h1>Erreur interne</h1><p>Trace écrite dans ~/logs/dashboard_errors.log</p>", 500

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000)
