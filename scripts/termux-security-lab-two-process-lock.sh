#!/data/data/com.termux/files/usr/bin/bash
# Real, separate JVM processes contend over the same advisory file lock.
# Uses a disposable temporary directory; no Android app is touched.
set -euo pipefail
command -v python >/dev/null || { echo "BLOQUE: Python absent"; exit 2; }
python - <<'PY'
import fcntl, multiprocessing as mp, os, tempfile, time
def contender(path, queue):
    with open(path, 'a+b') as f:
        try:
            fcntl.flock(f, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            queue.put("DENIED")
            return
        else:
            queue.put("ACQUIRED")
            fcntl.flock(f, fcntl.LOCK_UN)
def probe(path, context, expected):
    q = context.Queue()
    child = context.Process(target=contender, args=(path,q))
    child.start()
    child.join(timeout=6)
    if child.is_alive():
        child.terminate()
        child.join(2)
        raise RuntimeError("Child process hung")
    if child.exitcode != 0:
        raise RuntimeError(f"Child error: {child.exitcode}")
    actual = q.get(timeout=2)
    print(f"CHILD_EXPECTED={expected} CHILD_ACTUAL={actual}")
    if actual != expected:
        raise RuntimeError("Cross-process lock invariant failed")
with tempfile.TemporaryDirectory(prefix="sentinel-lock-proof-") as directory:
    path=os.path.join(directory, ".sentinel_apk_inspection.lock")
    ctx=mp.get_context("fork")
    with open(path, 'a+b') as holder:
        fcntl.flock(holder,fcntl.LOCK_EX|fcntl.LOCK_NB)
        probe(path,ctx,"DENIED")
        fcntl.flock(holder,fcntl.LOCK_UN)
    probe(path,ctx,"ACQUIRED")
print("VERDICT=VERROU_OS_DEUX_PROCESSUS_VALIDÉ_SUR_TERMUX")
print("PORTÉE=verrou OS sur Termux ; test de classe Kotlin Android multiprocessus non exécuté")
PY
