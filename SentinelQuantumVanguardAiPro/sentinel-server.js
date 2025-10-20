// sentinel-server.js
import express from "express";
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const app = express();
const logFile = path.join(__dirname, "logs/sentinel.log");

let port = 3000;

// Fonction de log
function log(msg) {
  const t = new Date().toISOString();
  fs.appendFileSync(logFile, `[${t}] ${msg}\n`);
  console.log(msg);
}

app.use(express.static(path.join(__dirname, "public")));
app.get("/", (req, res) => {
  res.send("<h2>🛰️ Sentinel Quantum Vanguard AI Pro - Light Server</h2>");
});

// Fonction pour démarrer le serveur
function startServer() {
  const server = app
    .listen(port, () => {
      log(`🟢 Serveur actif sur http://localhost:${port}`);
    })
    .on("error", (err) => {
      if (err.code === "EADDRINUSE") {
        log(`⚠️ Port ${port} déjà utilisé → tentative sur ${port + 1}`);
        port++;
        startServer();
      } else {
        log(`🔴 Erreur serveur : ${err.message}`);
      }
    });
}

startServer();
