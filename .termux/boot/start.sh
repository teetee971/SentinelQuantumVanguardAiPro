#!/data/data/com.termux/files/usr/bin/bash
nohup ~/scripts/sentinel-supervisor.sh >/dev/null 2>&1 &
nohup python ~/sentinel-dashboard/app.py >/dev/null 2>&1 &
