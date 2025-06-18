
# 🛡️ Sentinelle Quantum Vanguard AI Pro

> **Protection avancée pour femmes seules, personnes vulnérables, seniors et enfants** – alimentée par IA, OSINT et modules de cybersécurité militaire.

---

## 🔍 À propos

Sentinelle Quantum Vanguard AI Pro est une application de cybersécurité intelligente intégrant :
- IA prédictive comportementale
- Analyse OSINT géolocalisée
- Interface vocale intelligente
- Bouclier quantique + VPN interne

Ce projet est conçu pour Windows et Web (Flask) — déployable localement ou dans le cloud (Codespaces).

---

## 📁 Contenu du projet

| Fichier                          | Description |
|----------------------------------|-------------|
| `sentinelle_pc_flask.py`        | Backend Flask principal |
| `requirements.txt`              | Dépendances Python |
| `Sentinelle.ico`                | Icône officielle Windows |
| `splash.png`                    | Splash screen de démarrage |
| `build_exe.bat`                 | Compilation `.exe` avec PyInstaller |
| `inno_setup.iss`                | Script d'installation Windows `.msi` |
| `.github/workflows/build.yml`   | Action GitHub : build automatique |

---

## 🚀 Installation locale

1. Cloner ce dépôt
2. Installer les dépendances :
```bash
pip install -r requirements.txt
```
3. Lancer l'app Flask :
```bash
python sentinelle_pc_flask.py
```

Puis ouvrir : [http://localhost:8080](http://localhost:8080)

---

## 🏗️ Compilation Windows

Créer le fichier `.exe` :
```bash
build_exe.bat
```

Créer l'installateur `.msi` :
```bash
Ouvrir le fichier inno_setup.iss avec Inno Setup puis "Compiler"
```

---

## 🛠️ Modules inclus

- 🔐 `IA_BehavioralPredictive` – prédiction comportementale
- 🌐 `AI_OSINT_Monitoring` – surveillance ouverte géolocalisée
- 🧬 `QuantumShield` – protection cryptée
- 🎤 `VocalAlerts` – alertes vocales multilingues

---

## 🧪 Compilation auto GitHub Actions

Ce projet est compatible avec GitHub Actions :
- Build `.exe` automatique à chaque `push`
- Artefacts téléchargeables dans les Actions du dépôt

---

## 📌 Prochaines fonctionnalités

- [ ] Module IA de prévision de menaces locales
- [ ] Intégration de données santé connectées (montres / capteurs)
- [ ] Vision par caméra (analyse prédictive d’environnement)
- [ ] Dashboard web en React ou Electron

---

## 👨‍💻 Auteur

**Thierry Naud**  
Développeur & créateur du projet `SentinelleQuantum`  
GitHub : [teetee971](https://github.com/teetee971)

---
