#!/data/data/com.termux/files/usr/bin/bash
# Read-only validation of remote release candidate; no checkout, merge, build, signing or installation.
set -euo pipefail
REMOTE="https://github.com/teetee971/SentinelQuantumVanguardAiPro.git"
BRANCH="release/candidate-1.0.10-preflight-20261021"
EXPECTED="cb3f98d09cacce17fa430d131f532fe39ef716b9"
command -v git >/dev/null || { echo "BLOQUÉ : git absent"; exit 2; }
ACTUAL="$(git ls-remote "$REMOTE" "refs/heads/$BRANCH" | cut -f1)"
[[ "$ACTUAL" == "$EXPECTED" ]] || { echo "BLOQUÉ : branche candidate modifiée ou inaccessible ($ACTUAL)"; exit 2; }
echo "CANDIDAT=$ACTUAL"
echo "VERSION_CIBLE=1.0.10 / versionCode=11 (configuration GitHub, pas encore APK)"
echo "GATE_SIGNATURE=EN_ATTENTE : certificat de l'application installée non contrôlé"
echo "GATE_RUNTIME=EN_ATTENTE : tests instrumentés non exécutés"
echo "GATE_RELEASE=BLOQUÉ : aucun APK de production signé ou installé"
echo "VERDICT=CANDIDAT_IDENTIFIÉ — lecture seule"
