cd ~/SentinelQuantumVanguardAiPro
nano .github/workflows/sentinel-quantum-matrix.yml   # colle le contenu ci-dessus
https://github.com/teetee971/SentinelQuantumVanguardAiPro/actions/runs/18515768498/job/52765870121
name: ♻️ Wave 33 – Sentinel Continuum Backup & Rollback Core
on:
permissions:
jobs:
# Supprimer la trace du gros fichier du commit
git rm --cached backups/*.zip
git commit -m "🔥 Retrait des fichiers de backup >100Mo (non suivis par Git)"
echo "backups/" >> .gitignore
git add .gitignore
git commit -m "🧱 Ignore backups folder"
git push -u origin main
cd ~/SentinelQuantumVanguardAiPro
# Supprime le dossier local de backup du suivi Git
rm -rf backups
echo "backups/" >> .gitignore
# Nettoyage complet des fichiers trop lourds de l'historique
git filter-branch --force --index-filter   'git rm --cached --ignore-unmatch backups/*.zip'   --prune-empty --tag-name-filter cat -- --all
# Recompacte le dépôt local
rm -rf .git/refs/original/
git reflog expire --expire=now --all
git gc --prune=now --aggressive
git add .gitignore
git commit -m "🔥 Purge des fichiers >100Mo + ajout .gitignore"
git push -u origin main --force
cd ~/SentinelQuantumVanguardAiPro
# Supprime tout dossier ou zip de backup restant
rm -rf backups
find . -name "*.zip" -type f -delete
# Vérifie qu'il n'y a plus aucun fichier zip
find . -name "*.zip"
pip install git-filter-repo
d ~/SentinelQuantumVanguardAiPro
# Purge tous les fichiers ZIP et le dossier backups de l'historique
git filter-repo --invert-paths --path backups/ --path-glob '*.zip'
git reflog expire --expire=now --all
git gc --prune=now --aggressive
git add .gitignore
git commit -m "🔥 Purge complète des gros fichiers + ajout .gitignore"
git push origin main --force
cd ~/SentinelQuantumVanguardAiPro
git filter-repo --path backups.zip --invert-paths
git reflog expire --expire=now --all
git gc --prune=now --aggressive
git add .gitignore
git commit -m "🔥 Purge définitive du fichier backups.zip >100 Mo"
git push origin main --force
cd ~/SentinelQuantumVanguardAiPro
git restore .
git reset --hard
git filter-repo --force --invert-paths --path backups.zip --path-glob '*.zip' --path backups/
git reflog expire --expire=now --all
git gc --prune=now --aggressive
cd ~/SentinelQuantumVanguardAiPro
git remote add origin https://github.com/teetee971/SentinelQuantumVanguardAiPro.git
git push -u origin main --force
cd ~/SentinelQuantumVanguardAiPro
mkdir -p .github/workflows
cat > .github/workflows/wave34-auto-infrastructure-healer.yml <<'EOF'
name: 🌐 Wave 34 – Sentinel Auto-Infrastructure Healer

on:
  schedule:
    - cron: "*/30 * * * *"
  workflow_dispatch:

