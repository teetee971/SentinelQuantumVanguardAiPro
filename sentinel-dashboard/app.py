from flask import Flask, jsonify, render_template_string
import psutil, os, datetime

APP_NAME = "Sentinel Quantum Vanguard – Dashboard"

AGENTS = {
    "AutoResolve": "sentinel-auto-resolve.sh",
    "Recovery Agent": "sentinel-recovery-agent.sh",
    "Watchdog v2": "sentinel-watchdog-v2.sh",
    "Network Guardian": "sentinel-network-guardian.sh",
    "Supervisor": "sentinel-supervisor.sh",
}

def proc_matches_token(proc, token: str) -> bool:
    try:
        cmdline = proc.cmdline() or []
    except (psutil.NoSuchProcess, psutil.AccessDenied, psutil.ZombieProcess):
        return False
    for part in cmdline:
        if part.endswith(token) or token in part:
            return True
    return False

def is_running(token: str) -> bool:
    for p in psutil.process_iter(attrs=["pid","cmdline"]):
        if proc_matches_token(p, token):
            return True
    return False

HTML = """<!doctype html><html lang="fr"><head><meta charset="utf-8" />
<title>{{title}}</title>
<style>
 body{background:#0b0f14;color:#e6eef8;font-family:Inter,Arial;margin:0}
 header{padding:16px 20px;border-bottom:1px solid #19232f}
 h1{font-size:18px;margin:0}
 .wrap{max-width:900px;margin:24px auto;padding:0 16px}
 table{width:100%;border-collapse:collapse;margin:12px 0}
 th,td{padding:10px 12px;border-bottom:1px solid #1d2a38}
 th{text-align:left;color:#9fb3c8;font-weight:600}
 .ok{color:#13ce66}.bad{color:#ff5d5d}
 .meta{font-size:12px;color:#8aa2b3;margin-top:12px}
 .pill{display:inline-block;padding:2px 8px;border-radius:999px;font-size:12px}
 .pill-ok{background:#153b2b;color:#13ce66}
 .pill-degraded{background:#3a2a12;color:#f0ad4e}
 .pill-down{background:#3b1b1b;color:#ff5d5d}
 .cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:12px;margin-top:12px}
 .card{background:#0f1620;border:1px solid #1d2a38;border-radius:10px;padding:12px}
 .k{color:#9fb3c8;font-size:12px}.v{font-size:16px}
 a{color:#6cb8ff;text-decoration:none} a:hover{text-decoration:underline}
</style></head><body>
<header><h1>{{title}}</h1></header>
<div class="wrap">
  <div class="cards">
    <div class="card"><div class="k">Statut</div>
      <div class="v">
        {% if status=="ok" %}<span class="pill pill-ok">OK</span>
        {% elif status=="degraded" %}<span class="pill pill-degraded">Dégradé</span>
        {% else %}<span class="pill pill-down">Down</span>{% endif %}
      </div>
      <div class="meta">Dernière MAJ : {{ now }}</div>
    </div>
    <div class="card"><div class="k">Uptime (système)</div><div class="v">{{ uptime }}</div></div>
    <div class="card"><div class="k">Mémoire utilisée</div><div class="v">{{ mem }}%</div></div>
  </div>

  <h2 style="margin-top:18px;">Agents</h2>
  <table>
    <thead><tr><th>Nom</th><th>Processus</th><th>État</th></tr></thead>
    <tbody>
      {% for name, token in agents.items() %}
      {% set ok = statuses[name] %}
      <tr>
        <td>{{ name }}</td>
        <td><code>{{ token }}</code></td>
        <td class="{{ 'ok' if ok else 'bad' }}">{{ 'Actif' if ok else 'Arrêté' }}</td>
      </tr>
      {% endfor %}
    </tbody>
  </table>

  <div class="meta">
    API: <a href="/healthz">/healthz</a> • Debug: <a href="/debug/procs">/debug/procs</a>
  </div>
</div></body></html>"""

app = Flask(__name__)

@app.route("/")
def index():
    statuses = {name: is_running(token) for name, token in AGENTS.items()}
    status = "ok" if all(statuses.values()) else ("degraded" if any(statuses.values()) else "down")
    vm = psutil.virtual_memory()
    boot = datetime.datetime.fromtimestamp(psutil.boot_time())
    return render_template_string(
        HTML, title=APP_NAME + (" – OK" if status=="ok" else " – " + status.upper()),
        agents=AGENTS, statuses=statuses, status=status,
        mem=round(vm.percent,1), uptime=(datetime.datetime.now()-boot),
        now=datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
    )

@app.route("/healthz")
def healthz():
    data = {name: is_running(token) for name, token in AGENTS.items()}
    status = "ok" if all(data.values()) else ("degraded" if any(data.values()) else "down")
    return jsonify({"status": status, "agents": data})


@app.route("/debug/procs")
def debug_procs():
    import subprocess, json
    try:
        out = subprocess.run(["pgrep","-af","sentinel"], capture_output=True, text=True)
        items = []
        for line in (out.stdout or "").splitlines():
            line = line.strip()
            if not line: 
                continue
            pid, *rest = line.split(" ", 1)
            cmd = rest[0] if rest else ""
            try: pid = int(pid)
            except: continue
            items.append({"pid": pid, "cmd": cmd})
        return jsonify({"count": len(items), "items": items})
    except Exception as e:
        return jsonify({"error": str(e)}), 500

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000)
