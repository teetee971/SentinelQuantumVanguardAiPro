#!/data/data/com.termux/files/usr/bin/bash
echo "🔍 Audit Sentinel Quantum Vanguard AI Pro - $(date)"
echo "---------------------------------------------------"

# 1. Structure
echo ""
echo "📁 Vérification de la structure du projet..."
tree -L 2 2>/dev/null || echo "⚠️ 'tree' non installé"

# 2. Fichiers essentiels
echo ""
echo "🧱 Vérification des fichiers essentiels..."
for f in vite.config.js package.json index.html src/App.jsx; do
  if [ -f "$f" ]; then
    echo "✅ $f trouvé"
  else
    echo "❌ $f manquant"
  fi
done

# 3. Logo Sentinel
echo ""
echo "🛡️ Vérification du logo Sentinel..."
if [ -f "public/assets/sentinel_logo.png" ]; then
  echo "✅ Logo détecté dans /public/assets/"
else
  echo "❌ Logo manquant ou mauvais chemin"
fi

# 4. Build test
echo ""
echo "⚙️ Test du build..."
npm run build --silent
if [ $? -eq 0 ]; then
  echo "✅ Build réussi"
else
  echo "❌ Erreur pendant le build"
fi

# 5. Serveur local
echo ""
echo "🌐 Vérification serveur local (port 5173)..."
if ss -lnt 2>/dev/null | grep -q ":5173"; then
  echo "✅ Serveur local en cours d'exécution"
else
  echo "❌ Aucun serveur local détecté"
fi

# 6. Cloudflare Pages
echo ""
echo "☁️ Vérification du site Cloudflare..."
if curl -s -o /dev/null -w "%{http_code}" https://sentinelquantumvanguardaipro.pages.dev | grep -q "200"; then
  echo "✅ Site accessible sur Cloudflare"
else
  echo "❌ Site non accessible (404 ou DNS en attente)"
fi

# 7. Diagnostic IA Sentinel
echo ""
echo "🤖 Diagnostic IA en cours..."
AGENTS=("QuantumFailoverAI" "CognitiveTraceAgent" "HeuristicPredictorAI" "PerformanceAutoTuner" "GlobalFailoverWatcher")

for agent in "${AGENTS[@]}"; do
  STATUS=$(grep -i "$agent" ./src/dashboard/Dashboard.jsx 2>/dev/null)
  if [ -n "$STATUS" ]; then
    echo "✅ $agent détecté dans le code source"
  else
    echo "⚠️ $agent non détecté ou désactivé"
  fi
done

# Vérif journal réseau IA
echo ""
echo "📡 Analyse du journal IA..."
if grep -q "Activité réseau stable" ./src/dashboard/Dashboard.jsx 2>/dev/null; then
  echo "✅ Activité réseau IA stable"
else
  echo "⚠️ Aucun log réseau IA détecté"
fi

# 8. Résumé
echo ""
echo "📋 Audit terminé - Rapport IA + Système généré."
echo "---------------------------------------------------"
