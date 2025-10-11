===========================================================
🚀 SENTINEL QUANTUM VANGUARD AI PRO — AUTO DEPLOY SYSTEM
===========================================================

🧠 Mode : Supervision IA Active (Zero-Downtime + Auto-Heal)

Ce projet est équipé de deux workflows GitHub Actions :
-------------------------------------------------------

1️⃣ release-autodeploy.yml
   → Génère automatiquement un package ZIP complet
     (SentinelQuantumVanguardAiPro_AutoDeploy_CloudEdition.zip)
     et le publie dans GitHub Releases à chaque commit.

2️⃣ sentinel-autodeploy.yml
   → Déploie automatiquement sur Firebase Hosting
     et purge le cache Cloudflare Pages.
   → En cas d’échec, déclenche un rollback IA
     (via firebase-hosting-rollback-on-failure.yml).

-------------------------------------------------------

💡 Utilisation :
- Pousser un commit sur la branche `main`
- GitHub exécute automatiquement les deux workflows
- Le site est mis à jour, testé, puis publié sans intervention

-------------------------------------------------------

🧰 Configuration :
- 🔐 FIREBASE_TOKEN : Clé CI ajoutée dans Secrets GitHub
- 🔐 CF_API_TOKEN & CF_ACCOUNT_ID : pour purge Cloudflare
- 📦 Les fichiers Firebase (.firebaserc / firebase.json)
  définissent la cible de déploiement.

-------------------------------------------------------

👁️ Supervision IA :
- SentinelHealer : réparation automatique si erreur
- AutoVerifier : vérifie Firebase + Cloudflare synchronisés
- FlowFinalizer : détecte blocages et boucle de build
- QuantumFailoverAI : bascule instantanée sur version stable

-------------------------------------------------------

📍 Liens utiles :
- Firebase Console : https://console.firebase.google.com/
- GitHub Actions : onglet "Actions"
- Releases : https://github.com/teetee971/SentinelQuantumVanguardAiPro/releases

===========================================================
✅ Ce système est 100 % autonome : aucune action manuelle requise.
===========================================================