jobs:
  heal:
    runs-on: ubuntu-latest
    name: 🔁 Auto-Healing Cycle
    steps:
      - name: ⚙️ Initialisation
        run: |
          echo "Starting Wave 34 – Auto-Infrastructure Healer"
          mkdir -p logs && echo "$(date '+%F %T') | Heal started" >> logs/healer.log

      - name: 🌍 Vérification Render
        run: |
          STATUS=$(curl -s -o /dev/null -w "%{http_code}" "https://sentinelquantumvanguardaipro.onrender.com")
          if [ "$STATUS" != "200" ]; then
            echo "Render offline – redeploy triggered"
            curl -X POST "https://api.render.com/v1/services/${{ secrets.RENDER_SERVICE_ID }}/deploys" \
                 -H "Authorization: Bearer ${{ secrets.RENDER_API_KEY }}"
          fi

      - name: ☁️ Purge Cloudflare
        run: |
          curl -s -X POST "https://api.cloudflare.com/client/v4/zones/${{ secrets.CLOUDFLARE_ZONE_ID }}/purge_cache" \
               -H "Authorization: Bearer ${{ secrets.CLOUDFLARE_API_TOKEN }}" \
               -H "Content-Type: application/json" --data '{"purge_everything":true}'
          echo "$(date '+%T') | Cloudflare cache purged" >> logs/healer.log

      - name: 🧠 Synchronisation DNS + SSL
        run: |
          echo "Checking DNS + SSL propagation..."
          nslookup sentinelquantumvanguardaipro.pages.dev >> logs/healer.log
          curl -Iv https://sentinelquantumvanguardaipro.pages.dev 2>&1 | grep "SSL" >> logs/healer.log || true

      - name: 🪶 Auto-Commit des réparations
        run: |
          git config --global user.name "Sentinel-Healer"
          git config --global user.email "healer@sentinel.ai"
          git add logs/healer.log
          git commit -m "🌐 Auto-Healer log update $(date +%T)" || echo "Nothing to commit"
          git push || echo "No push required"

      - name: 📲 Telegram (optionnel)
        if: always()
        run: |
          if [ -n "${{ secrets.TELEGRAM_WEBHOOK }}" ]; then
            curl -s -X POST ${{ secrets.TELEGRAM_WEBHOOK }} \
                 -d "text=🌐 Wave 34 exécutée – infrastructure réparée."
          fi
EOF

git add .github/workflows/wave34-auto-infrastructure-healer.yml
git commit -m "🌐 Add Wave 34 – Sentinel Auto-Infrastructure Healer"
git push
cd ~/SentinelQuantumVanguardAiPro
cat > .github/workflows/wave34-auto-infrastructure-healer.yml <<'EOF'
name: 🌐 Wave 34 – Sentinel Auto-Infrastructure Healer (Extended Report)

on:
  schedule:
    - cron: "*/30 * * * *"
  workflow_dispatch:

