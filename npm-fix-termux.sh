#!/bin/bash
echo "🔧 Correction npm Termux..."
pkg install -y nodejs-lts
npm_path=$(find /data/data/com.termux/files/usr/lib -type f -name "npm-cli.js" 2>/dev/null | head -n 1)
if [ -n "$npm_path" ]; then
  ln -sf "$npm_path" /data/data/com.termux/files/usr/bin/npm
  echo "✅ npm réparé et lié à : $npm_path"
else
  echo "❌ npm introuvable, réinstalle nodejs-lts."
fi
npm -v && node -v
