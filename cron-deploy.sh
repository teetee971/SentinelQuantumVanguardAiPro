#!/data/data/com.termux/files/usr/bin/bash
cd ~/SentinelQuantumVanguardAiPro
ts=$(date +'%Y-%m-%d_%H-%M')
bash auto-deploy.sh >> logs/cron_$ts.log 2>&1
