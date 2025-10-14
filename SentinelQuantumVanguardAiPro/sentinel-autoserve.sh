#!/data/data/com.termux/files/usr/bin/bash
echo "🧠 Sentinel Quantum Vanguard AI Pro — AutoServe (Termux Safe Mode)"
echo "---------------------------------------------------------------"

# Étape 1 : Nettoyage complet
rm -rf dist node_modules/.vite

# Étape 2 : Serveur sans build (pas de Rollup)
echo "🚀 Démarrage direct du site depuis dist/ sur le port 3000..."
pnpm vite preview --port 3000 --host 0.0.0.0
