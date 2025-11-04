#!/usr/bin/env bash
set -euo pipefail
source ~/.config/sentinel/.env
cd ~/SentinelQuantumVanguardAiPro
./scripts/sentinel-auto-resolve.sh >> ~/sentinel-auto-resolve.log 2>&1
