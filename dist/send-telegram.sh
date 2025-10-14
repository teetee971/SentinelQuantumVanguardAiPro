#!/bin/bash
source ~/SentinelQuantumVanguardAiPro/dist/telegram.env

message="$1"

if [ -n "$message" ]; then
  curl -s -X POST "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendMessage" \
  -d chat_id="${TELEGRAM_CHAT_ID}" \
  -d text="$message" > /dev/null
fi
