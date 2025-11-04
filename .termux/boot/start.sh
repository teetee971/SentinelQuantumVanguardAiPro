#!/data/data/com.termux/files/usr/bin/bash
# dashboard
pgrep -af "sentinel-dashboard/app.py" >/dev/null || \
  nohup python ~/sentinel-dashboard/app.py >/dev/null 2>&1 &
# supervisor (relance les agents si morts)
pgrep -af "sentinel-supervisor.sh" >/dev/null || \
  nohup ~/scripts/sentinel-supervisor.sh >/dev/null 2>&1 &