jobs:
  heal:
    runs-on: ubuntu-latest
    name: 🔁 Auto-Healing Cycle
    steps:
      - name: ⚙️ Initialisation
        run: |
          echo "Wave 34 – Healer started"
          mkdir -p logs
          echo "$(date '+%F %T') | Start" >> logs/healer.log

      - name: 🌍 Vérification Render
        id: render
        run: |
          STATUS=$(curl -s -o /dev/null -w "%{http_code}" "https://sentinelquantumvanguardaipro.onrender.com")
          echo "status=$STATUS" >> $GITHUB_OUTPUT
          echo "Render → $STATUS" >> logs/healer.log
          if [ "$STATUS" != "200" ]; then
            curl -s -X POST "https://api.render.com/v1/services/${{ secrets.RENDER_SERVICE_ID }}/deploys" \
                 -H "Authorization: Bearer ${{ secrets.RENDER_API_KEY }}"
          fi

      - name: ☁️ Purge Cloudflare
        id: cloudflare
        run: |
          RESULT=$(curl -s -X POST "https://api.cloudflare.com/client/v4/zones/${{ secrets.CLOUDFLARE_ZONE_ID }}/purge_cache" \
                   -H "Authorization: Bearer ${{ secrets.CLOUDFLARE_API_TOKEN }}" \
                   -H "Content-Type: application/json" --data '{"purge_everything":true}' | grep -o '"success":[^,]*')
          echo "cf=$RESULT" >> $GITHUB_OUTPUT
          echo "Cloudflare → $RESULT" >> logs/healer.log

      - name: 🔎 Vérification DNS + SSL
        id: dns
        run: |
          DNS=$(nslookup sentinelquantumvanguardaipro.pages.dev | grep Address | tail -n 1 | awk '{print $2}')
          SSL=$(curl -Iv https://sentinelquantumvanguardaipro.pages.dev 2>&1 | grep "SSL connection" | head -n 1 || true)
          echo "dns=$DNS" >> $GITHUB_OUTPUT
          echo "ssl=$SSL" >> $GITHUB_OUTPUT
          echo "DNS → $DNS" >> logs/healer.log
          echo "SSL → $SSL" >> logs/healer.log

      - name: 🧠 Auto-Commit du rapport
        run: |
          git config --global user.name "Sentinel-Healer"
          git config --global user.email "healer@sentinel.ai"
          git add logs/healer.log
          git commit -m "🧠 Healer log update $(date +%T)" || echo "No commit"
          git push || echo "No push needed"

      - name: 📲 Notification Telegram (étendue)
        if: always()
        run: |
          if [ -n "${{ secrets.TELEGRAM_WEBHOOK }}" ]; then
            MSG="🌐 Wave 34 terminée \n\n🧩 Render : ${{ steps.render.outputs.status }}\n☁️ Cloudflare : ${{ steps.cloudflare.outputs.cf }}\n📡 DNS : ${{ steps.dns.outputs.dns }}\n🔒 SSL : ${{ steps.dns.outputs.ssl }}"
            curl -s -X POST "${{ secrets.TELEGRAM_WEBHOOK }}" -d "text=$MSG"
          fi
EOF

git add .github/workflows/wave34-auto-infrastructure-healer.yml
git commit -m "🌐 Update Wave 34 – Extended Telegram Report"
git push
cd ~/SentinelQuantumVanguardAiPro
nano .github/workflows/wave34-auto-infrastructure-healer.yml
git add .github/workflows/wave34-auto-infrastructure-healer.yml
git commit -m "🌐 Wave 34 – Full Telegram Fix + Extended Health Report"
git push
name: 🌍 Wave 34 – Sentinel Auto-Infrastructure Healer (Extended Report + Alert)
on:
jobs:
git add .github/workflows/wave34-auto-infrastructure-healer.yml
git commit -m "🚨 Wave 34 – Add automatic Telegram alert system"
git push
npm login
npm install
npm run build
npm install -D tailwindcss postcss autoprefixer
npx tailwindcss init -p
export default {
}/** @type {import('tailwindcss').Config} */
export default {
}
@tailwind base;
@tailwind components;
@tailwind utilities;
npm run build
npm install -D tailwindcss postcss autoprefixer
npx tailwindcss init -p
npm install -D tailwindcss postcss autoprefixer
npm install -g npm@9
rm -rf node_modules package-lock.json
npm cache clean --force
rm -rf node_modules package-lock.json
npm cache clean --force
npm install -D tailwindcss postcss autoprefixer
npm run build
pkg install nodejs-lts -y
npm install -g npm@9
rm -rf node_modules package-lock.json
npm cache clean --force
npm install -D tailwindcss postcss autoprefixer
npm run build
pkg update -y && pkg upgrade -y
pkg install nodejs-lts -y
node -v
npm install -g npm@9
pkg uninstall nodejs -y
pkg uninstall nodejs-lts -y
pkg install nodejs -y
# 1. Installe la version LTS stable (20.x)
n 20
# 2. Vérifie que c’est bien pris en compte
node -v
npm -v
rm -rf node_modules package-lock.json
npm cache clean --force
npm install -D tailwindcss postcss autoprefixer
npm run build
npm install -g n
node /data/data/com.termux/files/usr/lib/node_modules/n/bin/n 20
mkdir -p ~/.npm-global
npm config set prefix '~/.npm-global'
export PATH=$PATH:~/.npm-global/bin
echo 'export PATH=$PATH:~/.npm-global/bin' >> ~/.bashrc
source ~/.bashrc
npm install -g n
n 20
# 1️⃣ Forcer une installation locale du gestionnaire de versions "n"
npm install n
# 2️⃣ Lancer Node 20 via le chemin direct
node ./node_modules/n/bin/n 20
node -v
npm -v
rm -rf node_modules package-lock.json
npm cache clean --force
npm install -D tailwindcss postcss autoprefixer vite
npx tailwindcss init -p
rm -rf node_modules package-lock.json
npm cache clean --force
npm install --legacy-peer-deps
npm install -D tailwindcss postcss autoprefixer vite
rm -rf node_modules package-lock.json
npm cache clean --force
npm install -D tailwindcss@latest postcss@8.4.47 autoprefixer@10.4.20 vite@latest --force
npm run build
rm -rf node_modules package-lock.json
npm cache clean --force
npm install -D tailwindcss postcss autoprefixer vite
cat postcss.config.js
nano postcss.config.js
rm -rf node_modules package-lock.json
npm cache clean --force
npm install -D tailwindcss postcss autoprefixer vite
npm run build
nano postcss.config.js
nano tailwind.config.js
npm run build
nano postcss.config.js
rm -rf node_modules package-lock.json
npm cache clean --force
npm install -D tailwindcss postcss autoprefixer vite
npm run build
nano postcss.config.js
rm -rf node_modules package-lock.json
npm cache clean --force
npm install -D tailwindcss postcss autoprefixer vite
npm run build
nano postcss.config.js
rm -rf node_modules package-lock.json
npm cache clean --force
npm install -D tailwindcss postcss autoprefixer vite
npm run build
nano postcss.config.js
rm -rf node_modules package-lock.json
npm cache clean --force
npm install -D tailwindcss postcss autoprefixer vite
npm run build
nano postcss.config.js
rm -rf node_modules package-lock.json
npm cache clean --force
npm install -D tailwindcss postcss autoprefixer vite
nano vite.config.js
npm run build
nano postcss.config.js
rm -rf node_modules package-lock.json
npm cache clean --force
npm install -D tailwindcss postcss autoprefixer vite
npm run build
rm postcss.config.js
nano postcss.config.js
rm -rf node_modules package-lock.json
npm cache clean --force
npm install -D tailwindcss postcss autoprefixer vite
npm install @vitejs/plugin-react
npm run build
ls dist
nano tailwind.config.js
npm install -D @tailwindcss/forms @tailwindcss/typography
npm run build
rm -rf node_modules package-lock.json
npm cache clean --force
npm install -D tailwindcss@latest postcss@latest autoprefixer@latest vite@latest @vitejs/plugin-react@latest
npx tailwindcss init -p
nano postcss.config.js
nano tailwind.config.js
npm install -D tailwindcss@latest postcss@latest autoprefixer@latest
npm run build
import React, { useEffect, useState } from "react";
export default function App() {
}
darkMode: "class",
npm run build
npm run preview
nano src/App.jsx
npm run build
npm run preview
nano src/App.jsx
npm run build
npm run preview
ls src
nano src/main.jsx
npm run build
npm run preview
nano index.html
npm run build
npm run preview
npm install -D @tailwindcss/forms
npm run build
npm run preview
npm cache clean --force
npm install tailwindcss postcss autoprefixer
rm -rf node_modules package-lock.json
npm cache clean --force
npm install -D tailwindcss@latest postcss@latest autoprefixer@latest --legacy-peer-deps
npm install -D @tailwindcss/forms
nano tailwind.config.js
npm run build
npm run preview
npm install @tailwindcss/forms@0.5.7 --save-dev --legacy-peer-deps
npm run build
npm run preview
/** @type {import('tailwindcss').Config} */
export default {
}
nano tailwind.config.js
npm run build
npm run preview
npm install @tailwindcss/forms@0.5.7 --save-dev --legacy-peer-deps
rm -rf node_modules/.esbuild
npm cache clean --force
rm -rf node_modules package-lock.json
npm install --legacy-peer-deps
npm install @tailwindcss/forms@0.5.7 --save-dev --legacy-peer-deps
npm run build
npm run preview
rm -rf node_modules package-lock.json
npm cache clean --force
npm install tailwindcss@3.4.10 postcss@8.4.47 autoprefixer@10.4.20 --legacy-peer-deps
npm install @tailwindcss/forms@0.5.7 --save-dev --force
npm install tailwindcss@3.4.10 postcss@8.4.47 autoprefixer@10.4.20 --legacy-peer-deps
npm install @tailwindcss/forms@0.5.7 --save-dev --force
rm -rf node_modules package-lock.json ~/.npm ~/.pnpm ~/.cache/esbuild
npm cache clean --force
npm install -D tailwindcss@3.4.10 postcss@8.4.47 autoprefixer@10.4.20 --legacy-peer-deps
npm install -D @tailwindcss/forms@0.5.7 --force --legacy-peer-deps
ls node_modules/@tailwindcss/forms
npm run build
npm run preview
mkdir -p node_modules/@tailwindcss/forms
nano node_modules/@tailwindcss/forms/index.js
npm run build
npm run preview
name: 🌌 Wave 17 – Dark Neon Deployment Monitor
on:
permissions:
env:
jobs:
cd ~/SentinelQuantumVanguardAiPro
mkdir -p .github/workflows
nano .github/workflows/wave17-dark-neon-monitor.yml
cd ~/SentinelQuantumVanguardAiPro
mkdir -p .github/workflows
nano .github/workflows/wave17-dark-neon-monitor.yml
cd ~/SentinelQuantumVanguardAiPro
mkdir -p .github/workflows
nano .github/workflows/sentinel-autonomous-rebuilder.yml
git add .github/workflows/sentinel-autonomous-rebuilder.yml
git commit -m "♻️ Dark Neon Edition – Sentinel Autonomous Rebuilder"
git push origin main
curl -s "https://api.telegram.org/bot123456789:ABCxyz/sendMessage?chat_id=987654321&text=Test+Sentinel"
- name: 📲 Telegram Status Report
git add .github/workflows/sentinel-autonomous-rebuilder.yml
git commit -m "📲 Add enhanced Telegram reporting"
git push origin main
curl -s "https://api.telegram.org/bot7630324617:AAGYNTiOIhHFDyT83Qt_DezDUxEzEJf-K9E/sendMessage?chat_id=5707814118&text=✅+Sentinel+Webhook+Operational"
name: 🌙 Telegram Nightly Check Pro+
on:
jobs:
name: 📊 Sentinel — Dashboard Mirror
on:
permissions:
jobs:
name: 📊 Sentinel — Dashboard Mirror
on:
permissions:
jobs:
Upgrade Sentinel Dashboard Mirror (Advanced)
Add Sentinel World Map Visualization 🌐
npm run build
npm install recharts
import { PieChart, Pie, Cell, Tooltip } from "recharts";
export default function StatusChart({ agents }) {
}
[submodule "akiprisaye"]
curl "https://api.cloudflare.com/client/v4/user/tokens/verify" -H "Authorization: Bearer Od1yipdEPBByV5_RXj-t6PW_dZIkRvyNMHFqihyo"
git add .github/workflows/sentinel-service-monitor.yml
git commit -m "🩹 Fix: tolérance des erreurs d’état et notification Telegram stable"
git push
git pull --rebase origin main
git add .
git commit -m "🩹 Fix: synchronisation + tolérance erreurs service-monitor"
git push origin main
git fetch origin main
git rebase origin/main
git push origin main --force
git add .github/workflows/logsync.yml
git commit -m "🆕 Ajout du module Sentinel LogSync IA corrigé"
git push origin main
git add .github/workflows/logsync.yml
git commit -m "🩹 Fix EOF error in Sentinel LogSync IA report generation"
git push origin main
git pull origin main
git add .github/workflows/update-dashboard.yml
git commit -m "🩹 Fix sed expression in dashboard update workflow"
git push origin main
git pull origin main
git add wrangler.toml
git commit -m "🛠️ Fix wrangler config and Cloudflare Pages output dir"
git push origin main
echo "🚀 Reconstruction complète Sentinel Quantum Vanguard AI Pro..."
# 1️⃣ Sécuriser le dossier
cd ~/SentinelQuantumVanguardAiPro || exit
rm -rf dist build
# 2️⃣ Correction du fichier vite.config.js
cat > vite.config.js <<'EOF'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  build: {
    outDir: 'dist',
  },
  base: './',
})
EOF

# 3️⃣ Correction du fichier wrangler.toml
cat > wrangler.toml <<'EOF'
name = "sentinel-quantum-vanguard-ai-pro"
account_id = "78642e56f72fff94c78e1ef87cb589a7"
pages_build_output_dir = "dist"
compatibility_date = "2025-10-17"
EOF

# 4️⃣ Vérification package.json
if ! grep -q '"build":' package.json; then   echo "Ajout du script build manquant...";   sed -i '/"scripts": {/a \    "build": "vite build",' package.json; fi
# 5️⃣ Installation dépendances et build
npm install
npm run build
# 6️⃣ Vérification build
if [ -f dist/index.html ]; then   echo "✅ Build OK : dist/index.html trouvé."; else   echo "❌ Erreur : build incomplet.";   exit 1; fi
